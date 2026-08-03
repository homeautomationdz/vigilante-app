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
import com.vigilante.app.security.RecoveryCode
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

/** The Super Admin plus the recovery code that must be shown once at setup. */
data class FirstRunResult(val admin: Admin, val recoveryCode: String)

sealed class RecoveryResult {
    /** Password reset; [newRecoveryCode] replaces the code just consumed. */
    data class Success(val newRecoveryCode: String) : RecoveryResult()
    data object Invalid : RecoveryResult()
    data object WeakPassword : RecoveryResult()
    data object NoRecoveryConfigured : RecoveryResult()
    data class Locked(val remainingSeconds: Long) : RecoveryResult()
}

private const val RECOVERY_THROTTLE_KEY = "__recovery__"

@Singleton
class AuthRepository @Inject constructor(
    private val db: VigilanteDatabase,
    private val audit: AuditLogger,
    private val session: Session
) {
    suspend fun hasAnyAccount(): Boolean = db.adminDao().count() > 0

    /**
     * First run (SRS ch. 3): create the Super Admin + organization identity,
     * and mint the one-time recovery code the setup screen must show.
     */
    suspend fun createFirstSuperAdmin(
        fullName: String,
        username: String,
        password: String
    ): Result<FirstRunResult> {
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
                val recoveryCode = RecoveryCode.generate()
                db.settingsDao().put(
                    AppSetting(AppSetting.KEY_RECOVERY_CODE_HASH, RecoveryCode.hash(recoveryCode))
                )
                audit.log(admin.username, AuditAction.ADD_ADMIN, "إنشاء حساب مدير النظام الأول")
                audit.log(admin.username, AuditAction.RECOVERY_CODE_ISSUED, "إصدار رمز استرجاع جديد")
                FirstRunResult(admin, recoveryCode)
            }
        }
    }

    // ---- account recovery ----

    suspend fun hasRecoveryCode(): Boolean =
        !db.settingsDao().get(AppSetting.KEY_RECOVERY_CODE_HASH).isNullOrBlank()

    /**
     * Issues a fresh code and invalidates the previous one. Available to a
     * signed-in Super Admin from settings, and used automatically after a
     * successful recovery so the owner is never left without one.
     */
    suspend fun regenerateRecoveryCode(): Result<String> = runCatching {
        val actor = session.require()
        check(actor.admin.role == AdminRole.SUPER_ADMIN) { "مدير النظام فقط يمكنه تجديد رمز الاسترجاع" }
        val code = RecoveryCode.generate()
        db.withTransaction {
            db.settingsDao().put(
                AppSetting(AppSetting.KEY_RECOVERY_CODE_HASH, RecoveryCode.hash(code))
            )
            audit.log(actor.admin.username, AuditAction.RECOVERY_CODE_ISSUED, "تجديد رمز الاسترجاع")
        }
        code
    }

    /**
     * Resets a Super Admin's password using the recovery code, then issues a
     * replacement code (returned) so the account stays recoverable.
     * Wrong attempts are throttled with the same lock as failed logins.
     */
    suspend fun recoverWithCode(
        username: String,
        code: String,
        newPassword: String
    ): RecoveryResult {
        val name = username.trim()
        val now = LocalDateTime.now()
        val maxAttempts = settingInt(AppSetting.KEY_MAX_LOGIN_ATTEMPTS, 5)
        val lockSeconds = settingInt(AppSetting.KEY_LOCK_SECONDS, 30).toLong()

        db.adminDao().attemptState(RECOVERY_THROTTLE_KEY)?.lockedUntil?.let { until ->
            if (until.isAfter(now)) {
                return RecoveryResult.Locked(
                    ChronoUnit.SECONDS.between(now, until).coerceAtLeast(1)
                )
            }
        }
        if (!Validation.isValidPassword(newPassword)) return RecoveryResult.WeakPassword

        val storedHash = db.settingsDao().get(AppSetting.KEY_RECOVERY_CODE_HASH)
            ?.takeIf { it.isNotBlank() }
            ?: return RecoveryResult.NoRecoveryConfigured

        val admin = db.adminDao().byUsername(name)
        val codeOk = RecoveryCode.verify(code, storedHash)
        // The account must exist AND be a Super Admin, but the failure message
        // stays generic so the screen cannot be used to enumerate accounts.
        if (!codeOk || admin == null || admin.role != AdminRole.SUPER_ADMIN) {
            val failures = (db.adminDao().attemptState(RECOVERY_THROTTLE_KEY)?.consecutiveFailures ?: 0) + 1
            val lockedUntil = if (failures >= maxAttempts) now.plusSeconds(lockSeconds) else null
            db.adminDao().upsertAttemptState(
                LoginAttemptState(
                    RECOVERY_THROTTLE_KEY,
                    failures % maxAttempts.coerceAtLeast(1),
                    lockedUntil
                )
            )
            db.withTransaction {
                audit.log(
                    name, AuditAction.RECOVERY_FAILED,
                    "محاولة استرجاع فاشلة", result = AuditResult.FAILURE
                )
            }
            return RecoveryResult.Invalid
        }

        val freshCode = RecoveryCode.generate()
        db.withTransaction {
            db.adminDao().update(admin.copy(passwordHash = PasswordHasher.hash(newPassword)))
            db.settingsDao().put(
                AppSetting(AppSetting.KEY_RECOVERY_CODE_HASH, RecoveryCode.hash(freshCode))
            )
            db.adminDao().clearAttempts(RECOVERY_THROTTLE_KEY)
            db.adminDao().clearAttempts(name)
            audit.log(name, AuditAction.RECOVERY_USED, "استرجاع الحساب برمز الاسترجاع")
            audit.log(name, AuditAction.RECOVERY_CODE_ISSUED, "إصدار رمز استرجاع بديل")
        }
        return RecoveryResult.Success(freshCode)
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
