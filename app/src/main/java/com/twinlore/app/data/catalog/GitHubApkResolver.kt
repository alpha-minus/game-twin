package com.twinlore.app.data.catalog

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "GitHubApkResolver"

@Singleton
class GitHubApkResolver @Inject constructor(
    private val httpClient: OkHttpClient,
    private val baseApiUrl: String = "https://api.github.com"
) : ApkResolver {

    override suspend fun resolve(
        entryJson: JSONObject,
        supportedAbis: Array<String>
    ): ApkResolveResult = withContext(Dispatchers.IO) {
        try {
            val url = entryJson.optString("url")
            val (owner, repo) = extractOwnerRepo(url)
                ?: return@withContext ApkResolveResult.Failure("Cannot parse GitHub owner/repo from: $url")

            val settings = parseSettings(entryJson)
            val preferredIndex = entryJson.optInt("preferredApkIndex", 0)

            // Try /releases/latest first (single API call, less rate-limit pressure).
            // Fall back to /releases if latest is a prerelease we need to skip or
            // if the release title filter doesn't match.
            val release: JSONObject
            if (!settings.includePrereleases && settings.filterReleaseTitlesByRegEx.isEmpty()) {
                val latestUrl = "$baseApiUrl/repos/$owner/$repo/releases/latest"
                val (latestBody, latestCode) = httpGetWithCode(latestUrl)
                if (latestBody != null) {
                    release = JSONObject(latestBody)
                } else {
                    return@withContext ApkResolveResult.Failure(httpErrorMessage(latestCode, owner, repo))
                }
            } else {
                val releasesUrl = "$baseApiUrl/repos/$owner/$repo/releases"
                val (body, code) = httpGetWithCode(releasesUrl)
                if (body == null) {
                    return@withContext ApkResolveResult.Failure(httpErrorMessage(code, owner, repo))
                }
                val releases = JSONArray(body)
                release = findRelease(releases, settings)
                    ?: return@withContext ApkResolveResult.Failure("No suitable release found for $owner/$repo")
            }

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
        } catch (e: java.net.UnknownHostException) {
            ApkResolveResult.Failure("No internet connection")
        } catch (e: java.net.SocketTimeoutException) {
            ApkResolveResult.Failure("Connection timed out")
        } catch (e: Exception) {
            ApkResolveResult.Failure(e.message ?: "Unknown error")
        }
    }

    private fun extractOwnerRepo(url: String): Pair<String, String>? {
        val regex = Regex("""github\.com/([^/]+)/([^/]+)""")
        val match = regex.find(url) ?: return null
        return match.groupValues[1] to match.groupValues[2]
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
            if (!resp.isSuccessful) {
                Log.w(TAG, "HTTP ${resp.code} for $url")
            }
            return if (resp.isSuccessful) (resp.body?.string() to resp.code) else (null to resp.code)
        }
    }

    private fun httpErrorMessage(code: Int, owner: String, repo: String): String = when (code) {
        403, 429 -> "GitHub API rate limit reached. Try again after 1 hour."
        404 -> "Repository not found: $owner/$repo"
        else -> "Failed to fetch releases for $owner/$repo (HTTP $code)"
    }
}
