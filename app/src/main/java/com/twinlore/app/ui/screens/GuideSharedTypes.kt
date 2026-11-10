package com.twinlore.app.ui.screens

/**
 * Table of contents entry for bookmark sidebar (label + scroll position in pixels).
 */
data class TocEntry(
    val label: String,
    val scrollY: Int,
    /** Line index within the text FAQ body (for live scroll recalculation on zoom change). -1 for HTML-heading-based entries. */
    val lineIndex: Int = -1
)

/**
 * Progress data for bookmark save operation
 */
data class SaveProgress(
    val totalAssets: Int = 0,
    val downloadedAssets: Int = 0,
    val currentAsset: String? = null
)
