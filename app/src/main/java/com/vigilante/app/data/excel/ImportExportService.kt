package com.vigilante.app.data.excel

import androidx.room.withTransaction
import com.vigilante.app.core.AppFolders
import com.vigilante.app.core.EntityType
import com.vigilante.app.core.IdParser
import com.vigilante.app.data.local.VigilanteDatabase
import com.vigilante.app.data.local.entity.AppSetting
import com.vigilante.app.data.local.entity.AuditAction
import com.vigilante.app.data.local.entity.Permission
import com.vigilante.app.data.local.entity.Tag
import com.vigilante.app.data.local.entity.VolunteerTag
import com.vigilante.app.data.files.DownloadsWriter
import com.vigilante.app.data.repository.AuditLogger
import com.vigilante.app.security.Session
import java.io.File
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Result of an export: the internal encrypted file + its Downloads location. */
data class ExportOutcome(val file: File, val downloadsPath: String)

/**
 * Orchestrates the 9-step import flow (SRS ch. 50):
 * pick → verify file → verify DB version/org → BACKUP → analyze → report →
 * confirm (+ conflict decisions) → merge atomically → final report.
 */
@Singleton
class ImportExportService @Inject constructor(
    private val db: VigilanteDatabase,
    private val importer: ExcelImporter,
    private val exporter: ExcelExporter,
    private val merge: MergeEngine,
    private val backup: BackupManager,
    private val folders: AppFolders,
    private val audit: AuditLogger,
    private val session: Session,
    private val downloads: DownloadsWriter
) {
    suspend fun readImportFile(file: File): ImportReadResult {
        check(session.has(Permission.IMPORT_EXCEL)) { "لا تملك صلاحية الاستيراد" }
        val orgId = db.settingsDao().get(AppSetting.KEY_ORG_ID)
        // Password-protected export? Decrypt transparently — the supervisor
        // uploading the file never types the password (user decision).
        val readable = if (ExcelCrypto.isEncrypted(file)) {
            val password = db.settingsDao().get(AppSetting.KEY_EXCEL_PASSWORD)
                ?: return ImportReadResult.InvalidFile(
                    "الملف مشفر وكلمة مرور ملفات Excel غير محددة في الإعدادات"
                )
            runCatching { ExcelCrypto.decryptToTemp(file, password, folders.import) }
                .getOrElse { e ->
                    return ImportReadResult.InvalidFile(
                        e.message ?: "تعذر فك تشفير الملف — تحقق من كلمة المرور في الإعدادات"
                    )
                }
        } else file
        return try {
            importer.read(readable, orgId)
        } finally {
            if (readable !== file) readable.delete()
        }
    }

    suspend fun analyze(data: ImportedData): MergePlan = merge.plan(data)

    /**
     * Applies the confirmed plan. A fresh backup is taken FIRST (BR-008),
     * then all changes commit in one Room transaction.
     */
    suspend fun apply(
        plan: MergePlan,
        resolutions: Map<String, ConflictResolution>,
        appVersion: String
    ): Result<ImportReport> = runCatching {
        check(session.has(Permission.IMPORT_EXCEL)) { "لا تملك صلاحية الاستيراد" }
        val actor = session.require()
        val backupRecord = backup.create(reason = "BEFORE_IMPORT", appVersion = appVersion)

        var added = 0; var updated = 0; var ignored = 0

        db.withTransaction {
            for (item in plan.items) {
                when (item.kind) {
                    MergeDecisionKind.NEW -> {
                        db.volunteerDao().insert(item.incoming)
                        saveTags(item.incoming.volunteerId, item.incomingTags)
                        added++
                    }
                    MergeDecisionKind.AUTO_APPLY -> {
                        db.volunteerDao().update(item.incoming)
                        saveTags(item.incoming.volunteerId, item.incomingTags)
                        audit.log(
                            actor.admin.username, AuditAction.MERGE_DECISION,
                            "دمج تلقائي: ${item.reason}",
                            volunteerId = item.incoming.volunteerId,
                            volunteerName = item.incoming.displayName
                        )
                        updated++
                    }
                    MergeDecisionKind.AUTO_IGNORE -> {
                        audit.log(
                            actor.admin.username, AuditAction.MERGE_DECISION,
                            "تجاهل تلقائي: ${item.reason}",
                            volunteerId = item.incoming.volunteerId,
                            volunteerName = item.incoming.displayName
                        )
                        ignored++
                    }
                    MergeDecisionKind.CONFLICT -> {
                        when (resolutions[item.incoming.volunteerId]) {
                            ConflictResolution.TAKE_IMPORTED -> {
                                db.volunteerDao().update(item.incoming)
                                saveTags(item.incoming.volunteerId, item.incomingTags)
                                audit.log(
                                    actor.admin.username, AuditAction.MERGE_DECISION,
                                    "قرار المدير: اعتماد النسخة المستوردة",
                                    volunteerId = item.incoming.volunteerId,
                                    volunteerName = item.incoming.displayName
                                )
                                updated++
                            }
                            else -> {
                                audit.log(
                                    actor.admin.username, AuditAction.MERGE_DECISION,
                                    "قرار المدير: الاحتفاظ بالنسخة الحالية",
                                    volunteerId = item.incoming.volunteerId,
                                    volunteerName = item.incoming.displayName
                                )
                                ignored++
                            }
                        }
                    }
                    MergeDecisionKind.UNCHANGED -> { /* nothing */ }
                }
            }
            plan.newAttendance.forEach { db.attendanceDao().insert(it) }
            plan.newAdmins.forEach { db.adminDao().insert(it) }

            raiseCounters(plan)
            audit.log(
                actor.admin.username, AuditAction.IMPORT_EXCEL,
                "استيراد: $added إضافة، $updated تعديل، $ignored تجاهل، " +
                    "${plan.newAttendance.size} سجل حضور"
            )
        }
        ImportReport(
            added = added, updated = updated, ignored = ignored,
            attendanceAdded = plan.newAttendance.size,
            adminsAdded = plan.newAdmins.size,
            backupFileName = backupRecord.fileName
        )
    }

    /**
     * Export (SRS ch. 49 + user decisions): the exported file is encrypted with
     * the Excel password (Office-native — Excel prompts for it on a PC) and a
     * copy is placed in the phone's public Downloads folder. The internal copy
     * in Vigilante/Export/ is the same encrypted file.
     */
    suspend fun export(appVersion: String): Result<ExportOutcome> = runCatching {
        check(session.has(Permission.EXPORT_EXCEL)) { "لا تملك صلاحية التصدير" }
        val password = db.settingsDao().get(AppSetting.KEY_EXCEL_PASSWORD)
            ?.takeIf { it.isNotBlank() }
            ?: error("حدد أولًا كلمة مرور ملفات Excel من الإعدادات لحماية الملف المصدَّر")
        folders.ensureAll()
        var name = "Export_${LocalDate.now()}.xlsx"
        var target = File(folders.export, name)
        var i = 1
        while (target.exists()) {
            name = "Export_${LocalDate.now()}_$i.xlsx"; target = File(folders.export, name); i++
        }
        // 1) plain workbook to a temp file, 2) encrypt into Export/, 3) copy to Downloads
        val plain = File(folders.export, "$name.plain.tmp")
        try {
            exporter.exportAll(plain, appVersion)
            ExcelCrypto.encrypt(plain, target, password)
        } finally {
            plain.delete()
        }
        val downloadsPath = downloads.write(target, name)
        db.withTransaction {
            audit.log(
                session.require().admin.username, AuditAction.EXPORT_EXCEL,
                "تصدير مشفر: $name → مجلد التنزيلات"
            )
        }
        ExportOutcome(target, downloadsPath)
    }

    /** Rewrites the official master file (called after every mutating flow). */
    suspend fun syncMasterFile(appVersion: String): File =
        exporter.exportAll(folders.masterFile, appVersion)

    // ---- helpers ----

    private suspend fun saveTags(volunteerId: String, tagNames: List<String>) {
        db.tagDao().unlinkAll(volunteerId)
        tagNames.forEach { name ->
            val id = db.tagDao().tagIdByName(name)
                ?: db.tagDao().insertTag(Tag(name = name)).takeIf { it > 0 }
                ?: db.tagDao().tagIdByName(name) ?: return@forEach
            db.tagDao().link(VolunteerTag(volunteerId, id))
        }
    }

    /** Keep ID counters ahead of any imported IDs so new IDs never collide. */
    private suspend fun raiseCounters(plan: MergePlan) {
        val maxVol = plan.items.mapNotNull { IdParser.sequenceOf(it.incoming.volunteerId) }.maxOrNull()
        maxVol?.let { db.idCounterDao().raiseTo(EntityType.VOLUNTEER.name, it) }
        val maxAtt = plan.newAttendance.mapNotNull { IdParser.sequenceOf(it.attendanceId) }.maxOrNull()
        maxAtt?.let { db.idCounterDao().raiseTo(EntityType.ATTENDANCE.name, it) }
        val maxAdm = plan.newAdmins.mapNotNull { IdParser.sequenceOf(it.adminId) }.maxOrNull()
        maxAdm?.let { db.idCounterDao().raiseTo(EntityType.ADMIN.name, it) }
    }
}
