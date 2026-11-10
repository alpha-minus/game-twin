package com.twinlore.app.ui.screens.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for the host-comparison helpers used by both offline viewers'
 * long-press handlers to discriminate external image-anchors (donate
 * buttons, sponsored banners) from same-host ones (MediaWiki File: thumbs,
 * OmegaMetroid sidebar tiles).
 *
 * Robolectric for `android.net.Uri.parse` — no Android views involved.
 */
@RunWith(RobolectricTestRunner::class)
class IsExternalAnchorTest {

    // -- parseHost --------------------------------------------------------

    @Test
    fun `parseHost returns lowercased host for normal URLs`() {
        assertEquals("gamefaqs.gamespot.com",
            parseHost("https://gamefaqs.gamespot.com/ps4/x/faqs/1"))
        assertEquals("www.paypal.com",
            parseHost("https://WWW.PAYPAL.COM/cgi-bin/webscr"))
    }

    @Test
    fun `parseHost returns null for null and blank`() {
        assertNull(parseHost(null))
        assertNull(parseHost(""))
        assertNull(parseHost("   "))
    }

    @Test
    fun `parseHost returns null for malformed and non-hierarchical URLs`() {
        assertNull(parseHost("not-a-url"))
        assertNull(parseHost("javascript:void(0)"))
    }

    @Test
    fun `parseHost returns null for cid and file schemes without host`() {
        // MHT inline resources arrive as cid:..., MhtParser writes to
        // file:// — neither carries a host. Treated as "same as doc".
        assertNull(parseHost("cid:foo@mhtml.blink"))
        assertNull(parseHost("file:///data/user/0/com.twinlore.app/cache/x.html"))
    }

    // -- isExternalAnchor -------------------------------------------------

    @Test
    fun `isExternalAnchor true when hosts differ`() {
        assertTrue(isExternalAnchor(
            docUrl = "https://gamefaqs.gamespot.com/ps4/x/faqs/1",
            anchorHref = "https://www.paypal.com/cgi-bin/webscr",
        ))
    }

    @Test
    fun `isExternalAnchor case-insensitive on host`() {
        assertFalse(isExternalAnchor(
            docUrl = "https://Bulbapedia.bulbagarden.net/wiki/X",
            anchorHref = "https://bulbapedia.BULBAGARDEN.net/wiki/File:Foo.png",
        ))
    }

    @Test
    fun `isExternalAnchor false when anchor href is relative or fragment`() {
        // hit.extra would resolve these to absolute, but assert the
        // safe-default for callers that pass relative strings: return
        // false (treat as image / same-host).
        assertFalse(isExternalAnchor(
            docUrl = "https://omegametroid.com/super-metroid-walkthrough/",
            anchorHref = "/super-metroid-walkthrough/a-derelict-ship/",
        ))
        assertFalse(isExternalAnchor(
            docUrl = "https://omegametroid.com/super-metroid-walkthrough/",
            anchorHref = "#chapter-1",
        ))
    }

    @Test
    fun `isExternalAnchor false when either side is null or blank`() {
        assertFalse(isExternalAnchor(null, "https://paypal.com/x"))
        assertFalse(isExternalAnchor("https://x.com", null))
        assertFalse(isExternalAnchor("", "https://paypal.com/x"))
        assertFalse(isExternalAnchor("https://x.com", ""))
    }

    @Test
    fun `isExternalAnchor false for cid and file schemes (treat as same-host)`() {
        // cid: and file:// have no host and frequently appear in MHT-
        // backed saved pages. Safe default is "same-host" → image dialog.
        assertFalse(isExternalAnchor(
            docUrl = "https://bulbapedia.bulbagarden.net/wiki/X",
            anchorHref = "cid:abc@mhtml.blink",
        ))
        assertFalse(isExternalAnchor(
            docUrl = "https://bulbapedia.bulbagarden.net/wiki/X",
            anchorHref = "file:///data/cache/x.png",
        ))
    }

    // -- isLocalAnchor ------------------------------------------------------

    @Test
    fun `isLocalAnchor true for localhost-invalid resolved hrefs`() {
        // Both viewers load via loadDataWithBaseURL("https://localhost.invalid/", …),
        // so HitTestResult.extra for a bare "#frag" href resolves to this shape.
        assertTrue(isLocalAnchor(
            docUrl = "https://gamefaqs.gamespot.com/ps4/x/faqs/1",
            anchorHref = "https://localhost.invalid/#section-3",
        ))
        // Works even when the doc URL is blank (TXT-converted guides).
        assertTrue(isLocalAnchor(docUrl = null, anchorHref = "https://localhost.invalid/#frag"))
    }

    @Test
    fun `isLocalAnchor true for same-host hrefs regardless of exact path shape`() {
        assertTrue(isLocalAnchor(
            docUrl = "https://omegametroid.com/metroid-fusion-walkthrough/",
            anchorHref = "https://omegametroid.com/metroid-fusion-walkthrough/#sector-2",
        ))
        assertTrue(isLocalAnchor(
            docUrl = "https://example.com/guide?page=2",
            anchorHref = "https://example.com/guide?page=2#boss",
        ))
        // Same host, different page: still local (host comparison, not the
        // brittle exact-base equality that broke fully-qualified ToC links).
        assertTrue(isLocalAnchor(
            docUrl = "https://omegametroid.com/walkthrough/",
            anchorHref = "https://omegametroid.com/other-page/#frag",
        ))
    }

    @Test
    fun `isLocalAnchor false for external hosts and blank hrefs`() {
        assertFalse(isLocalAnchor(
            docUrl = "https://omegametroid.com/walkthrough/",
            anchorHref = "https://paypal.com/donate",
        ))
        assertFalse(isLocalAnchor(docUrl = "https://x.com/a", anchorHref = null))
        assertFalse(isLocalAnchor(docUrl = "https://x.com/a", anchorHref = ""))
    }
}

class SplitNormalizedHttpUrlTest {

    @org.junit.Test
    fun `normalizes www scheme case and trailing slash`() {
        org.junit.Assert.assertEquals(
            "omegametroid.com" to "/walkthrough",
            splitNormalizedHttpUrl("HTTPS://WWW.OmegaMetroid.com/walkthrough/"),
        )
        org.junit.Assert.assertEquals(
            "omegametroid.com" to "/walkthrough",
            splitNormalizedHttpUrl("http://omegametroid.com/walkthrough"),
        )
    }

    @org.junit.Test
    fun `keeps query drops fragment`() {
        org.junit.Assert.assertEquals(
            "gamefaqs.gamespot.com" to "/x/faqs/1?page=2",
            splitNormalizedHttpUrl("https://gamefaqs.gamespot.com/x/faqs/1?page=2#boss"),
        )
    }

    @org.junit.Test
    fun `root and non-http are handled`() {
        org.junit.Assert.assertEquals(
            "localhost.invalid" to "",
            splitNormalizedHttpUrl("https://localhost.invalid/#frag"),
        )
        org.junit.Assert.assertNull(splitNormalizedHttpUrl("mailto:a@b.c"))
        org.junit.Assert.assertNull(splitNormalizedHttpUrl(null))
        org.junit.Assert.assertNull(splitNormalizedHttpUrl(""))
    }
}
