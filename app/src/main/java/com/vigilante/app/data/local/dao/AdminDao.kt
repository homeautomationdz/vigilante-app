package com.vigilante.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.vigilante.app.data.local.entity.Admin
import com.vigilante.app.data.local.entity.LoginAttemptState
import kotlinx.coroutines.flow.Flow

@Dao
interface AdminDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(admin: Admin)

    @Update
    suspend fun update(admin: Admin)

    @Query("SELECT * FROM admins WHERE username = :username LIMIT 1")
    suspend fun byUsername(username: String): Admin?

    @Query("SELECT * FROM admins WHERE adminId = :id")
    suspend fun byId(id: String): Admin?

    @Query("SELECT * FROM admins ORDER BY createdAt")
    fun all(): Flow<List<Admin>>

    @Query("SELECT * FROM admins ORDER BY adminId")
    suspend fun allOnce(): List<Admin>

    @Query("SELECT COUNT(*) FROM admins")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM admins WHERE role = 'SUPER_ADMIN' AND active = 1")
    suspend fun activeSuperAdminCount(): Int

    // --- login throttling ---

    @Query("SELECT * FROM login_attempts WHERE username = :username")
    suspend fun attemptState(username: String): LoginAttemptState?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAttemptState(state: LoginAttemptState)

    @Query("DELETE FROM login_attempts WHERE username = :username")
    suspend fun clearAttempts(username: String)
}
