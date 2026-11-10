package com.twinlore.app.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedMapDao {
    @Query("SELECT * FROM saved_maps WHERE vaultId = :vaultId ORDER BY createdAt DESC")
    fun getMapsByVaultId(vaultId: String): Flow<List<SavedMapEntity>>

    @Query("SELECT * FROM saved_maps WHERE vaultId = :vaultId ORDER BY createdAt DESC")
    suspend fun getMapsByVaultIdOnce(vaultId: String): List<SavedMapEntity>

    @Query("SELECT * FROM saved_maps WHERE id = :mapId")
    suspend fun getMapById(mapId: String): SavedMapEntity?

    @Query("SELECT COUNT(*) FROM saved_maps WHERE vaultId = :vaultId")
    suspend fun getMapCount(vaultId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMap(map: SavedMapEntity)

    @Update
    suspend fun updateMap(map: SavedMapEntity)

    @Query("DELETE FROM saved_maps WHERE id = :mapId")
    suspend fun deleteMap(mapId: String)

    @Query("DELETE FROM saved_maps WHERE vaultId = :vaultId")
    suspend fun deleteAllMapsForVault(vaultId: String)

    @Query("DELETE FROM saved_maps WHERE resource_id = :resourceId")
    suspend fun deleteByResourceId(resourceId: String)

    @Query("DELETE FROM saved_maps WHERE resource_id IN (:ids)")
    suspend fun deleteByResourceIds(ids: List<String>)

    @Query("UPDATE saved_maps SET lastAccessed = :date WHERE id = :mapId")
    suspend fun updateLastAccessed(mapId: String, date: java.util.Date)
}
