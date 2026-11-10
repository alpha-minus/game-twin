package com.twinlore.app.data.remote

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path

/**
 * Retrofit interface for SteamGridDB API v2
 * Documentation: https://www.steamgriddb.com/api/v2
 * 
 * Note: API may return direct list or wrapped in { "success": true, "data": [...] }
 * We parse ResponseBody manually to handle both cases
 */
interface SteamGridApi {
    /**
     * Search for games by name (autocomplete)
     * GET /search/autocomplete/{gameName}
     * 
     * @param gameName The game name to search for
     * @param authorization Bearer token for API authentication
     * @return ResponseBody to parse manually
     */
    @GET("search/autocomplete/{gameName}")
    suspend fun searchGame(
        @Path("gameName") gameName: String,
        @Header("Authorization") authorization: String
    ): ResponseBody
    
    /**
     * Get hero images for a game
     * GET /heroes/game/{gameId}
     * 
     * @param gameId The game ID from search
     * @param authorization Bearer token for API authentication
     * @return ResponseBody to parse manually
     */
    @GET("heroes/game/{gameId}")
    suspend fun getHeroes(
        @Path("gameId") gameId: Int,
        @Header("Authorization") authorization: String
    ): ResponseBody
    
    /**
     * Get logo images for a game
     * GET /logos/game/{gameId}
     * 
     * @param gameId The game ID from search
     * @param authorization Bearer token for API authentication
     * @return ResponseBody to parse manually
     */
    @GET("logos/game/{gameId}")
    suspend fun getLogos(
        @Path("gameId") gameId: Int,
        @Header("Authorization") authorization: String
    ): ResponseBody
    
    /**
     * Get grid images for a game
     * GET /grids/game/{gameId}
     * 
     * @param gameId The game ID from search
     * @param authorization Bearer token for API authentication
     * @return ResponseBody to parse manually
     */
    @GET("grids/game/{gameId}")
    suspend fun getGrids(
        @Path("gameId") gameId: Int,
        @Header("Authorization") authorization: String
    ): ResponseBody
}

/**
 * Search result from SteamGridDB autocomplete
 */
data class SteamGridSearchResult(
    val id: Int,
    val name: String,
    val types: List<String>? = null
)

/**
 * Asset from SteamGridDB (hero, logo, or grid)
 */
data class SteamGridAsset(
    val id: Int,
    val score: Int? = null,
    val style: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val url: String,
    val thumb: String? = null,
    val tags: List<String>? = null
)

/**
 * SteamGridDB API response wrapper
 * API returns responses in format: { "success": true, "data": [...] }
 */
data class SteamGridResponse<T>(
    val success: Boolean? = null,
    val data: T? = null,
    val errors: List<String>? = null
)

