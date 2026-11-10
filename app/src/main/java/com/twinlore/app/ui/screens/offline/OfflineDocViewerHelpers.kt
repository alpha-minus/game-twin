package com.twinlore.app.ui.screens.offline

import android.app.Dialog
import android.content.Context
import android.net.Uri
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.webkit.WebChromeClient
import android.webkit.WebResourceResponse
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.twinlore.app.data.AppFont
import com.twinlore.app.ui.screens.GuideWebView
import com.twinlore.app.ui.screens.buildPaletteCss
import com.twinlore.app.ui.screens.contextAsActivity
import java.io.File

/**
 * Shared helpers for offline-doc viewers (single-guide + page-group).
 *
 * Each helper here is a verbatim lift from `PageGroupViewerScreen.kt`'s
 * inline WebView setup blocks, so adoption in either viewer is a single
 * call instead of 30–80 lines of duplicated state plumbing.
 */

/**
 * Splice the palette `<style>` block into the HTML so the browser parses
 * our theme CSS before first paint. Kills the white-flash that
 * onPageFinished CSS injection would otherwise allow.
 *
 * Lifted verbatim from `GuideScreen.applyPalettePrelude` (commits
 * d7236b5c / bb865471). Reused by both single-guide and page-group viewers.
 */
fun applyPalettePrelude(
    html: String,
    bgHex: String,
    fgHex: String,
    font: AppFont
): String {
    val cssRules = buildPaletteCss(bgHex, fgHex, font)
    val styleTag = "<style id=\"palette-override\">$cssRules</style>"
    val headMatch = Regex("<head\\b[^>]*>", RegexOption.IGNORE_CASE).find(html)
    return if (headMatch != null) {
        val insertAt = headMatch.range.last + 1
        html.substring(0, insertAt) + styleTag + html.substring(insertAt)
    } else {
        styleTag + html
    }
}

/**
 * Splice the reading-mode chrome-strip `<script>` block before `</body>`
 * so the keep-chain walk runs during DOM parse and the first paint is
 * already stripped — no flash of full chrome.
 *
 * No-op when [filterForHost] returns null (unknown host, malformed URL,
 * legacy save with no recorded URL) — the safe-fallback path. The caller
 * is responsible for gating on the per-vault theme-inject toggle; this
 * prelude always splices when a filter exists.
 *
 * Pairs with [REMOVE_READING_MODE_JS] for the theme-toggle-off LaunchedEffect.
 */
/**
 * Holds the first paint back until the reading-mode strip has run.
 *
 * The palette gets a `<style>` prelude and lands before anything is drawn; the
 * strip is a `<script>` before `</body>`, so the browser paints the page with
 * the site's own header, search box and navigation, and the script then removes
 * them and the whole document reflows. Captured on the Thor at chapter 20 of a
 * StrategyWiki group: the first frame carries "Search StrategyWiki" and text a
 * size larger than the one it settles at.
 *
 * So the document is hidden until the strip is done. Not opacity or a colour:
 * `visibility` keeps layout, so nothing moves when it comes back.
 *
 * The timeout is the safety net and is not optional. A script that throws, a
 * filter whose selector never matches, an uncloak that never runs for any
 * reason at all: every one of those would otherwise leave a permanently blank
 * guide, which is far worse than the flash this removes.
 */
const val READER_CLOAK_ID = "gt-reader-cloak"

private const val READER_CLOAK_PRELUDE =
    "<style id=\"$READER_CLOAK_ID\">html{visibility:hidden!important}</style>" +
        "<script>setTimeout(function(){var c=document.getElementById('$READER_CLOAK_ID');" +
        "if(c&&c.parentNode)c.parentNode.removeChild(c);},600);</script>"

/**
 * Reveals the document again.
 *
 * Run by the viewers from onPageFinished, once that pass has applied its own
 * reflows: the cosmetic filter and the text size. It used to be spliced inline
 * and lift on the frame after parsing, which was before onPageFinished, and the
 * overlay panel showed the page and then watched it change size.
 *
 * Still on the frame after, so the call does not depend on when it is made:
 * everything that runs at parse or on DOMContentLoaded (the strip, the padding,
 * the landing scroll, the decorations) has had its turn by then, and so has
 * whatever was evaluated just before it.
 */
const val READER_UNCLOAK_JS: String =
    "(function(){function u(){var c=document.getElementById('$READER_CLOAK_ID');" +
        "if(c&&c.parentNode)c.parentNode.removeChild(c);}" +
        "function s(){if(window.requestAnimationFrame)requestAnimationFrame(u);else u();}" +
        "if(document.readyState==='loading')" +
        "document.addEventListener('DOMContentLoaded',s);else s();})();"

fun applyChromeStripPrelude(html: String, sourceUrl: String?): String {
    val filter = filterForHost(sourceUrl) ?: return html
    val scriptBlock = buildReadingModeInjection(filter)
    val cloaked = READER_CLOAK_PRELUDE + html
    val bodyCloseMatch = Regex("</body\\s*>", RegexOption.IGNORE_CASE).find(cloaked)
    return if (bodyCloseMatch != null) {
        val insertAt = bodyCloseMatch.range.first
        cloaked.substring(0, insertAt) + scriptBlock + cloaked.substring(insertAt)
    } else {
        cloaked + scriptBlock
    }
}

