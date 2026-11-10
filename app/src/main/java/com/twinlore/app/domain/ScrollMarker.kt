package com.twinlore.app.domain

/**
 * A saved scroll position within a bookmark (guide page).
 * Used like map markers: user can jump between them with L1/R1.
 * occurrenceIndex: 1-based index of the label text in the page (0 = use scrollY fallback for old markers).
 */
data class ScrollMarker(
    val id: String,
    val label: String,
    val scrollY: Int,
    val order: Int,
    val occurrenceIndex: Int = 0
)
