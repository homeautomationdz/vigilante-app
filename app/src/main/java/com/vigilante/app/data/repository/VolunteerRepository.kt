package com.vigilante.app.data.repository

import androidx.room.withTransaction
import com.vigilante.app.core.EntityType
import com.vigilante.app.core.Validation
import com.vigilante.app.data.files.PhotoStore
import com.vigilante.app.data.files.QrStore
import com.vigilante.app.data.local.VigilanteDatabase
import com.vigilante.app.data.local.VolunteerFilter
import com.vigilante.app.data.local.VolunteerQueryBuilder
import com.vigilante.app.data.local.entity.AppSetting
import com.vigilante.app.data.local.entity.AuditAction
import com.vigilante.app.data.local.entity.IdCounter
import com.vigilante.app.data.local.entity.RecycleBinEntry
import com.vigilante.app.data.local.entity.Tag
import com.vigilante.app.data.local.entity.Volunteer
import com.vigilante.app.data.local.entity.VolunteerStatus
import com.vigilante.app.data.local.entity.VolunteerTag
import com.vigilante.app.security.Session
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VolunteerRepository @Inject constructor(
    private val db: VigilanteDatabase,
    private val audit: AuditLogger,
    private val session: Session,
    private val qrStore: QrStore,
    private val photoStore: PhotoStore
) {
    fun search(query: String): Flow<List<Volunteer>> = db.volunteerDao().search(query.trim())

    fun filtered(filter: VolunteerFilter): Flow<List<Volunteer>> =
        db.volunteerDao().filtered(VolunteerQueryBuilder.build(filter))

    fun byIdFlow(id: String): Flow<Volunteer?> = db.volunteerDao().byIdFlow(id)
    suspend fun byId(id: String): Volunteer? = db.volunteerDao().byId(id)
    fun archived(): Flow<List<Volunteer>> = db.volunteerDao().archived()

    suspend fun duplicatePhoneOwner(phone: String, excludeId: String = ""): Volunteer? =
        db.volunteerDao().byPrimaryPhone(phone.trim(), excludeId)

    /**
     * Atomic add (SRS: Atomic Operations): ID claim + membership number +
     * insert + tags + audit commit together or not at all. The QR file is
     * created after commit — a missing QR is always self-healed on read.
     */
    suspend fun add(draft: VolunteerDraft): Result<Volunteer> = runCatching {
        val actor = session.require()
        val now = LocalDateTime.now()
        val volunteer = db.withTransaction {
            val seq = db.idCounterDao().next(EntityType.VOLUNTEER.name)
            val id = EntityType.VOLUNTEER.format(seq)
            val membership = draft.membershipNumber?.trim()?.takeIf { it.isNotBlank() }
                ?: nextMembershipNumber(now.year)
            val v = draft.toEntity(
                volunteerId = id,
                membershipNumber = membership,
                createdBy = actor.admin.username,
                createdAt = now
            )
            db.volunteerDao().insert(v)
            saveTags(id, draft.tagNames)
            audit.log(
                actor.admin.username, AuditAction.ADD_VOLUNTEER,
                "إضافة متطوع جديد — رقم العضوية $membership",
                volunteerId = id, volunteerName = v.displayName
            )
            v
        }
        qrStore.ensureQr(volunteer.volunteerId)
        volunteer
    }

    /** Edit — every changed field is written to the audit log old→new (BR-004). */
    suspend fun update(updated: Volunteer, tagNames: List<String>): Result<Volunteer> = runCatching {
        val actor = session.require()
        db.withTransaction {
            val old = db.volunteerDao().byId(updated.volunteerId)
                ?: error("المتطوع غير موجود")
            val stamped = updated.copy(
                volunteerId = old.volunteerId,           // immutable
                createdBy = old.createdBy, createdAt = old.createdAt,
                updatedBy = actor.admin.username,
                updatedByRole = actor.admin.role.name,
                updatedAt = LocalDateTime.now()
            )
            db.volunteerDao().update(stamped)
            saveTags(stamped.volunteerId, tagNames)
            val changes = diff(old, stamped)
            if (changes.isNotEmpty()) {
                audit.log(
                    actor.admin.username, AuditAction.EDIT_VOLUNTEER,
                    changes.joinToString(" | "),
                    volunteerId = stamped.volunteerId, volunteerName = stamped.displayName
                )
            }
            stamped
        }
    }

    /** Archive instead of delete (BR-005, SRS ch. 17). */
    suspend fun archive(volunteerId: String, reason: String?): Result<Unit> = runCatching {
        val actor = session.require()
        db.withTransaction {
            val v = db.volunteerDao().byId(volunteerId) ?: error("المتطوع غير موجود")
            db.volunteerDao().update(
                v.copy(
                    status = VolunteerStatus.ARCHIVED,
                    archiveDate = LocalDateTime.now(),
                    archivedBy = actor.admin.username,
                    archiveReason = reason?.trim()?.takeIf { it.isNotBlank() }
                )
            )
            audit.log(
                actor.admin.username, AuditAction.ARCHIVE_VOLUNTEER,
                "أرشفة المتطوع" + (reason?.let { " — السبب: $it" } ?: ""),
                volunteerId = volunteerId, volunteerName = v.displayName
            )
        }
    }

    suspend fun restore(volunteerId: String): Result<Unit> = runCatching {
        val actor = session.require()
        db.withTransaction {
            val v = db.volunteerDao().byId(volunteerId) ?: error("المتطوع غير موجود")
            db.volunteerDao().update(
                v.copy(status = VolunteerStatus.ACTIVE, archiveDate = null, archivedBy = null, archiveReason = null)
            )
            audit.log(
                actor.admin.username, AuditAction.RESTORE_VOLUNTEER, "استعادة من الأرشيف",
                volunteerId = volunteerId, volunteerName = v.displayName
            )
        }
    }

    /**
     * "Permanent" delete (Super Admin only) → 30-day recycle bin first
     * (SRS ch. 20). The full record incl. attendance is snapshotted as JSON.
     */
    suspend fun moveToRecycleBin(volunteerId: String): Result<Unit> = runCatching {
        val actor = session.require()
        val days = db.settingsDao().get(AppSetting.KEY_RECYCLE_BIN_DAYS)?.toLongOrNull() ?: 30L
        db.withTransaction {
            val v = db.volunteerDao().byId(volunteerId) ?: error("المتطوع غير موجود")
            val attendance = db.attendanceDao().allForVolunteer(volunteerId)
            val tags = db.tagDao().tagsFor(volunteerId).map { it.name }
            val now = LocalDateTime.now()
            db.recycleBinDao().insert(
                RecycleBinEntry(
                    entityType = "VOLUNTEER",
                    entityId = volunteerId,
                    displayName = v.displayName,
                    payloadJson = VolunteerSnapshot.toJson(v, attendance.map { it }, tags),
                    deletedAt = now,
                    deletedBy = actor.admin.username,
                    purgeAfter = now.plusDays(days)
                )
            )
            db.tagDao().unlinkAll(volunteerId)
            db.attendanceDao().deleteForVolunteer(volunteerId)
            db.volunteerDao().deletePermanently(volunteerId)
            audit.log(
                actor.admin.username, AuditAction.PERMANENT_DELETE,
                "نقل إلى سلة المحذوفات (يُحذف نهائيًا بعد $days يومًا)",
                volunteerId = volunteerId, volunteerName = v.displayName
            )
        }
        photoStore.delete(volunteerId)
        qrStore.delete(volunteerId)
    }

    /** Volunteer timeline (SRS: صفحة المتطوع) — full audit history, oldest first. */
    suspend fun timeline(volunteerId: String): List<com.vigilante.app.data.local.entity.AuditLog> =
        db.auditLogDao().forVolunteer(volunteerId)

    /**
     * Restore a recycle-bin snapshot: volunteer + attendance + tags are
     * re-inserted in one transaction, then the entry is removed (SRS ch. 20).
     */
    suspend fun restoreFromRecycleBin(entryId: Long): Result<Unit> = runCatching {
        val actor = session.require()
        val restoredId = db.withTransaction {
            val entry = db.recycleBinDao().byId(entryId) ?: error("العنصر غير موجود")
            val obj = JSONObject(entry.payloadJson)
            val vo = obj.getJSONObject("volunteer")
            fun opt(o: JSONObject, key: String): String? =
                if (!o.has(key) || o.isNull(key)) null else o.getString(key)
            val volunteer = Volunteer(
                volunteerId = vo.getString("volunteerId"),
                membershipNumber = vo.getString("membershipNumber"),
                firstName = vo.getString("firstName"),
                lastName = vo.getString("lastName"),
                fatherName = vo.getString("fatherName"),
                birthDate = opt(vo, "birthDate")?.let(java.time.LocalDate::parse),
                joinDate = java.time.LocalDate.parse(vo.getString("joinDate")),
                municipality = opt(vo, "municipality"),
                district = opt(vo, "district"),
                bloodGroup = opt(vo, "bloodGroup"),
                phone1 = vo.getString("phone1"),
                phone2 = opt(vo, "phone2"),
                photoPath = opt(vo, "photoPath"),
                notes = opt(vo, "notes"),
                status = runCatching { VolunteerStatus.valueOf(vo.getString("status")) }
                    .getOrDefault(VolunteerStatus.ACTIVE),
                createdBy = vo.getString("createdBy"),
                createdAt = LocalDateTime.parse(vo.getString("createdAt"))
            )
            check(db.volunteerDao().byId(volunteer.volunteerId) == null) {
                "يوجد متطوع بنفس المعرف داخل قاعدة البيانات"
            }
            db.volunteerDao().insert(volunteer)
            val tagArr = obj.optJSONArray("tags")
            if (tagArr != null) {
                val names = mutableListOf<String>()
                for (i in 0 until tagArr.length()) names += tagArr.getString(i)
                saveTags(volunteer.volunteerId, names)
            }
            val attArr = obj.optJSONArray("attendance")
            if (attArr != null) {
                for (i in 0 until attArr.length()) {
                    val a = attArr.getJSONObject(i)
                    db.attendanceDao().insert(
                        com.vigilante.app.data.local.entity.Attendance(
                            attendanceId = a.getString("attendanceId"),
                            volunteerId = volunteer.volunteerId,
                            recordedAt = LocalDateTime.parse(a.getString("recordedAt")),
                            adminUsername = a.getString("adminUsername"),
                            notes = opt(a, "notes"),
                            status = runCatching {
                                com.vigilante.app.data.local.entity.AttendanceStatus.valueOf(a.getString("status"))
                            }.getOrDefault(com.vigilante.app.data.local.entity.AttendanceStatus.VALID)
                        )
                    )
                }
            }
            db.recycleBinDao().remove(entryId)
            audit.log(
                actor.admin.username, AuditAction.RESTORE_FROM_RECYCLE_BIN,
                "استعادة من سلة المحذوفات",
                volunteerId = volunteer.volunteerId, volunteerName = volunteer.displayName
            )
            volunteer.volunteerId
        }
        qrStore.ensureQr(restoredId)
        Unit
    }

    suspend fun tagsFor(volunteerId: String): List<Tag> = db.tagDao().tagsFor(volunteerId)
    fun allTags(): Flow<List<Tag>> = db.tagDao().allTags()
    suspend fun municipalities(): List<String> = db.volunteerDao().municipalities()
    suspend fun districts(): List<String> = db.volunteerDao().districts()
    suspend fun attendanceCount(volunteerId: String): Int =
        db.attendanceDao().countForVolunteer(volunteerId)

    // ---- internals ----

    private suspend fun saveTags(volunteerId: String, tagNames: List<String>) {
        db.tagDao().unlinkAll(volunteerId)
        tagNames.map { Validation.normalizeName(it) }.filter { it.isNotBlank() }.forEach { name ->
            val id = db.tagDao().tagIdByName(name)
                ?: db.tagDao().insertTag(Tag(name = name)).takeIf { it > 0 }
                ?: db.tagDao().tagIdByName(name)!!
            db.tagDao().link(VolunteerTag(volunteerId, id))
        }
    }

    /** Membership number pattern: YYYY-NNNN, sequence stored per year. */
    private suspend fun nextMembershipNumber(year: Int): String {
        val key = AppSetting.KEY_MEMBERSHIP_YEAR_SEQ_PREFIX + year
        val next = (db.settingsDao().get(key)?.toIntOrNull() ?: 0) + 1
        db.settingsDao().put(AppSetting(key, next.toString()))
        return "%d-%04d".format(year, next)
    }

    private fun diff(old: Volunteer, new: Volunteer): List<String> {
        val out = mutableListOf<String>()
        fun cmp(label: String, a: Any?, b: Any?) {
            if (a != b) out += "$label: ${a ?: "—"} → ${b ?: "—"}"
        }
        cmp("رقم العضوية", old.membershipNumber, new.membershipNumber)
        cmp("الاسم", old.firstName, new.firstName)
        cmp("اللقب", old.lastName, new.lastName)
        cmp("اسم الأب", old.fatherName, new.fatherName)
        cmp("تاريخ الميلاد", old.birthDate, new.birthDate)
        cmp("تاريخ الانضمام", old.joinDate, new.joinDate)
        cmp("البلدية", old.municipality, new.municipality)
        cmp("الحي", old.district, new.district)
        cmp("زمرة الدم", old.bloodGroup, new.bloodGroup)
        cmp("الهاتف الأساسي", old.phone1, new.phone1)
        cmp("الهاتف الثاني", old.phone2, new.phone2)
        cmp("الملاحظات", old.notes, new.notes)
        return out
    }
}

