package com.vigilante.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDateTime

/** Key/value app settings (SRS ch. 19 / Settings sheet). */
@Entity(tableName = "app_settings")
data class AppSetting(
    @PrimaryKey val key: String,
    val value: String
) {
    companion object {
        const val KEY_DB_VERSION = "database_version"
        const val KEY_ORG_ID = "organization_id"
        const val KEY_ORG_NAME = "organization_name"
        const val KEY_ORG_PHONE = "organization_phone"
        const val KEY_ORG_EMAIL = "organization_email"
        const val KEY_ORG_ADDRESS = "organization_address"
        const val KEY_LOCK_SECONDS = "login_lock_seconds"           // default 30
        const val KEY_MAX_LOGIN_ATTEMPTS = "max_login_attempts"     // default 5
        const val KEY_DUPLICATE_ATTENDANCE_SECONDS = "duplicate_attendance_seconds" // default 60
        const val KEY_SESSION_TIMEOUT_MINUTES = "session_timeout_minutes"           // default 15
        const val KEY_RECYCLE_BIN_DAYS = "recycle_bin_days"         // default 30
        const val KEY_READ_ONLY_MODE = "read_only_mode"             // "true"/"false"
        /** Password that protects exported .xlsx files (Office-native encryption).
         *  Never written into the exported Settings sheet. */
        const val KEY_EXCEL_PASSWORD = "excel_export_password"

        /** Encryption of exported files is OPT-IN ("true"/"false", default off):
         *  the app is already behind a login with per-admin passwords, so forcing
         *  a second password on every export only complicates the exchange. */
        const val KEY_ENCRYPT_EXPORTS = "encrypt_exports"
        const val KEY_MEMBERSHIP_YEAR_SEQ_PREFIX = "membership_seq_" // + year → last used seq

        const val CURRENT_DB_VERSION = "1.0"
    }
}

/** Tags (SRS final additions) — e.g. مصور، سائق، قائد فريق. */
@Entity(tableName = "tags", indices = [Index(value = ["name"], unique = true)])
data class Tag(
    @PrimaryKey(autoGenerate = true) val tagId: Long = 0,
    val name: String
)

@Entity(
    tableName = "volunteer_tags",
    primaryKeys = ["volunteerId", "tagId"],
    indices = [Index("tagId")]
)
data class VolunteerTag(
    val volunteerId: String,
    val tagId: Long
)

/**
 * Recycle bin (SRS ch. 20 suggestion): a permanent delete first parks the
 * full record here as JSON for [purgeAfter] days; only after that window
 * does it disappear for good.
 */
@Entity(tableName = "recycle_bin")
data class RecycleBinEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entityType: String,          // "VOLUNTEER"
    val entityId: String,            // VOL-000001
    val displayName: String,
    val payloadJson: String,         // full serialized snapshot (incl. attendance)
    val deletedAt: LocalDateTime,
    val deletedBy: String,
    val purgeAfter: LocalDateTime
)

/** Backup registry (BKP-000001) — SRS ch. 16. */
@Entity(tableName = "backups")
data class BackupRecord(
    @PrimaryKey val backupId: String,        // BKP-000001
    val fileName: String,                    // Backup_2026-08-02_14-30.xlsx
    val createdAt: LocalDateTime,
    val createdBy: String,
    val reason: String,                      // MANUAL / BEFORE_IMPORT / BEFORE_RESTORE / BEFORE_DELETE
    val volunteerCount: Int,
    val attendanceCount: Int,
    val adminCount: Int,
    val sizeBytes: Long
)

/**
 * Atomic ID counters — one row per [EntityType]; incremented inside the same
 * Room transaction as the insert so IDs are never skipped or duplicated even
 * if the device dies mid-operation (SRS: Atomic Operations).
 */
@Entity(tableName = "id_counters")
data class IdCounter(
    @PrimaryKey val entityType: String,
    val lastValue: Long
)

/** Login throttling state (5 failures → 30-second lock). */
@Entity(tableName = "login_attempts")
data class LoginAttemptState(
    @PrimaryKey val username: String,
    val consecutiveFailures: Int,
    val lockedUntil: LocalDateTime?
)
