package com.vigilante.app.data.excel

import androidx.room.withTransaction
import com.vigilante.app.core.AppFolders
import com.vigilante.app.core.EntityType
import com.vigilante.app.data.files.DownloadsWriter
import com.vigilante.app.data.local.VigilanteDatabase
import com.vigilante.app.data.local.entity.AppSetting
import com.vigilante.app.data.local.entity.AuditAction
import com.vigilante.app.data.local.entity.BackupRecord
import com.vigilante.app.data.repository.AuditLogger
import com.vigilante.app.security.Session
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where a manual backup ended up (user request: always show the location).
 * [internalPath] is the app-storage location shown to the user;
 * [downloadsPath] is set when an encrypted copy was also placed in the
 * phone's public Downloads folder (requires the Excel password setting).
 */
data class ManualBackupOutcome(
    val record: BackupRecord,
    val internalPath: String,
    val downloadsPath: String?
)

/**
 * SRS ch. 16 — Backup_YYYY-MM-DD_HH-MM.xlsx files, created manually or
 * automatically before every import / restore / permanent delete.
 */
@Singleton
class BackupManager @Inject constructor(
    private val db: VigilanteDatabase,
    private val exporter: ExcelExporter,
    private val folders: AppFolders,
    private val audit: AuditLogger,
    private val session: Session,
    private val downloads: DownloadsWriter
) {
    private val nameFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm")

    suspend fun create(reason: String, appVersion: String): BackupRecord {
        folders.ensureAll()
        val now = LocalDateTime.now()
        var fileName = "Backup_${now.format(nameFmt)}.xlsx"
        var target = File(folders.backup, fileName)
        var suffix = 1
        while (target.exists()) {
            fileName = "Backup_${now.format(nameFmt)}_$suffix.xlsx"
            target = File(folders.backup, fileName)
            suffix++
        }
        exporter.exportAll(target, appVersion)

        val actor = session.current.value?.admin?.username ?: "system"
        return db.withTransaction {
            val seq = db.idCounterDao().next(EntityType.BACKUP.name)
            val record = BackupRecord(
                backupId = EntityType.BACKUP.format(seq),
                fileName = fileName,
                createdAt = now,
                createdBy = actor,
                reason = reason,
                volunteerCount = db.volunteerDao().activeCountOnce() + db.volunteerDao().archivedCountOnce(),
                attendanceCount = db.attendanceDao().totalCount(),
                adminCount = db.adminDao().count(),
                sizeBytes = target.length()
            )
            db.backupDao().insert(record)
            audit.log(actor, AuditAction.CREATE_BACKUP, "نسخة احتياطية: $fileName — السبب: $reason")
            record
        }
    }

    fun backupFile(record: BackupRecord): File = File(folders.backup, record.fileName)

    /** User-visible location of a backup inside app storage. */
    fun internalPathOf(record: BackupRecord): String =
        "Android/data/com.vigilante.app/files/Vigilante/Backup/${record.fileName}"

    /**
     * Manual backup (user request): also drops an ENCRYPTED copy into the
     * phone's Downloads folder when the Excel password is configured, and
     * always reports exactly where the file(s) were saved.
     */
    suspend fun createManual(appVersion: String): ManualBackupOutcome {
        val record = create(reason = "MANUAL", appVersion = appVersion)
        val internal = backupFile(record)
        val password = db.settingsDao().get(AppSetting.KEY_EXCEL_PASSWORD)?.takeIf { it.isNotBlank() }
        val downloadsPath = if (password != null) {
            val encrypted = File(folders.backup, record.fileName + ".enc.tmp")
            try {
                ExcelCrypto.encrypt(internal, encrypted, password)
                downloads.write(encrypted, record.fileName)
            } finally {
                encrypted.delete()
            }
        } else null
        return ManualBackupOutcome(record, internalPathOf(record), downloadsPath)
    }
}
