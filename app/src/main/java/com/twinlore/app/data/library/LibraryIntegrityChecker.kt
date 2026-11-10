package com.twinlore.app.data.library

import com.twinlore.app.data.UriProbe
import com.twinlore.app.data.local.MediaSearchIndexDao
import com.twinlore.app.data.local.ResourceFileDao
import com.twinlore.app.data.local.ResourceFileEntity
import com.twinlore.app.data.local.SavedMapDao
import com.twinlore.app.data.local.SavedTabDao
import com.twinlore.app.data.local.VaultDao
import com.twinlore.app.domain.IntegrityReport
import com.twinlore.app.domain.LibraryIntegrityState
import com.twinlore.app.domain.ResourceSource
import com.twinlore.app.domain.VaultIssueGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Audits `resource_files` rows for missing files at app start (or on user
 * request via the integrity banner). Files that are gone but recoverable by
 * filename + size match are healed by [ResourceResolver.resolve] as a side
 * effect of the audit; rows that still resolve to null are reported.
 *
 * Rows that failed only because no persisted SAF grant currently covers them are
 * bucketed into [IntegrityReport.unreadable] instead, and are never removed -- see
 * `isBlockedByLostGrant`.
 *
 * On Remove: vaults whose entire resource set is missing are cascade-deleted
 * via [VaultDao.deleteVaultById]; partial misses keep the vault shell, drop
 * the dead `resource_files` rows, clear matching pointers on the vault, and
 * delete saved tabs whose `resource_id` was just removed.
 */
