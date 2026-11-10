package com.twinlore.app.data.ai

import android.util.Base64
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/** A streamed chunk from Gemini. */
sealed class GeminiChunk {
    data class Delta(val text: String) : GeminiChunk()
    object Done : GeminiChunk()
    data class Error(val kind: GeminiErrorKind, val message: String) : GeminiChunk()
}

enum class GeminiErrorKind { Auth, Quota, Network, Unknown }

/**
 * Minimal Gemini REST client over OkHttp SSE. Open so tests/VMs can fake [stream].
 * No Google SDK / Play Services — direct call to generativelanguage.googleapis.com.
 */
open class GeminiClient @Inject constructor() {
    private var httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()
    private var baseUrl: String = "https://generativelanguage.googleapis.com"

    /** Test seam: override transport + endpoint. */
    fun configureForTest(client: OkHttpClient, url: String) {
        httpClient = client
        baseUrl = url.trimEnd('/')
    }

    /** Index of the most recent USER message carrying a readable image, or -1. */
    private fun latestImageIndex(messages: List<AiMessage>): Int {
        for (i in messages.indices.reversed()) {
            val m = messages[i]
            if (m.role == AiRole.USER && m.imagePath != null && File(m.imagePath).exists()) return i
        }
        return -1
    }

    internal fun buildRequestBody(systemInstruction: String, messages: List<AiMessage>): String {
        val imageIdx = latestImageIndex(messages)
        val contents = JsonArray()
        messages.forEachIndexed { i, m ->
            val parts = JsonArray()
            if (m.text.isNotBlank()) {
                parts.add(JsonObject().apply { addProperty("text", m.text) })
            }
            if (i == imageIdx && m.imagePath != null) {
                // Guard the read: clear() can delete the image dir between the
                // exists() check in latestImageIndex and here. Omit on failure.
                val bytes = runCatching { File(m.imagePath).readBytes() }.getOrNull()
                if (bytes != null) {
                    val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    parts.add(JsonObject().apply {
                        // Always PNG: AiSessionStore.copyImageIntoSession stamps .png.
                        add("inline_data", JsonObject().apply {
                            addProperty("mime_type", "image/png")
                            addProperty("data", b64)
                        })
                    })
                }
            }
            if (parts.size() == 0) parts.add(JsonObject().apply { addProperty("text", "") })
            contents.add(JsonObject().apply {
                addProperty("role", when (m.role) { AiRole.USER -> "user"; AiRole.MODEL -> "model" })
                add("parts", parts)
            })
        }
        return JsonObject().apply {
            add("system_instruction", JsonObject().apply {
                add("parts", JsonArray().apply {
                    add(JsonObject().apply { addProperty("text", systemInstruction) })
                })
            })
            add("contents", contents)
        }.toString()
    }

    /**
     * POSTs the conversation and streams text deltas via SSE (`alt=sse`).
     * Emits Delta* then Done on success, or a single Error on failure.
     * Never throws — transport/parse failures map to GeminiChunk.Error.
     * Note: the blocking SSE read unwinds on collector cancellation at the next
     * line boundary or the OkHttp read timeout, not instantly.
     */
    open fun stream(
        apiKey: String,
        model: String,
        systemInstruction: String,
        messages: List<AiMessage>,
    ): Flow<GeminiChunk> = flow {
        val url = "$baseUrl/v1beta/models/$model:streamGenerateContent?alt=sse"
        val request = Request.Builder()
            .url(url)
            .addHeader("x-goog-api-key", apiKey)
            .addHeader("Content-Type", "application/json")
            // Disable transparent gzip: a compressed SSE stream is buffered in
            // blocks, which batches several events and breaks word-by-word display.
            .addHeader("Accept-Encoding", "identity")
            .post(buildRequestBody(systemInstruction, messages).toRequestBody("application/json".toMediaType()))
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val kind = when (response.code) {
                        401, 403 -> GeminiErrorKind.Auth
                        429 -> GeminiErrorKind.Quota
                        else -> GeminiErrorKind.Unknown
                    }
                    val body = runCatching { response.body?.string() }.getOrNull()
                    emit(GeminiChunk.Error(kind, extractErrorMessage(body) ?: "HTTP ${response.code}"))
                    return@flow
                }
                val source = response.body?.source()
                if (source == null) {
                    emit(GeminiChunk.Error(GeminiErrorKind.Unknown, "Empty response"))
                    return@flow
                }
                while (true) {
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val payload = line.removePrefix("data:").trim()
                    if (payload.isEmpty() || payload == "[DONE]") continue
                    val text = extractText(payload)
                    if (text.isNotEmpty()) emit(GeminiChunk.Delta(text))
                }
                emit(GeminiChunk.Done)
            }
        } catch (e: CancellationException) {
            throw e // never swallow cancellation
        } catch (e: IOException) {
            emit(GeminiChunk.Error(GeminiErrorKind.Network, e.message ?: "Network error"))
        } catch (e: Exception) {
            emit(GeminiChunk.Error(GeminiErrorKind.Unknown, e.message ?: "Unknown error"))
        }
    }.flowOn(Dispatchers.IO)

    private fun extractText(payload: String): String = try {
        val parts = JsonParser.parseString(payload).asJsonObject
            .getAsJsonArray("candidates")?.get(0)?.asJsonObject
            ?.getAsJsonObject("content")?.getAsJsonArray("parts")
        buildString {
            parts?.forEach { p ->
                p.asJsonObject.get("text")?.let { append(it.asString) }
            }
        }
    } catch (_: Exception) {
        "" // Skip malformed chunks rather than aborting the stream.
    }

    /**
     * Pulls the human-readable `error.message` out of a Gemini error body so the UI
     * can show a clean sentence instead of a raw JSON blob. Falls back to a trimmed
     * snippet of the body when it isn't the expected shape, or null when empty.
     */
    private fun extractErrorMessage(body: String?): String? {
        if (body.isNullOrBlank()) return null
        val parsed = try {
            JsonParser.parseString(body).asJsonObject
                .getAsJsonObject("error")?.get("message")?.asString
                ?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
        return parsed ?: body.take(300)
    }
}
