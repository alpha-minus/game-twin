package com.twinlore.app.ui.navigation

import com.twinlore.app.domain.VaultTab

/**
 * Mode within a tab: picker/landing page or content viewer.
 */
enum class TabMode {
    PICKER,
    VIEWER
}

/**
 * Snapshot of a tab's focus state, captured on tab switch.
 */
data class TabFocusState(
    val focusedElementId: String?,
    val chromeVisible: Boolean,
    val mode: TabMode
)

/**
 * Saves and restores focus state per tab within a vault session.
 * Cleared on vault switch (vaultId change) so each vault starts fresh.
 *
 * Usage:
 * - Call [save] when switching away from a tab
 * - Call [restore] when switching to a tab (returns null if no saved state → default to bottom bar)
 * - Call [clearAll] when vaultId changes
 */
class TabFocusRestorationManager {
    private val tabStates = mutableMapOf<VaultTab, TabFocusState>()
    private var currentVaultId: String? = null

    /**
     * Save focus state for the given tab. Automatically clears all state
     * if the vaultId has changed since the last save.
     */
    fun save(vaultId: String, tab: VaultTab, state: TabFocusState) {
        if (currentVaultId != null && currentVaultId != vaultId) {
            clearAll()
        }
        currentVaultId = vaultId
        tabStates[tab] = state
    }

    /**
     * Restore saved focus state for the given tab, or null if no state was saved.
     * Returns null on first visit to a tab (caller should default to bottom bar focus).
     */
    fun restore(tab: VaultTab): TabFocusState? {
        return tabStates[tab]
    }

    /**
     * Clear all saved states. Called on vault switch.
     */
    fun clearAll() {
        tabStates.clear()
        currentVaultId = null
    }
}
