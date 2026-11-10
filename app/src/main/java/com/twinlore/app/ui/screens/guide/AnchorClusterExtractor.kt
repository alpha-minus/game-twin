package com.twinlore.app.ui.screens.guide

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import org.json.JSONArray

/**
 * One detected chapter from the in-page anchor cluster (or TXT-FAQ
 * heading list). [targetScrollY] is in CSS pixels — the document Y
 * coordinate of the section the chapter points at.
 */
data class SingleGuideTocEntry(
    val label: String,
    val fragment: String,
    val targetScrollY: Int,
)

/**
 * Parse a JSON array string emitted by the anchor-cluster extractor
 * into a list of [SingleGuideTocEntry]. Visible for unit tests that
 * exercise the parser without a live WebView.
 *
 * Drops malformed rows (empty label / fragment, negative targetScrollY).
 */
internal fun parseAnchorClusterJson(raw: String?): List<SingleGuideTocEntry> {
    val cleaned = raw
        ?.trim()
        ?.removeSurrounding("\"")
        ?.replace("\\\"", "\"")
        ?.replace("\\\\", "\\")
        ?: "[]"
    return runCatching {
        val arr = JSONArray(cleaned)
        (0 until arr.length()).mapNotNull { i ->
            val obj = arr.optJSONObject(i) ?: return@mapNotNull null
            val label = obj.optString("label").trim()
            val fragment = obj.optString("fragment").trim()
            val y = obj.optInt("targetScrollY", -1)
            if (label.isEmpty() || fragment.isEmpty() || y < 0) null
            else SingleGuideTocEntry(label, fragment, y)
        }
    }.getOrDefault(emptyList())
}

/**
 * Run the anchor-cluster JS extractor in [webView] and post the parsed
 * entries to [onResult] on the main thread. Always returns; on parse
 * failure / missing JS asset / empty extractor result, calls
 * `onResult(emptyList())`.
 *
 * The JS is loaded from `assets/anchor_cluster_extract.js`. Caller
 * typically runs this from `onPageFinished` after the palette CSS has
 * been injected so the rect positions reflect final layout.
 */
fun extractAnchorClusterEntries(
    context: Context,
    webView: WebView?,
    onResult: (List<SingleGuideTocEntry>) -> Unit
) {
    if (webView == null) {
        onResult(emptyList()); return
    }
    val js = runCatching {
        context.assets.open("anchor_cluster_extract.js").bufferedReader()
            .use { it.readText() }
    }.getOrNull()
    if (js == null) {
        onResult(emptyList()); return
    }
    webView.evaluateJavascript(js) { raw ->
        val list = parseAnchorClusterJson(raw)
        Handler(Looper.getMainLooper()).post { onResult(list) }
    }
}
