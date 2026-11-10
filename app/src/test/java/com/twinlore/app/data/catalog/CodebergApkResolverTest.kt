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

class CodebergApkResolverTest {

    private lateinit var server: MockWebServer
    private lateinit var resolver: CodebergApkResolver

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
        resolver = CodebergApkResolver(OkHttpClient.Builder().build())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `resolves APK from Codeberg release with filter and arch`() = runTest {
        val body = """[{
            "tag_name": "v0.0.58",
            "prerelease": false,
            "assets": [
                {"name": "eden-standard-arm64-v8a.apk", "browser_download_url": "${server.url("/dl/eden-standard-arm64-v8a.apk")}"},
                {"name": "eden-standard-x86_64.apk", "browser_download_url": "${server.url("/dl/eden-standard-x86_64.apk")}"},
                {"name": "eden-mainline-arm64-v8a.apk", "browser_download_url": "${server.url("/dl/eden-mainline-arm64-v8a.apk")}"}
            ]
        }]"""
        server.enqueue(MockResponse().setBody(body))

        val settings = JSONObject().apply {
            put("apkFilterRegEx", "standard")
            put("invertAPKFilter", false)
            put("autoApkFilterByArch", true)
            put("includePrereleases", false)
            put("filterReleaseTitlesByRegEx", "")
        }
        val entry = JSONObject().apply {
            put("url", server.url("/eden-emu/eden").toString())
            put("additionalSettings", settings.toString())
            put("preferredApkIndex", 0)
        }

        val result = resolver.resolve(entry, supportedAbis = arrayOf("arm64-v8a"))
        assertTrue("Expected Success but got $result", result is ApkResolveResult.Success)
        val success = result as ApkResolveResult.Success
        assertTrue(success.downloadUrl.contains("eden-standard-arm64-v8a.apk"))
        assertEquals("eden-standard-arm64-v8a.apk", success.fileName)
        assertEquals("v0.0.58", success.version)
    }

    @Test
    fun `returns Failure on 404`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        val entry = JSONObject().apply {
            put("url", server.url("/owner/repo").toString())
            put("additionalSettings", "{}")
            put("preferredApkIndex", 0)
        }
        val result = resolver.resolve(entry, supportedAbis = arrayOf("arm64-v8a"))
        assertTrue(result is ApkResolveResult.Failure)
    }
}
