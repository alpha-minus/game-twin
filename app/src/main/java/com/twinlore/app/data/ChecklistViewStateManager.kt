package com.twinlore.app.data

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists checklist UI state per vault so returning to a vault restores
 * the last active tab and scroll position. Session-state: acceptable to
 * lose on app data clear.
 */
@Singleton
class ChecklistViewStateManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val PREFS_NAME = "checklist_view_state"
        private const val KEY_ACTIVE_TAB_PREFIX = "active_tab_"
        private const val KEY_SCROLL_INDEX_PREFIX = "scroll_index_"
        private const val KEY_SCROLL_OFFSET_PREFIX = "scroll_offset_"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getActiveTabId(vaultId: String): String? =
        prefs.getString("${KEY_ACTIVE_TAB_PREFIX}$vaultId", null)

    fun saveActiveTabId(vaultId: String, tabId: String?) {
        prefs.edit().apply {
            if (tabId != null) putString("${KEY_ACTIVE_TAB_PREFIX}$vaultId", tabId)
            else remove("${KEY_ACTIVE_TAB_PREFIX}$vaultId")
        }.apply()
    }

    fun getScrollPosition(vaultId: String): Pair<Int, Int>? {
        val index = prefs.getInt("${KEY_SCROLL_INDEX_PREFIX}$vaultId", -1)
        if (index < 0) return null
        val offset = prefs.getInt("${KEY_SCROLL_OFFSET_PREFIX}$vaultId", 0)
        return index to offset
    }

    fun saveScrollPosition(vaultId: String, firstVisibleIndex: Int, scrollOffset: Int) {
        prefs.edit()
            .putInt("${KEY_SCROLL_INDEX_PREFIX}$vaultId", firstVisibleIndex)
            .putInt("${KEY_SCROLL_OFFSET_PREFIX}$vaultId", scrollOffset)
            .apply()
    }

    fun clearState(vaultId: String) {
        prefs.edit()
            .remove("${KEY_ACTIVE_TAB_PREFIX}$vaultId")
            .remove("${KEY_SCROLL_INDEX_PREFIX}$vaultId")
            .remove("${KEY_SCROLL_OFFSET_PREFIX}$vaultId")
            .apply()
    }
}
