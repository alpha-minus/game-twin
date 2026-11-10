package com.twinlore.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ChecklistSectionDao {
    @Query("SELECT * FROM checklist_sections WHERE tabId = :tabId ORDER BY sortOrder ASC")
    fun getSectionsForTab(tabId: String): Flow<List<ChecklistSectionEntity>>

    @Query("SELECT * FROM checklist_sections WHERE tabId = :tabId ORDER BY sortOrder ASC")
    suspend fun getSectionsForTabOnce(tabId: String): List<ChecklistSectionEntity>

    @Query("SELECT * FROM checklist_sections WHERE vaultId = :vaultId ORDER BY sortOrder ASC")
    fun getSectionsForVault(vaultId: String): Flow<List<ChecklistSectionEntity>>

    @Query("SELECT * FROM checklist_sections WHERE id = :id")
    suspend fun getSectionById(id: String): ChecklistSectionEntity?

    @Query("SELECT MAX(sortOrder) FROM checklist_sections WHERE tabId = :tabId")
    suspend fun getMaxSortOrder(tabId: String): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(section: ChecklistSectionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sections: List<ChecklistSectionEntity>)

    @Update
    suspend fun update(section: ChecklistSectionEntity)

    @Query("UPDATE checklist_sections SET expanded = :expanded, updatedAt = :updatedAt WHERE tabId = :tabId")
    suspend fun setAllExpandedForTab(tabId: String, expanded: Boolean, updatedAt: Long)

    @Query("DELETE FROM checklist_sections WHERE id = :id")
    suspend fun deleteById(id: String)
}
