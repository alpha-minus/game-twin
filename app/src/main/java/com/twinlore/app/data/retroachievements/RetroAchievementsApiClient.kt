package com.twinlore.app.data.retroachievements

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides RetroAchievements API client. No credentials are stored in the client;
 * callers pass u and y on each request. No credential or URL logging.
 */
@Singleton
class RetroAchievementsApiClient @Inject constructor() {

    private val okHttp = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttp)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val api: RetroAchievementsApi = retrofit.create(RetroAchievementsApi::class.java)

    companion object {
        private const val BASE_URL = "https://retroachievements.org/API/"
    }
}
