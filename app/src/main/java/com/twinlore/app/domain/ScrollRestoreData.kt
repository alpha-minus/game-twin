package com.twinlore.app.domain

/**
 * Payload for scroll position restoration (anchor-based with ratio fallback).
 * Used for both bookmarks and last-visited URL.
 *
 * @param normalizedAnchor Normalized text at viewport top (alphanumeric, lowercase); null = ratio-only restore.
 * @param occurrenceIndex 1-based index of that text in the document (from window.find loop); 0 when anchor is null.
 * @param scrollRatio scrollY / pageHeight for fallback when anchor is not found.
 */
data class ScrollRestoreData(
    val normalizedAnchor: String?,
    val occurrenceIndex: Int,
    val scrollRatio: Double
) {
    companion object {
        /** Ratio-only restore when no anchor was captured. */
        fun ratioOnly(scrollRatio: Double) = ScrollRestoreData(
            normalizedAnchor = null,
            occurrenceIndex = 0,
            scrollRatio = scrollRatio
        )
    }
}
