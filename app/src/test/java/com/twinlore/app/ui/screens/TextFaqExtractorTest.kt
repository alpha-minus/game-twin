package com.twinlore.app.ui.screens

import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Regression tests for [extractTextFaqFromFile].
 *
 * Large GameFAQs text FAQs are split server-side into multiple sibling
 * `<pre id="faqspan-N">` blocks inside one `#faqtext` container, and the block
 * boundaries (`</pre><pre id="faqspan-N">`) land on a single line — sometimes
 * sharing that line with the trailing text of one block and the leading text of
 * the next. The extractor must capture every block, not just the first
 * transition on each line.
 */
class TextFaqExtractorTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun write(name: String, html: String): File =
        tmp.newFile(name).apply { writeText(html) }

    @Test
    fun `extracts all faqspan blocks when boundaries share a line`() {
        // Mirrors the real GameFAQs FFV structure: container + pre open on the
        // first line; boundaries `</pre><pre id="faqspan-N">` at line-start
        // (block 2) and mid-line with surrounding text (block 3); final
        // `</pre></div>` close.
        val html = buildString {
            append("<html><body>\n")
            append("\t<div class=\"faqtext\" id=\"faqtext\"><pre id=\"faqspan-1\">BLOCK_ONE_ALPHA\n")
            append("block one second line\n")
            append("</pre><pre id=\"faqspan-2\">BLOCK_TWO_BRAVO\n")
            append("block two second line\n")
            append("block two trailing text</pre><pre id=\"faqspan-3\">BLOCK_THREE_CHARLIE\n")
            append("block three second line\n")
            append("</pre><pre id=\"faqspan-4\">BLOCK_FOUR_DELTA\n")
            append("block four second line\n")
            append("******</pre></div>\n")
            append("</body></html>\n")
        }
        val file = write("ffv.html", html)

        val out = extractTextFaqFromFile(file)
        requireNotNull(out) { "extractor returned null" }

        for (marker in listOf(
            "BLOCK_ONE_ALPHA",
            "BLOCK_TWO_BRAVO",
            "BLOCK_THREE_CHARLIE",
            "BLOCK_FOUR_DELTA",
        )) {
            assertTrue("missing $marker — block was dropped:\n$out", out.contains(marker))
        }
        // Interior continuation lines of every block must survive too.
        assertTrue(out.contains("block two second line"))
        assertTrue(out.contains("block two trailing text"))
        assertTrue(out.contains("block three second line"))
    }

    @Test
    fun `preserves reading order across blocks`() {
        val html = "<div id=\"faqtext\"><pre id=\"faqspan-1\">AAA</pre>" +
            "<pre id=\"faqspan-2\">BBB</pre><pre id=\"faqspan-3\">CCC</pre></div>"
        val out = extractTextFaqFromFile(write("order.html", html))!!
        assertTrue(out.indexOf("AAA") < out.indexOf("BBB"))
        assertTrue(out.indexOf("BBB") < out.indexOf("CCC"))
    }

    @Test
    fun `single pre block still extracts cleanly`() {
        val html = "<div id=\"faqtext\"><pre id=\"faqspan-1\">SOLO_BLOCK_TEXT\nsecond line</pre></div>"
        val out = extractTextFaqFromFile(write("solo.html", html))!!
        assertTrue(out.contains("SOLO_BLOCK_TEXT"))
        assertTrue(out.contains("second line"))
    }

    @Test
    fun `preserves blank lines inside pre blocks`() {
        // Blank lines are the paragraph/heading separators of a text FAQ. GH #25:
        // they were silently dropped, so the downstream prose-join glued headings
        // into the preceding paragraph ("…the latest. c. Finding out special…").
        val html = "<div id=\"faqtext\"><pre id=\"faqspan-1\">" +
            "will wither on 18th August the \nlatest.\n\n" +
            "c. Finding out special events on that day\n" +
            "-\tYou can talk to Hikari.</pre></div>"
        val out = extractTextFaqFromFile(write("blank.html", html))!!
        assertTrue(
            "blank line between paragraph and heading was dropped:\n$out",
            out.contains("latest.\n\nc. Finding out"),
        )
    }
}
