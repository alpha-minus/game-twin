package com.twinlore.app.data.esde

/**
 * ES-DE names its auto-collections `auto-*` and themes keep grouped-collection art under
 * `custom-collections`; map the event short names (verified against ES-DE's
 * CollectionSystemsManager: `all`/`recent`/`favorites`, grouped bundle = `collections`) to the
 * theme folder names. Everything else — including custom es_systems.xml entries and ungrouped
 * custom collections — passes through verbatim, exactly like es-de-companion (no es_systems.xml
 * parsing; unknown names degrade to the text card).
 */
fun normalizeEsdeSystemName(s: String): String = when (s.lowercase()) {
    "favorites", "favourites", "auto-favorites" -> "auto-favorites"
    "lastplayed", "recent", "auto-lastplayed" -> "auto-lastplayed"
    "allgames", "all", "auto-allgames" -> "auto-allgames"
    "collections", "custom-collections" -> "custom-collections"
    else -> s.lowercase()
}

/**
 * Human title for a platform screen. ES-DE's auto collections arrive with lowercase full names
 * ("favorites", "last played") — give them proper capitals; real systems keep the event's full
 * name (already proper, e.g. "Sega Genesis").
 */
fun esdeSystemDisplayName(systemShort: String, systemFull: String): String =
    when (normalizeEsdeSystemName(systemShort)) {
        "auto-favorites" -> "Favorites"
        "auto-lastplayed" -> "Last Played"
        "auto-allgames" -> "All Games"
        "custom-collections" -> "Collections"
        else -> systemFull.ifBlank { systemShort }
    }
