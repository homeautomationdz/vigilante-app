package com.vigilante.app.data.excel

import com.vigilante.app.core.IdParser
import com.vigilante.app.core.Validation
import com.vigilante.app.data.local.entity.Admin
import com.vigilante.app.data.local.entity.AdminRole
import com.vigilante.app.data.local.entity.Attendance
import com.vigilante.app.data.local.entity.AttendanceStatus
import com.vigilante.app.data.local.entity.Volunteer
import com.vigilante.app.data.local.entity.VolunteerStatus
import org.dhatim.fastexcel.reader.ReadableWorkbook
import org.dhatim.fastexcel.reader.Row
import org.dhatim.fastexcel.reader.Sheet
import java.io.File
import java.io.InputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.jvm.optionals.getOrNull

/**
 * Streaming reader for Volunteers_Import.xlsx (SRS ch. 13/50).
 * Columns are located BY NAME (BR-010); the whole file is validated and a
 * full error report produced BEFORE anything touches the database (BR-009).
 */
@Singleton
class ExcelImporter @Inject constructor() {

    fun read(file: File, localOrgId: String?): ImportReadResult =
        runCatching { file.inputStream().use { readStream(it, localOrgId) } }
            .getOrElse { e -> ImportReadResult.InvalidFile("تعذر قراءة الملف: ${e.message ?: "ملف تالف"}") }

    private fun readStream(input: InputStream, localOrgId: String?): ImportReadResult {
        ReadableWorkbook(input).use { wb ->
            val volunteersSheet = wb.findSheet(ExcelSchema.Sheets.VOLUNTEERS).getOrNull()
                ?: return ImportReadResult.InvalidFile("ورقة ${ExcelSchema.Sheets.VOLUNTEERS} غير موجودة")

            val errors = mutableListOf<RowError>()
            val volunteers = mutableListOf<Pair<Volunteer, List<String>>>()
            val attendance = mutableListOf<Attendance>()
            val admins = mutableListOf<Admin>()
            val seenIds = mutableSetOf<String>()

            readVolunteers(volunteersSheet, ExcelSchema.Sheets.VOLUNTEERS, errors, volunteers, seenIds)

            wb.findSheet(ExcelSchema.Sheets.ARCHIVE).getOrNull()?.let { sheet ->
                readVolunteers(sheet, ExcelSchema.Sheets.ARCHIVE, errors, volunteers, seenIds, forceArchived = true)
            }
            wb.findSheet(ExcelSchema.Sheets.ATTENDANCE).getOrNull()?.let { sheet ->
                readAttendance(sheet, errors, attendance, seenIds)
            }
            wb.findSheet(ExcelSchema.Sheets.ADMINS).getOrNull()?.let { sheet ->
                readAdmins(sheet, errors, admins)
            }
            val metadata = wb.findSheet(ExcelSchema.Sheets.METADATA).getOrNull()
                ?.let { readKeyValue(it) } ?: emptyMap()

            if (errors.isNotEmpty()) return ImportReadResult.ValidationFailed(errors)

            val data = ImportedData(volunteers, attendance, admins, metadata)
            val fileOrg = metadata[ExcelSchema.Metadata.ORG_ID]
            if (!fileOrg.isNullOrBlank() && !localOrgId.isNullOrBlank() && fileOrg != localOrgId) {
                return ImportReadResult.WrongOrganization(fileOrg, localOrgId, data)
            }
            return ImportReadResult.Success(data)
        }
    }

    // ---- sheet readers ----

