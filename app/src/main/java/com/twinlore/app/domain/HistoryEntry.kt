package com.twinlore.app.domain

/**
 * One entry in a [BrowserTab]'s back/forward history stack.
 *
 * URL is the navigation target; title is the page's `<title>` at the time
 * onPageFinished fired (may be empty before the WebView resolves it).
 */
data class HistoryEntry(val url: String, val title: String)
