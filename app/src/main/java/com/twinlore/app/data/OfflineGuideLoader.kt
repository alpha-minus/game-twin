package com.twinlore.app.data

import android.util.Log
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the sidecar `manifest.json` an offline guide directory carries, so
 * the WebView's `shouldInterceptRequest` can serve the page's saved assets
 * from disk instead of the network.
 *
 * It no longer loads or transforms the HTML itself. Script stripping happens
 * at cache-write time now — [HtmlScriptStripper] runs in [MhtCacheManager],
 * [EpubCacheManager] and on the stripped copy `GuideScreen` writes for a
 * downloaded `.html` — so what reaches the WebView is already clean, and a
 * second read-time stripper would only be a second place for the two to drift
 * apart.
 */
@Singleton
class OfflineGuideLoader @Inject constructor() {

    companion object {
        private const val TAG = "OfflineGuideLoader"
    }

    /** Read only the manifest.json (URL → local path map) without parsing HTML. */
    fun loadManifestOnly(guideDir: File): Map<String, String> = loadManifest(guideDir)

    private fun loadManifest(guideDir: File): Map<String, String> {
        val manifestFile = File(guideDir, "manifest.json")
        if (!manifestFile.exists()) return emptyMap()
        return try {
            val json = org.json.JSONObject(manifestFile.readText())
            val map = mutableMapOf<String, String>()
            json.keys().forEach { key ->
                map[key] = json.getString(key)
            }
            map
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read manifest.json", e)
            emptyMap()
        }
    }
}
