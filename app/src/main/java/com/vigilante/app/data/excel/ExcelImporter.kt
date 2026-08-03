package com.vigilante.app.data.excel

import com.vigilante.app.core.IdParser
import com.vigilante.app.core.Validation
import com.vigilante.app.data.local.entity.Admin
import com.vigilante.app.data.local.entity.AdminRole
import com.vigilante.app.data.local.entity.Attendance
import com.vigilante.app.data.local.entity.AttendanceStatus
import com.vigilante.app.data.local.entity.Volunteer
import com.vigilante.app.data.local.entity.VolunteerStatus
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads Volunteers_Import.xlsx (SRS ch. 13/50) through [XlsxWorkbook].
 *
 * Columns are located BY NAME (BR-010); the whole file is validated and a full
 * error report produced BEFORE anything touches the database (BR-009). Every
 * rejection names its real cause — "الورقة Volunteers غير موجودة", "العمود
 * VolunteerID مفقود" — never a bare "ملف غير صالح".
 */
@Singleton
class ExcelImporter @Inject constructor() {

    fun read(file: File, localOrgId: String?): ImportReadResult {
        val workbook = try {
            XlsxWorkbook.open(file)
        } catch (e: NotAnXlsxException) {
            return ImportReadResult.InvalidFile(e.message ?: "الملف ليس ملف Excel بصيغة xlsx")
        } catch (e: Exception) {
            return ImportReadResult.InvalidFile(
                "تعذر فتح الملف: ${e.message ?: e.javaClass.simpleName}"
            )
        }

        workbook.use { wb ->
            if (!wb.hasSheet(ExcelSchema.Sheets.VOLUNTEERS)) {
                return ImportReadResult.InvalidFile(
                    "الورقة ${ExcelSchema.Sheets.VOLUNTEERS} غير موجودة داخل الملف. " +
                        "الأوراق الموجودة: ${wb.sheetNames.joinToString("، ").ifBlank { "لا شيء" }}"
                )
            }

            val errors = mutableListOf<RowError>()
            val volunteers = mutableListOf<Pair<Volunteer, List<String>>>()
            val attendance = mutableListOf<Attendance>()
            val admins = mutableListOf<Admin>()
            val seenIds = mutableSetOf<String>()

            readVolunteers(wb, ExcelSchema.Sheets.VOLUNTEERS, errors, volunteers, seenIds)
                ?.let { return ImportReadResult.InvalidFile(it) }

            if (wb.hasSheet(ExcelSchema.Sheets.ARCHIVE)) {
                readVolunteers(
                    wb, ExcelSchema.Sheets.ARCHIVE, errors, volunteers, seenIds, forceArchived = true
                )?.let { return ImportReadResult.InvalidFile(it) }
            }
            if (wb.hasSheet(ExcelSchema.Sheets.ATTENDANCE)) {
                readAttendance(wb, errors, attendance, seenIds)
                    ?.let { return ImportReadResult.InvalidFile(it) }
            }
            if (wb.hasSheet(ExcelSchema.Sheets.ADMINS)) {
                readAdmins(wb, admins)
            }
            val metadata = if (wb.hasSheet(ExcelSchema.Sheets.METADATA)) {
                readKeyValue(wb, ExcelSchema.Sheets.METADATA)
            } else emptyMap()

            val fileVersion = metadata[ExcelSchema.Metadata.DB_VERSION]
            if (!fileVersion.isNullOrBlank() && !isSupportedVersion(fileVersion)) {
                return ImportReadResult.InvalidFile(
                    "إصدار قاعدة البيانات في الملف ($fileVersion) غير مدعوم — " +
                        "الإصدار المدعوم: ${SUPPORTED_DB_VERSIONS.joinToString("، ")}"
                )
            }

            if (errors.isNotEmpty()) return ImportReadResult.ValidationFailed(errors)

            val data = ImportedData(volunteers, attendance, admins, metadata)
            val fileOrg = metadata[ExcelSchema.Metadata.ORG_ID]
            if (!fileOrg.isNullOrBlank() && !localOrgId.isNullOrBlank() && fileOrg != localOrgId) {
                return ImportReadResult.WrongOrganization(fileOrg, localOrgId, data)
            }
            return ImportReadResult.Success(data)
        }
    }