@Singleton
class LibraryIntegrityChecker @Inject constructor(
    private val resourceFileDao: ResourceFileDao,
    private val vaultDao: VaultDao,
    private val savedTabDao: SavedTabDao,
    private val savedMapDao: SavedMapDao,
    private val resolver: ResourceResolver,
    private val libraryFolderManager: LibraryFolderManager,
    private val mediaSearchIndexDao: MediaSearchIndexDao,
    private val artworkThumbnailCache: ArtworkThumbnailCache,
    private val mapThumbnailCache: MapThumbnailCache,
    private val uriProbe: UriProbe
) {

    private val _state = MutableStateFlow<LibraryIntegrityState>(LibraryIntegrityState.Idle)
    val state: StateFlow<LibraryIntegrityState> = _state.asStateFlow()

    /**
     * Every step below is blocking IO -- a Room read per row, and a SAF walk per
     * unresolved row -- and the only caller launches this on `viewModelScope`, i.e.
     * [kotlinx.coroutines.Dispatchers.Main]. [ResourceResolver.resolve] does not hop
     * off the caller's dispatcher on its own, so without this [withContext] the whole
     * audit runs on the main thread and the UI is frozen for its full duration.
     */
    suspend fun runCheck() {
        _state.value = LibraryIntegrityState.Checking
        _state.value = withContext(Dispatchers.IO) { scan() }
    }

    /**
     * Missing-file count per vault, for the library's Delete multiple "Missing files"
     * filter. Runs the same audit as [runCheck] (so healed and grant-blocked rows are
     * not counted) but leaves [state] alone: the integrity banner should not pop up
     * just because the user opened the delete picker.
     */
    suspend fun missingCountsByVault(): Map<String, Int> {
        val result = withContext(Dispatchers.IO) { scan() }
        val report = (result as? LibraryIntegrityState.Issues)?.report ?: return emptyMap()
        return report.perVault.associate { it.vaultId to it.missing.size }
    }

    private suspend fun scan(): LibraryIntegrityState {
        if (libraryFolderManager.homeFolderUri() == null) {
            return LibraryIntegrityState.Healthy
        }
        // This audit's whole purpose is detecting files that went missing since
        // they were last resolved. A cache hit in ResourceResolver would report
        // "still resolves fine" without re-checking SAF, so drop it first to make
        // every resolve() below a real, fresh check.
        resolver.invalidateAll()
        val all = resourceFileDao.getAllOnce()
        val missing = mutableListOf<ResourceFileEntity>()
        val unreadable = mutableListOf<ResourceFileEntity>()
        var healedViaIndex = 0
        for (row in all) {
            if (resolver.resolve(row.id) != null) continue

            // Fallback: try to heal via the media-search-index by filename + size.
            val hit = mediaSearchIndexDao.findByFilenameAndSize(row.filename, row.sizeBytes)
            if (hit != null) {
                val healedRow = row.copy(
                    absolutePath = hit.absolutePath ?: row.absolutePath,
                    homeRelativePath = if (hit.absolutePath == null) hit.relativePath else null,
                    source = ResourceSource.LOCAL_LINK.raw,
                    lastSeenAt = System.currentTimeMillis(),
                )
                resourceFileDao.update(healedRow)
                healedViaIndex++
                continue
            }
            if (isBlockedByLostGrant(row)) unreadable.add(row) else missing.add(row)
        }
        if (healedViaIndex > 0) {
            android.util.Log.i("LibraryIntegrity", "Healed $healedViaIndex resource(s) via media_search_index fallback")
        }
        if (unreadable.isNotEmpty()) {
            android.util.Log.i(
                "LibraryIntegrity",
                "${unreadable.size} resource(s) unreadable: no persisted grant covers them yet"
            )
        }
        if (missing.isEmpty() && unreadable.isEmpty()) {
            return LibraryIntegrityState.Healthy
        }
        // Counted against the vault's total, not netted out of it: an unreadable row
        // is still one of the vault's files, so a vault holding one dead file and one
        // unreadable file must not look entirely dead and get cascade-deleted.
        val totalsByVault: Map<String, Int> = all.mapNotNull { it.vaultId }
            .groupingBy { it }.eachCount()
        val byVault = missing.groupBy { it.vaultId }
        val orphans = byVault[null].orEmpty()
        val perVault = byVault.filterKeys { it != null }.map { (vaultId, rows) ->
            val vault = vaultDao.getVaultById(vaultId!!)
            VaultIssueGroup(
                vaultId = vaultId,
                vaultName = vault?.name ?: "(unknown)",
                totalResourceCount = totalsByVault[vaultId] ?: rows.size,
                missing = rows
            )
        }.sortedBy { it.vaultName.lowercase() }
        return LibraryIntegrityState.Issues(
            IntegrityReport(
                perVault = perVault,
                orphans = orphans,
                totalMissing = missing.size,
                unreadable = unreadable
            )
        )
    }

    /**
     * True when [row] points at a `content://` document whose covering SAF grant this
     * app no longer holds. A reinstall drops every persisted grant at once, so on the
     * first run after one these rows all fail to resolve even though their files never
     * moved; re-picking the folder restores them without touching the database.
     */
    private fun isBlockedByLostGrant(row: ResourceFileEntity): Boolean {
        val path = row.absolutePath ?: return false
        if (!path.startsWith("content://")) return false
        return !uriProbe.hasCoveringPersistedPermission(path)
    }

    suspend fun applyRemoval(report: IntegrityReport) {
        for (group in report.perVault) {
            if (group.willBeDeleted) {
                vaultDao.deleteVaultById(group.vaultId)
                withContext(Dispatchers.IO) { artworkThumbnailCache.invalidate(group.vaultId) }
            } else {
                val deadIds = group.missing.map { it.id }.toSet()
                val vault = vaultDao.getVaultById(group.vaultId)
                if (vault != null) {
                    if (vault.artworkResourceId in deadIds) {
                        vaultDao.updateArtworkResourceId(vault.id, null)
                        withContext(Dispatchers.IO) { artworkThumbnailCache.invalidate(vault.id) }
                    }
                    if (vault.mapResourceId in deadIds) {
                        vaultDao.updateMapResourceId(vault.id, null)
                    }
                    if (vault.guideResourceId in deadIds) {
                        vaultDao.updateGuideResourceId(vault.id, null)
                    }
                }
                for (id in deadIds) {
                    savedTabDao.deleteByResourceId(id)
                    savedMapDao.deleteByResourceId(id)
                    resourceFileDao.deleteById(id)
                    withContext(Dispatchers.IO) { mapThumbnailCache.invalidate(id) }
                }
            }
        }
        for (orphan in report.orphans) {
            savedTabDao.deleteByResourceId(orphan.id)
            savedMapDao.deleteByResourceId(orphan.id)
            resourceFileDao.deleteById(orphan.id)
            withContext(Dispatchers.IO) { mapThumbnailCache.invalidate(orphan.id) }
        }
        runCheck()
    }
}
