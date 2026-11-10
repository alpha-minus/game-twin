package com.twinlore.app.data.library

import android.os.SystemClock
import android.webkit.WebView
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.yield
import kotlin.coroutines.resume

/**
 * Foreground-WebView preparation step that runs immediately before
 * [WebView.saveWebArchive]. Chrome's MHT serializer only embeds subresources
 * that were already in the page's resource set at save time, so lazy-loaded
 * thumbs that hadn't entered the viewport are written as bare `<img>` tags
 * with no bytes. This helper:
 *
 *   1. Stashes every iframe's live `src` into `data-original-src` so the
 *      offline viewer can recover the URL after Chrome rewrites it to
 *      `cid:frame-...@mhtml.blink`. (Lifted from the previously-duplicated
 *      blocks in `AndroidPageGroupSaveDriver` and `BrowserSaveCoordinator`.)
 *   2. Strips `loading="lazy"`, re-arms incomplete images by re-assigning
 *      `src`, and sets `loading = "eager"`.
 *   3. Sweeps `window.scrollTo(0, y)` top→bottom in [scrollChunkPx] increments
 *      to fire IntersectionObserver / scroll-tied loaders.
 *   4. Polls `Array.from(document.images).every(i => i.complete)` every
 *      [pollIntervalMs] until true or [budgetMs] elapses.
 *   5. Waits [settleMs] for any final paints / late image bytes.
 *   6. Restores the original scrollY so the user's foreground tab is not left
 *      scrolled to the bottom after a single-page save.
 *
 * Cancel-aware: every JS round-trip uses [suspendCancellableCoroutine] and
 * every delay sits between [yield] calls, so coroutine cancellation aborts
 * the preload promptly.
 *
 * Safe on degenerate pages — single-screen pages short-circuit the scroll
 * loop, and a page with no images resolves the poll on the first tick.
 */
object MhtSaveHelpers {

    /** See `stashIframeSrcsJs_sets_data_original_src_only_when_missing`. */
    internal const val STASH_IFRAME_SRCS_JS = """
        (function() {
            try {
                var fs = document.querySelectorAll('iframe');
                for (var i = 0; i < fs.length; i++) {
                    var s = fs[i].getAttribute('src') || fs[i].src || '';
                    if (s && !fs[i].getAttribute('data-original-src')) {
                        fs[i].setAttribute('data-original-src', s);
                    }
                }
            } catch (e) {}
        })();
    """

    /** See `stripLazyAndRearmJs_strips_lazy_and_reassigns_src_on_incomplete_images`. */
    internal const val STRIP_LAZY_AND_REARM_JS = """
        (function() {
            try {
                var imgs = document.querySelectorAll('img');
                for (var i = 0; i < imgs.length; i++) {
                    var img = imgs[i];
                    if (img.getAttribute('loading') === 'lazy') {
                        img.removeAttribute('loading');
                    }
                    img.loading = 'eager';
                    if (!img.complete) {
                        var s = img.currentSrc || img.src;
                        if (s) img.src = s;
                    }
                }
            } catch (e) {}
        })();
    """

    /**
     * Single-expression JS — `evaluateJavascript` returns the result as a
     * JSON-encoded string (`"true"` / `"false"`). No semicolons, no IIFE.
     */
    internal const val ALL_IMAGES_COMPLETE_JS =
        "Array.from(document.images).every(function(i){return i.complete})"

    internal const val READ_SCROLL_Y_JS =
        "(window.pageYOffset||document.documentElement.scrollTop||0)"

    internal const val READ_SCROLL_HEIGHT_JS =
        "(document.body?document.body.scrollHeight:document.documentElement.scrollHeight)"

    internal fun scrollToYJs(y: Int): String = "window.scrollTo(0, $y)"

    /**
     * Run the preload sequence on [webView]. Must be called from the main thread.
     */
    suspend fun preloadForArchive(
        webView: WebView,
        budgetMs: Long = 8_000L,
        settleMs: Long = 1_000L,
        scrollChunkPx: Int = 400,
        scrollIntervalMs: Long = 50L,
        pollIntervalMs: Long = 200L,
    ) {
        val started = SystemClock.uptimeMillis()
        val originalScrollY = evalIntOrZero(webView, READ_SCROLL_Y_JS)

        evalUnit(webView, STASH_IFRAME_SRCS_JS)
        evalUnit(webView, STRIP_LAZY_AND_REARM_JS)

        val scrollHeight = evalIntOrZero(webView, READ_SCROLL_HEIGHT_JS)
        val innerHeight = evalIntOrZero(webView, "window.innerHeight")
        val bottom = (scrollHeight - innerHeight).coerceAtLeast(0)

        // Scroll sweep — chunks of scrollChunkPx until we reach `bottom`.
        var y = 0
        while (y < bottom && SystemClock.uptimeMillis() - started < budgetMs) {
            yield()
            evalUnit(webView, scrollToYJs(y))
            delay(scrollIntervalMs)
            y += scrollChunkPx
        }
        if (y > 0 || bottom > 0) {
            // One final scrollTo at the bottom to fire any observers gated
            // strictly at scrollHeight - innerHeight.
            evalUnit(webView, scrollToYJs(bottom))
            delay(scrollIntervalMs)
        }

        // Poll until every image reports .complete or budget elapses.
        while (SystemClock.uptimeMillis() - started < budgetMs) {
            yield()
            val allComplete = evalBoolOrTrue(webView, ALL_IMAGES_COMPLETE_JS)
            if (allComplete) break
            delay(pollIntervalMs)
        }

        delay(settleMs)

        // Restore scrollY so the user's foreground tab returns to where they were.
        evalUnit(webView, scrollToYJs(originalScrollY))
    }

    private suspend fun evalUnit(webView: WebView, js: String) =
        suspendCancellableCoroutine<Unit> { cont ->
            webView.evaluateJavascript(js) { if (cont.isActive) cont.resume(Unit) }
        }

    /** `evaluateJavascript` returns a JSON-encoded string. Parse common shapes. */
    private suspend fun evalIntOrZero(webView: WebView, js: String): Int =
        suspendCancellableCoroutine { cont ->
            webView.evaluateJavascript(js) { raw ->
                if (!cont.isActive) return@evaluateJavascript
                val v = raw?.trim()?.removeSurrounding("\"")?.toFloatOrNull()?.toInt() ?: 0
                cont.resume(v)
            }
        }

    private suspend fun evalBoolOrTrue(webView: WebView, js: String): Boolean =
        suspendCancellableCoroutine { cont ->
            webView.evaluateJavascript(js) { raw ->
                if (!cont.isActive) return@evaluateJavascript
                // Default to true on parse failure (null / unexpected shape)
                // so a transient JS engine error short-circuits the poll
                // rather than pinning the preload at the budget cap. The
                // worst case is a sub-budget save with one stale frame.
                val trimmed = raw?.trim()
                val v = trimmed == null || trimmed == "\"true\"" || trimmed == "true"
                cont.resume(v)
            }
        }
}
