package com.twinlore.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BrowserTabDao {
    @Query("SELECT * FROM browser_tabs WHERE vaultId = :vaultId ORDER BY lastAccessed DESC")
    fun observeForVault(vaultId: String): Flow<List<BrowserTabEntity>>

    @Query("SELECT * FROM browser_tabs WHERE vaultId = :vaultId ORDER BY lastAccessed DESC")
    suspend fun getForVaultSync(vaultId: String): List<BrowserTabEntity>

    @Query("SELECT * FROM browser_tabs WHERE vaultId = :vaultId ORDER BY lastAccessed DESC LIMIT 1")
    suspend fun getActive(vaultId: String): BrowserTabEntity?

    @Query("SELECT * FROM browser_tabs WHERE id = :id")
    suspend fun getById(id: String): BrowserTabEntity?

    @Query("SELECT COUNT(*) FROM browser_tabs WHERE vaultId = :vaultId")
    suspend fun getCountForVault(vaultId: String): Int

    @Query("SELECT MAX(tab_order) FROM browser_tabs WHERE vaultId = :vaultId")
    suspend fun getMaxOrder(vaultId: String): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tab: BrowserTabEntity)

    @Update
    suspend fun update(tab: BrowserTabEntity)

    @Query("DELETE FROM browser_tabs WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM browser_tabs WHERE vaultId = :vaultId")
    suspend fun deleteForVault(vaultId: String)

    @Query("UPDATE browser_tabs SET lastAccessed = :ts WHERE id = :id")
    suspend fun touchLastAccessed(id: String, ts: Long)

    @Query("UPDATE browser_tabs SET scrollY = :y WHERE id = :id")
    suspend fun updateScrollY(id: String, y: Int)

    @Query("UPDATE browser_tabs SET url = :url, title = :title WHERE id = :id")
    suspend fun updateUrl(id: String, url: String, title: String)

    @Query("UPDATE browser_tabs SET faviconBytes = :bytes WHERE id = :id")
    suspend fun updateFavicon(id: String, bytes: ByteArray?)

    @Query(
        "UPDATE browser_tabs " +
        "SET history_json = :json, history_index = :idx, url = :url, title = :title " +
        "WHERE id = :id"
    )
    suspend fun updateHistory(id: String, json: String, idx: Int, url: String, title: String)
}
