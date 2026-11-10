package com.twinlore.app.data.library

import android.util.Log
import com.twinlore.app.data.MapMarkerManager
import com.twinlore.app.data.MapViewStateManager
import com.twinlore.app.data.local.ResourceFileDao
import com.twinlore.app.data.local.SavedMapDao
import com.twinlore.app.data.local.SavedTabDao
import com.twinlore.app.data.local.VaultDao
import com.twinlore.app.domain.ResourceKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Deletes vault images (the Image tab: screenshots, added images and maps) together
 * with their files.
 *
 * The Image tab used to drop only the `saved_maps` row, so every deleted image stayed
 * on disk. Both deletes here take the file too, through
 * [LibraryFolderManager.deleteFileUnderHome], which only deletes inside the library
 * folder: files linked from other folders stay where they are. A file is kept while
 * anything else still uses it (another image entry, a guide tab, or the vault's artwork).
 */
@Singleton
class VaultImageCleaner @Inject constructor(
    private val savedMapDao: SavedMapDao,
    private val savedTabDao: SavedTabDao,
    private val resourceFileDao: ResourceFileDao,
    private val resolver: ResourceResolver,
    private val libraryFolderManager: LibraryFolderManager,
    private val vaultDao: VaultDao,
    private val mapThumbnailCache: MapThumbnailCache,
    private val markerManager: MapMarkerManager,
    private val mapViewStateManager: MapViewStateManager,
) {
    /**
     * The Image tab's delete (in the app and in the overlay panel): the entry, its
     * markers, and its file. Moving the selection off it is the caller's job.
     */
    suspend fun deleteImage(vaultId: String, imageId: String) = withContext(Dispatchers.IO) {
        val resourceId = savedMapDao.getMapById(imageId)?.resourceId
        markerManager.clearMarkers(vaultId, imageId)
        savedMapDao.deleteMap(imageId)
        if (resourceId != null) removeFilesIfUnused(vaultId, listOf(resourceId))
    }

    /**
     * Delete multiple's "Guides and images only": every image in [vaultId], plus image
     * and screenshot files no entry points at any more. Artwork is never touched and the
     * vault stays. Returns how many Image tab entries were removed.
     */
    suspend fun deleteAllImagesForVault(vaultId: String): Int = withContext(Dispatchers.IO) {
        val maps = savedMapDao.getMapsByVaultIdOnce(vaultId)
        for (map in maps) {
            markerManager.clearMarkers(vaultId, map.id)
            savedMapDao.deleteMap(map.id)
        }
        mapViewStateManager.saveSelectedMapId(vaultId, null)
        val rows = resourceFileDao.getForVaultAndKindOnce(vaultId, ResourceKind.MAP.raw) +
            resourceFileDao.getForVaultAndKindOnce(vaultId, ResourceKind.SCREENSHOT.raw)
        removeFilesIfUnused(vaultId, rows.map { it.id })
        maps.size
    }

    private suspend fun removeFilesIfUnused(vaultId: String, resourceIds: List<String>) {
        val vault = vaultDao.getVaultById(vaultId)
        val inUse = buildSet {
            savedMapDao.getMapsByVaultIdOnce(vaultId).mapNotNullTo(this) { it.resourceId }
            savedTabDao.getTabsForVaultSync(vaultId).mapNotNullTo(this) { it.resourceId }
            vault?.artworkResourceId?.let(::add)
        }
        val removed = mutableSetOf<String>()
        for (id in resourceIds) {
            if (id in inUse) continue
            runCatching {
                resolver.resolve(id)?.let { libraryFolderManager.deleteFileUnderHome(it) }
                resolver.invalidate(id)
                resourceFileDao.deleteById(id)
                mapThumbnailCache.invalidate(id)
                removed += id
            }.onFailure { Log.w(TAG, "Could not remove image file $id", it) }
        }
        if (vault?.mapResourceId?.let { it in removed } == true) {
            vaultDao.updateMapResourceId(vaultId, null)
        }
    }

    private companion object {
        const val TAG = "VaultImageCleaner"
    }
}
