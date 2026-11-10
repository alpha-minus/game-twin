package com.twinlore.app.data.scraping

import retrofit2.http.GET
import retrofit2.http.Query

interface TheGamesDbApi {
    @GET("v1/Games/ByGameName")
    suspend fun searchByName(
        @Query("apikey") apiKey: String,
        @Query("name") name: String,
        @Query("fields") fields: String = "",
    ): TgdbSearchResponse

    @GET("v1/Games/Images")
    suspend fun getImages(
        @Query("apikey") apiKey: String,
        @Query("games_id") gameId: Long,
    ): TgdbImagesResponse
}
