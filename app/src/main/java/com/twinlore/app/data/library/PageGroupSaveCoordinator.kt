package com.twinlore.app.data.library

import com.twinlore.app.data.PageGroupSidecarCache
import com.twinlore.app.ui.screens.browser.BulkSaveState
import com.twinlore.app.ui.screens.browser.ChapterResult
import com.twinlore.app.ui.screens.browser.Outcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import java.io.File
import java.util.UUID

/**
 * Builds one page-group from one ToC. Owns an offscreen WebView (via the
 * caller-supplied driverFactory) so the user's foreground tab is not
 * polluted. State is exposed via [state]; the UI binds to it.
 *
 * Failure model:
 *  - Per-URL load/archive failure → that save target gets `resourceId = null`
 *    in the prepared entry list; every ToC entry pointing at it inherits null.
 *  - Cancellation, storage failure, or vault deletion mid-run → full rollback:
 *    no createGroup, every written temp/staged file deleted.
 */
class PageGroupSaveCoordinator(
    private val save: GroupSaveSink,
    private val mainDispatcher: CoroutineDispatcher,
    private val politenessDelayMs: () -> Long = { (600L..1200L).random() },
    private val loadTimeoutMs: Long = 10_000L,
    private val cacheDir: File = File(System.getProperty("java.io.tmpdir"), "page-group-save"),
    /**
     * Optional sidecar-cache hook so the coordinator can persist the per-page
     * find-sidecar `.txt` as soon as the MHT is staged. Production wires this
     * to [PageGroupSidecarCache]; tests pass null to skip cache writes.
     */
    private val sidecarCache: PageGroupSidecarCache? = null
) {
    private val _state = MutableStateFlow<BulkSaveState>(BulkSaveState.Idle)
    val state: StateFlow<BulkSaveState> = _state

    private var job: Job? = null
    private var driver: PageGroupSaveDriver? = null
    private val stagedFiles = mutableListOf<File>()

    /**
     * GroupTabId from the most recent successful run. The UI reads this after
     * Done to navigate to the newly-created page-group. @Volatile so a UI
     * thread read after Done sees the latest write from the coroutine.
     */
    @Volatile
    var lastGroupTabId: String? = null
        private set

    fun start(
        vaultId: String,
        indexUrl: String,
        indexPageTitle: String,
        toc: List<TocEntry>,
        driverFactory: () -> PageGroupSaveDriver
    ) {
        if (job?.isActive == true) return
        cacheDir.mkdirs()
        lastGroupTabId = null
        val scope = CoroutineScope(SupervisorJob() + mainDispatcher)
        job = scope.launch {
            val groupTabId = UUID.randomUUID().toString()
            try {
                runQueue(vaultId, indexUrl, indexPageTitle, toc, groupTabId, driverFactory)
                lastGroupTabId = groupTabId
            } catch (ce: CancellationException) {
                _state.value = BulkSaveState.Cancelled(savedSoFar = 0)
                // Rollback the on-disk group folder + orphan resource_files
                // rows under NonCancellable. The catch body already runs in
                // a cancelled coroutine, and both rollbackGroupArtifacts and
                // sidecarCache.clear are suspend funs whose `withContext`
                // blocks would otherwise re-throw CancellationException
                // before doing any work — leaving the partial save on disk.
                // NonCancellable suppresses that re-throw for the duration
                // of the cleanup. The outer `throw ce` below still surfaces
                // the cancellation to the caller.
                withContext(NonCancellable) {
                    save.rollbackGroupArtifacts(groupTabId)
                    // Any sidecar .txt files we wrote during the cancelled
                    // run are orphans now (no group row will reference them)
                    // — drop the whole per-group cache dir.
                    sidecarCache?.clear(groupTabId)
                }
                throw ce
            } finally {
                driver?.destroy(); driver = null
                stagedFiles.forEach { runCatching { it.delete() } }
                stagedFiles.clear()
            }
        }
    }

    fun cancel() { job?.cancel() }

    private suspend fun runQueue(
        vaultId: String,
        indexUrl: String,
        indexPageTitle: String,
        toc: List<TocEntry>,
        groupTabId: String,
        driverFactory: () -> PageGroupSaveDriver
    ) {
        val saveTargets = toc.distinctBy { it.savedHref }.map { it.savedHref }
        _state.value = BulkSaveState.Preparing(total = saveTargets.size)
        yield()
        driver = withContext(mainDispatcher) { driverFactory() }
        val d = driver!!

        val completed = mutableListOf<ChapterResult>()
        // savedHref → resourceId (null if save failed for this target)
        val resourceIdByUrl = mutableMapOf<String, String?>()
        val anchorsByUrl = mutableMapOf<String, List<RawAnchor>>()
        val prepared = mutableListOf<PreparedEntry>()

        for ((i, url) in saveTargets.withIndex()) {
            val tocLabel = toc.first { it.savedHref == url }.label
            _state.value = BulkSaveState.Saving(i, saveTargets.size, currentTitle = tocLabel, completed = completed.toList())

            val loadOk = withTimeoutOrNull(loadTimeoutMs) { d.loadAndAwait(url) } ?: false
            if (!loadOk) {
                completed += ChapterResult(url, tocLabel, Outcome.FAILED_LOAD_TIMEOUT, failureReason = "load failed or timed out")
                resourceIdByUrl[url] = null
                continue
            }
            val tmp = File(cacheDir, "pg_${groupTabId}_${i}.mht").also { stagedFiles += it }
            val archived = withTimeoutOrNull(30_000L) { d.saveWebArchive(tmp.absolutePath) } ?: false
            if (!archived) {
                completed += ChapterResult(url, tocLabel, Outcome.FAILED_HTTP, failureReason = "saveWebArchive returned null or timed out")
                resourceIdByUrl[url] = null
                continue
            }

            // Capture byte count before stageSavedPage moves/renames the temp file.
            val byteCount = tmp.length()
            val resourceId = save.stageSavedPage(
                groupTabId = groupTabId,
                vaultId = vaultId,
                groupTitle = indexPageTitle,
                savedHref = url,
                tempMht = tmp,
                pageIndex = i
            )
            if (resourceId == null) {
                completed += ChapterResult(url, tocLabel, Outcome.FAILED_HTTP, failureReason = "storage write failed")
                resourceIdByUrl[url] = null
                if (i < saveTargets.lastIndex) delay(politenessDelayMs())
                continue
            }
            resourceIdByUrl[url] = resourceId
            completed += ChapterResult(url, tocLabel, Outcome.SAVED, bytes = byteCount)
            // MHT is now owned by the repository; drop it from the cleanup list.
            stagedFiles.remove(tmp)

            // Persist the find-sidecar `.txt` to the cache (not the library
            // folder — it's derived data we can rebuild from the MHT on demand).
            // Keyed by the just-assigned resourceId so the find resolver can
            // look it up without knowing the on-disk save folder name.
            if (sidecarCache != null) {
                val referencedIds = toc
                    .filter { it.savedHref == url && it.fragment != null }
                    .map { it.fragment!!.removePrefix("#") }
                    .toSet()
                val html = d.mainHtml().orEmpty()
                val txtBody = PageGroupTextIndex.buildSidecar(html, referencedIds)
                sidecarCache.writeSidecar(groupTabId, resourceId, txtBody)
            }

            // Capture the live-DOM anchors right after the archive lands; the
            // foreground WebView still shows the page we just saved. Silent on
            // success; log warn on failure so "empty anchors" reports have a
            // diagnosable signal (empty-list-on-failure is the intended behaviour).
            val anchorsResult = runCatching { d.extractAnchors() }
            anchorsResult.exceptionOrNull()?.let {
                android.util.Log.w("PageGroupSaveCoordinator", "extractAnchors failed for $url", it)
            }
            anchorsByUrl[url] = anchorsResult.getOrDefault(emptyList())

            if (i < saveTargets.lastIndex) delay(politenessDelayMs())
        }

        toc.forEach { e ->
            prepared += PreparedEntry(
                label = e.label,
                savedHref = e.savedHref,
                fragment = e.fragment,
                mhtResourceId = resourceIdByUrl[e.savedHref],
                anchors = anchorsByUrl[e.savedHref].orEmpty(),
                synthetic = e.synthetic
            )
        }

        try {
            save.commitGroup(
                groupTabId = groupTabId,
                vaultId = vaultId,
                title = indexPageTitle,
                indexUrl = indexUrl,
                indexPageTitle = indexPageTitle,
                entries = prepared
            )
        } catch (e: Exception) {
            // Commit failure: roll back any staged on-disk artifacts; surface as
            // Cancelled so the UI exits the Saving state.
            save.rollbackGroupArtifacts(groupTabId)
            sidecarCache?.clear(groupTabId)
            _state.value = BulkSaveState.Cancelled(
                savedSoFar = completed.count { it.outcome == Outcome.SAVED }
            )
            return
        }

        _state.value = BulkSaveState.Done(
            saved = completed.count { it.outcome == Outcome.SAVED },
            skipped = 0,
            failed = completed.filter { it.outcome.isFailure() }
        )
    }

    /** Stable handoff shape; consumed by the prod sink to build NewEntry list. */
    data class PreparedEntry(
        val label: String,
        val savedHref: String,
        val fragment: String?,
        val mhtResourceId: String?,
        val anchors: List<RawAnchor> = emptyList(),
        val synthetic: Boolean = false
    )

    interface GroupSaveSink {
        /**
         * Move [tempMht] into the group folder, insert a `resource_files`
         * row of `kind="group_page"`, return its id. Returns null on storage
         * failure → coordinator treats as a per-URL failure.
         *
         * Find-sidecar `.txt` files are NOT staged through the sink; the
         * coordinator writes them straight to [PageGroupSidecarCache] keyed
         * by the returned resourceId, so they stay out of the library folder.
         *
         * [groupTitle] is the index-page title from `start(...)`; the sink
         * sanitizes + collision-resolves it on the first call to derive the
         * `<vault>/<group title>/` on-disk folder, stashes the result, and
         * uses it for every subsequent stage call in the same run. It also
         * hands the resolved name to [commitGroup] so the saved_tabs row's
         * `group_folder_name` matches what's on disk.
         */
        suspend fun stageSavedPage(
            groupTabId: String,
            vaultId: String,
            groupTitle: String,
            savedHref: String,
            tempMht: File,
            pageIndex: Int
        ): String? = null

        /**
         * Commit: create saved_tabs row + page_group_entries + manifest.
         *
         * [groupTabId] is the id the coordinator used during staging (and
         * exposes as [lastGroupTabId] after the run). The sink MUST use it
         * as the new saved_tabs row's id so callers can route the post-save
         * "Open" banner directly to the row — and so per-group artifacts
         * keyed on this id (sidecar cache, staged folder) point at it.
         */
        suspend fun commitGroup(
            groupTabId: String,
            vaultId: String,
            title: String,
            indexUrl: String,
            indexPageTitle: String,
            entries: List<PreparedEntry>
        )

        /** Cancel / fatal: delete every staged file and any in-flight folder. */
        suspend fun rollbackGroupArtifacts(groupTabId: String) {}
    }

    companion object {
        const val BROWSER_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    }
}

/** One header captured at save time by [PageGroupSaveDriver.extractAnchors]. */
data class RawAnchor(val label: String, val scrollY: Int)

/** Per-invocation WebView wrapper. Production: AndroidPageGroupSaveDriver. */
interface PageGroupSaveDriver {
    suspend fun loadAndAwait(url: String): Boolean
    suspend fun saveWebArchive(absolutePath: String): Boolean
    /** Returns the parsed main HTML for sidecar generation, or null if unavailable. */
    suspend fun mainHtml(): String?
    /**
     * Walks the live DOM via the existing TOC_EXTRACTION_JS and returns one
     * [RawAnchor] per header element in DOM order. Called once per save target
     * right after the MHT is archived, while the WebView still shows the page.
     */
    suspend fun extractAnchors(): List<RawAnchor>
    fun destroy()
}
