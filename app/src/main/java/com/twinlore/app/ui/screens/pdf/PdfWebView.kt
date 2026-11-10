package com.twinlore.app.ui.screens.pdf

import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.view.ActionMode
import android.view.Choreographer
import android.view.InputDevice
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import android.webkit.ValueCallback

/**
 * WebView that loads the pdf.js viewer from assets and displays a PDF.
 * Supports custom text selection menu (Search online, YouTube, Copy) and link interception.
 */
class PdfWebView(context: Context, isDark: Boolean = true) : WebView(context) {

    companion object {
        private const val TAG = "PdfWebView"
        private const val ID_SEARCH_ONLINE = 0x7F0B0001
        private const val ID_YOUTUBE = 0x7F0B0002
        private const val ID_COPY = 0x7F0B0003
    }

    var customSelectionActions: CustomSelectionActions? = null
    var onLinkClicked: ((url: String) -> Unit)? = null

    /** True once viewer.html has finished loading and PDFViewerApplication is available. */
    private var viewerReady = false
    /** Path of the PDF currently loaded (or being loaded) in pdf.js. Null until the first loadPdf call. */
    private var currentFilePath: String? = null
    /** Queued while waiting for viewer.html's onPageFinished. */
    private var pendingFilePath: String? = null
    private var pendingInitialPage: Int = 0

    data class CustomSelectionActions(
        val onSearchOnline: (String) -> Unit,
        val onYouTube: (String) -> Unit,
        val onCopy: (String) -> Unit
    )

