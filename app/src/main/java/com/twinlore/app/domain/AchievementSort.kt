package com.twinlore.app.domain

import com.twinlore.app.data.retroachievements.RaAchievementDisplay
import com.twinlore.app.data.steam.SteamAchievementDisplay

/**
 * Sort fields for the RetroAchievements drawer. Reuses [SortDirection] for asc/desc:
 * the menu lists each field once and re-picking the current field toggles its direction.
 */
enum class RaAchievementSortField(val label: String) {
    UNLOCK_TIME("Unlock time"),
    DISPLAY_ORDER("Display order"),
    WON_BY("Won by"),
    POINTS("Points"),
    TITLE("Title"),
    TYPE("Type");

    /** Direction applied when this field is freshly selected from the menu. */
    val defaultDirection: SortDirection
        get() = when (this) {
            UNLOCK_TIME, WON_BY, POINTS -> SortDirection.DESC
            DISPLAY_ORDER, TITLE, TYPE -> SortDirection.ASC
        }

    /** Human wording for a direction, so the menu reads "Newest" rather than "descending". */
    fun directionLabel(direction: SortDirection): String {
        val asc = direction == SortDirection.ASC
        return when (this) {
            UNLOCK_TIME -> if (asc) "Oldest" else "Newest"
            DISPLAY_ORDER -> if (asc) "First" else "Last"
            WON_BY, POINTS -> if (asc) "Least" else "Most"
            TITLE, TYPE -> if (asc) "A-Z" else "Z-A"
        }
    }
}

/**
 * Achievement-type filter for the RA drawer, cycled by a single chip:
 * all -> progression -> missable -> all.
 *
 * [PROGRESSION] deliberately covers RetroAchievements' `win_condition` as well as `progression`,
 * matching how the checklist tabs already group them (see `AchievementDrawerViewModel`): the
 * beat-the-game achievement IS part of the critical path, and hiding it while filtering for
 * "what do I still have to do" would read as a bug.
 */
enum class RaAchievementTypeFilter(val label: String) {
    ALL("Type: All"),
    PROGRESSION("Progression"),
    MISSABLE("Missable");

    /** Next state in the chip's cycle, wrapping back to [ALL]. */
    fun next(): RaAchievementTypeFilter = entries[(ordinal + 1) % entries.size]

    /**
     * Whether an achievement of this RA `type` survives the filter. Types are compared
     * case- and whitespace-insensitively; a null/blank type only survives [ALL].
     */
    fun matches(type: String?): Boolean {
        val t = type?.lowercase()?.trim()
        return when (this) {
            ALL -> true
            PROGRESSION -> t == "progression" || t == "win_condition"
            MISSABLE -> t == "missable"
        }
    }
}

/**
 * The RA drawer's free-text filter, kept beside the sorter and the type filter it runs with rather
 * than inside the drawer that types into it.
 *
 * Matches on title and on description, because half of what a player remembers about an achievement
 * is what it asked them to do rather than what it is called ("collect all the stars" finds a badge
 * named "Star Struck"). Plain case-insensitive substring, not the fuzzy token matching the guide
 * catalogues use: those match one name against thousands of unrelated titles and need the slack,
 * while this is a person narrowing a list they are already looking at, where a query that quietly
 * matched something else is worse than one that matches nothing.
 */
object RaAchievementSearch {

    /** [list] narrowed to [query]; a blank query narrows nothing. */
    fun filter(list: List<RaAchievementDisplay>, query: String): List<RaAchievementDisplay> {
        val q = query.trim()
        if (q.isEmpty()) return list
        return list.filter {
            it.title.contains(q, ignoreCase = true) || it.description.contains(q, ignoreCase = true)
        }
    }
}

/**
 * The same for the Steam drawer, whose header carries the same search box.
 *
 * A separate object rather than one generic over both, because the two achievement types share no
 * supertype and inventing one to spare four lines would put a shape on the data model to serve a
 * text box. Steam's description is nullable - hidden achievements have none - and a missing
 * description simply has nothing to match.
 */
object SteamAchievementSearch {

