package com.vigilante.app.data.repository

import androidx.room.withTransaction
import com.vigilante.app.core.AppFolders
import com.vigilante.app.core.IdParser
import com.vigilante.app.data.files.QrStore
import com.vigilante.app.data.local.VigilanteDatabase
import com.vigilante.app.data.local.entity.AuditAction
import com.vigilante.app.security.Session
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

data class IntegrityReport(
    val checkedAt: LocalDateTime,
    val missingPhotos: List<String>,      // volunteers whose photoPath file is gone
    val missingQr: List<String>,          // volunteers without a QR file (auto-fixable)
    val orphanPhotos: List<String>,       // photo files with no volunteer
    val orphanQr: List<String>,           // QR files with no volunteer
    val orphanAttendance: List<String>,   // attendance rows pointing at nothing
    val invalidIds: List<String>,
    val lastBackup: String?,
    val masterFileExists: Boolean
) {
    val isClean: Boolean
        get() = missingPhotos.isEmpty() && missingQr.isEmpty() && orphanPhotos.isEmpty() &&
            orphanQr.isEmpty() && orphanAttendance.isEmpty() && invalidIds.isEmpty()
}

/**
 * SRS final additions — daily self-check: photos ↔ volunteers, QR ↔ volunteers,
 * attendance ↔ volunteers, ID validity, last backup, master file presence.
 * [autoFix] repairs what is safely repairable (recreating missing QR files).
 */
@Singleton
class IntegrityChecker @Inject constructor(
    private val db: VigilanteDatabase,
    private val folders: AppFolders,
    private val qrStore: QrStore,
    private val audit: AuditLogger,
    private val session: Session
) {
    suspend fun run(): IntegrityReport {
        folders.ensureAll()
        val volunteers = db.volunteerDao().all()
        val ids = volunteers.mapTo(HashSet()) { it.volunteerId }

        val missingPhotos = volunteers
            .filter { it.photoPath != null && !folders.photoFile(it.volunteerId).exists() }
            .map { it.volunteerId }
        val missingQr = volunteers
            .filter { !folders.qrFile(it.volunteerId).exists() }
            .map { it.volunteerId }
        val orphanPhotos = (folders.photos.listFiles() ?: emptyArray())
            .filter { it.extension.equals("jpg", true) && it.nameWithoutExtension !in ids }
            .map { it.name }
        val orphanQr = (folders.qr.listFiles() ?: emptyArray())
            .filter { it.extension.equals("png", true) && it.nameWithoutExtension !in ids }
            .map { it.name }
        val orphanAttendance = db.attendanceDao().all()
            .filter { it.volunteerId !in ids }
            .map { it.attendanceId }
        val invalidIds = volunteers
            .filter { !IdParser.isVolunteerId(it.volunteerId) }
            .map { it.volunteerId }

        return IntegrityReport(
            checkedAt = LocalDateTime.now(),
            missingPhotos = missingPhotos,
            missingQr = missingQr,
            orphanPhotos = orphanPhotos,
            orphanQr = orphanQr,
            orphanAttendance = orphanAttendance,
            invalidIds = invalidIds,
            lastBackup = db.backupDao().latest()?.fileName,
            masterFileExists = folders.masterFile.exists()
        )
    }

    /** Recreate missing QR files; clear dangling photoPath references. */
    suspend fun autoFix(report: IntegrityReport): Int {
        var fixed = 0
        report.missingQr.forEach { id ->
            qrStore.regenerate(id); fixed++
        }
        report.missingPhotos.forEach { id ->
            db.volunteerDao().byId(id)?.let {
                db.volunteerDao().update(it.copy(photoPath = null)); fixed++
            }
        }
        if (fixed > 0) {
            val actor = session.current.value?.admin?.username ?: "system"
            db.withTransaction {
                audit.log(actor, AuditAction.INTEGRITY_FIX, "إصلاح تلقائي لـ $fixed عنصرًا")
            }
        }
        return fixed
    }
}
