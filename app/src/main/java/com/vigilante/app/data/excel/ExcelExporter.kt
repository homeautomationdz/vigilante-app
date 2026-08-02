package com.vigilante.app.data.excel

import com.vigilante.app.core.AppFolders
import com.vigilante.app.core.Validation
import com.vigilante.app.data.local.VigilanteDatabase
import com.vigilante.app.data.local.entity.AppSetting
import com.vigilante.app.data.local.entity.VolunteerStatus
import org.dhatim.fastexcel.Workbook
import org.dhatim.fastexcel.Worksheet
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes the full 8-sheet Volunteers_Master.xlsx (SRS ch. 13/22) using
 * streaming row writes. The file is written to a temp file first and
 * atomically renamed so a crash can never leave a half-written master file.
 */
@Singleton
class ExcelExporter @Inject constructor(
    private val db: VigilanteDatabase,
    private val folders: AppFolders
) {
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss")

    /** Exports everything to [target]; returns the written file. */
    suspend fun exportAll(target: File, appVersion: String): File {
        folders.ensureAll()
        val tmp = File(target.parentFile, target.name + ".tmp")

        FileOutputStream(tmp).use { out ->
            val wb = Workbook(out, "Vigilante", "1.0")

            writeVolunteersSheet(wb, ExcelSchema.Sheets.VOLUNTEERS, archived = false)
            writeAttendanceSheet(wb)
            writeAdminsSheet(wb)
            writeAuditSheet(wb)
            writeVolunteersSheet(wb, ExcelSchema.Sheets.ARCHIVE, archived = true)
            writeSettingsSheet(wb)
            writeBloodGroupsSheet(wb)
            writeMetadataSheet(wb, appVersion)

            wb.finish()
        }
        if (target.exists()) target.delete()
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
        return target
    }

    private suspend fun writeVolunteersSheet(wb: Workbook, sheetName: String, archived: Boolean) {
        val ws = wb.newWorksheet(sheetName)
        ws.rightToLeft()
        val cols = if (archived) ExcelSchema.Volunteers.ARCHIVE_ALL else ExcelSchema.Volunteers.ALL
        header(ws, cols)
        var r = 1
        db.volunteerDao().all()
            .filter { (it.status == VolunteerStatus.ARCHIVED) == archived }
            .forEach { v ->
                val tags = db.tagDao().tagsFor(v.volunteerId).joinToString(",") { it.name }
                var c = 0
                fun put(value: String?) { ws.value(r, c++, value ?: "") }
                put(v.volunteerId); put(v.membershipNumber)
                put(v.firstName); put(v.lastName); put(v.fatherName)
                put(v.birthDate?.toString()); put(v.joinDate.toString())
                put(v.municipality); put(v.district); put(v.bloodGroup)
                put(v.phone1); put(v.phone2); put(v.photoPath); put(v.notes)
                put(v.status.name); put(tags)
                put(v.createdBy); put(v.createdAt.toString())
                put(v.updatedBy); put(v.updatedByRole); put(v.updatedAt?.toString())
                if (archived) {
                    put(v.archiveDate?.toString()); put(v.archivedBy); put(v.archiveReason)
                }
                r++
            }
    }

    private suspend fun writeAttendanceSheet(wb: Workbook) {
        val ws = wb.newWorksheet(ExcelSchema.Sheets.ATTENDANCE)
        ws.rightToLeft()
        header(ws, ExcelSchema.Attendance.ALL)
        var r = 1
        db.attendanceDao().all().forEach { a ->
            ws.value(r, 0, a.attendanceId)
            ws.value(r, 1, a.volunteerId)
            ws.value(r, 2, a.recordedAt.toLocalDate().toString())
            ws.value(r, 3, a.recordedAt.toLocalTime().format(timeFmt))
            ws.value(r, 4, a.adminUsername)
            ws.value(r, 5, a.notes ?: "")
            ws.value(r, 6, a.status.name)
            r++
        }
    }

    private suspend fun writeAdminsSheet(wb: Workbook) {
        val ws = wb.newWorksheet(ExcelSchema.Sheets.ADMINS)
        ws.rightToLeft()
        header(ws, ExcelSchema.Admins.ALL)
        var r = 1
        db.adminDao().allOnce().forEach { a ->
            ws.value(r, 0, a.adminId)
            ws.value(r, 1, a.fullName)
            ws.value(r, 2, a.username)
            ws.value(r, 3, a.passwordHash)     // BCrypt hash only — never plain text
            ws.value(r, 4, a.role.name)
            ws.value(r, 5, if (a.active) "TRUE" else "FALSE")
            ws.value(r, 6, a.createdAt.toString())
            ws.value(r, 7, a.lastLogin?.toString() ?: "")
            ws.value(r, 8, a.permissionsJson ?: "")
            r++
        }
    }

    private suspend fun writeAuditSheet(wb: Workbook) {
        val ws = wb.newWorksheet(ExcelSchema.Sheets.AUDIT_LOG)
        ws.rightToLeft()
        header(ws, ExcelSchema.AuditLog.ALL)
        var r = 1
        db.auditLogDao().all().forEach { log ->
            ws.value(r, 0, log.logId)
            ws.value(r, 1, log.timestamp.toLocalDate().toString())
            ws.value(r, 2, log.timestamp.toLocalTime().format(timeFmt))
            ws.value(r, 3, log.adminUsername)
            ws.value(r, 4, log.action.name)
            ws.value(r, 5, log.volunteerId ?: "")
            ws.value(r, 6, log.volunteerName ?: "")
            ws.value(r, 7, log.details)
            ws.value(r, 8, log.result.name)
            r++
        }
    }

    private suspend fun writeSettingsSheet(wb: Workbook) {
        val ws = wb.newWorksheet(ExcelSchema.Sheets.SETTINGS)
        ws.rightToLeft()
        header(ws, ExcelSchema.Settings.ALL)
        var r = 1
        db.settingsDao().all().forEach { s ->
            ws.value(r, 0, s.key)
            ws.value(r, 1, s.value)
            r++
        }
    }

    private fun writeBloodGroupsSheet(wb: Workbook) {
        val ws = wb.newWorksheet(ExcelSchema.Sheets.BLOOD_GROUPS)
        ws.value(0, 0, "BloodGroup")
        Validation.BLOOD_GROUPS.forEachIndexed { i, bg -> ws.value(i + 1, 0, bg) }
    }

    private suspend fun writeMetadataSheet(wb: Workbook, appVersion: String) {
        val ws = wb.newWorksheet(ExcelSchema.Sheets.METADATA)
        header(ws, listOf(ExcelSchema.Metadata.KEY, ExcelSchema.Metadata.VALUE))
        val rows = listOf(
            ExcelSchema.Metadata.DB_VERSION to
                (db.settingsDao().get(AppSetting.KEY_DB_VERSION) ?: AppSetting.CURRENT_DB_VERSION),
            ExcelSchema.Metadata.ORG_ID to (db.settingsDao().get(AppSetting.KEY_ORG_ID) ?: ""),
            ExcelSchema.Metadata.CREATED_AT to LocalDateTime.now().toString(),
            ExcelSchema.Metadata.UPDATED_AT to LocalDateTime.now().toString(),
            ExcelSchema.Metadata.APP_VERSION to appVersion
        )
        rows.forEachIndexed { i, (k, v) ->
            ws.value(i + 1, 0, k)
            ws.value(i + 1, 1, v)
        }
    }

    private fun header(ws: Worksheet, columns: List<String>) {
        columns.forEachIndexed { i, name ->
            ws.value(0, i, name)
            ws.style(0, i).bold().set()
        }
    }
}
