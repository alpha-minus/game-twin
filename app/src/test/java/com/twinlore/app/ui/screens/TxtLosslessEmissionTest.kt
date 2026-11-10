package com.twinlore.app.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The converted HTML must carry the guide's text losslessly: smart formatting is
 * a CSS mode over this markup, not a conversion-time branch, because page-group
 * chunk MHTs are written to disk and lose their source text (TextGuideSplitter).
 */
class TxtLosslessEmissionTest {

    /** Text content of every run / scroll-block in document order, joined by newline. */
    private fun reconstruct(html: String): String =
        Regex(
            """<div class="run"[^>]*>(.*?)</div>|<div class="scroll-block"[^>]*><pre>(.*?)</pre></div>""",
            RegexOption.DOT_MATCHES_ALL,
        )
            .findAll(html)
            .map { it.groupValues[1] + it.groupValues[2] }
            .map { it.replace(Regex("<[^>]+>"), "") }
            .map { unescape(it) }
            .joinToString("\n")

    private fun unescape(s: String) = s
        .replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&#39;", "'")
        .replace("&amp;", "&")

    @Test
    fun `hard-wrapped prose survives conversion unchanged`() {
        val txt = "While the pleasure of this game is to explore, I hope this guide would \n" +
            "help players who would like to experience all the events of the game.\n"
        assertEquals(txt.trimEnd('\n'), reconstruct(convertTxtToGameFaqHtml(txt)).trimEnd('\n'))
    }

    @Test
    fun `ascii table and indented prose survive conversion unchanged`() {
        val txt = "Shop items:\n" +
            "\n" +
            "+--------+--------+\n" +
            "| Sword  |   500G |\n" +
            "+--------+--------+\n" +
            "\n" +
            "    This indented paragraph is long enough to be treated as wrapped text and \n" +
            "    continues onto a second line here.\n"
        assertEquals(txt.trimEnd('\n'), reconstruct(convertTxtToGameFaqHtml(txt)).trimEnd('\n'))
    }

    @Test
    fun `run carries its indent relative to the segment common indent`() {
        val txt = "    This indented paragraph is long enough to be treated as wrapped text and \n" +
            "    continues onto a second line here.\n" +
            "        A deeper line.\n"
        val html = convertTxtToGameFaqHtml(txt)
        assertTrue(html, html.contains("""<div class="run" style="--ind:0">"""))
        assertTrue(html, html.contains("""<div class="run" style="--ind:4">"""))
    }

    @Test
    fun `body carries the text-faq marker`() {
        val html = convertTxtToGameFaqHtml("Short\n" + "x".repeat(80) + "\n")
        // Distinct from data-gamefaqs-converted, which EpubParser also sets on
        // reflowed EPUB HTML that has no runs or scroll-blocks to act on.
        assertTrue(html.contains("""data-gt-textfaq="1""""))
    }

    @Test
    fun `raw mode never rescales, leaving text size to the reader`() {
        // Auto-fitting the document to its widest line silently ate the text-size
        // slider: the fit was measured after WebView textZoom had been applied,
        // so it cancelled the zoom out and every slider position rendered the
        // same. Raw mode wraps instead, and the slider is the only sizing input.
        val html = convertTxtToGameFaqHtml("Some guide text.\n")
        val script = html.substringAfter("<script>").substringBefore("</script>")
        assertTrue("raw mode must not set a font size", !script.contains("fontSize"))
        assertTrue("raw mode must not measure a width probe", !html.contains("gt-probe"))
        assertTrue(
            "overflow in raw mode wraps rather than clipping or scrolling",
            html.contains("body.gt-raw .run{white-space:pre-wrap"),
        )
        assertTrue(
            "ASCII blocks wrap in raw mode too, so nothing is cut off",
            html.contains("body.gt-raw .scroll-block pre{white-space:pre-wrap}"),
        )
    }
}
