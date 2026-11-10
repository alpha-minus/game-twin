package com.twinlore.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MapMarkerDao {

    @Query("SELECT * FROM map_markers WHERE resource_file_id = :resourceFileId")
    fun getForResource(resourceFileId: String): Flow<List<MapMarkerEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: MapMarkerEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<MapMarkerEntity>)

    @Query("DELETE FROM map_markers WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM map_markers WHERE resource_file_id = :resourceFileId")
    suspend fun deleteForResource(resourceFileId: String)
}
