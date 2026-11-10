package com.twinlore.app.data.catalog

import android.app.DownloadManager
import android.content.Context
import com.twinlore.app.domain.Category
import com.twinlore.app.domain.RecommendedApp
import com.twinlore.app.ui.screens.catalog.ApkDownloadState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File

class ApkDownloadManagerTest {

    private lateinit var server: MockWebServer
    private lateinit var manager: ApkDownloadManager
    private lateinit var tempDir: File
    private val context = mock<Context>()
    private val downloadManagerMock = mock<DownloadManager>()

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
        tempDir = File(System.getProperty("java.io.tmpdir"), "apk-test-${System.nanoTime()}")
        tempDir.mkdirs()
        whenever(context.cacheDir).thenReturn(tempDir)
        whenever(context.getSystemService(Context.DOWNLOAD_SERVICE)).thenReturn(downloadManagerMock)
        val client = OkHttpClient.Builder().build()
        val githubResolver = GitHubApkResolver(client, baseApiUrl = server.url("/").toString().trimEnd('/'))
        val codebergResolver = CodebergApkResolver(client)
        val htmlResolver = HtmlApkResolver(client)
        manager = ApkDownloadManager(
            context = context,
            githubResolver = githubResolver,
            codebergResolver = codebergResolver,
            htmlResolver = htmlResolver,
            dispatcher = Dispatchers.IO,
            supportedAbis = arrayOf("arm64-v8a")
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
        tempDir.deleteRecursively()
    }

    private fun sampleApp(id: String): RecommendedApp {
        val entry = JSONObject().apply {
            put("id", id)
            put("url", "https://github.com/owner/$id")
            put("overrideSource", "GitHub")
            put("additionalSettings", JSONObject().apply {
                put("apkFilterRegEx", "")
                put("autoApkFilterByArch", false)
                put("includePrereleases", false)
                put("filterReleaseTitlesByRegEx", "")
            }.toString())
            put("preferredApkIndex", 0)
        }
        return RecommendedApp(
            id = id,
            name = id,
            author = "test",
            category = Category.Emulator,
            sourceUrl = "https://github.com/owner/$id",
            about = "",
            entryJson = entry.toString()
        )
    }

    /** Waits until the state for [appId] reaches a terminal state or Downloading. */
    private suspend fun awaitPostResolve(appId: String): ApkDownloadState {
        var state: ApkDownloadState
        while (true) {
            state = manager.states.first()[appId] ?: ApkDownloadState.Idle
            if (state is ApkDownloadState.Ready ||
                state is ApkDownloadState.Failed ||
                state is ApkDownloadState.Installed ||
                state is ApkDownloadState.Downloading
            ) break
            kotlinx.coroutines.delay(10)
        }
        return state
    }

    @Test
    fun `transitions to Failed when resolver fails`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404))

        manager.downloadApps(listOf(sampleApp("com.test.fail")))
        val state = awaitPostResolve("com.test.fail")

        assertTrue(
            "Expected Failed but got $state",
            state is ApkDownloadState.Failed
        )
    }

    @Test
    fun `transitions to Downloading after successful resolution`() = runBlocking {
        // /releases/latest returns a single object
        val releaseJson = """{"tag_name":"v1.0","prerelease":false,"assets":[
            {"name":"app.apk","browser_download_url":"https://example.com/app.apk"}
        ]}"""
        server.enqueue(MockResponse().setBody(releaseJson))
        // Mock DownloadManager.enqueue to return a download ID
        whenever(downloadManagerMock.enqueue(org.mockito.kotlin.any())).thenReturn(42L)

        manager.downloadApps(listOf(sampleApp("com.test.app")))
        val state = awaitPostResolve("com.test.app")

        assertTrue(
            "Expected Downloading but got $state",
            state is ApkDownloadState.Downloading
        )
    }

    @Test
    fun `cancelAll clears states`() = runBlocking {
        // Let resolution reach a terminal state before cancelling: cancelAll
        // cancels the parent job, but cancellation is cooperative, so an
        // in-flight resolution coroutine could otherwise re-write its state
        // AFTER the clear (raced under parallel test execution). This test
        // pins "cancelAll clears states", not in-flight cancel semantics.
        server.enqueue(MockResponse().setResponseCode(404))
        manager.downloadApps(listOf(sampleApp("com.test.x")))
        awaitPostResolve("com.test.x")

        manager.cancelAll()

        val states = manager.states.first()
        assertTrue("Expected empty states but got $states", states.isEmpty())
    }

    @Test
    fun `catalog downloads must be https`() {
        assertTrue(ApkDownloadManager.isHttpsUrl("https://github.com/x/y/releases/download/1/a.apk"))
        assertFalse(ApkDownloadManager.isHttpsUrl("http://buildbot.libretro.com/a.apk"))
        assertFalse(ApkDownloadManager.isHttpsUrl("ftp://x/a.apk"))
    }
}
