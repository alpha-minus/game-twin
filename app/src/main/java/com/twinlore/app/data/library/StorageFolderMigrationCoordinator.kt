package com.twinlore.app.data.library

import android.net.Uri
import com.twinlore.app.data.SettingsRepository
import com.twinlore.app.data.local.ResourceFileDao
import com.twinlore.app.data.local.VaultDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates the "user changes their storage folder" flow. Walks every
 * `resource_files` row whose backing file lives under the old tree (i.e.
 * [com.twinlore.app.data.local.ResourceFileEntity.homeRelativePath] is
 * non-null), copies it into the same relative position under the new tree,
 * and rewrites the row.
 *
 * Rows with only [com.twinlore.app.data.local.ResourceFileEntity.absolutePath]
 * (LINK_IN_PLACE'd files outside any storage folder) are left alone — their
 * source files don't move when the storage folder URI changes.
 *
 * The new URI is persisted to [SettingsRepository.setHomeFolderUri] only
 * after the loop completes; mid-failure leaves the old URI active and the
 * partially copied files orphaned in the new tree (the user can clean up
 * via the system files app if needed).
 */
@Singleton
class StorageFolderMigrationCoordinator @Inject constructor(
    private val resourceDao: ResourceFileDao,
    private val vaultDao: VaultDao,
    private val folder: LibraryFolderManager,
    private val settings: SettingsRepository,
    private val resourceResolver: ResourceResolver
) {

    sealed class Progress {
        data class Working(val current: Int, val total: Int, val filename: String) : Progress()
        data class Failed(val resourceId: String, val cause: Throwable) : Progress()
        data class Done(val migrated: Int, val failed: Int) : Progress()
    }

    /** Number of resource rows that would be migrated (homeRelativePath set). */
    suspend fun countMigratableFiles(): Int = withContext(Dispatchers.IO) {
        resourceDao.getAllOnce().count { it.homeRelativePath != null }
    }

    /**
     * Copy every storage-rooted file from [oldTreeUri] to [newTreeUri], updating
     * the matching `resource_files` row to point at its new location. Per-row
     * failures are reported via [progress] but do not abort the loop.
     *
     * Persists [newTreeUri] to settings on completion regardless of how many
     * rows succeeded — the caller has already taken persistable permission for
     * it and the user has explicitly asked to switch.
     */
    suspend fun migrate(
        oldTreeUri: Uri,
        newTreeUri: Uri,
        progress: (Progress) -> Unit
    ): Int = withContext(Dispatchers.IO) {
        val rows = resourceDao.getAllOnce().filter { it.homeRelativePath != null }
        val total = rows.size
        var migrated = 0
        var failed = 0
        rows.forEachIndexed { idx, row ->
            progress(Progress.Working(idx + 1, total, row.filename))
            try {
                val sourceUri = folder.resolveInTree(oldTreeUri, row.homeRelativePath!!)
                    ?: throw java.io.FileNotFoundException("Source not found: ${row.homeRelativePath}")
                val vault = row.vaultId?.let { vaultDao.getVaultById(it) }
                // resolveFolderName falls back to sanitizeForPath(name) for legacy
                // vaults whose folder_name column is null (migrations against
                // pre-29 rows always hit the fallback path).
                val vaultDirName = vault
                    ?.let { LibraryFolderManager.resolveFolderName(it) }
                    ?: LibraryFolderManager.sanitizeForPath("Unnamed")
                val outcome = folder.copyUriIntoTree(
                    sourceUri = sourceUri,
                    destTreeUri = newTreeUri,
                    vaultDirName = vaultDirName,
                    suggestedFilename = row.filename,
                    mimeType = row.mimeType
                ) ?: throw java.io.IOException("Copy failed for ${row.filename}")

                resourceDao.update(
                    row.copy(
                        homeRelativePath = outcome.homeRelativePath,
                        absolutePath = outcome.absolutePath,
                        lastSeenAt = System.currentTimeMillis()
                    )
                )
                // True move: drop the source after the copy + DB update succeeded.
                // Best-effort — a stale source file in the old tree is harmless
                // (the row no longer references it) and the user can clean up
                // via the system files app if needed.
                folder.deleteAt(sourceUri)
                migrated++
            } catch (e: Throwable) {
                progress(Progress.Failed(row.id, e))
                failed++
            }
        }
        settings.setHomeFolderUri(newTreeUri.toString())
        // Every row just moved to a new tree Uri (or was left behind under the
        // old one); any Uri cached from before this run is no longer trustworthy.
        resourceResolver.invalidateAll()
        progress(Progress.Done(migrated, failed))
        migrated
    }

    /**
     * Switch to [newTreeUri] without copying any files. Existing
     * `resource_files` rows that were anchored to the old tree will fail to
     * resolve until the user moves them manually; the caller is expected to
     * have warned the user before calling this.
     */
    suspend fun switchWithoutMigration(newTreeUri: Uri) = withContext(Dispatchers.IO) {
        settings.setHomeFolderUri(newTreeUri.toString())
        resourceResolver.invalidateAll()
    }

}
