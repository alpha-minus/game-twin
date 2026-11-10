package com.twinlore.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ChecklistItemDao {
    @Query("SELECT * FROM checklist_items WHERE vaultId = :vaultId ORDER BY sortOrder ASC")
    fun getItemsForVault(vaultId: String): Flow<List<ChecklistItemEntity>>

    @Query("SELECT * FROM checklist_items WHERE vaultId = :vaultId ORDER BY sortOrder ASC")
    suspend fun getItemsForVaultOnce(vaultId: String): List<ChecklistItemEntity>

    @Query("SELECT * FROM checklist_items WHERE id = :id")
    suspend fun getItemById(id: String): ChecklistItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ChecklistItemEntity)

    @Update
    suspend fun update(item: ChecklistItemEntity)

    @Query("DELETE FROM checklist_items WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM checklist_items WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("SELECT * FROM checklist_items WHERE sectionId = :sectionId ORDER BY sortOrder ASC, createdAt ASC")
    fun getItemsForSection(sectionId: String): Flow<List<ChecklistItemEntity>>

    @Query("SELECT * FROM checklist_items WHERE sectionId = :sectionId ORDER BY sortOrder ASC, createdAt ASC")
    suspend fun getItemsForSectionOnce(sectionId: String): List<ChecklistItemEntity>

    @Query("SELECT * FROM checklist_items WHERE vaultId = :vaultId AND sectionId = :sectionId AND LOWER(title) = LOWER(:title) LIMIT 1")
    suspend fun findByTitleAndSection(vaultId: String, sectionId: String, title: String): ChecklistItemEntity?

    @Query("SELECT MAX(sortOrder) FROM checklist_items WHERE sectionId = :sectionId")
    suspend fun getMaxSortOrderForSection(sectionId: String): Int?
}
