package com.twinlore.app.data.library

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.documentfile.provider.DocumentFile
import com.twinlore.app.data.local.MediaSearchDirDao
import com.twinlore.app.data.local.MediaSearchDirEntity
import com.twinlore.app.data.local.MediaSearchIndexDao
import com.twinlore.app.data.local.MediaSearchIndexEntity
import com.twinlore.app.data.local.MediaSearchPathDao
import com.twinlore.app.data.scraping.GameNameSanitizer
import com.twinlore.app.domain.MediaSearchPath
import com.twinlore.app.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

private fun GameNameSanitizer.normalizeLastSegment(relDir: String): String =
    if (relDir.isEmpty()) "" else this.normalize(relDir.substringAfterLast('/'))

/**
 * Walks each search-path root and populates media_search_index and
 * media_search_dirs. Disabled paths are skipped at every entry point.
 *
 * Suspend entry points (callers control their own scope):
 *  - rebuildAll() runs full rebuild for every (non-disabled) search path.
 *  - rebuildOne(spId) runs full rebuild for one search path.
 *  - scanDelta(spId) does an mtime-gated incremental walk (refresh, app start).
 *
 * Lifecycle entry points (launch in appScope so the work survives Settings teardown):
 *  - startScanAll() emits Finished.Global on done.
 *  - startScanForPath(spId) emits Started + Finished.PerPath; silent for disabled paths.
 *  - cancel() interrupts the current job.
 *
 * Observable state:
 *  - state: StateFlow<ScanState> drives Settings inline progress.
 *  - events: SharedFlow<ScanEvent> drives MainActivity toasts.
 */
