package com.twinlore.app.data

import com.twinlore.app.domain.SavedTab

/**
 * Fixed landing page slot identity. Order defines display order (0..9).
 *
 * Slots 5-9 were previously site-pickers (Game Art / Steam / GameFAQs / Neoseeker
 * / Manual). Those moved to the Guide tab's Browse menu, freeing slots 5-9 for
 * the map/diagram quick-search shortcuts that used to live in the Maps tab's chip
 * row (Cheat Sheet / 100% Completion / Resource Farming / Boss-Enemy / Puzzles).
 */
enum class LandingSection(
    val sectionId: String,
    val slotIndex: Int
) {
    GAMEPLAY("gameplay", 0),
    MAP("map", 1),
    TIPS("tips", 2),
    BUILDS("builds", 3),
    ACHIEVEMENTS("achievements", 4),
    CHEAT_SHEET("cheat_sheet", 5),
    COMPLETION_100("completion_100", 6),
    RESOURCE_FARMING("resource_farming", 7),
    BOSS_ENEMY("boss_enemy", 8),
    PUZZLE_SOLUTIONS("puzzle_solutions", 9);

    companion object {
        private val bySectionId = entries.associateBy { it.sectionId }
        val ordered: List<LandingSection> = entries.sortedBy { it.slotIndex }
        val displayOrder: List<LandingSection> = listOf(
            GAMEPLAY, MAP, TIPS, BUILDS, ACHIEVEMENTS, CHEAT_SHEET,
            COMPLETION_100, RESOURCE_FARMING, BOSS_ENEMY, PUZZLE_SOLUTIONS
        )

        fun fromSectionId(id: String): LandingSection? = bySectionId[id]
    }
}

/**
 * One landing page entry: label (on tile), append word (for query), image search toggle,
 * optional site/filetype operators. Query is built by [buildSearchQuery].
 */
data class LandingEntry(
    val label: String,
    val appendWord: String,
    val useImageSearch: Boolean,
    val site: String = "",
    val fileType: String = ""
)

/**
 * Default landing entries (order = slotIndex 0..9).
 * Query is built via [buildSearchQuery] from each entry's appendWord plus optional site/filetype.
 *
 * Slots 5-9 mirror the map/diagram shortcuts that used to live in the Maps tab's
 * chip row — all image searches so the tile lands on visual results.
 */
fun defaultLandingEntries(): List<LandingEntry> = listOf(
    LandingEntry("Gameplay",         "Gameplay",                          false),
    LandingEntry("Story / Lore",     "Story Lore Guide",                  false),
    LandingEntry("Tips & Tricks",    "Tips & Tricks",                     false),
    LandingEntry("Best Build",       "Best Build",                        false),
    LandingEntry("Achievements",     "All Achievements Guide",            false),
    LandingEntry("Cheat Sheet",      "cheat sheet",                       true),
    LandingEntry("100% Completion",  "100% completion collectibles map",  true),
    LandingEntry("Resource Farming", "resource farming map",              true),
    LandingEntry("Boss Guide",       "boss guide",                        false),
    LandingEntry("Puzzles",          "puzzle solutions images",           true),
)

/**
 * Build a search query for [entry] in the context of [gameName].
 * Joins non-blank parts with single spaces:
 *   "<gameName> <appendWord> [site:<site>] [filetype:<fileType>]"
 */
fun buildSearchQuery(gameName: String?, entry: LandingEntry): String {
    val parts = buildList {
        gameName?.takeIf { it.isNotBlank() }?.let { add(it.trim()) }
        entry.appendWord.takeIf { it.isNotBlank() }?.let { add(it.trim()) }
        entry.site.takeIf { it.isNotBlank() }?.let { add("site:${it.trim()}") }
        entry.fileType.takeIf { it.isNotBlank() }?.let { add("filetype:${it.trim()}") }
    }
    return parts.joinToString(" ")
}

/**
 * Build the inner HTML for the landing page nav circle from entries.
 * Escapes label for HTML. Section ids follow LandingSection.ordered.
 */
fun buildLandingNavHtml(entries: List<LandingEntry>): String {
    fun escape(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
    return LandingSection.displayOrder.map { section ->
        val entry = entries.getOrNull(section.slotIndex) ?: defaultLandingEntries().getOrNull(section.slotIndex) ?: return@map ""
        val label = escape(entry.label.ifBlank { section.sectionId })
        """            <a href="vault://section/${section.sectionId}" class="nav-icon" data-section="${section.sectionId}" aria-label="$label">
                <span class="icon-label">$label</span>
            </a>"""
    }.joinToString("\n")
}

/**
 * Build HTML for quick search template tiles (title only, compact).
 * Used on landing page when layout is "templates" (no circular pattern).
 */
fun buildLandingQuickSearchTemplatesHtml(entries: List<LandingEntry>): String {
    fun escape(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
    val pencilSvg = """<svg class="edit-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>"""
    return LandingSection.displayOrder.map { section ->
        val entry = entries.getOrNull(section.slotIndex) ?: defaultLandingEntries().getOrNull(section.slotIndex) ?: return@map ""
        val label = escape(entry.label.ifBlank { section.sectionId })
        """            <a href="vault://section/${section.sectionId}" class="quick-search-item" data-section="${section.sectionId}" aria-label="$label">
                <span class="quick-search-label">$label</span>
                <button class="quick-search-edit" data-slot="${section.slotIndex}" aria-label="Edit $label">$pencilSvg</button>
            </a>"""
    }.joinToString("\n")
}

/**
 * Build HTML for recent searches section (last 10). Each item has data-query for onRunSearch.
 */
fun buildRecentSearchesHtml(recentSearches: List<String>): String {
    fun escape(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
    if (recentSearches.isEmpty()) {
        return """            <p class="recent-searches-empty">No recent searches</p>"""
    }
    return recentSearches.take(10).mapIndexed { index, query ->
        val safe = escape(query.take(80))
        """            <a href="#" class="recent-search-item" data-query="${safe.replace("\"", "&quot;")}" aria-label="Search: $safe">$safe</a>"""
    }.joinToString("\n")
}

/**
 * Build the inner HTML for the "Saved links" section. Each entry renders as a
 * tile with favicon (via data-favicon attr; JS applies it as background-image),
 * label, and a x delete button. Returns empty string for empty input; caller
 * is responsible for hiding the surrounding <section> when output is empty.
 */
fun buildSavedLinksHtml(links: List<SavedTab>): String {
    if (links.isEmpty()) return ""
    fun escape(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
    val closeSvg = """<svg class="close-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"><path d="M6 6L18 18M18 6L6 18" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>"""
    return links.joinToString("\n") { link ->
        val rawLabel = link.title.ifBlank {
            runCatching { java.net.URL(link.url).host }.getOrNull() ?: link.url.take(40)
        }
        val label = escape(rawLabel)
        val faviconAttr = link.faviconBytes
            ?.let { """ data-favicon="data:image/png;base64,${java.util.Base64.getEncoder().encodeToString(it)}"""" }
            ?: ""
        val safeId = escape(link.id)
        """            <a href="#" class="saved-link-item" data-link-id="$safeId"$faviconAttr aria-label="$label">
                <span class="saved-link-favicon"></span>
                <span class="saved-link-label">$label</span>
                <button class="saved-link-delete" data-link-id="$safeId" aria-label="Remove $label">$closeSvg</button>
            </a>"""
    }
}
