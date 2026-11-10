package com.twinlore.app.journey.ui

/**
 * RetroAchievements media URLs the Journey screen needs beyond what [com.twinlore.app.data
 * .retroachievements.RaUrls] already covers (badges, game icons): rival avatars, and a
 * catch-all for any other media-relative path the domain layer hands back.
 */
object JourneyUrls {
    private const val MEDIA_BASE = "https://media.retroachievements.org"

    /** A player's avatar from their RetroAchievements username. */
    fun userPic(name: String): String = "$MEDIA_BASE/UserPic/$name.png"

    /** Full URL for a media-base-relative path (e.g. "/Images/012345.png"). */
    fun media(path: String): String {
        val trimmed = path.trim()
        return if (trimmed.startsWith("http")) trimmed else "$MEDIA_BASE$trimmed"
    }
}
