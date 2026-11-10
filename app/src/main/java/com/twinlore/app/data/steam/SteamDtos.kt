package com.twinlore.app.data.steam

import com.google.gson.annotations.SerializedName

// --------------- Vanity URL resolution ---------------

data class SteamVanityUrlResponse(val response: SteamVanityUrlResult)
data class SteamVanityUrlResult(
    val steamid: String? = null,
    val success: Int = 0
)

// --------------- Owned games ---------------

data class SteamOwnedGamesResponse(val response: SteamOwnedGamesResult)
data class SteamOwnedGamesResult(
    @SerializedName("game_count") val gameCount: Int = 0,
    val games: List<SteamOwnedGame>? = null
)
data class SteamOwnedGame(
    @SerializedName("appid") val appId: Int,
    val name: String? = null,
    @SerializedName("playtime_forever") val playtimeForever: Int = 0,
    @SerializedName("playtime_2weeks") val playtime2Weeks: Int? = null,
    @SerializedName("img_icon_url") val imgIconUrl: String? = null,
    @SerializedName("rtime_last_played") val rtimeLastPlayed: Long = 0
)

// --------------- Recently played ---------------

data class SteamRecentlyPlayedResponse(val response: SteamRecentlyPlayedResult)
data class SteamRecentlyPlayedResult(
    @SerializedName("total_count") val totalCount: Int = 0,
    val games: List<SteamRecentGame>? = null
)
data class SteamRecentGame(
    @SerializedName("appid") val appId: Int,
    val name: String? = null,
    @SerializedName("playtime_2weeks") val playtime2Weeks: Int = 0,
    @SerializedName("playtime_forever") val playtimeForever: Int = 0,
    @SerializedName("img_icon_url") val imgIconUrl: String? = null
)

// --------------- Player achievements ---------------

data class SteamPlayerAchievementsResponse(
    @SerializedName("playerstats") val playerStats: SteamPlayerStats?
)
data class SteamPlayerStats(
    val steamID: String? = null,
    val gameName: String? = null,
    val achievements: List<SteamPlayerAchievement>? = null,
    val success: Boolean = false
)
data class SteamPlayerAchievement(
    val apiname: String,
    val achieved: Int = 0,
    val unlocktime: Long = 0,
    /** Present when the request asked for a language; the schema's text otherwise. */
    val name: String? = null,
    val description: String? = null,
)

// --------------- Global achievement percentages ---------------

data class SteamGlobalAchievementPercentagesResponse(
    @SerializedName("achievementpercentages") val achievementPercentages: SteamGlobalAchievementPercentages?
)
data class SteamGlobalAchievementPercentages(
    val achievements: List<SteamGlobalAchievementPercent>? = null
)
data class SteamGlobalAchievementPercent(
    val name: String,
    val percent: Double = 0.0
)

// --------------- Game schema ---------------

data class SteamSchemaResponse(val game: SteamSchemaGame?)
data class SteamSchemaGame(
    val gameName: String? = null,
    val availableGameStats: SteamGameStats? = null
)
data class SteamGameStats(
    val achievements: List<SteamSchemaAchievement>? = null
)
data class SteamSchemaAchievement(
    val name: String,
    val defaultvalue: Int = 0,
    val displayName: String? = null,
    val hidden: Int = 0,
    val description: String? = null,
    val icon: String? = null,
    @SerializedName("icongray") val iconGray: String? = null
)
