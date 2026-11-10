// app/src/main/java/com/twinlore/app/data/MhtCacheManager.kt
package com.twinlore.app.data

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages parsed MHT cache. MHT files are parsed at import time and the result
 * (index.html + manifest.json + assets/) is written to the bookmark directory.
 * On load, OfflineGuideLoader reads the cached files — same path as HTML guides.
 *
 * Cache eviction: LRU by last-access timestamp. Keeps the 20 most recently
 * accessed entries. Eviction removes parsed cache only; the original .mht file
 * is always kept so re-parsing on next access is transparent.
 */
@Singleton
class MhtCacheManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mhtParser: MhtParser
) {
    companion object {
        private const val TAG = "MhtCacheManager"
        // Cap = 0 (was 20) — the parsed-MHT cache produced no perceptible
        // benefit for linear reading flows in an on-device A/B.
        // The cache hit only saves the MHT MIME parse + asset extraction
        // (1.5-3 s on a fat chapter); WebView load + the onPageFinished
        // work (anchor scan, reading-mode strip, palette inject,
        // scroll-restore poll) re-runs on every visit regardless of cache,
        // and that's what dominates the perceived load delay. Cap=0 makes
        // cold-start evictOldEntries() wipe every leaf and keeps the
        // long-tail disk footprint down (~250 MB observed on a heavy-user
        // device pre-change). Within a session the cache still builds —
        // the WebView needs files on disk to load from — so revisits to
        // the same chapter within one process stay fast.
        //
        // === If we want the cache back ===
        //
        // The right shape is NOT an entry-count or
        // total-size cap. Real usage is "the user is reading 1-2 guides
        // at a time and could land on any chapter inside one of them."
        // We don't know which chapters they'll visit, so:
        //
        //   - Eagerly fully-extract the active group(s) when a guide is
        //     opened — every chapter cached up-front, so Prev/Next/ToC
        //     across the whole group is instant.
        //   - "Move on" = a tracked signal (closePageGroupForVault, or
        //     opening a new group beyond the N=1-2 hot window, or an
        //     inactivity timeout). When that fires, wipe the demoted
        //     group's entire `cache/group_render/<groupId>/` subtree.
        //
        // That gives the user the best-case experience for the guides
        // they're actually using, and bounds disk usage at "2 groups
        // worth" instead of "an LRU of 20 random leaves." Single-guide
        // bookmarks are a separate question — they have no group
        // boundary, so the natural shape there is a small recent-set
        // (last 2-3 opened bookmarks). Decide both at the same time.
        //
        // === Background you'll want before touching this ===
        //
        // - A cache hit skips MhtCacheManager.parseAndCache (SAF read +
        //   multipart parse + asset extract). The viewer still re-reads
        //   index.html and re-runs every onPageFinished JS pass on
        //   every load. The cache isn't a "skip the whole load" thing.
        //
        // - evictOldEntries currently runs ONLY at cold start
        //   (TwinLoreApplication.onCreate). For the per-group strategy
        //   above, eviction needs a runtime hook from PageGroupViewer's
        //   close path — not just the cold-start sweep.
        //
        // - Reasons usage patterns might tip the cost-benefit back
        //   towards turning the cache on:
        //     * Heavy Prev/Next bouncing within a page-group
        //     * ToC tap to revisit an earlier chapter mid-session
        //     * Cold-start auto-resume to a deep chapter
        //     * Slow storage (cheap eMMC) where parse cost dominates
        private const val MAX_CACHED_ENTRIES = 0
        // Bumped from ".mht_cache" so caches written before the manifest-path
        // fix (which stored "assets/<name>" instead of "assets/mht_assets/<name>",
        // the actual on-disk location MhtParser writes to) get transparently
        // re-parsed on next access. Old marker files are orphaned and swept by
        // evictOldEntries when MAX_CACHED_ENTRIES is exceeded.
        //
        // Bumped again to v3 so caches written before [SOURCE_URL_FILE] existed
        // get re-parsed once and pick up their recorded source URL. Without the
        // bump, a hit on a v2 leaf would skip parseAndCache forever and
        // [readSourceUrl] would keep returning null for that guide.
        //
        // Bumped again to v4 so caches written before HtmlScriptStripper was
        // wired in get re-parsed and lose any scripts/handlers the page
        // shipped with. Without the bump, a hit on a v3 leaf would keep
        // serving unstripped HTML from a file:// origin with JS on.
        private const val CACHE_MARKER_FILE = ".mht_cache_v4"

        /**
         * The page URL this MHT was saved from, as recorded in the archive's own
         * `Content-Location` header. One line, no trailing newline.
         *
         * Exists because [com.twinlore.app.domain.SavedTab.url] is empty for
         * guides adopted from a scanned media folder — a sync app dropping .mht
         * files into a vault, rather than an in-app browser save (see
         * `LocalMediaBinder.insertSavedTab`, which inserts `url = ""`). Reading
         * mode resolves its per-host filter from that URL, so those guides got
         * no chrome strip at all, on any host. The archive knows where it came
         * from even when the database doesn't; this is where we keep the answer.
         */
        private const val SOURCE_URL_FILE = "source-url.txt"
    }

    /**
     * The URL [bookmarkDir]'s archive was saved from, or null when unknown
     * (no cache yet, cache predating [SOURCE_URL_FILE], or an archive whose
     * `Content-Location` was missing or not http(s)).
     *
     * Callers treat null as "no source URL" and fall back to their existing
     * behaviour — never to a guessed URL. A wrong host would pick the wrong
     * reading-mode filter, which is worse than none.
     */
    fun readSourceUrl(bookmarkDir: File): String? =
        runCatching {
            File(bookmarkDir, SOURCE_URL_FILE)
                .takeIf { it.exists() }
                ?.readText(Charsets.UTF_8)
                ?.trim()
                ?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
        }.getOrNull()

    /**
     * Parse an MHT file and write the result to [bookmarkDir] as index.html + manifest.json + assets.
     * Call this at import time. Returns true if cache was written successfully.
     */
    fun parseAndCache(mhtFile: File, bookmarkDir: File): Boolean {
        val cacheDir = File(bookmarkDir, "assets")
        val parseResult = mhtParser.parse(mhtFile, cacheDir) ?: return false

        try {
            // Write parsed HTML as index.html, with the page's own scripts,
            // handlers and javascript: URLs removed: this file is loaded from a
            // file:// origin with JavaScript on for the app's own scripts.
            File(bookmarkDir, "index.html").writeText(
                HtmlScriptStripper.strip(parseResult.html, parseResult.baseUrl ?: ""),
                Charsets.UTF_8,
            )

            // Write manifest.json (content-location URL → local relative path).
            // MhtParser writes resources to `cacheDir/mht_assets/<filename>` (the
            // "mht_assets" subdir is hardcoded inside MhtParser so it can clear
            // stale state with deleteRecursively without nuking siblings the
            // caller put in cacheDir, like index.html). The cacheDir we hand
            // it is `bookmarkDir/assets/`, so files land at
            // `bookmarkDir/assets/mht_assets/<filename>` and the manifest path
            // — used by buildMhtAssetInterceptor with baseDir = bookmarkDir —
            // must carry both segments. The earlier `"assets/$localPath"` form
            // was off by one directory and caused EVERY MHT-extracted resource
            // (images, CSS, fonts) to silently miss; pages fell back on the
            // network, which only worked while online.
            val manifest = JSONObject()
            for ((url, localPath) in parseResult.manifest) {
                manifest.put(url, "assets/mht_assets/$localPath")
            }
            File(bookmarkDir, "manifest.json").writeText(manifest.toString(2), Charsets.UTF_8)

            // Record where this archive came from. `parseResult.baseUrl` is the
            // main HTML part's Content-Location; `extractOriginalUrl` re-reads
            // the same header from the raw file and is the fallback for the
            // parse paths that don't carry it (notably the no-MIME-boundary
            // "treat it as plain HTML" branch, which returns baseUrl = null).
            // Only http(s) is stored — `cid:` and other opaque locations are
            // not page URLs and would resolve no host filter anyway.
            val sourceUrl = parseResult.baseUrl
                ?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
                ?: mhtParser.extractOriginalUrl(mhtFile)
            val sourceUrlFile = File(bookmarkDir, SOURCE_URL_FILE)
            if (sourceUrl != null) {
                sourceUrlFile.writeText(sourceUrl, Charsets.UTF_8)
            } else {
                // Re-parsing a dir that previously had a URL must not leave the
                // old one behind pointing at a different archive.
                sourceUrlFile.delete()
            }

            // Write cache marker with timestamp
            File(bookmarkDir, CACHE_MARKER_FILE).writeText(
                System.currentTimeMillis().toString()
            )

            Log.d(TAG, "Cached MHT parse result: ${parseResult.html.length} chars HTML, ${parseResult.manifest.size} assets")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write MHT cache", e)
            return false
        }
    }

    /**
     * Check if a cached parse result exists for this bookmark directory.
     */
    fun hasCachedResult(bookmarkDir: File): Boolean {
        return File(bookmarkDir, CACHE_MARKER_FILE).exists()
            && File(bookmarkDir, "index.html").exists()
    }

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
            Log.d(TAG, "Evicting MHT cache: ${dir.name}")
            File(dir, "index.html").delete()
            File(dir, "manifest.json").delete()
            File(dir, "assets").deleteRecursively()
            File(dir, SOURCE_URL_FILE).delete()
            File(dir, CACHE_MARKER_FILE).delete()
        }
        Log.d(TAG, "Evicted ${toEvict.size} MHT cache entries, kept ${MAX_CACHED_ENTRIES}")
    }
}
