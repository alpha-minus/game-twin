package com.twinlore.app.data.strategywiki

/**
 * One StrategyWiki guide page from the bundled completion-stage directory.
 *
 * [stage] is StrategyWiki's own completion stage. Only [StrategyWikiCatalog.MIN_STAGE] and up ever
 * reach a vault - stage 2 ("Maturing") pages are half-written walkthroughs and are filtered out on
 * load - so [stageName] names those three and nothing else.
 *
 * [pagePath] is the path exactly as it appears after `/wiki/` in the category
 * page's member link (already percent-encoded, e.g. `Luigi%27s_Mansion`), so
 * appending `/Walkthrough` yields a valid URL without further encoding.
 */
data class StrategyWikiEntry(
    val title: String,
    val pagePath: String,
    val stage: Int,
) {
    val pageUrl: String get() = "https://strategywiki.org/wiki/$pagePath"
    val walkthroughUrl: String get() = "$pageUrl/Walkthrough"
    val stageName: String get() = when (stage) {
        5 -> "Featured"
        4 -> "Comprehensive"
        3 -> "Thorough"
        else -> "Guide"
    }
}
