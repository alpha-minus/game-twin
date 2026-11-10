package com.twinlore.app.data.network

import com.twinlore.app.data.steam.SteamApiKeyProvider
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Injects `?key=<effective>` into every request destined for api.steampowered.com.
 * Requests to other hosts pass through unchanged.
 *
 * `runBlocking` is acceptable here because the provider is trivially fast
 * after the first call (cached) and OkHttp interceptors already run on a
 * background dispatcher.
 */
@Singleton
class SteamKeyInterceptor @Inject constructor(
    private val keyProvider: SteamApiKeyProvider,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.host != "api.steampowered.com") return chain.proceed(request)

        val effective = runBlocking { keyProvider.effectiveKey() }
        if (effective.isBlank()) {
            // No key configured - let the upstream call fail naturally with a clear error.
            return chain.proceed(request)
        }

        val newUrl = request.url.newBuilder()
            .setQueryParameter("key", effective)
            .build()
        return chain.proceed(request.newBuilder().url(newUrl).build())
    }
}
