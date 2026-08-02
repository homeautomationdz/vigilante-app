package com.vigilante.app.data.repository

import androidx.room.withTransaction
import com.vigilante.app.data.local.VigilanteDatabase
import com.vigilante.app.data.local.entity.AppSetting
import com.vigilante.app.data.local.entity.AuditAction
import com.vigilante.app.data.local.entity.Permission
import com.vigilante.app.security.Session
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val db: VigilanteDatabase,
    private val audit: AuditLogger,
    private val session: Session
) {
    suspend fun get(key: String): String? = db.settingsDao().get(key)
    fun observe(key: String): Flow<String?> = db.settingsDao().observe(key)

    suspend fun set(key: String, value: String): Result<Unit> = runCatching {
        check(session.has(Permission.MANAGE_SETTINGS)) { "لا تملك صلاحية تعديل الإعدادات" }
        check(key != AppSetting.KEY_ORG_ID || db.settingsDao().get(key) == null) {
            "معرف الجمعية للقراءة فقط بعد الإنشاء"
        }
        val actor = session.require()
        db.withTransaction {
            db.settingsDao().put(AppSetting(key, value))
            audit.log(actor.admin.username, AuditAction.EDIT_SETTINGS, "تعديل إعداد: $key")
        }
    }

    suspend fun organizationId(): String? = get(AppSetting.KEY_ORG_ID)
}
