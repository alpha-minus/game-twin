package com.twinlore.app.ui.screens

/**
 * Table-of-contents detection for plain-text game guides (GameFAQs/Neoseeker
 * style). Most text FAQs open with an explicit contents block ("Table of
 * Contents", "CONTENTS", "OUTLINE", …) listing every chapter once; the same
 * titles then reappear as body headings. Detecting that block and matching its
 * entries against the body gives a far more precise chapter list than pattern
 * heuristics alone: TOC lines stop being detected as duplicate chapters, and
 * ASCII art / sub-noise that isn't in the contents list is filtered out.
 *
 * Entry styles handled (all seen in real guides):
 *  - `1. Introduction` / `2-1. THE RUNNING GAME` / `3.02. Missions 11-20`
 *  - `A. Story` / `IV. Walkthrough` / `Section I:  Introduction`
 *  - `- 1.1 Notes` (bullet + hierarchical number)
 *  - `Controls..........0001` (dot leader + page number)
 *  - `Introduction . . . . . . [IN00]` (spaced dot leader + search code)
 *  - `- Chapter 1: Ghost Town....[WD01]` (bullet + leader + code)
 *  - plain short lines used as group headers inside the contents list
 */
internal data class GuideTocEntry(
    /** Human-readable title from the TOC line (list markers stripped, numbering kept). */
    val title: String,
    /** Matching key: title minus ornaments/numbering/codes, lowercased, alphanumeric only. */
    val key: String,
    /** Bracketed search code (`WD01`, `4.1`, …) if the entry carries one, else null. */
    val code: String?,
    /** Line index of the entry within the lines it was parsed from. */
    val lineIndex: Int,
    /** Hierarchy level within the contents list: 1 = main chapter, 2+ = sub. */
    val depth: Int = 1,
)

internal data class GuideTocBlock(
    val headerLine: Int,
    /** First line after the TOC block; body heading matching starts here. */
    val endLine: Int,
    val entries: List<GuideTocEntry>,
)

/** Exact (alnum-only) header keys. Kept short & unambiguous. */
private val TOC_HEADER_KEYS = setOf(
    "tableofcontents", "tableofcontent", "contents", "outline", "index", "toc",
)

/** Lowercase alphanumeric-only form used for all TOC/body title comparisons —
 *  immune to apostrophe variants, spacing, case and punctuation differences. */
internal fun tocMatchKey(text: String): String =
    text.lowercase().filter { it.isLetterOrDigit() }

/** True when a chapter title carries its own enumeration ("1. Intro",
 *  "II. Weapons & Items", "A. Story", "3.02. Missions") — contents lists
 *  should not prepend another running number to such titles. */
internal fun titleHasOwnEnumeration(title: String): Boolean {
    val t = title.trimStart('-', '*', '•', '>', '~', ' ')
    return LEADING_ENUM_REGEX.find(t)?.range?.first == 0 &&
        // require explicit punctuation after the enumerator ("1." / "A)" / "IV:"),
        // not just a leading word+space ("Mission 01" enumerates nothing)
        Regex("^[^ ]{0,12}[.):]").containsMatchIn(t)
}

/** True when [key] extends [base] with a real subtitle ("sequence1peaceatlast"
 *  extends "sequence1") — a digit continuation ("thejourney10" vs
 *  "thejourney1") is a DIFFERENT numbered title, not an extension. */
private fun keyExtends(key: String, base: String): Boolean =
    base.length >= 5 && key.length > base.length &&
        key.startsWith(base) && key[base.length].isLetter()

internal fun isTocHeaderLine(line: String): Boolean {
    val t = line.trim()
    if (t.isEmpty() || t.length > 120) return false
    val key = tocMatchKey(t)
    if (key in TOC_HEADER_KEYS) return true
    // Ornamented/banner forms: "O==< Table of Contents >==O",
    // "- T A B L E  O F  C O N T E N T S -". Keep the line short so prose
    // mentioning a table of contents doesn't qualify.
    return t.length <= 80 && key.contains("tableofcontents")
}

// Leading enumeration on a heading: "1.", "2-1.", "3.02.", "IV.", "A.",
// "Section I:", and bare "12 " (number + space before a letter). Bounded to
// 3-digit components so titles like "1602 A.D." survive.
private val LEADING_ENUM_REGEX = Regex(
    "^(?:(?:section|chapter|part|appendix)\\s+)?" +
        "(?:\\d{1,3}(?:[-.)\\]]\\d{1,3}){0,3}|[IVXLivxl]{1,7}|[A-Za-z])" +
        "(?:\\s*[.):\\]\\-]+\\s*|\\s+(?=[A-Za-z]))",
    RegexOption.IGNORE_CASE,
)

