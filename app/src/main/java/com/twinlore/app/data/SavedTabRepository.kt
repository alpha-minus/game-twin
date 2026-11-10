package com.twinlore.app.data

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.twinlore.app.data.library.LibrarySaveHelpers
import com.twinlore.app.data.library.LibraryFolderManager
import com.twinlore.app.data.library.ResourceResolver
import com.twinlore.app.data.local.PageBookmarkDao
import com.twinlore.app.data.local.PageBookmarkEntity
import com.twinlore.app.data.local.ResourceFileDao
import com.twinlore.app.data.local.ResourceFileEntity
import com.twinlore.app.data.local.SavedTabDao
import com.twinlore.app.data.local.SavedTabEntityMapper
import com.twinlore.app.data.local.ScrollMarkerDao
import com.twinlore.app.data.local.ScrollMarkerEntity
import com.twinlore.app.data.local.VaultDao
import com.twinlore.app.domain.PdfPageBookmark
import com.twinlore.app.domain.Result
import com.twinlore.app.domain.SavedTab
import com.twinlore.app.domain.ScrollMarker
import com.twinlore.app.domain.ScrollRestoreData
import dagger.hilt.android.qualifiers.ApplicationContext
import com.twinlore.app.domain.ResourceKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for bookmark / saved-tab operations.
 *
 * Bookmark metadata lives in [SavedTabEntity]
 * columns plus the [ScrollMarkerDao] / [PageBookmarkDao] tables, and offline
 * file content is written into the user's home folder via [LibraryFolderManager]
 * and tracked in [ResourceFileDao].
 */
