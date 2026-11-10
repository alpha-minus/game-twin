package com.twinlore.app.ui.screens.guide

import android.net.Uri
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The question the fold panel's link guard asks before it refuses anything.
 *
 * It has to agree with the viewer's own anchor handling further down the same
 * function. Getting it wrong in one direction sends every contents link in a
 * long walkthrough to a "links open in the app" banner; getting it wrong in the
 * other lets an external link through to a `startActivity` that drops a player
 * out of a running game. Both are worth a test that needs no WebView.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class InDocumentJumpTest {

    private val bookmarkUrl = "https://strategywiki.org/wiki/Castlevania/Walkthrough"

    private fun jump(url: String) = isInDocumentJump(url, bookmarkUrl, Uri.parse(url))

    @Test
    fun `a bare fragment is a jump`() {
        assertTrue(jump("#chapter-3"))
    }

    // Saved pages are served under a localhost base, so a relative anchor
    // resolves to localhost.invalid with the fragment intact.
    @Test
    fun `an anchor resolved against the offline base is a jump`() {
        assertTrue(jump("https://localhost.invalid/wiki/Castlevania#boss-rush"))
    }

    @Test
    fun `an anchor in the bookmark's own url is a jump`() {
        assertTrue(jump("$bookmarkUrl#appendix"))
    }

    // The query string is stripped before comparing, the same way the viewer's
    // own branch does it, or a link carrying a cache buster stops matching.
    @Test
    fun `a query string does not stop the bookmark matching`() {
        assertTrue(jump("$bookmarkUrl?printable=yes#appendix"))
    }

    @Test
    fun `another page on the same site is not a jump`() {
        assertFalse(jump("https://strategywiki.org/wiki/Castlevania/Enemies"))
    }

    @Test
    fun `an off-site link is not a jump, fragment or no fragment`() {
        assertFalse(jump("https://www.youtube.com/watch?v=abc"))
        assertFalse(jump("https://gamefaqs.gamespot.com/ds/1234#top"))
    }

    @Test
    fun `a scheme with nowhere to go is not a jump`() {
        assertFalse(jump("mailto:someone@example.com"))
        assertFalse(jump("intent://watch#Intent;package=com.google.android.youtube;end"))
    }

    // The localhost base with no fragment is a page load, not a jump: the
    // group viewer's cross-entry matching handles those.
    @Test
    fun `the offline base without a fragment is not a jump`() {
        assertFalse(jump("https://localhost.invalid/wiki/Castlevania"))
    }

    // Not a jump, and not a link either: cid: is MHTML's internal reference
    // between the parts of a saved page. Seen on the Thor, where the panel
    // answered a Castlevania page's own frame load with "links open in the
    // app" and queued a browser tab for it. It is excluded by the scheme
    // check rather than by this, which is why this test says so out loud.
    @Test
    fun `an mhtml internal reference is not a jump`() {
        assertFalse(jump("cid:frame-DE0665CCDC6CA8FB7786251D2917183E@mhtml.blink"))
    }
}
