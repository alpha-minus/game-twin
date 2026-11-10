package com.twinlore.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SyncTombstoneDao {
    @Query("SELECT * FROM sync_tombstones")
    suspend fun getAll(): List<SyncTombstoneEntity>

    @Query("SELECT * FROM sync_tombstones WHERE vaultId = :vaultId")
    suspend fun getByVaultId(vaultId: String): SyncTombstoneEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(row: SyncTombstoneEntity)

    /**
     * Keep the newest deletedAt; an older or equal-timestamp incoming row is a
     * no-op (ties keep the existing writer's deviceId). Callers are serialized
     * by the library save mutex, so read-then-write is race-free here.
     */
    suspend fun upsert(row: SyncTombstoneEntity) {
        val existing = getByVaultId(row.vaultId)
        if (existing == null || row.deletedAt > existing.deletedAt) insertOrReplace(row)
    }

    @Query("DELETE FROM sync_tombstones WHERE deletedAt < :cutoffMillis")
    suspend fun pruneOlderThan(cutoffMillis: Long)
}
