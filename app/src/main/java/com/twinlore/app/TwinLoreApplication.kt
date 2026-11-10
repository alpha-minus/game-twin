package com.twinlore.app

import android.app.Application
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.twinlore.app.data.EpubCacheManager
import com.twinlore.app.data.MhtCacheManager
import com.twinlore.app.data.TxtConversionCache
import com.twinlore.app.data.library.LibraryAutoSaveObserver
import com.twinlore.app.data.library.sync.LibrarySyncCoordinator
import com.twinlore.app.util.PerfLog
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltAndroidApp
class TwinLoreApplication : Application() {
    companion object {
        private const val TAG = "TwinLoreApp"
        private val IMAGE_EXTS = setOf("png", "jpg", "jpeg", "webp", "gif", "bmp")

        /**
         * Cold-start trim threshold for the Chromium WebView HTTP cache. The
         * Android WebView has no public API to bound this cache; it self-tunes.
         * Empirically it sits at ~5 MB under light browsing and can grow
         * indefinitely on heavy use. Set to 0 (matching
         * MAX_CACHED_ENTRIES = 0 in MhtCacheManager) so the
         * `WebView/Default/HTTP Cache/` directory is wiped on EVERY cold
         * start, not just when it crossed a 50 MB ceiling. Chromium recreates
         * the dir lazily on the next navigation, with no on-screen impact.
         * Bump back to a non-zero ceiling here if the always-wipe ever shows
         * up as a session-warmup cost worth dodging.
         */
        const val WEBVIEW_HTTP_CACHE_THRESHOLD_BYTES = 0L
    }

    @Inject
    lateinit var libraryAutoSaveObserver: LibraryAutoSaveObserver

    @Inject
    lateinit var librarySyncCoordinator: LibrarySyncCoordinator

    @Inject
    lateinit var mhtCacheManager: MhtCacheManager

    @Inject
    lateinit var epubCacheManager: EpubCacheManager

    @Inject
    lateinit var txtConversionCache: TxtConversionCache

    @Inject
    lateinit var pageGroupSidecarCache: com.twinlore.app.data.PageGroupSidecarCache

    private val cacheCleanupScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        PerfLog.mark("Application.onCreate begin")
        super.onCreate()

        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    librarySyncCoordinator.checkAsync()
                }
                override fun onPause(owner: LifecycleOwner) {
                    libraryAutoSaveObserver.saveAsync()
                }
            }
        )

        // Trim parsed-guide caches on cold start. Each manager keeps its own LRU
        // tail (MHT: 20, EPUB: 20, text-FAQ: 50); without this call the eviction
        // methods were dead code and cacheDir/guide_cache + guide_html_cache grew
        // monotonically. Fire-and-forget on IO so it never delays first frame.
        cacheCleanupScope.launch {
            // Drop into the OS "background" cgroup before doing anything. With
            // MAX_CACHED_ENTRIES = 0 + WEBVIEW_HTTP_CACHE_THRESHOLD_BYTES = 0,
            // a fresh launch can have hundreds of megabytes worth of leaves to
            // unlink — recursive deleteRecursively() across thousands of small
            // asset files is IO-bound but the kernel syscalls add up to real
            // CPU. THREAD_PRIORITY_BACKGROUND (nice +10) puts the kernel
            // scheduler firmly behind the main thread + first-frame work; if
            // the device is under load the cleanup gets a smaller slice and
            // the UI stays responsive.
            android.os.Process.setThreadPriority(
                android.os.Process.THREAD_PRIORITY_BACKGROUND
            )
            // Belt + braces: a short delay so the first activity + first frame
            // are unambiguously underway before any large delete starts. Even
            // with the background priority, contending for the same dispatcher
            // pool as Coil / Room / WebView init on cold start can spike
            // latency on slow storage.
            kotlinx.coroutines.delay(1500L)
            try {
                // EPUB bookmarks still write to cache/guide_cache/<id>/; MHT
                // bookmarks moved to cache/single_guide_render/<id>/ (single)
                // and cache/group_render/<groupTabId>/<resourceId>/ (page-group).
                // Each cache manager filters by its own marker file, so we hand
                // both the combined leaf-dir list — dirs without the right
                // marker are ignored.
                val candidateDirs = enumerateGuideCacheDirs(cacheDir)
                if (candidateDirs.isNotEmpty()) {
                    mhtCacheManager.evictOldEntries(candidateDirs)
                    epubCacheManager.evictOldEntries(candidateDirs)
                }
                txtConversionCache.evictOldEntries()
                // Page-group find-sidecar LRU; same shape as the others
                // (keeps the 20 most-recently-touched groups).
                pageGroupSidecarCache.evictOldEntries()

                // guide_html_stripped/ holds the script-stripped copy GuideScreen
                // writes for every downloaded .html guide it opens. It has no LRU
                // of its own (unlike its siblings above) because it doesn't need
                // one: the copy is rewritten from the source file on every open,
                // so nothing is lost by wiping the whole tree on cold start.
                // runCatching keeps a delete failure here (e.g. a file busy on a
                // weird filesystem) from aborting the rest of this cleanup pass.
                runCatching {
                    File(cacheDir, "guide_html_stripped").deleteRecursively()
                }.onFailure { Log.w(TAG, "guide_html_stripped wipe failed", it) }

                // One-time sweep of map-assign leftovers from older builds.
                // MapViewerScreenViewModel.assignMapFile used to write a temp PNG
                // named after the map title (e.g. "Zebes.png") at cacheDir/ and never
                // delete it on the success path. The success-path delete is fixed now,
                // but pre-fix devices can still have tens of MB of these stranded.
                cacheDir.listFiles { f ->
                    f.isFile && f.extension.lowercase() in IMAGE_EXTS
                }?.forEach { it.delete() }

                // Wipe the Chromium WebView HTTP cache on every cold start.
                // Threshold is 0 so this is unconditional — see
                // WEBVIEW_HTTP_CACHE_THRESHOLD_BYTES for the rationale. Repeat
                // requests inside the same session still hit the cache;
                // Chromium repopulates the on-disk store lazily on the next
                // navigation after restart.
                val freed = trimWebViewHttpCacheIfOverThreshold(cacheDir)
                if (freed > 0L) Log.d(TAG, "Wiped WebView HTTP cache: ${freed / 1024} KB")
            } catch (t: Throwable) {
                Log.w(TAG, "Cache cleanup failed", t)
            }
        }

        // (No PDFBox init here: the dependency was never declared
        // in app/build.gradle.kts, so Class.forName always threw
        // ClassNotFoundException — ~5–20 ms of main-thread reflection +
        // class-loader walk + Log.w per cold start for zero functional gain.
        // If PDF text search via PDFBox is re-added in the future, initialise
        // it on its own background dispatcher from the PDF viewer's
        // first-launch flow, not here on the application-onCreate critical
        // path.)
        // Preload UI feedback sounds + probe haptic capabilities (async loads, ~1 ms here).
        com.twinlore.app.ui.utils.FeedbackManager.initialize(this)
        PerfLog.mark("Application.onCreate end")
    }
}

