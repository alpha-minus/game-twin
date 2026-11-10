package com.twinlore.app.data

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages map viewer state persistence (zoom and pan position).
 *
 * The JSON sidecar mirror in vault_metadata.json is gone. The
 * SharedPreferences cache (per-vault/per-map zoom/offset) is the only home for
 * this transient state — and that's fine: zoom/pan are session-state and may
 * reset across reinstall.
 */
@Singleton
class MapViewStateManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val PREFS_NAME = "map_view_state"
        private const val KEY_ZOOM_PREFIX = "zoom_"
        private const val KEY_OFFSET_X_PREFIX = "offset_x_"
        private const val KEY_OFFSET_Y_PREFIX = "offset_y_"
        private const val KEY_SELECTED_PREFIX = "selected_"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Build the SharedPreferences key. When [mapId] is provided, state is scoped per-map. */
    private fun stateKey(prefix: String, vaultId: String, mapId: String? = null): String {
        return if (!mapId.isNullOrEmpty()) "$prefix${vaultId}_${mapId}" else "$prefix$vaultId"
    }

    /**
     * Save map view state for a vault (optionally scoped to a specific map)
     */
    fun saveState(vaultId: String, zoom: Float, offsetX: Float, offsetY: Float, mapId: String? = null) {
        prefs.edit()
            .putFloat(stateKey(KEY_ZOOM_PREFIX, vaultId, mapId), zoom)
            .putFloat(stateKey(KEY_OFFSET_X_PREFIX, vaultId, mapId), offsetX)
            .putFloat(stateKey(KEY_OFFSET_Y_PREFIX, vaultId, mapId), offsetY)
            .apply()
    }

    /**
     * Get saved map view state for a vault (optionally scoped to a specific map)
     */
    fun getState(vaultId: String, mapId: String? = null): MapViewState? {
        val zoom = prefs.getFloat(stateKey(KEY_ZOOM_PREFIX, vaultId, mapId), 1f)
        val offsetX = prefs.getFloat(stateKey(KEY_OFFSET_X_PREFIX, vaultId, mapId), 0f)
        val offsetY = prefs.getFloat(stateKey(KEY_OFFSET_Y_PREFIX, vaultId, mapId), 0f)

        return if (zoom != 1f || offsetX != 0f || offsetY != 0f) {
            MapViewState(zoom, offsetX, offsetY)
        } else {
            null
        }
    }

    /**
     * The image a vault is open on, or null when none has been chosen.
     *
     * One place for both the app's Image tab and the Always on Top panel,
     * the way the guide tab's active bookmark is: whichever surface pages to
     * an image writes it, and whichever surface opens next reads it. Before
     * this the app kept its choice in a view model and the panel opened on
     * the first of its list, so the two never agreed.
     */
    fun getSelectedMapId(vaultId: String): String? =
        prefs.getString("$KEY_SELECTED_PREFIX$vaultId", null)

    /** Records [mapId] as the image [vaultId] is open on; null forgets it. */
    fun saveSelectedMapId(vaultId: String, mapId: String?) {
        prefs.edit().apply {
            if (mapId != null) putString("$KEY_SELECTED_PREFIX$vaultId", mapId)
            else remove("$KEY_SELECTED_PREFIX$vaultId")
        }.apply()
    }

    /**
     * Clear saved state for a vault (optionally scoped to a specific map)
     */
    fun clearState(vaultId: String, mapId: String? = null) {
        prefs.edit()
            .remove(stateKey(KEY_ZOOM_PREFIX, vaultId, mapId))
            .remove(stateKey(KEY_OFFSET_X_PREFIX, vaultId, mapId))
            .remove(stateKey(KEY_OFFSET_Y_PREFIX, vaultId, mapId))
            .apply()
    }

}

/**
 * Data class for map view state
 */
data class MapViewState(
    val zoom: Float,
    val offsetX: Float,
    val offsetY: Float
)
