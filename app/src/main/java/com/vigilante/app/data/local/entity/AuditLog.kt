package com.vigilante.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDateTime

enum class AuditAction {
    LOGIN, LOGOUT, LOGIN_FAILED,
    ADD_VOLUNTEER, EDIT_VOLUNTEER, ARCHIVE_VOLUNTEER, RESTORE_VOLUNTEER,
    PERMANENT_DELETE, RESTORE_FROM_RECYCLE_BIN,
    RECORD_ATTENDANCE, CANCEL_ATTENDANCE,
    ADD_ADMIN, EDIT_ADMIN, DISABLE_ADMIN, RESET_PASSWORD, CHANGE_PASSWORD,
    IMPORT_EXCEL, EXPORT_EXCEL, MERGE_DECISION,
    CREATE_BACKUP, RESTORE_BACKUP,
    EDIT_SETTINGS, INTEGRITY_FIX,
    RECOVERY_CODE_ISSUED, RECOVERY_USED, RECOVERY_FAILED
}

enum class AuditResult { SUCCESS, FAILURE }

/**
 * SRS ch. 15 — append-only audit log. Nothing here is ever updated or
 * deleted by normal app flows; only the Super Admin may export or prune it.
 */
@Entity(
    tableName = "audit_log",
    indices = [Index("timestamp"), Index("adminUsername"), Index("volunteerId"), Index("action")]
)
data class AuditLog(
    @PrimaryKey val logId: String,          // LOG-000001
    val timestamp: LocalDateTime,
    val adminUsername: String,
    val action: AuditAction,
    val volunteerId: String? = null,
    val volunteerName: String? = null,
    /** Human-readable details, e.g. "الهاتف: 0555… → 0666…". */
    val details: String,
    val result: AuditResult = AuditResult.SUCCESS
)
