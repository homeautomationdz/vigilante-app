package com.vigilante.app.data.repository

import androidx.room.withTransaction
import com.vigilante.app.core.EntityType
import com.vigilante.app.data.local.VigilanteDatabase
import com.vigilante.app.data.local.dao.RollCallRow
import com.vigilante.app.data.local.entity.AppSetting
import com.vigilante.app.data.local.entity.Attendance
import com.vigilante.app.data.local.entity.AttendanceStatus
import com.vigilante.app.data.local.entity.AuditAction
import com.vigilante.app.data.local.entity.Volunteer
import com.vigilante.app.data.local.entity.VolunteerStatus
import com.vigilante.app.security.Session
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

sealed class AttendanceOutcome {
    data class Recorded(val attendance: Attendance, val volunteer: Volunteer) : AttendanceOutcome()
    data class Duplicate(val secondsAgo: Long, val volunteer: Volunteer) : AttendanceOutcome()
    data object VolunteerNotFound : AttendanceOutcome()
    data class VolunteerArchived(val volunteer: Volunteer) : AttendanceOutcome()
}

@Singleton
class AttendanceRepository @Inject constructor(
    private val db: VigilanteDatabase,
    private val audit: AuditLogger,
    private val session: Session
) {
    fun recent(limit: Int = 50): Flow<List<Attendance>> = db.attendanceDao().recent(limit)
    fun searchLog(query: String): Flow<List<Attendance>> = db.attendanceDao().searchLog(query.trim())
    fun forVolunteer(volunteerId: String): Flow<List<Attendance>> =
        db.attendanceDao().forVolunteer(volunteerId)

    /** Look up a scanned QR value (SRS ch. 8). */
    suspend fun lookup(volunteerId: String): AttendanceOutcome {
        val v = db.volunteerDao().byId(volunteerId.trim())
            ?: return AttendanceOutcome.VolunteerNotFound
        if (v.status == VolunteerStatus.ARCHIVED) return AttendanceOutcome.VolunteerArchived(v)
        return AttendanceOutcome.Recorded(
            Attendance("", v.volunteerId, LocalDateTime.now(), ""), v
        )
    }

    /**
     * Record attendance with the duplicate window guard (SRS ch. 7).
     * Insert + counter + audit are one atomic transaction.
     */
    suspend fun record(volunteerId: String, notes: String? = null): AttendanceOutcome {
        val actor = session.require()
        val now = LocalDateTime.now()
        val windowSeconds =
            db.settingsDao().get(AppSetting.KEY_DUPLICATE_ATTENDANCE_SECONDS)?.toLongOrNull() ?: 60L

        return db.withTransaction {
            val v = db.volunteerDao().byId(volunteerId.trim())
                ?: return@withTransaction AttendanceOutcome.VolunteerNotFound
            if (v.status == VolunteerStatus.ARCHIVED) {
                return@withTransaction AttendanceOutcome.VolunteerArchived(v)
            }
            val since = now.minusSeconds(windowSeconds).toString()
            db.attendanceDao().recentWithin(v.volunteerId, since)?.let { recentRec ->
                val ago = java.time.temporal.ChronoUnit.SECONDS.between(recentRec.recordedAt, now)
                return@withTransaction AttendanceOutcome.Duplicate(ago, v)
            }
            val seq = db.idCounterDao().next(EntityType.ATTENDANCE.name)
            val record = Attendance(
                attendanceId = EntityType.ATTENDANCE.format(seq),
                volunteerId = v.volunteerId,
                recordedAt = now,
                adminUsername = actor.admin.username,
                notes = notes?.trim()?.takeIf { it.isNotBlank() }
            )
            db.attendanceDao().insert(record)
            audit.log(
                actor.admin.username, AuditAction.RECORD_ATTENDANCE, "تسجيل حضور",
                volunteerId = v.volunteerId, volunteerName = v.displayName
            )
            AttendanceOutcome.Recorded(record, v)
        }
    }

    /** BR-007 — logical cancellation instead of delete. */
    suspend fun cancel(attendanceId: String, note: String): Result<Unit> = runCatching {
        val actor = session.require()
        db.withTransaction {
            val a = db.attendanceDao().byId(attendanceId) ?: error("السجل غير موجود")
            db.attendanceDao().update(
                a.copy(status = AttendanceStatus.CANCELLED, notes = note.ifBlank { "إلغاء منطقي" })
            )
            audit.log(
                actor.admin.username, AuditAction.CANCEL_ATTENDANCE,
                "إلغاء منطقي لسجل حضور $attendanceId", volunteerId = a.volunteerId
            )
        }
    }

    // ---- roll call (نداء الحضور) ----

    /**
     * One-shot snapshot for the roll-call screen. Deliberately NOT a Flow:
     * the order must stay frozen while the supervisor taps down the list,
     * otherwise rows would jump under their finger as scores change.
     */
    suspend fun rollCall(recentWindowDays: Long = 60): List<RollCallRow> {
        val today = LocalDate.now()
        return db.attendanceDao().rollCall(
            sinceIso = today.minusDays(recentWindowDays).atStartOfDay().toString(),
            todayIso = today.toString()
        )
    }

    /** Marks present; an existing record for today counts as success. */
    suspend fun markPresent(volunteerId: String): Result<Unit> = runCatching {
        when (val outcome = record(volunteerId)) {
            is AttendanceOutcome.Recorded -> Unit
            is AttendanceOutcome.Duplicate -> Unit          // already marked moments ago
            is AttendanceOutcome.VolunteerArchived -> error("هذا المتطوع مؤرشف")
            AttendanceOutcome.VolunteerNotFound -> error("المتطوع غير موجود")
        }
    }

    /**
     * Undo a mis-tap: today's record is logically cancelled (BR-007 — attendance
     * rows are never deleted), which also frees the duplicate-window guard so
     * the volunteer can be marked present again straight away.
     */
    suspend fun unmarkToday(volunteerId: String): Result<Unit> = runCatching {
        val today = LocalDate.now().toString()
        val record = db.attendanceDao().todayRecordFor(volunteerId, today)
            ?: return@runCatching
        cancel(record.attendanceId, "إلغاء من نداء الحضور — تم التأشير بالخطأ").getOrThrow()
    }

    data class VolunteerAttendanceSummary(
        val count: Int,
        val first: LocalDateTime?,
        val last: LocalDateTime?
    )

    suspend fun summaryFor(volunteerId: String): VolunteerAttendanceSummary =
        VolunteerAttendanceSummary(
            count = db.attendanceDao().countForVolunteer(volunteerId),
            first = db.attendanceDao().firstAttendance(volunteerId)?.let(LocalDateTime::parse),
            last = db.attendanceDao().lastAttendance(volunteerId)?.let(LocalDateTime::parse)
        )
}
