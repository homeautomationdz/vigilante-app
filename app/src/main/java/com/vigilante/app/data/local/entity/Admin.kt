package com.vigilante.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDateTime

enum class AdminRole { SUPER_ADMIN, ADMIN }

/**
 * SRS ch. 14 — admins. Passwords are stored ONLY as BCrypt hashes.
 * [permissionsJson] holds optional per-user RBAC overrides (SRS ch. 27
 * suggestion): a JSON map of Permission name -> Boolean that extends or
 * restricts the role's defaults, so new roles can be composed without
 * code changes.
 */
@Entity(
    tableName = "admins",
    indices = [Index(value = ["username"], unique = true)]
)
data class Admin(
    @PrimaryKey val adminId: String,        // ADM-000001
    val fullName: String,
    val username: String,
    val passwordHash: String,
    val role: AdminRole,
    val active: Boolean = true,
    val createdAt: LocalDateTime,
    val lastLogin: LocalDateTime? = null,
    val permissionsJson: String? = null
)

/** RBAC permission catalogue (SRS ch. 25). */
enum class Permission {
    ADD_VOLUNTEER,
    EDIT_VOLUNTEER,
    RECORD_ATTENDANCE,
    SEARCH,
    VIEW_STATS,
    ARCHIVE_VOLUNTEER,
    RESTORE_FROM_ARCHIVE,
    MANAGE_ADMINS,
    IMPORT_EXCEL,
    EXPORT_EXCEL,
    PERMANENT_DELETE,
    MANAGE_SETTINGS,
    RESTORE_BACKUP,
    VIEW_AUDIT_LOG;

    companion object {
        val ADMIN_DEFAULTS: Set<Permission> = setOf(
            ADD_VOLUNTEER, EDIT_VOLUNTEER, RECORD_ATTENDANCE,
            SEARCH, VIEW_STATS, ARCHIVE_VOLUNTEER
        )
        val SUPER_ADMIN_DEFAULTS: Set<Permission> = entries.toSet()
    }
}