private val TRAILING_CODE_REGEX = Regex("[\\[{][A-Za-z0-9][A-Za-z0-9 ._-]{0,15}[\\]}]\\s*$")

// Parenthesized enumerators: "(I)", "(12)", "(A)" at either end of a heading.
private val PAREN_ENUM_LEADING_REGEX = Regex("^\\((?:\\d{1,3}|[IVXLivxl]{1,7}|[A-Za-z])\\)\\s*")
private val PAREN_ENUM_TRAILING_REGEX = Regex("\\s*\\((?:\\d{1,3}|[IVXLivxl]{1,7}|[A-Za-z])\\)\\s*$")

// Dot-leader entry: "Title.......15", "Title . . . . [BS01]", and wide-gap
// tab-aligned leaders "Title    .       .       .   [INT]" — 3+ leader dots,
// optional trailing page number / code token.
private val TOC_DOT_LEADER_LINE = Regex("^(.{2,70}?)\\s*(?:[.·][ \\t]{0,7}){3,}\\s*(\\S{1,18})?$")

// Entry ending in a bracketed code with no dot leader: "Boss List [BOSS]".
private val TOC_TRAILING_CODE_LINE = Regex("^(.{2,70}?)\\s*[\\[{]([A-Za-z0-9][A-Za-z0-9 ._-]{1,15})[\\]}]$")

// Entry starting with a bracketed code: "{1} The Reactor #1 raid", "[WLK] Boss list".
private val TOC_LEADING_CODE_LINE = Regex("^[\\[{]([A-Za-z0-9][A-Za-z0-9._-]{0,15})[\\]}]\\s+(.{2,70})$")

private val BRACKETED_TOKEN_REGEX = Regex("^\\[([A-Za-z0-9][A-Za-z0-9._-]{0,15})\\]$")

// Unbracketed search code after a dot leader: "CT.TS", "01.02", "WD-01" —
// requires an internal separator so plain page numbers ("15", "0001") and
// words don't qualify.
private val UNBRACKETED_CODE_REGEX = Regex("^[A-Za-z0-9]{1,8}(?:[._-][A-Za-z0-9]{1,8}){1,2}$")

/** Strip one layer of box-drawing frame ("|  - Deku Tree....01.02  |") so
 *  framed TOC lines parse and their INNER indentation drives depth. */
internal fun stripBoxFrame(line: String): String {
    val t = line.trimEnd()
    val trimmed = t.trim()
    if (trimmed.length > 2 && trimmed.first() == '|' && trimmed.last() == '|') {
        val inner = trimmed.substring(1, trimmed.length - 1)
        // keep inner indentation: re-derive from the original line past the frame
        val start = t.indexOf('|')
        return if (start >= 0) t.substring(start + 1).trimEnd().removeSuffix("|") else inner
    }
    return line
}

/** A long ornament run ("------", "======") marks section banner lines. */
private val ORNAMENT_RUN_REGEX = Regex("[-=_~+]{5,}")

/** Whether [raw] contains [code] as a standalone token (non-alphanumeric,
 *  non-dot neighbours) — for unbracketed codes like "01.02" or "CT.TS". */
private fun lineHasBareCode(raw: String, code: String): Boolean {
    // Dot neighbours only disqualify DOTTED codes ("01.02" inside "v01.02.03");
    // for letter codes a dot is a legit ornament (".vb101." banner).
    val guardDots = code.contains('.')
    var idx = raw.indexOf(code)
    while (idx >= 0) {
        val before = if (idx == 0) ' ' else raw[idx - 1]
        val afterIdx = idx + code.length
        val after = if (afterIdx >= raw.length) ' ' else raw[afterIdx]
        if (!before.isLetterOrDigit() && !after.isLetterOrDigit() &&
            (!guardDots || (before != '.' && after != '.'))
        ) return true
        idx = raw.indexOf(code, idx + 1)
    }
    return false
}

