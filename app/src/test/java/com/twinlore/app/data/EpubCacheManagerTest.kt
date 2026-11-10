package com.twinlore.app.data

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File

class EpubCacheManagerTest {
    @get:Rule val tmp = TemporaryFolder()

    private val ctx: Context = mock()

    private fun fixture(name: String): File =
        File(javaClass.classLoader!!.getResource("epub/$name")!!.toURI())

    @Test fun `parseAndCache writes index_html manifest and marker`() {
        val mgr = EpubCacheManager(ctx, EpubParser())
        val bookmarkDir = tmp.newFolder("b")
        val ok = mgr.parseAndCache(fixture("minimal-epub3.epub"), bookmarkDir)
        assertTrue("parseAndCache should succeed", ok)
        assertTrue(File(bookmarkDir, "index.html").exists())
        assertTrue(File(bookmarkDir, "manifest.json").exists())
        assertTrue(File(bookmarkDir, ".epub_cache_v2").exists())
        assertTrue(mgr.hasCachedResult(bookmarkDir))
    }

    @Test fun `evictOldEntries keeps 20 most-recent`() {
        val mgr = EpubCacheManager(ctx, EpubParser())
        val dirs = (1..25).map { i ->
            tmp.newFolder("b$i").also {
                File(it, "index.html").writeText("x")
                File(it, ".epub_cache_v2").writeText((1000L + i).toString())
            }
        }
        mgr.evictOldEntries(dirs)
        // Five oldest evicted (timestamps 1001..1005)
        assertFalse(File(dirs[0], "index.html").exists())
        assertFalse(File(dirs[4], "index.html").exists())
        // Newest kept
        assertTrue(File(dirs[24], "index.html").exists())
    }

    @Test fun `touchCache updates marker timestamp`() {
        val mgr = EpubCacheManager(ctx, EpubParser())
        val dir = tmp.newFolder("b")
        val marker = File(dir, ".epub_cache_v2").apply { writeText("0") }
        Thread.sleep(2)  // ensure timestamp difference
        mgr.touchCache(dir)
        val updated = marker.readText().trim().toLong()
        assertTrue("marker advanced past 0", updated > 0L)
    }

    @Test fun `hasCachedResult is false when index_html missing`() {
        val mgr = EpubCacheManager(ctx, EpubParser())
        val dir = tmp.newFolder("b")
        File(dir, ".epub_cache_v2").writeText("123")  // marker only, no index.html
        assertFalse(mgr.hasCachedResult(dir))
    }

    @Test fun `parseAndCache strips page scripts before writing index_html`() {
        val parser: EpubParser = mock()
        whenever(parser.parse(any(), any())).thenReturn(
            EpubParser.ParseResult(
                html = "<html><body><script>alert(1)</script><p>Chapter</p></body></html>",
                baseUrl = null,
                manifest = emptyMap(),
                assetDir = null,
            )
        )
        val mgr = EpubCacheManager(ctx, parser)
        val bookmarkDir = tmp.newFolder("b")
        assertTrue(mgr.parseAndCache(tmp.newFile("in.epub"), bookmarkDir))
        val index = File(bookmarkDir, "index.html").readText(Charsets.UTF_8)
        assertFalse(index.contains("<script", ignoreCase = true))
        assertTrue(index.contains("Chapter"))
        assertTrue(File(bookmarkDir, ".epub_cache_v2").exists())
    }
}
