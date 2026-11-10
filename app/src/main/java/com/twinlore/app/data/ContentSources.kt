package com.twinlore.app.data

/**
 * Catalog of content sources the app routes users to. Tracks favicon host
 * (for Coil/S2 loading) and a support URL the user can open to donate,
 * subscribe, or otherwise contribute to the source maintainer.
 *
 * Used by picker dropdowns (MapBrowseMenu / GuideFindMenu) and the VGMaps
 * full-screen picker. New sources can be added by appending to [all].
 */
data class ContentSource(
    val id: String,
    val displayName: String,
    val faviconHost: String,
    val homeUrl: String,
    /** Optional support URL (donate / subscribe / contribute). Falls back to homeUrl if null. */
    val supportUrl: String? = null,
)

object ContentSources {
    val vgmaps = ContentSource(
        id = "vgmaps",
        displayName = "VGMaps",
        faviconHost = "vgmaps.com",
        homeUrl = "https://www.vgmaps.com/",
        supportUrl = "https://www.paypal.com/donate/?hosted_button_id=RDVP3ST9MXX6J",
    )

    val neoseeker = ContentSource(
        id = "neoseeker",
        displayName = "Neoseeker",
        faviconHost = "neoseeker.com",
        homeUrl = "https://www.neoseeker.com/",
        supportUrl = "https://www.neoseeker.com/nplus/?utm_source=mobile_sidenav&utm_medium=web&utm_campaign=nplus-upgrade",
    )

    val gamefaqs = ContentSource(
        id = "gamefaqs",
        displayName = "GameFAQs",
        faviconHost = "gamefaqs.gamespot.com",
        homeUrl = "https://gamefaqs.gamespot.com/",
        supportUrl = "https://gamefaqs.gamespot.com/contribute",
    )

    val omegaMetroid = ContentSource(
        id = "omegametroid",
        displayName = "Omega Metroid",
        faviconHost = "omegametroid.com",
        homeUrl = "https://www.omegametroid.com/",
        supportUrl = "https://www.patreon.com/spnet",
    )

    val zeldaDungeon = ContentSource(
        id = "zeldadungeon",
        displayName = "Zelda Dungeon",
        faviconHost = "zeldadungeon.net",
        homeUrl = "https://www.zeldadungeon.net/",
        supportUrl = "https://www.zeldadungeon.net/staff-openings/",
    )

    val bulbapedia = ContentSource(
        id = "bulbapedia",
        displayName = "Bulbapedia",
        faviconHost = "bulbapedia.bulbagarden.net",
        homeUrl = "https://bulbapedia.bulbagarden.net/",
        supportUrl = "https://bulbapedia.bulbagarden.net/wiki/Special:CreateAccount",
    )

    val strategywiki = ContentSource(
        id = "strategywiki",
        displayName = "StrategyWiki",
        faviconHost = "strategywiki.org",
        homeUrl = "https://strategywiki.org/",
        // StrategyWiki is a contributor-run wiki — "support" means joining to
        // edit, so the chip points at account creation (same as Bulbapedia).
        supportUrl = "https://strategywiki.org/wiki/Special:CreateAccount",
    )

    val ign = ContentSource(
        id = "ign",
        displayName = "IGN",
        faviconHost = "ign.com",
        homeUrl = "https://www.ign.com/",
        // IGN's subscription / ad-free membership.
        supportUrl = "https://www.ign.com/plus",
    )

    val fireEmblemWod = ContentSource(
        id = "fireemblemwod",
        displayName = "Fire Emblem WoD",
        // The GuideFindMenu series row looks this up by the host of the series'
        // hubUrl (www.fireemblemwod.com), so the favicon host carries the www.
        faviconHost = "www.fireemblemwod.com",
        homeUrl = "https://www.fireemblemwod.com/english.htm",
        // Fan site funded by donations — the maintainer's direct PayPal.
        supportUrl = "https://www.paypal.com/donate?token=iDAqCo5LRh9GHFQq5ZR2ylo4xwHKowng-C3TY-QoT5T5Sgt3lsuPZj-31XPv6Jr4FeoILvY0VZU7Zf88",
    )

    val all: List<ContentSource> = listOf(
        vgmaps, neoseeker, gamefaqs, omegaMetroid, zeldaDungeon, bulbapedia, strategywiki, ign,
        fireEmblemWod,
    )

    /** Lookup by favicon host (the existing identifier the picker rows already carry). */
    fun byFaviconHost(host: String): ContentSource? = all.firstOrNull { it.faviconHost == host }
}