@Singleton
class MediaIndexer(
    private val context: Context,
    private val pathDao: MediaSearchPathDao,
    private val indexDao: MediaSearchIndexDao,
    private val dirDao: MediaSearchDirDao,
    private val sanitizer: GameNameSanitizer,
    private val appScope: CoroutineScope,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        pathDao: MediaSearchPathDao,
        indexDao: MediaSearchIndexDao,
        dirDao: MediaSearchDirDao,
        sanitizer: GameNameSanitizer,
        @ApplicationScope appScope: CoroutineScope,
    ) : this(context, pathDao, indexDao, dirDao, sanitizer, appScope, clock = { System.currentTimeMillis() })

    companion object {
        val SUPPORTED_EXTS: Set<String> = setOf("png", "jpg", "jpeg", "webp", "pdf", "txt", "mht", "mhtml", "html")

        /**
         * True for names the walkers must never enter (directories) or index
         * (files): any dot-prefixed name. Covers Syncthing's `.stversions`
         * version backups and `.stfolder` marker, `.sync`, `.thumbnails`, and
         * Android's `.trashed-*` files. Without this, Syncthing backup copies
         * of vault files were indexed and then registered as duplicate
         * local_link resources + saved tabs by LocalMediaBinder.
         */
        fun isExcludedDirName(name: String): Boolean = name.startsWith(".")

        private const val TAG = "MediaIndexer"
    }

    sealed class ScanState {
        object Idle : ScanState()
        data class Running(val scanned: Int, val label: String) : ScanState()
        data class Done(val total: Int, val timestamp: Long) : ScanState()
    }

    sealed class ScanEvent {
        data class Started(val label: String) : ScanEvent()
        sealed class Finished : ScanEvent() {
            object Global : Finished()
            data class PerPath(val label: String, val fileCount: Int) : Finished()
        }
    }

    private val _state = MutableStateFlow<ScanState>(ScanState.Idle)
    val state: StateFlow<ScanState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<ScanEvent>(replay = 0, extraBufferCapacity = 8)
    val events: SharedFlow<ScanEvent> = _events.asSharedFlow()

    private var currentJob: Job? = null

    /** Launches a global scan in appScope. Emits Finished.Global on done. No Started event. */
    fun startScanAll() {
        currentJob?.cancel()
        currentJob = appScope.launch {
            _state.value = ScanState.Running(scanned = 0, label = "")
            rebuildAll { scanned, label ->
                _state.value = ScanState.Running(scanned, label)
            }
            val total = indexDao.count()
            _state.value = ScanState.Done(total, clock())
            _events.emit(ScanEvent.Finished.Global)
            delay(5_000)
            if (_state.value is ScanState.Done) _state.value = ScanState.Idle
        }
    }

    /** Launches a per-path scan in appScope. Emits Started + Finished.PerPath. */
    fun startScanForPath(spId: String) {
        currentJob?.cancel()
        currentJob = appScope.launch {
            val sp = pathDao.getById(spId) ?: return@launch
            if (sp.disabled) return@launch
            _events.emit(ScanEvent.Started(sp.label))
            _state.value = ScanState.Running(scanned = 0, label = sp.label)
            rebuildOne(spId) { scanned, label ->
                _state.value = ScanState.Running(scanned, label)
            }
            val after = indexDao.getForPath(spId).size
            val total = indexDao.count()
            _state.value = ScanState.Done(total, clock())
            _events.emit(ScanEvent.Finished.PerPath(sp.label, fileCount = after))
            delay(5_000)
            if (_state.value is ScanState.Done) _state.value = ScanState.Idle
        }
    }

    fun cancel() {
        currentJob?.cancel()
        currentJob = null
        _state.value = ScanState.Idle
    }

    suspend fun rebuildAll(progress: (scanned: Int, label: String) -> Unit = { _, _ -> }) =
        withContext(Dispatchers.IO) {
            for (sp in pathDao.getAll()) {
                if (sp.disabled) continue
                rebuildOne(sp.id, progress)
            }
        }

    suspend fun rebuildOne(searchPathId: String, progress: (Int, String) -> Unit = { _, _ -> }) =
        withContext(Dispatchers.IO) {
            val sp = pathDao.getById(searchPathId) ?: return@withContext
            val startedAt = SystemClock.elapsedRealtime()
            indexDao.deleteForPath(sp.id)
            dirDao.deleteForPath(sp.id)
            val tree = MediaTree.forUri(context.contentResolver, Uri.parse(sp.uri)) ?: return@withContext

            val entries = mutableListOf<MediaSearchIndexEntity>()
            val dirs = mutableListOf<MediaSearchDirEntity>()
            var scanned = 0

            // Capture the calling coroutine's context so the (non-suspend)
            // walk helpers can ensure cancellation propagates: a user
            // navigating away from Settings or starting a fresh scan won't
            // leave the previous walk ticking in the background.
            val cancelCtx = currentCoroutineContext()
            try {
                walk(tree, tree.root, "", sp.id, entries, dirs, cancelCtx) {
                    scanned++; if (scanned % 100 == 0) progress(scanned, sp.label)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Swallow — caller cancelled. Don't persist a half-walk.
                throw e
            }

            indexDao.insertAll(entries)
            dirDao.upsertAll(dirs)
            pathDao.touchScan(sp.id, clock())
            progress(scanned, sp.label)
            android.util.Log.i(
                TAG,
                "rebuildOne '${sp.label}' files=${entries.size} dirs=${dirs.size} " +
                    "listings=${tree.listings} in ${SystemClock.elapsedRealtime() - startedAt}ms",
            )
        }

    /**
     * Full walk below [current]. Every property comes off the listing entry, so the SAF
     * cost is one children query per directory, not several queries per file.
     */
    private fun walk(
        tree: MediaTree,
        current: MediaTreeEntry,
        relDir: String,
        searchPathId: String,
        out: MutableList<MediaSearchIndexEntity>,
        dirs: MutableList<MediaSearchDirEntity>,
        cancelCtx: kotlin.coroutines.CoroutineContext,
        onFile: () -> Unit,
    ) {
        cancelCtx.ensureActive()
        dirs += MediaSearchDirEntity(
            id = UUID.randomUUID().toString(),
            searchPathId = searchPathId,
            relativeDir = relDir,
            mtime = current.mtime,
            normalizedName = sanitizer.normalizeLastSegment(relDir),
        )
        for (child in tree.list(current)) {
            cancelCtx.ensureActive()
            val name = child.name
            if (isExcludedDirName(name)) continue
            if (child.isDirectory) {
                val childRel = if (relDir.isEmpty()) name else "$relDir/$name"
                walk(tree, child, childRel, searchPathId, out, dirs, cancelCtx, onFile)
            } else if (child.isFile) {
                val ext = name.substringAfterLast('.', "").lowercase()
                if (ext !in SUPPORTED_EXTS) continue
                out += indexEntity(child, relDir, searchPathId, id = UUID.randomUUID().toString(), ext = ext)
                onFile()
            }
        }
    }

    private fun indexEntity(
        child: MediaTreeEntry,
        relDir: String,
        searchPathId: String,
        id: String,
        ext: String,
    ): MediaSearchIndexEntity {
        val parents = if (relDir.isEmpty()) emptyList() else relDir.split('/')
        return MediaSearchIndexEntity(
            id = id,
            searchPathId = searchPathId,
            absolutePath = child.location,
            relativePath = (parents + child.name).joinToString("/"),
            filename = child.name,
            normalizedBasename = sanitizer.normalize(child.name.substringBeforeLast('.')),
            normalizedPathTokens = parents.joinToString(" ") { sanitizer.normalize(it) },
            extLower = ext,
            sizeBytes = child.size,
            mtime = child.mtime,
        )
    }

    @androidx.annotation.VisibleForTesting
    suspend fun rebuildOneFromDocumentFile(searchPathId: String, tree: DocumentFile) =
        withContext(Dispatchers.IO) {
            indexDao.deleteForPath(searchPathId)
            dirDao.deleteForPath(searchPathId)
            val entries = mutableListOf<MediaSearchIndexEntity>()
            val dirs = mutableListOf<MediaSearchDirEntity>()
            val docTree = DocumentFileMediaTree(tree)
            walk(docTree, docTree.root, "", searchPathId, entries, dirs, currentCoroutineContext()) {}
            indexDao.insertAll(entries)
            dirDao.upsertAll(dirs)
            pathDao.touchScan(searchPathId, clock())
        }

    /**
     * mtime-gated incremental walk. On a path that has never been scanned (a folder the
     * user just picked) no directory has a stored mtime, so this is a full walk; the
     * timing line it logs is how that first pass is measured on a device.
     */
    suspend fun scanDelta(searchPathId: String): Int = withContext(Dispatchers.IO) {
        val sp = pathDao.getById(searchPathId) ?: return@withContext 0
        if (sp.disabled) return@withContext 0
        val startedAt = SystemClock.elapsedRealtime()
        val tree = MediaTree.forUri(context.contentResolver, Uri.parse(sp.uri)) ?: return@withContext 0
        val firstPass = dirDao.getMtime(sp.id, "") == null
        val touched = AtomicInteger(0)
        deltaWalk(tree, tree.root, sp.id, "", touched)
        if (touched.get() > 0) pathDao.touchScan(sp.id, clock())
        android.util.Log.i(
            TAG,
            "scanDelta '${sp.label}' firstPass=$firstPass touched=${touched.get()} " +
                "listings=${tree.listings} in ${SystemClock.elapsedRealtime() - startedAt}ms",
        )
        touched.get()
    }

    private suspend fun deltaWalk(
        tree: MediaTree,
        current: MediaTreeEntry,
        searchPathId: String,
        relDir: String,
        touched: AtomicInteger,
    ) {
        val storedMtime = dirDao.getMtime(searchPathId, relDir)

        if (storedMtime != null && storedMtime == current.mtime) {
            // Listing unchanged here: recurse via cached subdir paths only. A leaf
            // directory (the big covers/, screenshots/ ones) is not listed at all.
            val cachedChildren = if (relDir.isEmpty()) {
                dirDao.getTopLevelChildren(searchPathId)
            } else {
                dirDao.getChildren(searchPathId, relDir)
            }
            val names = cachedChildren
                .map { it.relativeDir.substringAfterLast('/') }
                .filterNot { isExcludedDirName(it) }
                .toSet()
            if (names.isEmpty()) return
            for (sub in tree.subdirs(current, names)) {
                val childRel = if (relDir.isEmpty()) sub.name else "$relDir/${sub.name}"
                deltaWalk(tree, sub, searchPathId, childRel, touched)
            }
            return
        }

        // Listing changed: re-list and reconcile.
        val children = tree.list(current)
        val existing = if (relDir.isEmpty()) {
            indexDao.getFilesInRoot(searchPathId)
        } else {
            indexDao.getForDir(searchPathId, relDir)
        }.associateBy { it.relativePath }
        val seenRelPaths = mutableSetOf<String>()
        val changed = mutableListOf<MediaSearchIndexEntity>()

        for (child in children) {
            if (child.isDirectory) continue
            if (isExcludedDirName(child.name)) continue
            val ext = child.name.substringAfterLast('.', "").lowercase()
            if (ext !in SUPPORTED_EXTS) continue
            val parents = if (relDir.isEmpty()) emptyList() else relDir.split('/')
            val rel = (parents + child.name).joinToString("/")
            seenRelPaths += rel
            val prior = existing[rel]
            val updated = indexEntity(child, relDir, searchPathId, id = prior?.id ?: UUID.randomUUID().toString(), ext = ext)
            if (prior == null || prior != updated) changed += updated
        }
        // One write per directory: a first pass used to commit a Room transaction
        // per file, thousands of them for a downloaded_media tree.
        if (changed.isNotEmpty()) {
            indexDao.insertAll(changed)
            touched.addAndGet(changed.size)
        }
        // Delete entries that were under this dir before but aren't now.
        for ((rel, e) in existing) {
            if (rel !in seenRelPaths) {
                indexDao.deleteById(e.id)
                touched.incrementAndGet()
            }
        }
        dirDao.upsert(MediaSearchDirEntity(
            id = UUID.randomUUID().toString(),
            searchPathId = searchPathId,
            relativeDir = relDir,
            mtime = current.mtime,
            normalizedName = sanitizer.normalizeLastSegment(relDir),
        ))

        // Recurse into actual subdirs (could include brand-new ones not yet in dirDao).
        for (child in children) {
            if (child.isDirectory && !isExcludedDirName(child.name)) {
                val childRel = if (relDir.isEmpty()) child.name else "$relDir/${child.name}"
                deltaWalk(tree, child, searchPathId, childRel, touched)
            }
        }
    }
}
