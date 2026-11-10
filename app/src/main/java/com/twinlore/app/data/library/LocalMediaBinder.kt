package com.twinlore.app.data.library

import com.twinlore.app.data.VaultRepository
import com.twinlore.app.data.local.ResourceFileDao
import com.twinlore.app.data.local.ResourceFileEntity
import com.twinlore.app.data.local.SavedMapDao
import com.twinlore.app.data.local.SavedMapEntity
import com.twinlore.app.data.local.SavedTabDao
import com.twinlore.app.data.local.SavedTabEntity
import com.twinlore.app.domain.LocalMediaMatch
import com.twinlore.app.domain.ResourceKind
import com.twinlore.app.domain.ResourceSource
import com.twinlore.app.domain.Result
import com.twinlore.app.domain.Vault
import kotlinx.coroutines.flow.first
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class BindReport(
    val vaultId: String,
    val artworkBound: String? = null,
    val guideBound: String? = null,
    val artworkAvailable: Int = 0,
    val guideAvailable: Int = 0,
) {
    fun hasAny(): Boolean =
        artworkBound != null || guideBound != null || artworkAvailable > 0 || guideAvailable > 0
}

@Singleton
class LocalMediaBinder(
    private val matcher: LocalMediaMatcher,
    private val resourceDao: ResourceFileDao,
    private val vaultRepo: VaultRepository,
    private val savedTabDao: SavedTabDao,
    private val savedMapDao: SavedMapDao,
    private val settingsRepository: com.twinlore.app.data.SettingsRepository,
    private val mediaSearchPathRepository: MediaSearchPathRepository? = null,
    private val mediaIndexer: MediaIndexer? = null,
    private val bitmapContext: android.content.Context? = null,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val mapThumbnailCache: MapThumbnailCache? = null,
) {
    @Inject
    constructor(
        matcher: LocalMediaMatcher,
        resourceDao: ResourceFileDao,
        vaultRepo: VaultRepository,
        savedTabDao: SavedTabDao,
        savedMapDao: SavedMapDao,
        settingsRepository: com.twinlore.app.data.SettingsRepository,
        mediaSearchPathRepository: MediaSearchPathRepository,
        mediaIndexer: MediaIndexer,
        @dagger.hilt.android.qualifiers.ApplicationContext context: android.content.Context,
        mapThumbnailCache: MapThumbnailCache,
    ) : this(matcher, resourceDao, vaultRepo, savedTabDao, savedMapDao, settingsRepository, mediaSearchPathRepository, mediaIndexer, bitmapContext = context, clock = { System.currentTimeMillis() }, mapThumbnailCache = mapThumbnailCache)

    /**
     * Removes the media-index bindings a same-name vault would re-match, so a
     * delete + recreate can't resurrect stale entries. DB-only (no files touched).
     * Delegates to [LocalMediaMatcher.removeBindingsForVaultName]. Returns rows removed.
     */
    suspend fun removeBindingsForVaultName(displayName: String): Int =
        matcher.removeBindingsForVaultName(displayName)

    /** Tag-priority map for each user-selected default asset type. Lower priority wins. */
    private fun priorityFor(tag: String, type: com.twinlore.app.data.DefaultAssetType): Int = when (type) {
        com.twinlore.app.data.DefaultAssetType.GRID -> when (tag) {
            "cover" -> 0; "3dbox" -> 1; "fanart" -> 2; "miximage" -> 3; "marquee" -> 4; "screenshot" -> 5; else -> 6
        }
        com.twinlore.app.data.DefaultAssetType.LOGO -> when (tag) {
            "marquee" -> 0; "cover" -> 1; "3dbox" -> 2; "miximage" -> 3; "fanart" -> 4; "screenshot" -> 5; else -> 6
        }
        com.twinlore.app.data.DefaultAssetType.HERO -> when (tag) {
            "fanart" -> 0; "miximage" -> 1; "cover" -> 2; "3dbox" -> 3; "marquee" -> 4; "screenshot" -> 5; else -> 6
        }
    }

    private val guidePreference = listOf("pdf", "mht", "mhtml", "html", "txt")

    /**
     * @param preserveExistingSlots When true, the artwork/guide slots are
     * filled only if they are currently null. Existing bindings (including
     * LOCAL_LINK ones) are left untouched. The user-initiated 'Re-scan local
     * media' action passes true so it doesn't overwrite a vault's existing
     * artwork. The default false keeps the original re-evaluate-on-refresh
     * semantics for vault create/refresh paths.
     */
    suspend fun bindForVault(
        vaultParam: Vault,
        preserveExistingSlots: Boolean = false,
    ): BindReport {
        val rawMatches = matcher.findForVault(vaultParam)
        // Defense-in-depth: drop matches whose underlying file no longer exists.
        // The matcher returns rows from media_search_index, which can lag behind
        // disk reality (vault folder deletion, external file moves, SAF provider
        // quirks). Without this filter, phantom matches become resource_files
        // rows pointing at dead paths and auto-bind a non-existent artwork
        // into the vault's slot, which then makes VaultScrapeCoordinator skip
        // the remote scrape because the slot looks filled.
        val presentMatches = rawMatches.filter { existsOnDisk(it.absolutePath ?: it.contentUriString) }
        // Drop matches living under dot-prefixed directories (Syncthing
        // .stversions backups etc.). The walkers no longer index those, but
        // stale index rows from older builds can still reference them.
        // Drop GUIDE matches living inside one of this vault's page-group
        // folders: those page-N.mht files are owned by the group tab and must
        // never be registered as standalone guides.
        val groupFolders = if (presentMatches.isEmpty()) emptyList() else {
            savedTabDao.getTabsForVaultSync(vaultParam.id)
                .filter { it.fileType == "group" }
                .mapNotNull { it.groupFolderName }
        }
        val matches = presentMatches.filter { m ->
            !isSyncArtifactMatch(m) &&
                !(m.resourceKind == ResourceKind.GUIDE &&
                    (isInsideGroupFolder(m.absolutePath ?: m.contentUriString, groupFolders) ||
                        PageGroupFolderAdoption.isGroupPageFilename(m.filename)))
        }
        val droppedPhantom = rawMatches.size - presentMatches.size
        val preferScraper = settingsRepository.preferScraperForArtwork.first()
        android.util.Log.d(
            "LocalMediaBinder",
            "bindForVault id=${vaultParam.id} name='${vaultParam.displayName}' " +
                "artwork=${vaultParam.artworkResourceId} guide=${vaultParam.guideResourceId} " +
                "matches=${matches.size} (raw=${rawMatches.size} droppedPhantom=$droppedPhantom) " +
                "preferScraper=$preferScraper preserveExistingSlots=$preserveExistingSlots"
        )
        android.util.Log.i(
            "VAULT_RESIDUE",
            "bindForVault vaultId=${vaultParam.id} name='${vaultParam.displayName}' " +
                "matcherHits=${rawMatches.size} kept=${matches.size} droppedPhantom=$droppedPhantom " +
                "priorArtwork=${vaultParam.artworkResourceId} priorGuide=${vaultParam.guideResourceId} " +
                "preserveExistingSlots=$preserveExistingSlots"
        )
        if (matches.isEmpty()) {
            // Refresh must never null an ARTWORK slot the vault already has: a
            // user-assigned local artwork whose filename doesn't normalize to the
            // game name produces zero matcher hits here, and clearing it silently
            // dropped the user's pick (and then triggered a replacement scrape).
            // Assigned artwork is left untouched; genuinely dead rows are cleaned
            // up by LibraryIntegrityChecker, not by refresh.
            //
            // Guides keep the stale-clear: a saved-guide slot backed by a
            // LOCAL_LINK row that no longer matches is cleared so the vault
            // doesn't show a phantom guide binding. Preserve mode skips it.
            if (!preserveExistingSlots) {
                val priorRowsForGate = resourceDao.getForVaultOnce(vaultParam.id)
                val guideSrc = vaultParam.guideResourceId?.let { rid ->
                    priorRowsForGate.firstOrNull { it.id == rid }?.source
                }
                if (guideSrc == ResourceSource.LOCAL_LINK.raw) {
                    vaultRepo.updateGuideResourceId(vaultParam.id, null)
                }
            }
            return BindReport(vaultId = vaultParam.id)
        }

        // Clean up LOCAL_LINK rows from earlier builds where the indexer stored a
        // bare relative path as absolutePath (no scheme, no leading slash). Those
        // rows can't be resolved by Coil or the resource resolver and would dedup
        // against fresh matches by a different fingerprint, leaving orphans.
        var vault = vaultParam
        val priorRows = resourceDao.getForVaultOnce(vault.id)
        for (row in priorRows) {
            if (row.source != ResourceSource.LOCAL_LINK.raw) continue
            val path = row.absolutePath
            if (path != null && !path.startsWith("content://") &&
                !path.startsWith("file://") && !path.startsWith("/")
            ) {
                // Clear vault pointers if they reference this dead row, and
                // mirror the change on the local snapshot so the auto-bind
                // gate below sees the cleared state.
                if (vault.artworkResourceId == row.id) {
                    vaultRepo.updateArtworkResourceId(vault.id, null)
                    vault = vault.copy(artworkResourceId = null)
                }
                if (vault.guideResourceId == row.id) {
                    vaultRepo.updateGuideResourceId(vault.id, null)
                    vault = vault.copy(guideResourceId = null)
                }
                if (vault.mapResourceId == row.id) {
                    vaultRepo.updateMapResourceId(vault.id, null)
                    vault = vault.copy(mapResourceId = null)
                }
                resourceDao.deleteById(row.id)
                mapThumbnailCache?.invalidate(row.id)
            }
        }

        val existingRows = resourceDao.getForVaultOnce(vault.id)
        val rowByFingerprint = existingRows.associateBy { fingerprint(it.absolutePath, it.sizeBytes) }

        // Ensure every match has a resource_files row.
        val matchToRowId = mutableMapOf<LocalMediaMatch, String>()
        val insertedThisRun = mutableListOf<ResourceFileEntity>()
        for (m in matches) {
            val resolvedPath = m.absolutePath ?: m.contentUriString

            val fp = fingerprint(resolvedPath, m.sizeBytes)
            val existing = rowByFingerprint[fp]
            if (existing != null) {
                matchToRowId[m] = existing.id
                // Ensure a SavedTab/SavedMap exists for guide/artwork entries even when
                // the resource row already exists (handles re-bind after the user
                // deleted the tab/map manually).
                if (m.resourceKind == ResourceKind.GUIDE) {
                    ensureSavedTab(vault, existing.id, m)
                } else if (m.resourceKind == ResourceKind.ARTWORK && looksLikeMap(m)) {
                    ensureSavedMap(vault, existing.id, m)
                }
                continue
            }
            // Logical dedupe: the path fingerprint missed, but a resource with
            // the same (filename, size) already exists in this vault — the same
            // file reached via a vault-dir variant (`super metroid (3)/`) or a
            // guide already registered as browser_save. Reuse that row instead
            // of creating a duplicate resource + tab.
            val logicalDup = findLogicalDuplicate(existingRows + insertedThisRun, m.filename, m.sizeBytes)
            if (logicalDup != null) {
                matchToRowId[m] = logicalDup.id
                continue
            }
            val newId = UUID.randomUUID().toString()
            val newRow = ResourceFileEntity(
                id = newId,
                vaultId = vault.id,
                kind = m.resourceKind.raw,
                filename = m.filename,
                sizeBytes = m.sizeBytes,
                homeRelativePath = null,
                absolutePath = resolvedPath,
                mimeType = null,
                source = ResourceSource.LOCAL_LINK.raw,
                lastSeenAt = clock(),
                createdAt = clock(),
            )
            resourceDao.insert(newRow)
            insertedThisRun += newRow
            matchToRowId[m] = newId
            if (m.resourceKind == ResourceKind.GUIDE) {
                insertSavedTab(vault, newId, m)
            } else if (m.resourceKind == ResourceKind.ARTWORK && looksLikeMap(m)) {
                insertSavedMap(vault, newId, m)
            }
        }

        // Auto-bind empty slots (artwork and guide only; map is never auto-bound).
        val artworkMatches = matches.filter { it.resourceKind == ResourceKind.ARTWORK }
        val guideMatches = matches.filter { it.resourceKind == ResourceKind.GUIDE }

        var artworkBound: String? = null
        // When "Prefer scraper for artwork" is ON, leave the vault.artworkResourceId
        // slot empty so the remote scrape fills it. Local artwork rows are still
        // inserted above (so they appear in the picker and maps tab as alternatives).
        //
        // Refresh (and re-scan) only ever FILL an empty artwork slot; a vault that
        // already has artwork — auto-bound or user-picked alike — is left untouched.
        // Previously refresh re-evaluated LOCAL_LINK slots to track preference
        // changes (asset type, grid ratio), but that also silently replaced the
        // user's manually assigned local artwork with the "best ratio" match, since
        // both carry source = LOCAL_LINK and can't be told apart. Never touching an
        // assigned slot is the behavior the user expects.
        val currentArtworkSource = vault.artworkResourceId?.let { rid ->
            existingRows.firstOrNull { it.id == rid }?.source
        }
        val canAutoBindArtwork = vault.artworkResourceId == null
        if (canAutoBindArtwork && !preferScraper) {
            val assetType = settingsRepository.defaultAssetType.first()
            val targetRatio = settingsRepository.gridDisplayRatio.first().aspectRatio
            // Tag still filters out clearly off-target categories (e.g. backcover/screenshot for GRID),
            // but within the eligible set the candidate whose aspect ratio is closest to the user's
            // grid display ratio wins. Filenames are the final tiebreak.
            val pick = artworkMatches.minWithOrNull(
                compareBy<LocalMediaMatch> { priorityFor(it.artworkTag, assetType) }
                    .thenBy { ratioDistance(it, targetRatio) }
                    .thenBy { it.filename }
            )
            if (pick == null && currentArtworkSource == ResourceSource.LOCAL_LINK.raw) {
                // Loose match from the old algorithm no longer holds under strict equality.
                vaultRepo.updateArtworkResourceId(vault.id, null)
                vault = vault.copy(artworkResourceId = null)
                android.util.Log.d("LocalMediaBinder", "cleared stale LOCAL_LINK artwork slot on vault ${vault.id}")
            } else if (pick != null) {
                val pickRowId = matchToRowId[pick]!!
                // Skip the write (and skip surfacing this as a "newly bound" event)
                // when the slot already points at the same file.
                if (pickRowId != vault.artworkResourceId) {
                    val updateResult = vaultRepo.updateArtworkResourceId(vault.id, pickRowId)
                    if (updateResult is Result.Success) {
                        artworkBound = pick.filename
                        android.util.Log.d("LocalMediaBinder", "bound artwork '${pick.filename}' to vault ${vault.id}")
                        android.util.Log.i(
                            "VAULT_RESIDUE",
                            "bindForVault auto-bound artwork vaultId=${vault.id} resourceId=$pickRowId " +
                                "filename='${pick.filename}' path='${pick.absolutePath ?: pick.contentUriString}'"
                        )
                    } else {
                        android.util.Log.w("LocalMediaBinder", "updateArtworkResourceId failed for vault ${vault.id}: $updateResult")
                    }
                } else {
                    android.util.Log.d("LocalMediaBinder", "vault ${vault.id} artwork already matches the preferred pick; no rebind")
                }
            }
        } else {
            android.util.Log.d("LocalMediaBinder", "vault ${vault.id} artwork is from $currentArtworkSource (preserved); ${artworkMatches.size} local match(es) available as alternatives")
        }

        var guideBound: String? = null
        val currentGuideSource = vault.guideResourceId?.let { rid ->
            existingRows.firstOrNull { it.id == rid }?.source
        }
        val canAutoBindGuide = if (preserveExistingSlots) {
            vault.guideResourceId == null
        } else {
            vault.guideResourceId == null ||
                currentGuideSource == ResourceSource.LOCAL_LINK.raw
        }
        if (canAutoBindGuide) {
            val pick = guideMatches.minByOrNull { guidePriority(it.extLower) }
            if (pick == null && currentGuideSource == ResourceSource.LOCAL_LINK.raw) {
                vaultRepo.updateGuideResourceId(vault.id, null)
                vault = vault.copy(guideResourceId = null)
            } else if (pick != null) {
                val pickRowId = matchToRowId[pick]!!
                if (pickRowId != vault.guideResourceId) {
                    val updateResult = vaultRepo.updateGuideResourceId(vault.id, pickRowId)
                    if (updateResult is Result.Success) {
                        guideBound = pick.filename
                    }
                }
            }
        }

        return BindReport(
            vaultId = vault.id,
            artworkBound = artworkBound,
            guideBound = guideBound,
            artworkAvailable = artworkMatches.size,
            guideAvailable = guideMatches.size,
        )
    }

    /**
     * Vault-create entry point. Runs [bindForVault]; if the index has no
     * artwork or guide matches for this vault, refreshes the media-search
     * index (sync auto paths + scanDelta all enabled paths) and binds once
     * more so newly-added local media is picked up automatically.
     *
     * This is the only automatic media-index scan trigger; the startup loop
     * that scanned every search path on every cold launch was removed.
     * Existing-vault binds (refresh, long-press rescan) still read the index
     * without refreshing it — those flows have their own explicit triggers.
     */
    suspend fun bindForVaultWithRescanIfEmpty(vault: Vault): BindReport {
        val first = bindForVault(vault, preserveExistingSlots = false)
        if (first.artworkAvailable > 0 || first.guideAvailable > 0) return first
        val repo = mediaSearchPathRepository
        val indexer = mediaIndexer
        if (repo == null || indexer == null) return first
        android.util.Log.d(
            "LocalMediaBinder",
            "vault ${vault.id} bind empty; refreshing media index then retrying"
        )
        repo.syncAutoFromSettings()
        for (sp in repo.getAll()) {
            if (sp.disabled) continue
            indexer.scanDelta(sp.id)
        }
        return bindForVault(vault, preserveExistingSlots = false)
    }

    private suspend fun ensureSavedTab(vault: Vault, resourceId: String, m: LocalMediaMatch) {
        val tabs = savedTabDao.getTabsForVaultSync(vault.id)
        if (tabs.any { it.resourceId == resourceId }) return
        insertSavedTab(vault, resourceId, m)
    }

    private suspend fun insertSavedTab(vault: Vault, resourceId: String, m: LocalMediaMatch) {
        val maxOrder = savedTabDao.getMaxOrder(vault.id) ?: -1
        val now = Date(clock())
        val title = m.filename.substringBeforeLast('.').ifBlank { "Guide" }
        savedTabDao.insertTab(
            SavedTabEntity(
                id = UUID.randomUUID().toString(),
                vaultId = vault.id,
                title = title,
                url = "",
                scrollPosition = 0,
                zoomLevel = 1.0f,
                isFavorite = false,
                isMap = false,
                isCurrentContent = false,
                order = maxOrder + 1,
                createdAt = now,
                lastAccessed = now,
                fileType = m.extLower,
                fileName = m.filename,
                lastPdfPage = 0,
                pdfScrollOffset = 0f,
                resourceId = resourceId,
            )
        )
    }

    private suspend fun ensureSavedMap(vault: Vault, resourceId: String, m: LocalMediaMatch) {
        val maps = savedMapDao.getMapsByVaultIdOnce(vault.id)
        if (maps.any { it.resourceId == resourceId }) return
        insertSavedMap(vault, resourceId, m)
    }

    private suspend fun insertSavedMap(vault: Vault, resourceId: String, m: LocalMediaMatch) {
        val now = Date(clock())
        val title = m.filename.substringBeforeLast('.').ifBlank { m.filename }
        savedMapDao.insertMap(
            SavedMapEntity(
                id = UUID.randomUUID().toString(),
                vaultId = vault.id,
                title = title,
                resourceId = resourceId,
                order = 0,
                createdAt = now,
                lastAccessed = now,
            )
        )
    }

    /**
     * Aspect-ratio distance for a local match. Reads image dimensions on demand
     * via BitmapFactory.Options(inJustDecodeBounds=true) — fast (no decode) and
     * cached implicitly by the OS page cache. Returns Float.MAX_VALUE for files
     * we can't read so they sort after candidates with a known ratio.
     */
    private fun ratioDistance(m: LocalMediaMatch, target: Float): Float {
        val (w, h) = readImageDimensions(m) ?: return Float.MAX_VALUE
        if (w <= 0 || h <= 0) return Float.MAX_VALUE
        return kotlin.math.abs(w.toFloat() / h - target)
    }

    private fun readImageDimensions(m: LocalMediaMatch): Pair<Int, Int>? {
        val path = m.absolutePath ?: return null
        return try {
            val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            when {
                path.startsWith("content://") -> {
                    val ctx = bitmapContext ?: return null
                    ctx.contentResolver.openInputStream(android.net.Uri.parse(path))?.use {
                        android.graphics.BitmapFactory.decodeStream(it, null, opts)
                    }
                }
                path.startsWith("file://") -> {
                    java.io.FileInputStream(android.net.Uri.parse(path).path ?: return null).use {
                        android.graphics.BitmapFactory.decodeStream(it, null, opts)
                    }
                }
                path.startsWith("/") -> {
                    java.io.FileInputStream(path).use {
                        android.graphics.BitmapFactory.decodeStream(it, null, opts)
                    }
                }
                else -> return null
            }
            if (opts.outWidth > 0 && opts.outHeight > 0) opts.outWidth to opts.outHeight else null
        } catch (e: Exception) {
            null
        }
    }

    private fun guidePriority(ext: String): Int {
        val i = guidePreference.indexOf(ext)
        return if (i < 0) Int.MAX_VALUE else i
    }

    private fun fingerprint(path: String?, size: Long): String = "${path ?: ""}|$size"

    /**
     * True iff the file pointed at by [path] still exists on disk. Mirrors
     * LibraryFolderManager.exists() but inline so the binder doesn't have to
     * take that extra dependency; uses [bitmapContext] (the ApplicationContext)
     * for the SAF case. Returns true permissively when [path] is null/blank,
     * when no context is available (unit-test signal — the test fixture also
     * passes null and synthetic paths), or on any I/O failure so we never hide
     * a genuinely-present row due to a transient error.
     */
    private fun existsOnDisk(path: String?): Boolean {
        if (path.isNullOrBlank()) return true
        // Test mode: no ApplicationContext means we can't do SAF lookups and
        // the test fixture's file:// paths are synthetic. Trust the matcher.
        val ctx = bitmapContext ?: return true
        return try {
            when {
                path.startsWith("content://") -> {
                    ctx.contentResolver.query(
                        android.net.Uri.parse(path),
                        arrayOf(android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID),
                        null, null, null
                    )?.use { cursor -> cursor.count > 0 } ?: false
                }
                path.startsWith("file://") -> {
                    val p = android.net.Uri.parse(path).path ?: return false
                    java.io.File(p).exists()
                }
                path.startsWith("/") -> java.io.File(path).exists()
                else -> true  // relative or unknown scheme — trust the matcher
            }
        } catch (_: Throwable) {
            true
        }
    }

    /**
     * True iff the file's filename or any folder in its relative path contains
     * the substring "map" (case-insensitive). Used to gate which local artwork
     * matches surface as candidates in the maps tab. Replaces the prior
     * always-or-never `surfaceLocalImagesInMapsTab` user toggle so the maps
     * tab only fills with files the user actually wanted there.
     */
    private fun looksLikeMap(m: LocalMediaMatch): Boolean {
        val filename = m.filename
        val relPath = m.relativePath ?: ""
        return filename.contains("map", ignoreCase = true) ||
            relPath.contains("map", ignoreCase = true)
    }

    /**
     * True when the match lives under a dot-prefixed directory (or is itself a
     * dot-prefixed file) — Syncthing `.stversions` backups, `.trashed-*`, etc.
     * Checks the index-relative path when available, falling back to the
     * resolved absolute path / content URI (URL-decoded).
     */
    private fun isSyncArtifactMatch(m: LocalMediaMatch): Boolean {
        val rel = m.relativePath
        if (rel != null) {
            return rel.split('/').any { MediaIndexer.isExcludedDirName(it) }
        }
        val resolved = m.absolutePath ?: m.contentUriString ?: return false
        return decodeAssetPath(resolved).split('/').any { MediaIndexer.isExcludedDirName(it) }
    }

    /** Per-rule counts removed by [cleanupLocalLinkDuplicates]. */
    data class LocalLinkCleanupSummary(
        val syncArtifacts: Int,
        val groupOwned: Int,
        val logicalDuplicates: Int,
    ) {
        val total: Int get() = syncArtifacts + groupOwned + logicalDuplicates
    }

    /**
     * One-time (but idempotent, run every app start) cleanup of duplicate
     * local_link registrations created by earlier builds:
     *  1. rows whose path contains a dot-prefixed directory segment
     *     (Syncthing `.stversions` version backups etc.),
     *  2. rows inside one of the vault's page-group folders (pages owned by a
     *     `fileType="group"` tab, wrongly bound as standalone guides),
     *  3. logical duplicates by (filename lowercase, size): copies reached via
     *     vault-dir variants and local_link twins of browser_save resources.
     *
     * Deletion mechanics: rows are deleted in the live DB via the same DAO path
     * MediaSearchPathRepository.cleanupLocalLinksFor uses. No per-row tombstone
     * exists or is needed — saved_tabs/resource_files deletions propagate to
     * other devices because SyncStateRefresher recomputes the vault fingerprint
     * at the next snapshot save and bumps lastChangedAt, so the cleaned vault
     * wins the next whole-vault merge. (sync_tombstones is vault-level only and
     * covers whole-vault deletes.)
     *
     * Vault slots pointing at a deleted duplicate are repointed at the
     * surviving twin when one exists, otherwise cleared (next bind refills).
     */
    suspend fun cleanupLocalLinkDuplicates(vaultId: String): LocalLinkCleanupSummary {
        val rows = resourceDao.getForVaultOnce(vaultId)
        val groupFolders = savedTabDao.getTabsForVaultSync(vaultId)
            .filter { it.fileType == "group" }
            .mapNotNull { it.groupFolderName }
        val plan = planLocalLinkCleanup(rows, groupFolders)
        if (plan.isEmpty) {
            return LocalLinkCleanupSummary(0, 0, 0)
        }
        val ids = plan.allIds
        val idSet = ids.toSet()

        // Repoint vault slots that reference a deleted duplicate at its
        // surviving twin so the user doesn't lose an assigned artwork/guide.
        val vault = vaultRepo.getVaultById(vaultId)
        if (vault != null) {
            vault.artworkResourceId?.takeIf { it in idSet }?.let { rid ->
                plan.replacements[rid]?.let { vaultRepo.updateArtworkResourceId(vaultId, it) }
            }
            vault.guideResourceId?.takeIf { it in idSet }?.let { rid ->
                plan.replacements[rid]?.let { vaultRepo.updateGuideResourceId(vaultId, it) }
            }
            vault.mapResourceId?.takeIf { it in idSet }?.let { rid ->
                plan.replacements[rid]?.let { vaultRepo.updateMapResourceId(vaultId, it) }
            }
        }
        // Clear any slot still pointing at a deleted row (no surviving twin).
        for (id in ids) vaultRepo.clearAnySlotPointingAt(id)
        savedTabDao.deleteByResourceIds(ids)
        savedMapDao.deleteByResourceIds(ids)
        resourceDao.deleteByIds(ids)
        mapThumbnailCache?.invalidateAll(ids)

        val summary = LocalLinkCleanupSummary(
            syncArtifacts = plan.syncArtifactIds.size,
            groupOwned = plan.groupOwnedIds.size,
            logicalDuplicates = plan.duplicateIds.size,
        )
        android.util.Log.i(
            "LocalMediaBinder",
            "cleanupLocalLinkDuplicates vault=$vaultId removed=${summary.total} " +
                "(syncArtifacts=${summary.syncArtifacts} groupOwned=${summary.groupOwned} " +
                "logicalDuplicates=${summary.logicalDuplicates})"
        )
        return summary
    }
}

