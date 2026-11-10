package com.twinlore.app.data.library

import com.twinlore.app.data.vgmaps.VgmapsMap
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

@OptIn(ExperimentalCoroutinesApi::class)
class VgmapsBulkDownloaderTest {
    private lateinit var server: MockWebServer
    private lateinit var cacheDir: File

    @Before fun setUp() {
        server = MockWebServer().also { it.start() }
        cacheDir = Files.createTempDirectory("vgmaps-test").toFile()
    }

    @After fun tearDown() {
        server.shutdown()
        cacheDir.deleteRecursively()
    }

    private fun mapAt(path: String, caption: String) = VgmapsMap(
        caption = caption,
        imageUrl = server.url(path).toString(),
    )

    private fun pngResponse(label: String): MockResponse = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "image/png")
        .setBody(Buffer().writeUtf8("PNG-$label"))

    @Test fun `happy path saves every map and ends Complete`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        repeat(3) { server.enqueue(pngResponse("m$it")) }
        val maps = listOf(
            mapAt("/m0.png", "Map 0"),
            mapAt("/m1.png", "Map 1"),
            mapAt("/m2.png", "Map 2"),
        )
        val saved = mutableListOf<Pair<String, Long>>()
        val downloader = VgmapsBulkDownloader(
            httpClient = OkHttpClient(),
            cacheDir = cacheDir,
            mainDispatcher = dispatcher,
            ioDispatcher = dispatcher,
            politenessDelayMs = 0L,
        )
        downloader.start(maps) { cacheFile, caption ->
            saved += caption to cacheFile.length()
            true
        }
        advanceUntilIdle()
        val state = downloader.state.first()
        assertTrue("State was $state", state is VgmapsDownloadState.Complete)
        state as VgmapsDownloadState.Complete
        assertEquals(3, state.saved)
        assertEquals(0, state.failures.size)
        assertEquals(listOf("Map 0", "Map 1", "Map 2"), saved.map { it.first })
        assertTrue(saved.all { it.second > 0 })
    }

    @Test fun `per-image http failure is recorded, run continues`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        server.enqueue(pngResponse("ok-0"))
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(pngResponse("ok-2"))
        val maps = listOf(
            mapAt("/m0.png", "Map 0"),
            mapAt("/m1.png", "Map 1"),
            mapAt("/m2.png", "Map 2"),
        )
        val downloader = VgmapsBulkDownloader(
            httpClient = OkHttpClient(),
            cacheDir = cacheDir,
            mainDispatcher = dispatcher,
            ioDispatcher = dispatcher,
            politenessDelayMs = 0L,
        )
        downloader.start(maps) { _, _ -> true }
        advanceUntilIdle()
        val state = downloader.state.first()
        assertTrue(state is VgmapsDownloadState.Complete)
        state as VgmapsDownloadState.Complete
        assertEquals(2, state.saved)
        assertEquals(1, state.failures.size)
        assertEquals("Map 1", state.failures[0].map.caption)
    }

    @Test fun `saveFile returning false counts as a failure`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        server.enqueue(pngResponse("a"))
        server.enqueue(pngResponse("b"))
        val downloader = VgmapsBulkDownloader(
            httpClient = OkHttpClient(),
            cacheDir = cacheDir,
            mainDispatcher = dispatcher,
            ioDispatcher = dispatcher,
            politenessDelayMs = 0L,
        )
        var i = 0
        downloader.start(
            listOf(mapAt("/a.png", "A"), mapAt("/b.png", "B"))
        ) { _, _ -> (i++ == 0) }
        advanceUntilIdle()
        val state = downloader.state.first()
        assertTrue(state is VgmapsDownloadState.Complete)
        state as VgmapsDownloadState.Complete
        assertEquals(1, state.saved)
        assertEquals(1, state.failures.size)
        assertEquals("B", state.failures[0].map.caption)
    }

    @Test fun `cancel mid-run ends in Cancelled state`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        server.enqueue(pngResponse("a"))
        server.enqueue(pngResponse("b"))
        server.enqueue(pngResponse("c"))
        val downloader = VgmapsBulkDownloader(
            httpClient = OkHttpClient(),
            cacheDir = cacheDir,
            mainDispatcher = dispatcher,
            ioDispatcher = dispatcher,
            politenessDelayMs = 10_000L, // long delay so we can cancel between items
        )
        downloader.start(
            listOf(
                mapAt("/a.png", "A"),
                mapAt("/b.png", "B"),
                mapAt("/c.png", "C"),
            )
        ) { _, _ ->
            delay(0)
            true
        }
        // Let item 0 complete, then cancel.
        testScheduler.advanceTimeBy(50)
        downloader.cancel()
        advanceUntilIdle()
        val state = downloader.state.first()
        assertTrue("State was $state", state is VgmapsDownloadState.Cancelled)
    }
}