/**
 * Normalize a TOC entry or body heading down to its matching key: strip
 * ornaments, leading list markers + enumeration (repeatedly, for forms like
 * "1. - Controls"), and a trailing bracketed code, then reduce to lowercase
 * alphanumerics. Both sides of a comparison go through this, so numbering
 * style differences between TOC and body don't matter.
 */
internal fun guideHeadingKey(raw: String): String {
    var s = stripOrnaments(raw.trim())
    s = TRAILING_CODE_REGEX.replace(s, "").trim()
    // Parenthesized enumerators — "(I) Introduction" / "Introduction (I)".
    s = PAREN_ENUM_LEADING_REGEX.replaceFirst(s, "")
    s = PAREN_ENUM_TRAILING_REGEX.replaceFirst(s, "")
    var iterations = 0
    while (iterations < 3) {
        val before = s
        s = s.trimStart('-', '*', '•', '>', '~', '+', ' ')
        s = LEADING_ENUM_REGEX.replaceFirst(s, "")
        if (s == before) break
        iterations++
    }
    val key = tocMatchKey(s)
    // Everything was numbering/ornament (e.g. a bare "[IN00]" line) — fall back
    // to the raw form so the entry still gets a usable key.
    return key.ifEmpty { tocMatchKey(raw) }
}

/** Parse one trimmed TOC line into (title, code) or null if it can't be an entry. */
private fun parseTocEntryLine(t: String): Pair<String, String?>? {
    // Leader/code patterns run before any length cap: wide-gap dot leaders
    // legitimately exceed it, and the patterns bound the title themselves.
    TOC_DOT_LEADER_LINE.matchEntire(t)?.let { m ->
        val title = m.groupValues[1].trim()
        val token = m.groupValues[2]
        val code = BRACKETED_TOKEN_REGEX.find(token)?.groupValues?.get(1)
            ?: token.takeIf { UNBRACKETED_CODE_REGEX.matches(it) }
        return title to code
    }
    TOC_TRAILING_CODE_LINE.matchEntire(t)?.let { m ->
        return m.groupValues[1].trim() to m.groupValues[2].trim()
    }
    TOC_LEADING_CODE_LINE.matchEntire(t)?.let { m ->
        return m.groupValues[2].trim() to m.groupValues[1].trim()
    }
    // Long line without leader/code: prose, not an entry — unless it's an
    // enumerated entry that happens to be long ("- 2.4 \"Anno 1602\" or …").
    if (t.length > 65 && !(t.length <= 90 && ENUMERATED_LINE_REGEX.matches(t))) return null
    // A plain line ending in sentence punctuation is prose ("…press ENTER
    // twice to be where you wanted to be. Just like magic!"), not a chapter.
    if (!ENUMERATED_LINE_REGEX.matches(t) && t.last() in ".!?:;,") return null
    return t to null
}

/** Strip list markers and trailing leader-dot residue from an entry title for
 *  display ("- Mission 01" → "Mission 01", "Intro   .   ." → "Intro"). */
private fun cleanTocTitle(title: String): String =
    title.trimStart('-', '*', '•', '>', '~', ' ')
        .trimEnd('.', '·', ' ', '\t')
        .trim()
        .ifBlank { title.trim() }

private const val TOC_PARSE_WINDOW = 500
private const val MIN_TOC_ENTRIES = 4

// ── Hierarchy depth ──

private val LEADING_DASHES_REGEX = Regex("^(-{1,3})\\s")
private val LEADING_NUM_COMPONENTS_REGEX = Regex("^(\\d{1,3}(?:[-.)\\]]\\d{1,3}){0,3})[.):\\]\\s]")

/**
 * Raw hierarchy score of a TOC line: indentation plus strong markers (leading
 * dashes "- "/"-- ", hierarchical numbering "2-1"/"3.02"). Higher = deeper.
 * Scores are only meaningful relative to the other entries of the same TOC —
 * [assignDepths] clusters them into 1-based levels.
 */
private fun entryDepthScore(raw: String): Int {
    var indent = 0
    for (c in raw) {
        if (c == ' ') indent++ else if (c == '\t') indent += 4 else break
    }
    val afterIndent = raw.trimStart()
    val dashes = LEADING_DASHES_REGEX.find(afterIndent)?.groupValues?.get(1)?.length ?: 0
    val rest = afterIndent.drop(dashes).trimStart()
    val numComponents = LEADING_NUM_COMPONENTS_REGEX.find(rest)?.groupValues?.get(1)
        ?.count { it == '.' || it == '-' || it == ')' }?.plus(1) ?: 1
    return indent + dashes * 4 + (numComponents - 1) * 4
}

