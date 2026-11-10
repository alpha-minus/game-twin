package com.twinlore.app.data.catalog

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CodebergApkResolver @Inject constructor(
    private val httpClient: OkHttpClient
) {

    suspend fun resolve(
        entryJson: JSONObject,
        supportedAbis: Array<String> = android.os.Build.SUPPORTED_ABIS
    ): ApkResolveResult = withContext(Dispatchers.IO) {
        try {
            val url = entryJson.optString("url")
            val parts = extractParts(url)
                ?: return@withContext ApkResolveResult.Failure("Cannot parse owner/repo from: $url")

            val settings = parseSettings(entryJson)
            val preferredIndex = entryJson.optInt("preferredApkIndex", 0)

            val releasesUrl = "${parts.baseUrl}/api/v1/repos/${parts.owner}/${parts.repo}/releases"
            val (body, code) = httpGetWithCode(releasesUrl)
            if (body == null) {
                val msg = if (code in listOf(403, 429)) "API rate limit reached. Try again later."
                    else "Failed to fetch releases (HTTP $code)"
                return@withContext ApkResolveResult.Failure(msg)
            }

            val releases = JSONArray(body)
            val release = findRelease(releases, settings)
                ?: return@withContext ApkResolveResult.Failure("No suitable release found")

            val tag = release.optString("tag_name")
            val assets = release.getJSONArray("assets")
            val apkAssets = filterApkAssets(assets, settings, supportedAbis)

            if (apkAssets.isEmpty()) {
                return@withContext ApkResolveResult.Failure("No APK found matching filters in release $tag")
            }

            val chosen = apkAssets.getOrElse(preferredIndex) { apkAssets.first() }
            ApkResolveResult.Success(
                downloadUrl = chosen.second,
                fileName = chosen.first,
                version = tag
            )
        } catch (e: Exception) {
            ApkResolveResult.Failure(e.message ?: "Unknown error")
        }
    }

    private data class Parts(val baseUrl: String, val owner: String, val repo: String)

    private fun extractParts(url: String): Parts? {
        // Matches https://host.tld/owner/repo
        val regex = Regex("""(https?://[^/]+)/([^/]+)/([^/]+)""")
        val match = regex.find(url) ?: return null
        return Parts(match.groupValues[1], match.groupValues[2], match.groupValues[3])
    }

    private data class Settings(
        val includePrereleases: Boolean,
        val filterReleaseTitlesByRegEx: String,
        val apkFilterRegEx: String,
        val invertAPKFilter: Boolean,
        val autoApkFilterByArch: Boolean
    )

    private fun parseSettings(entryJson: JSONObject): Settings {
        val raw = entryJson.optString("additionalSettings", "{}")
        val s = JSONObject(raw)
        return Settings(
            includePrereleases = s.optBoolean("includePrereleases", false),
            filterReleaseTitlesByRegEx = s.optString("filterReleaseTitlesByRegEx", ""),
            apkFilterRegEx = s.optString("apkFilterRegEx", ""),
            invertAPKFilter = s.optBoolean("invertAPKFilter", false),
            autoApkFilterByArch = s.optBoolean("autoApkFilterByArch", true)
        )
    }

    private fun findRelease(releases: JSONArray, settings: Settings): JSONObject? {
        for (i in 0 until releases.length()) {
            val release = releases.getJSONObject(i)
            if (!settings.includePrereleases && release.optBoolean("prerelease", false)) continue
            if (settings.filterReleaseTitlesByRegEx.isNotEmpty()) {
                val title = release.optString("tag_name") + " " + release.optString("name")
                if (!Regex(settings.filterReleaseTitlesByRegEx).containsMatchIn(title)) continue
            }
            return release
        }
        return null
    }

    private fun filterApkAssets(
        assets: JSONArray,
        settings: Settings,
        supportedAbis: Array<String>
    ): List<Pair<String, String>> {
        val apks = mutableListOf<Pair<String, String>>()
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            val name = asset.optString("name")
            val downloadUrl = asset.optString("browser_download_url")
            if (!name.endsWith(".apk", ignoreCase = true)) continue
            apks.add(name to downloadUrl)
        }

        val filtered = if (settings.apkFilterRegEx.isNotEmpty()) {
            val regex = Regex(settings.apkFilterRegEx, RegexOption.IGNORE_CASE)
            apks.filter { (name, _) ->
                val matches = regex.containsMatchIn(name)
                if (settings.invertAPKFilter) !matches else matches
            }
        } else {
            apks
        }

        return if (settings.autoApkFilterByArch) {
            val names = filtered.map { it.first }
            val accepted = ArchFilter.filter(names, supportedAbis)
            filtered.filter { it.first in accepted }
        } else {
            filtered
        }
    }

    private fun httpGetWithCode(url: String): Pair<String?, Int> {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "game-twin-apk-resolver/1")
            .build()
        httpClient.newCall(req).execute().use { resp ->
            return if (resp.isSuccessful) (resp.body?.string() to resp.code) else (null to resp.code)
        }
    }
}
