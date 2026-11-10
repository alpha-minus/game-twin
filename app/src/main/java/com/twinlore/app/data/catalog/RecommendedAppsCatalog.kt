package com.twinlore.app.data.catalog

import android.content.Context
import android.util.Log
import com.twinlore.app.domain.Category
import com.twinlore.app.domain.DeviceType
import com.twinlore.app.domain.RecommendedApp
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads the recommended-apps list for the current [DeviceType] from one of:
 *
 *  1. **Cached file** under `filesDir/catalog/<device>.json` — written by
 *     [refresh] after a successful fetch.
 *  2. **Bundled asset** under `assets/catalog/<device>.json` — initial copy of
 *     the upstream pack shipped in the APK, used until the user has refreshed.
 *
 * Refresh queries the GitHub Releases API for the latest tag of
 * `RJNY/Obtainium-Emulation-Pack`, downloads both standard and dual-screen
 * JSON assets, and writes them to the cache directory.
 *
 * Each entry's `about` text is taken verbatim from the upstream JSON's
 * `additionalSettings.about` field — no in-app remapping.
 */
@Singleton
class RecommendedAppsCatalog @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpClient: OkHttpClient
) {

    /**
     * Parsed list of apps for the requested [type], enriched with the console
     * mapping. Returns an empty list (and logs) if neither cache nor asset is
     * readable — should never happen in shipped builds because the assets are
     * baked in.
     */
    suspend fun load(type: DeviceType): List<RecommendedApp> = withContext(Dispatchers.IO) {
        val text = readCacheFile(type) ?: readBundledAsset(type)
        if (text == null) {
            Log.w(TAG, "Could not read catalog for $type from cache or assets")
            return@withContext emptyList()
        }
        try {
            parse(text)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse catalog JSON for $type", e)
            emptyList()
        }
    }

    sealed class RefreshResult {
        data class Success(val tag: String, val standardCount: Int, val dualCount: Int) : RefreshResult()
        data class Failure(val message: String) : RefreshResult()
    }

    /**
     * Fetches the latest release of `RJNY/Obtainium-Emulation-Pack`, downloads
     * both JSON asset files, and replaces the cached copies. After a successful
     * call, subsequent [load] invocations return the freshly fetched content.
     */
    suspend fun refresh(): RefreshResult = withContext(Dispatchers.IO) {
        try {
            val releaseInfo = httpGet(LATEST_RELEASE_API_URL)
                ?: return@withContext failure("No response from GitHub.")
            val release = JSONObject(releaseInfo)
            val tag = release.optString("tag_name", "?")
            val assets = release.getJSONArray("assets")
            var standardUrl: String? = null
            var dualUrl: String? = null
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.optString("name")
                val downloadUrl = asset.optString("browser_download_url")
                when {
                    name.contains("dual-screen", ignoreCase = true) -> dualUrl = downloadUrl
                    name.endsWith(".json", ignoreCase = true) -> standardUrl = downloadUrl
                }
            }
            if (standardUrl == null || dualUrl == null) {
                return@withContext failure("Release $tag is missing one of the JSON assets.")
            }
            val standardBody = httpGet(standardUrl)
                ?: return@withContext failure("Failed to download standard catalog.")
            val dualBody = httpGet(dualUrl)
                ?: return@withContext failure("Failed to download dual-screen catalog.")
            val standardCount = appCount(standardBody)
            val dualCount = appCount(dualBody)
            writeCacheFile(DeviceType.SingleScreen, standardBody)
            writeCacheFile(DeviceType.DualScreen, dualBody)
            RefreshResult.Success(tag = tag, standardCount = standardCount, dualCount = dualCount)
        } catch (e: Exception) {
            Log.e(TAG, "Refresh failed", e)
            failure(e.message ?: "Unknown error")
        }
    }

    // ---------------------------------------------------------------- internals

    private fun cacheFile(type: DeviceType): File {
        val dir = File(context.filesDir, "catalog").apply { mkdirs() }
        val name = when (type) {
            DeviceType.SingleScreen -> "single-screen.json"
            DeviceType.DualScreen -> "dual-screen.json"
        }
        return File(dir, name)
    }

    private fun readCacheFile(type: DeviceType): String? {
        val f = cacheFile(type)
        return if (f.isFile && f.length() > 0) f.readText(Charsets.UTF_8) else null
    }

    private fun writeCacheFile(type: DeviceType, body: String) {
        cacheFile(type).writeText(body, Charsets.UTF_8)
    }

    private fun readBundledAsset(type: DeviceType): String? = try {
        context.assets.open(type.assetFile).use { it.readBytes().toString(Charsets.UTF_8) }
    } catch (e: Exception) {
        Log.w(TAG, "Bundled asset missing: ${type.assetFile}", e)
        null
    }

    private fun httpGet(url: String): String? {
        val req = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        httpClient.newCall(req).execute().use { resp ->
            return if (resp.isSuccessful) resp.body?.string() else null
        }
    }

    private fun appCount(jsonBody: String): Int = try {
        JSONObject(jsonBody).optJSONArray("apps")?.length() ?: 0
    } catch (e: Exception) {
        0
    }

    private fun failure(message: String): RefreshResult.Failure {
        Log.w(TAG, "Refresh failure: $message")
        return RefreshResult.Failure(message)
    }

    /**
     * Parses the upstream JSON document. The upstream shape is
     * `{ "apps": [ { id, url, name, author, categories, additionalSettings, ... } ] }`.
     * `additionalSettings` is a stringified inner JSON containing the `about`
     * text we surface as the row's second line.
     */
    internal fun parse(jsonBody: String): List<RecommendedApp> {
        val root = JSONObject(jsonBody)
        val arr = root.optJSONArray("apps") ?: return emptyList()
        val out = ArrayList<RecommendedApp>(arr.length())
        for (i in 0 until arr.length()) {
            val entry = arr.getJSONObject(i)
            val id = entry.optString("id")
            if (id.isBlank()) continue
            val name = entry.optString("name", id)
            val author = entry.optString("author")
            val sourceUrl = entry.optString("url")
            val category = mapCategory(entry.optJSONArray("categories"))
                ?: Category.Utility
            val about = runCatching {
                JSONObject(entry.optString("additionalSettings", "{}")).optString("about")
            }.getOrDefault("")
            out.add(
                RecommendedApp(
                    id = id,
                    name = name,
                    author = author,
                    category = category,
                    sourceUrl = sourceUrl,
                    about = about,
                    entryJson = entry.toString()
                )
            )
        }
        return out
    }

    private fun mapCategory(arr: org.json.JSONArray?): Category? {
        if (arr == null) return null
        for (i in 0 until arr.length()) {
            when (arr.optString(i)) {
                "Emulator", "PC Emulation" -> return Category.Emulator
                "Frontend" -> return Category.Frontend
                "Streaming" -> return Category.Streaming
                "Track Only" -> return Category.TrackOnly
                "Utilities" -> return Category.Utility
            }
        }
        return null
    }

    companion object {
        private const val TAG = "RecommendedAppsCatalog"
        private const val LATEST_RELEASE_API_URL =
            "https://api.github.com/repos/RJNY/Obtainium-Emulation-Pack/releases/latest"
        private const val USER_AGENT = "game-twin-recommended-apps-catalog/1"
    }
}
