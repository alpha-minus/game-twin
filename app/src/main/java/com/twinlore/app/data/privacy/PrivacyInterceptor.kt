package com.twinlore.app.data.privacy

import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.SequenceInputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.zip.GZIPInputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class PrivacyInterceptor @Inject constructor(
    private val preferences: PrivacyPreferences,
    private val fetcher: PrivacyHttpFetcher,
) {

    fun intercept(request: WebResourceRequest): WebResourceResponse? {
        if (!preferences.enabled) return null

        val url = request.url?.toString() ?: return null

        if (BlockedHosts.isBlockedSubresource(url) || BlockedHosts.isBlockedSubresourcePath(url)) {
            return emptyBlockedResponse()
        }

        if (!request.isForMainFrame) return null
        if (request.method?.uppercase(Locale.ROOT) != "GET") return null

        return try {
            handleMainFrame(url, request.requestHeaders ?: emptyMap())
        } catch (t: Throwable) {
            Log.w(TAG, "intercept failed for $url", t)
            null
        }
    }

    private fun handleMainFrame(url: String, headers: Map<String, String>): WebResourceResponse? {
        val response = fetcher.fetch(url, headers)
        var ownsBody = true
        try {
            // Non-HTML: not our problem. Let the WebView fetch it itself so cookies +
            // cache layers stay intact.
            val contentType = response.contentType ?: ""
            if (!contentType.startsWith("text/html", ignoreCase = true)) {
                return null
            }
            // Non-2xx: not our problem either. Let the WebView see the real error.
            if (response.statusCode !in 200..299) {
                return null
            }
            // Unsupported encoding: we won't try to rewrite.
            val encoding = response.contentEncoding?.lowercase(Locale.ROOT)
            if (encoding != null && encoding != "identity" && encoding != "gzip") {
                Log.w(TAG, "unsupported Content-Encoding $encoding for $url; not rewriting")
                return null
            }
            val charset = parseCharset(contentType)
            // Read up to PREFIX_LIMIT, then decide.
            val rawStream: InputStream = if (encoding == "gzip") GZIPInputStream(response.body) else response.body
            val prefix = ByteArray(PREFIX_LIMIT)
            var totalRead = 0
            while (totalRead < PREFIX_LIMIT) {
                val n = rawStream.read(prefix, totalRead, PREFIX_LIMIT - totalRead)
                if (n < 0) break
                totalRead += n
            }
            val prefixBytes = prefix.copyOf(totalRead)
            val prefixText = String(prefixBytes, charset)
            val hasSignature = BlockedHosts.htmlContainsQuantcastSignature(prefixText)
            if (!hasSignature) {
                // No Quantcast on this page; nothing to do. Drop our fetched copy and let
                // the WebView fetch it normally with its own cookies + cache.
                return null
            }
            val spliced = HtmlHeadSplicer.spliceAfterHead(prefixBytes, charset, ConsentStubs.STUB_SCRIPT_TAG)
                ?: return null
            val combined = SequenceInputStream(ByteArrayInputStream(spliced), rawStream)
            ownsBody = false
            return buildResponse(response, combined, stripEncoding = encoding == "gzip")
        } finally {
            if (ownsBody) {
                runCatching { response.body.close() }
            }
        }
    }

    private fun emptyBlockedResponse(): WebResourceResponse =
        WebResourceResponse(
            "text/plain",
            "utf-8",
            204,
            "No Content",
            emptyMap(),
            ByteArrayInputStream(ByteArray(0)),
        )

    private fun buildResponse(
        source: PrivacyHttpResponse,
        body: InputStream,
        stripEncoding: Boolean,
    ): WebResourceResponse {
        val mimeType = source.contentType?.substringBefore(";")?.trim()?.ifEmpty { null } ?: "text/html"
        val encodingHeader = parseCharset(source.contentType ?: "").name()
        val responseHeaders: MutableMap<String, String> = LinkedHashMap(source.headers)
        if (stripEncoding) responseHeaders.remove("Content-Encoding")
        // Content-Length is now meaningless since the body length may differ. Remove it.
        responseHeaders.remove("Content-Length")
        return WebResourceResponse(
            mimeType,
            encodingHeader,
            source.statusCode,
            source.reasonPhrase.ifBlank { "OK" },
            responseHeaders,
            body,
        )
    }

    private fun parseCharset(contentType: String): Charset {
        val marker = "charset="
        val idx = contentType.lowercase(Locale.ROOT).indexOf(marker)
        if (idx < 0) return StandardCharsets.UTF_8
        val raw = contentType.substring(idx + marker.length).trim().trim('"').substringBefore(';').trim()
        return try {
            Charset.forName(raw)
        } catch (e: Exception) {
            StandardCharsets.UTF_8
        }
    }

    private companion object {
        const val TAG = "gametwin.privacy"
        const val PREFIX_LIMIT = 64 * 1024
    }
}
