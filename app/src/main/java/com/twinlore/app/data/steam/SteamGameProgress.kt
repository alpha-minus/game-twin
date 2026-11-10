package com.twinlore.app.data.steam

data class SteamAchievementDisplay(
    val apiName: String,
    val title: String,
    val description: String?,
    val iconUrl: String?,
    val iconGrayUrl: String?,
    val achieved: Boolean,
    /** UTC unix seconds when unlocked, null if locked. */
    val unlockTime: Long?,
    val hidden: Boolean,
    /** Global achievement rate (0.0–100.0), null if unavailable. */
    val globalPercent: Double? = null
)

data class SteamGameProgress(
    val steamAppId: Int,
    val gameTitle: String,
    val numAchievements: Int,
    val numUnlocked: Int,
    val achievements: List<SteamAchievementDisplay>,
    val recentUnlocks: List<SteamAchievementDisplay>,
    val lastSyncedAt: Long,
    val playtimeMinutes: Int? = null,
    /** https://cdn.akamai.steamstatic.com/steam/apps/{appid}/header.jpg */
    val headerImageUrl: String? = null
)
