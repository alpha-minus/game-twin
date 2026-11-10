package com.twinlore.app.data.gamefaqs

import com.twinlore.app.data.canonicalMatchTokens
import org.jsoup.parser.Parser

/**
 * Parses the GameFAQs Contributor Central ("FAQ of the Month") page into
 * [GameFaqsEntry] rows. Works on a raw HTML string or an unencoded MHT body
 * (the snapshot uses `Content-Transfer-Encoding: binary`, so the HTML is inline
 * and the MIME prefix is harmless to the anchor regex).
 *
 * Each featured FAQ is an anchor of the shape
 * `<a href="https://gamefaqs.gamespot.com/{platform}/{id}-{slug}/faqs/{faqid}">Title (PLATFORM)</a>`.
 * The trailing ` (PLATFORM)` suffix is split off the link text into
 * [GameFaqsEntry.platform]. Entries are returned in document order, deduped by
 * canonical-title key (first occurrence wins — some games are featured more than
 * once and on multiple platforms).
 */
object GameFaqsContributorParser {
    // href group 1 = path after the host (platform/id-slug/faqs/faqid),
    // group 2 = raw anchor text ("Title (PLATFORM)", may carry HTML entities).
    private val ANCHOR = Regex(
        """<a\b[^>]*\bhref="https://gamefaqs\.gamespot\.com/([a-z0-9]+/\d+-[^"/]+/faqs/\d+)"[^>]*>([^<]+)</a>""",
        RegexOption.IGNORE_CASE,
    )

    // Trailing platform code in parentheses at the very end, e.g. " (PS5)".
    private val PLATFORM_SUFFIX = Regex("""\s*\(([^()]*)\)\s*$""")

    fun extractEntries(source: String): List<GameFaqsEntry> {
        val out = LinkedHashMap<String, GameFaqsEntry>()
        for (m in ANCHOR.findAll(source)) {
            val faqPath = m.groupValues[1]
            val rawText = Parser.unescapeEntities(m.groupValues[2], false).trim()
            if (rawText.isEmpty()) continue
            val match = PLATFORM_SUFFIX.find(rawText)
            val title = (if (match != null) rawText.removeRange(match.range) else rawText).trim()
            val platform = match?.groupValues?.get(1)?.trim().orEmpty()
            if (title.isEmpty()) continue
            val key = canonicalMatchTokens(title).joinToString(" ")
            if (key.isEmpty()) continue
            out.putIfAbsent(key, GameFaqsEntry(title, platform, faqPath))
        }
        return out.values.toList()
    }
}
