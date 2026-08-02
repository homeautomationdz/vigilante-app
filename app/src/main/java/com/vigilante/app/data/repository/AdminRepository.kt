package com.vigilante.app.data.repository

import androidx.room.withTransaction
import com.vigilante.app.core.EntityType
import com.vigilante.app.core.Validation
import com.vigilante.app.data.local.VigilanteDatabase
import com.vigilante.app.data.local.entity.Admin
import com.vigilante.app.data.local.entity.AdminRole
import com.vigilante.app.data.local.entity.AuditAction
import com.vigilante.app.data.local.entity.Permission
import com.vigilante.app.security.PasswordHasher
import com.vigilante.app.security.Session
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdminRepository @Inject constructor(
    private val db: VigilanteDatabase,
    private val audit: AuditLogger,
    private val session: Session
) {
    fun all(): Flow<List<Admin>> = db.adminDao().all()

    suspend fun add(
        fullName: String,
        username: String,
        password: String,
        role: AdminRole,
        permissionsJson: String? = null
    ): Result<Admin> = runCatching {
        requirePermission(Permission.MANAGE_ADMINS)
        require(Validation.isValidUsername(username)) { "اسم المستخدم غير صالح" }
        require(Validation.isValidPassword(password)) { "كلمة المرور ضعيفة" }
        val actor = session.require()
        db.withTransaction {
            check(db.adminDao().byUsername(username.trim()) == null) { "اسم المستخدم موجود مسبقًا" }
            val seq = db.idCounterDao().next(EntityType.ADMIN.name)
            val admin = Admin(
                adminId = EntityType.ADMIN.format(seq),
                fullName = fullName.trim(),
                username = username.trim(),
                passwordHash = PasswordHasher.hash(password),
                role = role,
                createdAt = LocalDateTime.now(),
                permissionsJson = permissionsJson
            )
            db.adminDao().insert(admin)
            audit.log(actor.admin.username, AuditAction.ADD_ADMIN, "إضافة مشرف: ${admin.username}")
            admin
        }
    }

    /** The last active Super Admin can never be disabled (SRS ch. 14). */
    suspend fun setActive(adminId: String, active: Boolean): Result<Unit> = runCatching {
        requirePermission(Permission.MANAGE_ADMINS)
        val actor = session.require()
        db.withTransaction {
            val target = db.adminDao().byId(adminId) ?: error("المشرف غير موجود")
            if (!active && target.role == AdminRole.SUPER_ADMIN &&
                db.adminDao().activeSuperAdminCount() <= 1
            ) error("لا يمكن تعطيل آخر مدير نظام")
            db.adminDao().update(target.copy(active = active))
            audit.log(
                actor.admin.username, AuditAction.DISABLE_ADMIN,
                (if (active) "تفعيل" else "تعطيل") + " حساب ${target.username}"
            )
        }
    }

    suspend fun resetPassword(adminId: String, newPassword: String): Result<Unit> = runCatching {
        requirePermission(Permission.MANAGE_ADMINS)
        require(Validation.isValidPassword(newPassword)) { "كلمة المرور ضعيفة" }
        val actor = session.require()
        db.withTransaction {
            val target = db.adminDao().byId(adminId) ?: error("المشرف غير موجود")
            db.adminDao().update(target.copy(passwordHash = PasswordHasher.hash(newPassword)))
            audit.log(actor.admin.username, AuditAction.RESET_PASSWORD, "إعادة تعيين كلمة مرور ${target.username}")
        }
    }

    suspend fun changeOwnPassword(current: String, newPassword: String): Result<Unit> = runCatching {
        val actor = session.require()
        require(Validation.isValidPassword(newPassword)) { "كلمة المرور ضعيفة" }
        db.withTransaction {
            val self = db.adminDao().byId(actor.admin.adminId) ?: error("الحساب غير موجود")
            check(PasswordHasher.verify(current, self.passwordHash)) { "كلمة المرور الحالية غير صحيحة" }
            db.adminDao().update(self.copy(passwordHash = PasswordHasher.hash(newPassword)))
            audit.log(actor.admin.username, AuditAction.CHANGE_PASSWORD, "تغيير كلمة المرور")
        }
    }

    suspend fun updateRoleAndPermissions(
        adminId: String,
        role: AdminRole,
        permissionsJson: String?
    ): Result<Unit> = runCatching {
        requirePermission(Permission.MANAGE_ADMINS)
        val actor = session.require()
        db.withTransaction {
            val target = db.adminDao().byId(adminId) ?: error("المشرف غير موجود")
            if (target.role == AdminRole.SUPER_ADMIN && role != AdminRole.SUPER_ADMIN &&
                db.adminDao().activeSuperAdminCount() <= 1
            ) error("لا يمكن تخفيض آخر مدير نظام")
            db.adminDao().update(target.copy(role = role, permissionsJson = permissionsJson))
            audit.log(actor.admin.username, AuditAction.EDIT_ADMIN, "تعديل صلاحيات ${target.username}")
        }
    }

    private fun requirePermission(permission: Permission) {
        check(session.has(permission)) { "لا تملك صلاحية هذه العملية" }
    }
}