// ── pure helpers (top-level so they're unit-testable without Android) ─────────

/**
 * URL-decodes a content:// document URI so path segments like
 * `primary%3AGameTwin%2FLic%2F.stversions` become inspectable
 * (`primary:GameTwin/Lic/.stversions`). Non-content paths pass through
 * unchanged. Literal `+` is preserved (SAF encodes spaces as %20, never +).
 */
internal fun decodeAssetPath(path: String?): String {
    if (path.isNullOrEmpty()) return ""
    if (!path.startsWith("content://")) return path
    return try {
        java.net.URLDecoder.decode(path.replace("+", "%2B"), "UTF-8")
    } catch (_: Exception) {
        path
    }
}

/**
 * True when [path] (URL-decoded if a content:// URI) contains one of
 * [groupFolders] as a parent DIRECTORY segment (exact segment equality,
 * case-insensitive — emulated storage is case-insensitive but SAF string
 * comparisons aren't). The last segment is the filename and never matches.
 */
internal fun isInsideGroupFolder(path: String?, groupFolders: Collection<String>): Boolean {
    if (path.isNullOrEmpty() || groupFolders.isEmpty()) return false
    val segments = decodeAssetPath(path).split('/').filter { it.isNotEmpty() }
    if (segments.size < 2) return false
    val dirSegments = segments.dropLast(1)
    return dirSegments.any { seg ->
        groupFolders.any { folder -> folder.isNotBlank() && seg.equals(folder, ignoreCase = true) }
    }
}