/** Input model for the add screen. */
data class VolunteerDraft(
    val membershipNumber: String? = null,
    val firstName: String,
    val lastName: String,
    val fatherName: String,
    val birthDate: java.time.LocalDate?,
    val joinDate: java.time.LocalDate,
    val municipality: String?,
    val district: String?,
    val bloodGroup: String?,
    val phone1: String,
    val phone2: String?,
    val photoPath: String?,
    val notes: String?,
    val tagNames: List<String> = emptyList()
) {
    fun toEntity(
        volunteerId: String,
        membershipNumber: String,
        createdBy: String,
        createdAt: LocalDateTime
    ) = Volunteer(
        volunteerId = volunteerId,
        membershipNumber = membershipNumber,
        firstName = Validation.normalizeName(firstName),
        lastName = Validation.normalizeName(lastName),
        fatherName = Validation.normalizeName(fatherName),
        birthDate = birthDate,
        joinDate = joinDate,
        municipality = municipality?.trim()?.takeIf { it.isNotBlank() },
        district = district?.trim()?.takeIf { it.isNotBlank() },
        bloodGroup = bloodGroup?.trim()?.takeIf { it.isNotBlank() },
        phone1 = phone1.trim(),
        phone2 = phone2?.trim()?.takeIf { it.isNotBlank() },
        photoPath = photoPath,
        notes = notes?.trim()?.takeIf { it.isNotBlank() },
        createdBy = createdBy,
        createdAt = createdAt
    )
}

