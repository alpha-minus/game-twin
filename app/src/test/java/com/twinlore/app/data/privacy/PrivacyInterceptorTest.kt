package com.twinlore.app.data.privacy

import android.net.Uri
import android.webkit.WebResourceRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPOutputStream
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PrivacyInterceptorTest {

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun preferences(enabled: Boolean): PrivacyPreferences {
        // Dispatchers.Unconfined runs the collector synchronously up to the first suspension
        // point. MutableStateFlow emits its initial value before suspending, so the @Volatile
        // is set before this helper returns. No race.
        val src = kotlinx.coroutines.flow.MutableStateFlow(enabled)
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)
        return PrivacyPreferences(src, scope)
    }

    private fun fakeRequest(
        url: String,
        method: String = "GET",
        isMainFrame: Boolean = true,
        headers: Map<String, String> = emptyMap(),
    ): WebResourceRequest {
        val request: WebResourceRequest = mock()
        whenever(request.url).thenReturn(Uri.parse(url))
        whenever(request.method).thenReturn(method)
        whenever(request.isForMainFrame).thenReturn(isMainFrame)
        whenever(request.requestHeaders).thenReturn(headers)
        return request
    }

    private fun textResponse(
        body: String,
        contentType: String = "text/html; charset=utf-8",
        statusCode: Int = 200,
        contentEncoding: String? = null,
    ): PrivacyHttpResponse {
        val bytes: ByteArray = if (contentEncoding == "gzip") {
            val buf = ByteArrayOutputStream()
            GZIPOutputStream(buf).use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
            buf.toByteArray()
        } else {
            body.toByteArray(StandardCharsets.UTF_8)
        }
        return PrivacyHttpResponse(
            statusCode = statusCode,
            reasonPhrase = "OK",
            contentType = contentType,
            contentEncoding = contentEncoding,
            headers = emptyMap(),
            body = ByteArrayInputStream(bytes),
        )
    }

    private class FakeFetcher(private val response: PrivacyHttpResponse? = null, private val error: Throwable? = null) : PrivacyHttpFetcher {
        var lastUrl: String? = null
        override fun fetch(url: String, requestHeaders: Map<String, String>): PrivacyHttpResponse {
            lastUrl = url
            if (error != null) throw error
            return response ?: error("no response configured")
        }
    }

    private fun bodyAsString(response: android.webkit.WebResourceResponse): String {
        val stream: InputStream = response.data ?: return ""
        return stream.readBytes().toString(StandardCharsets.UTF_8)
    }

    @Test
    fun `disabled returns null for every request`() {
        val interceptor = PrivacyInterceptor(preferences(enabled = false), FakeFetcher())
        assertNull(interceptor.intercept(fakeRequest("https://quantcast.com/sdk.js")))
        assertNull(interceptor.intercept(fakeRequest("https://example.com/")))
    }

    @Test
    fun `quantcast subresource returns 204 empty`() {
        val interceptor = PrivacyInterceptor(preferences(enabled = true), FakeFetcher())
        val response = interceptor.intercept(
            fakeRequest("https://choice.inmobi.com/sdk.js", isMainFrame = false)
        )
        assertNotNull(response)
        assertEquals(204, response!!.statusCode)
    }

    @Test
    fun `non-GET main frame returns null`() {
        val interceptor = PrivacyInterceptor(preferences(enabled = true), FakeFetcher())
        assertNull(interceptor.intercept(fakeRequest("https://example.com/", method = "POST")))
    }

    @Test
    fun `non-html content type returns null`() {
        val fetcher = FakeFetcher(textResponse("not html", contentType = "application/json"))
        val interceptor = PrivacyInterceptor(preferences(enabled = true), fetcher)
        assertNull(interceptor.intercept(fakeRequest("https://example.com/api")))
    }

    @Test
    fun `html without signature returns null`() {
        val html = "<html><head><title>Hi</title></head><body>plain</body></html>"
        val fetcher = FakeFetcher(textResponse(html))
        val interceptor = PrivacyInterceptor(preferences(enabled = true), fetcher)
        assertNull(interceptor.intercept(fakeRequest("https://example.com/")))
    }

    @Test
    fun `html with signature gets stub spliced into head`() {
        val html = """<html><head><script src="https://cmp.consensu.org/x.js"></script></head><body>q</body></html>"""
        val fetcher = FakeFetcher(textResponse(html))
        val interceptor = PrivacyInterceptor(preferences(enabled = true), fetcher)
        val response = interceptor.intercept(fakeRequest("https://neoseeker.com/page"))
        assertNotNull(response)
        val out = bodyAsString(response!!)
        assertTrue("stub present", out.contains("window.__tcfapi"))
        // Stub must appear before the original quantcast script tag.
        val stubIdx = out.indexOf("window.__tcfapi")
        val qcIdx = out.indexOf("consensu.org")
        assertTrue("stub appears before original script", stubIdx in 0..<qcIdx)
    }

    @Test
    fun `fetcher throwing returns null`() {
        val fetcher = FakeFetcher(error = RuntimeException("boom"))
        val interceptor = PrivacyInterceptor(preferences(enabled = true), fetcher)
        assertNull(interceptor.intercept(fakeRequest("https://example.com/")))
    }

    @Test
    fun `http 404 returns null`() {
        val fetcher = FakeFetcher(textResponse("not found", statusCode = 404))
        val interceptor = PrivacyInterceptor(preferences(enabled = true), fetcher)
        assertNull(interceptor.intercept(fakeRequest("https://example.com/missing")))
    }

    @Test
    fun `gzip encoded html is decoded scanned and served as identity`() {
        val html = """<html><head><script src="https://cmp.consensu.org/x.js"></script></head></html>"""
        val fetcher = FakeFetcher(textResponse(html, contentEncoding = "gzip"))
        val interceptor = PrivacyInterceptor(preferences(enabled = true), fetcher)
        val response = interceptor.intercept(fakeRequest("https://neoseeker.com/"))
        assertNotNull(response)
        // Response is no longer gzipped; Content-Encoding should be absent on the rewritten response.
        assertNull(response!!.responseHeaders?.get("Content-Encoding"))
        val out = bodyAsString(response)
        assertTrue(out.contains("window.__tcfapi"))
    }

    @Test
    fun `brotli encoded html returns null`() {
        val response = PrivacyHttpResponse(
            statusCode = 200,
            reasonPhrase = "OK",
            contentType = "text/html",
            contentEncoding = "br",
            headers = emptyMap(),
            body = ByteArrayInputStream(byteArrayOf(0x00)),
        )
        val fetcher = FakeFetcher(response)
        val interceptor = PrivacyInterceptor(preferences(enabled = true), fetcher)
        assertNull(interceptor.intercept(fakeRequest("https://example.com/")))
    }

    @Test
    fun `html with signature but no head tag returns null`() {
        // Quantcast signature is in the body, but there is no <head> tag anywhere.
        // Splicer returns null; interceptor returns null too and lets the WebView fetch
        // normally.
        val html = "<html><body><script src=\"https://cmp.consensu.org/x.js\"></script>plain</body></html>"
        val fetcher = FakeFetcher(textResponse(html))
        val interceptor = PrivacyInterceptor(preferences(enabled = true), fetcher)
        assertNull(interceptor.intercept(fakeRequest("https://neoseeker.com/")))
    }
}
