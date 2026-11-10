package com.twinlore.app.ui.screens

import android.content.Context
import android.graphics.Rect
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.ValueCallback
import com.twinlore.app.data.privacy.PrivacyDelegatingWebViewClient
import com.twinlore.app.data.privacy.PrivacyInterceptor
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * WebView for Guide and Browser tabs.
 * - When [customSelectionActions] is set (Guide tab): custom text selection menu (Copy, Save Marker, Find in Page, YouTube, Search online, Select All).
 * - When null (Browser tab): default Android text selection bar (Copy, Share, etc.).
 */
class GuideWebView(context: Context) : WebView(context) {

    companion object {
        private const val ID_SAVE_MARKER = 0x7F0A0002
        private const val ID_FIND_IN_PAGE = 0x7F0A0003
        private const val ID_YOUTUBE = 0x7F0A0004
        private const val ID_SEARCH_ONLINE = 0x7F0A0005
        private const val ID_COPY = 0x7F0A0006
        private const val ID_SELECT_ALL = 0x7F0A0007
    }

    /** When set (Guide tab), the system ActionMode bar is shown with our custom menu items. When null (Browser tab), default bar is used. */
    @Volatile
    var customSelectionActions: CustomSelectionActions? = null

    /**
     * True while a selection ActionMode is up, false when it goes.
     *
     * Nothing reads it today. It existed to let the fold panel take focus for
     * the toolbar's lifetime, and that turned out to destroy the selection it
     * was meant to serve: changing FLAG_NOT_FOCUSABLE relayouts the window, and
     * the WebView drops the selection with it, so a long press did nothing at
     * all. Left in place because the hook itself is sound and the next attempt
     * at the toolbar will want it.
     */
    @Volatile
    var onSelectionModeChanged: ((Boolean) -> Unit)? = null

    /** Cached selection rect in view coordinates (updated by JS selectionchange; used by onGetContentRect). */
    private var lastSelectionRect: Rect? = null
    private val selectionRectLock = Any()

    /**
     * Live CSS-px → device-px factor (= [getScale]; already folds in density).
     * The JS selection bridge reports `getBoundingClientRect()` in CSS px and
     * runs on the JavaBridge thread, where calling [getScale] isn't safe — so we
     * cache the scale here, refreshed on the UI thread whenever it changes.
     *
     * Must be the page scale, NOT raw display density: the guide WebViews use
     * `useWideViewPort` + `loadWithOverviewMode`, so desktop-width guides render
     * zoomed out (scale < density) and pinch-zoom changes it further. Converting
     * with density alone over-scales the rect, an error that grows with Y — so
     * the selection menu drifts further down the lower the selection sits (and
     * the injected top/bottom scroll padding, which pushes every Y down, makes
     * it worse).
     */
    @Volatile private var cssToDevicePx: Float = context.resources.displayMetrics.density

