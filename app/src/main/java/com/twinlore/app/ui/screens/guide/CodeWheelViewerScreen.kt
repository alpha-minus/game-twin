package com.twinlore.app.ui.screens.guide

import android.annotation.SuppressLint
import android.net.Uri
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.twinlore.app.data.codewheel.CodeWheelCacheManager
import com.twinlore.app.data.library.ResourceResolver
import com.twinlore.app.domain.SavedTab
import com.twinlore.app.ui.MainViewModel
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.withContext
import java.io.File

// Rotation and layer switching are button-driven by synthesizing the keyboard
// events the wheel template already handles, so touch stays free for native
// pan/pinch/double-tap/tap. keydown(which 37/39) rotates one detent (24°);
// keyup(keyCode 38/40) switches the active layer. keyCode/which are not
// settable via the constructor, so force them. When [returnLabel] is set, the
// active layer's label is returned so we can surface it.
private fun keyDispatchJs(code: Int, returnLabel: Boolean): String {
    val tail = if (returnLabel) {
        "var a=document.querySelector('.nav .active')||document.querySelector('.menu .active')||document.querySelector('.active');" +
            "return a?(a.textContent||'').replace(/\\s+/g,' ').trim():'';"
    } else {
        ""
    }
    return """
(function (c) {
  function k(type) {
    var e = new KeyboardEvent(type, { bubbles: true, cancelable: true });
    try { Object.defineProperty(e, 'keyCode', { get: function () { return c; } }); } catch (_) {}
    try { Object.defineProperty(e, 'which', { get: function () { return c; } }); } catch (_) {}
    document.dispatchEvent(e);
  }
  k('keydown'); k('keyup');
  $tail
})($code);
"""
}

// Probe: do the template's rotation globals exist? Gate the fine-nudge buttons
// on this so a non-standard wheel just shows the coarse detent controls.
private const val FINE_SUPPORT_PROBE_JS =
    "(typeof currAngle === 'number' && typeof angled === 'number')"

// Rotate the active wheel by [fraction] of its OWN detent, read live from the
// page's global `angled`. Drives the same globals (`currAngle`, `target`,
// `storeAngles`) the template's AnimateWheel + drag paths use, so fine nudges
// compose with coarse detent presses and persist to the active layer. Returns
// 'nofine' (a no-op) if the globals are missing.
internal fun fineRotateJs(fraction: Float): String = """
(function (f) {
  try {
    if (typeof currAngle === 'number' && window.target && typeof target.rotate === 'function') {
      var step = (typeof angled === 'number' ? angled : 24);
      currAngle += step * f;
      target.rotate({ animateTo: currAngle });
      if (typeof prevAngle === 'number') prevAngle = currAngle;
      if (typeof storeAngles === 'function') storeAngles();
      return 'ok';
    }
  } catch (e) {}
  return 'nofine';
})($fraction);
"""

// Natural content size (CSS px) as "width|height" so we can scale the whole
// wheel to fit the screen in either dimension.
private const val MEASURE_SIZE_JS =
    "(Math.max(document.documentElement.scrollWidth, (document.body ? document.body.scrollWidth : 0))) + '|' + " +
        "(Math.max(document.documentElement.scrollHeight, (document.body ? document.body.scrollHeight : 0)))"

@EntryPoint
@InstallIn(SingletonComponent::class)
interface CodeWheelViewerEntryPoint {
    fun cacheManager(): CodeWheelCacheManager
    fun resourceResolver(): ResourceResolver
}

@Suppress("DEPRECATION")
private fun webScale(v: WebView): Float = v.scale

/** Rotate control: a quick tap nudges slowly ([onTapSlow] — a fine, sub-detent
 *  step for precise alignment); holding escalates to fast, repeated full-detent
 *  spinning ([onHoldFast]). */
@Composable
private fun RotateButton(
    imageVector: ImageVector,
    contentDescription: String,
    onTapSlow: () -> Unit,
    onHoldFast: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    LaunchedEffect(pressed) {
        if (pressed) {
            onTapSlow()                         // tap = one slow, fine nudge
            kotlinx.coroutines.delay(350)       // held past this -> fast coarse spin
            while (pressed) {
                onHoldFast()
                kotlinx.coroutines.delay(150)   // fast-spin cadence (higher = slower)
            }
        }
    }
    IconButton(onClick = {}, interactionSource = interaction, modifier = Modifier.size(WHEEL_BUTTON_SIZE)) {
        Icon(imageVector, contentDescription = contentDescription, modifier = Modifier.size(WHEEL_ICON_SIZE))
    }
}

