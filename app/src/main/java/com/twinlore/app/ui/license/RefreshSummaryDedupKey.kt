package com.twinlore.app.ui.license

/**
 * Stable hash of the set of game names skipped by the license cap on
 * the most recent refresh. Used to suppress the summary dialog when the
 * same set of skipped games would repeat unchanged across refreshes.
 */
fun refreshSummaryDedupKey(skippedNames: List<String>): String =
    skippedNames.toSortedSet().joinToString("|").hashCode().toString()
