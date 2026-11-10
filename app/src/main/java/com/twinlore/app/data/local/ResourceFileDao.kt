package com.twinlore.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ResourceFileDao {

    @Query("SELECT * FROM resource_files WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ResourceFileEntity?

    @Query("SELECT * FROM resource_files WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<ResourceFileEntity>

    @Query("SELECT * FROM resource_files WHERE vault_id = :vaultId")
    fun getForVault(vaultId: String): Flow<List<ResourceFileEntity>>

    @Query("SELECT * FROM resource_files WHERE vault_id = :vaultId")
    suspend fun getForVaultOnce(vaultId: String): List<ResourceFileEntity>

    @Query("SELECT * FROM resource_files")
    suspend fun getAllOnce(): List<ResourceFileEntity>

    @Query("SELECT * FROM resource_files WHERE vault_id = :vaultId AND kind = :kind")
    fun getForVaultAndKind(vaultId: String, kind: String): Flow<List<ResourceFileEntity>>

    @Query("SELECT * FROM resource_files WHERE vault_id = :vaultId AND kind = :kind")
    suspend fun getForVaultAndKindOnce(vaultId: String, kind: String): List<ResourceFileEntity>

    @Query("SELECT * FROM resource_files WHERE vault_id = :vaultId AND filename = :filename AND size_bytes = :sizeBytes LIMIT 1")
    suspend fun findByVaultFilenameAndSize(vaultId: String, filename: String, sizeBytes: Long): ResourceFileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ResourceFileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<ResourceFileEntity>)

    @Update
    suspend fun update(entity: ResourceFileEntity)

    @Query("UPDATE resource_files SET last_seen_at = :now WHERE id = :id")
    suspend fun touchLastSeen(id: String, now: Long)

    @Delete
    suspend fun delete(entity: ResourceFileEntity)

    @Query("DELETE FROM resource_files WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM resource_files WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)
}
