package com.twinlore.app.data.scraping

import android.util.Log
import com.twinlore.app.data.SettingsRepository
import com.twinlore.app.data.remote.SteamGridApi
import com.twinlore.app.data.remote.SteamGridAsset
import com.twinlore.app.data.remote.SteamGridResponse
import com.twinlore.app.data.remote.SteamGridSearchResult
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import okhttp3.ResponseBody
import com.twinlore.app.domain.AssetType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Client for SteamGridDB API v2
 * Handles HTTP communication and media downloads
 * Documentation: https://www.steamgriddb.com/api/v2
 */
@Singleton
class SteamGridClient @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ArtworkScraper {

    override val providerName: String = "steamgriddb"

    override suspend fun scrape(
        name: String,
        types: Set<ArtworkType>,
    ): ScrapedArtwork? {
        val userKey = settingsRepository.steamGridApiKey.first()
        val effectiveKey = userKey ?: bundledKey
        val keySource = if (userKey != null) "USER_OVERRIDE" else "BUNDLED_DEFAULT"
        Log.d(TAG, "scrape() keySource=$keySource")
        val authorization = try {
            "Bearer $effectiveKey"
        } catch (e: Throwable) {
            throw ScrapeError.Unknown(e)
        }
        return try {
            val searchBody = api.searchGame(name, authorization)
            val results = parseResponseBody<SteamGridSearchResult>(searchBody)
            if (results.isEmpty()) return null
            val top = results.first()

            val grids = if (ArtworkType.GRID in types) {
                parseResponseBody<SteamGridAsset>(api.getGrids(top.id, authorization))
            } else emptyList()
            val heroes = if (ArtworkType.HERO in types) {
                parseResponseBody<SteamGridAsset>(api.getHeroes(top.id, authorization))
            } else emptyList()
            val logos = if (ArtworkType.LOGO in types) {
                parseResponseBody<SteamGridAsset>(api.getLogos(top.id, authorization))
            } else emptyList()

            ScrapedArtwork(
                provider = providerName,
                gameId = top.id.toString(),
                gameName = top.name,
                grids = grids.map { AssetCandidate(it.url, it.width, it.height, it.thumb) },
                heroes = heroes.map { AssetCandidate(it.url, it.width, it.height, it.thumb) },
                logos = logos.map { AssetCandidate(it.url, it.width, it.height, it.thumb) },
            )
        } catch (e: retrofit2.HttpException) {
            Log.w(TAG, "HTTP ${e.code()} with keySource=$keySource")
            when (e.code()) {
                404 -> null // 404 on assets is "none", not a hard error
                429 -> throw ScrapeError.RateLimited
                401 -> throw ScrapeError.InvalidKey("SteamGridDB")
                403 -> throw ScrapeError.QuotaExceeded("SteamGridDB")
                502, 503, 504 -> throw ScrapeError.Unavailable
                else -> throw ScrapeError.Unknown(e)
            }
        } catch (e: java.io.IOException) {
            Log.w(TAG, "IOException with keySource=$keySource: ${e.message}")
            throw ScrapeError.Unavailable
        } catch (e: ScrapeError) {
            throw e
        } catch (e: Throwable) {
            Log.w(TAG, "Unexpected error with keySource=$keySource: ${e.message}")
            throw ScrapeError.Unknown(e)
        }
    }

    companion object {
        private const val TAG = "SteamGridClient"
        private const val BASE_URL = "https://www.steamgriddb.com/api/v2/"
    }

    private val bundledKey: String by lazy {
        com.twinlore.app.data.security.ObfuscatedKey.decode(
            com.twinlore.app.BuildConfig.STEAMGRIDDB_API_KEY_ENCODED,
            com.twinlore.app.BuildConfig.KEY_OBFUSCATION_MASK,
        )
    }
    
    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(
            OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(5, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    val request = chain.request()
                    val startTime = System.currentTimeMillis()
                    Log.d(TAG, "[API] ${request.method} ${request.url}")
                    
                    val response = chain.proceed(request)
                    val duration = System.currentTimeMillis() - startTime
                    
                    if (response.isSuccessful) {
                        Log.d(TAG, "[API] SUCCESS ${response.code} (${duration}ms)")
                    } else {
                        Log.e(TAG, "[API] ERROR ${response.code} ${response.message} (${duration}ms)")
                        // Only peek error response body (doesn't consume it)
                        try {
                            val errorBody = response.peekBody(500).string()
                            Log.e(TAG, "[API] Error body: $errorBody")
                        } catch (e: Exception) {
                            // Ignore peek errors
                        }
                    }
                    
                    response
                }
                .build()
        )
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    
    private val api: SteamGridApi = retrofit.create(SteamGridApi::class.java)
    private val gson = Gson()
    
