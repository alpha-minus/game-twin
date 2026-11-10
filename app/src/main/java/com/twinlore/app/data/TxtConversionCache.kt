package com.twinlore.app.data

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Caches converted text FAQ HTML in the app's cache directory.
 *
 * Cache location: cacheDir/guide_html_cache/{bookmarkId}/index.html
 * Ephemeral — system can clear cacheDir anytime. App re-derives on miss.
 *
 * LRU eviction: keeps 50 most recently accessed entries.
 */
@Singleton
class TxtConversionCache @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "TxtConversionCache"
        // Bumped on conversion-format changes so stale cached HTML is discarded
        // (cache is served verbatim across app updates, else fixes never surface):
        //   v2 — fixed text-FAQ extractor that dropped every other <pre> block on
        //        large multi-faqspan GameFAQs guides (e.g. Final Fantasy V).
        //   v3 — content-visibility chunking so multi-MB guides don't OOM-kill the
        //        app rendering the whole document at once.
        //   v4 — cap oversized guides (MAX_TEXT_GUIDE_RENDER_CHARS) to stop the
        //        crash-loop while per-page loading is built.
        //   v5 — oversized guides now split into page-groups; discard v4's capped
        //        single-render HTML so they re-extract and split instead.
        //   v6 — extractor preserves blank lines in <pre>, list-marker lines are
        //        prose, join requires hard-wrap evidence, font boosting disabled
        //        (GH #25: merged headings, clipped bullets, mixed text sizes).
        //   v7 — lossless run-based emission so the Smart Formatting toggle can
        //        fall back to the author's original layout, plus the batched,
        //        single-layout fit pass.
        private const val CACHE_DIR_NAME = "guide_html_cache_v7"
        private const val MAX_CACHED_ENTRIES = 50
        private const val CACHE_MARKER = ".cache_marker"
    }

    private fun cacheDir(bookmarkId: String): File {
        return File(File(context.cacheDir, CACHE_DIR_NAME), bookmarkId)
    }

    fun readCachedHtml(bookmarkId: String): String? {
        val file = File(cacheDir(bookmarkId), "index.html")
        return if (file.exists()) {
            try {
                file.readText(Charsets.UTF_8)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to read cache for $bookmarkId", e)
                null
            }
        } else null
    }

    fun writeCachedHtml(bookmarkId: String, html: String) {
        try {
            val dir = cacheDir(bookmarkId)
            dir.mkdirs()
            File(dir, "index.html").writeText(html, Charsets.UTF_8)
            File(dir, CACHE_MARKER).writeText(System.currentTimeMillis().toString())
            Log.d(TAG, "Cached conversion: $bookmarkId (${html.length} chars)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write cache for $bookmarkId", e)
        }
    }

    fun touch(bookmarkId: String) {
        val marker = File(cacheDir(bookmarkId), CACHE_MARKER)
        if (marker.exists()) {
            try { marker.writeText(System.currentTimeMillis().toString()) } catch (_: Exception) {}
        }
    }

    fun evictOldEntries() {
        val root = File(context.cacheDir, CACHE_DIR_NAME)
        if (!root.exists()) return
        val dirs = root.listFiles()?.filter { it.isDirectory } ?: return
        val cached = dirs.mapNotNull { dir ->
            val marker = File(dir, CACHE_MARKER)
            if (marker.exists()) {
                val ts = marker.readText().trim().toLongOrNull() ?: 0L
                dir to ts
            } else null
        }.sortedByDescending { it.second }

        if (cached.size <= MAX_CACHED_ENTRIES) return
        val toEvict = cached.drop(MAX_CACHED_ENTRIES)
        for ((dir, _) in toEvict) {
            dir.deleteRecursively()
        }
        Log.d(TAG, "Evicted ${toEvict.size} entries, kept $MAX_CACHED_ENTRIES")
    }

    fun remove(bookmarkId: String) {
        cacheDir(bookmarkId).deleteRecursively()
    }
}