/**
 * Collect every leaf directory under the four guide-cache roots so the
 * MHT + EPUB cache managers can LRU-prune them. Each manager filters by
 * its own marker file (`.mht_cache_v4` / `.epub_cache_v2`); dirs without a
 * recognized marker are ignored.
 *
 * Layout:
 *  - cache/guide_cache/<bookmarkId>/                   (legacy / EPUB)
 *  - cache/single_guide_render/<bookmarkId>/           (single-guide MHT)
 *  - cache/group_render/<groupTabId>/<resourceId>/     (page-group MHT)
 */
internal fun enumerateGuideCacheDirs(cacheDir: File): List<File> {
    val singleRoots = listOf("guide_cache", "single_guide_render")
        .map { File(cacheDir, it) }
        .flatMap { it.listFiles()?.filter { d -> d.isDirectory }.orEmpty() }
    val groupLeaves = File(cacheDir, "group_render")
        .listFiles()
        ?.filter { it.isDirectory }
        ?.flatMap { it.listFiles()?.filter { d -> d.isDirectory }.orEmpty() }
        .orEmpty()
    return singleRoots + groupLeaves
}

/**
 * Wipe `cacheDir/WebView/Default/HTTP Cache/` when its on-disk size exceeds
 * [thresholdBytes]. Returns the size that was freed (0 if below threshold,
 * dir missing, or wipe failed). Chromium recreates the dir lazily on the
 * next request, so callers don't need to ensure the WebView is initialized.
 *
 * We delete the whole `HTTP Cache` subtree (bodies + Code Cache + index)
 * rather than just `Cache_Data` so the index doesn't end up pointing at
 * deleted entries.
 */
internal fun trimWebViewHttpCacheIfOverThreshold(
    cacheDir: File,
    thresholdBytes: Long = TwinLoreApplication.WEBVIEW_HTTP_CACHE_THRESHOLD_BYTES,
): Long {
    val httpCacheDir = File(cacheDir, "WebView/Default/HTTP Cache")
    if (!httpCacheDir.exists() || !httpCacheDir.isDirectory) return 0L
    val size = computeDirSize(httpCacheDir)
    if (size < thresholdBytes) return 0L
    return if (httpCacheDir.deleteRecursively()) size else 0L
}

/** Recursive file-bytes sum. Doesn't include directory-entry overhead. */
private fun computeDirSize(dir: File): Long {
    if (!dir.exists()) return 0L
    var total = 0L
    dir.walkTopDown().forEach { if (it.isFile) total += it.length() }
    return total
}