/**
 * Splice anchor-badge + scroll-marker-highlight `<script>` blocks into
 * the HTML so they execute during page parsing — decorations appear
 * with the first frame instead of after a perceptible delay from
 * onPageFinished's evaluateJavascript.
 *
 * Place just before `</body>` so all anchor / text content is parsed
 * before the IIFEs run. Falls back to appending at the end of the
 * document if there's no `</body>` tag.
 *
 * Both IIFEs are idempotent (data-pg-local-marked and
 * .scroll-marker-highlight guards), so the existing
 * onPageFinished re-injection stays safe — it's a no-op on the
 * already-decorated DOM.
 */
fun spliceInlineDecorationsScript(
    html: String,
    decorationsScript: String
): String {
    val scriptBlock = "<script id=\"gt-decorations\">$decorationsScript</script>"
    val bodyCloseMatch = Regex("</body\\s*>", RegexOption.IGNORE_CASE).find(html)
    return if (bodyCloseMatch != null) {
        val insertAt = bodyCloseMatch.range.first
        html.substring(0, insertAt) + scriptBlock + html.substring(insertAt)
    } else {
        html + scriptBlock
    }
}

/**
 * Inline SVG markup for the "external link" badge (Material open_in_new
 * icon, 12×12). Marks anchors that will open in the browser tab so the
 * affordance is distinct from in-document anchors (which carry no badge —
 * the accent link color + dotted underline is their whole affordance).
 * Used by both [com.twinlore.app.ui.screens.guide.buildInternalAnchorBadgeJs]
 * and [com.twinlore.app.ui.screens.buildLocalLinkMarkerJs] so the
 * decoration stays consistent across single-guide and page-group viewers.
 *
 * `currentColor` so the badge picks up the link's CSS color (the accent
 * color the decoration JS sets inline).
 */
const val EXTERNAL_LINK_SVG_INNER: String =
    "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"12\" height=\"12\" " +
        "viewBox=\"0 0 24 24\" fill=\"currentColor\" aria-hidden=\"true\">" +
        "<path d=\"M19 19H5V5h7V3H5c-1.11 0-2 .9-2 2v14c0 1.1.89 2 2 2h14c1.1 0 2-.9 2-2v-7h-2v7zM14 3v2h3.59l-9.83 9.83 1.41 1.41L19 6.41V10h2V3h-7z\"/>" +
        "</svg>"

/** Style on the trailing `<span>` badge — inline-block + faint so the icon doesn't dominate the link text. */
const val ANCHOR_BADGE_SPAN_STYLE: String =
    "display:inline-block;vertical-align:middle;margin-left:3px;opacity:0.65;"

/** Style appended to the anchor itself — the accent color IS the affordance; no underline. */
const val ANCHOR_DECORATION_STYLE: String =
    "text-decoration: none; cursor: pointer;"

/**
 * JS fragment that classifies a raw `href` string and returns
 * `'http:'`/`'https:'` only when the link is a real browser-bound external
 * navigation. Returns null for empty, `javascript:`, `mailto:`, `tel:`,
 * and bare-fragment hrefs (those are handled by the in-doc internal
 * classifier).
 *
 * Inlined into both anchor-decoration JS builders so each viewer's
 * external pass uses the same skip rules.
 */
const val EXTERNAL_HREF_CLASSIFIER_JS: String = """
    function gtClassifyExternal(rawHref) {
        if (!rawHref) return false;
        if (rawHref.charAt(0) === '#') return false;
        var lower = rawHref.toLowerCase();
        if (lower.indexOf('javascript:') === 0) return false;
        if (lower.indexOf('mailto:') === 0) return false;
        if (lower.indexOf('tel:') === 0) return false;
        try {
            var p = new URL(rawHref, window.location.href).protocol;
            return p === 'http:' || p === 'https:';
        } catch (e) { return false; }
    }
"""

/**
 * Returns true when [url] is hosted on omegametroid.com (apex, www., or any
 * future subdomain). Used by the offline-viewer anchor-badge JS builders to
 * skip badging sidebar-tile anchors (`<a><img></a>`) where the badge would
 * sit underneath the image inside the anchor box. Scoped to this one host —
 * every other host keeps the badge on image-wrapping anchors per the
 * existing rules.
 */
internal fun isOmegametroidHost(url: String): Boolean {
    if (url.isBlank()) return false
    val host = runCatching { java.net.URL(url).host?.lowercase() }.getOrNull() ?: return false
    return host == "omegametroid.com" || host.endsWith(".omegametroid.com")
}

/**
 * Build a function that serves MHT-extracted assets (images / CSS / fonts)
 * off-disk by URL. Returns null on a manifest miss, missing baseDir, or
 * missing on-disk file — caller falls back to `super.shouldInterceptRequest`,
 * which lets online still work for resources Chrome didn't capture in the MHT.
 *
 * Lifted from `PageGroupViewerScreen.kt:1011-1037`. Manifest format is
 * `{ originalUrl -> "assets/<file>" }` (relative to baseDir).
 *
 * The MutableState refs are read live so the interceptor closure stays
 * stable across page navigations within the same WebView. Reads are
 * thread-safe under the Compose snapshot model — `shouldInterceptRequest`
 * runs on a non-main WebView worker thread.
 */
