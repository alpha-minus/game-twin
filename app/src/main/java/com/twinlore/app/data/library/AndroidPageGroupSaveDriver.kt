package com.twinlore.app.data.library

import android.webkit.WebView
import android.webkit.WebViewClient
import com.twinlore.app.ui.screens.extractTocEntries
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Driver that takes over an EXISTING WebView (the one the user is looking at)
 * for the duration of a page-group save. Saves the prior WebViewClient and the
 * URL the WebView was showing; restores both on [destroy].
 *
 * Using the foreground WebView (rather than a 0x0 offscreen one) is necessary
 * because pages routinely show cookie-consent dialogs that block content load
 * until dismissed. The user can see and dismiss those dialogs only if the
 * WebView is visible.
 *
 * Note: [loadAndAwait] is only safe to call serially. The driver assumes the
 * caller will not start a new load before the previous one resolves or is
 * cancelled — which is true for [PageGroupSaveCoordinator]'s sequential queue.
 */
class AndroidPageGroupSaveDriver(
    private val webView: WebView,
    private val originalUrl: String?
) : PageGroupSaveDriver {

    private val originalWebViewClient: WebViewClient? = runCatching {
        // No public getter pre-26; we target >= 26.
        webView.webViewClient
    }.getOrNull()

    private var pendingResume: ((Boolean) -> Unit)? = null

    init {
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                val resume = pendingResume
                pendingResume = null
                resume?.invoke(true)
            }

            override fun onReceivedError(
                view: WebView?,
                request: android.webkit.WebResourceRequest?,
                error: android.webkit.WebResourceError?
            ) {
                // Subresource failures (favicon, images, ad scripts) are normal noise
                // during a page load; only the main frame's failure counts as a load failure.
                if (request?.isForMainFrame != true) return
                val resume = pendingResume
                pendingResume = null
                resume?.invoke(false)
            }
        }
    }

    override suspend fun loadAndAwait(url: String): Boolean =
        suspendCancellableCoroutine { cont ->
            pendingResume = { ok -> if (cont.isActive) cont.resume(ok) }
            cont.invokeOnCancellation {
                // Stop the in-flight load so a delayed onPageFinished can't resolve
                // the next iteration's continuation. Clear pendingResume too — this
                // continuation is dead.
                pendingResume = null
                runCatching { webView.stopLoading() }
            }
            webView.loadUrl(url)
        }

    override suspend fun saveWebArchive(absolutePath: String): Boolean {
        // Stash iframe srcs into data-original-src, strip lazy on every <img>,
        // sweep-scroll the page, and wait until every image reports .complete
        // (capped by an 8s budget + 1s settle). See [MhtSaveHelpers] for the
        // why and the trade-offs.
        MhtSaveHelpers.preloadForArchive(webView)
        return suspendCancellableCoroutine { cont ->
            webView.saveWebArchive(absolutePath, false) { result ->
                if (cont.isActive) cont.resume(result != null)
            }
        }
    }

    override suspend fun mainHtml(): String? =
        suspendCancellableCoroutine { cont ->
            webView.evaluateJavascript("(function(){return document.documentElement.outerHTML;})();") { raw ->
                if (cont.isActive) cont.resume(unquoteJs(raw))
            }
        }

    /**
     * Walks the live DOM via the existing TOC_EXTRACTION_JS asset that
     * `GuideBrowserHelpers.extractTocEntries` runs. The returned list is in
     * DOM order; each [RawAnchor] carries the header text and the scrollY
     * (in CSS pixels) captured against the foreground WebView. Called once
     * per save target while the WebView still shows the just-archived page.
     *
     * Failures (JS engine error, callback never fires) surface as a thrown
     * exception inside the coordinator's `runCatching`; the coordinator
     * treats that as "no anchors" and continues the save.
     *
     * Returns empty when the JS extractor falls back to its synthetic placeholder
     * grid (no real headers found): 7 evenly-spaced entries labelled "Top",
     * "Section 2"–"Section 6", "Bottom".
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun extractAnchors(): List<RawAnchor> =
        suspendCancellableCoroutine { cont ->
            extractTocEntries(webView) { tocEntries ->
                val anchors = tocEntries.map { entry ->
                    RawAnchor(label = entry.label, scrollY = entry.scrollY)
                }
                val isSynthetic = anchors.size == 7
                    && anchors.first().label == "Top"
                    && anchors.last().label == "Bottom"
                val result = if (isSynthetic) emptyList() else anchors
                if (cont.isActive) cont.resume(result) {}
            }
        }

    override fun destroy() {
        // Restore the browser's original WebViewClient so onPageStarted /
        // onPageFinished hooks the screen relies on come back. Then navigate
        // back to where the user was before the bulk save started, and clear
        // the polluted history.
        originalWebViewClient?.let { webView.webViewClient = it }
        val restoreUrl = originalUrl
        if (restoreUrl != null) {
            runCatching {
                webView.loadUrl(restoreUrl)
                webView.clearHistory()
            }
        }
        // NOTE: do NOT call webView.destroy() — this is the foreground WebView,
        // the BrowserScreen still owns its lifecycle.
    }

    /**
     * evaluateJavascript hands us a JSON-encoded string ("..."). Decode the
     * outer quotes and standard JSON escapes so callers get the raw HTML.
     * Falls back to the raw input on any decode failure.
     */
    private fun unquoteJs(raw: String?): String? {
        if (raw == null || raw == "null") return null
        return runCatching {
            val s = raw.trim()
            if (s.length < 2 || s.first() != '"' || s.last() != '"') return raw
            val sb = StringBuilder(s.length)
            var i = 1
            val end = s.length - 1
            while (i < end) {
                val c = s[i]
                if (c == '\\' && i + 1 < end) {
                    val n = s[i + 1]
                    when (n) {
                        '"', '\\', '/' -> { sb.append(n); i += 2 }
                        'n' -> { sb.append('\n'); i += 2 }
                        'r' -> { sb.append('\r'); i += 2 }
                        't' -> { sb.append('\t'); i += 2 }
                        'b' -> { sb.append('\b'); i += 2 }
                        'f' -> { sb.append(''); i += 2 }
                        'u' -> {
                            if (i + 5 <= end) {
                                val hex = s.substring(i + 2, i + 6)
                                sb.append(hex.toInt(16).toChar())
                                i += 6
                            } else { sb.append(c); i++ }
                        }
                        else -> { sb.append(n); i += 2 }
                    }
                } else {
                    sb.append(c); i++
                }
            }
            sb.toString()
        }.getOrNull() ?: raw
    }
}