/**
 * Cluster entry scores into 1-based depth levels. Scores whose sorted gaps are
 * ≤2 merge into one level — that absorbs right-aligned enumerators ("  II." vs
 * "VIII.") which jitter the indent by a couple of columns.
 */
private fun assignDepths(entries: List<GuideTocEntry>, scores: List<Int>): List<GuideTocEntry> {
    if (entries.isEmpty()) return entries
    val sorted = scores.distinct().sorted()
    val levelOfScore = mutableMapOf<Int, Int>()
    var level = 1
    var prev = sorted.first()
    for (s in sorted) {
        if (s - prev > 2) level++
        levelOfScore[s] = level
        prev = s
    }
    return entries.mapIndexed { i, e -> e.copy(depth = levelOfScore[scores[i]] ?: 1) }
}

/**
 * Keep only main chapters when the contents list is hierarchical: choose the
 * shallowest depth cutoff that still yields a usable chapter count for the
 * guide's size (at least 6, scaling up for very long guides so a 30K-line
 * guide isn't reduced to a handful of giant chapters). Falls back to all
 * matched entries when even full depth is below the target.
 */
internal fun selectTocDepth(matches: List<GuideTocMatch>, totalLines: Int): List<GuideTocMatch> {
    if (matches.isEmpty()) return matches
    val maxDepth = matches.maxOf { it.entry.depth }
    if (maxDepth <= 1) return matches
    val target = maxOf(6, totalLines / 700)
    for (d in 1 until maxDepth) {
        val sel = matches.filter { it.entry.depth <= d }
        if (sel.size >= target) return sel
    }
    return matches
}

// A structured entry: enumerated ("1.", "A.", "IV.", "Section I:", and
// hierarchical numbers without punctuation like "1.1 Notes") — used to tell
// real entries from body prose once a separator ends the visual block.
private val ENUMERATED_LINE_REGEX = Regex(
    "^(?:[-*•>~]\\s*)?(?:" +
        "(?:(?:section|chapter|part|appendix)\\s+)?" +
        "(?:\\d{1,3}(?:[-.)\\]]\\d{1,3}){0,3}|[IVXLivxl]{1,7}|[A-Za-z])\\s*[.):\\]\\-]+\\s+\\S.*" +
        "|\\d{1,3}(?:[-.)\\]]\\d{1,3}){1,3}\\s+\\S.*" +
        "|\\((?:\\d{1,3}|[IVXLivxl]{1,7}|[A-Za-z])\\)\\s+\\S.*" +
        ")$",
    RegexOption.IGNORE_CASE,
)

/**
 * Parse the entry list following a TOC header. Blank lines and separator /
 * frame lines inside the block are skipped; two consecutive prose-like lines
 * end it (guides often follow the contents with a "press Ctrl+F" note).
 *
 * Body-start detection (all of these end the block):
 *  - a line whose key repeats or extends the FIRST entry's key — that's the
 *    body's first section heading, not a TOC line;
 *  - a line containing the first entry's bracketed search code;
 *  - once a separator fence follows the entries, a line that is neither
 *    enumerated nor dot-leader/code shaped — body headings after a fence
 *    would otherwise be swallowed as fake "entries" forever.
 *
 * The block always ends right after its last entry line, so body matching can
 * begin at (and include) the very line that stopped the parse.
 */
