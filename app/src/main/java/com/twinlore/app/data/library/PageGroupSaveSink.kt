package com.twinlore.app.data.library

import android.content.Context
import com.twinlore.app.data.PageGroupRepository
import com.twinlore.app.data.local.ResourceFileDao
import com.twinlore.app.data.local.ResourceFileEntity
import com.twinlore.app.data.local.VaultDao
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import java.io.File
import java.util.UUID

/**
 * Bridges [PageGroupSaveCoordinator]'s save callbacks to disk
 * (via [PageGroupSaveTargets]) and Room (via [PageGroupRepository]).
 *
 * Concurrency note: this sink is `@Singleton`. It is safe because the
 * coordinator only ever runs one save at a time — `start(...)` early-returns
 * while a previous job is active. The mutable state below is touched only
 * from inside one save's callbacks; commit/rollback both clear it so the
 * next run starts clean.
 */
@Singleton
class PageGroupSaveSink @Inject constructor(
    @ApplicationContext private val context: Context,
    private val folder: LibraryFolderManager,
    private val resourceDao: ResourceFileDao,
    private val pageGroupRepository: PageGroupRepository,
    private val vaultDao: VaultDao
) : PageGroupSaveCoordinator.GroupSaveSink {
    // resourceId → page-N.mht filename. commitGroup consumes this to record
    // the on-disk MHT name into the manifest. The matching .txt sidecar lives
    // in PageGroupSidecarCache keyed by resourceId, not next to the MHT.
    // Cleared on commit or rollback.
    private val pageFileByResourceId = mutableMapOf<String, String>()
    // Cached during the run so rollback + commit can locate the on-disk folder
    // and so commitGroup can persist the same name on saved_tabs.group_folder_name.
    @Volatile private var lastStagedVaultName: String? = null
    @Volatile private var lastStagedGroupFolderName: String? = null

    override suspend fun stageSavedPage(
        groupTabId: String, vaultId: String, groupTitle: String,
        savedHref: String, tempMht: File, pageIndex: Int
    ): String? {
        val vault = vaultDao.getVaultById(vaultId) ?: return null
        val vaultName = LibraryFolderManager.resolveFolderName(vault)
        // Resolve the on-disk folder name once per run, on the first stage call.
        // Collision is resolved against existing children of <home>/<vaultName>/.
        val groupFolderName = lastStagedGroupFolderName ?: run {
            val resolved = PageGroupSaveTargets.resolveUniqueGroupFolderName(
                context = context, folder = folder,
                vaultName = vaultName, groupTitle = groupTitle
            ) ?: return null
            lastStagedGroupFolderName = resolved
            resolved
        }
        lastStagedVaultName = vaultName
        val outcome = PageGroupSaveTargets.moveIntoGroupFolder(
            context = context, folder = folder, vaultName = vaultName,
            groupFolderName = groupFolderName, pageIndex = pageIndex,
            tempMht = tempMht
        ) ?: return null
        val rid = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        // Map inserted FIRST so the rollback path can clean up even if the DB
        // insert is interrupted mid-coroutine (cancel between map+DB would cause
        // deleteByIds to be called for a non-existent rid, which is a safe no-op).
        pageFileByResourceId[rid] = outcome.pageFile
        resourceDao.insert(
            ResourceFileEntity(
                id = rid, vaultId = vaultId, kind = "group_page",
                filename = outcome.pageFile, sizeBytes = outcome.sizeBytes,
                homeRelativePath = outcome.homeRelativePath, absolutePath = null,
                mimeType = "multipart/related", source = "browser_bulk",
                lastSeenAt = now, createdAt = now
            )
        )
        return rid
    }

    override suspend fun commitGroup(
        groupTabId: String,
        vaultId: String, title: String, indexUrl: String, indexPageTitle: String,
        entries: List<PageGroupSaveCoordinator.PreparedEntry>
    ) {
        val newEntries = entries.map { e ->
            val pageFile = e.mhtResourceId?.let(pageFileByResourceId::get)
            PageGroupRepository.NewEntry(
                label = e.label, savedHref = e.savedHref, fragment = e.fragment,
                mhtResourceId = e.mhtResourceId,
                pageFile = pageFile, textFile = null,  // sidecars live in cacheDir, not the library
                anchors = e.anchors.map { a ->
                    PageGroupRepository.NewAnchor(label = a.label, scrollPosition = a.scrollY)
                },
                synthetic = e.synthetic
            )
        }
        pageGroupRepository.createGroup(
            groupTabId = groupTabId,
            vaultId = vaultId, title = title,
            indexUrl = indexUrl, indexPageTitle = indexPageTitle,
            groupFolderName = lastStagedGroupFolderName,
            entries = newEntries
        )
        pageFileByResourceId.clear()
        lastStagedVaultName = null
        lastStagedGroupFolderName = null
    }

    override suspend fun rollbackGroupArtifacts(groupTabId: String) {
        // If no page was staged (lastStaged* both null), there's nothing on disk yet.
        val vaultName = lastStagedVaultName
        val folderName = lastStagedGroupFolderName
        runCatching {
            PageGroupSaveTargets.deleteGroupFolder(context, folder, vaultName, folderName)
        }
        // Also delete the resource_files rows we inserted before rollback
        // (these are orphans without page_group_entries pointing at them).
        if (pageFileByResourceId.isNotEmpty()) {
            resourceDao.deleteByIds(pageFileByResourceId.keys.toList())
            pageFileByResourceId.clear()
        }
        lastStagedVaultName = null
        lastStagedGroupFolderName = null
    }
}
