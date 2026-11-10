package com.twinlore.app.data.strategywiki

import org.json.JSONObject

/**
 * Parses a StrategyWiki MediaWiki API response
 * (`action=query&generator=categorymembers&prop=info&inprop=url`) into
 * [StrategyWikiEntry] rows.
 *
 * The members live under `query.pages` as an object keyed by page id; each
 * value carries `title` and `fullurl`. `pagePath` is the part of `fullurl`
 * after `/wiki/` (canonically encoded, e.g. `Luigi%27s_Mansion`). Deduped by
 * `pagePath`.
 */
object StrategyWikiDirectoryParser {
    private const val WIKI_MARKER = "/wiki/"

    fun extractEntries(json: String, stage: Int): List<StrategyWikiEntry> {
        val pages = JSONObject(json)
            .optJSONObject("query")
            ?.optJSONObject("pages")
            ?: return emptyList()
        val out = LinkedHashMap<String, StrategyWikiEntry>()
        for (key in pages.keys()) {
            val p = pages.getJSONObject(key)
            val title = p.optString("title").trim()
            val fullUrl = p.optString("fullurl")
            val idx = fullUrl.indexOf(WIKI_MARKER)
            if (title.isEmpty() || idx < 0) continue
            val pagePath = fullUrl.substring(idx + WIKI_MARKER.length)
            if (pagePath.isEmpty()) continue
            out.putIfAbsent(pagePath, StrategyWikiEntry(title, pagePath, stage))
        }
        return out.values.toList()
    }
}