/** Slightly enlarged hit targets / glyphs for the wheel control cluster. */
private val WHEEL_BUTTON_SIZE = 56.dp
private val WHEEL_ICON_SIZE = 30.dp

/** Slow-tap nudge as a fraction of the wheel's own detent (tunable on device). */
private const val FINE_FRACTION = 1f / 6f

/** Double-tap zoom-in factor over the fitted scale, matching the image/map viewer. */
private const val DOUBLE_TAP_ZOOM = 3.5f

/** Measure the wheel and scale it so the WHOLE page fits the screen (limited by
 *  width or height, whichever is tighter), then reload — which both applies the
 *  load-time scale (not clamped by the overview minimum) and resets scroll to
 *  the top-left. [fitHolder] caches the computed percent for re-fits. */
@Suppress("DEPRECATION")
private fun requestFit(view: WebView, fitHolder: IntArray) {
    view.evaluateJavascript(MEASURE_SIZE_JS) { res ->
        val parts = (res ?: "").trim().trim('"').split("|")
        val cw = parts.getOrNull(0)?.toFloatOrNull() ?: return@evaluateJavascript
        val ch = parts.getOrNull(1)?.toFloatOrNull() ?: return@evaluateJavascript
        val vw = view.width
        val vh = view.height
        if (cw <= 0f || ch <= 0f || vw <= 0 || vh <= 0) return@evaluateJavascript
        val pct = (minOf(vw / cw, vh / ch) * 100f).toInt().coerceIn(10, 500)
        fitHolder[0] = pct
        view.setInitialScale(pct)
        view.reload()
    }
}

