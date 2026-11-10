// app/src/main/java/com/twinlore/app/data/EpubCacheManager.kt
package com.twinlore.app.data

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages parsed EPUB cache. Mirrors MhtCacheManager: EPUB files are parsed at
 * import time and the result (index.html + manifest.json + assets/) is written
 * to the bookmark directory. On load, the same WebView file:// path is used.
 *
 * Cache eviction: LRU by last-access timestamp. Keeps the 20 most recently
 * accessed entries. Eviction removes parsed cache only; the original .epub
 * file is always kept so re-parsing on next access is transparent.
 *
 * NOTE: Kept separate from MhtCacheManager so the EPUB path can stabilize
 * independently. Once both are proven, the shared shape can be promoted to
 * an ArchiveCacheManager — but only as a follow-up cleanup.
 */
@Singleton
class EpubCacheManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val epubParser: EpubParser
) {
    companion object {
        private const val TAG = "EpubCacheManager"
        private const val MAX_CACHED_ENTRIES = 20
        private const val CACHE_MARKER_FILE = ".epub_cache_v2"
    }

    /**
     * Parse an EPUB file and write the result to [bookmarkDir] as index.html + manifest.json + assets.
     * Call this at import time. Returns true if cache was written successfully.
     */
    fun parseAndCache(epubFile: File, bookmarkDir: File): Boolean {
        bookmarkDir.mkdirs()
        val assetsDir = File(bookmarkDir, "assets")
        val parseResult = epubParser.parse(epubFile, assetsDir) ?: return false

        return try {
            // Same reason as MhtCacheManager: a file:// document with JS on.
            File(bookmarkDir, "index.html").writeText(
                HtmlScriptStripper.strip(parseResult.html, parseResult.baseUrl ?: ""),
                Charsets.UTF_8,
            )

            // Write manifest.json (content-location URL → local relative path).
            // EpubParser already prefixes manifest values with "assets/" — write the map verbatim.
            val manifest = JSONObject()
            for ((url, localPath) in parseResult.manifest) {
                manifest.put(url, localPath)
            }
            File(bookmarkDir, "manifest.json").writeText(manifest.toString(2), Charsets.UTF_8)

            // Write cache marker with timestamp
            File(bookmarkDir, CACHE_MARKER_FILE).writeText(
                System.currentTimeMillis().toString()
            )

            Log.d(TAG, "Cached EPUB: ${parseResult.html.length} chars HTML, ${parseResult.manifest.size} assets")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write EPUB cache", e)
            false
        }
    }

    /**
     * Check if a cached parse result exists for this bookmark directory.
     */
    fun hasCachedResult(bookmarkDir: File): Boolean =
        File(bookmarkDir, CACHE_MARKER_FILE).exists()
            && File(bookmarkDir, "index.html").exists()

    /**
     * Touch the cache entry timestamp (call on each guide load).
     */
    fun touchCache(bookmarkDir: File) {
        val marker = File(bookmarkDir, CACHE_MARKER_FILE)
        if (marker.exists()) {
            marker.writeText(System.currentTimeMillis().toString())
        }
    }

    /**
     * Evict oldest cache entries beyond MAX_CACHED_ENTRIES.
     * Call on app start or after import.
     *
     * @param allBookmarkDirs list of all bookmark directories for the current vault
     */
    fun evictOldEntries(allBookmarkDirs: List<File>) {
        val cached = allBookmarkDirs.mapNotNull { dir ->
            val marker = File(dir, CACHE_MARKER_FILE)
            if (marker.exists()) {
                val timestamp = marker.readText().trim().toLongOrNull() ?: 0L
                dir to timestamp
            } else null
        }.sortedByDescending { it.second }

        if (cached.size <= MAX_CACHED_ENTRIES) return

        val toEvict = cached.drop(MAX_CACHED_ENTRIES)
        for ((dir, _) in toEvict) {
            Log.d(TAG, "Evicting EPUB cache: ${dir.name}")
            File(dir, "index.html").delete()
            File(dir, "manifest.json").delete()
            File(dir, "assets").deleteRecursively()
            File(dir, CACHE_MARKER_FILE).delete()
        }
        Log.d(TAG, "Evicted ${toEvict.size} EPUB cache entries, kept $MAX_CACHED_ENTRIES")
    }
}
