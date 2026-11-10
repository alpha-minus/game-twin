package com.twinlore.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface ChecklistTagDao {
    @Query("SELECT * FROM checklist_tags WHERE vaultId = :vaultId ORDER BY sortOrder ASC")
    suspend fun getTagsForVaultOnce(vaultId: String): List<ChecklistTagEntity>

    @Query("SELECT * FROM checklist_tags WHERE id = :id")
    suspend fun getTagById(id: String): ChecklistTagEntity?

    @Query("SELECT MAX(sortOrder) FROM checklist_tags WHERE vaultId = :vaultId")
    suspend fun getMaxSortOrder(vaultId: String): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tag: ChecklistTagEntity)

    @Update
    suspend fun update(tag: ChecklistTagEntity)

    @Query("DELETE FROM checklist_tags WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM checklist_tags WHERE vaultId = :vaultId")
    suspend fun deleteAllForVault(vaultId: String)
}
