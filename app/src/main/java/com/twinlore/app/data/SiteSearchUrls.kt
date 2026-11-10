package com.twinlore.app.data

/**
 * Queries for the site searches the pickers reach directly, rather than
 * through the user's own search engine.
 *
 * These live here, and not beside the menu that happened to need them first,
 * because more than one menu reaches the same site: the Guide tab's Browse
 * Guide and the Image tab's Browse Image both offer Neoseeker, and they have
 * to ask it the same question. They did not - the Image tab carried its own
 * copy of the URL builder that skipped the sanitising step - and a vault whose
 * name had a colon or a bracketed region in it searched fine from one tab and
 * came back with nothing from the other.
 */

/**
 * Strip noisy punctuation from a vault display name before feeding it to a
 * site-specific search endpoint. GameFAQs and Neoseeker both treat the
 * query as an exact-match-leaning game lookup, so subtitle separators
 * (`:`, `-`, `–`, `—`) and bracketed remasters / regions (`Pokémon Red
 * (USA)`, `Final Fantasy VII [Remake]`) cripple the result list. Collapses
 * the noise to a single space so word boundaries survive.
 *
 * The site-engine queries (Steam guides, the PDF manual) forward the name
 * verbatim to the user's general search engine, where those characters are
 * usually ignored anyway, so they are unchanged.
 */
internal fun sanitiseSiteSearchName(gameName: String): String {
    return gameName
        .replace(Regex("[:\\-–—()\\[\\]{}/,;\"']"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}

/**
 * Neoseeker search via their Google Custom Search Engine endpoint.
 * The header search box and result pages both route through seeker.php with this fixed
 * cx/cof/ie/sa preamble; only `q` varies.
 *
 * The bare game name, with no "walkthrough" or "map" suffix appended: Neoseeker's
 * top hits for a title already list its walkthrough and its maps/charts pages
 * side by side, and narrowing the query only loses one of them.
 */
internal fun neoseekerSearchUrl(gameName: String): String {
    val cleaned = sanitiseSiteSearchName(gameName).ifBlank { "walkthrough" }
    val q = java.net.URLEncoder.encode(cleaned, "UTF-8")
    return "https://www.neoseeker.com/seeker.php" +
        "?cx=016579983352691578828%3A8lybbedigmo" +
        "&cof=FORID%3A9" +
        "&ie=UTF-8" +
        "&sa=Search" +
        "&q=$q"
}
