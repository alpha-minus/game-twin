package com.twinlore.app.data

import android.content.Context
import android.content.SharedPreferences
import com.twinlore.app.domain.VaultTab
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages vault last active tab (Guide, Map, Achievements, Browser) persistence.
 * Used so when the user returns to a vault from the library, the last left tab is restored.
 */
@Singleton
class VaultModeStateManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val PREFS_NAME = "vault_mode_state"
        private const val KEY_MODE_PREFIX = "mode_"
        private const val MODE_BROWSER = "browser"
        private const val MODE_MAP = "map"
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    /**
     * Save last active tab for a vault. Use this when leaving the vault or switching tabs.
     */
    fun saveLastTab(vaultId: String, tab: VaultTab) {
        prefs.edit()
            .putString("${KEY_MODE_PREFIX}$vaultId", tab.name)
            .apply()
    }
    
    /**
     * Get last active tab for a vault.
     * @return The last tab, or null if never set (caller should default to e.g. Guide).
     */
    fun getLastTab(vaultId: String): VaultTab? {
        val mode = prefs.getString("${KEY_MODE_PREFIX}$vaultId", null) ?: return null
        return when (mode) {
            MODE_BROWSER -> VaultTab.Browser
            MODE_MAP -> VaultTab.Map
            else -> VaultTab.entries.firstOrNull { it.name == mode }
        }
    }
    
    /**
     * Save last active mode for a vault (Browser vs Map).
     * @deprecated Use saveLastTab instead. Kept for backward compatibility with navigateToMapViewer/navigateToGuideViewer.
     */
    fun saveLastMode(vaultId: String, isBrowser: Boolean) {
        saveLastTab(vaultId, if (isBrowser) VaultTab.Browser else VaultTab.Map)
    }
    
}

