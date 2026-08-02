package com.vigilante.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.vigilante.app.data.local.entity.District
import com.vigilante.app.data.local.entity.Municipality
import kotlinx.coroutines.flow.Flow

@Dao
interface PlacesDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMunicipality(m: Municipality): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDistrict(d: District): Long

    @Query("SELECT * FROM municipalities ORDER BY name")
    fun municipalities(): Flow<List<Municipality>>

    @Query("SELECT * FROM districts WHERE municipalityId = :municipalityId ORDER BY name")
    fun districtsOf(municipalityId: Long): Flow<List<District>>

    @Query("SELECT * FROM districts ORDER BY name")
    fun allDistricts(): Flow<List<District>>

    @Query("SELECT * FROM municipalities WHERE name = :name LIMIT 1")
    suspend fun municipalityByName(name: String): Municipality?

    @Query("DELETE FROM municipalities WHERE id = :id")
    suspend fun deleteMunicipality(id: Long)

    @Query("DELETE FROM districts WHERE id = :id")
    suspend fun deleteDistrict(id: Long)
}
