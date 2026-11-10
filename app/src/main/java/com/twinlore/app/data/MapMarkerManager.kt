package com.twinlore.app.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.twinlore.app.domain.MapMarker
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages map marker persistence (up to [MAX_MARKERS] markers per vault/map).
 *
 * The JSON sidecar mirror in the vault directory is gone — the
 * MapMarkerDao + this SharedPreferences cache are the source of truth, and the
 * JsonSidecarImporter handles the one-time import on app startup.
 */
@Singleton
class MapMarkerManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val PREFS_NAME = "map_markers"
        private const val KEY_MARKERS_PREFIX = "markers_"
        private const val KEY_FOCUSED_PREFIX = "focused_marker_"
        internal const val MAX_MARKERS = 1000
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val markerListType = object : TypeToken<List<MapMarker>>() {}.type

    /** Build the SharedPreferences key. When [mapId] is provided, markers are scoped per-map. */
    private fun markersKey(vaultId: String, mapId: String? = null): String {
        return if (!mapId.isNullOrEmpty()) "${KEY_MARKERS_PREFIX}${vaultId}_${mapId}" else "${KEY_MARKERS_PREFIX}$vaultId"
    }

    private fun focusedKey(vaultId: String, mapId: String? = null): String {
        return if (!mapId.isNullOrEmpty()) "${KEY_FOCUSED_PREFIX}${vaultId}_${mapId}" else "${KEY_FOCUSED_PREFIX}$vaultId"
    }

    /**
     * Migrate old vault-level markers to a specific map if they exist.
     * Call once when the first map for a vault is created from migration.
     */
    fun migrateMarkersToMap(vaultId: String, mapId: String) {
        val oldKey = "${KEY_MARKERS_PREFIX}$vaultId"
        val newKey = markersKey(vaultId, mapId)
        val existing = prefs.getString(oldKey, null)
        if (existing != null && prefs.getString(newKey, null) == null) {
            prefs.edit()
                .putString(newKey, existing)
                .apply()
            android.util.Log.d("MapMarkerManager", "Migrated markers from vaultId=$vaultId to mapId=$mapId")
        }
        // Migrate focused marker too
        val oldFocused = prefs.getString("${KEY_FOCUSED_PREFIX}$vaultId", null)
        if (oldFocused != null) {
            prefs.edit().putString(focusedKey(vaultId, mapId), oldFocused).apply()
        }
    }

    /**
     * Get all markers for a vault (optionally scoped to a specific map)
     */
    fun getMarkers(vaultId: String, mapId: String? = null): List<MapMarker> {
        val json = prefs.getString(markersKey(vaultId, mapId), null) ?: return emptyList()
        return try {
            gson.fromJson<List<MapMarker>>(json, markerListType) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Batch-read marker counts for multiple maps in a single vault. Returns an empty map for
     * empty input; map ids not present in SharedPreferences are treated as count 0 (they are
     * simply absent from the result; callers should default missing keys to 0).
     */
    fun getMarkerCounts(vaultId: String, mapIds: Collection<String>): Map<String, Int> {
        if (mapIds.isEmpty()) return emptyMap()
        val result = mutableMapOf<String, Int>()
        for (mapId in mapIds) {
            val json = prefs.getString(markersKey(vaultId, mapId), null) ?: continue
            val count = try {
                gson.fromJson<List<MapMarker>>(json, markerListType)?.size ?: 0
            } catch (_: Exception) {
                0
            }
            if (count > 0) result[mapId] = count
        }
        return result
    }

    /**
     * Save a marker for a vault/map
     * @return true if saved successfully, false if limit reached
     */
    fun saveMarker(vaultId: String, marker: MapMarker, mapId: String? = null): Boolean {
        val markers = getMarkers(vaultId, mapId).toMutableList()

        // Check if we're updating an existing marker
        val existingIndex = markers.indexOfFirst { it.id == marker.id }
        if (existingIndex >= 0) {
            markers[existingIndex] = marker
        } else {
            // Adding new marker - check limit
            if (markers.size >= MAX_MARKERS) {
                return false
            }
            markers.add(marker)
        }

        val json = gson.toJson(markers)
        prefs.edit().putString(markersKey(vaultId, mapId), json).apply()

        return true
    }

    /**
     * Delete a marker by ID
     */
    fun deleteMarker(vaultId: String, markerId: String, mapId: String? = null) {
        val markers = getMarkers(vaultId, mapId).toMutableList()
        val beforeCount = markers.size
        val removed = markers.removeAll { it.id == markerId }
        val afterCount = markers.size
        android.util.Log.d("MapMarkerManager", "deleteMarker: vaultId=$vaultId, mapId=$mapId, markerId=$markerId, beforeCount=$beforeCount, afterCount=$afterCount, removed=$removed")
        val json = gson.toJson(markers)
        prefs.edit().putString(markersKey(vaultId, mapId), json).apply()
    }

    /**
     * Check if marker limit is reached
     */
    fun isLimitReached(vaultId: String, mapId: String? = null): Boolean {
        return getMarkers(vaultId, mapId).size >= MAX_MARKERS
    }

    /**
     * Clear all markers for a vault/map
     */
    fun clearMarkers(vaultId: String, mapId: String? = null) {
        prefs.edit().remove(markersKey(vaultId, mapId)).apply()
        setFocusedMarkerId(vaultId, null, mapId)
    }

    /**
     * Get the last focused map marker id for a vault/map (for L1/R1 restore).
     */
    fun getFocusedMarkerId(vaultId: String, mapId: String? = null): String? {
        return prefs.getString(focusedKey(vaultId, mapId), null)?.takeIf { it.isNotBlank() }
    }

    /**
     * Persist the focused map marker id for a vault/map (so L1/R1 continues from last focus).
     */
    fun setFocusedMarkerId(vaultId: String, markerId: String?, mapId: String? = null) {
        prefs.edit().putString(focusedKey(vaultId, mapId), markerId ?: "").apply()
    }

}
