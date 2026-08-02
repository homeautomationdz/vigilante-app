package com.vigilante.app.data.repository

import com.vigilante.app.data.local.VigilanteDatabase
import com.vigilante.app.data.local.dao.GroupCount
import com.vigilante.app.data.local.dao.VolunteerAttendanceCount
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

data class DashboardStats(
    val totalActive: Int,
    val totalArchived: Int,
    val joinedThisYear: Int,
    val joinedThisMonth: Int,
    val attendanceToday: Int,
    val attendanceThisWeek: Int,
    val attendanceThisMonth: Int,
    val attendanceThisYear: Int,
    val attendanceTotal: Int,
    val byBloodGroup: List<GroupCount>,
    val byMunicipality: List<GroupCount>,
    val topAttendees: List<VolunteerAttendanceCount>,
    val lowestAttendees: List<VolunteerAttendanceCount>
)

@Singleton
class StatsRepository @Inject constructor(private val db: VigilanteDatabase) {

    fun activeCount(): Flow<Int> = db.volunteerDao().activeCount()
    fun archivedCount(): Flow<Int> = db.volunteerDao().archivedCount()
    fun attendanceToday(): Flow<Int> =
        db.attendanceDao().countOnDate(LocalDate.now().toString())
    fun joinedThisYear(): Flow<Int> =
        db.volunteerDao().joinedInYear(LocalDate.now().year.toString())

    suspend fun dashboard(): DashboardStats {
        val today = LocalDate.now()
        val startOfDay = today.atStartOfDay()
        val startOfWeek = today.minusDays(today.dayOfWeek.value.toLong() - 1).atStartOfDay()
        val startOfMonth = today.withDayOfMonth(1).atStartOfDay()
        val startOfYear = today.withDayOfYear(1).atStartOfDay()

        suspend fun since(t: LocalDateTime) = db.attendanceDao().countSince(t.toString())

        return DashboardStats(
            totalActive = db.volunteerDao().activeCountOnce(),
            totalArchived = db.volunteerDao().archivedCountOnce(),
            joinedThisYear = db.volunteerDao().joinedSince(today.withDayOfYear(1).toString()),
            joinedThisMonth = db.volunteerDao().joinedSince(today.withDayOfMonth(1).toString()),
            attendanceToday = since(startOfDay),
            attendanceThisWeek = since(startOfWeek),
            attendanceThisMonth = since(startOfMonth),
            attendanceThisYear = since(startOfYear),
            attendanceTotal = db.attendanceDao().totalCount(),
            byBloodGroup = db.volunteerDao().countByBloodGroup(),
            byMunicipality = db.volunteerDao().countByMunicipality(),
            topAttendees = db.attendanceDao().topAttendees(),
            lowestAttendees = db.attendanceDao().lowestAttendees()
        )
    }
}
