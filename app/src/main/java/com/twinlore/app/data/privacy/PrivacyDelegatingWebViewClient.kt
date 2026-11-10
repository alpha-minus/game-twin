package com.twinlore.app.data.privacy

import android.graphics.Bitmap
import android.util.Log
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * Wraps any [WebViewClient] (or `null`) so that [PrivacyInterceptor] participates in
 * shouldInterceptRequest. Precedence is delegate-first, interceptor-second: the delegate's
 * own intercept logic (e.g., offline asset serving from a local manifest) gets first chance,
 * and the privacy interceptor only runs when the delegate returned null. All other callbacks
 * forward to the delegate unchanged.
 *
 * Use [wrap] to install: it short-circuits if the client is already a
 * [PrivacyDelegatingWebViewClient] so repeated assignments do not stack. The interceptor
 * argument is preserved from the original wrap; subsequent wrap calls with a different
 * interceptor instance keep the original.
 */
internal class PrivacyDelegatingWebViewClient(
    private val interceptor: PrivacyInterceptor,
    private val delegate: WebViewClient?,
) : WebViewClient() {

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest,
    ): WebResourceResponse? {
        // Delegate runs first so its offline-asset serving (manifest-mapped HTTP URLs to
        // local files) is preserved. Only when the delegate has nothing to say do we run the
        // privacy interceptor.
        val delegateResponse = delegate?.shouldInterceptRequest(view, request)
        if (delegateResponse != null) return delegateResponse

        return try {
            interceptor.intercept(request)
        } catch (t: Throwable) {
            Log.w(TAG, "interceptor failed for ${request.url}", t)
            null
        }
    }

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        delegate?.onPageStarted(view, url, favicon)
    }

    override fun onPageFinished(view: WebView, url: String) {
        delegate?.onPageFinished(view, url)
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
        delegate?.shouldOverrideUrlLoading(view, request) ?: false

    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: WebResourceError,
    ) {
        delegate?.onReceivedError(view, request, error)
    }

    override fun onReceivedHttpError(
        view: WebView,
        request: WebResourceRequest,
        errorResponse: WebResourceResponse,
    ) {
        delegate?.onReceivedHttpError(view, request, errorResponse)
    }

    override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
        delegate?.doUpdateVisitedHistory(view, url, isReload)
    }

    override fun onLoadResource(view: WebView, url: String) {
        delegate?.onLoadResource(view, url)
    }

    override fun onScaleChanged(view: WebView, oldScale: Float, newScale: Float) {
        delegate?.onScaleChanged(view, oldScale, newScale)
    }

    companion object {
        private const val TAG = "gametwin.privacy"

        fun wrap(delegate: WebViewClient?, interceptor: PrivacyInterceptor): PrivacyDelegatingWebViewClient =
            if (delegate is PrivacyDelegatingWebViewClient) delegate
            else PrivacyDelegatingWebViewClient(interceptor, delegate)
    }
}
