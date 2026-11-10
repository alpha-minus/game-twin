package com.twinlore.app.data

import android.content.Context
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MhtCacheManagerTest {
    @get:Rule val tmp = TemporaryFolder()

    private val ctx: Context = mock()

    private fun fixture(name: String): File =
        File(javaClass.classLoader!!.getResource("vgmaps/$name")!!.toURI())

    /**
     * Regression for the silent off-by-one-directory bug: parseAndCache used to
     * write manifest values as "assets/<filename>" but MhtParser actually writes
     * resources under "assets/mht_assets/<filename>". Every entry then failed
     * `File(baseDir, manifestValue).exists()` in buildMhtAssetInterceptor, so
     * MHT-saved images, CSS and fonts all disappeared.
     *
     * Contract: every relative path in manifest.json MUST resolve to an
     * existing file under bookmarkDir.
     */
    @Test fun `manifest paths resolve to actual files on disk`() {
        val mgr = MhtCacheManager(ctx, MhtParser())
        val bookmarkDir = tmp.newFolder("b")
        val ok = mgr.parseAndCache(fixture("directory.mht"), bookmarkDir)
        assertTrue("parseAndCache should succeed", ok)
        val manifestText = File(bookmarkDir, "manifest.json").readText(Charsets.UTF_8)
        val manifest = JSONObject(manifestText)
        val keys = manifest.keys().asSequence().toList()
        assertTrue("fixture should yield a non-empty manifest", keys.isNotEmpty())
        for (key in keys) {
            val relPath = manifest.getString(key)
            val resolved = File(bookmarkDir, relPath)
            assertTrue(
                "manifest path $relPath for $key must exist at $resolved",
                resolved.exists() && resolved.isFile
            )
        }
    }

    @Test fun `parseAndCache writes index_html manifest and v4 marker`() {
        val mgr = MhtCacheManager(ctx, MhtParser())
        val bookmarkDir = tmp.newFolder("b")
        val ok = mgr.parseAndCache(fixture("directory.mht"), bookmarkDir)
        assertTrue("parseAndCache should succeed", ok)
        assertTrue(File(bookmarkDir, "index.html").exists())
        assertTrue(File(bookmarkDir, "manifest.json").exists())
        assertTrue(File(bookmarkDir, ".mht_cache_v4").exists())
        // Old marker names should NOT be created on fresh parse.
        assertFalse(File(bookmarkDir, ".mht_cache").exists())
        assertFalse(File(bookmarkDir, ".mht_cache_v2").exists())
        assertTrue(mgr.hasCachedResult(bookmarkDir))
    }

    /**
     * Caches written before the manifest-path fix carried the old `.mht_cache`
     * marker. hasCachedResult MUST return false for those so parseAndCache
     * runs again and rewrites the manifest with the correct paths.
     */
    @Test fun `hasCachedResult ignores legacy mht_cache marker`() {
        val mgr = MhtCacheManager(ctx, MhtParser())
        val bookmarkDir = tmp.newFolder("b")
        File(bookmarkDir, "index.html").writeText("<html></html>")
        File(bookmarkDir, ".mht_cache").writeText(System.currentTimeMillis().toString())
        assertFalse(
            "legacy marker should not be treated as a valid cache",
            mgr.hasCachedResult(bookmarkDir)
        )
    }

    /**
     * v2 caches have no source-url.txt. A hit on one would skip parseAndCache
     * forever, so readSourceUrl could never recover the URL for that guide and
     * reading mode would stay off. The v3 bump forces exactly one re-parse.
     */
    @Test fun `hasCachedResult ignores v2 marker so source url is recovered`() {
        val mgr = MhtCacheManager(ctx, MhtParser())
        val bookmarkDir = tmp.newFolder("b")
        File(bookmarkDir, "index.html").writeText("<html></html>")
        File(bookmarkDir, ".mht_cache_v2").writeText(System.currentTimeMillis().toString())
        assertFalse(
            "a v2 cache predates source-url.txt and must be re-parsed",
            mgr.hasCachedResult(bookmarkDir)
        )
    }

    // -- Source URL recovery ----------------------------------------------
    //
    // Guides adopted from a scanned media folder are inserted with url = ""
    // (LocalMediaBinder), so the viewer has no host to resolve a reading-mode
    // filter from. The archive's own Content-Location is the fallback.

    @Test fun `parseAndCache records the archive source url`() {
        val mgr = MhtCacheManager(ctx, MhtParser())
        val bookmarkDir = tmp.newFolder("b")
        assertTrue(mgr.parseAndCache(fixture("directory.mht"), bookmarkDir))
        assertEquals(
            "https://www.vgmaps.com/Atlas/index.htm",
            mgr.readSourceUrl(bookmarkDir)
        )
    }

    @Test fun `readSourceUrl is null when no cache has been written`() {
        val mgr = MhtCacheManager(ctx, MhtParser())
        assertNull(mgr.readSourceUrl(tmp.newFolder("empty")))
    }

    /**
     * Callers fall back on null, never on a guess — a non-http Content-Location
     * (`cid:`, `file:`) is not a page URL and must not reach filterForHost.
     */
    @Test fun `readSourceUrl rejects a non-http recorded value`() {
        val mgr = MhtCacheManager(ctx, MhtParser())
        val bookmarkDir = tmp.newFolder("b")
        File(bookmarkDir, "source-url.txt").writeText("cid:frame-0@example")
        assertNull(mgr.readSourceUrl(bookmarkDir))
    }

    /**
     * End-to-end for the reported case: a StrategyWiki archive dropped into a
     * vault by a sync app. The DB row carries no URL, so the recovered one is
     * what reading mode resolves its host filter from.
     */
    @Test fun `recovered url resolves the strategywiki reading-mode filter`() {
        val mgr = MhtCacheManager(ctx, MhtParser())
        val bookmarkDir = tmp.newFolder("b")
        val mht = tmp.newFile("scanned.mht").apply {
            writeText(
                """
                From: <Saved by Blink>
                Snapshot-Content-Location: https://strategywiki.org/wiki/Castlevania:_Aria_of_Sorrow/Chapel
                Subject: Chapel
                MIME-Version: 1.0
                Content-Type: multipart/related; type="text/html"; boundary="----BOUNDARY"

                ------BOUNDARY
                Content-Type: text/html
                Content-Transfer-Encoding: quoted-printable
                Content-Location: https://strategywiki.org/wiki/Castlevania:_Aria_of_Sorrow/Chapel

                <html><body><div id=3D"mw-content-text">body</div></body></html>
                ------BOUNDARY--
                """.trimIndent().replace("\n", "\r\n"),
                Charsets.UTF_8,
            )
        }
        assertTrue(mgr.parseAndCache(mht, bookmarkDir))
        val recovered = mgr.readSourceUrl(bookmarkDir)
        assertEquals(
            "https://strategywiki.org/wiki/Castlevania:_Aria_of_Sorrow/Chapel",
            recovered,
        )
        // The whole point of recovering it: an empty bookmark.url resolves no
        // filter, the recovered one resolves strategywiki.
        assertNull(
            "an empty url must resolve no filter — that was the bug",
            com.twinlore.app.ui.screens.offline.filterForHost(""),
        )
        assertEquals(
            "strategywiki",
            com.twinlore.app.ui.screens.offline.filterForHost(recovered)?.name,
        )
    }

    @Test fun `parseAndCache strips page scripts before writing index_html`() {
        val parser: MhtParser = mock()
        whenever(parser.parse(any(), any())).thenReturn(
            MhtParser.ParseResult(
                html = "<html><body><script>alert(1)</script><p onclick=\"x()\">Guide</p></body></html>",
                baseUrl = "https://example.com/guide",
                manifest = emptyMap(),
                assetDir = null,
            )
        )
        val mgr = MhtCacheManager(ctx, parser)
        val bookmarkDir = tmp.newFolder("b")
        assertTrue(mgr.parseAndCache(tmp.newFile("in.mht"), bookmarkDir))
        val index = File(bookmarkDir, "index.html").readText(Charsets.UTF_8)
        assertFalse(index.contains("<script", ignoreCase = true))
        assertFalse(index.contains("onclick", ignoreCase = true))
        assertTrue(index.contains("Guide"))
        assertTrue(File(bookmarkDir, ".mht_cache_v4").exists())
    }
}
