package com.twinlore.app.data

/**
 * Search engine options for browser and map searches
 */
enum class SearchEngine(
    val displayName: String,
    val homeUrl: String,
    private val searchUrlTemplate: String
) {
    GOOGLE(
        displayName = "Google",
        homeUrl = "https://www.google.com",
        searchUrlTemplate = "https://www.google.com/search?q={query}"
    ),
    DUCKDUCKGO(
        displayName = "DuckDuckGo",
        homeUrl = "https://duckduckgo.com",
        searchUrlTemplate = "https://duckduckgo.com/?q={query}"
    ),
    BRAVE(
        displayName = "Brave Search",
        homeUrl = "https://search.brave.com",
        searchUrlTemplate = "https://search.brave.com/search?q={query}"
    ),
    STARTPAGE(
        displayName = "Startpage",
        homeUrl = "https://www.startpage.com",
        searchUrlTemplate = "https://www.startpage.com/search?q={query}"
    ),
    QWANT(
        displayName = "Qwant",
        homeUrl = "https://www.qwant.com",
        searchUrlTemplate = "https://www.qwant.com/?q={query}"
    ),
    ECOSIA(
        displayName = "Ecosia",
        homeUrl = "https://www.ecosia.org",
        searchUrlTemplate = "https://www.ecosia.org/search?q={query}"
    ),
    BING(
        displayName = "Bing",
        homeUrl = "https://www.bing.com",
        searchUrlTemplate = "https://www.bing.com/search?q={query}"
    ),
    BAIDU(
        displayName = "Baidu",
        homeUrl = "https://www.baidu.com",
        searchUrlTemplate = "https://www.baidu.com/s?wd={query}"
    ),
    YANDEX(
        displayName = "Yandex",
        homeUrl = "https://yandex.com",
        searchUrlTemplate = "https://yandex.com/search/?text={query}"
    );

    /**
     * Check if the given URL looks like a search URL for this engine.
     * Used for special handling of search navigations in the browser.
     */
    fun isSearchUrl(url: String): Boolean {
        val prefix = searchUrlTemplate.substringBefore("{query}")
        return url.startsWith(prefix)
    }
}
