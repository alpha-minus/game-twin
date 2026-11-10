package com.twinlore.app.data.gamefaqs

/**
 * One curated GameFAQs featured FAQ from the bundled Contributor Central
 * ("FAQ of the Month") directory.
 *
 * [faqPath] is the path exactly as it appears after `gamefaqs.gamespot.com/` in
 * the listing anchor, e.g. `ps5/383909-silent-hill-2/faqs/81517`, so [faqUrl]
 * lands directly on that specific featured FAQ. [title] has the trailing
 * ` (PLATFORM)` suffix stripped; [platform] keeps the platform code (`PS5`).
 */
data class GameFaqsEntry(
    val title: String,
    val platform: String,
    val faqPath: String,
) {
    val faqUrl: String get() = "https://gamefaqs.gamespot.com/$faqPath"
}
