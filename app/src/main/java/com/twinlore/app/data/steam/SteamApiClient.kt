package com.twinlore.app.data.steam

import com.twinlore.app.data.network.SteamKeyInterceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides Steam Web API client. The effective API key is injected
 * per-request by SteamKeyInterceptor (resolving user-override or
 * bundled-decoded default). No credential or URL logging.
 */
@Singleton
class SteamApiClient @Inject constructor(
    steamKeyInterceptor: SteamKeyInterceptor,
) {

    private val okHttp = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .addInterceptor(steamKeyInterceptor)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttp)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val api: SteamApi = retrofit.create(SteamApi::class.java)

    companion object {
        private const val BASE_URL = "https://api.steampowered.com/"
    }
}
