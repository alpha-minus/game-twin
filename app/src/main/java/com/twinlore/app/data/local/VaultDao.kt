package com.twinlore.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.util.Date

/**
 * Data Access Object for vault operations
 */
@Dao
interface VaultDao {
    /**
     * Get all vaults sorted by creation date (descending) for stable grid/carousel ordering.
     * Last-opened timestamp is tracked separately for focus positioning.
     */
    @Query("SELECT * FROM vaults ORDER BY createdAt DESC")
    fun getAllVaultsSortedByLastOpened(): Flow<List<VaultEntity>>
    
    /**
     * Get all vaults (unsorted)
     */
    @Query("SELECT * FROM vaults")
    suspend fun getAllVaults(): List<VaultEntity>
    
    /**
     * Get vault by ID
     */
    @Query("SELECT * FROM vaults WHERE id = :id")
    suspend fun getVaultById(id: String): VaultEntity?
    
    /**
     * Get vault by ID as Flow
     */
    @Query("SELECT * FROM vaults WHERE id = :id")
    fun getVaultByIdFlow(id: String): Flow<VaultEntity?>
    
    /**
     * Find vault by ROM path (for identity resolution)
     */
    @Query("SELECT * FROM vaults WHERE romPath = :romPath LIMIT 1")
    suspend fun findVaultByRomPath(romPath: String): VaultEntity?
    
    /**
     * Find vault by ROM filename (for identity resolution)
     */
    @Query("SELECT * FROM vaults WHERE romFilename = :romFilename LIMIT 1")
    suspend fun findVaultByRomFilename(romFilename: String): VaultEntity?

    /**
     * Find vault by RetroAchievements game ID (for last-played sync from RA API)
     */
    @Query("SELECT * FROM vaults WHERE raGameId = :raGameId LIMIT 1")
    suspend fun findVaultByRaGameId(raGameId: String): VaultEntity?

    /**
     * Find vault by Steam App ID (for last-played sync from Steam API)
     */
    @Query("SELECT * FROM vaults WHERE steamAppId = :steamAppId LIMIT 1")
    suspend fun findVaultBySteamAppId(steamAppId: String): VaultEntity?

    /**
     * Insert vault
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVault(vault: VaultEntity)
    
    /**
     * Insert multiple vaults
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVaults(vaults: List<VaultEntity>)
    
    /**
     * Update vault
     */
    @Update
    suspend fun updateVault(vault: VaultEntity)
    
    /**
     * Delete vault
     */
    @Delete
    suspend fun deleteVault(vault: VaultEntity)
    
    /**
     * Delete vault by ID
     */
    @Query("DELETE FROM vaults WHERE id = :id")
    suspend fun deleteVaultById(id: String)
    
    /**
     * Update last opened timestamp
     */
    @Query("UPDATE vaults SET lastOpened = :lastOpened WHERE id = :id")
    suspend fun updateLastOpened(id: String, lastOpened: Date)

    /**
     * Update artwork vibrant color (ARGB) for a vault. Used after artwork assign so focus/glow color is persisted. Pass null to clear.
     */
    @Query("UPDATE vaults SET artworkVibrantArgb = :argb WHERE id = :id")
    suspend fun updateArtworkVibrantArgb(id: String, argb: Int?)

    @Query("UPDATE vaults SET artwork_resource_id = :resourceId WHERE id = :id")
    suspend fun updateArtworkResourceId(id: String, resourceId: String?)

    @Query("UPDATE vaults SET map_resource_id = :resourceId WHERE id = :id")
    suspend fun updateMapResourceId(id: String, resourceId: String?)

    @Query("UPDATE vaults SET guide_resource_id = :resourceId WHERE id = :id")
    suspend fun updateGuideResourceId(id: String, resourceId: String?)

    /**
     * Atomically null-out any vault slot (artwork, guide, map) that points at
     * [resourceId]. Used by media-bind cleanup when a search path is removed
     * or disabled and its resource_files rows are being deleted.
     */
    @androidx.room.Transaction
    suspend fun clearAnySlotPointingAt(resourceId: String) {
        clearArtworkSlot(resourceId)
        clearGuideSlot(resourceId)
        clearMapSlot(resourceId)
    }

