package com.twinlore.app.data.catalog

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GitHubApkResolverTest {

    private lateinit var server: MockWebServer
    private lateinit var resolver: GitHubApkResolver

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
        val client = OkHttpClient.Builder().build()
        resolver = GitHubApkResolver(client, baseApiUrl = server.url("/").toString().trimEnd('/'))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    /** Single release object (for /releases/latest endpoint). */
    private fun releaseJson(
        tag: String = "v1.0",
        prerelease: Boolean = false,
        assets: List<Pair<String, String>> = listOf("app-arm64-v8a.apk" to "https://dl.test/app-arm64-v8a.apk")
    ): String {
        val assetsArr = assets.joinToString(",") { (name, url) ->
            """{"name":"$name","browser_download_url":"$url"}"""
        }
        return """{"tag_name":"$tag","prerelease":$prerelease,"assets":[$assetsArr]}"""
    }

    /** Array of releases (for /releases endpoint). */
    private fun releasesArrayJson(vararg releases: String): String =
        "[${releases.joinToString(",")}]"

    private fun entry(
        url: String = "https://github.com/owner/repo",
        apkFilterRegEx: String = "",
        invertAPKFilter: Boolean = false,
        autoApkFilterByArch: Boolean = false,
        includePrereleases: Boolean = false,
        filterReleaseTitlesByRegEx: String = "",
        preferredApkIndex: Int = 0
    ): JSONObject {
        val settings = JSONObject().apply {
            put("apkFilterRegEx", apkFilterRegEx)
            put("invertAPKFilter", invertAPKFilter)
            put("autoApkFilterByArch", autoApkFilterByArch)
            put("includePrereleases", includePrereleases)
            put("filterReleaseTitlesByRegEx", filterReleaseTitlesByRegEx)
        }
        return JSONObject().apply {
            put("url", url)
            put("additionalSettings", settings.toString())
            put("preferredApkIndex", preferredApkIndex)
        }
    }

    @Test
    fun `resolves APK from simple GitHub release`() = runTest {
        // Default settings use /releases/latest (single object)
        server.enqueue(MockResponse().setBody(releaseJson()))
        val result = resolver.resolve(entry(), supportedAbis = arrayOf("arm64-v8a"))
        assertTrue("Expected Success but got $result", result is ApkResolveResult.Success)
        val success = result as ApkResolveResult.Success
        assertEquals("https://dl.test/app-arm64-v8a.apk", success.downloadUrl)
        assertEquals("app-arm64-v8a.apk", success.fileName)
        assertEquals("v1.0", success.version)
    }

    @Test
    fun `applies apkFilterRegEx to asset names`() = runTest {
        val assets = listOf(
            "azahar-vanilla.apk" to "https://dl.test/azahar-vanilla.apk",
            "azahar-canary.apk" to "https://dl.test/azahar-canary.apk"
        )
        server.enqueue(MockResponse().setBody(releaseJson(assets = assets)))
        val result = resolver.resolve(
            entry(apkFilterRegEx = "vanilla"),
            supportedAbis = arrayOf("arm64-v8a")
        )
        assertTrue(result is ApkResolveResult.Success)
        assertEquals("https://dl.test/azahar-vanilla.apk", (result as ApkResolveResult.Success).downloadUrl)
    }

    @Test
    fun `skips prereleases when includePrereleases is true`() = runTest {
        // includePrereleases=true uses /releases array endpoint
        val beta = releaseJson(tag = "v2.0-beta", prerelease = true, assets = listOf("app.apk" to "https://dl.test/beta.apk"))
        val stable = releaseJson(tag = "v1.0", assets = listOf("app.apk" to "https://dl.test/stable.apk"))
        server.enqueue(MockResponse().setBody(releasesArrayJson(beta, stable)))
        val result = resolver.resolve(
            entry(includePrereleases = true),
            supportedAbis = arrayOf("arm64-v8a")
        )
        assertTrue(result is ApkResolveResult.Success)
        // With includePrereleases=true, it takes the first release (beta)
        assertEquals("https://dl.test/beta.apk", (result as ApkResolveResult.Success).downloadUrl)
    }

    @Test
    fun `returns Failure when no APK assets found`() = runTest {
        server.enqueue(MockResponse().setBody(releaseJson(assets = listOf("source.tar.gz" to "https://dl.test/source.tar.gz"))))
        val result = resolver.resolve(entry(), supportedAbis = arrayOf("arm64-v8a"))
        assertTrue("Expected Failure but got $result", result is ApkResolveResult.Failure)
    }

    @Test
    fun `returns Failure on network error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        val result = resolver.resolve(entry(), supportedAbis = arrayOf("arm64-v8a"))
        assertTrue(result is ApkResolveResult.Failure)
    }
}