@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
fun CodeWheelViewerScreen(
    guide: SavedTab,
    vaultId: String,
    mainViewModel: MainViewModel? = null,
    isTabVisible: Boolean = true,
    contentFocusRequester: FocusRequester? = null,
    bottomBarFocusRequester: FocusRequester? = null,
    onShowGuidePicker: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val isWheel = guide.fileType == "wheel"
    val entryPoint = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            CodeWheelViewerEntryPoint::class.java
        )
    }

    var entryFileUrl by remember(guide.id) { mutableStateOf<String?>(null) }
    var error by remember(guide.id) { mutableStateOf<String?>(null) }
    var webView by remember(guide.id) { mutableStateOf<WebView?>(null) }
    // Whether this wheel exposes the template rotation globals (gates fine buttons).
    var fineSupported by remember(guide.id) { mutableStateOf(false) }

    // Transient layer-name label shown when the active layer changes.
    var layerLabel by remember { mutableStateOf<String?>(null) }
    var layerLabelAt by remember { mutableStateOf(0L) }
    LaunchedEffect(layerLabelAt) {
        if (layerLabel != null) {
            kotlinx.coroutines.delay(1600)
            if (System.currentTimeMillis() - layerLabelAt >= 1500) layerLabel = null
        }
    }

    // Auto-hide chrome (mirrors PdfGuideViewer). Drives the vault-session top/
    // bottom bars too via mainViewModel, so a tap reveals the full chrome.
    var showTopBar by remember { mutableStateOf(true) }
    var lastInteractionTime by remember { mutableStateOf(0L) }
    fun touchInteraction() {
        lastInteractionTime = System.currentTimeMillis()
        showTopBar = true
    }
    LaunchedEffect(showTopBar, lastInteractionTime) {
        if (showTopBar) {
            kotlinx.coroutines.delay(5000)
            if (System.currentTimeMillis() - lastInteractionTime >= 5000) {
                showTopBar = false
            }
        }
    }
    LaunchedEffect(showTopBar, isTabVisible, mainViewModel) {
        if (mainViewModel != null && isTabVisible) {
            mainViewModel.setVaultSessionGuideChromeVisible(showTopBar)
        }
    }
    LaunchedEffect(isTabVisible, mainViewModel) {
        if (mainViewModel == null || !isTabVisible) return@LaunchedEffect
        // drop(1): the StateFlow replays its current value on collect; only a
        // NEW interaction should reset the auto-hide timer.
        mainViewModel.chromeInteractionTrigger.drop(1).collect { touchInteraction() }
    }
    // Resume + repaint the WebView once it exists and the tab is visible.
    // Without this the WebView can render BLACK on first open (and after
    // returning from another tab) until a visibility change forces a redraw.
    LaunchedEffect(webView, isTabVisible) {
        val wv = webView ?: return@LaunchedEffect
        if (isTabVisible) {
            wv.onResume()
            wv.invalidate()
        } else {
            wv.onPause()
        }
    }

    // 0 = not yet fitted. Array so the WebViewClient closure can mutate it
    // across the measure->reload round trip.
    val fitPercent = remember(guide.id) { intArrayOf(0) }
    // The realized fit scale, captured after the one-time initial fit, so the
    // Fit button can reset the view (zoom/scroll) without reloading the wheel.
    val fitScale = remember(guide.id) { floatArrayOf(0f) }

    LaunchedEffect(guide.id) {
        val rid = guide.resourceId
        if (rid == null) {
            error = "File not found."
            return@LaunchedEffect
        }
        val result = withContext(Dispatchers.IO) {
            val uri: Uri = entryPoint.resourceResolver().resolve(rid)
                ?: return@withContext null
            val dir = File(context.cacheDir, "code_wheel_render/${guide.id}")
            context.contentResolver.openInputStream(uri)?.use { input ->
                entryPoint.cacheManager().extract(input, dir)
            }
        }
        if (result == null) {
            error = "Could not open code wheel."
        } else {
            entryFileUrl = Uri.fromFile(result).toString()
        }
    }

    // Left analog stick scrolls / pans the wheel view; the physical d-pad is
    // reserved for rotate (LEFT/RIGHT) + layer switch (UP/DOWN). Mirrors
    // GuideScreen's stick-scroll loop but drives the wheel's OWN WebView.
    LaunchedEffect(isTabVisible, webView, mainViewModel) {
        val vm = mainViewModel ?: return@LaunchedEffect
        if (!isTabVisible) return@LaunchedEffect
        val wv = webView ?: return@LaunchedEffect
        val deadzone = 0.1f
        while (true) {
            val scroll = vm.browserScroll.value
            val panX = vm.browserPanX.value
            val hasInput = kotlin.math.abs(scroll) > deadzone || kotlin.math.abs(panX) > deadzone
            if (hasInput) {
                val sy = (scroll * 56).toInt()
                val sx = (panX * 56).toInt()
                if (sx != 0 || sy != 0) wv.scrollBy(sx, sy)
            }
            kotlinx.coroutines.delay(if (hasInput) 16L else 200L)
        }
    }

    BackHandler(onBack = onClose)

    fun rotate(code: Int) {
        webView?.evaluateJavascript(keyDispatchJs(code, returnLabel = false), null)
    }

    fun fineRotate(fraction: Float) {
        webView?.evaluateJavascript(fineRotateJs(fraction), null)
    }

    fun switchLayer(code: Int) {
        val wv = webView ?: return
        wv.evaluateJavascript(keyDispatchJs(code, returnLabel = true)) { res ->
            val label = (res ?: "").trim().trim('"').takeIf { it.isNotBlank() }
            layerLabel = label ?: "Layer changed"
            layerLabelAt = System.currentTimeMillis()
        }
    }

    // Reset the VIEW only (zoom back to fit + scroll to top), NOT the wheel —
    // no reload, so rotation/layer state is preserved. Like double-tap-to-fit.
    fun resetView() {
        val wv = webView ?: return
        val fit = fitScale[0]
        val cur = webScale(wv)
        if (fit > 0f && cur > 0f) {
            @Suppress("DEPRECATION")
            wv.zoomBy((fit / cur).coerceIn(0.01f, 100f))
        }
        wv.scrollTo(0, 0)
    }

    // Double-tap toggles between fit and a strong zoom-in (matching the
    // image/map viewer's 3.5x), recentering on the tapped point. We own this
    // gesture instead of the WebView's milder native double-tap zoom.
    fun doubleTapZoom(tapX: Float, tapY: Float) {
        val wv = webView ?: return
        val fit = fitScale[0]
        if (fit <= 0f) return
        val cur = webScale(wv)
        if (cur <= 0f) return
        val zoomingIn = cur <= fit * 1.1f
        val target = if (zoomingIn) fit * DOUBLE_TAP_ZOOM else fit
        // Content point (in pre-zoom device px from the content origin) under the
        // finger, so we can put it back under the finger after the scale changes.
        val contentX = wv.scrollX + tapX
        val contentY = wv.scrollY + tapY
        val ratio = target / cur
        @Suppress("DEPRECATION")
        wv.zoomBy(ratio.coerceIn(0.01f, 100f))
        val targetX = if (zoomingIn) (contentX * ratio - tapX).toInt().coerceAtLeast(0) else 0
        val targetY = if (zoomingIn) (contentY * ratio - tapY).toInt().coerceAtLeast(0) else 0
        // zoomBy animates; recenter once the scale has settled near the target.
        fun settle(attempt: Int) {
            val s = webScale(wv)
            if (kotlin.math.abs(s - target) / target < 0.02f || attempt > 24) {
                wv.scrollTo(targetX, targetY)
            } else {
                wv.postDelayed({ settle(attempt + 1) }, 16)
            }
        }
        wv.postDelayed({ settle(0) }, 16)
    }

    val surfaceBg = MaterialTheme.colorScheme.surface

    // Focusable capture layer: bound to the vault-session contentFocusRequester so
    // D-pad UP from the bottom bar lands here, then the d-pad drives the wheel —
    // LEFT/RIGHT rotate (tap = slow fine nudge, hold auto-repeats into fast full
    // detents), UP/DOWN switch layers. We consume those keys so the WebView neither
    // rotates nor scrolls on them (scroll/pan is the left stick; return to the bar
    // is B/Back). The WebView itself is non-focusable so it never steals key input.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(if (contentFocusRequester != null) Modifier.focusRequester(contentFocusRequester) else Modifier)
            .focusable()
            .onKeyEvent { ke ->
                val native = ke.nativeKeyEvent
                val isDpad = native.keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
                    native.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ||
                    native.keyCode == KeyEvent.KEYCODE_DPAD_UP ||
                    native.keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                if (native.action != KeyEvent.ACTION_DOWN) {
                    // Swallow the matching key-up so it can't leak to the chrome.
                    return@onKeyEvent isDpad
                }
                when (native.keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        if (native.repeatCount == 0 && fineSupported) fineRotate(-FINE_FRACTION) else rotate(37)
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (native.repeatCount == 0 && fineSupported) fineRotate(FINE_FRACTION) else rotate(39)
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_UP -> { if (native.repeatCount == 0) switchLayer(38); true }
                    KeyEvent.KEYCODE_DPAD_DOWN -> { if (native.repeatCount == 0) switchLayer(40); true }
                    else -> false
                }
            }
    ) {
        when {
            error != null -> Text(
                text = error!!,
                modifier = Modifier.align(Alignment.Center)
            )
            entryFileUrl == null -> CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center)
            )
            else -> AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        @Suppress("DEPRECATION")
                        settings.allowFileAccess = true
                        @Suppress("DEPRECATION")
                        settings.allowFileAccessFromFileURLs = false
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.setSupportZoom(true)
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        // Non-focusable: the Compose capture layer above owns all
                        // key input (d-pad rotate/layer). Touch pan/pinch still works
                        // — touch is delivered regardless of focusability.
                        isFocusable = false
                        isFocusableInTouchMode = false
                        // Transparent so the parent surface (not black) shows
                        // through during the fit reload's blank frame.
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)

                        // A confirmed single tap toggles chrome; a double tap zooms
                        // (we own it so it goes deeper than the WebView's native
                        // double-tap). Drag/pinch fall through to the WebView for
                        // native pan/zoom. We only consume events while a double tap
                        // is in flight, so the native double-tap zoom never fires.
                        var consumingDoubleTap = false
                        val gestureDetector = GestureDetector(
                            ctx,
                            object : GestureDetector.SimpleOnGestureListener() {
                                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                                    if (showTopBar) showTopBar = false else touchInteraction()
                                    return false
                                }
                                override fun onDoubleTap(e: MotionEvent): Boolean {
                                    consumingDoubleTap = true
                                    doubleTapZoom(e.x, e.y)
                                    return true
                                }
                            }
                        )
                        setOnTouchListener { _, ev ->
                            gestureDetector.onTouchEvent(ev)
                            when (ev.actionMasked) {
                                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                    val consumed = consumingDoubleTap
                                    consumingDoubleTap = false
                                    consumed
                                }
                                else -> consumingDoubleTap
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView, url: String) {
                                if (fitPercent[0] == 0) {
                                    // Fit once after layout settles (measure -> reload).
                                    view.postDelayed({ requestFit(view, fitPercent) }, 150)
                                } else if (fitScale[0] == 0f) {
                                    // Post-fit reload: capture the realized scale for resetView().
                                    view.postDelayed({ fitScale[0] = webScale(view) }, 250)
                                }
                                // Detect the template's rotation globals so the
                                // fine-nudge buttons only show where they'll work.
                                view.evaluateJavascript(FINE_SUPPORT_PROBE_JS) { res ->
                                    fineSupported = res?.trim()?.trim('"') == "true"
                                }
                                // Force a repaint after (re)load — the fit reload
                                // can otherwise leave the surface black until a
                                // visibility change.
                                view.postInvalidate()
                            }
                        }
                        webView = this
                        loadUrl(entryFileUrl!!)
                    }
                },
                onRelease = { view ->
                    if (webView === view) webView = null
                    try {
                        view.stopLoading()
                        view.loadUrl("about:blank")
                        view.removeAllViews()
                        view.destroy()
                    } catch (_: Throwable) {
                    }
                },
            )
        }

        // Top bar (auto-hide), flush to the top — no Scaffold padding gap.
        AnimatedVisibility(
            visible = showTopBar && error == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Surface(
                color = surfaceBg.copy(alpha = 0.95f),
                tonalElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(onClick = { onShowGuidePicker(); touchInteraction() }) {
                        Icon(Icons.Filled.FolderOpen, contentDescription = "Guide picker")
                    }
                    Text(
                        text = guide.displayTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Transient active-layer label.
        layerLabel?.let { label ->
            Surface(
                color = surfaceBg.copy(alpha = 0.9f),
                tonalElevation = 8.dp,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                )
            }
        }

        // Always-visible controls: rotate, switch layer, fit-to-screen.
        if (entryFileUrl != null && error == null) {
            Surface(
                color = surfaceBg.copy(alpha = 0.85f),
                tonalElevation = 6.dp,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (isWheel) {
                        // Tap = slow fine nudge (align a drifting answer); hold =
                        // fast full-detent spin. The fine nudge needs the wheel's
                        // rotation globals; without them a tap falls back to one
                        // detent, so the buttons still work everywhere.
                        Row {
                            RotateButton(
                                imageVector = Icons.Filled.RotateLeft,
                                contentDescription = "Rotate left (tap slow, hold fast)",
                                onTapSlow = { if (fineSupported) fineRotate(-FINE_FRACTION) else rotate(37) },
                                onHoldFast = { rotate(37) },
                            )
                            RotateButton(
                                imageVector = Icons.Filled.RotateRight,
                                contentDescription = "Rotate right (tap slow, hold fast)",
                                onTapSlow = { if (fineSupported) fineRotate(FINE_FRACTION) else rotate(39) },
                                onHoldFast = { rotate(39) },
                            )
                        }
                        Row {
                            IconButton(onClick = { switchLayer(38) }, modifier = Modifier.size(WHEEL_BUTTON_SIZE)) {
                                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Previous wheel layer", modifier = Modifier.size(WHEEL_ICON_SIZE))
                            }
                            IconButton(onClick = { switchLayer(40) }, modifier = Modifier.size(WHEEL_BUTTON_SIZE)) {
                                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Next wheel layer", modifier = Modifier.size(WHEEL_ICON_SIZE))
                            }
                        }
                    }
                    IconButton(onClick = { resetView() }, modifier = Modifier.size(WHEEL_BUTTON_SIZE)) {
                        Icon(Icons.Filled.FitScreen, contentDescription = "Fit to screen", modifier = Modifier.size(WHEEL_ICON_SIZE))
                    }
                }
            }
        }
    }
}
