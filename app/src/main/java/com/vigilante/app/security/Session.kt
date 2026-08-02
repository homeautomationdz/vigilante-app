package com.vigilante.app.security

import com.vigilante.app.data.local.entity.Admin
import com.vigilante.app.data.local.entity.AdminRole
import com.vigilante.app.data.local.entity.Permission
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory session: current admin + resolved permissions + read-only mode.
 * The session auto-expires after the configured idle timeout (NFR-003).
 */
@Singleton
class Session @Inject constructor() {

    private val _current = MutableStateFlow<SessionState?>(null)
    val current: StateFlow<SessionState?> = _current

    var readOnlyMode: Boolean = false

    @Volatile private var lastActivity: LocalDateTime = LocalDateTime.MIN
    var timeoutMinutes: Long = 15

    fun open(admin: Admin) {
        _current.value = SessionState(admin, resolvePermissions(admin))
        touch()
    }

    fun close() { _current.value = null }

    fun touch() { lastActivity = LocalDateTime.now() }

    fun isExpired(): Boolean {
        val state = _current.value ?: return true
        return lastActivity.plusMinutes(timeoutMinutes).isBefore(LocalDateTime.now())
            .also { if (it) close() }
    }

    fun require(): SessionState =
        _current.value ?: error("No active session")

    fun has(permission: Permission): Boolean {
        if (readOnlyMode && permission.mutates()) return false
        return _current.value?.permissions?.contains(permission) == true
    }

    private fun Permission.mutates(): Boolean = this !in setOf(
        Permission.SEARCH, Permission.VIEW_STATS, Permission.VIEW_AUDIT_LOG
    )

    /** Role defaults + optional per-user JSON overrides (RBAC). */
    private fun resolvePermissions(admin: Admin): Set<Permission> {
        val base = when (admin.role) {
            AdminRole.SUPER_ADMIN -> Permission.SUPER_ADMIN_DEFAULTS
            AdminRole.ADMIN -> Permission.ADMIN_DEFAULTS
        }.toMutableSet()

        admin.permissionsJson?.takeIf { it.isNotBlank() }?.let { json ->
            runCatching {
                val obj = JSONObject(json)
                for (key in obj.keys()) {
                    val perm = runCatching { Permission.valueOf(key) }.getOrNull() ?: continue
                    if (obj.getBoolean(key)) base.add(perm) else base.remove(perm)
                }
            }
        }
        return base
    }
}

data class SessionState(
    val admin: Admin,
    val permissions: Set<Permission>
)