fun buildMhtAssetInterceptor(
    manifestState: MutableState<Map<String, String>>,
    baseDirState: MutableState<File?>
): (String) -> WebResourceResponse? = serve@{ url ->
    val manifest = manifestState.value
    val baseDir = baseDirState.value
    if (manifest.isEmpty() || baseDir == null) return@serve null
    val relativePath = manifest[url] ?: return@serve null
    val localFile = File(baseDir, relativePath)
    if (!localFile.exists()) return@serve null
    val mimeType = MimeTypeMap.getSingleton()
        .getMimeTypeFromExtension(
            relativePath.substringAfterLast('.', "").lowercase()
        ) ?: "application/octet-stream"
    WebResourceResponse(mimeType, "UTF-8", localFile.inputStream())
}

/**
 * Resolve an image URL inside a saved guide to the on-disk file inside the
 * MHT cache. Returns null if either the URL isn't in the manifest or the file
 * has been evicted.
 *
 * The viewers' image-tap and image-long-press flows both need this; lifted
 * here so it's not duplicated and so the page-group viewer can call it once
 * it grows an equivalent long-press handler.
 */
fun resolveSavedGuideImageFile(
    baseDir: java.io.File?,
    manifest: Map<String, String>,
    srcUrl: String
): java.io.File? {
    val rel = manifest[srcUrl] ?: return null
    if (baseDir == null) return null
    val file = java.io.File(baseDir, rel)
    return file.takeIf { it.exists() }
}

/**
 * Derive a filesystem-safe suggested filename from an image URL. Strips the
 * query string, falls back to a timestamped placeholder when the path
 * doesn't carry a usable basename (typical for CDN-style URLs that end in
 * `/`). The returned name always carries a file extension matching the
 * source so MediaStore picks the right mime when displaying.
 */
fun suggestedImageFilename(srcUrl: String, sourceExtension: String?): String {
    val basename = srcUrl
        .substringBefore('?')
        .substringBefore('#')
        .substringAfterLast('/')
        .ifBlank { "" }
    if (basename.isNotBlank() && basename.contains('.')) return basename
    val ext = sourceExtension?.takeIf { it.isNotBlank() } ?: "png"
    return "image-${System.currentTimeMillis()}.$ext"
}

/**
 * Apply iOS-Safari-style chrome gestures to an offline-doc WebView.
 *
 * Three signals:
 *  - **Tap without movement** → onTapToggle (toggle chrome).
 *  - **Scroll down past tap-slop** → onScrollDownHide (hide chrome).
 *  - **scrollY <= topRevealThresholdPx** → onTopReveal (reveal chrome).
 *
 * `onScrollY` is called on every scroll change with the current Y; viewers
 * plug their scroll-driven displayEntry derivation into this hook.
 *
 * Lifted from `PageGroupViewerScreen.kt:830-911`. Never consumes touches
 * (the WebView keeps scroll + text-selection); listeners are additive.
 */
/**
 * Wire global controller actions and analog-stick scrolling into an offline
 * doc viewer's WebView. Restores the same bindings the legacy `GuideScreen`
 * had before the offline single + page-group viewers took over its place.
 *
 * - Left stick (Y/X) → `wv.scrollBy(panX * 56, scroll * 56)` on a 16ms tick
 *   while active, 200ms when idle. Reads `mainViewModel.browserScroll` and
 *   `browserPanX` directly so a 60Hz motion event stream doesn't recompose
 *   the viewer.
 * - Right stick (RY/RZ) deliberately NOT wired here — the offline viewers
 *   have no zoom of their own (the WebView's pinch-zoom + the text-size
 *   slider cover the use case).
 *
 * Controller-action dispatch via [com.twinlore.app.ui.MainViewModel.controllerAction]:
 *
 *   | Action                          | Callback         |
 *   |---------------------------------|------------------|
 *   | OPEN_FIND_ON_PAGE (X)           | [onOpenFind]     |
 *   | OPEN_TOC (Y)                    | [onToggleToc]    |
 *   | BROWSER_SCROLL_TO_TOP (L3)      | inline `scrollTo(0,0)` |
 *   | BROWSER_SCROLL_TO_END (R3)      | inline JS `window.scrollTo(0, h)` |
 *   | BROWSER_SCROLL_MARKER_PREV (L1) | [onMarkerPrev]   |
 *   | BROWSER_SCROLL_MARKER_NEXT (R1) | [onMarkerNext]   |
 *   | BROWSER_PREV_BOOKMARK (L2)      | [onPrevAction]   |
 *   | BROWSER_NEXT_BOOKMARK (R2)      | [onNextAction]   |
 *
 * The L2 / R2 callbacks are deliberately abstract — the single viewer wires
 * them to "prev/next bookmark in the vault" (matching the legacy meaning);
 * the page-group viewer reuses the slots for "prev/next chapter" (the
 * group-only override the user asked for).
 *
 * Active only while [isActive] is true so a hidden tab doesn't fight the
 * visible one for the same global flow.
 */
