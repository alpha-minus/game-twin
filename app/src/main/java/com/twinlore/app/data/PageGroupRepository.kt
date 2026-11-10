package com.twinlore.app.data

import androidx.room.withTransaction
import com.twinlore.app.data.library.PageGroupManifest
import com.twinlore.app.data.local.PageGroupEntryAnchorDao
import com.twinlore.app.data.local.PageGroupEntryAnchorEntity
import com.twinlore.app.data.local.PageGroupEntryDao
import com.twinlore.app.data.local.PageGroupEntryEntity
import com.twinlore.app.data.local.ResourceFileDao
import com.twinlore.app.data.local.SavedTabDao
import com.twinlore.app.data.local.SavedTabEntity
import com.twinlore.app.data.local.ScrollMarkerDao
import com.twinlore.app.data.local.ScrollMarkerEntity
import com.twinlore.app.data.local.VaultDatabase
import com.twinlore.app.domain.ScrollMarker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single writer of page-group state. Every mutation:
 *   1. updates Room (saved_tabs / page_group_entries / resource_files),
 *   2. rewrites <groupFolder>/manifest.json from the post-mutation Room state.
 *
 * Disk is therefore consistent or one transaction behind, never ahead.
 *
 * The `groupsFolderResolver` returns the on-disk folder for a given
 * groupTabId; production wires it to
 *   `<library>/<vault-folder>/groups/<groupTabId>/`
 * via `LibraryFolderManager` + `PageGroupSaveTargets`. Tests pass a temp
 * directory directly.
 */
/**
 * Resolves the on-disk folder for a given page-group, and performs the
 * scheme-aware on-disk delete that `File.deleteRecursively()` can't do
 * on SAF (`content://`) homes.
 *
 * Production wiring points at `<library>/<vault>/<group folder name>/`
 * (or `<library>/<vault>/groups/<groupTabId>/` for legacy v0 groups);
 * tests pass an inline impl that returns a temp directory. An interface
 * (instead of a raw `suspend (String) -> File`) so Hilt can bind a
 * singleton without tripping over generic erasure on the lambda type.
 */
interface GroupsFolderResolver {
    suspend fun resolve(groupTabId: String): File
    /**
     * Best-effort delete of the on-disk group folder. For file:// homes
     * this is equivalent to `resolve(id).deleteRecursively()`; for SAF
     * homes it walks via `DocumentsContract` so the deletion actually
     * reaches the user-visible storage. Default no-op so test fakes
     * don't have to implement it.
     */
    suspend fun deleteOnDisk(groupTabId: String) {}

    /**
     * Write `manifest.json` into the group's on-disk folder.
     *
     * Overridden in production to go through SAF: [resolve] can only name a real
     * directory for `file://` homes, so a `File`-based write on a SAF home landed
     * in app-private storage and the manifest never reached the library folder —
     * which is why a group synced to another device arrives as bare `page-N.mht`
     * files with nothing describing them. The default keeps the `File` behaviour
     * so test fakes that hand back a temp directory need no changes.
     */
    suspend fun writeManifest(groupTabId: String, json: String) {
        val folder = resolve(groupTabId).apply { mkdirs() }
        File(folder, "manifest.json").writeText(json)
    }
}

/**
 * Bound by `AppModule.providePageGroupRepository` (not via `@Inject` constructor)
 * because the optional [sidecarCache] parameter uses a default value that Hilt
 * does not honor on injected constructors. Tests construct directly + pass null.
 */