    /** [list] narrowed to [query]; a blank query narrows nothing. */
    fun filter(list: List<SteamAchievementDisplay>, query: String): List<SteamAchievementDisplay> {
        val q = query.trim()
        if (q.isEmpty()) return list
        return list.filter {
            it.title.contains(q, ignoreCase = true) ||
                it.description?.contains(q, ignoreCase = true) == true
        }
    }
}

/** Sort fields for the Steam achievement drawer. */
enum class SteamAchievementSortField(val label: String) {
    UNLOCK_TIME("Unlock time"),
    TITLE("Title"),
    RARITY("Rarity");

    val defaultDirection: SortDirection
        get() = when (this) {
            UNLOCK_TIME -> SortDirection.DESC
            TITLE, RARITY -> SortDirection.ASC
        }

    fun directionLabel(direction: SortDirection): String {
        val asc = direction == SortDirection.ASC
        return when (this) {
            UNLOCK_TIME -> if (asc) "Oldest" else "Newest"
            TITLE -> if (asc) "A-Z" else "Z-A"
            RARITY -> if (asc) "Rarest" else "Most common"
        }
    }
}

/** Pure sorter for the RA drawer list. */
object RaAchievementSorter {
    fun sort(
        achievements: List<RaAchievementDisplay>,
        field: RaAchievementSortField,
        direction: SortDirection
    ): List<RaAchievementDisplay> {
        val asc = direction == SortDirection.ASC
        return when (field) {
            // Locked achievements stay at the bottom in both directions; the direction only
            // reorders the unlocked block. dateAwarded is "YYYY-MM-DD HH:MM:SS", so string
            // compare is chronological.
            RaAchievementSortField.UNLOCK_TIME -> achievements.sortedWith(
                if (asc) {
                    compareByDescending<RaAchievementDisplay> { it.dateAwarded != null }
                        .thenBy { it.dateAwarded ?: "" }
                } else {
                    compareByDescending<RaAchievementDisplay> { it.dateAwarded != null }
                        .thenByDescending { it.dateAwarded ?: "" }
                }
            )
            RaAchievementSortField.DISPLAY_ORDER ->
                if (asc) achievements.sortedBy { it.displayOrder }
                else achievements.sortedByDescending { it.displayOrder }
            RaAchievementSortField.WON_BY ->
                if (asc) achievements.sortedBy { it.numAwarded ?: Int.MAX_VALUE }
                else achievements.sortedByDescending { it.numAwarded ?: 0 }
            RaAchievementSortField.POINTS ->
                if (asc) achievements.sortedBy { it.points }
                else achievements.sortedByDescending { it.points }
            RaAchievementSortField.TITLE ->
                if (asc) achievements.sortedBy { it.title.lowercase() }
                else achievements.sortedByDescending { it.title.lowercase() }
            RaAchievementSortField.TYPE ->
                if (asc) achievements.sortedBy { (it.type ?: "").lowercase() }
                else achievements.sortedByDescending { (it.type ?: "").lowercase() }
        }
    }
}

/** Pure sorter for the Steam drawer list. */
object SteamAchievementSorter {
    fun sort(
        achievements: List<SteamAchievementDisplay>,
        field: SteamAchievementSortField,
        direction: SortDirection
    ): List<SteamAchievementDisplay> {
        val asc = direction == SortDirection.ASC
        return when (field) {
            // Locked achievements stay at the bottom in both directions.
            SteamAchievementSortField.UNLOCK_TIME -> achievements.sortedWith(
                if (asc) {
                    compareByDescending<SteamAchievementDisplay> { it.achieved }
                        .thenBy { it.unlockTime ?: Long.MAX_VALUE }
                } else {
                    compareByDescending<SteamAchievementDisplay> { it.achieved }
                        .thenByDescending { it.unlockTime ?: 0L }
                }
            )
            SteamAchievementSortField.TITLE ->
                if (asc) achievements.sortedBy { it.title.lowercase() }
                else achievements.sortedByDescending { it.title.lowercase() }
            // Rarest = lowest global unlock percent.
            SteamAchievementSortField.RARITY ->
                if (asc) achievements.sortedBy { it.globalPercent ?: 100.0 }
                else achievements.sortedByDescending { it.globalPercent ?: 0.0 }
        }
    }
}
