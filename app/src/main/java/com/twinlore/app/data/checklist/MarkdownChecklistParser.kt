package com.twinlore.app.data.checklist

open class MarkdownChecklistParser {

    data class ParsedChecklist(val tabs: List<ParsedTab>)
    data class ParsedTab(val name: String?, val sections: List<ParsedSection>)
    data class ParsedSection(val name: String, val items: List<ParsedItem>)
    data class ParsedItem(val title: String, val completed: Boolean)

    private val tabPattern = Regex("^#\\s+(.+)$")
    private val sectionPattern = Regex("^#{2,}\\s+(.+)$")
    private val taskPattern = Regex("^[-*+]\\s+\\[([ xX])]\\s+(.+)$")
    private val bulletPattern = Regex("^[-*+]\\s+(.+)$")
    private val specialCharsPattern = Regex("[\\[\\]{}()<>:;^#*+\\-\\t|~`@\\\\=/]")
    private val multiSpacePattern = Regex("\\s{2,}")

    fun parse(input: String): ParsedChecklist {
        // First, try markdown detection on the raw input — no normalization
        // so that markdown syntax (# ## - [x] etc.) is never corrupted.
        val allLines = input.lines().map { it.trim() }
        val rawLines = allLines.filter { it.isNotEmpty() }
        if (rawLines.isEmpty()) return ParsedChecklist(emptyList())

        val hasTabHeading = rawLines.any { tabPattern.containsMatchIn(it) }
        val hasSectionHeading = rawLines.any { sectionPattern.containsMatchIn(it) }

        if (hasSectionHeading) return parseStandard(rawLines)
        if (hasTabHeading) return parseTabOnlyMode(rawLines)
        if (achievementHeuristicApplies(rawLines)) return parseAchievementHeuristic(rawLines)
        if (hasParagraphBlocks(allLines)) return parseParagraphBlocksMode(allLines)

        // No structured format detected — normalize literal escape sequences
        // (e.g. two-char "\n" from some clipboard sources) and fall back to
        // plain-text mode.
        val normalized = input
            .replace("\\n", "\n")
            .replace("\\t", "\t")
            .replace("\\r", "")
        val normalizedLines = normalized.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (normalizedLines.isEmpty()) return ParsedChecklist(emptyList())

        return parsePlainTextMode(normalizedLines)
    }

    private fun achievementHeuristicApplies(lines: List<String>): Boolean {
        if (lines.size < 2) return false
        val dashCount = lines.count { it.contains(" - ") }
        return dashCount * 2 >= lines.size
    }

    /** True when the raw input contains at least two content blocks separated
     *  by blank lines — the paragraph-blocks heuristic. */
    private fun hasParagraphBlocks(allLines: List<String>): Boolean {
        var blocks = 0
        var inBlock = false
        for (line in allLines) {
            if (line.isEmpty()) {
                if (inBlock) {
                    blocks++
                    if (blocks >= 2) return true
                    inBlock = false
                }
            } else {
                inBlock = true
            }
        }
        if (inBlock) blocks++
        return blocks >= 2
    }

    /**
     * Paragraph-blocks mode: blank lines delimit **sections**. Each block of
     * consecutive non-blank lines becomes one section (no items); multi-line
     * blocks are joined with a single space so wrapped paragraphs collapse
     * into a single section title. Bullet / task markers at the start of a
     * single-line block are stripped so e.g. "- [x] Done\n\n- [ ] Todo"
     * yields two clean sections rather than keeping the bullet syntax.
     */
    private fun parseParagraphBlocksMode(allLines: List<String>): ParsedChecklist {
        val sections = mutableListOf<ParsedSection>()
        val current = StringBuilder()
        fun flush() {
            val raw = current.toString().trim()
            current.clear()
            if (raw.isEmpty()) return
            val taskMatch = taskPattern.matchEntire(raw)
            val bulletMatch = bulletPattern.matchEntire(raw)
            val name = when {
                taskMatch != null -> taskMatch.groupValues[2].trim()
                bulletMatch != null -> bulletMatch.groupValues[1].trim()
                else -> raw
            }.take(MAX_SECTION_NAME)
            if (name.isNotEmpty()) sections.add(ParsedSection(name, emptyList()))
        }
        for (line in allLines) {
            if (line.isEmpty()) flush() else {
                if (current.isNotEmpty()) current.append(' ')
                current.append(line)
            }
        }
        flush()
        return ParsedChecklist(
            listOf(ParsedTab(name = null, sections = sections)),
        )
    }

