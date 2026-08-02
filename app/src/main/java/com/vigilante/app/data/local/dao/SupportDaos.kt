package com.vigilante.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.vigilante.app.data.local.entity.AppSetting
import com.vigilante.app.data.local.entity.AuditLog
import com.vigilante.app.data.local.entity.BackupRecord
import com.vigilante.app.data.local.entity.IdCounter
import com.vigilante.app.data.local.entity.RecycleBinEntry
import com.vigilante.app.data.local.entity.Tag
import com.vigilante.app.data.local.entity.VolunteerTag
import kotlinx.coroutines.flow.Flow

@Dao
interface AuditLogDao {
    @Insert suspend fun insert(log: AuditLog)

    @Query(
        """
        SELECT * FROM audit_log
        WHERE (:query = '' OR
               adminUsername LIKE '%' || :query || '%' OR
               action LIKE '%' || :query || '%' OR
               volunteerId LIKE '%' || :query || '%' OR
               volunteerName LIKE '%' || :query || '%' OR
               timestamp LIKE '%' || :query || '%')
        ORDER BY timestamp DESC LIMIT :limit
        """
    )
    fun search(query: String, limit: Int = 300): Flow<List<AuditLog>>

    @Query("SELECT * FROM audit_log WHERE volunteerId = :volunteerId ORDER BY timestamp ASC")
    suspend fun forVolunteer(volunteerId: String): List<AuditLog>

    @Query("SELECT * FROM audit_log ORDER BY logId")
    suspend fun all(): List<AuditLog>
}

@Dao
interface SettingsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(setting: AppSetting)

    @Query("SELECT value FROM app_settings WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Query("SELECT value FROM app_settings WHERE `key` = :key")
    fun observe(key: String): Flow<String?>

    @Query("SELECT * FROM app_settings")
    suspend fun all(): List<AppSetting>
}

@Dao
interface TagDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTag(tag: Tag): Long

    @Query("SELECT * FROM tags ORDER BY name")
    fun allTags(): Flow<List<Tag>>

    @Query("SELECT tagId FROM tags WHERE name = :name")
    suspend fun tagIdByName(name: String): Long?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun link(link: VolunteerTag)

    @Query("DELETE FROM volunteer_tags WHERE volunteerId = :volunteerId")
    suspend fun unlinkAll(volunteerId: String)

    @Query(
        """
        SELECT t.* FROM tags t
        JOIN volunteer_tags vt ON vt.tagId = t.tagId
        WHERE vt.volunteerId = :volunteerId ORDER BY t.name
        """
    )
    suspend fun tagsFor(volunteerId: String): List<Tag>

    @Query(
        """
        SELECT vt.volunteerId FROM volunteer_tags vt
        JOIN tags t ON t.tagId = vt.tagId WHERE t.name = :tagName
        """
    )
    suspend fun volunteerIdsWithTag(tagName: String): List<String>
}

@Dao
interface RecycleBinDao {
    @Insert suspend fun insert(entry: RecycleBinEntry)

    @Query("SELECT * FROM recycle_bin ORDER BY deletedAt DESC")
    fun all(): Flow<List<RecycleBinEntry>>

    @Query("SELECT * FROM recycle_bin WHERE id = :id")
    suspend fun byId(id: Long): RecycleBinEntry?

    @Query("DELETE FROM recycle_bin WHERE id = :id")
    suspend fun remove(id: Long)

    @Query("DELETE FROM recycle_bin WHERE purgeAfter < :nowIso")
    suspend fun purgeExpired(nowIso: String): Int
}

@Dao
interface BackupDao {
    @Insert suspend fun insert(record: BackupRecord)

    @Query("SELECT * FROM backups ORDER BY createdAt DESC")
    fun all(): Flow<List<BackupRecord>>

    @Query("SELECT * FROM backups ORDER BY createdAt DESC LIMIT 1")
    suspend fun latest(): BackupRecord?
}

@Dao
interface IdCounterDao {
    @Query("SELECT * FROM id_counters WHERE entityType = :type")
    suspend fun get(type: String): IdCounter?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(counter: IdCounter)

    /**
     * Atomically claim the next sequence value for [type].
     * MUST be called inside a Room transaction together with the insert that
     * consumes the ID, so a crash can never leave a claimed-but-unused gap
     * visible outside the transaction.
     */
    @androidx.room.Transaction
    suspend fun next(type: String): Long {
        val current = get(type)?.lastValue ?: 0L
        val next = current + 1
        put(IdCounter(type, next))
        return next
    }

    /** Used after import/merge to keep counters ahead of any imported IDs. */
    suspend fun raiseTo(type: String, minimum: Long) {
        val current = get(type)?.lastValue ?: 0L
        if (minimum > current) put(IdCounter(type, minimum))
    }
}
