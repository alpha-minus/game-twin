package com.twinlore.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface VaultSyncStateDao {
    @Query("SELECT * FROM vault_sync_state")
    suspend fun getAll(): List<VaultSyncStateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<VaultSyncStateEntity>)

    @Query("DELETE FROM vault_sync_state WHERE vaultId IN (:vaultIds)")
    suspend fun deleteByVaultIds(vaultIds: List<String>)
}
