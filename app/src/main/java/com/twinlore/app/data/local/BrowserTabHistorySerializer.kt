package com.twinlore.app.data.local

import android.util.Log
import com.twinlore.app.domain.HistoryEntry
import org.json.JSONArray
import org.json.JSONObject

/**
 * Marshals [HistoryEntry] lists to/from the JSON stored in
 * `browser_tabs.history_json`. Uses `org.json` (already a project dep, see
 * GuideBrowserWebViewContent.kt:24) so no extra serialization library.
 *
 * Defensive parsing: any failure to parse, or an empty array, yields a
 * single-entry list synthesized from the caller-supplied fallback `(url,
 * title)`. This keeps the "history is always non-empty" invariant true
 * even if a row's column is corrupted or wasn't backfilled correctly.
 */
object BrowserTabHistorySerializer {
    private const val TAG = "BrowserTabHistorySer"

    fun toJson(entries: List<HistoryEntry>): String {
        val arr = JSONArray()
        for (e in entries) {
            arr.put(JSONObject().apply {
                put("url", e.url)
                put("title", e.title)
            })
        }
        return arr.toString()
    }

    fun fromJson(json: String, fallbackUrl: String, fallbackTitle: String): List<HistoryEntry> {
        val fallback = listOf(HistoryEntry(fallbackUrl, fallbackTitle))
        return try {
            val arr = JSONArray(json)
            if (arr.length() == 0) return fallback
            buildList(arr.length()) {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val url = obj.optString("url", "")
                    val title = obj.optString("title", "")
                    if (url.isNotEmpty()) add(HistoryEntry(url, title))
                }
            }.ifEmpty { fallback }
        } catch (e: Exception) {
            Log.w(TAG, "fromJson parse failed, using fallback", e)
            fallback
        }
    }
}
