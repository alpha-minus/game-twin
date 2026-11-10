package com.twinlore.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ChecklistTabDao {
    @Query("SELECT * FROM checklist_tabs WHERE vaultId = :vaultId ORDER BY sortOrder ASC")
    fun getTabsForVault(vaultId: String): Flow<List<ChecklistTabEntity>>

    @Query("SELECT * FROM checklist_tabs WHERE vaultId = :vaultId ORDER BY sortOrder ASC")
    suspend fun getTabsForVaultOnce(vaultId: String): List<ChecklistTabEntity>

    @Query("SELECT * FROM checklist_tabs WHERE id = :id")
    suspend fun getTabById(id: String): ChecklistTabEntity?

    @Query("SELECT COUNT(*) FROM checklist_tabs WHERE vaultId = :vaultId")
    suspend fun countForVault(vaultId: String): Int

    @Query("SELECT MAX(sortOrder) FROM checklist_tabs WHERE vaultId = :vaultId")
    suspend fun getMaxSortOrder(vaultId: String): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tab: ChecklistTabEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tabs: List<ChecklistTabEntity>)

    @Update
    suspend fun update(tab: ChecklistTabEntity)

    @Query("DELETE FROM checklist_tabs WHERE id = :id")
    suspend fun deleteById(id: String)
}
