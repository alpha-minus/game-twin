package com.twinlore.app.domain

import java.util.Date

/**
 * Domain model for a vault
 */
data class Vault(
    val id: String,
    val name: String,
    val romPath: String?,
    val romFilename: String?,
    val artworkVibrantArgb: Int? = null,
    val systemeid: String?,
    val gameId: String?,
    val source: String?,
    val raGameId: String? = null,
    val steamAppId: String? = null,
    val artworkResourceId: String? = null,
    val mapResourceId: String? = null,
    val guideResourceId: String? = null,
    val launchIntentJson: String? = null,
    val lastOpened: Date?,
    val createdAt: Date,
    /**
     * Persisted stable folder name. Null on legacy rows; use [resolvedFolderName]
     * which falls back to sanitizing [name] for those.
     */
    val folderName: String? = null,
) {
    /**
     * Get display name for the vault
     * Priority: user-defined name > ROM filename > ROM path > "Unnamed Vault"
     */
    val displayName: String
        get() = when {
            name.isNotBlank() -> name
            romFilename != null -> romFilename
            romPath != null -> romPath.substringAfterLast('/')
            else -> "Unnamed Vault"
        }

    val hasArtwork: Boolean
        get() = artworkResourceId != null

    val hasMap: Boolean
        get() = mapResourceId != null

    val hasGuide: Boolean
        get() = guideResourceId != null

    /**
     * Check if vault has enough data to launch the game
     */
    val hasLaunchData: Boolean
        get() = launchIntentJson != null
}


