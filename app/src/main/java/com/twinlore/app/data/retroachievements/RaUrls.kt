package com.twinlore.app.data.retroachievements

/**
 * Canonical RetroAchievements media endpoints. Every screen must build image URLs from these:
 * mixing hosts (s3-eu-west-1…, retroachievements.org, media.retroachievements.org) for the same
 * asset gives each variant its own Coil disk-cache entry, so the same badge is stored repeatedly.
 * media.retroachievements.org is the CDN the site itself serves from (no redirect hop).
 */
object RaUrls {
    const val BADGE_BASE = "https://media.retroachievements.org/Badge/"
    const val IMAGES_BASE = "https://media.retroachievements.org/Images/"

    /** Full URL of an achievement badge icon from its API `badgeName`. */
    fun badge(badgeName: String): String = "$BADGE_BASE$badgeName.png"

    /**
     * Full URL of a game's icon from the API's `ImageIcon` field, which arrives in three
     * shapes: a site-absolute path ("/Images/012345.png"), a bare filename with extension,
     * or a bare id without one.
     */
    fun gameImage(imageIcon: String): String {
        val icon = imageIcon.trim()
        return when {
            icon.startsWith("/") -> "https://retroachievements.org$icon"
            icon.contains('.') -> "$IMAGES_BASE$icon"
            else -> "$IMAGES_BASE$icon.png"
        }
    }
}