    private fun refreshScale() {
        val s = scale
        if (s > 0.01f) cssToDevicePx = s
    }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        refreshScale()
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        refreshScale()
    }

    init {
        addJavascriptInterface(
            object {
                @android.webkit.JavascriptInterface
                fun setSelectionRect(left: Double, top: Double, right: Double, bottom: Double) {
                    val s = cssToDevicePx
                    val r = Rect(
                        (left * s).toInt(),
                        (top * s).toInt(),
                        (right * s).toInt(),
                        (bottom * s).toInt()
                    )
                    synchronized(selectionRectLock) { lastSelectionRect = r }
                }
            },
            "SelectionRectBridge"
        )
    }

    /**
     * Set custom selection menu actions (Guide tab). Pass null for Browser tab to use default Android bar.
     */
    fun setCustomSelectionMenuActions(actions: CustomSelectionActions?) {
        customSelectionActions = actions
    }

    data class CustomSelectionActions(
        val showSaveMarker: Boolean,
        /** Called with (selectedText, occurrenceIndex). occurrenceIndex is 1-based; 0 if not determined. */
        val onSaveMarker: ((String, Int) -> Unit)?,
        val onFindInPage: (String) -> Unit,
        val onYouTube: (String) -> Unit,
        val onSearchOnline: (String) -> Unit,
        val onCopy: (String) -> Unit,
        /**
         * Copy and Save Marker only. The fold panel cannot leave the reader
         * for YouTube or a web search, and its find bar is a tap away.
         */
        val copyAndMarkerOnly: Boolean = false,
    )

    private val privacyInterceptor: PrivacyInterceptor =
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            GuideWebViewEntryPoint::class.java,
        ).privacyInterceptor()

    override fun setWebViewClient(client: WebViewClient) {
        super.setWebViewClient(PrivacyDelegatingWebViewClient.wrap(client, privacyInterceptor))
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    internal interface GuideWebViewEntryPoint {
        fun privacyInterceptor(): PrivacyInterceptor
    }

    override fun startActionMode(callback: ActionMode.Callback): ActionMode? {
        return startActionMode(callback, android.view.ActionMode.TYPE_FLOATING)
    }

    override fun startActionMode(callback: ActionMode.Callback, type: Int): ActionMode? {
        val actions = customSelectionActions
        val useFloating = (type == android.view.ActionMode.TYPE_FLOATING)
        val effective = if (actions != null) {
            replaceWithCustomSelectionMenu(callback, actions, useFloating)
        } else {
            callback
        }
        val effectiveType = if (actions != null) android.view.ActionMode.TYPE_FLOATING else type
        // Null means the window has no DecorView to make a toolbar: the fold
        // panel's overlay window. Without a mode the WebView keeps the word it
        // highlighted but drops the handles and the menu, so supply one.
        return super.startActionMode(effective, effectiveType)
            ?: HostlessSelectionActionMode(this, effective).start()
    }

    private fun getSelectionContentRect(view: android.view.View, outRect: Rect) {
        val rect = synchronized(selectionRectLock) { lastSelectionRect?.let { Rect(it) } }
        if (rect != null && rect.width() > 0 && rect.height() > 0 &&
            rect.left < view.width && rect.top < view.height && rect.right > 0 && rect.bottom > 0) {
            outRect.set(rect)
        } else {
            outRect.set(0, view.height.coerceAtLeast(1) - 1, view.width, view.height)
        }
    }

    /** Custom selection menu items: Copy first, Select All last (utility bookends around the contextual actions). */
    private fun populateSelectionMenu(menu: Menu, actions: CustomSelectionActions) {
        menu.clear()
        menu.add(Menu.NONE, ID_COPY, 1, "Copy")
        if (actions.onSaveMarker != null) {
            menu.add(Menu.NONE, ID_SAVE_MARKER, 2, "Save Marker")
        }
        if (actions.copyAndMarkerOnly) return
        menu.add(Menu.NONE, ID_FIND_IN_PAGE, 3, "Find in Page")
        menu.add(Menu.NONE, ID_YOUTUBE, 4, "YouTube")
        menu.add(Menu.NONE, ID_SEARCH_ONLINE, 5, "Search online")
        menu.add(Menu.NONE, ID_SELECT_ALL, 6, "Select All")
    }

    private fun replaceWithCustomSelectionMenu(original: ActionMode.Callback, actions: CustomSelectionActions, useFloating: Boolean): ActionMode.Callback {
        val callback = object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                if (mode == null || menu == null) return false
                val created = original.onCreateActionMode(mode, menu)
                if (!created) return false
                populateSelectionMenu(menu, actions)
                onSelectionModeChanged?.invoke(true)
                return true
            }

            override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                original.onPrepareActionMode(mode, menu)
                if (menu != null) populateSelectionMenu(menu, actions)
                return true
            }

            override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
                if (mode == null || item == null) return false
                val action: (() -> Unit)? = when (item.itemId) {
                    ID_SAVE_MARKER -> actions.onSaveMarker?.let { cb -> { runWithSelectionAndOccurrence(cb, mode) } }
                    ID_FIND_IN_PAGE -> ({ runWithSelection(actions.onFindInPage, mode) })
                    ID_YOUTUBE -> ({ runWithSelection(actions.onYouTube, mode) })
                    ID_SEARCH_ONLINE -> ({ runWithSelection(actions.onSearchOnline, mode) })
                    ID_COPY -> ({ runWithSelection(actions.onCopy, mode) })
                    ID_SELECT_ALL -> ({ selectAllInDocument(mode) })
                    else -> null
                }
                if (action != null) {
                    action.invoke()
                    return true
                }
                return original.onActionItemClicked(mode, item)
            }

            override fun onDestroyActionMode(mode: ActionMode?) {
                onSelectionModeChanged?.invoke(false)
                original.onDestroyActionMode(mode)
            }
        }
        if (!useFloating) return callback
        return object : ActionMode.Callback2() {
            override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean = callback.onCreateActionMode(mode, menu)
            override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean = callback.onPrepareActionMode(mode, menu)
            override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean = callback.onActionItemClicked(mode, item)
            override fun onDestroyActionMode(mode: ActionMode?) { callback.onDestroyActionMode(mode) }
            override fun onGetContentRect(mode: ActionMode?, view: android.view.View?, outRect: Rect?) {
                if (view != null && outRect != null) getSelectionContentRect(view, outRect)
                else super.onGetContentRect(mode, view, outRect)
            }
        }
    }

    /**
     * Select the entire readable document text. Keeps the action mode open
     * so the user can immediately Copy / Find / etc. on the new selection;
     * once the selectionchange listener (SELECTION_RECT_JS) has reported the
     * new rect to SelectionRectBridge we invalidate the content rect so the
     * floating toolbar repositions over it.
     */
    private fun selectAllInDocument(mode: ActionMode) {
        evaluateJavascript(
            """
            (function() {
                try {
                    if (document.execCommand('selectAll')) return;
                } catch (e) { /* fall through to Range */ }
                try {
                    var range = document.createRange();
                    range.selectNodeContents(document.body);
                    var sel = window.getSelection();
                    sel.removeAllRanges();
                    sel.addRange(range);
                } catch (e) { /* swallow: selection stays as-is */ }
            })();
            """.trimIndent(),
            ValueCallback {
                // Give the selectionchange → SelectionRectBridge round-trip a
                // beat before repositioning the floating toolbar.
                postDelayed({ runCatching { mode.invalidateContentRect() } }, 120L)
            }
        )
    }

    private fun runWithSelection(action: (String) -> Unit, mode: ActionMode) {
        evaluateJavascript(
            "(function(){return window.getSelection().toString()})()",
            ValueCallback { value ->
                val selection = parseSelectionResult(value)?.trim().orEmpty()
                action(selection)
                mode.finish()
            }
        )
    }

    private fun runWithSelectionAndOccurrence(action: (String, Int) -> Unit, mode: ActionMode) {
        evaluateJavascript(
            """
            (function() {
                function isTextNodeVisible(tn) {
                    var el = tn.parentElement;
                    if (!el) return false;
                    while (el && el !== document.body) {
                        var tag = (el.tagName || '').toUpperCase();
                        if (tag === 'SCRIPT' || tag === 'STYLE' || tag === 'NOSCRIPT' || tag === 'HEAD') return false;
                        var cs = window.getComputedStyle(el);
                        if (cs.display === 'none' || cs.visibility === 'hidden') return false;
                        el = el.parentElement;
                    }
                    return true;
                }
                var sel = window.getSelection();
                var text = sel.toString().trim();
                if (!text) return JSON.stringify({text:'', occurrenceIndex:0, totalCount:0});
                var selRange = sel.rangeCount > 0 ? sel.getRangeAt(0) : null;
                var queryLower = text.toLowerCase();
                var matches = [];
                var walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, null, false);
                var node;
                while (node = walker.nextNode()) {
                    if (!isTextNodeVisible(node)) continue;
                    var nodeText = node.textContent;
                    if (!nodeText) continue;
                    var idx = 0;
                    while ((idx = nodeText.toLowerCase().indexOf(queryLower, idx)) !== -1) {
                        var r = document.createRange();
                        r.setStart(node, idx);
                        r.setEnd(node, idx + text.length);
                        matches.push(r);
                        idx += text.length;
                    }
                }
                var occurrenceIndex = 0;
                if (selRange && matches.length > 0) {
                    for (var i = 0; i < matches.length; i++) {
                        try {
                            var cmpStart = selRange.compareBoundaryPoints(Range.START_TO_START, matches[i]);
                            var cmpEnd = selRange.compareBoundaryPoints(Range.END_TO_START, matches[i]);
                            if (cmpStart >= 0 && cmpEnd <= 0) {
                                occurrenceIndex = i + 1;
                                break;
                            }
                        } catch (e) {}
                    }
                    if (occurrenceIndex === 0 && matches.length > 0) occurrenceIndex = 1;
                }
                return JSON.stringify({ text: text, occurrenceIndex: occurrenceIndex, totalCount: matches.length });
            })();
            """.trimIndent(),
            ValueCallback { value ->
                val raw = value?.removeSurrounding("\"")?.replace("\\\"", "\"") ?: "{}"
                val text: String
                val occurrenceIndex: Int
                try {
                    val json = org.json.JSONObject(raw)
                    text = json.optString("text", "").trim()
                    occurrenceIndex = json.optInt("occurrenceIndex", 0).coerceIn(0, Int.MAX_VALUE)
                } catch (_: Exception) {
                    action("", 0)
                    mode.finish()
                    return@ValueCallback
                }
                action(text, occurrenceIndex)
                mode.finish()
            }
        )
    }

    private fun parseSelectionResult(value: String?): String? {
        if (value == null || value == "null") return null
        return value.removeSurrounding("\"").replace("\\\"", "\"")
    }

    /**
     * Public bridge to the protected `View.computeVerticalScrollRange()` so
     * scroll listeners in callers (e.g. PageGroupViewerScreen's near-end
     * detector) can compute the page-progress ratio in the same coordinate
     * space as `View.scrollY`. Same value the View returns; no caching.
     */
    fun verticalScrollRangePx(): Int = computeVerticalScrollRange()
}
