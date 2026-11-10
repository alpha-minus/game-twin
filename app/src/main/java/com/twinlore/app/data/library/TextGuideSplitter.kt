package com.twinlore.app.data.library

import android.content.Context
import com.twinlore.app.data.PageGroupRepository
import com.twinlore.app.data.local.ResourceFileDao
import com.twinlore.app.data.local.ResourceFileEntity
import com.twinlore.app.data.local.SavedTabDao
import com.twinlore.app.data.local.VaultDao
import com.twinlore.app.ui.screens.TextGuideChunker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns a very large single text guide into a page-group so the WebView only
 * ever renders one chunk's DOM at a time (a multi-MB single document OOM-kills
 * the app — see [TextGuideChunker]).
 *
 * Reuses the existing page-group machinery end to end: each chunk's HTML is
 * wrapped as an MHT, staged into the vault group folder exactly like a bulk
 * save ([PageGroupSaveSink]), and registered via [PageGroupRepository.createGroup].
 * Each detected section becomes a group entry, so the table of contents and the
 * per-page scroll markers / resume all work unchanged.
 *
 * v1 is non-destructive: it creates a NEW group tab and leaves the original
 * guide in place. The caller decides whether to open the group and (later)
 * retire the original.
 */
@Singleton
class TextGuideSplitter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val folder: LibraryFolderManager,
    private val vaultDao: VaultDao,
    private val resourceDao: ResourceFileDao,
    private val pageGroupRepository: PageGroupRepository,
    private val savedTabDao: SavedTabDao,
) {
    /** True if [text] is big enough to be worth splitting into a group. */
    fun shouldSplit(text: String): Boolean = TextGuideChunker.shouldSplit(text)

    /**
     * Build a page-group from [text]. Returns the new group's tab id, or null
     * if the text isn't large enough / staging failed (caller falls back to the
     * normal single-guide render).
     */
    suspend fun createGroupFromText(
        vaultId: String,
        title: String,
        indexUrl: String,
        text: String,
        /** When set, the original single-guide tab is deleted after the group is
         *  created (in-place replacement) so re-opening doesn't re-split. */
        replaceTabId: String? = null,
    ): String? = withContext(Dispatchers.IO) {
        try {
        if (!shouldSplit(text)) return@withContext null
        val vault = vaultDao.getVaultById(vaultId)
            ?: run { android.util.Log.w(TAG, "abort: vault null for $vaultId"); return@withContext null }
        val vaultName = LibraryFolderManager.resolveFolderName(vault)

        val lines = text.split("\n")
        val plans = TextGuideChunker.planChunks(lines)
        if (plans.size < 2) return@withContext null

        val groupFolderName = PageGroupSaveTargets.resolveUniqueGroupFolderName(
            context = context, folder = folder, vaultName = vaultName, groupTitle = title
        ) ?: run { android.util.Log.w(TAG, "abort: groupFolderName null"); return@withContext null }

        val tmpDir = File(context.cacheDir, "text_split_tmp/${UUID.randomUUID()}").apply { mkdirs() }
        // chunk index -> (resourceId, pageFile) for the staged MHT
        val staged = mutableMapOf<Int, Pair<String, String>>()
        try {
            for (plan in plans) {
                // Render + wrap one chunk at a time so the whole document's HTML
                // is never held in memory at once (that OOM-kills on a maxed device).
                val mht = TextGuideChunker.wrapMht(
                    TextGuideChunker.renderChunkHtml(lines, plan), plan.index
                )
                val tmpMht = File(tmpDir, "page-${plan.index + 1}.mht")
                tmpMht.writeText(mht, Charsets.UTF_8)
                val outcome = PageGroupSaveTargets.moveIntoGroupFolder(
                    context = context, folder = folder, vaultName = vaultName,
                    groupFolderName = groupFolderName, pageIndex = plan.index, tempMht = tmpMht
                ) ?: run { android.util.Log.w(TAG, "abort: moveIntoGroupFolder null at chunk ${plan.index}"); return@withContext null }
                tmpMht.delete()
                val rid = UUID.randomUUID().toString()
                val now = System.currentTimeMillis()
                resourceDao.insert(
                    ResourceFileEntity(
                        id = rid, vaultId = vaultId, kind = "group_page",
                        filename = outcome.pageFile, sizeBytes = outcome.sizeBytes,
                        homeRelativePath = outcome.homeRelativePath, absolutePath = null,
                        mimeType = "multipart/related", source = "text_split",
                        lastSeenAt = now, createdAt = now
                    )
                )
                staged[plan.index] = rid to outcome.pageFile
            }
        } finally {
            tmpDir.deleteRecursively()
        }

        // One entry per detected section; sections in the same chunk share the
        // chunk's MHT and differ only by in-page fragment (anchor siblings).
        val entries = plans.flatMap { plan ->
            val (rid, pageFile) = staged[plan.index] ?: return@flatMap emptyList()
            val chunkUrl = "https://localhost.invalid/gtchunk-${plan.index}.html"
            plan.sections.map { sec ->
                PageGroupRepository.NewEntry(
                    label = sec.title,
                    savedHref = chunkUrl,
                    fragment = sec.fragment,
                    mhtResourceId = rid,
                    pageFile = pageFile,
                    textFile = null,
                )
            }
        }
        if (entries.isEmpty()) { android.util.Log.w(TAG, "abort: entries empty"); return@withContext null }

        val groupId = pageGroupRepository.createGroup(
            vaultId = vaultId,
            title = title,
            indexUrl = indexUrl,
            indexPageTitle = title,
            groupFolderName = groupFolderName,
            entries = entries,
        )
        android.util.Log.i(TAG, "split '$title' into ${plans.size} pages, ${entries.size} entries")
        // In-place replacement: drop the original single-guide tab so the library
        // shows one entry and re-opening can't re-split. (Markers on the original
        // cascade-delete — migrating them onto the chunk entries is a follow-up.)
        if (replaceTabId != null) {
            savedTabDao.getTabById(replaceTabId)?.let { savedTabDao.deleteTab(it) }
        }
        groupId
        } catch (e: Throwable) {
            android.util.Log.e(TAG, "split failed with exception", e)
            null
        }
    }

    private companion object { const val TAG = "TextGuideSplitter" }
}