@Singleton
class SavedTabRepository @Inject constructor(
    private val savedTabDao: SavedTabDao,
    private val scrollMarkerDao: ScrollMarkerDao,
    private val pageBookmarkDao: PageBookmarkDao,
    private val resourceFileDao: ResourceFileDao,
    private val resourceResolver: ResourceResolver,
    private val libraryFolderManager: LibraryFolderManager,
    private val vaultDao: VaultDao,
    private val pageGroupRepository: PageGroupRepository,
    @ApplicationContext private val context: Context
) : BaseRepository() {
    companion object {
        private const val TAG = "SavedTabRepository"
        const val MAX_SCROLL_MARKERS = 100
        private const val MAX_PAGE_BOOKMARKS = 50
    }

    /**
     * Get all tabs for a vault
     */
    fun getTabsForVault(vaultId: String): Flow<List<SavedTab>> {
        return savedTabDao.getTabsForVault(vaultId)
            .map { entities -> SavedTabEntityMapper.toDomainList(entities) }
    }

    /**
     * Observe link-type bookmarks for a vault. Surfaced on the landing page.
     */
    fun getLinkBookmarksForVault(vaultId: String): Flow<List<SavedTab>> {
        return savedTabDao.getLinkBookmarksForVault(vaultId)
            .map { entities -> SavedTabEntityMapper.toDomainList(entities) }
    }

    /**
     * Get all tabs for a vault (suspend version)
     */
    suspend fun getTabsForVaultSync(vaultId: String): List<SavedTab> {
        val entities = savedTabDao.getTabsForVaultSync(vaultId)
        return SavedTabEntityMapper.toDomainList(entities)
    }

    /**
     * Get tab by ID
     */
    suspend fun getTabById(id: String): SavedTab? {
        val entity = savedTabDao.getTabById(id)
        return entity?.let { SavedTabEntityMapper.toDomain(it) }
    }

    /**
     * Get favorite tab for a vault
     */
    suspend fun getFavoriteTab(vaultId: String): SavedTab? {
        val entity = savedTabDao.getFavoriteTab(vaultId)
        return entity?.let { SavedTabEntityMapper.toDomain(it) }
    }

    /**
     * Get tab marked as "Map" for a vault
     */
    suspend fun getMapTab(vaultId: String): SavedTab? {
        val entity = savedTabDao.getMapTab(vaultId)
        return entity?.let { SavedTabEntityMapper.toDomain(it) }
    }

    /**
     * Get tab marked as "Current Content" for a vault
     */
    suspend fun getCurrentContentTab(vaultId: String): SavedTab? {
        val entity = savedTabDao.getCurrentContentTab(vaultId)
        return entity?.let { SavedTabEntityMapper.toDomain(it) }
    }

    /**
     * Create a new tab
     */
    suspend fun createTab(
        vaultId: String,
        title: String = "",
        url: String = SearchEngine.GOOGLE.homeUrl
    ): Result<SavedTab> {
        return try {
            val maxOrder = savedTabDao.getMaxOrder(vaultId) ?: -1
            val tab = SavedTab(
                id = UUID.randomUUID().toString(),
                vaultId = vaultId,
                title = title,
                url = url,
                scrollPosition = 0,
                zoomLevel = 1.0f,
                isFavorite = false,
                isMap = false,
                isCurrentContent = false,
                order = maxOrder + 1,
                createdAt = Date(),
                lastAccessed = Date(),
                fileType = "html"
            )
            savedTabDao.insertTab(SavedTabEntityMapper.toEntity(tab))
            Result.success(tab)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Create a PDF guide from a source file (streaming, avoids loading into RAM).
     */
    suspend fun createPdfGuideFromFile(vaultId: String, sourceFile: File, fileName: String): Result<SavedTab> = withContext(Dispatchers.IO) {
        try {
            createGuideFromSourceFile(
                vaultId = vaultId,
                source = sourceFile,
                originalFileName = fileName,
                fileType = "pdf",
                mimeType = "application/pdf",
                url = ""
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error creating PDF guide from file", e)
            Result.failure(e)
        }
    }

    /**
     * Create a TXT guide from raw text content.
     */
    suspend fun createTxtGuide(vaultId: String, txtContent: String, fileName: String): Result<SavedTab> = withContext(Dispatchers.IO) {
        try {
            createGuideFromBytes(
                vaultId = vaultId,
                bytes = txtContent.toByteArray(StandardCharsets.UTF_8),
                originalFileName = fileName,
                fileType = "txt",
                mimeType = "text/plain",
                url = ""
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error creating TXT guide", e)
            Result.failure(e)
        }
    }

    /**
     * Create an MHT guide from a source file (.mht web archive). Stored under the
     * vault's home-folder sub-folder; loaded via MhtParser at view time.
     */
    suspend fun createMhtGuide(vaultId: String, sourceFile: File, fileName: String, url: String = ""): Result<SavedTab> = withContext(Dispatchers.IO) {
        try {
            createGuideFromSourceFile(
                vaultId = vaultId,
                source = sourceFile,
                originalFileName = fileName,
                fileType = "mht",
                mimeType = "multipart/related",
                url = url
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error creating MHT guide", e)
            Result.failure(e)
        }
    }

    /**
     * Create an EPUB guide from a source file. Stored under the vault's home-folder
     * sub-folder; parsed and cached at view time by EpubCacheManager — same flow
     * as MHT, with the EPUB-specific parser.
     */
    suspend fun createEpubGuide(vaultId: String, sourceFile: File, fileName: String): Result<SavedTab> = withContext(Dispatchers.IO) {
        try {
            createGuideFromSourceFile(
                vaultId = vaultId,
                source = sourceFile,
                originalFileName = fileName,
                fileType = "epub",
                mimeType = "application/epub+zip",
                url = ""
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error creating EPUB guide", e)
            Result.failure(e)
        }
    }

    /** Store an already-slimmed HTML-bundle zip (code wheel or generic) as a
     *  guide. [fileType] is "wheel" or "htmlzip" (resolved by HtmlZipImporter). */
    suspend fun createHtmlZipGuide(
        vaultId: String,
        sourceFile: File,
        fileName: String,
        fileType: String,
    ): Result<SavedTab> = withContext(Dispatchers.IO) {
        try {
            createGuideFromSourceFile(
                vaultId = vaultId,
                source = sourceFile,
                originalFileName = fileName,
                fileType = fileType,
                mimeType = "application/zip",
                url = ""
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error creating HTML-zip guide", e)
            Result.failure(e)
        }
    }

    /**
     * Create an HTML guide from a source file. Stored under the vault's home-folder
     * sub-folder as a single .html file; the HTML guide loader honors bookmark.resourceId
     * and loads with `assetManifest = emptyMap()` (no manifest, no assets dir).
     */
    suspend fun createHtmlGuide(vaultId: String, sourceFile: File, fileName: String, url: String = ""): Result<SavedTab> = withContext(Dispatchers.IO) {
        try {
            createGuideFromSourceFile(
                vaultId = vaultId,
                source = sourceFile,
                originalFileName = fileName,
                fileType = "html",
                mimeType = "text/html",
                url = url
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error creating HTML guide", e)
            Result.failure(e)
        }
    }

    /**
     * Update PDF reading position for a guide (page number and scroll offset within page).
     */
    suspend fun updatePdfState(guideId: String, lastPage: Int, scrollOffset: Float): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val tab = getTabById(guideId) ?: return@withContext Result.failure(Exception("Guide not found"))
            val updated = tab.copy(lastPdfPage = lastPage.coerceAtLeast(0), pdfScrollOffset = scrollOffset.coerceIn(0f, 1f))
            savedTabDao.updateTab(SavedTabEntityMapper.toEntity(updated))
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating PDF state", e)
            Result.failure(e)
        }
    }

    /**
     * Update tab
     */
    suspend fun updateTab(tab: SavedTab): Result<Unit> {
        return try {
            savedTabDao.updateTab(SavedTabEntityMapper.toEntity(tab))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Delete tab, and the file it is the handle on.
     *
     * For fileType="group" rows, delegates to [PageGroupRepository.deleteGroup]
     * so the on-disk group folder, the kind="group_page" resource_files rows,
     * and the find-sidecar cache all get cleaned up — `savedTabDao.deleteTabById`
     * alone only cascades `page_group_entries` via the FK and leaves everything
     * else behind.
     *
     * Every other file-backed kind now gets the same treatment through
     * [deleteBackingFile]. It used to get only the row, and the file stayed in
     * the library folder with nothing pointing at it. That is not a leak you
     * can ignore, because it is not permanent: `LocalMediaBinder.ensureSavedTab`
     * decides whether to register a guide by asking whether a tab already
     * points at the resource, so the delete removed the very thing that was
     * suppressing re-registration. The guide came back on the picker's Sync
     * and on the reindex that a rebuilt library runs.
     *
     * A tab with no [SavedTabEntity.resourceId] is a link rather than a file,
     * and owns nothing on disk to delete.
     */
    suspend fun deleteTab(tabId: String): Result<Unit> {
        return try {
            val existing = savedTabDao.getTabById(tabId)
            if (existing?.fileType.equals("group", ignoreCase = true)) {
                pageGroupRepository.deleteGroup(tabId)
            } else {
                // On disk first, for the same reason the group path does it
                // first: resolving a resource reads its row, and after the
                // delete there is nothing left to resolve it from.
                existing?.resourceId?.let { deleteBackingFile(it, existing.vaultId) }
                savedTabDao.deleteTabById(tabId)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Takes the file off disk, drops its `resource_files` row, and clears the
     * vault slot that pointed at it.
     *
     * The slot matters as much as the file: a vault's guideResourceId naming a
     * row that no longer exists is a vault claiming a guide it cannot open.
     * The resolver caches URIs by resource id, so its entry goes too, or the
     * next lookup answers with the path of a file that is gone.
     *
     * Deletion goes through [LibraryFolderManager.deleteFileUnderHome] and nowhere else.
     * This used to be a local helper that called
     * `DocumentFile.fromSingleUri(context, uri).delete()`, which silently does
     * nothing on the tree-document URIs [ResourceResolver] returns and hands
     * back a false that nobody read. On a SAF library folder the row went and
     * the file stayed, and the next rescan registered the guide again. The
     * same trap is written up at `PageGroupSaveTargets.kt:95`.
     */
    private suspend fun deleteBackingFile(resourceId: String, vaultId: String) {
        resourceResolver.resolve(resourceId)?.let { libraryFolderManager.deleteFileUnderHome(it) }
        resourceResolver.invalidate(resourceId)
        resourceFileDao.deleteById(resourceId)
        if (vaultDao.getVaultById(vaultId)?.guideResourceId == resourceId) {
            vaultDao.updateGuideResourceId(vaultId, null)
        }
    }

    /**
     * Remove every guide from [vaultId] but keep the vault (the library's
     * Delete multiple, "Guides only"). Each guide goes through [deleteBookmark],
     * the guide picker's own delete, so page groups and backing files in the
     * library folder go with it; files linked from other folders are left alone.
     * Map tabs stay. Guide files no tab points at any more are removed too, since
     * freeing space is the point. Returns how many guides were removed.
     */
    suspend fun deleteAllGuidesForVault(vaultId: String): Int = withContext(Dispatchers.IO) {
        var removed = 0
        for (tab in savedTabDao.getTabsForVaultSync(vaultId)) {
            if (tab.isMap) continue
            if (deleteBookmark(tab.id).isSuccess()) {
                removed++
                java.io.File(context.cacheDir, "code_wheel_render/${tab.id}").deleteRecursively()
            }
        }
        val stillUsed = savedTabDao.getTabsForVaultSync(vaultId).mapNotNull { it.resourceId }.toSet()
        for (row in resourceFileDao.getForVaultAndKindOnce(vaultId, ResourceKind.GUIDE.raw)) {
            if (row.id in stillUsed) continue
            runCatching { deleteBackingFile(row.id, vaultId) }
                .onFailure { Log.w(TAG, "Could not remove guide file ${row.id}", it) }
        }
        removed
    }

    /**
     * Delete all tabs for a vault
     */
    suspend fun deleteTabsForVault(vaultId: String): Result<Unit> {
        return try {
            savedTabDao.deleteTabsForVault(vaultId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Update last accessed timestamp
     */
    suspend fun updateLastAccessed(tabId: String): Result<Unit> {
        return try {
            savedTabDao.updateLastAccessed(tabId, Date())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Create a lightweight URL shortcut bookmark (e.g. YouTube). No offline file is
     * written; the URL is stored on the tab and the browser navigates to it on open.
     */
    suspend fun createLinkBookmark(
        vaultId: String,
        url: String,
        title: String,
        zoomLevel: Float = 1.0f,
        faviconBytes: ByteArray? = null
    ): Result<SavedTab> = withContext(Dispatchers.IO) {
        try {
            val bookmarkId = UUID.randomUUID().toString()
            val maxOrder = savedTabDao.getMaxOrder(vaultId) ?: -1
            val createdAt = Date()
            val bookmark = SavedTab(
                id = bookmarkId,
                vaultId = vaultId,
                title = title,
                url = url,
                scrollPosition = 0,
                zoomLevel = zoomLevel,
                isFavorite = false,
                isMap = false,
                isCurrentContent = false,
                order = maxOrder + 1,
                createdAt = createdAt,
                lastAccessed = createdAt,
                fileType = "link",
                faviconBytes = faviconBytes
            )
            savedTabDao.insertTab(SavedTabEntityMapper.toEntity(bookmark))
            Log.d(TAG, "Created link bookmark: id=$bookmarkId, title=$title, url=$url, hasFavicon=${faviconBytes != null}")
            Result.success(bookmark)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating link bookmark", e)
            Result.failure(e)
        }
    }

    /**
     * Re-inserts a previously-deleted link bookmark verbatim, preserving id and
     * all fields. Mirror of BrowserTabRepository.restoreTab; used by the undo
     * affordance on the landing page.
     */
    suspend fun restoreLinkBookmark(tab: SavedTab): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            savedTabDao.insertTab(SavedTabEntityMapper.toEntity(tab))
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "restoreLinkBookmark failed", e)
            Result.failure(e)
        }
    }

    /**
     * Delete bookmark and its underlying file (if any).
     *
     * Removes the resource_files row and the on-disk content for guides backed by
     * the home folder, then deletes the saved_tabs row. Linked-cascade DAOs handle
     * scroll markers and page bookmarks via foreign-key ON DELETE CASCADE.
     */
    suspend fun deleteBookmark(bookmarkId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val bookmark = getTabById(bookmarkId)
            if (bookmark == null) {
                Log.w(TAG, "Bookmark not found: $bookmarkId")
                return@withContext Result.failure(Exception("Bookmark not found"))
            }

            // Page-groups own a per-group on-disk folder + a per-group sidecar
            // cache + N "group_page" resource_files rows; delegate to the
            // page-group repository so all of that is reaped together. The
            // generic single-page path below would only clear one
            // resource_files row (none, in the group case — the saved_tabs
            // row has no `resource_id`) and leave the folder behind.
            if (bookmark.fileType.equals("group", ignoreCase = true)) {
                pageGroupRepository.deleteGroup(bookmarkId)
                Log.d(TAG, "Deleted page-group via PageGroupRepository: $bookmarkId")
                return@withContext Result.success(Unit)
            }

            bookmark.resourceId?.let { deleteBackingFile(it, bookmark.vaultId) }

            savedTabDao.deleteTabById(bookmarkId)

            Log.d(TAG, "Deleted bookmark: $bookmarkId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting bookmark", e)
            Result.failure(e)
        }
    }

    /**
     * Get scroll markers for a bookmark (from Room).
     */
    suspend fun getScrollMarkers(vaultId: String, bookmarkId: String): List<ScrollMarker> = withContext(Dispatchers.IO) {
        scrollMarkerDao.getForTab(bookmarkId).first().map { it.toDomain() }
    }

    /**
     * Observe scroll markers for a saved tab (bookmark) as a Flow. Used by
     * SingleGuideViewerViewModel and the future single-guide viewer code path
     * so marker creation in the body reflects live in the ToC Markers tab.
     */
    fun getScrollMarkersFlow(bookmarkId: String): Flow<List<ScrollMarker>> =
        scrollMarkerDao.getForTab(bookmarkId).map { rows -> rows.map { it.toDomain() } }

    /**
     * Get scroll markers and the last focused marker id for a bookmark.
     */
    suspend fun getScrollMarkersWithFocusedId(vaultId: String, bookmarkId: String): Pair<List<ScrollMarker>, String?> = withContext(Dispatchers.IO) {
        val markers = scrollMarkerDao.getForTab(bookmarkId).first().map { it.toDomain() }
        val tab = savedTabDao.getTabById(bookmarkId)
        val focusedId = tab?.focusedScrollMarkerId?.takeIf { it.isNotBlank() }
        Pair(markers, focusedId)
    }

    /**
     * Persist the focused scroll marker id for a bookmark.
     */
    suspend fun setFocusedScrollMarkerId(vaultId: String, bookmarkId: String, markerId: String?): Result<Unit> = withContext(Dispatchers.IO) {
        val tab = savedTabDao.getTabById(bookmarkId)
            ?: return@withContext Result.failure(Exception("Bookmark not found"))
        savedTabDao.updateTab(tab.copy(focusedScrollMarkerId = markerId?.takeIf { it.isNotBlank() }))
        Result.success(Unit)
    }

    /**
     * Add a scroll marker. Fails if already at MAX_SCROLL_MARKERS.
     * occurrenceIndex: 1-based index of the label in the page (0 = use scrollY fallback when restoring).
     */
    suspend fun addScrollMarker(vaultId: String, bookmarkId: String, label: String, scrollY: Int, occurrenceIndex: Int = 0): Result<ScrollMarker> = withContext(Dispatchers.IO) {
        val tab = savedTabDao.getTabById(bookmarkId)
            ?: return@withContext Result.failure(Exception("Bookmark not found"))
        val current = scrollMarkerDao.getForTab(bookmarkId).first()
        if (current.size >= MAX_SCROLL_MARKERS) {
            return@withContext Result.failure(Exception("Max scroll markers ($MAX_SCROLL_MARKERS) reached"))
        }
        val newId = UUID.randomUUID().toString()
        val newMarker = ScrollMarker(
            id = newId,
            label = label.trim().take(200).ifEmpty { "Marker ${current.size + 1}" },
            scrollY = scrollY,
            order = current.size,
            occurrenceIndex = occurrenceIndex.coerceIn(0, Int.MAX_VALUE)
        )
        scrollMarkerDao.insert(
            ScrollMarkerEntity(
                id = newMarker.id,
                savedTabId = bookmarkId,
                scrollPosition = newMarker.scrollY,
                markerOrder = newMarker.order,
                createdAt = System.currentTimeMillis(),
                label = newMarker.label,
                occurrenceIndex = newMarker.occurrenceIndex
            )
        )
        savedTabDao.updateTab(tab.copy(focusedScrollMarkerId = newMarker.id))
        Result.success(newMarker)
    }

    /**
     * Delete a single scroll marker by id and rewrite the order field on remaining
     * markers so they stay 0..N-1.
     */
    suspend fun deleteScrollMarker(vaultId: String, bookmarkId: String, markerId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val current = scrollMarkerDao.getForTab(bookmarkId).first()
        if (current.none { it.id == markerId }) {
            return@withContext Result.failure(Exception("Marker not found"))
        }
        scrollMarkerDao.deleteById(markerId)
        val remaining = current.filter { it.id != markerId }.mapIndexed { index, m ->
            m.copy(markerOrder = index)
        }
        if (remaining.isNotEmpty()) {
            scrollMarkerDao.insertAll(remaining)
        }
        // Clear focused marker pointer if it was the deleted one.
        val tab = savedTabDao.getTabById(bookmarkId)
        if (tab != null && tab.focusedScrollMarkerId == markerId) {
            savedTabDao.updateTab(tab.copy(focusedScrollMarkerId = null))
        }
        Result.success(Unit)
    }

    /**
     * Delete all scroll markers for a bookmark.
     */
    suspend fun deleteAllScrollMarkers(vaultId: String, bookmarkId: String): Result<Unit> = withContext(Dispatchers.IO) {
        scrollMarkerDao.deleteForTab(bookmarkId)
        val tab = savedTabDao.getTabById(bookmarkId)
        if (tab != null && tab.focusedScrollMarkerId != null) {
            savedTabDao.updateTab(tab.copy(focusedScrollMarkerId = null))
        }
        Result.success(Unit)
    }

    /**
     * Persist a per-bookmark text-zoom level. Used by the offline guide
     * viewers when the user moves the Display Settings 'Resize text'
     * slider. Decoupled from updateBookmarkScrollRestore so a zoom
     * change doesn't have to re-write the (possibly stale) scroll
     * payload that lives in the same row.
     */
    suspend fun updateBookmarkZoomLevel(bookmarkId: String, zoomLevel: Float): Result<Unit> = withContext(Dispatchers.IO) {
        val tab = savedTabDao.getTabById(bookmarkId)
            ?: return@withContext Result.failure(Exception("Bookmark not found"))
        savedTabDao.updateTab(tab.copy(zoomLevel = zoomLevel))
        Result.success(Unit)
    }

    /**
     * Update the scroll-restore payload (anchor + ratio) for a bookmark.
     * Optionally updates zoom level too.
     */
    suspend fun updateBookmarkScrollRestore(vaultId: String, bookmarkId: String, scrollRestore: ScrollRestoreData, updatedZoomLevel: Float? = null): Result<Unit> = withContext(Dispatchers.IO) {
        val tab = savedTabDao.getTabById(bookmarkId)
            ?: return@withContext Result.failure(Exception("Bookmark not found"))
        val updated = tab.copy(
            scrollNormalizedAnchor = scrollRestore.normalizedAnchor,
            scrollOccurrenceIndex = scrollRestore.occurrenceIndex,
            scrollRatio = scrollRestore.scrollRatio,
            zoomLevel = updatedZoomLevel ?: tab.zoomLevel
        )
        savedTabDao.updateTab(updated)
        Result.success(Unit)
    }

    suspend fun getPdfPageBookmarks(vaultId: String, guideId: String): List<PdfPageBookmark> = withContext(Dispatchers.IO) {
        pageBookmarkDao.getForTab(guideId).first().mapIndexed { index, e ->
            PdfPageBookmark(
                id = e.id,
                name = e.label.orEmpty().ifEmpty { "Page ${e.scrollPosition}" },
                pageNumber = e.scrollPosition,
                order = index
            )
        }
    }

    suspend fun addPdfPageBookmark(vaultId: String, guideId: String, name: String, pageNumber: Int): Result<PdfPageBookmark> = withContext(Dispatchers.IO) {
        try {
            val current = pageBookmarkDao.getForTab(guideId).first()
            if (current.size >= MAX_PAGE_BOOKMARKS) {
                return@withContext Result.failure(Exception("Max page bookmarks ($MAX_PAGE_BOOKMARKS) reached"))
            }
            val bookmark = PdfPageBookmark(
                id = UUID.randomUUID().toString(),
                name = name.trim().ifEmpty { "Page $pageNumber" },
                pageNumber = pageNumber,
                order = current.size
            )
            pageBookmarkDao.insert(
                PageBookmarkEntity(
                    id = bookmark.id,
                    savedTabId = guideId,
                    label = bookmark.name,
                    scrollPosition = bookmark.pageNumber,
                    createdAt = System.currentTimeMillis()
                )
            )
            Result.success(bookmark)
        } catch (e: Exception) {
            Log.e(TAG, "Error adding PDF page bookmark", e)
            Result.failure(e)
        }
    }

    suspend fun deletePdfPageBookmark(vaultId: String, guideId: String, bookmarkId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            pageBookmarkDao.deleteById(bookmarkId)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting PDF page bookmark", e)
            Result.failure(e)
        }
    }

    // ─── Private helpers ────────────────────────────────────────────────────

    private fun ScrollMarkerEntity.toDomain(): ScrollMarker = ScrollMarker(
        id = id,
        label = label,
        scrollY = scrollPosition,
        order = markerOrder,
        occurrenceIndex = occurrenceIndex
    )

    /**
     * Common path for guide creation from raw bytes: stages a temp file under
     * `cacheDir`, then defers to [moveIntoHomeAndAttach].
     */
    private suspend fun createGuideFromBytes(
        vaultId: String,
        bytes: ByteArray,
        originalFileName: String,
        fileType: String,
        mimeType: String,
        url: String,
        explicitTitle: String? = null,
        zoomLevel: Float = 1.0f,
        scrollRestore: ScrollRestoreData? = null
    ): Result<SavedTab> {
        val tmp = File(context.cacheDir, "stab_${UUID.randomUUID()}")
        tmp.outputStream().use { it.write(bytes) }
        return moveIntoHomeAndAttach(
            vaultId = vaultId,
            cacheFile = tmp,
            suggestedFilename = sanitizeFilename(originalFileName.ifBlank { "guide.$fileType" }),
            fileType = fileType,
            mimeType = mimeType,
            url = url,
            explicitTitle = explicitTitle,
            zoomLevel = zoomLevel,
            scrollRestore = scrollRestore
        )
    }

    /**
     * Common path for guide creation from a source File: copies into `cacheDir` first
     * to avoid moving across mount points, then defers to [moveIntoHomeAndAttach].
     */
    private suspend fun createGuideFromSourceFile(
        vaultId: String,
        source: File,
        originalFileName: String,
        fileType: String,
        mimeType: String,
        url: String
    ): Result<SavedTab> {
        val tmp = File(context.cacheDir, "stab_${UUID.randomUUID()}")
        source.copyTo(tmp, overwrite = true)
        return moveIntoHomeAndAttach(
            vaultId = vaultId,
            cacheFile = tmp,
            suggestedFilename = sanitizeFilename(originalFileName.ifBlank { "guide.$fileType" }),
            fileType = fileType,
            mimeType = mimeType,
            url = url
        )
    }

    /**
     * Writes [cacheFile] into the home folder under `<home>/<vaultName>/`, registers
     * a resource_files row, and inserts a SavedTab pointing at it. Mirrors the
     * [com.twinlore.app.data.library.LibrarySaveHelpers.moveIntoHomeAndAttach]
     * pattern; kept inline here to honor the "touch only this file" constraint.
     */
    private suspend fun moveIntoHomeAndAttach(
        vaultId: String,
        cacheFile: File,
        suggestedFilename: String,
        fileType: String,
        mimeType: String,
        url: String,
        explicitTitle: String? = null,
        zoomLevel: Float = 1.0f,
        scrollRestore: ScrollRestoreData? = null
    ): Result<SavedTab> {
        val vault = vaultDao.getVaultById(vaultId)
            ?: run { cacheFile.delete(); return Result.failure(Exception("Vault not found: $vaultId")) }
        val homeUri = libraryFolderManager.homeFolderUri()
            ?: run { cacheFile.delete(); return Result.failure(Exception("Storage folder is not set")) }

        val outcome = when (homeUri.scheme) {
            "file" -> writeToFileHome(homeUri, LibraryFolderManager.resolveFolderName(vault), suggestedFilename, cacheFile)
            "content" -> writeToSafHome(homeUri, LibraryFolderManager.resolveFolderName(vault), suggestedFilename, mimeType, cacheFile)
            else -> {
                cacheFile.delete()
                return Result.failure(Exception("Unsupported home folder scheme: ${homeUri.scheme}"))
            }
        } ?: return Result.failure(Exception("Failed to write into home folder"))

        val resourceId = UUID.randomUUID().toString()
        resourceFileDao.insert(
            ResourceFileEntity(
                id = resourceId,
                vaultId = vaultId,
                kind = "guide",
                filename = outcome.finalFilename,
                sizeBytes = outcome.sizeBytes,
                homeRelativePath = outcome.homeRelativePath,
                absolutePath = null,
                mimeType = mimeType,
                source = "saved_tab_repo",
                lastSeenAt = System.currentTimeMillis(),
                createdAt = System.currentTimeMillis()
            )
        )

        val tabId = UUID.randomUUID().toString()
        val maxOrder = savedTabDao.getMaxOrder(vaultId) ?: -1
        val now = Date()
        val title = (explicitTitle ?: outcome.finalFilename.substringBeforeLast('.')).ifBlank { "Guide" }
        val tab = SavedTab(
            id = tabId,
            vaultId = vaultId,
            title = title,
            url = url,
            scrollPosition = 0,
            zoomLevel = zoomLevel,
            isFavorite = false,
            isMap = false,
            isCurrentContent = false,
            order = maxOrder + 1,
            createdAt = now,
            lastAccessed = now,
            fileType = fileType,
            fileName = outcome.finalFilename,
            lastPdfPage = 0,
            pdfScrollOffset = 0f,
            resourceId = resourceId
        )
        val entity = SavedTabEntityMapper.toEntity(tab).let { e ->
            if (scrollRestore != null) e.copy(
                scrollNormalizedAnchor = scrollRestore.normalizedAnchor,
                scrollOccurrenceIndex = scrollRestore.occurrenceIndex,
                scrollRatio = scrollRestore.scrollRatio
            ) else e
        }
        savedTabDao.insertTab(entity)
        Log.d(TAG, "Created guide: id=$tabId, fileType=$fileType, file=${outcome.finalFilename}")
        return Result.success(SavedTabEntityMapper.toDomain(entity))
    }

    /**
     * Writes [cacheFile] into a `file://` home folder under `<home>/<vaultName>/`.
     */
    private fun writeToFileHome(
        homeUri: Uri,
        vaultName: String,
        suggestedFilename: String,
        cacheFile: File
    ): SaveOutcome? {
        val home = File(homeUri.path ?: return null)
        val targetDir = File(home, vaultName).apply { mkdirs() }
        val target = LibrarySaveHelpers.pickUniqueTarget(targetDir, suggestedFilename)
        if (!cacheFile.renameTo(target)) {
            cacheFile.copyTo(target, overwrite = false)
            cacheFile.delete()
        }
        return SaveOutcome(
            homeRelativePath = target.relativeTo(home).path,
            sizeBytes = target.length(),
            uri = Uri.fromFile(target),
            finalFilename = target.name
        )
    }

    /**
     * Writes [cacheFile] into a SAF (`content://`) home tree under `<home>/<vaultName>/`.
     */
    private suspend fun writeToSafHome(
        homeUri: Uri,
        vaultName: String,
        suggestedFilename: String,
        mimeType: String,
        cacheFile: File
    ): SaveOutcome? {
        val vaultDocUri = libraryFolderManager.findOrCreateChildDir(homeUri, vaultName) ?: return null
        val vaultDoc = DocumentFile.fromTreeUri(context, vaultDocUri) ?: return null
        val uniqueName = pickUniqueDocName(vaultDoc, suggestedFilename)
        val targetDoc = vaultDoc.createFile(mimeType.ifBlank { "application/octet-stream" }, uniqueName) ?: return null
        val ok = context.contentResolver.openOutputStream(targetDoc.uri)?.use { out ->
            cacheFile.inputStream().use { it.copyTo(out) }
            true
        } ?: false
        if (!ok) {
            targetDoc.delete()
            return null
        }
        cacheFile.delete()
        val storedName = targetDoc.name ?: uniqueName
        return SaveOutcome(
            homeRelativePath = "$vaultName/$storedName",
            sizeBytes = targetDoc.length(),
            uri = targetDoc.uri,
            finalFilename = storedName
        )
    }

    /**
     * Returns [name] under SAF [dir] if no doc with that name exists; otherwise
     * appends `-2`, `-3`, ... before the extension.
     */
    private fun pickUniqueDocName(dir: DocumentFile, name: String): String {
        if (dir.findFile(name) == null) return name
        val stem = name.substringBeforeLast('.')
        val ext = if ('.' in name) "." + name.substringAfterLast('.') else ""
        var idx = 2
        while (true) {
            val candidate = "$stem-$idx$ext"
            if (dir.findFile(candidate) == null) return candidate
            idx++
        }
    }

    /** Best-effort delete of an on-disk resource backing a bookmark. */
    internal fun sanitizeFilename(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|]"), " ").replace(Regex("\\s+"), " ").trim()
            // "." and ".." survive the character strip above and would name
            // a directory reference rather than a new guide file.
            .let { if (it.isEmpty() || it == "." || it == "..") "guide" else it }

    private data class SaveOutcome(
        val homeRelativePath: String,
        val sizeBytes: Long,
        val uri: Uri,
        val finalFilename: String
    )
}
