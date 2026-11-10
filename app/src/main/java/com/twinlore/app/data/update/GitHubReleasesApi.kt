package com.twinlore.app.data.update

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET

data class GitHubAsset(
    @SerializedName("name") val name: String,
    @SerializedName("browser_download_url") val browserDownloadUrl: String
)

data class GitHubRelease(
    @SerializedName("tag_name") val tagName: String,
    @SerializedName("body") val body: String?,
    @SerializedName("assets") val assets: List<GitHubAsset>
)

interface GitHubReleasesApi {
    @GET("repos/alpha-minus/game-twin/releases/latest")
    suspend fun getLatestRelease(): GitHubRelease
}
