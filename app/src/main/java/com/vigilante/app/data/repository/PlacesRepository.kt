package com.vigilante.app.data.repository

import androidx.room.withTransaction
import com.vigilante.app.core.Validation
import com.vigilante.app.data.local.VigilanteDatabase
import com.vigilante.app.data.local.entity.AuditAction
import com.vigilante.app.data.local.entity.District
import com.vigilante.app.data.local.entity.Municipality
import com.vigilante.app.data.local.entity.Permission
import com.vigilante.app.security.Session
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** Admin-managed بلديات/أحياء lists. Reading is open; editing needs MANAGE_SETTINGS. */
@Singleton
class PlacesRepository @Inject constructor(
    private val db: VigilanteDatabase,
    private val audit: AuditLogger,
    private val session: Session
) {
    fun municipalities(): Flow<List<Municipality>> = db.placesDao().municipalities()
    fun districtsOf(municipalityId: Long): Flow<List<District>> =
        db.placesDao().districtsOf(municipalityId)

    suspend fun addMunicipality(name: String): Result<Unit> = runCatching {
        requireManage()
        val clean = Validation.normalizeName(name)
        require(clean.isNotBlank()) { "اسم البلدية فارغ" }
        db.withTransaction {
            check(db.placesDao().insertMunicipality(Municipality(name = clean)) > 0) {
                "البلدية موجودة مسبقًا"
            }
            audit.log(session.require().admin.username, AuditAction.EDIT_SETTINGS, "إضافة بلدية: $clean")
        }
    }

    suspend fun addDistrict(municipalityId: Long, name: String): Result<Unit> = runCatching {
        requireManage()
        val clean = Validation.normalizeName(name)
        require(clean.isNotBlank()) { "اسم الحي فارغ" }
        db.withTransaction {
            check(db.placesDao().insertDistrict(District(municipalityId = municipalityId, name = clean)) > 0) {
                "الحي موجود مسبقًا في هذه البلدية"
            }
            audit.log(session.require().admin.username, AuditAction.EDIT_SETTINGS, "إضافة حي: $clean")
        }
    }

    suspend fun deleteMunicipality(id: Long): Result<Unit> = runCatching {
        requireManage()
        db.withTransaction {
            db.placesDao().deleteMunicipality(id)
            audit.log(session.require().admin.username, AuditAction.EDIT_SETTINGS, "حذف بلدية")
        }
    }

    suspend fun deleteDistrict(id: Long): Result<Unit> = runCatching {
        requireManage()
        db.withTransaction {
            db.placesDao().deleteDistrict(id)
            audit.log(session.require().admin.username, AuditAction.EDIT_SETTINGS, "حذف حي")
        }
    }

    private fun requireManage() {
        check(session.has(Permission.MANAGE_SETTINGS)) { "لا تملك صلاحية إدارة الأماكن" }
    }
}