class PageGroupRepository(
    private val savedTabDao: SavedTabDao,
    private val resourceFileDao: ResourceFileDao,
    private val pageGroupEntryDao: PageGroupEntryDao,
    private val pageGroupEntryAnchorDao: PageGroupEntryAnchorDao,
    private val scrollMarkerDao: ScrollMarkerDao,
    private val groupsFolderResolver: GroupsFolderResolver,
    private val database: VaultDatabase,
    private val sidecarCache: PageGroupSidecarCache? = null
) {

    data class NewAnchor(val label: String, val scrollPosition: Int)

    data class NewEntry(
        val label: String,
        val savedHref: String,
        val fragment: String?,
        val mhtResourceId: String?,
        val pageFile: String?,
        val textFile: String?,
        val anchors: List<NewAnchor> = emptyList(),
        val synthetic: Boolean = false
    )

    /**
     * Inserts the parent saved_tabs row, the page_group_entries rows (in
     * the supplied order), sets active_entry to the first entry with a
     * non-null mhtResourceId, then writes manifest.json. Returns the
     * groupTabId (either the caller-supplied [groupTabId] or a freshly
     * minted UUID when null).
     *
     * The save pipeline supplies its own [groupTabId] so the row id matches
     * the staging artifacts (sidecar cache, temp file naming) and so the
     * post-save "Open" banner can route directly to the new row.
     */
    suspend fun createGroup(
        vaultId: String,
        title: String,
        indexUrl: String,
        indexPageTitle: String,
        groupFolderName: String? = null,
        entries: List<NewEntry>,
        groupTabId: String = UUID.randomUUID().toString()
    ): String = withContext(Dispatchers.IO) {
        val now = Date()
        val entryRows = entries.mapIndexed { i, e ->
            PageGroupEntryEntity(
                id = UUID.randomUUID().toString(),
                groupTabId = groupTabId,
                position = i,
                label = e.label,
                savedHref = e.savedHref,
                fragment = e.fragment,
                mhtResourceId = e.mhtResourceId,
                synthetic = e.synthetic
            )
        }
        val firstSavedEntryId = entryRows.firstOrNull { it.mhtResourceId != null }?.id

        savedTabDao.insertTab(
            SavedTabEntity(
                id = groupTabId,
                vaultId = vaultId,
                title = title,
                url = indexUrl,
                createdAt = now,
                lastAccessed = now,
                fileType = "group",
                fileName = null,
                resourceId = null,
                groupActiveEntryId = firstSavedEntryId,
                groupFolderName = groupFolderName
            )
        )
        pageGroupEntryDao.insertAll(entryRows)
        val anchorRows = entryRows.zip(entries).flatMap { (row, src) ->
            src.anchors.mapIndexed { idx, a ->
                PageGroupEntryAnchorEntity(
                    id = UUID.randomUUID().toString(),
                    pageGroupEntryId = row.id,
                    position = idx,
                    label = a.label,
                    scrollPosition = a.scrollPosition
                )
            }
        }
        if (anchorRows.isNotEmpty()) pageGroupEntryAnchorDao.insertAll(anchorRows)
        val anchorsByEntryId = anchorRows.groupBy { it.pageGroupEntryId }
        writeManifestForGroup(
            groupTabId = groupTabId,
            title = title,
            indexUrl = indexUrl,
            indexPageTitle = indexPageTitle,
            createdAt = now.time,
            lastAccessed = now.time,
            activeEntryId = firstSavedEntryId,
            entries = entryRows.zip(entries) { row, src ->
                ManifestSeed(row, src.pageFile, src.textFile, anchorsByEntryId[row.id].orEmpty())
            }
        )
        groupTabId
    }

    /** Renames the group's title and rewrites the manifest. */
    suspend fun renameGroup(groupTabId: String, newTitle: String) = withContext(Dispatchers.IO) {
        val parent = savedTabDao.getTabById(groupTabId) ?: return@withContext
        savedTabDao.updateTab(parent.copy(title = newTitle))
        rewriteManifestFromRoom(groupTabId)
    }

    /** Sets the user's resume position. */
    suspend fun updateActiveEntry(groupTabId: String, entryId: String?) = withContext(Dispatchers.IO) {
        savedTabDao.updateGroupActiveEntry(groupTabId, entryId)
        rewriteManifestFromRoom(groupTabId)
    }

    /** Persists scroll restore state for one entry. */
    suspend fun updateEntryScroll(
        groupTabId: String,
        entryId: String,
        anchor: String?,
        occurrenceIndex: Int,
        ratio: Double
    ) = withContext(Dispatchers.IO) {
        pageGroupEntryDao.updateScroll(entryId, anchor, occurrenceIndex, ratio)
        rewriteManifestFromRoom(groupTabId)
    }

    /**
     * Save one scroll marker inside a page-group. [groupTabId] is the parent
     * saved_tabs.id (used as the `saved_tab_id` FK so the marker cascades on
     * group delete). [pageGroupEntryId] records which entry was active so the
     * Markers tab can navigate the user there before scrolling.
     */
    suspend fun addMarker(
        groupTabId: String,
        pageGroupEntryId: String,
        label: String,
        scrollPosition: Int,
        occurrenceIndex: Int
    ): Result<ScrollMarker> = withContext(Dispatchers.IO) {
        runCatching {
            // Sequential UI use only: not transactional; concurrent addMarker calls
            // on the same group can produce duplicate markerOrder values.
            val nextOrder = (scrollMarkerDao.getMaxOrderForTab(groupTabId) ?: -1) + 1
            val entity = ScrollMarkerEntity(
                id = UUID.randomUUID().toString(),
                savedTabId = groupTabId,
                scrollPosition = scrollPosition,
                markerOrder = nextOrder,
                createdAt = System.currentTimeMillis(),
                label = label,
                occurrenceIndex = occurrenceIndex,
                pageGroupEntryId = pageGroupEntryId
            )
            scrollMarkerDao.insert(entity)
            ScrollMarker(
                id = entity.id,
                label = entity.label,
                scrollY = entity.scrollPosition,
                order = entity.markerOrder,
                occurrenceIndex = entity.occurrenceIndex
            )
        }
    }

    suspend fun deleteMarker(markerId: String) = withContext(Dispatchers.IO) {
        scrollMarkerDao.deleteById(markerId)
    }

    /**
     * Bulk-delete every scroll marker that belongs to a page-group. Group
     * markers share `saved_tab_id = groupTabId` (set by [addMarker]) so the
     * existing `deleteForTab` query already does the right thing — no new
     * DAO method needed.
     */
    suspend fun deleteAllMarkersForGroup(groupTabId: String) = withContext(Dispatchers.IO) {
        scrollMarkerDao.deleteForTab(groupTabId)
    }

    fun observeGroupMarkers(groupTabId: String): kotlinx.coroutines.flow.Flow<List<ScrollMarkerEntity>> =
        scrollMarkerDao.getForTab(groupTabId)

    /**
     * Removes one entry. If the entry was the last reference to its
     * `mht_resource_id`, also deletes the `resource_files` row, the on-disk
     * MHT, and the cached find-sidecar `.txt` for that resource.
     */
    suspend fun removeEntry(groupTabId: String, entryId: String) = withContext(Dispatchers.IO) {
        val row = pageGroupEntryDao.getById(entryId) ?: return@withContext
        pageGroupEntryDao.deleteById(entryId)
        scrollMarkerDao.deleteForGroupEntry(entryId)
        row.mhtResourceId?.let { resourceId ->
            if (pageGroupEntryDao.countReferences(groupTabId, resourceId) == 0) {
                val resource = resourceFileDao.getById(resourceId)
                if (resource != null) {
                    resourceFileDao.delete(resource)
                    val folder = groupsFolderResolver.resolve(groupTabId)
                    File(folder, resource.filename).delete()
                }
                sidecarCache?.deleteResource(groupTabId, resourceId)
            }
        }
        // Re-dense positions so the ToC reads 0..N-1.
        // Use targeted UPDATE instead of insertAll(REPLACE) to avoid triggering
        // FK CASCADE on the page_group_entry_anchors child table.
        val remaining = pageGroupEntryDao.getForGroup(groupTabId).sortedBy { it.position }
        remaining.forEachIndexed { i, e ->
            if (e.position != i) pageGroupEntryDao.updatePosition(e.id, i)
        }
        // Clear dangling groupActiveEntryId if the removed entry was the active one.
        val parent = savedTabDao.getTabById(groupTabId)
        if (parent?.groupActiveEntryId == entryId) {
            val fallback = remaining.firstOrNull { it.mhtResourceId != null }?.id
            savedTabDao.updateGroupActiveEntry(groupTabId, fallback)
        }
        rewriteManifestFromRoom(groupTabId)
    }

    /** Deletes the group, every entry, every group_page resource, the on-disk folder, and the sidecar cache. */
    suspend fun deleteGroup(groupTabId: String) = withContext(Dispatchers.IO) {
        val entries = pageGroupEntryDao.getForGroup(groupTabId)
        val resourceIds = entries.mapNotNull { it.mhtResourceId }.toSet().toList()
        // On-disk delete FIRST — both the resolver's path derivation and the
        // SAF traversal need the saved_tabs row's group_folder_name + vaultId,
        // and those are gone after the transaction. If the transaction then
        // fails (rare), the integrity checker reconciles the orphan row on
        // next launch — worse to leave files behind than rows.
        val folder = groupsFolderResolver.resolve(groupTabId)
        groupsFolderResolver.deleteOnDisk(groupTabId)
        folder.deleteRecursively()
        sidecarCache?.clear(groupTabId)
        database.withTransaction {
            savedTabDao.deleteTabById(groupTabId)   // FK cascades page_group_entries
            if (resourceIds.isNotEmpty()) resourceFileDao.deleteByIds(resourceIds)
        }
    }

    private data class ManifestSeed(
        val row: PageGroupEntryEntity,
        val pageFile: String?,
        val textFile: String?,
        val anchors: List<PageGroupEntryAnchorEntity> = emptyList()
    )

    private suspend fun writeManifestForGroup(
        groupTabId: String,
        title: String,
        indexUrl: String,
        indexPageTitle: String,
        createdAt: Long,
        lastAccessed: Long,
        activeEntryId: String?,
        entries: List<ManifestSeed>
    ) {
        val manifest = PageGroupManifest(
            groupTabId = groupTabId,
            title = title,
            indexUrl = indexUrl,
            indexPageTitle = indexPageTitle,
            activeEntryId = activeEntryId,
            createdAt = createdAt,
            lastAccessed = lastAccessed,
            entries = entries.map { seed ->
                PageGroupManifest.Entry(
                    id = seed.row.id, position = seed.row.position, label = seed.row.label,
                    savedHref = seed.row.savedHref, fragment = seed.row.fragment,
                    pageFile = seed.pageFile, textFile = seed.textFile,
                    scrollAnchor = seed.row.scrollAnchor,
                    scrollOccurrenceIndex = seed.row.scrollOccurrenceIndex,
                    scrollRatio = seed.row.scrollRatio,
                    synthetic = seed.row.synthetic,
                    anchors = seed.anchors.map { a ->
                        PageGroupManifest.Anchor(
                            position = a.position,
                            label = a.label,
                            scrollPosition = a.scrollPosition
                        )
                    }
                )
            }
        )
        groupsFolderResolver.writeManifest(groupTabId, PageGroupManifest.toJson(manifest))
    }

    /**
     * Rebuilds manifest.json from current Room state. Used after
     * mutations that don't already know the pageFile/textFile names
     * (e.g. scroll update, rename) — looks them up via resource_files.
     */
    private suspend fun rewriteManifestFromRoom(groupTabId: String) {
        val parent = savedTabDao.getTabById(groupTabId) ?: return
        val rows = pageGroupEntryDao.getForGroup(groupTabId)
        val seeds = rows.map { row ->
            val resource = row.mhtResourceId?.let { resourceFileDao.getById(it) }
            val pageFile = resource?.filename
            val textFile = pageFile?.let { it.substringBeforeLast('.', it) + ".txt" }
            val anchors = pageGroupEntryAnchorDao.getForEntry(row.id)
            ManifestSeed(row, pageFile, textFile, anchors)
        }
        writeManifestForGroup(
            groupTabId = groupTabId,
            title = parent.title,
            indexUrl = parent.url,
            indexPageTitle = parent.title,
            createdAt = parent.createdAt.time,
            lastAccessed = parent.lastAccessed.time,
            activeEntryId = parent.groupActiveEntryId,
            entries = seeds
        )
    }
}