    /**
     * Parse ResponseBody to extract list (handles both direct list and wrapped response)
     */
    private inline fun <reified T> parseResponseBody(responseBody: ResponseBody): List<T> {
        return try {
            val jsonString = responseBody.string()
            val jsonElement = JsonParser.parseString(jsonString)

            when {
                jsonElement.isJsonArray -> {
                    // Direct list: [...]
                    val type = object : TypeToken<List<T>>() {}.type
                    val result = gson.fromJson<List<T>>(jsonElement, type)
                    result
                }
                jsonElement.isJsonObject -> {
                    // Wrapped: { "success": true, "data": [...] }
                    val jsonObject = jsonElement.asJsonObject
                    if (jsonObject.has("data")) {
                        val dataElement = jsonObject.get("data")
                        if (dataElement.isJsonArray) {
                            val type = object : TypeToken<List<T>>() {}.type
                            gson.fromJson<List<T>>(dataElement, type)
                        } else {
                            Log.e(TAG, "Response has 'data' field but it's not an array")
                            emptyList()
                        }
                    } else {
                        Log.e(TAG, "Response object doesn't have 'data' field")
                        emptyList()
                    }
                }
                else -> {
                    Log.e(TAG, "Unexpected JSON format")
                    emptyList()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing response body: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * Get API key from settings or use default
     */
    private suspend fun getApiKey(): String {
        return settingsRepository.steamGridApiKey.first() ?: bundledKey
    }
    
    /**
     * Get authorization header value
     */
    private suspend fun getAuthorizationHeader(): String {
        val apiKey = getApiKey()
        return "Bearer $apiKey"
    }
    
    /**
     * Search for game by name
     * @param gameName The game name to search for
     * @return List of matching games, or empty list on error
     */
    suspend fun searchGame(gameName: String): List<SteamGridSearchResult> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Searching for game: '$gameName'")
            val authorization = getAuthorizationHeader()
            val responseBody = api.searchGame(gameName, authorization)

            val results = parseResponseBody<SteamGridSearchResult>(responseBody)
            Log.d(TAG, "Found ${results.size} game(s) matching '$gameName'")
            results.forEachIndexed { index, result ->
                Log.d(TAG, "  Result ${index + 1}: ID=${result.id}, Name=${result.name}")
            }
            results
        } catch (e: Exception) {
            Log.e(TAG, "Error searching for game: '$gameName'", e)
            emptyList()
        }
    }
    
    /**
     * Get hero images for a game
     * @param gameId The game ID from search
     * @return List of hero images, or empty list on error
     */
    suspend fun getHeroes(gameId: Int): List<SteamGridAsset> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fetching heroes for game ID: $gameId")
            val authorization = getAuthorizationHeader()
            val responseBody = api.getHeroes(gameId, authorization)
            Log.d(TAG, "API Response code: ${responseBody.contentLength()} bytes")
            
            val assets = parseResponseBody<SteamGridAsset>(responseBody)
            Log.d(TAG, "Found ${assets.size} hero image(s) for game ID: $gameId")
            assets
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching heroes for game ID: $gameId", e)
            Log.e(TAG, "Exception type: ${e.javaClass.name}")
            Log.e(TAG, "Exception message: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Get logo images for a game
     * @param gameId The game ID from search
     * @return List of logo images, or empty list on error
     */
    suspend fun getLogos(gameId: Int): List<SteamGridAsset> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fetching logos for game ID: $gameId")
            val authorization = getAuthorizationHeader()
            val responseBody = api.getLogos(gameId, authorization)
            Log.d(TAG, "API Response code: ${responseBody.contentLength()} bytes")
            
            val assets = parseResponseBody<SteamGridAsset>(responseBody)
            Log.d(TAG, "Found ${assets.size} logo image(s) for game ID: $gameId")
            assets.forEachIndexed { index, asset ->
                Log.d(TAG, "  Logo ${index + 1}: ID=${asset.id}, URL=${asset.url}")
            }
            assets
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching logos for game ID: $gameId", e)
            Log.e(TAG, "Exception type: ${e.javaClass.name}")
            Log.e(TAG, "Exception message: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Get grid images for a game
     * @param gameId The game ID from search
     * @return List of grid images, or empty list on error
     */
    suspend fun getGrids(gameId: Int): List<SteamGridAsset> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fetching grids for game ID: $gameId")
            val authorization = getAuthorizationHeader()
            val responseBody = api.getGrids(gameId, authorization)
            Log.d(TAG, "API Response code: ${responseBody.contentLength()} bytes")
            
            val assets = parseResponseBody<SteamGridAsset>(responseBody)
            Log.d(TAG, "Found ${assets.size} grid image(s) for game ID: $gameId")
            assets
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching grids for game ID: $gameId", e)
            Log.e(TAG, "Exception type: ${e.javaClass.name}")
            Log.e(TAG, "Exception message: ${e.message}")
            emptyList()
        }
    }
    
}

