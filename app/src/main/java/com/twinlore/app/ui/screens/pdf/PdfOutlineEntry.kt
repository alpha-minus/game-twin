package com.twinlore.app.ui.screens.pdf

/**
 * A single entry in the PDF document outline (table of contents).
 */
data class PdfOutlineEntry(
    val title: String,
    val pageNumber: Int,
    val children: List<PdfOutlineEntry> = emptyList()
)
