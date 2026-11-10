package com.twinlore.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Lightweight projection of [RaGameCacheEntity] for the library hot path.
 * Avoids deserializing the heavy `jsonSnapshot` on every emit.
 */
data class RaCountsRow(
    val vaultId: String,
    val raGameId: Int,
    val numAwardedSoft: Int,
    val numAwardedHardcore: Int,
    val numAchievements: Int,
    val highestAwardKind: String?,
)

@Dao
interface RaGameCacheDao {
    @Query("SELECT * FROM ra_game_cache WHERE vaultId = :vaultId LIMIT 1")
    suspend fun getByVaultId(vaultId: String): RaGameCacheEntity?

    @Query("SELECT * FROM ra_game_cache")
    suspend fun getAll(): List<RaGameCacheEntity>

    @Query("DELETE FROM ra_game_cache WHERE vaultId = :vaultId")
    suspend fun deleteByVaultId(vaultId: String)

    @Query("DELETE FROM ra_game_cache")
    suspend fun clearAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: RaGameCacheEntity)

    @Query("""
        SELECT vaultId, raGameId, numAwardedSoft, numAwardedHardcore,
               numAchievements, highestAwardKind
          FROM ra_game_cache
    """)
    fun countsFlow(): Flow<List<RaCountsRow>>
}
