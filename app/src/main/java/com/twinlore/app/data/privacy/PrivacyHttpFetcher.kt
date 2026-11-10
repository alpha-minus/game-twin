package com.twinlore.app.data.privacy

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

internal data class PrivacyHttpResponse(
    val statusCode: Int,
    val reasonPhrase: String,
    val contentType: String?,
    val contentEncoding: String?,
    val headers: Map<String, String>,
    val body: InputStream,
)

internal interface PrivacyHttpFetcher {
    /**
     * Performs a synchronous GET. Throws on network failure. Caller is responsible for closing
     * [PrivacyHttpResponse.body].
     */
    fun fetch(url: String, requestHeaders: Map<String, String>): PrivacyHttpResponse
}

@Singleton
internal class OkHttpPrivacyHttpFetcher @Inject constructor(
    private val client: OkHttpClient,
) : PrivacyHttpFetcher {

    override fun fetch(url: String, requestHeaders: Map<String, String>): PrivacyHttpResponse {
        val requestBuilder = Request.Builder().url(url).get()
        requestHeaders.forEach { (name, value) ->
            // Skip headers OkHttp manages itself or that would confuse it.
            if (!RESTRICTED_HEADERS.contains(name.lowercase(Locale.ROOT))) {
                requestBuilder.header(name, value)
            }
        }
        val response = client.newCall(requestBuilder.build()).execute()
        val body = response.body ?: run {
            response.close()
            error("OkHttp returned a null body for $url")
        }
        val headerMap = response.headers.toMap()
        return PrivacyHttpResponse(
            statusCode = response.code,
            reasonPhrase = response.message.ifBlank { "OK" },
            contentType = response.header("Content-Type"),
            contentEncoding = response.header("Content-Encoding"),
            headers = headerMap,
            body = body.byteStream(),
        )
    }

    private fun okhttp3.Headers.toMap(): Map<String, String> {
        val out = LinkedHashMap<String, String>(size)
        for (i in 0 until size) out[name(i)] = value(i)
        return out
    }

    private companion object {
        // OkHttp owns these; passing them through can cause double-encoding or duplicate headers.
        val RESTRICTED_HEADERS: Set<String> = setOf(
            "content-length",
            "host",
            "connection",
            "transfer-encoding",
            "accept-encoding", // we set our own so we know what encodings to handle
        )
    }
}