private fun parseTocEntries(lines: List<String>, headerLine: Int): GuideTocBlock? {
    val entries = mutableListOf<GuideTocEntry>()
    val entryScores = mutableListOf<Int>()
    var nonEntryRun = 0
    var blankRun = 0
    var lastEntryLine = headerLine
    var separatorSeen = false
    val windowEnd = minOf(lines.size, headerLine + 1 + TOC_PARSE_WINDOW)
    var i = headerLine + 1
    while (i < windowEnd) {
        val rawLine = lines[i]
        // Box-framed TOCs ("|  - Deku Tree....01.02  |"): parse the inner text.
        val inner = stripBoxFrame(rawLine)
        val t = inner.trim()
        if (t.isEmpty()) {
            blankRun++
            if (blankRun >= 6 && entries.isNotEmpty()) break
            i++
            continue
        }
        blankRun = 0
        if (isSeparatorLine(rawLine.trim()) || isFrameBorder(rawLine.trim()) ||
            isSeparatorLine(t) || isFrameBorder(t)
        ) {
            if (entries.isNotEmpty()) separatorSeen = true
            i++
            continue
        }
        // Decorative spaced-letter banner ("P R E S E N T   ( 1 0 0 0 A D )")
        // — an era/group divider inside the contents, not an entry. Dots are
        // excluded from the count: ". . . ." is a spaced dot LEADER, not art.
        val tokens = t.split(' ', '\t').filter { it.isNotBlank() }
        val spacedSingles = tokens.count { it.length == 1 && it[0] != '.' && it[0] != '·' }
        if (spacedSingles >= 6 && spacedSingles * 10 >= tokens.size * 7) {
            i++
            continue
        }
        // Body start: the first entry's search code re-appears as an anchor.
        val firstCode = entries.firstOrNull()?.code
        if (firstCode != null &&
            (t.contains("[$firstCode]") || t.contains("{$firstCode}") ||
                (ORNAMENT_RUN_REGEX.containsMatchIn(t) && lineHasBareCode(t, firstCode)))
        ) break

        val parsed = parseTocEntryLine(t)
        if (parsed != null) {
            val (title, code) = parsed
            val key = guideHeadingKey(title)
            if (key.length >= 3 && title.count { it.isLetter() } >= 3) {
                val structured = code != null ||
                    TOC_DOT_LEADER_LINE.matches(t) ||
                    ENUMERATED_LINE_REGEX.matches(t)
                // Body start: first section heading repeats (or extends) entry 0.
                // The extends case only applies to unstructured lines — a
                // structured line whose title extends entry 0 is a sibling TOC
                // entry ("1) Introduction" … "5) Introduction to Alucard").
                val firstKey = entries.firstOrNull()?.key
                if (entries.size >= 3 && firstKey != null &&
                    (key == firstKey || (!structured && keyExtends(key, firstKey)))
                ) break
                // Body start: a PLAIN underlined heading ("Abbreviations" over
                // "====="). Plain contents entries are never underlined, and
                // eating the body's first heading as an entry breaks matching
                // when its title also occurs deeper in the guide. Structured
                // lines don't stop here: TOC group headers may be underlined
                // too ("II) Characters {C}" over "=====").
                if (!structured && entries.size >= 3 && i + 1 < lines.size &&
                    isSeparatorLine(lines[i + 1].trim())
                ) break
                // Past a fence, only structured lines still count as entries;
                // unstructured ones (body headings, wrapped-entry tails) are
                // skipped and two in a row end the block.
                if (!separatorSeen || structured) {
                    entries.add(GuideTocEntry(cleanTocTitle(title), key, code, i))
                    entryScores.add(entryDepthScore(inner))
                    lastEntryLine = i
                    nonEntryRun = 0
                    i++
                    continue
                }
            }
        }
        nonEntryRun++
        if (nonEntryRun >= 2 && entries.isNotEmpty()) break
        i++
    }
    if (entries.size < MIN_TOC_ENTRIES) return null
    return GuideTocBlock(headerLine, lastEntryLine + 1, assignDepths(entries, entryScores))
}

/**
 * Find the guide's contents block: scan the head of the guide for a TOC header
 * line, then parse the entry list that follows. Returns null when no header
 * with at least [MIN_TOC_ENTRIES] parseable entries exists.
 */
internal fun detectGuideTocBlock(lines: List<String>): GuideTocBlock? {
    val searchLimit = minOf(lines.size, maxOf(150, lines.size / 4))
    var headersTried = 0
    for (i in 0 until searchLimit) {
        if (!isTocHeaderLine(lines[i])) continue
        val block = parseTocEntries(lines, i)
        if (block != null) return block
        headersTried++
        if (headersTried >= 5) break
    }
    return null
}

/** One TOC entry resolved to its body heading line. */
internal data class GuideTocMatch(
    val entry: GuideTocEntry,
    val line: Int,
    val title: String,
)

/**
 * Locate each TOC entry's body heading. Matching is monotonic (entries appear
 * in body order; unmatched entries are skipped without resetting the scan
 * position). A body line matches an entry when:
 *  - it contains the entry's bracketed search code (`[WD01]`) AND either also
 *    carries the entry title or is a bare anchor line (guides drop codes like
 *    `{2}` into data tables too — a code alone in running text is not a
 *    heading), or
 *  - its heading key equals the entry key, or
 *  - its heading key extends the entry key slightly (TOC often abbreviates:
 *    "Sequence 1" vs body "Sequence 1: Peace at Last").
 */