    // ---- sheet readers: return a fatal message, or null when the sheet is usable ----

    private fun readVolunteers(
        wb: XlsxWorkbook,
        sheetName: String,
        errors: MutableList<RowError>,
        out: MutableList<Pair<Volunteer, List<String>>>,
        seenIds: MutableSet<String>,
        forceArchived: Boolean = false
    ): String? {
        var header: Map<String, Int>? = null
        var fatal: String? = null

        wb.readRows(sheetName) { rowNumber, cells ->
            if (fatal != null) return@readRows
            if (header == null) {
                header = headerMap(cells)
                val missing = ExcelSchema.Volunteers.REQUIRED.filter { it !in header!! }
                if (missing.isNotEmpty()) {
                    fatal = "أعمدة أساسية مفقودة في الورقة $sheetName: ${missing.joinToString("، ")}"
                }
                return@readRows
            }
            val h = header!!
            fun cell(name: String): String? =
                h[name]?.let { idx -> cells[idx]?.trim()?.takeIf { it.isNotBlank() } }

            val id = cell(ExcelSchema.Volunteers.ID) ?: run {
                errors += RowError(sheetName, rowNumber, "VolunteerID مفقود"); return@readRows
            }
            if (!IdParser.isVolunteerId(id)) {
                errors += RowError(sheetName, rowNumber, "VolunteerID غير صالح: $id"); return@readRows
            }
            if (!seenIds.add(id)) {
                errors += RowError(sheetName, rowNumber, "VolunteerID مكرر داخل الملف: $id")
                return@readRows
            }
            val firstName = cell(ExcelSchema.Volunteers.FIRST_NAME)
            val lastName = cell(ExcelSchema.Volunteers.LAST_NAME)
            val fatherName = cell(ExcelSchema.Volunteers.FATHER_NAME)
            val phone1 = cell(ExcelSchema.Volunteers.PHONE1)
            if (firstName == null || lastName == null || fatherName == null) {
                errors += RowError(sheetName, rowNumber, "الاسم أو اللقب أو اسم الأب مفقود")
                return@readRows
            }
            if (phone1 == null || !Validation.isValidPhone(phone1)) {
                errors += RowError(
                    sheetName, rowNumber,
                    "رقم الهاتف غير صالح (يجب 10 أرقام تبدأ بـ 0): ${phone1 ?: "فارغ"}"
                )
                return@readRows
            }
            val joinDate = cell(ExcelSchema.Volunteers.JOIN_DATE)?.let(::parseDate) ?: run {
                errors += RowError(sheetName, rowNumber, "تاريخ الانضمام غير صالح"); return@readRows
            }
            val birthRaw = cell(ExcelSchema.Volunteers.BIRTH_DATE)
            val birthDate = if (birthRaw == null) null else parseDate(birthRaw) ?: run {
                errors += RowError(sheetName, rowNumber, "تاريخ الميلاد غير صالح: $birthRaw")
                return@readRows
            }
            val bloodGroup = cell(ExcelSchema.Volunteers.BLOOD_GROUP)
            if (!Validation.isValidBloodGroup(bloodGroup)) {
                errors += RowError(sheetName, rowNumber, "زمرة الدم غير صحيحة: $bloodGroup")
                return@readRows
            }
            val status = when {
                forceArchived -> VolunteerStatus.ARCHIVED
                cell(ExcelSchema.Volunteers.STATUS)?.equals("ARCHIVED", true) == true ->
                    VolunteerStatus.ARCHIVED
                else -> VolunteerStatus.ACTIVE
            }
            val tags = cell(ExcelSchema.Volunteers.TAGS)
                ?.split(',', '،')?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()

            out += Volunteer(
                volunteerId = id,
                membershipNumber = cell(ExcelSchema.Volunteers.MEMBERSHIP) ?: id,
                firstName = firstName, lastName = lastName, fatherName = fatherName,
                birthDate = birthDate, joinDate = joinDate,
                municipality = cell(ExcelSchema.Volunteers.MUNICIPALITY),
                district = cell(ExcelSchema.Volunteers.DISTRICT),
                bloodGroup = bloodGroup,
                phone1 = phone1,
                phone2 = cell(ExcelSchema.Volunteers.PHONE2),
                photoPath = cell(ExcelSchema.Volunteers.PHOTO_PATH),
                notes = cell(ExcelSchema.Volunteers.NOTES),
                status = status,
                archiveDate = cell(ExcelSchema.Volunteers.ARCHIVE_DATE)?.let(::parseDateTime),
                archivedBy = cell(ExcelSchema.Volunteers.ARCHIVED_BY),
                archiveReason = cell(ExcelSchema.Volunteers.ARCHIVE_REASON),
                createdBy = cell(ExcelSchema.Volunteers.CREATED_BY) ?: "import",
                createdAt = cell(ExcelSchema.Volunteers.CREATED_AT)?.let(::parseDateTime)
                    ?: LocalDateTime.now(),
                updatedBy = cell(ExcelSchema.Volunteers.UPDATED_BY),
                updatedByRole = cell(ExcelSchema.Volunteers.UPDATED_BY_ROLE),
                updatedAt = cell(ExcelSchema.Volunteers.UPDATED_AT)?.let(::parseDateTime)
            ) to tags
        }
        return fatal
    }

