package com.twinlore.app.ui.screens.browser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserScrollRestoreJsTest {

    @Test
    fun `embeds the target scroll position`() {
        assertTrue(BrowserScrollRestoreJs.build(1234).contains("var target = 1234;"))
    }

    @Test
    fun `never restores past the document's max scroll`() {
        // The whole point: a saved Y that exceeds the reopened (shorter) page's
        // range must be clamped, never applied raw — otherwise the WebView dumps
        // the user at the bottom.
        val js = BrowserScrollRestoreJs.build(5000)
        assertTrue("missing clamp to max", js.contains("Math.min(target, m.max)"))
        assertFalse(
            "must not scroll to the raw target unconditionally",
            js.contains("window.scrollTo(0, target)"),
        )
    }

    @Test
    fun `falls back to the top when the target is unreachable by more than a viewport`() {
        // User-stated expectation: a page that can no longer honor the saved
        // position should open at the TOP, not the bottom.
        val js = BrowserScrollRestoreJs.build(5000)
        assertTrue(
            "missing unreachable -> top guard",
            js.contains("target - m.max > window.innerHeight") && js.contains("window.scrollTo(0, 0)"),
        )
    }

    @Test
    fun `polls until the document height settles`() {
        // A reopened page is re-laid-out live (images decode, blocked banners
        // collapse), so the restore must re-apply across the settle window
        // instead of firing once against an intermediate height.
        val js = BrowserScrollRestoreJs.build(100)
        assertTrue("missing height-change re-apply", js.contains("m.h !== lastH"))
        assertTrue("missing stability counter", js.contains("stable >= 5"))
        assertTrue("missing tick ceiling", js.contains("ticks >= 20"))
        assertTrue("missing poll cadence", js.contains("setTimeout(tick, 100)"))
    }
}
