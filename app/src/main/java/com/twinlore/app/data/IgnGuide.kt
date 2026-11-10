package com.twinlore.app.data

import java.text.Normalizer

/**
 * IGN wiki guides are addressed by a slug derived from the game name:
 * accents are stripped, the text is lowercased, and every run of
 * non-alphanumeric characters (spaces, ":", "-", etc.) collapses to a single
 * "-". Digits are kept so sequels survive ("God of War 2" → "god-of-war-2").
 *
 *   "Castlevania: Circle of the Moon" → castlevania-circle-of-the-moon
 *   "Pokémon Red"                     → pokemon-red
 */
internal fun ignSlug(name: String): String {
    val noAccents = Normalizer.normalize(name, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
    return noAccents.lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
}

/**
 * Build the IGN wiki guide URL for [gameName] (the game's wiki landing page —
 * no "/walkthrough" suffix). Falls back to the IGN wikis landing page when the
 * name has no usable characters.
 */
internal fun ignGuideUrl(gameName: String): String {
    val slug = ignSlug(gameName)
    return if (slug.isEmpty()) "https://www.ign.com/wikis"
    else "https://www.ign.com/wikis/$slug"
}
