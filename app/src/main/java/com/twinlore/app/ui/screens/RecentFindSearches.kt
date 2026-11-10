package com.twinlore.app.ui.screens

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The last few find-on-page queries, newest first. Trimmed, blanks ignored,
 * duplicates folded case-insensitively (the newest spelling wins and moves to
 * the front), capped at [CAPACITY].
 *
 * Held in memory only and never persisted: the list lives as long as the
 * process, so it survives screen changes and is gone after process death.
 */
open class RecentFindSearchStore {
    private val _queries = MutableStateFlow<List<String>>(emptyList())
    val queries: StateFlow<List<String>> = _queries.asStateFlow()

    /** Records [query] as just searched. A blank query is ignored. */
    fun record(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        _queries.update { current ->
            (listOf(q) + current.filterNot { it.equals(q, ignoreCase = true) })
                .take(CAPACITY)
        }
    }

    fun clear() {
        _queries.value = emptyList()
    }

    companion object {
        const val CAPACITY = 5
    }
}

/**
 * The one list every guide reader shares: single web and text guides, PDFs,
 * page groups (both scopes), in the app and in the fold panel's overlay
 * window, which runs in the same process.
 */
object RecentFindSearches : RecentFindSearchStore()