/**
 * First row in [existingRows] carrying the same (filename lowercase, size) as
 * the candidate — regardless of source or path. Null when none.
 */
internal fun findLogicalDuplicate(
    existingRows: List<ResourceFileEntity>,
    filename: String,
    sizeBytes: Long,
): ResourceFileEntity? = existingRows.firstOrNull {
    it.sizeBytes == sizeBytes && it.filename.equals(filename, ignoreCase = true)
}

/**
 * Cleanup plan for one vault's resource_files rows. Only `source='local_link'`
 * rows are ever planned for deletion; the three id lists are disjoint.
 */
internal data class LocalLinkCleanupPlan(
    /** Rule 1: path contains a dot-prefixed segment (.stversions, .trashed-*). */
    val syncArtifactIds: List<String>,
    /** Rule 2: path lies inside one of the vault's page-group folders. */
    val groupOwnedIds: List<String>,
    /** Rule 3: logical (filename, size) duplicates; one survivor kept per key. */
    val duplicateIds: List<String>,
    /** Rule-3 deleted id -> surviving row id with the same (filename, size). */
    val replacements: Map<String, String> = emptyMap(),
) {
    val allIds: List<String> get() = syncArtifactIds + groupOwnedIds + duplicateIds
    val isEmpty: Boolean get() = syncArtifactIds.isEmpty() && groupOwnedIds.isEmpty() && duplicateIds.isEmpty()
}

