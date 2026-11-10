package com.twinlore.app.data.steam

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Retrofit interface for Steam Web API.
 * Base URL: https://api.steampowered.com/
 * Auth: `key` query parameter is injected by SteamKeyInterceptor on the
 * OkHttp client. Do not log credentials.
 */
interface SteamApi {

    @GET("ISteamUser/ResolveVanityURL/v1/")
    suspend fun resolveVanityURL(
        @Query("vanityurl") vanityUrl: String,
    ): SteamVanityUrlResponse

    @GET("IPlayerService/GetOwnedGames/v1/")
    suspend fun getOwnedGames(
        @Query("steamid") steamId: String,
        @Query("include_appinfo") includeAppInfo: Int = 1,
        @Query("format") format: String = "json",
    ): SteamOwnedGamesResponse

    @GET("IPlayerService/GetRecentlyPlayedGames/v1/")
    suspend fun getRecentlyPlayedGames(
        @Query("steamid") steamId: String,
        @Query("format") format: String = "json",
    ): SteamRecentlyPlayedResponse

    @GET("ISteamUserStats/GetPlayerAchievements/v1/")
    suspend fun getPlayerAchievements(
        @Query("steamid") steamId: String,
        @Query("appid") appId: Int,
        @Query("l") language: String = "english",
    ): SteamPlayerAchievementsResponse

    @GET("ISteamUserStats/GetGlobalAchievementPercentagesForApp/v2/")
    suspend fun getGlobalAchievementPercentages(
        @Query("gameid") appId: Int,
    ): SteamGlobalAchievementPercentagesResponse

    @GET("ISteamUserStats/GetSchemaForGame/v2/")
    suspend fun getSchemaForGame(
        @Query("appid") appId: Int,
        @Query("l") language: String = "english",
    ): SteamSchemaResponse
}
