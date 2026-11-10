package com.twinlore.app.data.license

import android.util.Log
import com.twinlore.app.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * API client for Lemon Squeezy License API
 * Handles HTTP communication with Lemon Squeezy's license endpoints
 */
@Singleton
class LemonSqueezyApiClient @Inject constructor() {
    companion object {
        private const val TAG = "LemonSqueezyApiClient"
        private const val BASE_URL = "https://api.lemonsqueezy.com/v1/licenses/"
        private const val CONNECT_TIMEOUT_SECONDS = 10L
        private const val READ_TIMEOUT_SECONDS = 30L
        private const val WRITE_TIMEOUT_SECONDS = 30L
    }
    
    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(
            OkHttpClient.Builder()
                .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                // Bodies on this endpoint carry the licence key and, on the way back, the
                // buyer's email and order. Never log them on a shipping build: Log calls are
                // not stripped by R8, so BODY here would put a customer's email and key into
                // logcat on every activation. Debug builds keep the full trace.
                .addInterceptor(
                    HttpLoggingInterceptor { message ->
                        Log.d(TAG, message)
                    }.apply {
                        level = if (BuildConfig.DEBUG) {
                            HttpLoggingInterceptor.Level.BODY
                        } else {
                            HttpLoggingInterceptor.Level.NONE
                        }
                    }
                )
                .addInterceptor { chain ->
                    try {
                        chain.proceed(chain.request())
                    } catch (e: Exception) {
                        // Status only. The message can echo the request, key included.
                        Log.e(TAG, "License request failed: ${e.javaClass.simpleName}")
                        throw e
                    }
                }
                .build()
        )
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    
    val api: LemonSqueezyApi = retrofit.create(LemonSqueezyApi::class.java)
}