@Composable
fun OfflineViewerControllerWiring(
    isActive: Boolean,
    mainViewModel: com.twinlore.app.ui.MainViewModel,
    webViewProvider: () -> android.webkit.WebView?,
    onOpenFind: () -> Unit,
    onToggleToc: () -> Unit,
    onMarkerPrev: () -> Unit = {},
    onMarkerNext: () -> Unit = {},
    onPrevAction: () -> Unit = {},
    onNextAction: () -> Unit = {},
) {
    // rememberUpdatedState keeps the controller-action LaunchedEffect (which
    // collects a long-lived Flow) reading the freshest closure each time it
    // fires. Without this the LaunchedEffect captures the lambdas from the
    // first composition — and lambdas like `onPrevAction = {
    // viewModel.goPrev(fromEntryId = displayEntry?.id) }` close over
    // `displayEntry`, which the user changes by scrolling. The stale closure
    // pivots on the original displayEntry no matter where the user has
    // scrolled, which is exactly the "L2/R2 doesn't behave like the top-bar
    // Prev/Next buttons" bug (the top-bar lambdas don't have this problem
    // because they're recreated per recomposition).
    val currentOnOpenFind by rememberUpdatedState(onOpenFind)
    val currentOnToggleToc by rememberUpdatedState(onToggleToc)
    val currentOnMarkerPrev by rememberUpdatedState(onMarkerPrev)
    val currentOnMarkerNext by rememberUpdatedState(onMarkerNext)
    val currentOnPrevAction by rememberUpdatedState(onPrevAction)
    val currentOnNextAction by rememberUpdatedState(onNextAction)
    val currentWebViewProvider by rememberUpdatedState(webViewProvider)

    // Stick scroll loop — direct StateFlow.value reads, no collectAsState.
    LaunchedEffect(isActive) {
        if (!isActive) return@LaunchedEffect
        val deadzone = 0.1f
        while (true) {
            val wv = currentWebViewProvider()
            if (wv == null) {
                delay(200L)
                continue
            }
            val scrollY = mainViewModel.browserScroll.value
            val panX = mainViewModel.browserPanX.value
            val hasInput =
                kotlin.math.abs(scrollY) > deadzone || kotlin.math.abs(panX) > deadzone
            if (hasInput) {
                val dy = (scrollY * 56).toInt()
                val dx = (panX * 56).toInt()
                if (dy != 0 || dx != 0) wv.scrollBy(dx, dy)
            }
            delay(if (hasInput) 16L else 200L)
        }
    }

    // Controller-action dispatch. Same flow GuideScreen subscribes to;
    // we just react to the actions the offline viewers know how to handle
    // and let everything else fall through. All callback invocations route
    // through the `currentOn…` rememberUpdatedState wrappers so they always
    // see the freshest closure (see comment above).
    LaunchedEffect(isActive) {
        if (!isActive) return@LaunchedEffect
        mainViewModel.controllerAction.collect { action ->
            val wv = currentWebViewProvider()
            when (action) {
                com.twinlore.app.ui.MainViewModel.ControllerAction.OPEN_FIND_ON_PAGE ->
                    currentOnOpenFind()
                com.twinlore.app.ui.MainViewModel.ControllerAction.OPEN_TOC ->
                    currentOnToggleToc()
                com.twinlore.app.ui.MainViewModel.ControllerAction.BROWSER_SCROLL_TO_TOP ->
                    wv?.scrollTo(0, 0)
                com.twinlore.app.ui.MainViewModel.ControllerAction.BROWSER_SCROLL_TO_END ->
                    wv?.evaluateJavascript(
                        "(function(){var h=Math.max(document.body.scrollHeight||0," +
                            "document.documentElement.scrollHeight||0," +
                            "document.body.offsetHeight||0," +
                            "document.documentElement.offsetHeight||0);" +
                            "window.scrollTo(0,h);})();",
                        null,
                    )
                com.twinlore.app.ui.MainViewModel.ControllerAction.BROWSER_SCROLL_MARKER_PREV ->
                    currentOnMarkerPrev()
                com.twinlore.app.ui.MainViewModel.ControllerAction.BROWSER_SCROLL_MARKER_NEXT ->
                    currentOnMarkerNext()
                com.twinlore.app.ui.MainViewModel.ControllerAction.BROWSER_PREV_BOOKMARK ->
                    currentOnPrevAction()
                com.twinlore.app.ui.MainViewModel.ControllerAction.BROWSER_NEXT_BOOKMARK ->
                    currentOnNextAction()
                else -> {} // Other actions: not handled by offline viewers
            }
        }
    }
}

