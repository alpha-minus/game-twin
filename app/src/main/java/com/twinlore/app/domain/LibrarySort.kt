package com.twinlore.app.domain

/** Sort options for the vault library. Reuses [SortDirection] for asc/desc. */
enum class LibrarySortField(val label: String) {
    NAME("Name"),
    DATE_ADDED("Date added"),
    LAST_OPENED("Last opened"),
    ACHIEVEMENTS("Achievements");

    /** Direction applied when this field is freshly selected from the menu. */
    val defaultDirection: SortDirection
        get() = if (this == NAME) SortDirection.ASC else SortDirection.DESC

    /** Cycle to the next sort field in declaration order, wrapping at the end (R1 bumper). */
    fun next(): LibrarySortField {
        val all = LibrarySortField.entries
        return all[(ordinal + 1) % all.size]
    }

    /** Cycle to the previous sort field in declaration order, wrapping at the start. */
    fun previous(): LibrarySortField {
        val all = LibrarySortField.entries
        return all[(ordinal - 1 + all.size) % all.size]
    }
}

/**
 * Pure, stable sorter for the displayed vault list. Direction-aware per field so that
 * tied values keep their input order in BOTH directions (Kotlin's sortedBy /
 * sortedByDescending are both stable). A plain "ascending then reverse" would flip
 * the relative order of ties in descending mode, which we don't want.
 */
object LibraryVaultSorter {
    fun sort(
        vaults: List<Vault>,
        field: LibrarySortField,
        direction: SortDirection,
        achievementInfo: Map<String, AchievementSortInfo>
    ): List<Vault> {
        val asc = direction == SortDirection.ASC
        return when (field) {
            LibrarySortField.NAME ->
                if (asc) vaults.sortedBy { it.displayName.lowercase() }
                else vaults.sortedByDescending { it.displayName.lowercase() }
            LibrarySortField.DATE_ADDED ->
                if (asc) vaults.sortedBy { it.createdAt.time }
                else vaults.sortedByDescending { it.createdAt.time }
            LibrarySortField.LAST_OPENED ->
                if (asc) vaults.sortedBy { it.lastOpened?.time ?: 0L }
                else vaults.sortedByDescending { it.lastOpened?.time ?: 0L }
            LibrarySortField.ACHIEVEMENTS ->
                // softcoreFraction is the higher of the two layers (hardcore <= softcore),
                // so it represents overall completion — "whichever has more".
                if (asc) vaults.sortedBy { achievementInfo[it.id]?.softcoreFraction ?: 0f }
                else vaults.sortedByDescending { achievementInfo[it.id]?.softcoreFraction ?: 0f }
        }
    }
}
