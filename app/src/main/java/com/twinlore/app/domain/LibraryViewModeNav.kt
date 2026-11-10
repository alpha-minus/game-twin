package com.twinlore.app.domain

import com.twinlore.app.data.LibraryViewMode

/** Cycle to the next view mode in declaration order, wrapping at the end. */
fun LibraryViewMode.next(): LibraryViewMode {
    val entries = LibraryViewMode.entries
    return entries[(ordinal + 1) % entries.size]
}

/** Cycle to the previous view mode in declaration order, wrapping at the start. */
fun LibraryViewMode.previous(): LibraryViewMode {
    val entries = LibraryViewMode.entries
    return entries[(ordinal - 1 + entries.size) % entries.size]
}