fun applyOfflineChromeGestureListeners(
    webView: GuideWebView,
    topRevealThresholdPx: Int,
    onTapToggle: () -> Unit,
    onScrollDownHide: () -> Unit,
    onTopReveal: () -> Unit,
    onScrollY: (Int) -> Unit
) {
    val tapSlopPx = ViewConfiguration.get(webView.context).scaledTouchSlop
    val longPressTimeoutMs = ViewConfiguration.getLongPressTimeout().toLong()
    var downX = 0f
    var downY = 0f
    var moved = false
    webView.setOnTouchListener { _, event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x; downY = event.y; moved = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (!moved &&
                    (kotlin.math.abs(event.x - downX) > tapSlopPx ||
                        kotlin.math.abs(event.y - downY) > tapSlopPx)
                ) {
                    moved = true
                }
            }
            MotionEvent.ACTION_UP -> {
                // Only a genuine TAP toggles chrome. A long-press (hold) is a
                // down→up with no movement too, but it starts a text selection —
                // toggling chrome there resizes the WebView and shoves the freshly
                // captured selection rect, mispositioning the selection menu. Gate
                // on press duration so a hold leaves the chrome untouched.
                val heldMs = event.eventTime - event.downTime
                if (!moved && heldMs < longPressTimeoutMs) onTapToggle()
            }
        }
        false // never consume — keep scroll/select working
    }
    // Suppress the top-reveal callback for a short window after a hide.
    //
    // Why this is needed for stick scroll but not finger gesture: when chrome
    // hides, VaultSessionScreen animates the Guide tab's top padding from
    // topBarHeightDp → 0 over 200ms. That resizes the WebView. The WebView's
    // own scroll-clamp logic can briefly drop `scrollY` back into the
    // top-reveal zone during the animation — once it does, onTopReveal fires,
    // chrome re-shows, the padding animates the other way, the WebView
    // resizes again, scrollY moves, hide re-fires. Loop.
    //
    // With a finger, the user releases the touch before the layout settles
    // and the scroll listener stops firing, so the loop window is too short
    // to notice. With the stick, scroll keeps firing at 60Hz from
    // `wv.scrollBy` ticks for as long as the user holds the stick — every
    // intermediate scrollY the layout produces gets a fresh listener event.
    //
    // 500ms covers the 200ms animation plus a buffer for the WebView's
    // post-layout scroll settle. Doesn't hurt legitimate finger scrolls:
    // releasing then immediately scrolling back to the top within 500ms is
    // unusual, and even then the reveal only fires when scrollY ≤ ~21px,
    // not for any reasonable mid-page position.
    var suppressTopRevealUntil = 0L
    webView.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
        val now = System.currentTimeMillis()
        if (scrollY - oldScrollY > tapSlopPx) {
            suppressTopRevealUntil = now + 500L
            onScrollDownHide()
        }
        if (scrollY <= topRevealThresholdPx && now >= suppressTopRevealUntil) {
            onTopReveal()
        }
        onScrollY(scrollY)
    }
}

/**
 * Build a WebChromeClient that gives offline-doc viewers a working
 * HTML5/YouTube fullscreen button: a Dialog hosts the player's custom
 * view, and on dismiss we restore the underlying WebView's scroll Y.
 *
 * Lifted from `PageGroupViewerScreen.kt:918-988`.
 *
 * The `webViewRef` is read live (not captured by value) so post-create
 * WebView swaps still work.
 */
fun buildOfflineWebChromeClient(
    ctx: Context,
    webViewRef: MutableState<GuideWebView?>
): WebChromeClient = object : WebChromeClient() {
    private var customView: View? = null
    private var customViewCallback: CustomViewCallback? = null
    private var customViewDialog: Dialog? = null
    private var preFullscreenScrollY: Int = 0

    override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
        if (customView != null) { onHideCustomView(); return }
        if (view == null || callback == null) return
        val activity = contextAsActivity(ctx) ?: return
        preFullscreenScrollY = webViewRef.value?.scrollY ?: 0
        customView = view
        customViewCallback = callback
        customViewDialog = Dialog(
            activity,
            android.R.style.Theme_Black_NoTitleBar_Fullscreen
        ).apply {
            setCancelable(true)
            setCanceledOnTouchOutside(false)
            setContentView(
                view,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            setOnDismissListener {
                customViewCallback?.onCustomViewHidden()
                customViewCallback = null
                customView = null
                customViewDialog = null
                // Restore scroll. The YouTube player's exit handler
                // fires window.scrollTo(0, 0) within the first frame
                // after dismiss, and the iframe goes through a brief
                // shrunk state while it relayouts. Previously we tried
                // 0ms + 100ms with a "skip if target > maxScroll + 50"
                // guard, but on slow relayout both retries land in the
                // shrunk window and the restore is silently dropped —
                // user ends up at scroll 0.
                //
                // Strategy: keep the guard (so we never scroll into a
                // not-yet-reflowed document and pin at the bottom), but
                // poll every 50 ms for up to 1500 ms waiting for the
                // document to grow back. Stop on first successful restore.
                val target = preFullscreenScrollY
                val wv = webViewRef.value
                if (wv != null && target > 0) {
                    val deadline = android.os.SystemClock.uptimeMillis() + 1500L
                    fun tryRestore() {
                        if (android.os.SystemClock.uptimeMillis() > deadline) return
                        val maxScroll = wv.verticalScrollRangePx() - wv.height
                        if (target > maxScroll + 50) {
                            // Document still mid-relayout (too short for target).
                            // Try again on the next frame.
                            wv.postDelayed(::tryRestore, 50)
                            return
                        }
                        // Document can hold target; restore on both the Android
                        // and JS scrollers (one of them is the one the YouTube
                        // exit handler races us on).
                        wv.scrollTo(0, target)
                        wv.evaluateJavascript("window.scrollTo(0, $target);", null)
                        // YouTube's exit handler may race in after our restore;
                        // re-check at 100 ms and re-restore if we got bumped.
                        wv.postDelayed({
                            if (kotlin.math.abs(wv.scrollY - target) > 50 &&
                                android.os.SystemClock.uptimeMillis() <= deadline
                            ) tryRestore()
                        }, 100)
                    }
                    wv.post { tryRestore() }
                }
            }
        }
        customViewDialog?.show()
    }

    override fun onHideCustomView() {
        customViewDialog?.dismiss()
        customViewDialog = null
    }
}

