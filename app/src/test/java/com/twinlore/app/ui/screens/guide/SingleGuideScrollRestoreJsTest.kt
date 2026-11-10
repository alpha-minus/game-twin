package com.twinlore.app.ui.screens.guide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The single-page viewer's resume-reading landing.
 *
 * Reported twice, most recently as "I leave a guide half way down, go back to
 * the library, come back, and it has scrolled further down — the lower I was,
 * the worse it is". The saved position is a ratio of the document height, so a
 * landing measured before the app finishes shrinking the document (the
 * text-size font-size injection in onPageFinished, the ASCII-block refit that
 * follows it) misses by `ratio * heightLost`, proportionally.
 */
class SingleGuideScrollRestoreJsTest {

    @Test
    fun `the target is recomputed from the live height, not measured once`() {
        val js = buildInlineScrollRestoreJs(0.62)
        assertTrue("height read is not inside the re-applied body", js.contains("scrollHeight"))
        assertTrue("ratio not applied to the live height", js.contains("h * 0.62"))
        assertTrue("landing is not re-applied when the height moves", js.contains("h !== lastH"))
    }

    @Test
    fun `the landing keeps up with a document that is still settling`() {
        val js = buildInlineScrollRestoreJs(0.62)
        assertTrue("no stability window", js.contains("stable >="))
        assertTrue("no ceiling", js.contains("ticks >="))
        assertTrue("no abort when the reader takes over", js.contains("stopped = true"))
    }

    @Test
    fun `nothing is spliced when there is no position to go back to`() {
        assertEquals("", buildInlineScrollRestoreJs(null))
        assertEquals("", buildInlineScrollRestoreJs(0.0))
        assertEquals("", buildInlineScrollRestoreJs(-1.0))
    }

    @Test
    fun `splices as its own script block just before the body close`() {
        val html = "<html><body><p>guide</p></body></html>"
        val spliced = spliceScrollRestoreScript(html, "window.scrollTo(0, 10);")
        assertTrue(spliced.contains("id=\"sgv-scroll-restore\""))
        assertTrue(spliced.indexOf("sgv-scroll-restore") < spliced.indexOf("</body>"))
    }
}
