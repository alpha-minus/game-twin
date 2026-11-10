package com.twinlore.app.data.ai

import app.cash.turbine.test
import com.google.gson.JsonParser
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class GeminiClientTest {
    private lateinit var client: GeminiClient
    private val tmpImages = mutableListOf<File>()

    @Before
    fun setup() {
        client = GeminiClient()
    }

    @After
    fun tearDown() {
        tmpImages.forEach { it.delete() }
    }

    private fun pngFile(bytes: ByteArray): File {
        val f = File.createTempFile("img", ".png").also { it.writeBytes(bytes) }
        tmpImages.add(f)
        return f
    }

    @Test
    fun body_includesSystemInstructionAndRoles() {
        val messages = listOf(
            AiMessage(AiRole.USER, "hi"),
            AiMessage(AiRole.MODEL, "hello"),
            AiMessage(AiRole.USER, "where now?"),
        )
        val json = JsonParser.parseString(client.buildRequestBody("Game: Zelda", messages)).asJsonObject
        assertEquals(
            "Game: Zelda",
            json.getAsJsonObject("system_instruction")
                .getAsJsonArray("parts")[0].asJsonObject["text"].asString,
        )
        val contents = json.getAsJsonArray("contents")
        assertEquals(3, contents.size())
        assertEquals("user", contents[0].asJsonObject["role"].asString)
        assertEquals("model", contents[1].asJsonObject["role"].asString)
    }

    @Test
    fun body_skipsImageWhenFileMissing() {
        val messages = listOf(
            AiMessage(AiRole.USER, "look", imagePath = "/nonexistent/does-not-exist.png"),
        )
        val json = JsonParser.parseString(client.buildRequestBody("g", messages)).asJsonObject
        val parts = json.getAsJsonArray("contents")[0].asJsonObject.getAsJsonArray("parts")
        assertFalse(parts.any { it.asJsonObject.has("inline_data") })
    }

    @Test
    fun body_attachesOnlyTheMostRecentImage() {
        val older = pngFile(byteArrayOf(1))
        val newer = pngFile(byteArrayOf(2, 2))
        val messages = listOf(
            AiMessage(AiRole.USER, "old shot", imagePath = older.absolutePath),
            AiMessage(AiRole.MODEL, "ok"),
            AiMessage(AiRole.USER, "new shot", imagePath = newer.absolutePath),
        )
        val json = JsonParser.parseString(client.buildRequestBody("g", messages)).asJsonObject
        val contents = json.getAsJsonArray("contents")
        // Oldest user message: text only, no inline_data.
        val firstParts = contents[0].asJsonObject.getAsJsonArray("parts")
        assertFalse(firstParts.any { it.asJsonObject.has("inline_data") })
        // Latest user message: has inline_data with base64 of the newer file.
        val lastParts = contents[2].asJsonObject.getAsJsonArray("parts")
        val inline = lastParts.first { it.asJsonObject.has("inline_data") }
            .asJsonObject.getAsJsonObject("inline_data")
        assertEquals("image/png", inline["mime_type"].asString)
        assertTrue(inline["data"].asString.isNotEmpty())
    }

    @Test
    fun stream_emitsDeltasThenDone() = runTest {
        val server = MockWebServer()
        val sse = buildString {
            append("data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Hel\"}]}}]}\n\n")
            append("data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"lo\"}]}}]}\n\n")
        }
        server.enqueue(MockResponse().setBody(sse).addHeader("Content-Type", "text/event-stream"))
        server.start()
        client.configureForTest(OkHttpClient(), server.url("/").toString())

        client.stream("key", "gemini-flash-latest", "sys", listOf(AiMessage(AiRole.USER, "hi"))).test {
            assertEquals(GeminiChunk.Delta("Hel"), awaitItem())
            assertEquals(GeminiChunk.Delta("lo"), awaitItem())
            assertEquals(GeminiChunk.Done, awaitItem())
            awaitComplete()
        }
        server.shutdown()
    }

    @Test
    fun stream_mapsAuthError() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(401).setBody("{\"error\":\"bad key\"}"))
        server.start()
        client.configureForTest(OkHttpClient(), server.url("/").toString())

        client.stream("key", "m", "sys", listOf(AiMessage(AiRole.USER, "hi"))).test {
            val item = awaitItem()
            assertTrue(item is GeminiChunk.Error && item.kind == GeminiErrorKind.Auth)
            awaitComplete()
        }
        server.shutdown()
    }

    @Test
    fun stream_mapsQuotaError() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(429).setBody("{}"))
        server.start()
        client.configureForTest(OkHttpClient(), server.url("/").toString())

        client.stream("key", "m", "sys", listOf(AiMessage(AiRole.USER, "hi"))).test {
            val item = awaitItem()
            assertTrue(item is GeminiChunk.Error && item.kind == GeminiErrorKind.Quota)
            awaitComplete()
        }
        server.shutdown()
    }

    @Test
    fun stream_mapsNetworkError() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        server.start()
        client.configureForTest(OkHttpClient(), server.url("/").toString())

        client.stream("key", "m", "sys", listOf(AiMessage(AiRole.USER, "hi"))).test {
            val item = awaitItem()
            assertTrue(item is GeminiChunk.Error && item.kind == GeminiErrorKind.Network)
            awaitComplete()
        }
        server.shutdown()
    }

    @Test
    fun stream_skipsMalformedChunks() = runTest {
        val server = MockWebServer()
        val sse = buildString {
            append("data: not-json\n\n")
            append("data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"ok\"}]}}]}\n\n")
        }
        server.enqueue(MockResponse().setBody(sse).addHeader("Content-Type", "text/event-stream"))
        server.start()
        client.configureForTest(OkHttpClient(), server.url("/").toString())

        client.stream("key", "m", "sys", listOf(AiMessage(AiRole.USER, "hi"))).test {
            assertEquals(GeminiChunk.Delta("ok"), awaitItem())
            assertEquals(GeminiChunk.Done, awaitItem())
            awaitComplete()
        }
        server.shutdown()
    }
}