/**
 * The visual row both offline-doc ToC overlays use for each chapter
 * entry. Carries the index prefix, label, active-border highlight, and
 * the optional warning icon for save-failed entries.
 *
 * Lifted from `PageGroupTocViewScreen.ContentsTab`'s item lambda
 * (lines 148-185) so the page-group viewer and the new single-guide
 * viewer keep identical rendering.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContentsTabRow(
    index: Int,
    label: String,
    isActive: Boolean,
    failed: Boolean,
    focusColor: Color,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    /** Hide the running "1." prefix when the labels carry their own
     *  enumeration (guide TOCs like "II. Weapons & Items") — double
     *  numbering is confusing. */
    showIndex: Boolean = true,
    modifier: Modifier = Modifier,
) {
    // combinedClickable (rather than pointerInput { detectTapGestures }) so
    // the row is FOCUSABLE — D-pad up/down can traverse the list and
    // D-pad center fires onTap. The previous pointerInput hook caught
    // touches but never registered the row with Compose's focus system, so
    // controller-only navigation skipped right over the ToC entries.
    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onTap,
                onLongClick = onLongPress
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .then(
                if (isActive) Modifier
                    .border(2.dp, focusColor, RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 4.dp)
                else Modifier
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showIndex) Text("${index + 1}.", modifier = Modifier.widthIn(min = 32.dp))
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (failed) {
            Icon(
                Icons.Filled.Warning,
                contentDescription = "Save failed",
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

/**
 * Shared bottom navigation strip for the offline guide viewers.
 *
 * Narrow opaque (alpha 0.95) bar flush with the top edge of the
 * CompactBottomBar:
 *   `< Back-label                    ×                  Fwd-label >`
 *
 * The chevron + label on each end shows what Back / Forward would go
 * to. The centre × clears both stacks. Strip hides entirely with no
 * history; either end may be empty (weighted spacers keep × centred).
 *
 * Used by both the page-group viewer (entry id + scroll restore) and
 * the single-guide viewer (scroll restore only). The composable is
 * agnostic to the caller's history model — callers resolve labels and
 * provide the back/forward actions themselves.
 */
@Composable
fun BoxScope.NavigationHistoryStrip(
    backLabel: String?,
    forwardLabel: String?,
    accentColor: androidx.compose.ui.graphics.Color,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onClear: () -> Unit,
    /**
     * How much chrome sits below this strip. The default clears the vault
     * session's bottom bar. A host that has no such bar under the viewer, like
     * the fold panel, passes zero, or the strip floats above nothing.
     */
    chromeBelow: androidx.compose.ui.unit.Dp = 66.dp
) {
    val canBack = backLabel != null
    val canForward = forwardLabel != null
    val bottomInset = WindowInsets.navigationBars
        .asPaddingValues()
        .calculateBottomPadding()
    AnimatedVisibility(
        visible = canBack || canForward,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .padding(bottom = bottomInset + chromeBelow)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // Match the CompactBottomBar's surface fill (0.95 alpha) so
                // the strip + bar read as one glass surface.
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
                .padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .then(if (canBack) Modifier.clickable(onClick = onBack) else Modifier)
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (canBack) {
                    Icon(
                        Icons.Filled.ChevronLeft,
                        contentDescription = "History back",
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(2.dp))
                    Text(
                        text = backLabel!!,
                        style = MaterialTheme.typography.labelSmall,
                        color = accentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Icon(
                Icons.Filled.Close,
                contentDescription = "Clear history",
                tint = accentColor,
                modifier = Modifier
                    .size(18.dp)
                    .clickable(onClick = onClear)
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .then(if (canForward) Modifier.clickable(onClick = onForward) else Modifier)
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (canForward) {
                    Text(
                        text = forwardLabel!!,
                        style = MaterialTheme.typography.labelSmall,
                        color = accentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.width(2.dp))
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = "History forward",
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}


/**
 * Google-Docs-style scroll-position chip on the right edge.
 *
 *   - Appears on the first scroll movement (no velocity or distance
 *     threshold), fades out 1500 ms after the last scroll. Tied to
 *     scroll inactivity, NOT to chrome visibility, so it still
 *     acknowledges scroll-while-reading without keeping the
 *     full chrome up.
 *   - Y position = `(scrollY / maxScroll) × trackHeight`, applied via
 *     `Modifier.offset`. Single math op per recompose, no re-layout.
 *   - Drag-to-scroll: drag delta in pixels → scroll delta via the
 *     `maxScroll / trackHeight` factor. `rememberUpdatedState` keeps
 *     the captured pointerInput closure reading live values.
 *
 * Label: percentage of the document (e.g. "47 %").
 *
 * Caller is responsible for disabling the native WebView scrollbar
 * (`isVerticalScrollBarEnabled = false`) so the two don't fight.
 */
@Composable
fun BoxScope.ScrollIndicatorChip(
    scrollY: Int,
    maxScroll: Int,
    onScrollTo: (Int) -> Unit,
) {
    val ratio = if (maxScroll > 0) (scrollY.toFloat() / maxScroll.toFloat()).coerceIn(0f, 1f) else 0f
    var isDragging by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }
    // Shows on the first pixel of movement: no velocity or distance gate.
    // The first run of the effect below sees prevScrollY == scrollY (both
    // seeded from the same value), so entering the composition - the
    // chip is only composed once maxScroll > 0 and is torn down while
    // find / ToC are open - never flashes it by itself.
    var prevScrollY by remember { mutableStateOf(scrollY) }
    // Counter incremented on each qualifying "show" event. Used as a
    // key on the hide-timer LaunchedEffect so every scroll event re-arms
    // the 1500ms timer from the last movement.
    var showTick by remember { mutableStateOf(0) }
    // Pass 1: per-scroll bookkeeping. Bumps showTick on any movement.
    LaunchedEffect(scrollY) {
        if (scrollIndicatorShouldShow(prevScrollY, scrollY)) showTick++
        prevScrollY = scrollY
    }
    // Pass 2: visibility timer. Show on every showTick (or while
    // dragging); hide 1500ms after the last tick / drag-end.
    LaunchedEffect(showTick, isDragging) {
        if (showTick == 0 && !isDragging) return@LaunchedEffect
        visible = true
        if (!isDragging) {
            delay(1500)
            visible = false
        }
    }
    val density = LocalDensity.current
    val scrollYState by rememberUpdatedState(scrollY)
    val maxScrollState by rememberUpdatedState(maxScroll)
    val onScrollToFn by rememberUpdatedState(onScrollTo)
    BoxWithConstraints(
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .fillMaxHeight()
            .width(96.dp)
    ) {
        // Top inset = top bar (~40dp). Bottom inset = chrome stack
        // (history strip + scroll-to-edge button + bottom bar ~140dp).
        // trackHeight excludes both so the chip never overlaps chrome.
        val containerHpx = with(density) { maxHeight.toPx() }
        val chipHpx = with(density) { 32.dp.toPx() }
        val topInsetPx = with(density) { 56.dp.toPx() }
        val bottomInsetPx = with(density) { 156.dp.toPx() }
        val trackHeight = (containerHpx - chipHpx - topInsetPx - bottomInsetPx).coerceAtLeast(1f)
        val chipYpx = (ratio * trackHeight + topInsetPx).toInt()
        // Push the pill past the right edge so its trailing side sits
        // partly off-screen — a small visual cue that it's a pulled-out
        // handle. The drag-indicator dots remain fully visible.
        val overhangPx = with(density) { 14.dp.toPx().toInt() }
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopEnd),
        ) {
            androidx.compose.material3.Surface(
                modifier = Modifier
                    .offset { IntOffset(overhangPx, chipYpx) }
                    .height(32.dp)
                    .width(82.dp)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { isDragging = true },
                            onDragEnd = { isDragging = false },
                            onDragCancel = { isDragging = false },
                            onDrag = { _, drag ->
                                val maxS = maxScrollState
                                if (maxS <= 0 || trackHeight <= 0f) return@detectDragGestures
                                val factor = maxS.toFloat() / trackHeight
                                val newScroll = (scrollYState + drag.y * factor)
                                    .toInt()
                                    .coerceIn(0, maxS)
                                onScrollToFn(newScroll)
                            }
                        )
                    },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                shadowElevation = 4.dp,
                tonalElevation = 2.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${(ratio * 100).toInt()} %",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Filled.DragIndicator,
                        contentDescription = "Drag to scrub",
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

/**
 * True when a scroll event moved the document at all, which is all it
 * takes to surface [ScrollIndicatorChip]. Before 1.13 the chip was gated
 * on a decaying "scroll energy" (300ms half-life, 400px threshold), so
 * reading-pace scrolls never showed it.
 */
internal fun scrollIndicatorShouldShow(prevScrollY: Int, scrollY: Int): Boolean =
    scrollY != prevScrollY

/**
 * Lowercased host component of [url], or null when [url] is null / blank /
 * malformed, or when the URL has no host (e.g. `cid:` MHT inline refs,
 * `file://` local paths, opaque schemes like `javascript:`).
 *
 * Used by the offline viewers' long-press handlers to compare an anchor
 * href's host against the saved page's host (see [isExternalAnchor]).
 * The "no host → null" semantics keep MHT-backed `cid:` / `file://`
 * anchors classified as same-host (the safe default → image dialog).
 */
internal fun parseHost(url: String?): String? {
    if (url.isNullOrBlank()) return null
    return try {
        Uri.parse(url).host?.lowercase()?.ifEmpty { null }
    } catch (_: Exception) {
        null
    }
}

/**
 * True when [anchorHref] points to a different host than [docUrl]
 * (case-insensitive), i.e. the anchor leaves the saved page's origin.
 *
 * Returns false whenever either side fails to yield a host
 * ([parseHost] returns null) — relative URLs, fragments, blank
 * strings, malformed URLs, and `cid:` / `file://` MHT-internal refs
 * are all treated as "same-host". This is the safe default for the
 * long-press routing: ambiguity → image dialog (existing behaviour)
 * rather than link dialog (which only makes sense for genuinely
 * external links).
 *
 * Calling sites: `SingleGuideViewerScreen.setOnLongClickListener` +
 * `PageGroupViewerScreen.setOnLongClickListener`, both for the
 * `SRC_IMAGE_ANCHOR_TYPE` branch.
 */
internal fun isExternalAnchor(docUrl: String?, anchorHref: String?): Boolean {
    val docHost = parseHost(docUrl) ?: return false
    val anchorHost = parseHost(anchorHref) ?: return false
    return !anchorHost.equals(docHost, ignoreCase = true)
}

/**
 * True when [anchorHref] is an in-document anchor: it carries a fragment
 * and points back at the current document. Two shapes qualify, mirroring
 * the same-page-anchor classification in the viewers'
 * `shouldOverrideUrlLoading`:
 *
 *  - host is `localhost.invalid` — both offline viewers load via
 *    `loadDataWithBaseURL("https://localhost.invalid/", …)`, so a bare
 *    `#frag` href resolves to that host (HitTestResult.extra is always
 *    the resolved absolute URL);
 *  - same host as the saved document's original URL ([docUrl] =
 *    `bookmark.url` for the single viewer, `activeEntry.savedHref` for the
 *    page-group viewer) — covers fully-qualified ToC/section hrefs without
 *    depending on exact path/scheme string equality, which proved too
 *    brittle (www vs bare host, http vs https, trailing slashes).
 *
 * Used by the long-press handlers to route local anchors to
 * [copyAnchorTextToClipboard] instead of the link dialog.
 */
/**
 * Normalized (host, path+query) pair for an http(s) URL; null for anything else.
 * Host is lowercased with a leading `www.` stripped; the rest drops the fragment
 * and any trailing slash but KEEPS the query (paginated FAQ entries differ only
 * by `?page=N`). Kotlin mirror of the `gtSplit` JS in `buildLocalLinkMarkerJs` —
 * the decorator (JS) and the tap-navigation matcher (Kotlin) must classify
 * identically or links render local but navigate external.
 */
internal fun splitNormalizedHttpUrl(url: String?): Pair<String, String>? {
    if (url.isNullOrBlank()) return null
    val m = Regex("^https?://([^/#?]+)([^#]*)", RegexOption.IGNORE_CASE).find(url) ?: return null
    var host = m.groupValues[1].lowercase()
    if (host.startsWith("www.")) host = host.removePrefix("www.")
    var rest = m.groupValues[2]
    if (rest.length > 1 && rest.endsWith("/")) rest = rest.dropLast(1)
    if (rest == "/") rest = ""
    return host to rest
}

internal fun isLocalAnchor(docUrl: String?, anchorHref: String?): Boolean {
    if (anchorHref.isNullOrBlank()) return false
    if (parseHost(anchorHref)?.equals("localhost.invalid", ignoreCase = true) == true) return true
    return !isExternalAnchor(docUrl, anchorHref)
}

/**
 * Copy the long-pressed anchor's TEXT (not its href) to the clipboard and
 * confirm with the viewers' usual "Copied to clipboard" toast.
 *
 * `WebView.HitTestResult` only exposes the href, so this mirrors the
 * `requestImageRef` Handler pattern the image long-press path uses:
 * [android.webkit.WebView.requestFocusNodeHref] resolves the focused
 * anchor and delivers its text in the message's `"title"` field.
 *
 * No-op (no toast) when the anchor has no text — e.g. icon-only anchors,
 * which the badge JS skips for the same reason.
 */
internal fun copyAnchorTextToClipboard(webView: android.webkit.WebView) {
    val handler = android.os.Handler(android.os.Looper.getMainLooper()) { msg ->
        val text = msg.data.getString("title")?.trim().orEmpty()
        // Fallback: some hit-test results carry no anchor text (image-ish
        // anchors, odd markup) — copying the URL beats silently doing nothing.
        val url = msg.data.getString("url")?.trim().orEmpty()
        val (clipText, toast) = when {
            text.isNotEmpty() -> text to "Copied to clipboard"
            url.isNotEmpty() -> url to "Link copied"
            else -> null to null
        }
        if (clipText != null) {
            val ctx = webView.context
            val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE)
                as? android.content.ClipboardManager
            clipboard?.setPrimaryClip(
                android.content.ClipData.newPlainText("link text", clipText)
            )
            android.widget.Toast.makeText(ctx, toast, android.widget.Toast.LENGTH_SHORT).show()
        } else {
            android.util.Log.d("AnchorCopy", "requestFocusNodeHref returned no title/url")
        }
        true
    }
    webView.requestFocusNodeHref(handler.obtainMessage())
}
