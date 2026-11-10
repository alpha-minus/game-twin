package com.twinlore.app.data.retroachievements

/**
 * UI-friendly achievement item for the drawer (merged from API achievement + user progress).
 */
data class RaAchievementDisplay(
    val id: Int,
    val title: String,
    val description: String,
    val points: Int,
    /** RetroPoints ("white points") the achievement is worth; from API TrueRatio. */
    val trueRatio: Int = 0,
    val badgeName: String,
    /** Rarity: percent of players who earned (softcore) = 100 * numAwarded / totalPlayers. */
    val rarityPercent: Double?,
    /** Rarity: percent of players who earned hardcore = 100 * numAwardedHardcore / totalPlayers. */
    val rarityHardcorePercent: Double? = null,
    val dateAwarded: String?,
    val hardcoreAchieved: Boolean,
    /** Display order from API for default sort. */
    val displayOrder: Int = 0,
    /** "progression", "win_condition", "missable", or null. */
    val type: String? = null,
    /** Number of players who earned (softcore); for "N players" display. */
    val numAwarded: Int? = null,
    /** Number of players who earned hardcore. */
    val numAwardedHardcore: Int? = null,
    val author: String? = null
)

/**
 * Game progress summary for the achievement drawer.
 */
data class RaGameProgress(
    val raGameId: Int,
    val gameTitle: String,
    val totalPoints: Int,
    val numAchievements: Int,
    val userPoints: Int,
    val userNumAchieved: Int,
    val userHardcorePoints: Int,
    val userNumHardcore: Int,
    val recentUnlocks: List<RaAchievementDisplay>,
    val achievements: List<RaAchievementDisplay>,
    val lastSyncedAt: Long,
    val consoleName: String? = null,
    val gameImageIcon: String? = null,
    /** Time spent on this game by the user, in minutes. */
    val userTotalPlaytimeMinutes: Int? = null,
    /** Distinct players (softcore) who have played this game. */
    val numDistinctPlayers: Int? = null,
    /** User completion percentage string (e.g. "45.28%"). */
    val userCompletionPercent: String? = null,
    val userCompletionHardcorePercent: String? = null,
    /** User's rank by points for this game (from API_GetUserGameRankAndScore). */
    val userRank: Int? = null,
    /** Highest completion award: "mastered", "completed", "beaten-hardcore", "beaten-softcore", or null (from API_GetUserCompletionProgress). */
    val highestAwardKind: String? = null
)
