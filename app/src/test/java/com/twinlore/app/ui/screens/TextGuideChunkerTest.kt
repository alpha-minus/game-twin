package com.twinlore.app.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextGuideChunkerTest {

    /** A large guide: many sections, each with body lines. */
    private fun bigGuide(sectionCount: Int, bodyLinesPerSection: Int): String = buildString {
        for (s in 0 until sectionCount) {
            append("==============================\n")
            append("Section $s - The Walkthrough Part\n")
            append("==============================\n")
            repeat(bodyLinesPerSection) { append("Line $it of section $s: walk north, fight the boss, grab the item.\n") }
        }
    }

    @Test
    fun `plans multiple memory-safe chunks for a huge guide`() {
        val text = bigGuide(sectionCount = 140, bodyLinesPerSection = 150)
        assertTrue("test guide should be large (was ${text.length})", text.length > 800_000)

        val lines = text.split("\n")
        val plans = TextGuideChunker.planChunks(lines)
        assertTrue("should produce several chunks, got ${plans.size}", plans.size >= 3)

        // Each rendered chunk stays under the render cap that would crash the WebView.
        for (plan in plans) {
            val html = TextGuideChunker.renderChunkHtml(lines, plan)
            assertTrue(
                "chunk ${plan.index} html ${html.length} exceeds render cap",
                html.length <= MAX_TEXT_GUIDE_RENDER_CHARS,
            )
        }
    }

    @Test
    fun `every entry fragment exists as an anchor in its chunk html`() {
        // Large sections so each chunk holds well under MAX_SECTIONS_PER_CHUNK and
        // keeps its per-section fragments (rather than collapsing to a page entry).
        val text = bigGuide(sectionCount = 60, bodyLinesPerSection = 320)
        val lines = text.split("\n")
        val plans = TextGuideChunker.planChunks(lines)

        // Every entry has either a null fragment (page top) or one that resolves
        // to an actual anchor in that chunk's rendered HTML — no dangling links.
        var checked = 0
        for (plan in plans) {
            val html = TextGuideChunker.renderChunkHtml(lines, plan)
            for (sec in plan.sections) {
                val frag = sec.fragment ?: continue
                checked++
                assertTrue(
                    "fragment $frag missing from chunk ${plan.index} html",
                    html.contains("id=\"$frag\""),
                )
            }
        }
        assertTrue("expected at least one anchored entry to verify", checked >= 1)
    }

    @Test
    fun `wrapMht round-trips back to the original html via MhtParser`() {
        val html = "<html><head></head><body><pre>Hello\nWorld</pre></body></html>"
        val mht = TextGuideChunker.wrapMht(html, 0)

        val tmp = java.io.File.createTempFile("chunk", ".mht")
        val cache = java.io.File.createTempFile("cache", "dir").let { it.delete(); it.mkdirs(); it }
        try {
            tmp.writeText(mht)
            val parsed = com.twinlore.app.data.MhtParser().parse(tmp, cache)
            requireNotNull(parsed) { "MhtParser returned null for synthesized chunk MHT" }
            assertTrue("parsed html should contain the body", parsed.html.contains("Hello"))
            assertTrue(parsed.html.contains("World"))
            assertEquals("https://localhost.invalid/gtchunk-0.html", parsed.baseUrl)
        } finally {
            tmp.delete(); cache.deleteRecursively()
        }
    }

    @Test
    fun `toc guides anchor chapters in later chunks via guide-wide entries`() {
        // A guide with a contents block whose chapter headings are spread
        // across multiple chunks. Later chunks contain no TOC of their own —
        // only the guide-wide entry pass-through can label their sections.
        val chapters = (1..12).map { "Chapter $it The Journey Part $it" }
        val text = buildString {
            append("My Epic Guide\n\n")
            append("Table of Contents\n\n")
            for ((i, c) in chapters.withIndex()) append("${i + 1}. $c\n")
            append("\n\n")
            for ((i, c) in chapters.withIndex()) {
                append("${i + 1}. $c\n\n")
                repeat(1200) { append("Prose line $it of chapter ${i + 1}: fight, loot, level up, repeat forever.\n") }
                append("\n")
            }
        }
        val lines = text.split("\n")
        val plans = TextGuideChunker.planChunks(lines)
        assertTrue("should produce several chunks, got ${plans.size}", plans.size >= 2)

        val allTitles = plans.flatMap { p -> p.sections.map { it.title } }
        // Every chapter from the contents block is a group entry in some chunk,
        // and nothing else (no per-chunk "Page N" fallbacks, no noise).
        for ((i, c) in chapters.withIndex()) {
            assertTrue("missing chapter ${i + 1} in $allTitles", allTitles.any { it.contains(c) })
        }
        assertEquals(chapters.size, allTitles.size)
        // Later chunks carry TOC-derived sections too, not just chunk 0.
        assertTrue(plans.last().sections.none { it.title.startsWith("Page ") })
    }

    @Test
    fun `small guide is left as a single chunk`() {
        val text = bigGuide(sectionCount = 2, bodyLinesPerSection = 5)
        assertTrue(text.length < TextGuideChunker.DEFAULT_TARGET_CHARS)
        val plans = TextGuideChunker.planChunks(text.split("\n"))
        assertEquals(1, plans.size)
    }

    @Test
    fun `guides the size of the chrono trigger faq split into a page set`() {
        // Measured on device: the Chrono Trigger GameFAQs guide extracts to
        // 1,354,326 chars, which sat at 90% of the old 1.5M threshold and so
        // rendered as one sluggish 1.46MB document instead of paging.
        val text = ("x".repeat(79) + "\n").repeat(1_354_326 / 80)
        assertTrue("a 1.35M-char guide must split", TextGuideChunker.shouldSplit(text))
        assertEquals("the render cap is a separate concern", 1_500_000, MAX_TEXT_GUIDE_RENDER_CHARS)
    }

    @Test
    fun `mid-size guides still render as one page`() {
        val text = ("x".repeat(79) + "\n").repeat(600_000 / 80)
        assertTrue("a 600K guide is not worth splitting", !TextGuideChunker.shouldSplit(text))
    }
}
