package com.twinlore.app.data.steam

/**
 * One curated Steam Community guide for a famous PC indie title.
 *
 * [guideId] is the guide's Steam `publishedfileid`, so [guideUrl] deep-links
 * straight to that specific guide. [appId] is the game's Steam app id, stored
 * for favicon / future use only — it is NOT used for matching (matching is by
 * [title], like every other catalog).
 */
data class SteamGuideEntry(
    val title: String,
    val guideId: String,
    val appId: Long? = null,
) {
    val guideUrl: String
        get() = "https://steamcommunity.com/sharedfiles/filedetails/?id=$guideId"
}
