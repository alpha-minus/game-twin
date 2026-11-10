package com.twinlore.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class SteamCountsRow(
    val vaultId: String,
    val steamAppId: Int,
    val numUnlocked: Int,
    val numAchievements: Int,
)

@Dao
interface SteamGameCacheDao {
    @Query("SELECT * FROM steam_game_cache WHERE vaultId = :vaultId LIMIT 1")
    suspend fun getByVaultId(vaultId: String): SteamGameCacheEntity?

    @Query("SELECT * FROM steam_game_cache")
    suspend fun getAll(): List<SteamGameCacheEntity>

    @Query("DELETE FROM steam_game_cache WHERE vaultId = :vaultId")
    suspend fun deleteByVaultId(vaultId: String)

    @Query("DELETE FROM steam_game_cache")
    suspend fun clearAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SteamGameCacheEntity)

    @Query("""
        SELECT vaultId, steamAppId, numUnlocked, numAchievements
          FROM steam_game_cache
    """)
    fun countsFlow(): Flow<List<SteamCountsRow>>
}
