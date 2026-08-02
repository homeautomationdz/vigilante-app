package com.vigilante.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.vigilante.app.data.local.entity.Attendance
import kotlinx.coroutines.flow.Flow

@Dao
interface AttendanceDao {

    @Insert
    suspend fun insert(attendance: Attendance)

    @Update
    suspend fun update(attendance: Attendance)

    @Query("SELECT * FROM attendance WHERE attendanceId = :id")
    suspend fun byId(id: String): Attendance?

    @Query("SELECT * FROM attendance WHERE volunteerId = :volunteerId AND status = 'VALID' ORDER BY recordedAt DESC")
    fun forVolunteer(volunteerId: String): Flow<List<Attendance>>

    @Query("SELECT COUNT(*) FROM attendance WHERE volunteerId = :volunteerId AND status = 'VALID'")
    suspend fun countForVolunteer(volunteerId: String): Int

    @Query("SELECT MAX(recordedAt) FROM attendance WHERE volunteerId = :volunteerId AND status = 'VALID'")
    suspend fun lastAttendance(volunteerId: String): String?

    @Query("SELECT MIN(recordedAt) FROM attendance WHERE volunteerId = :volunteerId AND status = 'VALID'")
    suspend fun firstAttendance(volunteerId: String): String?

    /** Duplicate guard (SRS ch. 7): most recent record within the window. */
    @Query(
        """
        SELECT * FROM attendance
        WHERE volunteerId = :volunteerId AND status = 'VALID' AND recordedAt >= :sinceIso
        ORDER BY recordedAt DESC LIMIT 1
        """
    )
    suspend fun recentWithin(volunteerId: String, sinceIso: String): Attendance?

    @Query(
        """
        SELECT a.* FROM attendance a
        JOIN volunteers v ON v.volunteerId = a.volunteerId
        WHERE (:query = '' OR
               v.firstName LIKE '%' || :query || '%' OR
               v.lastName LIKE '%' || :query || '%' OR
               a.adminUsername LIKE '%' || :query || '%' OR
               v.municipality LIKE '%' || :query || '%' OR
               a.recordedAt LIKE '%' || :query || '%')
        ORDER BY a.recordedAt DESC
        LIMIT :limit
        """
    )
    fun searchLog(query: String, limit: Int = 200): Flow<List<Attendance>>

    @Query("SELECT COUNT(*) FROM attendance WHERE status = 'VALID'")
    suspend fun totalCount(): Int

    @Query("SELECT COUNT(*) FROM attendance WHERE status = 'VALID' AND recordedAt >= :fromIso")
    suspend fun countSince(fromIso: String): Int

    @Query("SELECT COUNT(*) FROM attendance WHERE status = 'VALID' AND date(recordedAt) = :dateIso")
    fun countOnDate(dateIso: String): Flow<Int>

    @Query(
        """
        SELECT volunteerId, COUNT(*) as count FROM attendance
        WHERE status = 'VALID' GROUP BY volunteerId ORDER BY count DESC LIMIT :limit
        """
    )
    suspend fun topAttendees(limit: Int = 10): List<VolunteerAttendanceCount>

    @Query(
        """
        SELECT v.volunteerId, COUNT(a.attendanceId) as count FROM volunteers v
        LEFT JOIN attendance a ON a.volunteerId = v.volunteerId AND a.status = 'VALID'
        WHERE v.status = 'ACTIVE'
        GROUP BY v.volunteerId ORDER BY count ASC LIMIT :limit
        """
    )
    suspend fun lowestAttendees(limit: Int = 10): List<VolunteerAttendanceCount>

    @Query("SELECT * FROM attendance ORDER BY recordedAt DESC LIMIT :limit")
    fun recent(limit: Int = 50): Flow<List<Attendance>>

    @Query("SELECT * FROM attendance ORDER BY attendanceId")
    suspend fun all(): List<Attendance>

    @Query("SELECT * FROM attendance WHERE volunteerId = :volunteerId")
    suspend fun allForVolunteer(volunteerId: String): List<Attendance>

    @Query("DELETE FROM attendance WHERE volunteerId = :volunteerId")
    suspend fun deleteForVolunteer(volunteerId: String)
}

data class VolunteerAttendanceCount(val volunteerId: String, val count: Int)
