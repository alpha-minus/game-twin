package com.twinlore.app.data.gamefaqs

import com.twinlore.app.data.canonicalMatchTokens
import org.jsoup.parser.Parser

/** One parsed guide row from a community contributions page (before filtering). */
data class ContributionGuide(
    val title: String,
    val platform: String,
    val faqPath: String,
    val type: String,
    val isHtml: Boolean,
)

/**
 * Parses a GameFAQs community **contributions** page
 * (`/community/<user>/contributions/faqs`) into guide rows.
 *
 * Unlike the FOTM "Contributor Central" page, each game is a bold header anchor
 * (`<a class="bold float_l" href=".../faqs">Game Name</a>`, no platform suffix)
 * followed by one or more guide rows
 * (`<a class="link_color" href=".../faqs/{id}">Type</a>`), each optionally
 * flaired `<span class="flair">HTML</span>` when the guide is HTML-formatted.
 *
 * [extractGuides] returns every guide row verbatim. [selectEntries] applies the
 * catalog policy: keep only the wanted guide types, then collapse to one
 * [GameFaqsEntry] per game (canonical) title, preferring an HTML-formatted guide
 * over a plain-text one.
 */
object GameFaqsContributionsParser {

    /**
     * Guide types worth surfacing as a "walkthrough" in the picker. Compared
     * case-insensitively. "FAQs" (the user's wording) is GameFAQs' "FAQ".
     */
    val ALLOWED_TYPES = setOf(
        "faq",
        "faqs",
        "guide and walkthrough",
        "guide",
        "strategy guide",
    )

    private val GAME_HEADER = Regex(
        """<a class="bold[^"]*" href="https://gamefaqs\.gamespot\.com/[a-z0-9-]+/\d+-[^"/]+/faqs">([^<]+)</a>""",
    )

    private val GUIDE_ROW = Regex(
        """<a class="link_color" href="https://gamefaqs\.gamespot\.com/([a-z0-9-]+/\d+-[^"/]+/faqs/\d+)">([^<]+)</a>(\s*<span class="flair">HTML</span>)?""",
    )

    fun extractGuides(source: String): List<ContributionGuide> {
        // Game headers in document order; each guide row binds to the nearest
        // preceding header.
        val headers = GAME_HEADER.findAll(source)
            .map { it.range.first to Parser.unescapeEntities(it.groupValues[1], false).trim() }
            .toList()
        if (headers.isEmpty()) return emptyList()

        val out = ArrayList<ContributionGuide>()
        for (m in GUIDE_ROW.findAll(source)) {
            val faqPath = m.groupValues[1]
            val type = Parser.unescapeEntities(m.groupValues[2], false).trim()
            val isHtml = m.groupValues[3].isNotEmpty()
            // Nearest header before this row.
            val title = headers.lastOrNull { it.first < m.range.first }?.second ?: continue
            if (title.isEmpty()) continue
            val platform = faqPath.substringBefore('/').uppercase()
            out += ContributionGuide(title, platform, faqPath, type, isHtml)
        }
        return out
    }

    /**
     * Catalog policy for contribution guides: drop unwanted types, then collapse
     * to one guide per canonical game title — preferring an HTML guide over a
     * plain-text one, else keeping the first seen. The kept [ContributionGuide]
     * retains its [ContributionGuide.isHtml] flag so the catalog merge can rank
     * an HTML community guide above a (format-unknown) FOTM entry.
     */
    fun selectGuides(guides: List<ContributionGuide>): List<ContributionGuide> {
        val byTitle = LinkedHashMap<String, ContributionGuide>()
        for (g in guides) {
            if (g.type.lowercase() !in ALLOWED_TYPES) continue
            val key = canonicalMatchTokens(g.title).joinToString(" ")
            if (key.isEmpty()) continue
            val existing = byTitle[key]
            if (existing == null || (!existing.isHtml && g.isHtml)) {
                byTitle[key] = g
            }
        }
        return byTitle.values.toList()
    }

    /** [selectGuides] mapped to catalog [GameFaqsEntry] rows. */
    fun selectEntries(guides: List<ContributionGuide>): List<GameFaqsEntry> =
        selectGuides(guides).map { GameFaqsEntry(it.title, it.platform, it.faqPath) }
}
