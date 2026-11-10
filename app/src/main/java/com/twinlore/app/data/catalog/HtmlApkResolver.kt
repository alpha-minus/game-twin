package com.twinlore.app.data.catalog

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HtmlApkResolver @Inject constructor(
    private val httpClient: OkHttpClient
) {

    suspend fun resolve(
        entryJson: JSONObject,
        supportedAbis: Array<String> = android.os.Build.SUPPORTED_ABIS
    ): ApkResolveResult = withContext(Dispatchers.IO) {
        try {
            val pageUrl = entryJson.optString("url")
            val settings = parseSettings(entryJson)
            val preferredIndex = entryJson.optInt("preferredApkIndex", 0)

            val html = httpGet(pageUrl, settings.requestHeaders)
                ?: return@withContext ApkResolveResult.Failure("Failed to fetch page: $pageUrl")

            val apkLinks = extractApkLinks(html, pageUrl).ifEmpty {
                traverseDirectoryListing(html, pageUrl, settings.requestHeaders) ?: emptyList()
            }
            if (apkLinks.isEmpty()) {
                return@withContext ApkResolveResult.Failure("No APK links found on page")
            }

            // Apply customLinkFilterRegex (matches against the full URL)
            val linkFiltered = if (settings.customLinkFilterRegex.isNotEmpty()) {
                val regex = Regex(settings.customLinkFilterRegex, RegexOption.IGNORE_CASE)
                apkLinks.filter { (_, url) -> regex.containsMatchIn(url) }
            } else {
                apkLinks
            }

            // Apply apkFilterRegEx (matches against the file name)
            val filtered = if (settings.apkFilterRegEx.isNotEmpty()) {
                val regex = Regex(settings.apkFilterRegEx, RegexOption.IGNORE_CASE)
                linkFiltered.filter { (name, _) ->
                    val matches = regex.containsMatchIn(name)
                    if (settings.invertAPKFilter) !matches else matches
                }
            } else {
                linkFiltered
            }

            // Apply arch filter
            val archFiltered = if (settings.autoApkFilterByArch) {
                val names = filtered.map { it.first }
                val accepted = ArchFilter.filter(names, supportedAbis)
                filtered.filter { it.first in accepted }
            } else {
                filtered
            }

            val finalList = archFiltered.ifEmpty { filtered }
            if (finalList.isEmpty()) {
                return@withContext ApkResolveResult.Failure("No APK matches filters on page")
            }

            val chosen = finalList.getOrElse(preferredIndex) { finalList.first() }
            ApkResolveResult.Success(
                downloadUrl = chosen.second,
                fileName = chosen.first,
                version = null
            )
        } catch (e: Exception) {
            ApkResolveResult.Failure(e.message ?: "Unknown error")
        }
    }

    /**
     * Extracts all href values ending in .apk from the HTML body.
     * Returns list of (fileName, absoluteUrl) pairs.
     */
    private fun extractApkLinks(html: String, baseUrl: String): List<Pair<String, String>> {
        val hrefRegex = Regex("""href\s*=\s*["']([^"']*\.apk)["']""", RegexOption.IGNORE_CASE)
        return hrefRegex.findAll(html).map { match ->
            val href = match.groupValues[1]
            val absoluteUrl = resolveUrl(baseUrl, href)
            val fileName = href.substringAfterLast('/')
            fileName to absoluteUrl
        }.toList()
    }

    private fun resolveUrl(base: String, href: String): String {
        if (href.startsWith("http://") || href.startsWith("https://")) return href
        if (href.startsWith("/")) {
            val origin = Regex("""(https?://[^/]+)""").find(base)?.groupValues?.get(1) ?: base
            return "$origin$href"
        }
        return "${base.trimEnd('/')}/$href"
    }

    private data class Settings(
        val apkFilterRegEx: String,
        val customLinkFilterRegex: String,
        val invertAPKFilter: Boolean,
        val autoApkFilterByArch: Boolean,
        val requestHeaders: List<Pair<String, String>>
    )

    private fun parseSettings(entryJson: JSONObject): Settings {
        val raw = entryJson.optString("additionalSettings", "{}")
        val s = JSONObject(raw)
        val headers = mutableListOf<Pair<String, String>>()
        val headerArray = s.optJSONArray("requestHeader")
        if (headerArray != null) {
            for (i in 0 until headerArray.length()) {
                val entry = headerArray.optJSONObject(i)
                val value = entry?.optString("requestHeader", "") ?: ""
                val colonIdx = value.indexOf(':')
                if (colonIdx > 0) {
                    headers += value.substring(0, colonIdx).trim() to
                        value.substring(colonIdx + 1).trim()
                }
            }
        }
        return Settings(
            apkFilterRegEx = s.optString("apkFilterRegEx", ""),
            customLinkFilterRegex = s.optString("customLinkFilterRegex", ""),
            invertAPKFilter = s.optBoolean("invertAPKFilter", false),
            autoApkFilterByArch = s.optBoolean("autoApkFilterByArch", true),
            requestHeaders = headers
        )
    }

    /**
     * When the initial page has no APK links, look for version-like directory
     * entries (e.g. "1.22.2/"), follow the latest one, and optionally descend
     * into a platform subdirectory like "android/".
     */
    private fun traverseDirectoryListing(
        html: String,
        baseUrl: String,
        extraHeaders: List<Pair<String, String>> = emptyList()
    ): List<Pair<String, String>>? {
        val dirRegex = Regex("""href\s*=\s*["']([^"']*/)["']""", RegexOption.IGNORE_CASE)
        val dirs = dirRegex.findAll(html).map { it.groupValues[1] }
            .filter { href ->
                val name = href.trimEnd('/').substringAfterLast('/')
                name.isNotEmpty() && name.first().isDigit()
            }
            .toList()

        if (dirs.isEmpty()) return null

        val latestDir = dirs.sortedWith(Comparator { a, b ->
            val av = versionKey(a)
            val bv = versionKey(b)
            for (i in 0 until maxOf(av.size, bv.size)) {
                val ai = av.getOrElse(i) { 0 }
                val bi = bv.getOrElse(i) { 0 }
                if (ai != bi) return@Comparator ai - bi
            }
            0
        }).lastOrNull() ?: return null

        val latestUrl = resolveUrl(baseUrl, latestDir)
        val dirHtml = httpGet(latestUrl, extraHeaders) ?: return null

        val apks = extractApkLinks(dirHtml, latestUrl)
        if (apks.isNotEmpty()) return apks

        // No APKs yet: look for a platform subdirectory (android/)
        val subDirRegex =
            Regex("""href\s*=\s*["']([^"']*android[^"']*/?)["']""", RegexOption.IGNORE_CASE)
        val androidDir = subDirRegex.find(dirHtml)?.groupValues?.get(1)
        if (androidDir != null) {
            val androidUrl = resolveUrl(latestUrl, androidDir)
            val androidHtml = httpGet(androidUrl, extraHeaders) ?: return null
            return extractApkLinks(androidHtml, androidUrl)
        }

        return null
    }

    private fun versionKey(href: String): List<Int> {
        val name = href.trimEnd('/').substringAfterLast('/')
        return name.split(Regex("[^0-9]+"))
            .filter { it.isNotEmpty() }
            .map { it.toIntOrNull() ?: 0 }
    }

    private fun httpGet(
        url: String,
        extraHeaders: List<Pair<String, String>> = emptyList()
    ): String? {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android) game-twin-apk-resolver/1")
        for ((name, value) in extraHeaders) {
            builder.header(name, value) // overrides default if same name
        }
        httpClient.newCall(builder.build()).execute().use { resp ->
            return if (resp.isSuccessful) resp.body?.string() else null
        }
    }
}
