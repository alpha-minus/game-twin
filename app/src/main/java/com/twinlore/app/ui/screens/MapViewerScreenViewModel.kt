package com.twinlore.app.ui.screens

import android.content.Context
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.twinlore.app.data.MapMarkerManager
import com.twinlore.app.data.MapViewStateManager
import com.twinlore.app.data.SavedMapRepository
import com.twinlore.app.data.VaultRepository
import com.twinlore.app.data.library.MapThumbnailCache
import com.twinlore.app.data.library.ResourceResolver
import com.twinlore.app.domain.AddFileResult
import com.twinlore.app.domain.MapMarker
import com.twinlore.app.domain.ResourceKind
import com.twinlore.app.domain.SavedMap
import com.twinlore.app.domain.Vault
import com.twinlore.app.ui.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * ViewModel for Map Viewer screen
 */
@HiltViewModel
class MapViewerScreenViewModel @Inject constructor(
    private val vaultRepository: VaultRepository,
    val stateManager: MapViewStateManager,
    private val markerManager: MapMarkerManager,
    private val savedMapRepository: SavedMapRepository,
    private val resourceResolver: ResourceResolver,
    private val mapThumbnailCache: MapThumbnailCache,
    private val imageCleaner: com.twinlore.app.data.library.VaultImageCleaner
) : BaseViewModel() {

    /**
     * Pre-flight resolution check used by the saved-maps picker before
     * switching the active map. Returns true if the file is reachable, false
     * if the resolver came back null (likely missing in the home folder).
     */
    suspend fun isResourcePresent(resourceId: String): Boolean =
        resourceResolver.resolve(resourceId) != null

    private val _markers = MutableStateFlow<List<MapMarker>>(emptyList())
    val markers: StateFlow<List<MapMarker>> = _markers.asStateFlow()

    /** List of saved maps for the current vault. */
    private val _savedMaps = MutableStateFlow<List<SavedMap>>(emptyList())
    val savedMaps: StateFlow<List<SavedMap>> = _savedMaps.asStateFlow()

    /** Currently selected map ID. */
    private val _selectedMapId = MutableStateFlow<String?>(null)
    val selectedMapId: StateFlow<String?> = _selectedMapId.asStateFlow()

    /** (vaultId, generation) — when map is replaced for a vault (same path overwrite), UI can bust cache. */
    private val _mapReplacedSignal = MutableStateFlow(Pair("", 0))
    val mapReplacedSignal: StateFlow<Pair<String, Int>> = _mapReplacedSignal.asStateFlow()

    /** Call after assigning a new map image so the map UI reloads even when path is unchanged (e.g. map/map.jpg). */
    fun notifyMapReplaced(vaultId: String) {
        _mapReplacedSignal.value = vaultId to (_mapReplacedSignal.value.second + 1)
    }

    fun getVault(vaultId: String): Flow<Vault?> {
        return vaultRepository.getVaultByIdFlow(vaultId)
    }

    private var observeJob: Job? = null

    /** The vault whose images are being observed, so a selection can be recorded against it. */
    private var selectionVaultId: String? = null

    /**
     * Every selection goes through here, so the store always says what this
     * view model says.
     *
     * The store is [MapViewStateManager]'s per-vault selection, which the
     * Always on Top panel reads to open on the image the app was showing and
     * writes when it pages. Before this the selection lived only here, and
     * the panel opened on the top of its list every time.
     */
    private fun setSelected(vaultId: String?, mapId: String?) {
        _selectedMapId.value = mapId
        if (vaultId != null) stateManager.saveSelectedMapId(vaultId, mapId)
    }

    /**
     * The image to open on when nothing valid is selected: the one the store
     * records for the vault if the vault still has it, else the first.
     */
    private fun restoredOrFirst(vaultId: String, maps: List<SavedMap>): String? {
        val stored = stateManager.getSelectedMapId(vaultId)
        return maps.firstOrNull { it.id == stored }?.id ?: maps.firstOrNull()?.id
    }

    /**
     * Observe saved maps for a vault. Selects the recorded image, or the
     * first, when nothing valid is selected.
     */
    fun observeSavedMaps(vaultId: String) {
        selectionVaultId = vaultId
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            savedMapRepository.getMapsByVaultId(vaultId).collect { maps ->
                _savedMaps.value = maps
                if (_selectedMapId.value == null || maps.none { it.id == _selectedMapId.value }) {
                    setSelected(vaultId, restoredOrFirst(vaultId, maps))
                }
            }
        }
    }

    /** Select a specific map. */
    fun selectMap(mapId: String) {
        val vaultId = _savedMaps.value.firstOrNull { it.id == mapId }?.vaultId ?: selectionVaultId
        setSelected(vaultId, mapId)
        viewModelScope.launch {
            savedMapRepository.updateLastAccessed(mapId)
        }
    }

    /**
     * Follows the store's selection for [vaultId] without writing anything.
     *
     * For the Image tab coming back from Always on Top: it stayed composed
     * behind the panel with its selection in memory, and the panel paged to
     * another image and recorded that. Nothing recorded, or an image the vault
     * no longer has, changes nothing.
     */
    fun adoptStoredSelection(vaultId: String) {
        val stored = stateManager.getSelectedMapId(vaultId) ?: return
        if (stored == _selectedMapId.value) return
        if (_savedMaps.value.none { it.id == stored }) return
        _selectedMapId.value = stored
    }

    /** Add a new map and select it. Returns the new map via callback. */
    fun addMap(vaultId: String, title: String, resourceId: String?, onAdded: ((SavedMap) -> Unit)? = null) {
        viewModelScope.launch {
            val map = savedMapRepository.addMap(vaultId, title, resourceId)
            setSelected(vaultId, map.id)
            // Migrate old vault-level markers to first map if this is the first map
            val count = savedMapRepository.getMapCount(vaultId)
            if (count == 1) {
                markerManager.migrateMarkersToMap(vaultId, map.id)
            }
            onAdded?.invoke(map)
        }
    }

    /** Delete a map and its markers. Files are user-owned (home folder); we no longer manage on-disk lifecycle. */
    fun deleteSavedMap(vaultId: String, mapId: String) {
        viewModelScope.launch {
            // Entry, markers, thumbnail and the file itself (it used to stay on disk).
            imageCleaner.deleteImage(vaultId, mapId)

            // If deleted map was selected, select another
            if (_selectedMapId.value == mapId) {
                val remaining = savedMapRepository.getMapsByVaultIdOnce(vaultId)
                setSelected(vaultId, remaining.firstOrNull()?.id)
            }
        }
    }

    /** Rename a saved map. */
    fun renameSavedMap(mapId: String, newTitle: String) {
        viewModelScope.launch {
            savedMapRepository.renameMap(mapId, newTitle)
        }
    }

    /**
     * Batch-read marker counts for multiple maps. Runs on IO to avoid blocking the main thread
     * when called from a LaunchedEffect in the picker. Missing map ids are absent from the result;
     * callers should default missing keys to 0.
     */
    suspend fun getMarkerCounts(vaultId: String, ids: Collection<String>): Map<String, Int> =
        withContext(Dispatchers.IO) { markerManager.getMarkerCounts(vaultId, ids) }

    fun loadMarkers(vaultId: String, mapId: String? = null) {
        viewModelScope.launch {
            _markers.value = markerManager.getMarkers(vaultId, mapId)
        }
    }

    fun saveMarker(vaultId: String, marker: MapMarker, mapId: String? = null): Boolean {
        val success = markerManager.saveMarker(vaultId, marker, mapId)
        if (success) {
            viewModelScope.launch {
                _markers.value = markerManager.getMarkers(vaultId, mapId)
            }
        }
        return success
    }

    fun deleteMarker(vaultId: String, markerId: String, mapId: String? = null) {
        viewModelScope.launch {
            markerManager.deleteMarker(vaultId, markerId, mapId)
            _markers.value = markerManager.getMarkers(vaultId, mapId)
        }
    }

    fun deleteAllMarkers(vaultId: String, mapId: String? = null) {
        viewModelScope.launch {
            markerManager.clearMarkers(vaultId, mapId)
            _markers.value = markerManager.getMarkers(vaultId, mapId)
        }
    }

    fun updateMarkerStyle(vaultId: String, markerId: String, styleId: Int, mapId: String? = null) {
        viewModelScope.launch {
            val markers = _markers.value.toMutableList()
            val index = markers.indexOfFirst { it.id == markerId }
            if (index >= 0) {
                val marker = markers[index]
                val updatedMarker = marker.copy(styleId = styleId)
                markerManager.saveMarker(vaultId, updatedMarker, mapId)
                _markers.value = markerManager.getMarkers(vaultId, mapId)
            }
        }
    }

    fun isLimitReached(vaultId: String, mapId: String? = null): Boolean {
        return markerManager.isLimitReached(vaultId, mapId)
    }

    /** Last focused map marker id for L1/R1 restore. */
    fun getFocusedMarkerId(vaultId: String, mapId: String? = null): String? = markerManager.getFocusedMarkerId(vaultId, mapId)

    /** Persist focused map marker id so L1/R1 continues from last focus. */
    fun setFocusedMarkerId(vaultId: String, markerId: String?, mapId: String? = null) {
        markerManager.setFocusedMarkerId(vaultId, markerId, mapId)
    }

    fun removeMap(vaultId: String) {
        viewModelScope.launch {
            // Clear markers first (markers are tied to specific map)
            val mapId = _selectedMapId.value
            markerManager.clearMarkers(vaultId, mapId)
            _markers.value = emptyList()

            // Clear the vault's primary-map FK; the resource_files row is left in
            // place because saved_maps may still reference it.
            vaultRepository.updateMapResourceId(vaultId, null)
        }
    }

    fun updateMapResourceId(vaultId: String, resourceId: String?) {
        viewModelScope.launch {
            vaultRepository.updateMapResourceId(vaultId, resourceId)
        }
    }

    fun updateArtworkResourceId(vaultId: String, resourceId: String?) {
        viewModelScope.launch {
            vaultRepository.updateArtworkResourceId(vaultId, resourceId)
        }
    }

    /**
     * Add the file at [uri] to the vault as a map. Always copies the source
     * bytes into the storage folder; the original file is left untouched. On
     * success creates a new SavedMap, selects it, and updates
     * `vault.mapResourceId`. Calls [onResult] with the new SavedMap or null on failure.
     */
    fun assignMapFile(
        vaultId: String,
        uri: Uri,
        title: String,
        context: Context,
        mimeType: String? = null,
        onResult: (SavedMap?) -> Unit = {}
    ) {
        viewModelScope.launch {
            copyAndAdd(vaultId, uri, title, context, mimeType, onResult)
        }
    }

    private suspend fun copyAndAdd(
        vaultId: String,
        uri: Uri,
        title: String,
        context: Context,
        mimeType: String?,
        onResult: (SavedMap?) -> Unit
    ) {
        // Derive extension and MIME from the URI. For file:// URIs (e.g. the
        // VGMaps downloader's cache file) the path is the authoritative source;
        // contentResolver.getType returns null on Android 16 for private-cache
        // file:// URIs. For content:// URIs (e.g. the OpenDocument file picker)
        // we fall back to the resolver's MIME, which IS reliable there.
        val resolvedMime = context.contentResolver.getType(uri)
        val pathExt = uri.lastPathSegment
            ?.substringAfterLast('.', "")
            ?.takeIf { it.isNotBlank() }
            ?.lowercase()
        val mimeExt = resolvedMime
            ?.substringAfterLast('/', "")
            ?.takeIf { it.isNotBlank() }
        // Prefer the URI path extension (authoritative for file://), then MIME,
        // then "bin" so worst-case files don't look like leftover temp files.
        val ext = pathExt ?: mimeExt ?: "bin"
        // Pass through a MIME for SAF createFile so the storage provider stores
        // the file with the right type. If we don't have one from getType, derive
        // it from the extension via MimeTypeMap for the common image types.
        val effectiveMime = mimeType
            ?: resolvedMime
            ?: android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            ?: "application/octet-stream"

        val tempFile = withContext(Dispatchers.IO) {
            try {
                // Name the cache file using the caller-supplied title so it
                // propagates to the library folder via VaultRepository.addFile
                // (which uses sourceFile.name as the suggestedFilename).
                val sanitized = title.trim()
                    .replace(Regex("""[\\/:*?"<>|]"""), "_")
                    .replace(Regex("\\s+"), " ")
                    .take(120)
                    .ifBlank { "map_${System.currentTimeMillis()}" }
                val f = File(context.cacheDir, "$sanitized.$ext")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    f.outputStream().use { out -> input.copyTo(out) }
                } ?: return@withContext null
                f.takeIf { it.exists() }
            } catch (_: Exception) { null }
        }
        if (tempFile == null) { onResult(null); return }
        when (val result = vaultRepository.addFile(vaultId, tempFile, ResourceKind.MAP, effectiveMime)) {
            is AddFileResult.Copied -> {
                // addFile() copied the bytes into the vault folder; the temp under
                // cacheDir/ is now dead weight. Without this delete every assigned map
                // leaks one PNG named for its title (e.g. cacheDir/Zebes.png at 13 MB).
                tempFile.delete()
                finishAssignMap(vaultId, title, result.resourceFileId, onResult)
            }
            is AddFileResult.Failed -> {
                tempFile.delete()
                onResult(null)
            }
        }
    }

    private suspend fun finishAssignMap(
        vaultId: String,
        title: String,
        resourceFileId: String,
        onResult: (SavedMap?) -> Unit
    ) {
        // saved_maps now stores the resource_files FK directly; ResourceResolver
        // turns that into the on-disk Uri at render time.
        val map = savedMapRepository.addMap(vaultId, title, resourceFileId)
        setSelected(vaultId, map.id)
        val count = savedMapRepository.getMapCount(vaultId)
        if (count == 1) {
            markerManager.migrateMarkersToMap(vaultId, map.id)
        }
        vaultRepository.updateMapResourceId(vaultId, resourceFileId)
        onResult(map)
    }
}