/**
 * Classifies which of [rows] (ALL of a vault's resource_files rows — non-local
 * rows are needed as rule-3 dedupe anchors but are never deleted) should be
 * removed, given the vault's page-group folder names. Pure and deterministic;
 * see [LocalMediaBinder.cleanupLocalLinkDuplicates] for the rules.
 */
internal fun planLocalLinkCleanup(
    rows: List<ResourceFileEntity>,
    groupFolders: Collection<String>,
): LocalLinkCleanupPlan {
    val localLinks = rows.filter { it.source == ResourceSource.LOCAL_LINK.raw }
    if (localLinks.isEmpty()) return LocalLinkCleanupPlan(emptyList(), emptyList(), emptyList())

    fun hasDotSegment(row: ResourceFileEntity): Boolean =
        decodeAssetPath(row.absolutePath).split('/').any { it.startsWith(".") }

    val syncArtifacts = localLinks.filter { hasDotSegment(it) }
    val afterRule1 = localLinks - syncArtifacts.toSet()

    val groupOwned = afterRule1.filter { isInsideGroupFolder(it.absolutePath, groupFolders) }
    val afterRule2 = afterRule1 - groupOwned.toSet()

    val nonLocalByKey = rows.asSequence()
        .filter { it.source != ResourceSource.LOCAL_LINK.raw }
        .groupBy { it.filename.lowercase() to it.sizeBytes }

    val duplicates = mutableListOf<ResourceFileEntity>()
    val replacements = mutableMapOf<String, String>()
    for ((key, group) in afterRule2.groupBy { it.filename.lowercase() to it.sizeBytes }) {
        val nonLocalTwin = nonLocalByKey[key]?.firstOrNull()
        if (nonLocalTwin != null) {
            // A browser_save / user_add / scrape resource already carries this
            // file: every local_link copy is redundant.
            duplicates += group
            for (r in group) replacements[r.id] = nonLocalTwin.id
            continue
        }
        if (group.size > 1) {
            // Keep exactly one — prefer the shortest decoded path (the base
            // vault dir beats `<name> (3)/` variants); ties break on path then
            // id for determinism.
            val keeper = group.minWithOrNull(
                compareBy(
                    { decodeAssetPath(it.absolutePath).length },
                    { decodeAssetPath(it.absolutePath) },
                    { it.id },
                )
            )!!
            val losers = group - keeper
            duplicates += losers
            for (r in losers) replacements[r.id] = keeper.id
        }
    }

    return LocalLinkCleanupPlan(
        syncArtifactIds = syncArtifacts.map { it.id },
        groupOwnedIds = groupOwned.map { it.id },
        duplicateIds = duplicates.map { it.id },
        replacements = replacements,
    )
}