/** JSON snapshot used by the recycle bin. */
object VolunteerSnapshot {
    fun toJson(
        v: Volunteer,
        attendance: List<com.vigilante.app.data.local.entity.Attendance>,
        tags: List<String>
    ): String {
        val obj = JSONObject()
        obj.put("volunteer", JSONObject().apply {
            put("volunteerId", v.volunteerId)
            put("membershipNumber", v.membershipNumber)
            put("firstName", v.firstName); put("lastName", v.lastName)
            put("fatherName", v.fatherName)
            put("birthDate", v.birthDate?.toString() ?: JSONObject.NULL)
            put("joinDate", v.joinDate.toString())
            put("municipality", v.municipality ?: JSONObject.NULL)
            put("district", v.district ?: JSONObject.NULL)
            put("bloodGroup", v.bloodGroup ?: JSONObject.NULL)
            put("phone1", v.phone1); put("phone2", v.phone2 ?: JSONObject.NULL)
            put("photoPath", v.photoPath ?: JSONObject.NULL)
            put("notes", v.notes ?: JSONObject.NULL)
            put("status", v.status.name)
            put("createdBy", v.createdBy); put("createdAt", v.createdAt.toString())
        })
        obj.put("tags", JSONArray(tags))
        obj.put("attendance", JSONArray().apply {
            attendance.forEach { a ->
                put(JSONObject().apply {
                    put("attendanceId", a.attendanceId)
                    put("recordedAt", a.recordedAt.toString())
                    put("adminUsername", a.adminUsername)
                    put("notes", a.notes ?: JSONObject.NULL)
                    put("status", a.status.name)
                })
            }
        })
        return obj.toString()
    }
}