internal fun matchTocEntries(
    lines: List<String>,
    entries: List<GuideTocEntry>,
    scanFrom: Int,
): List<GuideTocMatch> {
    if (entries.isEmpty()) return emptyList()
    val keyCache = arrayOfNulls<String>(lines.size)
    fun keyAt(j: Int): String {
        keyCache[j]?.let { return it }
        val t = lines[j].trim()
        val k = if (t.isEmpty() || t.length > 90) "" else guideHeadingKey(t)
        keyCache[j] = k
        return k
    }

    val matches = mutableListOf<GuideTocMatch>()
    var ptr = scanFrom.coerceAtLeast(0)
    for (entry in entries) {
        val codeToken = entry.code?.let { "[$it]" }
        val codeTokenBrace = entry.code?.let { "{$it}" }
        val codeLetters = entry.code?.count { it.isLetter() } ?: 0
        var found = -1
        var byCode = false
        var j = ptr
        while (j < lines.size) {
            val raw = lines[j]
            if (codeToken != null) {
                val hasBracketed = raw.contains(codeToken) || raw.contains(codeTokenBrace!!)
                // Unbracketed codes ("01.02") only anchor on ornamented banner
                // lines — bare tokens in prose or data tables don't count.
                val hasBare = !hasBracketed && ORNAMENT_RUN_REGEX.containsMatchIn(raw) &&
                    lineHasBareCode(raw, entry.code!!)
                if (hasBracketed || hasBare) {
                    val carriesTitle = entry.key.length >= 3 && keyAt(j).contains(entry.key)
                    val bareAnchor = raw.count { it.isLetter() } - codeLetters <= 3
                    if (hasBare || carriesTitle || bareAnchor) {
                        found = j
                        byCode = true
                        break
                    }
                }
            }
            if (entry.key.length >= 3) {
                val k = keyAt(j)
                if (k == entry.key) {
                    found = j
                    break
                }
                // Body extends TOC ("Sequence 1" → "Sequence 1: Peace at Last")
                // or TOC extends body ("Hidden bonus item locations (both
                // quests)" → "Hidden bonus item locations").
                val extendsEither =
                    (keyExtends(k, entry.key) && k.length <= entry.key.length + 12) ||
                        (keyExtends(entry.key, k) && entry.key.length <= k.length + 12)
                if (extendsEither && raw.trim().length <= 80) {
                    found = j
                    break
                }
            }
            j++
        }
        if (found >= 0) {
            val bodyTitle = stripOrnaments(lines[found].trim())
            // Spaced-letter body banners ("S I T E  1 6") don't fully collapse
            // when digits/apostrophes break the letter runs — the TOC entry
            // title is the clean form.
            val spacedArt = lines[found].split(' ').count { it.length == 1 && it != "." } >= 4
            val title = when {
                byCode || spacedArt -> entry.title
                bodyTitle.count { it.isLetter() } >= 3 && bodyTitle.length <= 80 -> bodyTitle
                else -> entry.title
            }
            matches.add(GuideTocMatch(entry, found, title))
            ptr = found + 1
        }
    }
    return matches
}

internal fun matchTocEntriesToBody(
    lines: List<String>,
    entries: List<GuideTocEntry>,
    scanFrom: Int,
): List<DetectedSection> =
    matchTocEntries(lines, entries, scanFrom).map { DetectedSection(it.title, it.line, it.line + 1) }

/**
 * Guide-wide TOC matches, verified against the body: returns the resolved
 * (entry, body line) list only when at least [MIN_TOC_ENTRIES] entries
 * actually match body headings, so callers (the chunker) never trust a
 * misparsed contents block.
 */
internal fun computeVerifiedTocMatches(lines: List<String>): List<GuideTocMatch>? {
    val block = detectGuideTocBlock(lines) ?: return null
    val matched = matchTocEntries(lines, block.entries, block.endLine)
    if (matched.size < MIN_TOC_ENTRIES) return null
    return selectTocDepth(matched, lines.size)
}

/** Entry list of [computeVerifiedTocMatches], for single-shot callers. */
internal fun detectVerifiedGuideTocEntries(lines: List<String>): List<GuideTocEntry>? =
    computeVerifiedTocMatches(lines)?.map { it.entry }
