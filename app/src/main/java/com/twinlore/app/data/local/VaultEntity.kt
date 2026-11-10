package com.twinlore.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

/**
 * Room entity representing a vault in the database
 */
@Entity(tableName = "vaults")
data class VaultEntity(
    @PrimaryKey
    val id: String,
    
    /**
     * User-defined name for the vault
     */
    val name: String,
    
    /**
     * Full ROM path (if available) - highest priority for identity resolution
     */
    val romPath: String?,
    
    /**
     * ROM filename - second priority for identity resolution
     */
    val romFilename: String?,

    /**
     * Vibrant color extracted from artwork (ARGB) for focus border/glow; null until computed on artwork assign.
     */
    val artworkVibrantArgb: Int?,

    /**
     * ScreenScraper system ID (platform) - mandatory for scraping
     */
    val systemeid: String?,
    
    /**
     * ScreenScraper game ID (cached from search) - for media fetching
     */
    val gameId: String?,
    
    /**
     * Source of vault creation (e.g., "Scraped from Screenscraper", "Manual", "RetroArch")
     */
    val source: String?,
    
    /**
     * RetroAchievements Game ID (matched or manually set) for achievement drawer
     */
    val raGameId: String?,

    /**
     * Steam App ID for Steam achievement tracking
     */
    val steamAppId: String?,

    /**
     * FK to resource_files for current cover artwork. Null if no artwork attached.
     * Old artworkPath stays for one-release back-compat reads.
     */
    @ColumnInfo(name = "artwork_resource_id")
    val artworkResourceId: String? = null,

    /**
     * FK to resource_files for the vault's primary guide. Null if no primary guide attached.
     */
    @ColumnInfo(name = "guide_resource_id")
    val guideResourceId: String? = null,

    /**
     * FK to resource_files for the vault's primary map. Null if no primary map attached.
     */
    @ColumnInfo(name = "map_resource_id")
    val mapResourceId: String? = null,

    /**
     * Complete launch intent as JSON — stores package, activity, and all extras
     * needed to fire the emulator. Unified format for both RetroArch and ES-DE sources.
     */
    val launchIntentJson: String? = null,

    /**
     * Last time the vault was opened
     */
    val lastOpened: Date?,

    /**
     * When the vault was created
     */
    val createdAt: Date,

    /**
     * Stable, sanitized folder name for the vault's on-disk subfolder under
     * the library home. Populated at create time with
     * `LibraryFolderManager.sanitizeForPath(name)` and intentionally NOT
     * updated when the vault is renamed — rename is metadata-only and the
     * folder stays put, so this column lets save + delete paths target the
     * actual folder rather than re-sanitizing a possibly-renamed display name.
     *
     * Nullable for back-compat with rows written before MIGRATION_28_29.
     * Call sites must use `Vault.folderName` (which falls back to
     * `sanitizeForPath(name)` when null) rather than reading this directly.
     */
    @ColumnInfo(name = "folder_name")
    val folderName: String? = null
)


