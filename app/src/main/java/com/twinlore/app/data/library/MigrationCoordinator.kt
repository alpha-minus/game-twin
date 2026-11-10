package com.twinlore.app.data.library

import com.twinlore.app.data.local.ResourceFileDao
import com.twinlore.app.data.local.ResourceFileEntity
import com.twinlore.app.data.local.SavedTabDao
import com.twinlore.app.data.local.ScrollMarkerDao
import com.twinlore.app.data.local.VaultDao
import com.twinlore.app.data.local.VaultEntity
import javax.inject.Inject
import javax.inject.Singleton

sealed class LibraryMigrationProgress {
    object Idle : LibraryMigrationProgress()
    data class Working(val current: Int, val total: Int, val vaultName: String) : LibraryMigrationProgress()
    data class Failed(val vaultId: String, val cause: Throwable) : LibraryMigrationProgress()
    object Done : LibraryMigrationProgress()
}

@Singleton
class MigrationCoordinator(
    private val vaultDao: VaultDao,
    private val resourceDao: ResourceFileDao,
    private val savedTabDao: SavedTabDao,
    private val scrollMarkerDao: ScrollMarkerDao,
    private val libraryAutoSaveObserver: LibraryAutoSaveObserver,
    private val sizeProbe: (String) -> Long = { java.io.File(it).length() },
    private val uuid: (String) -> String = { discriminator ->
        java.util.UUID.nameUUIDFromBytes(discriminator.toByteArray()).toString()
    },
    private val clock: () -> Long = { System.currentTimeMillis() }
) {

    /** Hilt-friendly entry point. Uses production defaults; tests hit the primary ctor. */
    @Inject constructor(
        vaultDao: VaultDao,
        resourceDao: ResourceFileDao,
        savedTabDao: SavedTabDao,
        scrollMarkerDao: ScrollMarkerDao,
        libraryAutoSaveObserver: LibraryAutoSaveObserver
    ) : this(vaultDao, resourceDao, savedTabDao, scrollMarkerDao, libraryAutoSaveObserver, { java.io.File(it).length() })

    /**
     * Orchestrates Phase A0 (legacy resource discovery), Phase B (JSON sidecar
     * import), and Phase C (file relocation) for every vault.
     *
     * Phase A0 reads `vault_metadata.json` under each legacy vault directory to
     * discover artwork / map / guide cache files and inserts the corresponding
     * `resource_files` rows, updating the FK columns on the vault and saved_tab
     * rows accordingly.
     *
     * Phase B imports three sidecar kinds: map markers, page bookmarks, and
     * scroll markers. Scroll markers are pre-cleared before import to avoid
     * duplicates on re-run.
     *
     * Phase C relocates every resource row whose backing file still lives at an
     * absolute legacy path (i.e. [ResourceFileEntity.homeRelativePath] is null).
     * Already-relocated rows are skipped. Per-vault cleanup removes the legacy
     * directory when every file relocated cleanly.
     *
     * @param legacyVaultsRoots Both external and internal app vault roots are
     *   unioned so split installs aren't missed.
     * @param progress Invoked synchronously on the caller's coroutine for every
     *   per-vault state change. Must not throw.
     */
    suspend fun runFullMigration(
        legacyVaultsRoots: List<java.io.File>,
        sidecarImporter: JsonSidecarImporter,
        fileRelocator: FileRelocator,
        progress: (LibraryMigrationProgress) -> Unit
    ) {
        val vaults = vaultDao.getAllVaults()
        val total = vaults.size
        vaults.forEachIndexed { idx, v ->
            progress(LibraryMigrationProgress.Working(idx + 1, total, v.name))
            val legacyVaultDirs = legacyVaultsRoots.map { java.io.File(it, v.id) }.filter { it.isDirectory }
            if (legacyVaultDirs.isEmpty()) return@forEachIndexed

            // Phase A0: discover and insert resource_files rows for this vault.
            for (legacyVaultDir in legacyVaultDirs) {
                try {
                    discoverLegacyResources(v, legacyVaultDir)
                } catch (e: Throwable) {
                    progress(LibraryMigrationProgress.Failed(v.id, e))
                }
            }

            // Phase B (unchanged): JSON sidecar imports.
            for (legacyVaultDir in legacyVaultDirs) {
                try {
                    val mapDirs = java.io.File(legacyVaultDir, "map")
                        .listFiles()
                        ?.filter { it.isDirectory }
                        ?: emptyList()
                    for (mapDir in mapDirs) {
                        val image = mapDir.listFiles()?.firstOrNull {
                            it.extension in setOf("png", "jpg", "jpeg", "webp")
                        } ?: continue
                        val resourceRow = resourceDao.findByVaultFilenameAndSize(v.id, image.name, image.length())
                            ?: continue
                        sidecarImporter.importMapMarkers(mapDir, resourceRow.id)
                    }
                    val tabs = savedTabDao.getTabsForVaultSync(v.id)
                    for (tab in tabs) {
                        val bookmarkDir = java.io.File(legacyVaultDir, "guide/${tab.id}")
                        if (!bookmarkDir.isDirectory) continue
                        sidecarImporter.importPageBookmarks(bookmarkDir, tab.id)
                        scrollMarkerDao.deleteForTab(tab.id)
                        sidecarImporter.importScrollMarkers(bookmarkDir, tab.id)
                    }
                } catch (e: Throwable) {
                    progress(LibraryMigrationProgress.Failed(v.id, e))
                }
            }

            // Phase C: relocate every resource_files row that still points at the
            // legacy disk path.
            var allRelocated = true
            try {
                val rows = resourceDao.getForVaultOnce(v.id)
                val safeVaultName = LibraryFolderManager.sanitizeForPath(v.name)
                for (row in rows) {
                    if (row.absolutePath != null && row.homeRelativePath == null) {
                        val subdir = deriveSubdir(row)
                        val ok = fileRelocator.relocate(row, safeVaultName, subdir)
                        if (!ok) allRelocated = false
                    }
                }
            } catch (e: Throwable) {
                progress(LibraryMigrationProgress.Failed(v.id, e))
                allRelocated = false
            }

            // Per-vault cleanup: drop legacy directories if everything moved cleanly.
            if (allRelocated) {
                legacyVaultDirs.forEach { it.deleteRecursively() }
            }
        }
        // Persist the populated library to the new home folder immediately,
        // so `library.gametwin` is there as soon as the user lands on the main
        // UI instead of waiting for the first ON_PAUSE.
        runCatching { libraryAutoSaveObserver.saveNow() }
        progress(LibraryMigrationProgress.Done)
    }

    /**
     * Derive the per-vault subdirectory for a resource row from its legacy
     * absolutePath. Examples (input -> output):
     *   ".../<vault-id>/art/artwork.png"                -> "art"
     *   ".../<vault-id>/map/map_1234.png"               -> "map"
     *   ".../<vault-id>/guide/<bookmark-id>/guide.mht"  -> "guide/<bookmark-id>"
     *
     * Falls back to `row.kind` when the path doesn't match the expected
     * structure so we never silently place files at a wrong-looking subdir.
     */
    private fun deriveSubdir(row: ResourceFileEntity): String {
        val path = row.absolutePath ?: return row.kind
        val parts = path.split('/')
        return when (row.kind) {
            "guide" -> {
                // Need at least 4 segments to the right of the legacy root so
                // bookmarkIdx isn't pointing at the "guide" literal itself.
                if (parts.size < 4) return row.kind
                val bookmarkId = parts[parts.lastIndex - 1]
                if (bookmarkId == "guide") return row.kind
                "guide/$bookmarkId"
            }
            else -> row.kind
        }
    }

    private suspend fun discoverLegacyResources(vault: VaultEntity, legacyVaultDir: java.io.File) {
        val metadataFile = java.io.File(legacyVaultDir, "vault_metadata.json")
        val metadata = if (metadataFile.isFile) {
            runCatching { org.json.JSONObject(metadataFile.readText()) }.getOrNull()
        } else null

        // Artwork.
        if (vault.artworkResourceId == null) {
            val artworkRel = metadata?.optString("artworkPath")
            if (!artworkRel.isNullOrBlank()) {
                val artworkFile = java.io.File(legacyVaultDir, artworkRel)
                if (artworkFile.isFile) {
                    val row = makeResourceRow(vault.id, "artwork", artworkFile)
                    resourceDao.insert(row)
                    vaultDao.updateArtworkResourceId(vault.id, row.id)
                }
            }
        }

        // Map.
        if (vault.mapResourceId == null) {
            val mapRel = metadata?.optString("mapPath")
            if (!mapRel.isNullOrBlank()) {
                val mapFile = java.io.File(legacyVaultDir, mapRel)
                if (mapFile.isFile) {
                    val row = makeResourceRow(vault.id, "map", mapFile)
                    resourceDao.insert(row)
                    vaultDao.updateMapResourceId(vault.id, row.id)
                }
            }
        }

        // Saved-guide offline cache (MHT or pre-MHT zip).
        val tabs = savedTabDao.getTabsForVaultSync(vault.id)
        for (tab in tabs) {
            if (tab.resourceId != null) continue  // already migrated on a previous run
            val guideDir = java.io.File(legacyVaultDir, "guide/${tab.id}")
            if (!guideDir.isDirectory) continue

            val mht = java.io.File(guideDir, "guide.mht").takeIf { it.isFile }
            if (mht != null) {
                val row = makeResourceRow(vault.id, "guide", mht)
                resourceDao.insert(row)
                savedTabDao.updateResourceId(tab.id, row.id)
                continue
            }

            // Fall back to *.zip (older pre-MHT format).
            val zipFile = guideDir.listFiles { f -> f.isFile && f.extension.equals("zip", ignoreCase = true) }
                ?.firstOrNull()
            if (zipFile != null) {
                val extracted = extractHtmlFromZip(zipFile, vault.id, tab.id)
                if (extracted != null) {
                    val row = makeResourceRow(vault.id, "guide", extracted)
                    resourceDao.insert(row)
                    savedTabDao.updateResourceId(tab.id, row.id)
                }
            }
        }
    }

    /**
     * Extract the main HTML entry from a legacy zipped bookmark to a temp file.
     * Returns the temp file or null if no HTML entry was found / extraction failed.
     * Drops asset entries — image refs inside the HTML pointing at the zip's
     * `assets/...` will 404 on next read. The saved_tab URL is preserved for
     * live re-fetch.
     *
     * The zip entry name is only used as a filter predicate; no File object is
     * constructed from it. The output path is a fixed sanitized name in a
     * controlled temp dir, so there is no zip-slip risk on either read or write
     * side.
     *
     * Note: if Phase C fails permanently for this row, the temp file accumulates
     * across retries until the OS tmp cleanup runs. Acceptable at migration scale.
     */
    private fun extractHtmlFromZip(zipFile: java.io.File, vaultId: String, tabId: String): java.io.File? {
        val zip = runCatching { java.util.zip.ZipFile(zipFile) }.getOrNull() ?: return null
        return zip.use { z ->
            val entries = z.entries().toList()
            val htmlEntry = entries.firstOrNull { !it.isDirectory && it.name.endsWith("index.html", ignoreCase = true) }
                ?: entries.firstOrNull { !it.isDirectory && it.name.endsWith(".html", ignoreCase = true) }
                ?: return@use null
            val tempDir = java.io.File(System.getProperty("java.io.tmpdir") ?: "/tmp", "twinlore-mig").apply { mkdirs() }
            val outFile = java.io.File(tempDir, "$vaultId-$tabId-guide.html")
            z.getInputStream(htmlEntry).use { input ->
                outFile.outputStream().use { input.copyTo(it) }
            }
            outFile
        }
    }

    private fun makeResourceRow(
        vaultId: String,
        kind: String,
        file: java.io.File
    ): ResourceFileEntity {
        val now = clock()
        return ResourceFileEntity(
            id = uuid("$vaultId-$kind-${file.absolutePath}"),
            vaultId = vaultId,
            kind = kind,
            filename = file.name,
            sizeBytes = file.length(),
            homeRelativePath = null,
            absolutePath = file.absolutePath,
            mimeType = null,
            source = "migrated",
            lastSeenAt = now,
            createdAt = now
        )
    }
}
