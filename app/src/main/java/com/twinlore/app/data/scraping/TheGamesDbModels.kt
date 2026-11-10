package com.twinlore.app.data.scraping

import com.google.gson.annotations.SerializedName

data class TgdbSearchResponse(
    val code: Int,
    val status: String,
    val data: TgdbSearchData,
)

data class TgdbSearchData(
    val count: Int,
    val games: List<TgdbGame>,
)

data class TgdbGame(
    val id: Long,
    @SerializedName("game_title") val gameTitle: String,
)

data class TgdbImagesResponse(
    val code: Int,
    val status: String,
    val data: TgdbImagesData,
)

data class TgdbImagesData(
    val count: Int,
    @SerializedName("base_url") val baseUrl: TgdbBaseUrl,
    val images: Map<String, List<TgdbImage>>,
)

data class TgdbBaseUrl(
    val original: String,
    val large: String?,
)

data class TgdbImage(
    val id: Long,
    val type: String,
    val side: String? = null,
    val filename: String,
    val resolution: String? = null,
)
