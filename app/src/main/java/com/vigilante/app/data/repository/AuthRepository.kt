package com.vigilante.app.data.repository

import androidx.room.withTransaction
import com.vigilante.app.core.EntityType
import com.vigilante.app.core.Validation
import com.vigilante.app.data.local.VigilanteDatabase
import com.vigilante.app.data.local.entity.Admin
import com.vigilante.app.data.local.entity.AdminRole
import com.vigilante.app.data.local.entity.AppSetting
import com.vigilante.app.data.local.entity.AuditAction
import com.vigilante.app.data.local.entity.AuditResult
import com.vigilante.app.data.local.entity.LoginAttemptState
import com.vigilante.app.security.PasswordHasher
import com.vigilante.app.security.Session
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

sealed class LoginResult {
    data class Success(val admin: Admin) : LoginResult()
    data object WrongCredentials : LoginResult()
    data object AccountDisabled : LoginResult()
    data class Locked(val remainingSeconds: Long) : LoginResult()
}

@Singleton
class AuthRepository @Inject constructor(
    private val db: VigilanteDatabase,
    private val audit: AuditLogger,
    private val session: Session
) {
    suspend fun hasAnyAccount(): Boolean = db.adminDao().count() > 0

    /** First run (SRS ch. 3): create the Super Admin + organization identity. */
    suspend fun createFirstSuperAdmin(
        fullName: String,
        username: String,
        password: String
    ): Result<Admin> {
        if (!Validation.isValidUsername(username)) {
            return Result.failure(IllegalArgumentException("اسم المستخدم غير صالح"))
        }
        if (!Validation.isValidPassword(password)) {
            return Result.failure(IllegalArgumentException("كلمة المرور ضعيفة"))
        }
        return runCatching {
            db.withTransaction {
                check(db.adminDao().count() == 0) { "يوجد حسابات مسبقًا" }
                val seq = db.idCounterDao().next(EntityType.ADMIN.name)
                val admin = Admin(
                    adminId = EntityType.ADMIN.format(seq),
                    fullName = fullName.trim(),
                    username = username.trim(),
                    passwordHash = PasswordHasher.hash(password),
                    role = AdminRole.SUPER_ADMIN,
                    createdAt = LocalDateTime.now()
                )
                db.adminDao().insert(admin)
                // Organization ID (SRS ch. 34) — generated once, read-only after.
                db.settingsDao().put(
                    AppSetting(
                        AppSetting.KEY_ORG_ID,
                        "ORG-" + UUID.randomUUID().toString().take(8).uppercase()
                    )
                )
                db.settingsDao().put(
                    AppSetting(AppSetting.KEY_DB_VERSION, AppSetting.CURRENT_DB_VERSION)
                )
                audit.log(admin.username, AuditAction.ADD_ADMIN, "إنشاء حساب مدير النظام الأول")
                admin
            }
        }
    }

    /** SRS ch. 3: 5 consecutive failures → 30-second lock; failures audited. */
    suspend fun login(username: String, password: String): LoginResult {
        val name = username.trim()
        val now = LocalDateTime.now()
        val maxAttempts = settingInt(AppSetting.KEY_MAX_LOGIN_ATTEMPTS, 5)
        val lockSeconds = settingInt(AppSetting.KEY_LOCK_SECONDS, 30).toLong()

        val state = db.adminDao().attemptState(name)
        state?.lockedUntil?.let { until ->
            if (until.isAfter(now)) {
                return LoginResult.Locked(ChronoUnit.SECONDS.between(now, until).coerceAtLeast(1))
            }
        }

        val admin = db.adminDao().byUsername(name)
        val ok = admin != null && PasswordHasher.verify(password, admin.passwordHash)

        if (!ok) {
            val failures = (state?.consecutiveFailures ?: 0) + 1
            val lockedUntil = if (failures >= maxAttempts) now.plusSeconds(lockSeconds) else null
            db.adminDao().upsertAttemptState(LoginAttemptState(name, failures % maxAttempts.coerceAtLeast(1), lockedUntil))
            db.withTransaction {
                audit.log(name, AuditAction.LOGIN_FAILED, "محاولة دخول فاشلة", result = AuditResult.FAILURE)
            }
            return LoginResult.WrongCredentials
        }
        if (!admin!!.active) return LoginResult.AccountDisabled

        db.withTransaction {
            db.adminDao().clearAttempts(name)
            db.adminDao().update(admin.copy(lastLogin = now))
            audit.log(name, AuditAction.LOGIN, "تسجيل دخول")
        }
        session.timeoutMinutes = settingInt(AppSetting.KEY_SESSION_TIMEOUT_MINUTES, 15).toLong()
        session.open(admin.copy(lastLogin = now))
        return LoginResult.Success(admin)
    }

    suspend fun logout() {
        session.current.value?.let {
            db.withTransaction { audit.log(it.admin.username, AuditAction.LOGOUT, "تسجيل خروج") }
        }
        session.close()
    }

    private suspend fun settingInt(key: String, default: Int): Int =
        db.settingsDao().get(key)?.toIntOrNull() ?: default
}
