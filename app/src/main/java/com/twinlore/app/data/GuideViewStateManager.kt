package com.twinlore.app.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.twinlore.app.domain.ScrollRestoreData
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages guide browser state: "last page" = URL + scroll/anchor (like an
 * ephemeral bookmark).
 *
 * There are no vault_metadata.json sidecars, so URL + scroll restore now
 * live entirely in SharedPreferences (per-vault). Zoom is still persisted
 * per-bookmark via Room. State resets if the user clears app data — this is
 * intentional: this is session state and acceptable to lose.
 */
@Singleton
class GuideViewStateManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "GuideViewStateManager"
        private const val PREFS_NAME = "guide_view_state"
        private const val KEY_URL_PREFIX = "url_"
        private const val KEY_SCROLL_ANCHOR_PREFIX = "scroll_anchor_"
        private const val KEY_SCROLL_OCCURRENCE_PREFIX = "scroll_occurrence_"
        private const val KEY_SCROLL_RATIO_PREFIX = "scroll_ratio_"
        private const val KEY_ALWAYS_LOAD_BOOKMARKS_OFFLINE_PREFIX = "always_load_bookmarks_offline_"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Get saved last viewed URL for a vault. */
    fun getLastUrl(vaultId: String): String? {
        return prefs.getString("${KEY_URL_PREFIX}$vaultId", null)
    }

    /** Get the persisted active bookmark ID for a vault (guide tab). */
    fun getActiveBookmarkId(vaultId: String): String? {
        return prefs.getString("active_bookmark_$vaultId", null)
    }

    /** Persist the active bookmark ID for a vault (guide tab). */
    fun saveActiveBookmarkId(vaultId: String, bookmarkId: String?) {
        prefs.edit().apply {
            if (bookmarkId != null) {
                putString("active_bookmark_$vaultId", bookmarkId)
            } else {
                remove("active_bookmark_$vaultId")
            }
        }.apply()
    }

    /**
     * Get saved guide scroll restore for the last-visited URL. Reads the
     * SharedPreferences mirror written by [saveLastPageState].
     */
    suspend fun getGuideScrollRestore(vaultId: String): ScrollRestoreData? {
        val anchor = prefs.getString("${KEY_SCROLL_ANCHOR_PREFIX}$vaultId", null)
        val occurrenceIndex = prefs.getInt("${KEY_SCROLL_OCCURRENCE_PREFIX}$vaultId", -1)
        val ratio = run {
            val raw = prefs.getFloat("${KEY_SCROLL_RATIO_PREFIX}$vaultId", Float.NaN)
            if (raw.isNaN()) Double.NaN else raw.toDouble()
        }
        if (occurrenceIndex < 0 && (ratio.isNaN() || ratio < 0.0 || ratio > 1.0)) return null
        return ScrollRestoreData(
            normalizedAnchor = anchor,
            occurrenceIndex = occurrenceIndex.coerceAtLeast(0),
            scrollRatio = if (ratio.isNaN()) 0.0 else ratio.coerceIn(0.0, 1.0)
        )
    }

    /**
     * Save last page state: URL + scroll/anchor. Single write to prefs (no
     * disk JSON to merge). Zoom is persisted per-bookmark, not here.
     */
    fun saveLastPageState(vaultId: String, url: String?, scrollRestore: ScrollRestoreData?) {
        prefs.edit().apply {
            if (url != null) {
                putString("${KEY_URL_PREFIX}$vaultId", url)
            }
            if (scrollRestore != null) {
                if (scrollRestore.normalizedAnchor != null) {
                    putString("${KEY_SCROLL_ANCHOR_PREFIX}$vaultId", scrollRestore.normalizedAnchor)
                } else {
                    remove("${KEY_SCROLL_ANCHOR_PREFIX}$vaultId")
                }
                putInt("${KEY_SCROLL_OCCURRENCE_PREFIX}$vaultId", scrollRestore.occurrenceIndex)
                putFloat("${KEY_SCROLL_RATIO_PREFIX}$vaultId", scrollRestore.scrollRatio.toFloat())
            } else {
                remove("${KEY_SCROLL_ANCHOR_PREFIX}$vaultId")
                remove("${KEY_SCROLL_OCCURRENCE_PREFIX}$vaultId")
                remove("${KEY_SCROLL_RATIO_PREFIX}$vaultId")
            }
        }.apply()
    }

    /**
     * Clear saved state for a vault.
     */
    suspend fun clearState(vaultId: String) {
        prefs.edit()
            .remove("${KEY_URL_PREFIX}$vaultId")
            .remove("${KEY_SCROLL_ANCHOR_PREFIX}$vaultId")
            .remove("${KEY_SCROLL_OCCURRENCE_PREFIX}$vaultId")
            .remove("${KEY_SCROLL_RATIO_PREFIX}$vaultId")
            .apply()
        Log.d(TAG, "Cleared state for vault: $vaultId")
    }
}
