package com.twinlore.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for saved tab operations
 */
@Dao
interface SavedTabDao {
    /**
     * Get all tabs for a vault, ordered by order field
     */
    @Query("SELECT * FROM saved_tabs WHERE vaultId = :vaultId ORDER BY tab_order ASC, lastAccessed DESC")
    fun getTabsForVault(vaultId: String): Flow<List<SavedTabEntity>>
    
    /**
     * Get all tabs for a vault (suspend version)
     */
    @Query("SELECT * FROM saved_tabs WHERE vaultId = :vaultId ORDER BY tab_order ASC, lastAccessed DESC")
    suspend fun getTabsForVaultSync(vaultId: String): List<SavedTabEntity>
    
    /**
     * Get tab by ID
     */
    @Query("SELECT * FROM saved_tabs WHERE id = :id")
    suspend fun getTabById(id: String): SavedTabEntity?
    
    /**
     * Get favorite tab for a vault
     */
    @Query("SELECT * FROM saved_tabs WHERE vaultId = :vaultId AND isFavorite = 1 LIMIT 1")
    suspend fun getFavoriteTab(vaultId: String): SavedTabEntity?
    
    /**
     * Get tab marked as "Map" for a vault
     */
    @Query("SELECT * FROM saved_tabs WHERE vaultId = :vaultId AND isMap = 1 LIMIT 1")
    suspend fun getMapTab(vaultId: String): SavedTabEntity?
    
    /**
     * Get tab marked as "Current Content" for a vault
     */
    @Query("SELECT * FROM saved_tabs WHERE vaultId = :vaultId AND isCurrentContent = 1 LIMIT 1")
    suspend fun getCurrentContentTab(vaultId: String): SavedTabEntity?
    
    /**
     * Insert tab
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTab(tab: SavedTabEntity)
    
    /**
     * Update tab
     */
    @Update
    suspend fun updateTab(tab: SavedTabEntity)
    
    /**
     * Delete tab
     */
    @Delete
    suspend fun deleteTab(tab: SavedTabEntity)
    
    /**
     * Delete tab by ID
     */
    @Query("DELETE FROM saved_tabs WHERE id = :id")
    suspend fun deleteTabById(id: String)
    
    /**
     * Delete all tabs for a vault
     */
    @Query("DELETE FROM saved_tabs WHERE vaultId = :vaultId")
    suspend fun deleteTabsForVault(vaultId: String)

    @Query("UPDATE saved_tabs SET resource_id = :resourceId WHERE id = :tabId")
    suspend fun updateResourceId(tabId: String, resourceId: String?)

    @Query("DELETE FROM saved_tabs WHERE resource_id = :resourceId")
    suspend fun deleteByResourceId(resourceId: String)

    @Query("DELETE FROM saved_tabs WHERE resource_id IN (:ids)")
    suspend fun deleteByResourceIds(ids: List<String>)

    /**
     * Update last accessed timestamp
     */
    @Query("UPDATE saved_tabs SET lastAccessed = :lastAccessed WHERE id = :id")
    suspend fun updateLastAccessed(id: String, lastAccessed: java.util.Date)

    @Query("UPDATE saved_tabs SET group_active_entry_id = :entryId WHERE id = :groupTabId")
    suspend fun updateGroupActiveEntry(groupTabId: String, entryId: String?)
    
    /**
     * Clear favorite flag for all tabs in a vault
     */
    @Query("UPDATE saved_tabs SET isFavorite = 0 WHERE vaultId = :vaultId")
    suspend fun clearFavoriteForVault(vaultId: String)
    
    /**
     * Clear map flag for all tabs in a vault
     */
    @Query("UPDATE saved_tabs SET isMap = 0 WHERE vaultId = :vaultId")
    suspend fun clearMapForVault(vaultId: String)
    
    /**
     * Clear current content flag for all tabs in a vault
     */
    @Query("UPDATE saved_tabs SET isCurrentContent = 0 WHERE vaultId = :vaultId")
    suspend fun clearCurrentContentForVault(vaultId: String)
    
    /**
     * Get maximum order value for a vault
     */
    @Query("SELECT MAX(tab_order) FROM saved_tabs WHERE vaultId = :vaultId")
    suspend fun getMaxOrder(vaultId: String): Int?

    /**
     * Observe link-type bookmarks for a vault, sorted by recency.
     * Used to render the saved-links section on the landing page.
     */
    @Query("SELECT * FROM saved_tabs WHERE vaultId = :vaultId AND fileType = 'link' ORDER BY lastAccessed DESC")
    fun getLinkBookmarksForVault(vaultId: String): kotlinx.coroutines.flow.Flow<List<SavedTabEntity>>

    /**
     * Returns the subset of [urls] that already have a saved_tabs row in [vaultId].
     * Used by bulk-save dedup to mark already-saved chapters in the selection sheet
     * and to skip them in the save queue.
     */
    @Query("SELECT url FROM saved_tabs WHERE vaultId = :vaultId AND url IN (:urls)")
    suspend fun findUrlsForVault(vaultId: String, urls: List<String>): List<String>
}

