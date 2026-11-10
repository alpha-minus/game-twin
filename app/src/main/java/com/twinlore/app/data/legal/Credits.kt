package com.twinlore.app.data.legal

/**
 * A single attribution line: who or what is being credited, why, and where to learn more.
 */
data class Credit(val name: String, val detail: String, val url: String?)

/**
 * Groups of things Game Twin credits that are not open-source dependencies: the metadata sources
 * it queries, the fonts it bundles, and the upstream projects it integrates with.
 */
enum class CreditGroup(val displayName: String) {
    SOURCES("Metadata & artwork sources"),
    FONTS("Fonts"),
    THANKS("Thanks"),
}

/**
 * Attribution for things Game Twin uses that are not open-source software dependencies (those
 * live in [com.twinlore.app.data.OpenSourceLicenseRepository]). `CreditsCoverageTest` ties the
 * font entries to the files actually bundled under `assets/fonts/`, so this list cannot drift
 * from what ships.
 */
object Credits {
    val ALL: Map<CreditGroup, List<Credit>> = mapOf(
        CreditGroup.SOURCES to listOf(
            Credit(
                name = "RetroAchievements",
                detail = "Achievement data and artwork",
                url = "https://retroachievements.org",
            ),
            Credit(
                name = "Steam",
                detail = "Game metadata",
                url = "https://store.steampowered.com",
            ),
            Credit(
                name = "SteamGridDB",
                detail = "Box art and grid images",
                url = "https://www.steamgriddb.com",
            ),
            Credit(
                name = "TheGamesDB",
                detail = "Game metadata and artwork",
                url = "https://thegamesdb.net",
            ),
        ),
        CreditGroup.FONTS to listOf(
            Credit(
                name = "Courier Prime",
                detail = "Designed by Alan Dague-Greene for John August, released by Quote-Unquote Apps",
                url = null,
            ),
            Credit(
                name = "JetBrains Mono",
                detail = "Designed by Philipp Nurullin and Konstantin Bulenkov at JetBrains",
                url = null,
            ),
            Credit(
                name = "Press Start 2P",
                detail = "Designed by Cody Boisclair",
                url = null,
            ),
            Credit(
                name = "Share Tech Mono",
                detail = "Designed by Carrois Type Design, Ralph du Carrois",
                url = null,
            ),
            Credit(
                name = "Space Mono",
                detail = "Designed by Colophon Foundry for Google Fonts",
                url = null,
            ),
        ),
        CreditGroup.THANKS to listOf(
            Credit(
                name = "ES-DE",
                detail = "A frontend Game Twin sits alongside",
                url = "https://es-de.org",
            ),
            Credit(
                name = "Cocoon",
                detail = "A dual-screen frontend Game Twin sits alongside",
                url = "https://cocoon-shell.com",
            ),
            Credit(
                name = "RetroArch",
                detail = "The emulator Game Twin detects play sessions from",
                url = "https://www.retroarch.com",
            ),
            Credit(
                name = "Not affiliated",
                detail = "Game Twin is an independent project and is not affiliated with, " +
                    "endorsed by, or sponsored by any of the projects or services named above.",
                url = null,
            ),
        ),
    )
}