    private fun readAttendance(
        wb: XlsxWorkbook,
        errors: MutableList<RowError>,
        out: MutableList<Attendance>,
        volunteerIds: Set<String>
    ): String? {
        val sheet = ExcelSchema.Sheets.ATTENDANCE
        var header: Map<String, Int>? = null
        var fatal: String? = null

        wb.readRows(sheet) { rowNumber, cells ->
            if (fatal != null) return@readRows
            if (header == null) {
                header = headerMap(cells)
                val missing = ExcelSchema.Attendance.REQUIRED.filter { it !in header!! }
                if (missing.isNotEmpty()) {
                    fatal = "أعمدة أساسية مفقودة في الورقة $sheet: ${missing.joinToString("، ")}"
                }
                return@readRows
            }
            val h = header!!
            fun cell(name: String): String? =
                h[name]?.let { idx -> cells[idx]?.trim()?.takeIf { it.isNotBlank() } }

            val id = cell(ExcelSchema.Attendance.ID) ?: run {
                errors += RowError(sheet, rowNumber, "AttendanceID مفقود"); return@readRows
            }
            val volunteerId = cell(ExcelSchema.Attendance.VOLUNTEER_ID) ?: run {
                errors += RowError(sheet, rowNumber, "VolunteerID مفقود"); return@readRows
            }
            if (volunteerId !in volunteerIds) {
                errors += RowError(sheet, rowNumber, "سجل حضور لمتطوع غير موجود: $volunteerId")
                return@readRows
            }
            val date = cell(ExcelSchema.Attendance.DATE)?.let(::parseDate) ?: run {
                errors += RowError(sheet, rowNumber, "التاريخ غير صالح"); return@readRows
            }
            val time = cell(ExcelSchema.Attendance.TIME)?.let(::parseTime) ?: LocalTime.MIDNIGHT
            out += Attendance(
                attendanceId = id,
                volunteerId = volunteerId,
                recordedAt = LocalDateTime.of(date, time),
                adminUsername = cell(ExcelSchema.Attendance.ADMIN) ?: "import",
                notes = cell(ExcelSchema.Attendance.NOTES),
                status = if (cell(ExcelSchema.Attendance.STATUS)?.equals("CANCELLED", true) == true)
                    AttendanceStatus.CANCELLED else AttendanceStatus.VALID
            )
        }
        return fatal
    }

