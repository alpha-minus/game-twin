package com.twinlore.app.ui.screens.pdf

/**
 * Pre-order flatten of a hierarchical [PdfOutlineEntry] tree into a flat
 * list of (entry, depth) pairs. Depth = number of ancestors; root entries
 * are depth 0, their direct children depth 1, etc. Consumed by the
 * Contents tab in [PdfTocViewScreen] to render indented rows.
 */
internal fun flattenOutline(
    entries: List<PdfOutlineEntry>,
    depth: Int = 0,
): List<Pair<PdfOutlineEntry, Int>> {
    val result = mutableListOf<Pair<PdfOutlineEntry, Int>>()
    for (entry in entries) {
        result.add(entry to depth)
        if (entry.children.isNotEmpty()) {
            result.addAll(flattenOutline(entry.children, depth + 1))
        }
    }
    return result
}
