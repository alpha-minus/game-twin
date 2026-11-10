package com.twinlore.app.data.library

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.twinlore.app.data.SettingsRepository
import com.twinlore.app.data.local.MediaSearchIndexDao
import com.twinlore.app.data.local.MediaSearchPathDao
import com.twinlore.app.data.local.MediaSearchPathEntity
import com.twinlore.app.domain.MediaSearchPath
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the media_search_paths table. AUTO entries are reconciled from
 * SettingsRepository (ESDE/Cocoon folder pickers); MANUAL entries are
 * user-added.
 */
@Singleton
class MediaSearchPathRepository(
    private val pathDao: MediaSearchPathDao,
    private val indexDao: MediaSearchIndexDao,
    private val dirDao: com.twinlore.app.data.local.MediaSearchDirDao,
    private val resourceDao: com.twinlore.app.data.local.ResourceFileDao,
    private val savedTabDao: com.twinlore.app.data.local.SavedTabDao,
    private val savedMapDao: com.twinlore.app.data.local.SavedMapDao,
    private val vaultRepo: com.twinlore.app.data.VaultRepository,
    private val settings: SettingsRepository,
    private val context: Context? = null,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val mapThumbnailCache: MapThumbnailCache? = null,
) {
    @Inject
    constructor(
        pathDao: MediaSearchPathDao,
        indexDao: MediaSearchIndexDao,
        dirDao: com.twinlore.app.data.local.MediaSearchDirDao,
        resourceDao: com.twinlore.app.data.local.ResourceFileDao,
        savedTabDao: com.twinlore.app.data.local.SavedTabDao,
        savedMapDao: com.twinlore.app.data.local.SavedMapDao,
        vaultRepo: com.twinlore.app.data.VaultRepository,
        settings: SettingsRepository,
        @ApplicationContext context: Context,
        mapThumbnailCache: MapThumbnailCache,
    ) : this(pathDao, indexDao, dirDao, resourceDao, savedTabDao, savedMapDao, vaultRepo, settings, context, clock = { System.currentTimeMillis() }, mapThumbnailCache = mapThumbnailCache)

    suspend fun getAll(): List<MediaSearchPath> = withContext(Dispatchers.IO) {
        pathDao.getAll().map { it.toDomain() }
    }

    suspend fun add(uri: Uri, label: String): MediaSearchPath = withContext(Dispatchers.IO) {
        // Dedupe by URI: if the user picks the same folder twice (or the
        // SAME folder once as MANUAL after it's already AUTO), reuse the
        // existing row rather than spawning a duplicate.
        val uriString = uri.toString()
        val existing = pathDao.getAll().firstOrNull { it.uri == uriString }
        if (existing != null) return@withContext existing.toDomain()

        val entity = MediaSearchPathEntity(
            id = UUID.randomUUID().toString(),
            uri = uriString,
            label = label,
            origin = MediaSearchPath.Origin.MANUAL.raw,
            lastScannedAt = null,
            createdAt = clock(),
            disabled = false,
        )
        pathDao.upsert(entity)
        entity.toDomain()
    }

    /** Removes a MANUAL entry. AUTO entries are silently ignored. */
    suspend fun remove(id: String) = withContext(Dispatchers.IO) {
        val row = pathDao.getById(id) ?: return@withContext
        if (MediaSearchPath.Origin.fromRaw(row.origin) != MediaSearchPath.Origin.MANUAL) return@withContext
        pathDao.deleteById(id)
        // Index entries cascade via FK ON DELETE.
    }

    /**
     * Check each search path's underlying URI for accessibility. Returns a
     * map keyed by search-path id with one of:
     *  - [com.twinlore.app.domain.MediaSearchPathStatus.Ok] if the folder
     *    resolves and is readable. `currentName` carries the provider's
     *    current display name (may differ from the stored label if the user
     *    renamed the folder externally).
     *  - [com.twinlore.app.domain.MediaSearchPathStatus.Missing] if the
     *    folder is gone, moved, or the URI permission was revoked.
     */
    suspend fun validate(): Map<String, com.twinlore.app.domain.MediaSearchPathStatus> = withContext(Dispatchers.IO) {
        val all = pathDao.getAll()
        val result = mutableMapOf<String, com.twinlore.app.domain.MediaSearchPathStatus>()
        for (row in all) {
            result[row.id] = checkPath(row.uri)
        }
        result
    }

    private fun checkPath(uriString: String): com.twinlore.app.domain.MediaSearchPathStatus {
        val uri = try { Uri.parse(uriString) } catch (_: Exception) {
            return com.twinlore.app.domain.MediaSearchPathStatus.Missing
        }
        return when (uri.scheme) {
            "file" -> {
                val f = File(uri.path ?: return com.twinlore.app.domain.MediaSearchPathStatus.Missing)
                if (f.isDirectory && f.canRead()) {
                    com.twinlore.app.domain.MediaSearchPathStatus.Ok(currentName = f.name)
                } else {
                    com.twinlore.app.domain.MediaSearchPathStatus.Missing
                }
            }
            "content" -> {
                val ctx = context ?: return com.twinlore.app.domain.MediaSearchPathStatus.Missing
                val doc = try {
                    DocumentFile.fromTreeUri(ctx, uri)
                } catch (_: Exception) {
                    return com.twinlore.app.domain.MediaSearchPathStatus.Missing
                }
                if (doc != null && doc.exists() && doc.isDirectory && doc.canRead()) {
                    com.twinlore.app.domain.MediaSearchPathStatus.Ok(currentName = doc.name)
                } else {
                    com.twinlore.app.domain.MediaSearchPathStatus.Missing
                }
            }
            else -> com.twinlore.app.domain.MediaSearchPathStatus.Missing
        }
    }

    /**
     * Update an entry's label. Used when the user accepts a "rename
     * detected" prompt. Only MANUAL entries can be relabeled; AUTO entries
     * derive their label from the origin's static template.
     */
    suspend fun relabel(id: String, newLabel: String) = withContext(Dispatchers.IO) {
        val row = pathDao.getById(id) ?: return@withContext
        if (MediaSearchPath.Origin.fromRaw(row.origin) != MediaSearchPath.Origin.MANUAL) return@withContext
        pathDao.update(row.copy(label = newLabel))
    }

    /**
     * Reconcile AUTO_ESDE / AUTO_COCOON rows against current settings.
     * - If the source folder is set AND <src>/downloaded_media exists, upsert the AUTO row.
     * - Otherwise, remove any AUTO row of that origin.
     * Idempotent.
     */
    suspend fun syncAutoFromSettings() = withContext(Dispatchers.IO) {
        reconcileAuto(MediaSearchPath.Origin.AUTO_ESDE, settings.esdeFolderUri.first()?.takeIf { it.isNotBlank() }?.let { resolveDownloadedMediaUri(it) })
        reconcileAuto(MediaSearchPath.Origin.AUTO_COCOON, settings.cocoonFolderUri.first()?.takeIf { it.isNotBlank() }?.let { resolveDownloadedMediaUri(it) })
        reconcileAuto(MediaSearchPath.Origin.AUTO_HOME, settings.homeFolderUri.first()?.takeIf { it.isNotBlank() })
    }

    private suspend fun reconcileAuto(
        origin: MediaSearchPath.Origin,
        expectedUri: String?,
    ) {
        val existing = pathDao.findByOrigin(origin.raw)
        if (expectedUri == null) {
            if (existing != null) pathDao.deleteById(existing.id)
            return
        }
        if (existing == null) {
            // Consolidate with any pre-existing MANUAL row sharing this URI
            // (e.g., the user added the home folder manually before AUTO_HOME existed).
            // Flip its origin in place to preserve the path id and scanned index rows.
            val manualWithSameUri = pathDao.findByUri(expectedUri)
                ?.takeIf { it.origin == MediaSearchPath.Origin.MANUAL.raw }
            if (manualWithSameUri != null) {
                pathDao.update(
                    manualWithSameUri.copy(
                        origin = origin.raw,
                        label = labelFor(origin),
                    ),
                )
                return
            }
            pathDao.upsert(
                MediaSearchPathEntity(
                    id = UUID.randomUUID().toString(),
                    uri = expectedUri,
                    label = labelFor(origin),
                    origin = origin.raw,
                    lastScannedAt = null,
                    createdAt = clock(),
                    disabled = false,
                )
            )
        } else if (existing.uri != expectedUri) {
            // URI changed (user re-picked the source folder). Preserve disabled
            // flag; user has to explicitly re-enable from Settings.
            pathDao.update(existing.copy(uri = expectedUri))
            // Old index for the previous URI is now stale; clear so a fresh scan rebuilds.
            indexDao.deleteForPath(existing.id)
            dirDao.deleteForPath(existing.id)
        }
    }

    /**
     * Counts distinct vaults that have at least one LOCAL_LINK resource_files
     * row from this search path bound to a slot or referenced by a saved tab/map.
     */
    suspend fun countAffectedVaults(spId: String): Int = withContext(Dispatchers.IO) {
        pathDao.countAffectedVaults(spId)
    }

    /**
     * Hard-remove (MANUAL paths). Cleans up LOCAL_LINK rows + path row.
     * FK CASCADE wipes media_search_index + media_search_dirs.
     */
    suspend fun removeWithCleanup(spId: String) = withContext(Dispatchers.IO) {
        cleanupLocalLinksFor(spId)
        pathDao.deleteById(spId)
    }

    /**
     * Soft-disable (AUTO paths). Cleans up LOCAL_LINK rows, wipes the index,
     * keeps the path row with disabled = true.
     */
    suspend fun disableWithCleanup(spId: String) = withContext(Dispatchers.IO) {
        cleanupLocalLinksFor(spId)
        indexDao.deleteForPath(spId)
        dirDao.deleteForPath(spId)
        val row = pathDao.getById(spId) ?: return@withContext
        pathDao.update(row.copy(disabled = true, lastScannedAt = null))
    }

    /** Re-enable a previously disabled path. Caller follows up with a scan. */
    suspend fun enable(spId: String) = withContext(Dispatchers.IO) {
        val row = pathDao.getById(spId) ?: return@withContext
        if (!row.disabled) return@withContext
        pathDao.update(row.copy(disabled = false))
    }

    private suspend fun cleanupLocalLinksFor(spId: String) {
        val affected = pathDao.getLocalLinkResourceIdsFor(spId)
        if (affected.isEmpty()) return
        for (rid in affected) vaultRepo.clearAnySlotPointingAt(rid)
        savedTabDao.deleteByResourceIds(affected)
        savedMapDao.deleteByResourceIds(affected)
        resourceDao.deleteByIds(affected)
        mapThumbnailCache?.invalidateAll(affected)
    }

    private fun resolveDownloadedMediaUri(parent: String): String? {
        val parentUri = Uri.parse(parent)
        return when (parentUri.scheme) {
            "file" -> {
                val sub = File(parentUri.path ?: return null, "downloaded_media")
                if (sub.isDirectory) Uri.fromFile(sub).toString() else null
            }
            "content" -> {
                val ctx = context ?: return null
                val tree = DocumentFile.fromTreeUri(ctx, parentUri) ?: return null
                tree.findFile("downloaded_media")?.takeIf { it.isDirectory }?.uri?.toString()
            }
            else -> null
        }
    }

    private fun labelFor(origin: MediaSearchPath.Origin): String = when (origin) {
        MediaSearchPath.Origin.AUTO_ESDE -> "ES-DE downloaded_media"
        MediaSearchPath.Origin.AUTO_COCOON -> "Cocoon downloaded_media"
        MediaSearchPath.Origin.AUTO_HOME -> "Library folder"
        MediaSearchPath.Origin.MANUAL -> "Custom"
    }

    private fun MediaSearchPathEntity.toDomain() = MediaSearchPath(
        id = id,
        uri = uri,
        label = label,
        origin = MediaSearchPath.Origin.fromRaw(origin),
        lastScannedAt = lastScannedAt,
        createdAt = createdAt,
        disabled = disabled,
    )
}
