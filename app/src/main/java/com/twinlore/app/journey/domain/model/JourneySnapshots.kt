package com.twinlore.app.journey.domain.model

// Trimmed cache payloads. Serialized with Gson using these exact field names.
data class AchSnapshot(
    val id: Int, val title: String, val description: String,
    val points: Int, val badgeName: String, val type: String?,
    val dateEarned: String?, val dateEarnedHardcore: String?, val numAwarded: Int,
)

data class GameSnapshot(
    val gameId: Int, val title: String, val iconPath: String?,
    val artPaths: List<String>, val players: Int,
    val achievements: List<AchSnapshot>,
    // Nullable with defaults on purpose: entries cached before these existed deserialize with the
    // fields absent, and Gson leaves them null rather than failing the whole snapshot.
    val titlePath: String? = null, val boxArtPath: String? = null,
    /** The in-game screenshot by name, for the export-background picker. Same caching rule. */
    val ingamePath: String? = null,
    /**
     * How long this player has had the game open, in SECONDS, as RetroAchievements reports it
     * (`UserTotalPlaytime` on API_GetUserGameInfoAndUserProgress - the same field, in the same
     * unit, that `RaGameCacheRepository` divides by 60 to store minutes for the RA drawer).
     *
     * Null is not zero. A snapshot cached before this field existed deserializes without it, and a
     * journey that shows "played 0h 0m" over a game somebody mastered is worse than one that does
     * not mention the hours at all - so the header drops that half of its line rather than
     * inventing a number (see `JourneyStripText.gameHeader`).
     */
    val userTotalPlaytimeSeconds: Int? = null,
)

data class CompletionRow(
    val gameId: Int, val title: String, val iconPath: String?,
    val numAwarded: Int, val numAwardedHardcore: Int,
    val mostRecentAwardedDate: String?, val highestAwardKind: String?, val highestAwardDate: String?,
)