    init {
        settings.apply {
            javaScriptEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            domStorageEnabled = true
            builtInZoomControls = false
            setSupportZoom(false)
            @Suppress("DEPRECATION")
            allowFileAccessFromFileURLs = true
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs = false
            // Performance: enable caching. WebView is hardware-accelerated by default;
            // do NOT force setLayerType(LAYER_TYPE_HARDWARE) — it adds GPU pressure and
            // was contributing to GPU-process crashes under rapid guide switching.
            cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, isDark)
        }
        // Surface pdf.js console messages to logcat so release-only JS failures are diagnosable.
        webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                consoleMessage ?: return true
                Log.d(TAG, "JS[${consoleMessage.messageLevel()}] ${consoleMessage.sourceId()}:${consoleMessage.lineNumber()} ${consoleMessage.message()}")
                return true
            }
        }
        webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                view ?: return
                // Only treat the viewer.html page as "viewer ready". pdf.js itself triggers
                // subsequent navigations (to the PDF file URL) that also fire onPageFinished —
                // those must not re-run our load logic.
                if (url == null || !url.contains("pdfjs/web/viewer.html")) {
                    return
                }
                viewerReady = true
                val filePath = pendingFilePath
                val page = pendingInitialPage
                pendingFilePath = null
                Log.d(TAG, "onPageFinished viewer.html ready, pending=$filePath page=$page")
                if (filePath != null) {
                    openPdfInternal(filePath, page)
                }
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                // Only intercept main frame navigations (actual link clicks),
                // not subresource loads (images, fonts, etc. inside the PDF)
                val isMainFrame = request?.isForMainFrame == true
                if ((url.startsWith("http://") || url.startsWith("https://")) && isMainFrame) {
                    onLinkClicked?.invoke(url)
                    return true
                }
                if (url.startsWith("file://")) return false
                return isMainFrame // Block unsupported schemes only for main frame
            }
        }
    }

    /**
     * Single entry point to show a PDF. Idempotent and safe to call multiple times:
     *
     * - If the viewer hasn't been loaded yet, loads viewer.html and queues the file to open
     *   when `onPageFinished` fires.
     * - If the viewer is ready and the requested file is different, swaps the document via
     *   `PDFViewerApplication.open()` — no viewer.html reload needed.
     * - If the same file is requested again, just jumps to `initialPage` if specified.
     *
     * This replaces the previous [loadViewer] + [setPendingPdfFile] + [openPdfFile] triad,
     * which was prone to double-invocation races when called from a Compose effect that
     * restarted under rapid recomposition.
     */
    fun loadPdf(filePath: String, initialPage: Int = 0) {
        if (filePath == currentFilePath && viewerReady) {
            Log.d(TAG, "loadPdf: same file already loaded, jumping to page=$initialPage")
            if (initialPage > 0) goToPage(initialPage)
            return
        }
        Log.d(TAG, "loadPdf path=$filePath initialPage=$initialPage viewerReady=$viewerReady")
        currentFilePath = filePath
        pendingInitialPage = initialPage
        if (viewerReady) {
            // Viewer is already up; swap the document in place.
            openPdfInternal(filePath, initialPage)
        } else {
            // First load (or viewer still booting): queue and load viewer.html.
            pendingFilePath = filePath
            loadUrl("file:///android_asset/pdfjs/web/viewer.html?file=")
        }
    }

    /**
     * Inject the JS that drives `PDFViewerApplication.open(...)`. Only called once viewer.html
     * has finished loading (i.e., [viewerReady] is true).
     */
    private fun openPdfInternal(filePath: String, initialPage: Int) {
        val fileUrl = "file://$filePath"
        val escaped = fileUrl.replace("\\", "\\\\").replace("'", "\\'")
        evaluateJavascript(
            """
            (function() {
                // Polyfill: ReadableStream async iteration (for await...of) is only available in
                // Chromium 124+. Older WebViews (e.g. 109 on many Samsung devices) don't provide
                // ReadableStream.prototype[Symbol.asyncIterator], which pdf.js 5.x relies on in
                // getTextContent() — without this polyfill, find-on-page silently returns zero
                // matches because text extraction throws "readableStream is not async iterable".
                if (typeof ReadableStream !== 'undefined' && !ReadableStream.prototype[Symbol.asyncIterator]) {
                    ReadableStream.prototype[Symbol.asyncIterator] = function() {
                        var reader = this.getReader();
                        return {
                            next: function() {
                                return reader.read().then(function(result) {
                                    if (result.done) { reader.releaseLock(); }
                                    return result;
                                }, function(err) { reader.releaseLock(); throw err; });
                            },
                            'return': function(value) {
                                reader.releaseLock();
                                return Promise.resolve({ value: value, done: true });
                            },
                            [Symbol.asyncIterator]: function() { return this; }
                        };
                    };
                }
                if (typeof PDFViewerApplication === 'undefined') { return; }
                PDFViewerApplication.initializedPromise.then(function() {
                    // One-time tuning + event handler registration. Guarded so that opening
                    // a second PDF on the same viewer doesn't re-register duplicate handlers
                    // (which would cause every bridge callback to fire twice).
                    if (!window.__pdfBridgeInit) {
                        try {
                            if (typeof AppOptions !== 'undefined') {
                                AppOptions.set('disableAutoFetch', false);
                                AppOptions.set('enableXfa', false);
                                AppOptions.set('disableRange', true);
                                AppOptions.set('maxCanvasPixels', 16777216);  // 16MP cap — keeps nearby pages sharp during scroll/zoom; bounded by smaller page cache below
                            }
                        } catch(e) { console.warn('AppOptions tuning failed:', e); }

                        var eb = PDFViewerApplication.eventBus;
                        eb.on('pagechanging', function(e) {
                            Android.onPageChanged(e.pageNumber, PDFViewerApplication.pagesCount || 0);
                        });
                        eb.on('updatefindmatchescount', function(e) {
                            var r = e.matchesCount || {};
                            Android.onFindResult(r.current || 0, r.total || 0);
                        });
                        eb.on('updatefindcontrolstate', function(e) {
                            var r = e.matchesCount || {};
                            Android.onFindResult(r.current || 0, r.total || 0);
                        });
                        eb.on('documentloaded', function() {
                            var total = PDFViewerApplication.pagesCount || 0;
                            Android.onDocumentLoaded(total);
                            window.__pdfExtractOutline && window.__pdfExtractOutline();
                        });

                        // Dynamic render-buffer shrink at high zoom. At >3x scale each
                        // cached canvas approaches the maxCanvasPixels cap (~64 MB) so
                        // we shrink the LRU cache from 25 back to 10 to keep total
                        // memory bounded. setCacheSize is exposed by our viewer.mjs
                        // patch (GAMETWIN-RENDER-TUNE).
                        eb.on('scalechanging', function () {
                            try {
                                var s = PDFViewerApplication.pdfViewer.currentScale || 1;
                                var target = s > 3.0 ? 10 : 25;
                                if (window.__pdfLastCacheTarget !== target) {
                                    window.__pdfLastCacheTarget = target;
                                    if (typeof PDFViewerApplication.pdfViewer.setCacheSize === 'function') {
                                        PDFViewerApplication.pdfViewer.setCacheSize(target);
                                    }
                                }
                            } catch (e) {}
                        });

                        window.__pdfBridgeInit = true;
                    }

                    // Outline extraction is attached as a window-scoped function so the
                    // one-time documentloaded handler can call it for every subsequent PDF.
                    window.__pdfExtractOutline = function() {
                        try {
                            var pdfDoc = PDFViewerApplication.pdfDocument;
                            if (!pdfDoc) { Android.onOutlineReady('[]'); return; }
                            pdfDoc.getOutline().then(function(outline) {
                                try {
                                    if (!outline || outline.length === 0) {
                                        Android.onOutlineReady('[]');
                                        return;
                                    }
                                    function walkOutline(items) {
                                        var result = [];
                                        for (var i = 0; i < items.length; i++) {
                                            var item = items[i];
                                            var entry = { title: item.title || '', dest: null, children: [] };
                                            if (item.dest) entry.dest = item.dest;
                                            if (item.items && item.items.length > 0) entry.children = walkOutline(item.items);
                                            result.push(entry);
                                        }
                                        return result;
                                    }
                                    var tree = walkOutline(outline);
                                    function resolvePages(entries) {
                                        var promises = [];
                                        entries.forEach(function(e) {
                                            if (e.dest && typeof e.dest === 'string') {
                                                promises.push(pdfDoc.getDestination(e.dest).then(function(d) {
                                                    if (d && d[0]) return pdfDoc.getPageIndex(d[0]).then(function(idx) { e.pageNumber = idx + 1; });
                                                }).catch(function() {}));
                                            } else if (e.dest && Array.isArray(e.dest) && e.dest[0]) {
                                                promises.push(pdfDoc.getPageIndex(e.dest[0]).then(function(idx) { e.pageNumber = idx + 1; }).catch(function() {}));
                                            }
                                            if (e.children.length > 0) promises.push(resolvePages(e.children));
                                        });
                                        return Promise.all(promises);
                                    }
                                    var timeout = new Promise(function(resolve) { setTimeout(resolve, 5000); });
                                    Promise.race([resolvePages(tree), timeout]).then(function() {
                                        function clean(entries) {
                                            return entries.map(function(e) {
                                                return { title: e.title, pageNumber: e.pageNumber || 0, children: clean(e.children) };
                                            });
                                        }
                                        Android.onOutlineReady(JSON.stringify(clean(tree)));
                                    }).catch(function() { Android.onOutlineReady('[]'); });
                                } catch(ex) { Android.onOutlineReady('[]'); }
                            }).catch(function() { Android.onOutlineReady('[]'); });
                        } catch(ex) { Android.onOutlineReady('[]'); }
                    };

                    PDFViewerApplication.open({ url: '$escaped' }).then(function() {
                        if ($initialPage > 0) {
                            PDFViewerApplication.page = $initialPage;
                        }

                        // One-shot text-extraction probe — read the first 3 pages
                        // (or all if smaller) and report whether the doc has any
                        // extractable text. Scanned/image-only PDFs return ~empty
                        // text content; real text PDFs hit the 8-char threshold
                        // easily even on cover/index pages.
                        try {
                            window.__pdfTextProbeFired = false;
                            var probePages = Math.min(3, PDFViewerApplication.pagesCount || 0);
                            if (probePages <= 0) {
                                window.__pdfTextProbeFired = true;
                                Android.onTextProbe(true);
                            } else {
                                var requests = [];
                                for (var i = 1; i <= probePages; i++) {
                                    requests.push(
                                        PDFViewerApplication.pdfDocument.getPage(i)
                                            .then(function (p) { return p.getTextContent(); })
                                            .then(function (tc) {
                                                var s = '';
                                                for (var k = 0; k < tc.items.length; k++) {
                                                    s += tc.items[k].str;
                                                }
                                                return s.replace(/\s+/g, '').length;
                                            })
                                            .catch(function () { return 0; })
                                    );
                                }
                                Promise.all(requests).then(function (counts) {
                                    if (window.__pdfTextProbeFired) return;
                                    window.__pdfTextProbeFired = true;
                                    var total = counts.reduce(function (a, b) { return a + b; }, 0);
                                    Android.onTextProbe(total >= 8);
                                });
                                // 3s safety timeout: if the probe stalls, default to
                                // "has text" so we never falsely toast a scanned warning.
                                setTimeout(function () {
                                    if (window.__pdfTextProbeFired) return;
                                    window.__pdfTextProbeFired = true;
                                    Android.onTextProbe(true);
                                }, 3000);
                            }
                        } catch (e) {
                            if (!window.__pdfTextProbeFired) {
                                window.__pdfTextProbeFired = true;
                                Android.onTextProbe(true);
                            }
                        }
                    }).catch(function(err) {
                        console.error('PDF open failed:', err);
                    });
                }).catch(function(err) {
                    console.error('PDF viewer init failed:', err);
                });
            })();
            """.trimIndent(),
            null
        )
    }

    fun goToPage(page: Int) {
        evaluateJavascript(
            "if (typeof PDFViewerApplication !== 'undefined') { PDFViewerApplication.page = ${page.coerceAtLeast(1)}; }",
            null
        )
    }

    // ---- Synthetic touch scroll for gamepad stick input ----
    //
    // Using JS scrollBy() from a Kotlin loop forces main-thread scroll: every call fires a
    // `scroll` event that pdf.js handles synchronously, which causes visible stutter at high
    // zoom. Real finger scrolls go through Chromium's threaded compositor scroll path (no
    // per-frame main-thread work), which is why touch feels smooth even at high speed.
    //
    // To get the same fast path for stick scroll, we synthesize a single-pointer touch drag
    // on this WebView: ACTION_DOWN when the stick first engages, ACTION_MOVE every vsync frame
    // as the position integrates the velocity, and ACTION_CANCEL when the stick returns to
    // rest (CANCEL avoids tap detection and avoids fling momentum that would override
    // continuous control).
    private var synthTouchActive = false
    private var synthTouchX = 0f
    private var synthTouchY = 0f
    private var synthTouchDownTime = 0L
    private var synthStickVx = 0f // touch-space px/sec; sign opposite scroll velocity
    private var synthStickVy = 0f
    private var synthLastFrameNanos = 0L
    private val synthFrameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!synthTouchActive) { synthLastFrameNanos = 0L; return }
            if (synthStickVx == 0f && synthStickVy == 0f) {
                dispatchSynthTouch(MotionEvent.ACTION_CANCEL, synthTouchX, synthTouchY)
                synthTouchActive = false
                synthLastFrameNanos = 0L
                return
            }
            val dt = if (synthLastFrameNanos == 0L) 0f else (frameTimeNanos - synthLastFrameNanos) / 1_000_000_000f
            synthLastFrameNanos = frameTimeNanos
            synthTouchX += synthStickVx * dt
            synthTouchY += synthStickVy * dt
            // Keep the synthetic finger inside the view; if it would leave, wrap back to center
            // so we can keep dragging indefinitely.
            val w = width; val h = height
            if (w > 0 && h > 0) {
                val margin = 32f
                if (synthTouchX < margin || synthTouchX > w - margin ||
                    synthTouchY < margin || synthTouchY > h - margin
                ) {
                    dispatchSynthTouch(MotionEvent.ACTION_CANCEL, synthTouchX, synthTouchY)
                    synthTouchX = (w / 2).toFloat()
                    synthTouchY = (h / 2).toFloat()
                    synthTouchDownTime = SystemClock.uptimeMillis()
                    dispatchSynthTouch(MotionEvent.ACTION_DOWN, synthTouchX, synthTouchY)
                }
            }
            dispatchSynthTouch(MotionEvent.ACTION_MOVE, synthTouchX, synthTouchY)
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    private fun dispatchSynthTouch(action: Int, x: Float, y: Float) {
        val now = SystemClock.uptimeMillis()
        val ev = MotionEvent.obtain(synthTouchDownTime, now, action, x, y, 0)
        ev.source = InputDevice.SOURCE_TOUCHSCREEN
        try { dispatchTouchEvent(ev) } finally { ev.recycle() }
    }

    fun pdfSetStickScroll(vxPxPerSec: Float, vyPxPerSec: Float) {
        // Touch-space velocity is the negation of scroll velocity:
        // to scroll content down (scrollTop +), the finger drags up (y -).
        synthStickVx = -vxPxPerSec
        synthStickVy = -vyPxPerSec
        if ((synthStickVx != 0f || synthStickVy != 0f) && !synthTouchActive) {
            val w = width; val h = height
            if (w <= 0 || h <= 0) return
            synthTouchX = (w / 2).toFloat()
            synthTouchY = (h / 2).toFloat()
            synthTouchDownTime = SystemClock.uptimeMillis()
            dispatchSynthTouch(MotionEvent.ACTION_DOWN, synthTouchX, synthTouchY)
            synthTouchActive = true
            synthLastFrameNanos = 0L
            Choreographer.getInstance().postFrameCallback(synthFrameCallback)
        }
    }

    /**
     * Continuous stick-driven zoom. [velocityLnPerSec] is the zoom speed in natural-log
     * scale units per second (positive = zoom in, negative = zoom out). The JS side runs
     * a requestAnimationFrame loop that multiplies currentScale by exp(velocity * dt) each
     * frame, giving smooth multiplicative zoom.  Pass 0 to stop.
     */
    fun pdfSetStickZoom(velocityLnPerSec: Float) {
        evaluateJavascript(
            """(function(){
                if(typeof PDFViewerApplication==='undefined')return;
                window.__pdfStickZoomV=$velocityLnPerSec;
                if(window.__pdfStickZoomRunning)return;
                if($velocityLnPerSec===0)return;
                window.__pdfStickZoomRunning=true;
                var viewer=PDFViewerApplication.pdfViewer;
                var last=0;
                function frame(ts){
                    var v=window.__pdfStickZoomV||0;
                    if(v===0){window.__pdfStickZoomRunning=false;return;}
                    if(last===0){last=ts;requestAnimationFrame(frame);return;}
                    var dt=(ts-last)/1000;
                    if(dt<0.1){requestAnimationFrame(frame);return;}
                    last=ts;
                    var s=viewer.currentScale*Math.exp(v*dt);
                    s=Math.max(0.15,Math.min(10,s));
                    viewer.currentScale=s;
                    requestAnimationFrame(frame);
                }
                requestAnimationFrame(frame);
            })()""".trimIndent(),
            null
        )
    }

    /** Reset zoom to fit the current view. In grid mode, fits all pages on screen. */
    fun pdfFitToView() {
        evaluateJavascript(
            """(function() {
                if (typeof PDFViewerApplication === 'undefined') return;
                var v = PDFViewerApplication.pdfViewer;
                if (v.scrollMode === 2) {
                    // Grid mode: fit all pages on screen
                    var totalPages = v.pagesCount || 1;
                    var container = v.container || document.getElementById('viewerContainer');
                    var cw = container ? container.clientWidth : window.innerWidth;
                    var ch = container ? container.clientHeight : window.innerHeight;
                    var pv = v.getPageView(0);
                    var pw = pv ? (pv.viewport.width / pv.viewport.scale) : 600;
                    var ph = pv ? (pv.viewport.height / pv.viewport.scale) : 800;
                    var bestScale = 0.1;
                    for (var cols = 1; cols <= totalPages; cols++) {
                        var rows = Math.ceil(totalPages / cols);
                        var scaleW = cw / (cols * pw + (cols - 1) * 10);
                        var scaleH = ch / (rows * ph + (rows - 1) * 10);
                        var s = Math.min(scaleW, scaleH);
                        if (cols === 1 || s > bestScale) bestScale = s;
                        if (rows <= cols) break;
                    }
                    v.currentScale = Math.max(0.02, bestScale * 0.9);
                } else {
                    v.currentScaleValue = 'page-fit';
                }
            })();""",
            null
        )
    }

    /**
     * Set view mode: 0 = single page, 1 = spread, 2 = grid (wrapped).
     * Grid mode zooms out so multiple pages fit per row.
     * Returns the actual mode via callback.
     */
    fun pdfSetViewMode(mode: Int, callback: (Int) -> Unit) {
        evaluateJavascript(
            """(function() {
                if (typeof PDFViewerApplication === 'undefined') return 0;
                var v = PDFViewerApplication.pdfViewer;
                // Remove grid tap handler when leaving grid mode
                if (window._gridClickHandler) {
                    document.getElementById('viewer').removeEventListener('click', window._gridClickHandler);
                    window._gridClickHandler = null;
                }
                if ($mode === 0) {
                    v.spreadMode = 0; v.scrollMode = 0;
                    v.currentScaleValue = 'page-fit';
                } else if ($mode === 1) {
                    v.spreadMode = 1; v.scrollMode = 0;
                    v.currentScaleValue = 'page-fit';
                } else if ($mode === 2) {
                    v.spreadMode = 0; v.scrollMode = 2;
                    // Zoom out so all pages fit in a grid on screen
                    var totalPages = v.pagesCount || 1;
                    var container = v.container || document.getElementById('viewerContainer');
                    var cw = container ? container.clientWidth : window.innerWidth;
                    var ch = container ? container.clientHeight : window.innerHeight;
                    var pv = v.getPageView(0);
                    var pw = pv ? (pv.viewport.width / pv.viewport.scale) : 600;
                    var ph = pv ? (pv.viewport.height / pv.viewport.scale) : 800;
                    var bestScale = 0.1;
                    for (var cols = 1; cols <= totalPages; cols++) {
                        var rows = Math.ceil(totalPages / cols);
                        var scaleW = cw / (cols * pw + (cols - 1) * 10);
                        var scaleH = ch / (rows * ph + (rows - 1) * 10);
                        var s = Math.min(scaleW, scaleH);
                        if (cols === 1 || s > bestScale) bestScale = s;
                        if (rows <= cols) break;
                    }
                    v.currentScale = Math.max(0.02, bestScale * 0.9);
                    // Install tap handler: tap a page → go to single view
                    window._gridClickHandler = function(e) {
                        var el = e.target;
                        while (el && el.id !== 'viewer') {
                            if (el.classList && el.classList.contains('page')) {
                                var pageNum = parseInt(el.getAttribute('data-page-number'), 10);
                                if (pageNum > 0) { Android.onGridPageTap(pageNum); }
                                break;
                            }
                            el = el.parentNode;
                        }
                    };
                    document.getElementById('viewer').addEventListener('click', window._gridClickHandler);
                }
                return $mode;
            })();"""
        ) { result ->
            callback(result?.toIntOrNull() ?: 0)
        }
    }

    /** Read current view mode: 0 = single, 1 = spread, 2 = grid. */
    fun pdfGetViewMode(callback: (Int) -> Unit) {
        evaluateJavascript(
            """(function() {
                if (typeof PDFViewerApplication === 'undefined') return 0;
                var v = PDFViewerApplication.pdfViewer;
                if (v.scrollMode === 2) return 2;
                if (v.spreadMode !== 0) return 1;
                return 0;
            })();"""
        ) { result ->
            callback(result?.toIntOrNull() ?: 0)
        }
    }

    fun findNext(query: String) {
        Log.d(TAG, "findNext query='$query'")
        val escaped = query.replace("\\", "\\\\").replace("'", "\\'")
        evaluateJavascript(
            """
            if (typeof PDFViewerApplication !== 'undefined') {
                PDFViewerApplication.eventBus.dispatch('find', {
                    source: window,
                    type: 'again',
                    query: '$escaped',
                    caseSensitive: false,
                    highlightAll: true,
                    phraseSearch: true,
                    findPrevious: false
                });
            }
            """.trimIndent(),
            null
        )
    }

    fun findPrevious(query: String) {
        Log.d(TAG, "findPrevious query='$query'")
        val escaped = query.replace("\\", "\\\\").replace("'", "\\'")
        evaluateJavascript(
            """
            if (typeof PDFViewerApplication !== 'undefined') {
                PDFViewerApplication.eventBus.dispatch('find', {
                    source: window,
                    type: 'again',
                    query: '$escaped',
                    caseSensitive: false,
                    highlightAll: true,
                    phraseSearch: true,
                    findPrevious: true
                });
            }
            """.trimIndent(),
            null
        )
    }

    fun setFindQuery(query: String) {
        Log.d(TAG, "setFindQuery query='$query'")
        val escaped = query.replace("\\", "\\\\").replace("'", "\\'")
        evaluateJavascript(
            """
            if (typeof PDFViewerApplication !== 'undefined') {
                PDFViewerApplication.eventBus.dispatch('find', {
                    source: window,
                    query: '$escaped',
                    caseSensitive: false,
                    highlightAll: true,
                    phraseSearch: true,
                    findPrevious: false
                });
            }
            """.trimIndent(),
            null
        )
    }

    override fun startActionMode(callback: ActionMode.Callback): ActionMode? {
        return startActionMode(callback, ActionMode.TYPE_FLOATING)
    }

    override fun startActionMode(callback: ActionMode.Callback, type: Int): ActionMode? {
        val actions = customSelectionActions
        return if (actions != null) {
            super.startActionMode(replaceWithCustomSelectionMenu(callback, actions), ActionMode.TYPE_FLOATING)
        } else {
            super.startActionMode(callback, type)
        }
    }

    private fun replaceWithCustomSelectionMenu(original: ActionMode.Callback, actions: CustomSelectionActions): ActionMode.Callback {
        if (original is ActionMode.Callback2) {
            return object : ActionMode.Callback2() {
                override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                    if (mode == null || menu == null) return false
                    original.onCreateActionMode(mode, menu)
                    menu.clear()
                    menu.add(Menu.NONE, ID_SEARCH_ONLINE, 1, "Search online")
                    menu.add(Menu.NONE, ID_YOUTUBE, 2, "YouTube")
                    menu.add(Menu.NONE, ID_COPY, 3, "Copy")
                    return true
                }

                override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                    return true
                }

                override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
                    if (mode == null || item == null) return false
                    when (item.itemId) {
                        ID_SEARCH_ONLINE -> runWithSelection(actions.onSearchOnline, mode)
                        ID_YOUTUBE -> runWithSelection(actions.onYouTube, mode)
                        ID_COPY -> runWithSelection(actions.onCopy, mode)
                        else -> return false
                    }
                    return true
                }

                override fun onDestroyActionMode(mode: ActionMode?) {
                    original.onDestroyActionMode(mode)
                }

                override fun onGetContentRect(mode: ActionMode?, view: android.view.View?, outRect: android.graphics.Rect?) {
                    original.onGetContentRect(mode, view, outRect)
                }
            }
        }
        return object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                if (mode == null || menu == null) return false
                original.onCreateActionMode(mode, menu)
                menu.clear()
                menu.add(Menu.NONE, ID_SEARCH_ONLINE, 1, "Search online")
                menu.add(Menu.NONE, ID_YOUTUBE, 2, "YouTube")
                menu.add(Menu.NONE, ID_COPY, 3, "Copy")
                return true
            }

            override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                return true
            }

            override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
                if (mode == null || item == null) return false
                when (item.itemId) {
                    ID_SEARCH_ONLINE -> runWithSelection(actions.onSearchOnline, mode)
                    ID_YOUTUBE -> runWithSelection(actions.onYouTube, mode)
                    ID_COPY -> runWithSelection(actions.onCopy, mode)
                    else -> return false
                }
                return true
            }

            override fun onDestroyActionMode(mode: ActionMode?) {
                original.onDestroyActionMode(mode)
            }
        }
    }

    private fun runWithSelection(action: (String) -> Unit, mode: ActionMode) {
        evaluateJavascript(
            "(function(){ return window.getSelection().toString(); })()",
            ValueCallback { value ->
                var selection = value?.removeSurrounding("\"")?.replace("\\\"", "\"")?.trim().orEmpty()
                // PDF.js has a dual text layer that can cause the selection to be doubled.
                // Detect and fix: if the text is exactly repeated, take the first half.
                if (selection.length >= 2 && selection.length % 2 == 0) {
                    val half = selection.length / 2
                    val first = selection.substring(0, half)
                    val second = selection.substring(half)
                    if (first == second) selection = first
                }
                action(selection)
                mode.finish()
            }
        )
    }
}
