package com.twinlore.app.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for [convertTxtToGameFaqHtml]'s smart formatting (GH #25).
 *
 * A line whose only "structure" signal is the tab/wide gap right after a single
 * leading list marker ("-\tSome sentence…", "1.\tLocations") is a prose bullet,
 * not columnar data — classifying it structured split every bullet's first line
 * into its own unwrapped scroll-block, clipped at the screen edge. Column gaps
 * appearing after the marker's text ("1. Sword   2. Shield") still mark the
 * line structured so multi-column lists keep their horizontal layout.
 *
 * The prose join must only merge lines that show hard-wrap evidence (near
 * wrap-width) and must never join into a line that starts its own list item —
 * otherwise headings and TOC entries collapse into flowing paragraphs.
 */
class TxtSmartFormatTest {

    private fun structuredBlocks(html: String): List<String> =
        Regex("""<div class="scroll-block"[^>]*><pre>(.*?)</pre></div>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(html).map { it.groupValues[1] }.toList()

    /**
     * Text of each `<div class="run">`, tags stripped. A run is the group of
     * lines smart formatting reflows into one flowing line; the join is now
     * expressed as run membership rather than a literal space, so the raw text
     * survives for the Smart Formatting toggle to fall back on.
     */
    private fun runs(html: String): List<String> =
        Regex("""<div class="run"[^>]*>(.*?)</div>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(html).map { it.groupValues[1].replace(Regex("<[^>]+>"), "") }.toList()

    @Test
    fun `single leading bullet with tab is prose joined with its continuation`() {
        val txt = "a. Morning exercise\n" +
            "-\tIf you sleep before 11pm at the previous night, you will \n" +
            "automatically take part in the morning exercise at the park today.\n" +
            "\n" +
            "b. Watering the morning glory\n"
        val html = convertTxtToGameFaqHtml(txt)

        val blocks = structuredBlocks(html)
        assertTrue(
            "bullet line must not become a scroll-block: $blocks",
            blocks.none { it.contains("If you sleep") },
        )
        // First bullet line joins its hard-wrapped continuation…
        assertTrue(
            "bullet and its continuation must share one run: ${runs(html)}",
            runs(html).any { it.contains("you will") && it.contains("automatically take part") },
        )
        // …but the heading before it stays on its own line.
        assertTrue(
            "the heading must be its own run: ${runs(html)}",
            runs(html).any { it.trim() == "a. Morning exercise" },
        )
    }

    @Test
    fun `multi-column list line stays structured`() {
        val txt = "Bottle cap list:\n" +
            "\n" +
            "1. Red Cap\t2. Blue Cap\t3. Green Cap\n" +
            "4. Gold Cap\t5. Black Cap\t6. White Cap\n"
        val html = convertTxtToGameFaqHtml(txt)
        val blocks = structuredBlocks(html)
        assertTrue(
            "multi-column list must keep horizontal layout: $blocks",
            blocks.any { it.contains("Red Cap") && it.contains("Blue Cap") },
        )
    }

    @Test
    fun `bullet with value column stays structured`() {
        val txt = "Shop items:\n" +
            "\n" +
            "-\tWooden Sword       500 yen\n" +
            "-\tFishing Rod        800 yen\n"
        val html = convertTxtToGameFaqHtml(txt)
        val blocks = structuredBlocks(html)
        assertTrue(
            "bullet followed by a value column is columnar data: $blocks",
            blocks.any { it.contains("Wooden Sword") },
        )
    }

    @Test
    fun `heading after full-width line is not merged into the paragraph`() {
        val txt = "This paragraph line is quite long and definitely exceeds the wrap width used here.\n" +
            "c. Finding out special events on that day\n" +
            "-\tYou can talk to Hikari and she will tell you where you can see them.\n"
        val html = convertTxtToGameFaqHtml(txt)
        assertFalse(
            "heading must not share a run with the previous paragraph",
            runs(html).any { it.contains("here.") && it.contains("c. Finding out") },
        )
    }

    @Test
    fun `short toc entries are not joined into one line`() {
        val txt = "Table of Contents\n" +
            "1.\tLocations\n" +
            "2. \tDaily activities\n" +
            "3. \tDiary events\n"
        val html = convertTxtToGameFaqHtml(txt)
        assertTrue(
            "TOC entries must each be their own run: ${runs(html)}",
            runs(html).any { it.trim() == "1.\tLocations" },
        )
        assertFalse(runs(html).any { it.contains("Locations") && it.contains("2.") })
        assertFalse(runs(html).any { it.contains("Table of Contents") && it.contains("1.") })
    }

    @Test
    fun `hard-wrapped paragraph lines still join into flowing text`() {
        val txt = "While the pleasure of this game is to explore, I hope this guide would \n" +
            "help players who would like to experience all the events of the game.\n"
        val html = convertTxtToGameFaqHtml(txt)
        assertTrue(
            "hard-wrapped prose must share one run: ${runs(html)}",
            runs(html).any { it.contains("this guide would") && it.contains("help players") },
        )
    }

    @Test
    fun `converted html exposes the format hook and a raw mode`() {
        val html = convertTxtToGameFaqHtml("Some guide text.\n")
        assertTrue("Kotlin toggles formatting through this hook", html.contains("window.gtApplyFormat"))
        assertTrue("raw mode is a body class", html.contains("body.gt-raw"))
    }

    @Test
    fun `fit pass batches reads before writes`() {
        // Interleaving a read after a write forces a synchronous layout of the
        // whole document. On the Chrono Trigger guide (1.46MB, ~1087 blocks)
        // that meant ~2174 layouts per pass. Every measurement must land before
        // every mutation so the read phase resolves against one layout.
        val html = convertTxtToGameFaqHtml("Some guide text.\n")
        val script = html.substringAfter("<script>").substringBefore("</script>")
        // Scope to the fit pass. The tap handler below it legitimately reads
        // after writing, but it touches exactly one block, not a thousand.
        val fit = script.substringBefore("document.addEventListener('click'")
        val lastRead = maxOf(fit.lastIndexOf("scrollWidth"), fit.lastIndexOf("scrollHeight"))
        val firstWrite = fit.indexOf("style.transform='scale(")
        assertTrue("expected both markers in the fit pass", lastRead >= 0 && firstWrite >= 0)
        assertTrue(
            "every measurement must precede every style write, got last read at " +
                "$lastRead and first write at $firstWrite",
            lastRead < firstWrite,
        )
    }

    @Test
    fun `converted html disables webview font boosting`() {
        val html = convertTxtToGameFaqHtml("Some guide text.\n")
        assertTrue(
            "text-size-adjust:100% must be set so TEXT_AUTOSIZING cannot inflate joined lines",
            html.contains("text-size-adjust:100%"),
        )
    }
}
