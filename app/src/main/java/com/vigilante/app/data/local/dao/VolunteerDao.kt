package com.vigilante.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Update
import androidx.sqlite.db.SupportSQLiteQuery
import com.vigilante.app.data.local.entity.Volunteer
import kotlinx.coroutines.flow.Flow

@Dao
interface VolunteerDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(volunteer: Volunteer)

    @Update
    suspend fun update(volunteer: Volunteer)

    @Query("SELECT * FROM volunteers WHERE volunteerId = :id")
    suspend fun byId(id: String): Volunteer?

    @Query("SELECT * FROM volunteers WHERE volunteerId = :id")
    fun byIdFlow(id: String): Flow<Volunteer?>

    @Query("SELECT * FROM volunteers WHERE membershipNumber = :number LIMIT 1")
    suspend fun byMembershipNumber(number: String): Volunteer?

    @Query("SELECT * FROM volunteers WHERE phone1 = :phone AND volunteerId != :excludeId LIMIT 1")
    suspend fun byPrimaryPhone(phone: String, excludeId: String = ""): Volunteer?

    /**
     * Incremental search (SRS ch. 9/37): partial match over id, membership
     * number, names, phones, municipality and district. Only ACTIVE unless
     * [includeArchived].
     */
    @Query(
        """
        SELECT * FROM volunteers
        WHERE (:includeArchived = 1 OR status = 'ACTIVE')
          AND (
            :query = '' OR
            volunteerId LIKE '%' || :query || '%' OR
            membershipNumber LIKE '%' || :query || '%' OR
            firstName LIKE '%' || :query || '%' OR
            lastName LIKE '%' || :query || '%' OR
            fatherName LIKE '%' || :query || '%' OR
            phone1 LIKE '%' || :query || '%' OR
            phone2 LIKE '%' || :query || '%' OR
            municipality LIKE '%' || :query || '%' OR
            district LIKE '%' || :query || '%'
          )
        ORDER BY firstName, lastName
        """
    )
    fun search(query: String, includeArchived: Boolean = false): Flow<List<Volunteer>>

    /** Composed filters are built dynamically — see VolunteerQueryBuilder. */
    @RawQuery(observedEntities = [Volunteer::class])
    fun filtered(query: SupportSQLiteQuery): Flow<List<Volunteer>>

    @Query("SELECT * FROM volunteers WHERE status = 'ARCHIVED' ORDER BY archiveDate DESC")
    fun archived(): Flow<List<Volunteer>>

    @Query("SELECT * FROM volunteers ORDER BY volunteerId")
    suspend fun all(): List<Volunteer>

    @Query("SELECT COUNT(*) FROM volunteers WHERE status = 'ACTIVE'")
    fun activeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM volunteers WHERE status = 'ACTIVE'")
    suspend fun activeCountOnce(): Int

    @Query("SELECT COUNT(*) FROM volunteers WHERE status = 'ARCHIVED'")
    suspend fun archivedCountOnce(): Int

    @Query("SELECT COUNT(*) FROM volunteers WHERE joinDate >= :fromIso")
    suspend fun joinedSince(fromIso: String): Int

    @Query("SELECT COUNT(*) FROM volunteers WHERE status = 'ARCHIVED'")
    fun archivedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM volunteers WHERE strftime('%Y', joinDate) = :year")
    fun joinedInYear(year: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM volunteers WHERE joinDate = :date")
    suspend fun joinedOn(date: String): Int

    @Query("SELECT bloodGroup, COUNT(*) as count FROM volunteers WHERE bloodGroup IS NOT NULL AND status = 'ACTIVE' GROUP BY bloodGroup")
    suspend fun countByBloodGroup(): List<GroupCount>

    @Query("SELECT municipality as bloodGroup, COUNT(*) as count FROM volunteers WHERE municipality IS NOT NULL AND status = 'ACTIVE' GROUP BY municipality ORDER BY count DESC")
    suspend fun countByMunicipality(): List<GroupCount>

    @Query("SELECT DISTINCT municipality FROM volunteers WHERE municipality IS NOT NULL ORDER BY municipality")
    suspend fun municipalities(): List<String>

    @Query("SELECT DISTINCT district FROM volunteers WHERE district IS NOT NULL ORDER BY district")
    suspend fun districts(): List<String>

    @Query("DELETE FROM volunteers WHERE volunteerId = :id")
    suspend fun deletePermanently(id: String)
}

data class GroupCount(val bloodGroup: String?, val count: Int)
