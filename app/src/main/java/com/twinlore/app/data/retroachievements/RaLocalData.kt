package com.twinlore.app.data.retroachievements

import com.google.gson.annotations.SerializedName

/** Console entry from data/retroachievements/consoles.json */
data class RaLocalConsole(
    @SerializedName("ID") val id: Int,
    @SerializedName("Name") val name: String,
    @SerializedName("Active") val active: Boolean? = null,
    @SerializedName("IsGameSystem") val isGameSystem: Boolean? = null
)

/** Game entry inside data/retroachievements/games/<id>.json "games" array */
data class RaLocalGame(
    @SerializedName("ID") val id: Int,
    @SerializedName("Title") val title: String,
    @SerializedName("ConsoleID") val consoleId: Int? = null,
    @SerializedName("ConsoleName") val consoleName: String? = null,
    @SerializedName("NumAchievements") val numAchievements: Int? = null,
    @SerializedName("Points") val points: Int? = null
)

/** Root of data/retroachievements/games/<id>.json */
data class RaLocalGamesFile(
    @SerializedName("consoleId") val consoleId: Int? = null,
    @SerializedName("consoleName") val consoleName: String? = null,
    @SerializedName("games") val games: List<RaLocalGame>? = null
)