    private fun readVolunteers(
        sheet: Sheet,
        sheetName: String,
        errors: MutableList<RowError>,
        out: MutableList<Pair<Volunteer, List<String>>>,
        seenIds: MutableSet<String>,
        forceArchived: Boolean = false
    ) {
        val rows = sheet.openStream()
        var header: Map<String, Int>? = null
        rows.use { stream ->
            stream.forEach { row ->
                if (header == null) {
                    header = headerMap(row)
                    val missing = ExcelSchema.Volunteers.REQUIRED.filter { it !in header!! }
                    if (missing.isNotEmpty()) {
                        errors += RowError(sheetName, 1, "أعمدة أساسية مفقودة: ${missing.joinToString()}")
                        return
                    }
                    return@forEach
                }
                val h = header!!
                val rowNum = row.rowNum
                fun cell(name: String): String? =
                    h[name]?.let { idx -> row.getCellText(idx)?.trim()?.takeIf { it.isNotBlank() } }

                val id = cell(ExcelSchema.Volunteers.ID) ?: run {
                    errors += RowError(sheetName, rowNum, "VolunteerID مفقود"); return@forEach
                }
                if (!IdParser.isVolunteerId(id)) {
                    errors += RowError(sheetName, rowNum, "VolunteerID غير صالح: $id"); return@forEach
                }
                if (!seenIds.add(id)) {
                    errors += RowError(sheetName, rowNum, "VolunteerID مكرر داخل الملف: $id"); return@forEach
                }
                val firstName = cell(ExcelSchema.Volunteers.FIRST_NAME)
                val lastName = cell(ExcelSchema.Volunteers.LAST_NAME)
                val fatherName = cell(ExcelSchema.Volunteers.FATHER_NAME)
                val phone1 = cell(ExcelSchema.Volunteers.PHONE1)
                val joinDateStr = cell(ExcelSchema.Volunteers.JOIN_DATE)
                if (firstName == null || lastName == null || fatherName == null) {
                    errors += RowError(sheetName, rowNum, "الاسم أو اللقب أو اسم الأب مفقود"); return@forEach
                }
                if (phone1 == null || !Validation.isValidPhone(phone1)) {
                    errors += RowError(sheetName, rowNum, "رقم الهاتف غير صالح"); return@forEach
                }
                val joinDate = joinDateStr?.let { parseDate(it) } ?: run {
                    errors += RowError(sheetName, rowNum, "تاريخ الانضمام غير صالح"); return@forEach
                }
                val birthDate = cell(ExcelSchema.Volunteers.BIRTH_DATE)?.let {
                    parseDate(it) ?: run {
                        errors += RowError(sheetName, rowNum, "تاريخ الميلاد غير صالح"); return@forEach
                    }
                }
                val bloodGroup = cell(ExcelSchema.Volunteers.BLOOD_GROUP)
                if (!Validation.isValidBloodGroup(bloodGroup)) {
                    errors += RowError(sheetName, rowNum, "زمرة الدم غير صحيحة: $bloodGroup"); return@forEach
                }
                val status = when {
                    forceArchived -> VolunteerStatus.ARCHIVED
                    cell(ExcelSchema.Volunteers.STATUS)?.equals("ARCHIVED", true) == true -> VolunteerStatus.ARCHIVED
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
        }
    }

    private fun readAttendance(
        sheet: Sheet,
        errors: MutableList<RowError>,
        out: MutableList<Attendance>,
        volunteerIds: Set<String>
    ) {
        var header: Map<String, Int>? = null
        sheet.openStream().use { stream ->
            stream.forEach { row ->
                if (header == null) {
                    header = headerMap(row)
                    val missing = ExcelSchema.Attendance.REQUIRED.filter { it !in header!! }
                    if (missing.isNotEmpty()) {
                        errors += RowError(ExcelSchema.Sheets.ATTENDANCE, 1, "أعمدة أساسية مفقودة: ${missing.joinToString()}")
                        return
                    }
                    return@forEach
                }
                val h = header!!
                val rowNum = row.rowNum
                fun cell(name: String): String? =
                    h[name]?.let { idx -> row.getCellText(idx)?.trim()?.takeIf { it.isNotBlank() } }

                val id = cell(ExcelSchema.Attendance.ID) ?: run {
                    errors += RowError(ExcelSchema.Sheets.ATTENDANCE, rowNum, "AttendanceID مفقود"); return@forEach
                }
                val volunteerId = cell(ExcelSchema.Attendance.VOLUNTEER_ID) ?: run {
                    errors += RowError(ExcelSchema.Sheets.ATTENDANCE, rowNum, "VolunteerID مفقود"); return@forEach
                }
                if (volunteerId !in volunteerIds) {
                    errors += RowError(
                        ExcelSchema.Sheets.ATTENDANCE, rowNum,
                        "سجل حضور لمتطوع غير موجود: $volunteerId"
                    ); return@forEach
                }
                val date = cell(ExcelSchema.Attendance.DATE)?.let(::parseDate) ?: run {
                    errors += RowError(ExcelSchema.Sheets.ATTENDANCE, rowNum, "التاريخ غير صالح"); return@forEach
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
        }
    }

    private fun readAdmins(sheet: Sheet, errors: MutableList<RowError>, out: MutableList<Admin>) {
        var header: Map<String, Int>? = null
        sheet.openStream().use { stream ->
            stream.forEach { row ->
                if (header == null) { header = headerMap(row); return@forEach }
                val h = header!!
                fun cell(name: String): String? =
                    h[name]?.let { idx -> row.getCellText(idx)?.trim()?.takeIf { it.isNotBlank() } }
                val id = cell(ExcelSchema.Admins.ID) ?: return@forEach
                val username = cell(ExcelSchema.Admins.USERNAME) ?: return@forEach
                val hash = cell(ExcelSchema.Admins.PASSWORD_HASH) ?: return@forEach
                val role = cell(ExcelSchema.Admins.ROLE)?.let {
                    runCatching { AdminRole.valueOf(it) }.getOrNull()
                } ?: AdminRole.ADMIN
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
    }

    private fun readKeyValue(sheet: Sheet): Map<String, String> {
        val map = mutableMapOf<String, String>()
        var first = true
        sheet.openStream().use { stream ->
            stream.forEach { row ->
                if (first) { first = false; return@forEach }
                val k = row.getCellText(0)?.trim()
                val v = row.getCellText(1)?.trim()
                if (!k.isNullOrBlank() && v != null) map[k] = v
            }
        }
        return map
    }

    // ---- helpers ----

    private fun headerMap(row: Row): Map<String, Int> {
        val map = mutableMapOf<String, Int>()
        for (i in 0 until row.cellCount) {
            row.getCellText(i)?.trim()?.takeIf { it.isNotBlank() }?.let { map[it] = i }
        }
        return map
    }

    private fun Row.getCellText(index: Int): String? =
        runCatching { getCellAsString(index).getOrNull() }.getOrNull()

    private val dateFormats = listOf(
        DateTimeFormatter.ISO_LOCAL_DATE,
        DateTimeFormatter.ofPattern("dd/MM/yyyy"),
        DateTimeFormatter.ofPattern("d/M/yyyy"),
        DateTimeFormatter.ofPattern("yyyy/MM/dd")
    )

    private fun parseDate(text: String): LocalDate? {
        val t = text.trim().substringBefore('T').substringBefore(' ')
        dateFormats.forEach { fmt ->
            runCatching { return LocalDate.parse(t, fmt) }
        }
        return null
    }

    private fun parseDateTime(text: String): LocalDateTime? =
        runCatching { LocalDateTime.parse(text.trim()) }.getOrNull()
            ?: parseDate(text)?.atStartOfDay()

    private fun parseTime(text: String): LocalTime? =
        runCatching { LocalTime.parse(text.trim()) }.getOrNull()
}
