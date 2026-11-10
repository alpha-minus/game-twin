package com.twinlore.app.data.retroachievements

/**
 * UI model for RetroAchievements profile screen.
 */
// All fields carry defaults so Kotlin synthesizes a no-arg ctor — required for
// Gson to deserialize the cached JSON under R8/release without the Unsafe path.
data class RaProfileDisplay(
    val username: String = "",
    val totalPoints: Int = 0,
    val totalSoftcorePoints: Int = 0,
    val rank: Int? = null,
    val memberSince: String? = null,
    val recentAchievements: List<RaRecentAchievementDisplay> = emptyList(),
    val recentlyPlayed: List<RaRecentlyPlayedDisplay> = emptyList(),
    val lastSyncedAt: Long = 0L,
    /** Most-recent game id + its rich-presence ("now playing") string from the summary. */
    val lastGameId: Int? = null,
    val richPresenceMsg: String? = null
)

data class RaRecentAchievementDisplay(
    val gameId: Int = 0,
    val gameTitle: String? = null,
    val title: String = "",
    val points: Int = 0,
    val badgeName: String? = null,
    val date: String? = null,
    val hardcoreAchieved: Boolean = false
)

data class RaRecentlyPlayedDisplay(
    val gameId: Int = 0,
    val title: String? = null,
    val consoleName: String? = null,
    val numAchieved: Int? = null,
    val numPossible: Int? = null,
    val scoreAchieved: Int? = null,
    val possibleScore: Int? = null,
    /** Raw RA "yyyy-MM-dd HH:mm:ss" UTC string; used for last-played recency. */
    val lastPlayed: String? = null,
    /** RA game icon path (e.g. "/Images/056340.png"); prefix with the RA host for a URL. */
    val imageIcon: String? = null
)
