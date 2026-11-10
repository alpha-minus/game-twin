package com.twinlore.app.data.catalog

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HtmlApkResolverTest {

    private lateinit var server: MockWebServer
    private lateinit var resolver: HtmlApkResolver

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
        resolver = HtmlApkResolver(OkHttpClient.Builder().build())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `extracts APK links from HTML page`() = runTest {
        val html = """
            <html><body>
            <a href="/releases/2024/dolphin-5.0-21088-arm64-v8a.apk">ARM64</a>
            <a href="/releases/2024/dolphin-5.0-21088-x86_64.apk">x86_64</a>
            </body></html>
        """.trimIndent()
        server.enqueue(MockResponse().setBody(html))

        val entry = JSONObject().apply {
            put("url", server.url("/download").toString())
            put("additionalSettings", JSONObject().apply {
                put("apkFilterRegEx", "")
                put("invertAPKFilter", false)
                put("autoApkFilterByArch", true)
            }.toString())
            put("preferredApkIndex", 0)
        }

        val result = resolver.resolve(entry, supportedAbis = arrayOf("arm64-v8a"))
        assertTrue("Expected Success but got $result", result is ApkResolveResult.Success)
        val success = result as ApkResolveResult.Success
        assertTrue(success.downloadUrl.contains("arm64-v8a.apk"))
        assertTrue(success.fileName.contains("arm64-v8a.apk"))
    }

    @Test
    fun `applies apkFilterRegEx to HTML links`() = runTest {
        val html = """
            <html><body>
            <a href="/dl/app-free.apk">Free</a>
            <a href="/dl/app-gold.apk">Gold</a>
            </body></html>
        """.trimIndent()
        server.enqueue(MockResponse().setBody(html))

        val entry = JSONObject().apply {
            put("url", server.url("/page").toString())
            put("additionalSettings", JSONObject().apply {
                put("apkFilterRegEx", "gold")
                put("invertAPKFilter", false)
                put("autoApkFilterByArch", false)
            }.toString())
            put("preferredApkIndex", 0)
        }

        val result = resolver.resolve(entry, supportedAbis = arrayOf("arm64-v8a"))
        assertTrue(result is ApkResolveResult.Success)
        assertTrue((result as ApkResolveResult.Success).downloadUrl.contains("app-gold.apk"))
    }

    @Test
    fun `returns Failure when no APK links found`() = runTest {
        server.enqueue(MockResponse().setBody("<html><body>No downloads here</body></html>"))
        val entry = JSONObject().apply {
            put("url", server.url("/empty").toString())
            put("additionalSettings", "{}")
            put("preferredApkIndex", 0)
        }
        val result = resolver.resolve(entry, supportedAbis = arrayOf("arm64-v8a"))
        assertTrue(result is ApkResolveResult.Failure)
    }

    @Test
    fun `returns Failure on network error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        val entry = JSONObject().apply {
            put("url", server.url("/down").toString())
            put("additionalSettings", "{}")
            put("preferredApkIndex", 0)
        }
        val result = resolver.resolve(entry, supportedAbis = arrayOf("arm64-v8a"))
        assertTrue(result is ApkResolveResult.Failure)
    }

    @Test
    fun `traverses directory listing to find APK`() = runTest {
        // First request: directory listing page with version directories
        val dirListing = """
            <html><body>
            <a href="1.9.0/">1.9.0/</a>
            <a href="1.22.2/">1.22.2/</a>
            <a href="1.10.0/">1.10.0/</a>
            </body></html>
        """.trimIndent()
        server.enqueue(MockResponse().setBody(dirListing))

        // Second request: latest version directory (1.22.2/)
        val versionDir = """
            <html><body>
            <a href="android/">android/</a>
            <a href="windows/">windows/</a>
            </body></html>
        """.trimIndent()
        server.enqueue(MockResponse().setBody(versionDir))

        // Third request: android subdirectory
        val androidDir = """
            <html><body>
            <a href="RetroArch_aarch64.apk">ARM64</a>
            <a href="RetroArch.apk">32-bit</a>
            </body></html>
        """.trimIndent()
        server.enqueue(MockResponse().setBody(androidDir))

        val entry = JSONObject().apply {
            put("url", server.url("/stable").toString())
            put("additionalSettings", JSONObject().apply {
                put("apkFilterRegEx", "")
                put("invertAPKFilter", false)
                put("autoApkFilterByArch", true)
            }.toString())
            put("preferredApkIndex", 0)
        }

        val result = resolver.resolve(entry, supportedAbis = arrayOf("arm64-v8a"))
        assertTrue("Expected Success but got $result", result is ApkResolveResult.Success)
        val success = result as ApkResolveResult.Success
        assertTrue(success.fileName.contains("aarch64"))
    }
}
