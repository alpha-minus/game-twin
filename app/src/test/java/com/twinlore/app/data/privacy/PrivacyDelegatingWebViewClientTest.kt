package com.twinlore.app.data.privacy

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PrivacyDelegatingWebViewClientTest {

    private val view: WebView = mock()
    private val request: WebResourceRequest = mock()

    @Test
    fun `delegate non-null short-circuits interceptor`() {
        val delegateResponse: WebResourceResponse = mock()
        val interceptor: PrivacyInterceptor = mock()
        val delegate: WebViewClient = mock()
        whenever(delegate.shouldInterceptRequest(view, request)).thenReturn(delegateResponse)
        val client = PrivacyDelegatingWebViewClient(interceptor, delegate)

        val response = client.shouldInterceptRequest(view, request)

        assertSame(delegateResponse, response)
        verify(interceptor, never()).intercept(any())
    }

    @Test
    fun `delegate null falls through to interceptor`() {
        val interceptorResponse: WebResourceResponse = mock()
        val interceptor: PrivacyInterceptor = mock()
        whenever(interceptor.intercept(any())).thenReturn(interceptorResponse)
        val delegate: WebViewClient = mock()
        whenever(delegate.shouldInterceptRequest(view, request)).thenReturn(null)
        val client = PrivacyDelegatingWebViewClient(interceptor, delegate)

        val response = client.shouldInterceptRequest(view, request)

        assertSame(interceptorResponse, response)
    }

    @Test
    fun `null delegate goes straight to interceptor`() {
        val interceptorResponse: WebResourceResponse = mock()
        val interceptor: PrivacyInterceptor = mock()
        whenever(interceptor.intercept(any())).thenReturn(interceptorResponse)
        val client = PrivacyDelegatingWebViewClient(interceptor, delegate = null)

        val response = client.shouldInterceptRequest(view, request)

        assertSame(interceptorResponse, response)
    }

    @Test
    fun `null delegate and null interceptor returns null`() {
        val interceptor: PrivacyInterceptor = mock()
        whenever(interceptor.intercept(any())).thenReturn(null)
        val client = PrivacyDelegatingWebViewClient(interceptor, delegate = null)

        val response = client.shouldInterceptRequest(view, request)

        assertNull(response)
    }

    @Test
    fun `interceptor throwing returns null when delegate returned null`() {
        val interceptor: PrivacyInterceptor = mock()
        whenever(interceptor.intercept(any())).thenThrow(RuntimeException("boom"))
        val delegate: WebViewClient = mock()
        whenever(delegate.shouldInterceptRequest(view, request)).thenReturn(null)
        val client = PrivacyDelegatingWebViewClient(interceptor, delegate)

        val response = client.shouldInterceptRequest(view, request)

        assertNull(response)
    }

    @Test
    fun `wrap helper handles null delegate`() {
        val interceptor: PrivacyInterceptor = mock()
        val wrapped = PrivacyDelegatingWebViewClient.wrap(null, interceptor)
        // No assertion about identity; just that this does not crash and constructs an instance.
        // Exercise it with a null-returning interceptor to confirm shape.
        whenever(interceptor.intercept(any())).thenReturn(null)
        assertNull(wrapped.shouldInterceptRequest(view, request))
    }

    @Test
    fun `wrap helper does not double-wrap an already-wrapped client`() {
        val interceptor: PrivacyInterceptor = mock()
        val once = PrivacyDelegatingWebViewClient.wrap(null, interceptor)
        val twice = PrivacyDelegatingWebViewClient.wrap(once, interceptor)
        assertSame(once, twice)
    }
}
