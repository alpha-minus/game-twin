package com.twinlore.app.ui.screens.pdf

import android.util.Log
import android.webkit.JavascriptInterface

/**
 * JavaScript interface for the pdf.js viewer.
 * The viewer (viewer.html) or injected scripts call these from JS via window.Android.*.
 */
class PdfBridge(
    private val pageChangedCallback: (page: Int, total: Int) -> Unit,
    private val documentLoadedCallback: (totalPages: Int) -> Unit,
    private val outlineReadyCallback: (json: String) -> Unit,
    private val findResultCallback: (current: Int, total: Int) -> Unit,
    private val scrollPositionChangedCallback: (page: Int, offset: Float) -> Unit,
    private val linkClickedCallback: (url: String) -> Unit,
    private val gridPageTapCallback: (page: Int) -> Unit = {},
    private val textProbeCallback: (hasText: Boolean) -> Unit = {},
) {
    companion object {
        private const val TAG = "PdfBridge"
    }

    @JavascriptInterface
    fun onPageChanged(page: Int, total: Int) {
        Log.d(TAG, "onPageChanged: $page / $total")
        pageChangedCallback(page, total)
    }

    @JavascriptInterface
    fun onDocumentLoaded(totalPages: Int) {
        Log.d(TAG, "onDocumentLoaded: totalPages=$totalPages")
        documentLoadedCallback(totalPages)
    }

    @JavascriptInterface
    fun onOutlineReady(json: String) {
        Log.d(TAG, "onOutlineReady: ${json.take(200)}...")
        outlineReadyCallback(json)
    }

    @JavascriptInterface
    fun onFindResult(current: Int, total: Int) {
        Log.d(TAG, "onFindResult: $current / $total")
        findResultCallback(current, total)
    }

    @JavascriptInterface
    fun onScrollPositionChanged(page: Int, offset: Float) {
        scrollPositionChangedCallback(page, offset)
    }

    @JavascriptInterface
    fun onLinkClicked(url: String) {
        Log.d(TAG, "onLinkClicked: $url")
        linkClickedCallback(url)
    }

    @JavascriptInterface
    fun onGridPageTap(page: Int) {
        Log.d(TAG, "onGridPageTap: $page")
        gridPageTapCallback(page)
    }

    @JavascriptInterface
    fun onTextProbe(hasText: Boolean) {
        Log.d(TAG, "onTextProbe: hasText=$hasText")
        textProbeCallback(hasText)
    }

}