    @Query("UPDATE vaults SET artwork_resource_id = NULL WHERE artwork_resource_id = :rid")
    suspend fun clearArtworkSlot(rid: String)

    @Query("UPDATE vaults SET guide_resource_id = NULL WHERE guide_resource_id = :rid")
    suspend fun clearGuideSlot(rid: String)

    @Query("UPDATE vaults SET map_resource_id = NULL WHERE map_resource_id = :rid")
    suspend fun clearMapSlot(rid: String)

    /**
     * Update the stored launch intent JSON and ROM path for a vault.
     */
    @Query("""
        UPDATE vaults SET
            launchIntentJson = :intentJson,
            romPath = COALESCE(:romPath, romPath),
            romFilename = COALESCE(:romFilename, romFilename)
        WHERE id = :id
    """)
    suspend fun updateLaunchIntent(
        id: String,
        intentJson: String,
        romPath: String?,
        romFilename: String?
    )

    /**
     * Update launch intent on any vault matching by name or ROM path.
     */
    @Query("""
        UPDATE vaults SET
            launchIntentJson = :intentJson,
            romPath = COALESCE(:romPath, romPath),
            romFilename = COALESCE(:romFilename, romFilename)
        WHERE name = :name OR romPath = :romPath
    """)
    suspend fun updateLaunchIntentByMatch(
        name: String,
        romPath: String?,
        romFilename: String?,
        intentJson: String
    )

    /**
     * Check if vault exists
     */
    @Query("SELECT EXISTS(SELECT 1 FROM vaults WHERE id = :id)")
    suspend fun vaultExists(id: String): Boolean

    /**
     * Delete all vaults from the database. Used when storage folder changes
     * (non-migration) to prevent stale entries with broken images.
     */
    @Query("DELETE FROM vaults")
    suspend fun deleteAllVaults()

    /**
     * Current number of vaults in the library.
     */
    @Query("SELECT COUNT(*) FROM vaults")
    suspend fun count(): Int

    /**
     * How much the user has put into each vault, for the Delete multiple "Empty" filter.
     * Counts every resource except artwork (scraped automatically, so it says nothing
     * about use), plus saved guide tabs, saved maps and checklist items. A vault with a
     * zero here holds nothing a user would miss. [VaultContentCount.guideCount] is
     * and [VaultContentCount.imageCount] are what "Guides and images only" removes
     * (every saved tab except the map tab, and every Image tab entry). The byte
     * sums skip files linked from other folders, which no delete touches, so they
     * are what deleting frees.
     */
    @Query("""
        SELECT v.id AS vaultId,
            (SELECT COUNT(*) FROM resource_files r WHERE r.vault_id = v.id AND r.kind != 'artwork')
            + (SELECT COUNT(*) FROM saved_tabs s WHERE s.vaultId = v.id)
            + (SELECT COUNT(*) FROM saved_maps m WHERE m.vaultId = v.id)
            + (SELECT COUNT(*) FROM checklist_items c WHERE c.vaultId = v.id) AS contentCount,
            (SELECT COUNT(*) FROM saved_tabs g WHERE g.vaultId = v.id AND g.isMap = 0) AS guideCount,
            (SELECT COUNT(*) FROM saved_maps i WHERE i.vaultId = v.id) AS imageCount,
            (SELECT COALESCE(SUM(b.size_bytes), 0) FROM resource_files b
                WHERE b.vault_id = v.id AND b.source != 'local_link') AS totalBytes,
            (SELECT COALESCE(SUM(gb.size_bytes), 0) FROM resource_files gb
                WHERE gb.vault_id = v.id AND gb.source != 'local_link'
                AND gb.kind IN ('guide', 'group_page', 'map', 'screenshot')) AS guideImageBytes
        FROM vaults v
    """)
    suspend fun getContentCounts(): List<VaultContentCount>
}