    /**
     * Tab-only mode: the paste has at least one `#` heading but no `##`. Bullets and
     * plain lines under a tab become sections (no items). Each tab ends up as a
     * list of empty sections that users can fill in later.
     */
    private fun parseTabOnlyMode(lines: List<String>): ParsedChecklist {
        val tabs = mutableListOf<MutableTab>()
        var current: MutableTab? = null

        fun ensureTab(): MutableTab {
            val t = current ?: MutableTab(name = null).also { tabs.add(it); current = it }
            return t
        }

        for (line in lines) {
            val tabMatch = tabPattern.matchEntire(line)
            if (tabMatch != null) {
                val t = MutableTab(name = tabMatch.groupValues[1].trim().take(MAX_TAB_NAME))
                tabs.add(t); current = t
                continue
            }
            // Any non-tab line becomes a section under the current tab. Task-checkbox
            // shapes strip their syntax first so the section name is clean.
            val sectionName = when {
                taskPattern.matchEntire(line) != null ->
                    taskPattern.matchEntire(line)!!.groupValues[2].trim()
                bulletPattern.matchEntire(line) != null ->
                    bulletPattern.matchEntire(line)!!.groupValues[1].trim()
                else -> line.trim()
            }.take(MAX_SECTION_NAME)
            if (sectionName.isNotEmpty()) {
                ensureTab().sections.add(MutableSection(sectionName))
            }
        }

        return ParsedChecklist(tabs.map { ParsedTab(it.name, it.sections.map {
            s -> ParsedSection(s.name, s.items.toList())
        }) })
    }

    /**
     * Plain-text mode: no headings, no achievement heuristic, no paragraph blocks.
     * Each line becomes one item. Tabs and special characters within a line are
     * cleaned and joined (not used as separators).
     */
    private fun parsePlainTextMode(lines: List<String>): ParsedChecklist {
        val items = lines.mapNotNull { line ->
            val cleaned = cleanSpecialChars(line).take(MAX_ITEM_TITLE)
            if (cleaned.isNotEmpty()) ParsedItem(cleaned, false) else null
        }
        if (items.isEmpty()) return ParsedChecklist(emptyList())

        val sectionName = "Imported ${currentDate()}"
        return ParsedChecklist(
            listOf(ParsedTab(name = null, sections = listOf(ParsedSection(sectionName, items))))
        )
    }

    /** Strip non-word punctuation and collapse resulting whitespace. */
    private fun cleanSpecialChars(text: String): String =
        text.replace(specialCharsPattern, " ").trim().replace(multiSpacePattern, " ")

    /** Overridable for testing; production returns today's date. */
    internal open fun currentDate(): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        return sdf.format(java.util.Date())
    }

    private fun parseAchievementHeuristic(lines: List<String>): ParsedChecklist {
        val sections = lines.map { line ->
            val idx = line.indexOf(" - ")
            if (idx > 0) {
                val name = line.substring(0, idx).trim().take(MAX_SECTION_NAME)
                val item = line.substring(idx + 3).trim().take(MAX_ITEM_TITLE)
                ParsedSection(name, listOf(ParsedItem(item, false)))
            } else {
                val name = line.trim().take(MAX_SECTION_NAME)
                ParsedSection(name, listOf(ParsedItem(name.take(MAX_ITEM_TITLE), false)))
            }
        }
        return ParsedChecklist(listOf(ParsedTab(name = null, sections = sections)))
    }

    private fun parseStandard(lines: List<String>): ParsedChecklist {
        val tabs = mutableListOf<MutableTab>()
        var currentTab: MutableTab? = null
        var currentSection: MutableSection? = null

        fun ensureTab(): MutableTab {
            val t = currentTab ?: MutableTab(name = null).also {
                tabs.add(it); currentTab = it
            }
            return t
        }

        fun ensureSection(): MutableSection {
            val t = ensureTab()
            val s = currentSection ?: MutableSection("General").also {
                t.sections.add(it); currentSection = it
            }
            return s
        }

        for (line in lines) {
            val tabMatch = tabPattern.matchEntire(line)
            if (tabMatch != null) {
                val name = tabMatch.groupValues[1].trim().take(MAX_TAB_NAME)
                val newTab = MutableTab(name = name)
                tabs.add(newTab); currentTab = newTab; currentSection = null
                continue
            }

            val sectionMatch = sectionPattern.matchEntire(line)
            if (sectionMatch != null) {
                val name = sectionMatch.groupValues[1].trim().take(MAX_SECTION_NAME)
                val t = ensureTab()
                val newSection = MutableSection(name = name)
                t.sections.add(newSection)
                currentSection = newSection
                continue
            }

            val taskMatch = taskPattern.matchEntire(line)
            if (taskMatch != null) {
                val done = taskMatch.groupValues[1].equals("x", ignoreCase = true)
                val text = taskMatch.groupValues[2].trim().take(MAX_ITEM_TITLE)
                ensureSection().items.add(ParsedItem(text, done))
                continue
            }

            val bulletMatch = bulletPattern.matchEntire(line)
            if (bulletMatch != null) {
                val text = bulletMatch.groupValues[1].trim().take(MAX_ITEM_TITLE)
                ensureSection().items.add(ParsedItem(text, false))
                continue
            }

            val text = line.trim().take(MAX_ITEM_TITLE)
            if (text.isNotEmpty()) {
                ensureSection().items.add(ParsedItem(text, false))
            }
        }

        return ParsedChecklist(
            tabs.map { tab ->
                ParsedTab(
                    name = tab.name,
                    sections = tab.sections.map { ParsedSection(it.name, it.items.toList()) }
                )
            }
        )
    }

    private data class MutableTab(
        val name: String?,
        val sections: MutableList<MutableSection> = mutableListOf()
    )
    private data class MutableSection(
        val name: String,
        val items: MutableList<ParsedItem> = mutableListOf()
    )

    companion object {
        const val MAX_TAB_NAME = 50
        const val MAX_SECTION_NAME = 50
        const val MAX_ITEM_TITLE = 400
    }
}
