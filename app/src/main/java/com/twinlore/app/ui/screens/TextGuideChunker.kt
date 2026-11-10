package com.twinlore.app.ui.screens

/**
 * Splits a very large text guide into memory-safe chunks for the page-group
 * viewer. Each chunk is small enough that the WebView only ever builds one
 * chunk's DOM at a time (the whole-document DOM of a multi-MB guide is what
 * lmkd-kills the app — see [MAX_TEXT_GUIDE_RENDER_CHARS]).
 *
 * Two-phase by design so the SPLIT itself stays memory-frugal: [planChunks]
 * computes lightweight line-range plans (cheap), and [renderChunkHtml] builds
 * one chunk's HTML on demand. The caller renders + writes one chunk at a time
 * and lets it be GC'd before the next — it never holds the whole document's
 * converted HTML in memory at once.
 *
 * Chunk boundaries land on detected section starts whenever possible so pages
 * begin at a heading and the table of contents survives: every detected section
 * becomes a group entry pointing at its chunk page and an in-page `sec-N`
 * anchor. A single section larger than [DEFAULT_HARD_CHARS] is force-split
 * mid-section (the continuation chunk just starts with no heading).
 */
data class GuideChunkSection(
    /** Section heading text, used as the group entry label. */
    val title: String,
    /** In-page anchor id (`sec-N`) matching the chunk HTML, or null for page top. */
    val fragment: String?,
)

/** Lightweight plan for one chunk — a line range plus its sections. Holds no
 *  HTML, so a full guide's worth of plans is cheap to keep around. */
data class ChunkPlan(
    val index: Int,
    val startLine: Int,
    val endLine: Int,
    val sections: List<GuideChunkSection>,
)

object TextGuideChunker {
    /** Soft target: cut at the next section boundary once a chunk reaches this. */
    const val DEFAULT_TARGET_CHARS = 350_000

    /** Hard limit: force a cut even mid-section. Kept well under
     *  [MAX_TEXT_GUIDE_RENDER_CHARS] so a chunk never hits the render cap. */
    const val DEFAULT_HARD_CHARS = 500_000

    /** A guide above this size is worth splitting into a group. Deliberately far
     *  below [MAX_TEXT_GUIDE_RENDER_CHARS]: that constant is a CRASH ceiling,
     *  while "worth paging" starts much lower. While the two were aliased, the
     *  Chrono Trigger FAQ (1,354,326 chars) sat at 90% of the cap and rendered
     *  as one 1.46MB document — never crashing, just miserable to scroll. */
    const val SPLIT_THRESHOLD_CHARS = 700_000

    /** Above this many detected sections in a single chunk, detection has
     *  degraded to per-paragraph noise — collapse the chunk to one page entry. */
    const val MAX_SECTIONS_PER_CHUNK = 25

    fun shouldSplit(text: String): Boolean = text.length > SPLIT_THRESHOLD_CHARS

    /** Compute chunk plans (line ranges + per-chunk sections) without building
     *  any HTML. [lines] is the guide text split on '\n'. */
    fun planChunks(
        lines: List<String>,
        targetChars: Int = DEFAULT_TARGET_CHARS,
        hardChars: Int = DEFAULT_HARD_CHARS,
    ): List<ChunkPlan> {
        if (lines.isEmpty()) return emptyList()
        // Cut purely by SIZE, preferring a blank line once past the target. We do
        // NOT run detectTextSections over the whole guide to find boundaries:
        // detection is super-linear (≈0.2s for 800KB but ≈8s for a 1.9MB guide,
        // from regex backtracking on ASCII art) and that freeze is what lets lmkd
        // reap the app mid-split. Cutting by size is a cheap O(n) scan.
        val cutEnds = mutableListOf<Int>()
        var size = 0
        for (i in lines.indices) {
            size += lines[i].length + 1
            val atEnd = i == lines.size - 1
            when {
                atEnd -> cutEnds.add(i + 1)
                size >= targetChars && lines[i].isBlank() -> { cutEnds.add(i + 1); size = 0 }
                size >= hardChars -> { cutEnds.add(i + 1); size = 0 }
            }
        }

        // Detect sections PER CHUNK (each ~one chunk in size → fast, and because
        // detection is super-linear, several small runs cost far less than one
        // whole-guide run). convertTxtToGameFaqHtml re-detects the same chunk and
        // emits sec-0..N in the same order, so these fragments line up exactly.
        //
        // The guide-wide contents block IS detected and matched once over the
        // full guide (cheap: it only scans the head + one linear match pass).
        // Each chunk then only receives the entries whose body heading falls
        // inside it, so chapters whose TOC lives in chunk 0 still anchor in
        // later chunks — and no chunk can re-match another chunk's chapter.
        val tocMatches = computeVerifiedTocMatches(lines)
        val plans = mutableListOf<ChunkPlan>()
        var start = 0
        for ((idx, end) in cutEnds.withIndex()) {
            val chunkLines = lines.subList(start, end)
            val chunkEntries = tocMatches?.let { ms ->
                val s = start
                ms.filter { it.line in s until end }.map { it.entry }
            }
            val detected = detectTextSections(chunkLines, chunkEntries)
            val chunkSections = when {
                detected.isEmpty() ->
                    listOf(GuideChunkSection("Page ${idx + 1}", null))
                // Too many "sections" in one chunk means the detector fell back to
                // treating paragraph breaks as headings — that's noise, not a TOC.
                // Collapse to a single page entry labelled by the first heading.
                // TOC-verified sections are the guide's own contents — never noise.
                tocMatches == null && detected.size > MAX_SECTIONS_PER_CHUNK ->
                    listOf(GuideChunkSection(detected.first().title, null))
                else ->
                    detected.mapIndexed { j, s -> GuideChunkSection(s.title, "sec-$j") }
            }
            plans.add(ChunkPlan(idx, start, end, chunkSections))
            start = end
        }
        return plans
    }

    /** Build one chunk's self-contained HTML on demand. The emitted sec-0..N
     *  anchors line up with [ChunkPlan.sections]' fragments. Re-derives the
     *  guide-wide TOC matches exactly like [planChunks] so both produce the
     *  same section list for a chunk. */
    fun renderChunkHtml(lines: List<String>, plan: ChunkPlan): String {
        val chunkText = lines.subList(plan.startLine, plan.endLine).joinToString("\n")
        val chunkEntries = computeVerifiedTocMatches(lines)?.let { ms ->
            ms.filter { it.line in plan.startLine until plan.endLine }.map { it.entry }
        }
        return convertTxtToGameFaqHtml(chunkText, chunkEntries)
    }

    /** Wrap self-contained HTML as a minimal multipart/related MHT that
     *  [com.twinlore.app.data.MhtParser] can parse back into the same HTML. */
    fun wrapMht(html: String, index: Int): String {
        val boundary = "GTCHUNKBOUNDARY${index}xKQ"
        return buildString {
            append("From: <gametwin-split>\r\n")
            append("MIME-Version: 1.0\r\n")
            append("Content-Type: multipart/related; boundary=\"$boundary\"\r\n")
            append("\r\n")
            append("--").append(boundary).append("\r\n")
            append("Content-Type: text/html\r\n")
            append("Content-Location: https://localhost.invalid/gtchunk-$index.html\r\n")
            append("\r\n")
            append(html)
            append("\r\n--").append(boundary).append("--\r\n")
        }
    }
}