    private fun readAdmins(wb: XlsxWorkbook, out: MutableList<Admin>) {
        var header: Map<String, Int>? = null
        wb.readRows(ExcelSchema.Sheets.ADMINS) { _, cells ->
            if (header == null) { header = headerMap(cells); return@readRows }
            val h = header!!
            fun cell(name: String): String? =
                h[name]?.let { idx -> cells[idx]?.trim()?.takeIf { it.isNotBlank() } }

            val id = cell(ExcelSchema.Admins.ID) ?: return@readRows
            val username = cell(ExcelSchema.Admins.USERNAME) ?: return@readRows
            val hash = cell(ExcelSchema.Admins.PASSWORD_HASH) ?: return@readRows
            val role = cell(ExcelSchema.Admins.ROLE)
                ?.let { runCatching { AdminRole.valueOf(it) }.getOrNull() }
                ?: AdminRole.ADMIN
            out += Admin(
                adminId = id,
                fullName = cell(ExcelSchema.Admins.FULL_NAME) ?: username,
                username = username,
                passwordHash = hash,
                role = role,
                active = cell(ExcelSchema.Admins.ACTIVE)?.equals("TRUE", true) != false,
                createdAt = cell(ExcelSchema.Admins.CREATED_AT)?.let(::parseDateTime)
                    ?: LocalDateTime.now(),
                lastLogin = cell(ExcelSchema.Admins.LAST_LOGIN)?.let(::parseDateTime),
                permissionsJson = cell(ExcelSchema.Admins.PERMISSIONS)
            )
        }
    }

    private fun readKeyValue(wb: XlsxWorkbook, sheet: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        var first = true
        wb.readRows(sheet) { _, cells ->
            if (first) { first = false; return@readRows }
            val k = cells[0]?.trim()
            val v = cells[1]?.trim()
            if (!k.isNullOrBlank() && v != null) map[k] = v
        }
        return map
    }

    // ---- helpers ----

    private fun headerMap(cells: Map<Int, String>): Map<String, Int> =
        cells.entries
            .mapNotNull { (index, value) ->
                value.trim().takeIf { it.isNotBlank() }?.let { it to index }
            }
            .toMap()

    private val dateFormats = listOf(
        DateTimeFormatter.ISO_LOCAL_DATE,
        DateTimeFormatter.ofPattern("dd/MM/yyyy"),
        DateTimeFormatter.ofPattern("d/M/yyyy"),
        DateTimeFormatter.ofPattern("yyyy/MM/dd")
    )

    /** Excel's own epoch: serial 1 = 1900-01-01, with the historical leap-year bug. */
    private val excelEpoch: LocalDate = LocalDate.of(1899, 12, 30)

    private fun parseDate(text: String): LocalDate? {
        val t = text.trim().substringBefore('T').substringBefore(' ')
        dateFormats.forEach { fmt -> runCatching { return LocalDate.parse(t, fmt) } }
        // A date typed inside Excel comes back as a serial number.
        val serial = t.toDoubleOrNull()
        if (serial != null && serial > 0 && serial < 200_000) {
            return excelEpoch.plusDays(serial.toLong())
        }
        return null
    }

    private fun parseDateTime(text: String): LocalDateTime? =
        runCatching { LocalDateTime.parse(text.trim()) }.getOrNull()
            ?: parseDate(text)?.atStartOfDay()

    private fun parseTime(text: String): LocalTime? {
        val t = text.trim()
        runCatching { return LocalTime.parse(t) }
        // Excel stores a bare time as a fraction of a day.
        val fraction = t.toDoubleOrNull()
        if (fraction != null && fraction >= 0 && fraction < 1) {
            return LocalTime.ofSecondOfDay((fraction * 86_400).toLong().coerceIn(0, 86_399))
        }
        return null
    }

    private fun isSupportedVersion(version: String): Boolean =
        version.trim() in SUPPORTED_DB_VERSIONS

    private companion object {
        val SUPPORTED_DB_VERSIONS = setOf("1.0")
    }
}
