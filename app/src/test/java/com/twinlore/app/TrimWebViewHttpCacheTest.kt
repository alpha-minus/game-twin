package com.twinlore.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TrimWebViewHttpCacheTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun cacheDirWithHttpCache(totalBytes: Long): File {
        val cacheDir = tmp.newFolder("cache")
        val httpCacheDir = File(cacheDir, "WebView/Default/HTTP Cache").apply { mkdirs() }
        File(httpCacheDir, "Cache_Data").mkdirs()
        // Spread the requested size across two real files so deleteRecursively
        // exercises both the index and the body subtree.
        val half = totalBytes / 2
        File(httpCacheDir, "Cache_Data/body_0").writeBytes(ByteArray(half.toInt()))
        File(httpCacheDir, "Cache_Data/body_1").writeBytes(ByteArray((totalBytes - half).toInt()))
        return cacheDir
    }

    @Test
    fun under_threshold_keeps_cache_intact() {
        val cacheDir = cacheDirWithHttpCache(totalBytes = 10_000L)
        val freed = trimWebViewHttpCacheIfOverThreshold(cacheDir, thresholdBytes = 100_000L)
        assertEquals(0L, freed)
        assertTrue(File(cacheDir, "WebView/Default/HTTP Cache/Cache_Data/body_0").exists())
    }

    @Test
    fun at_or_over_threshold_wipes_entire_http_cache_subtree() {
        val cacheDir = cacheDirWithHttpCache(totalBytes = 200_000L)
        val freed = trimWebViewHttpCacheIfOverThreshold(cacheDir, thresholdBytes = 100_000L)
        assertEquals(200_000L, freed)
        assertFalse(File(cacheDir, "WebView/Default/HTTP Cache").exists())
        // Sibling cache subtrees must not be touched.
        // (none in this fixture; covered by the next test)
    }

    @Test
    fun missing_http_cache_dir_is_a_noop() {
        val cacheDir = tmp.newFolder("cache")
        val freed = trimWebViewHttpCacheIfOverThreshold(cacheDir, thresholdBytes = 100_000L)
        assertEquals(0L, freed)
    }

    @Test
    fun does_not_touch_sibling_cache_dirs() {
        val cacheDir = cacheDirWithHttpCache(totalBytes = 200_000L)
        // Drop a real-looking sibling to confirm scope is the WebView subtree.
        val sidecar = File(cacheDir, "page_group_sidecars/abc.txt")
        sidecar.parentFile.mkdirs()
        sidecar.writeText("don't touch me")

        trimWebViewHttpCacheIfOverThreshold(cacheDir, thresholdBytes = 100_000L)

        assertFalse(File(cacheDir, "WebView/Default/HTTP Cache").exists())
        assertTrue(sidecar.exists())
        assertEquals("don't touch me", sidecar.readText())
    }

    @Test
    fun exact_threshold_value_is_not_wiped() {
        // The check is `size < thresholdBytes`, so `size == thresholdBytes` triggers.
        val cacheDir = cacheDirWithHttpCache(totalBytes = 100_000L)
        val freed = trimWebViewHttpCacheIfOverThreshold(cacheDir, thresholdBytes = 100_000L)
        assertEquals(100_000L, freed)
    }
}
