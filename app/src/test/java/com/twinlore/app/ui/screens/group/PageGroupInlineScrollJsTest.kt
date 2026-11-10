package com.twinlore.app.ui.screens.group

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The page-group viewer's landing scroll, which picks one of three targets:
 * a history position, a chapter fragment, or the entry's saved reading ratio.
 *
 * All three used to be applied exactly once, at DOMContentLoaded, and the group
 * viewer never even got the window.load re-apply the single-page viewer has.
 * The document is still moving at that point — the text-size font-size lands in
 * onPageFinished and takes about a sixth of the height with it — so a ratio
 * measured there puts the reader `ratio * heightLost` too far down, which is
 * what "coming back to a group guide scrolls further than I left it" was.
 */
class PageGroupInlineScrollJsTest {

    @Test
    fun `the saved ratio is re-applied against the live height until it settles`() {
        val js = buildPageGroupInlineScrollJs(
            historyYCssPx = null,
            fragment = null,
            scrollRatio = 0.62,
        )
        assertTrue("ratio not applied to the live height", js.contains("h * 0.62"))
        assertTrue("not re-applied when the height moves", js.contains("h !== lastH"))
        assertTrue("no stability window", js.contains("stable >="))
        assertTrue("no abort when the reader takes over", js.contains("stopped = true"))
    }

    @Test
    fun `a chapter fragment re-measures its element instead of trusting one rect`() {
        // The same reflow that moves a ratio moves a heading's rect. Re-running
        // the lookup means the landing follows the heading down the page.
        val js = buildPageGroupInlineScrollJs(
            historyYCssPx = null,
            fragment = "#sec-12",
            scrollRatio = null,
        )
        assertTrue(js.contains("getBoundingClientRect"))
        assertTrue("rect read is not inside the re-applied body", js.contains("h !== lastH"))
    }

    @Test
    fun `a history position survives an intermediate height that cannot hold it`() {
        // Applied against a half-built document, a deep Y clamps to that
        // document's max scroll and the frame is lost. Re-applying restores it
        // once the page is tall enough again.
        val js = buildPageGroupInlineScrollJs(
            historyYCssPx = 8400,
            fragment = null,
            scrollRatio = null,
        )
        assertTrue(js.contains("window.scrollTo(0, 8400)"))
        assertTrue(js.contains("h !== lastH"))
    }

    @Test
    fun `history wins over a fragment, and a fragment over the saved ratio`() {
        val all = buildPageGroupInlineScrollJs(
            historyYCssPx = 8400,
            fragment = "#sec-12",
            scrollRatio = 0.62,
        )
        assertTrue(all.contains("8400"))
        assertTrue(!all.contains("0.62"))

        val fragmentAndRatio = buildPageGroupInlineScrollJs(
            historyYCssPx = null,
            fragment = "#sec-12",
            scrollRatio = 0.62,
        )
        assertTrue(fragmentAndRatio.contains("sec-12"))
        assertTrue(!fragmentAndRatio.contains("0.62"))
    }

    @Test
    fun `nothing is spliced when there is no target at all`() {
        assertEquals(
            "",
            buildPageGroupInlineScrollJs(historyYCssPx = null, fragment = null, scrollRatio = null),
        )
        assertEquals(
            "",
            buildPageGroupInlineScrollJs(historyYCssPx = 0, fragment = "", scrollRatio = 0.0),
        )
    }
}
