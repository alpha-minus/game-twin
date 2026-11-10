package com.twinlore.app.domain

/**
 * Tab within a vault session (bottom bar).
 */
enum class VaultTab { Guide, Map, Achievements, Checklist, Browser, Ai }

/**
 * Authoritative UI state model - exactly 6 states as per PRD
 * No overlays, single active state at all times
 */
sealed class AppState {
    /**
     * Root Library screen - shows all vaults
     */
    object Library : AppState() {
        override fun toString(): String = "Library"
    }

    /**
     * Map Viewer screen - displays map image for a vault
     * @param vaultId The unique identifier of the vault
     */
    data class MapViewer(val vaultId: String) : AppState() {
        init {
            require(vaultId.isNotBlank()) { "Vault ID cannot be blank" }
        }
    }

    /**
     * Guide Viewer screen - displays HTML browser for a vault
     * @param vaultId The unique identifier of the vault
     * @param tabId Optional tab ID to open specific saved tab
     */
    data class GuideViewer(val vaultId: String, val tabId: String? = null) : AppState() {
        init {
            require(vaultId.isNotBlank()) { "Vault ID cannot be blank" }
        }
    }

    /**
     * Vault session - single screen with 4-5 tabs (Guide, Map, [Achievements], Checklist, Browser).
     * @param vaultId The unique identifier of the vault
     * @param activeTab The currently selected tab
     */
    data class VaultSession(
        val vaultId: String,
        val activeTab: VaultTab,
        val artworkUri: String? = null,
        val accentArgb: Int? = null,
    ) : AppState() {
        init {
            require(vaultId.isNotBlank()) { "Vault ID cannot be blank" }
        }
    }

    /**
     * Settings screen - global application settings
     */
    object Settings : AppState() {
        override fun toString(): String = "Settings"
    }

    /**
     * Sleep mode - screen dimmed, rendering paused
     */
    object Sleep : AppState() {
        override fun toString(): String = "Sleep"
    }

    /**
     * RetroAchievements profile statistics panel (from Vault overlay or context menu).
     */
    object RetroAchievementsProfile : AppState() {
        override fun toString(): String = "RetroAchievementsProfile"
    }

    /**
     * Recommended apps catalog - browse curated emulation apps with
     * Add-to-Obtainium, source page, Google, and YouTube actions.
     */
    object AppCatalog : AppState() {
        override fun toString(): String = "AppCatalog"
    }

    /**
     * Play statistics for the ES-DE library (top games, play time charts, genres,
     * achievements).
     */
    object GameStats : AppState() {
        override fun toString(): String = "GameStats"
    }

    /**
     * Achievement Journey - guided achievement hunting flow, optionally locked to a
     * single game (opened from that game's context) or free-roaming across the library.
     * @param lockedGameId If non-null, the journey is scoped to this game.
     * @param lockedGameTitle Display title for the locked game, if any.
     */
    data class Journey(
        val lockedGameId: Int? = null,
        val lockedGameTitle: String? = null,
    ) : AppState()

    // Page-group viewing is rendered as a sub-state of VaultSession's Guide tab,
    // not a separate AppState. See MainViewModel.openPageGroupByVault +
    // GuideTabContent.

}



