package com.twinlore.app.ui.utils

import android.net.Uri
import android.util.Log
import android.util.LruCache
import java.io.File

/**
 * Utility functions for handling image paths.
 *
 * Relative-path support is gone — every caller now passes a fully
 * qualified path (file://, content://, http(s)://) or an absolute filesystem
 * path. The vault-relative "art/<file>" / "map/<file>" / "guide/<file>" form
 * is no longer recognized; if you see one, treat it as a programmer error.
 *
 * Includes an LRU cache so repeated calls for the same (path, vaultId) pair
 * skip filesystem I/O (URI parsing, File.exists checks).
 */
object ImagePathUtils {
    private const val TAG = "ImagePathUtils"
    private const val CACHE_MAX_ENTRIES = 128

    private val SENTINEL_NULL = Object()

    private val resolvedCache = object : LruCache<String, Any>(CACHE_MAX_ENTRIES) {}

    /** Evict cached resolutions for a vault (e.g. after artwork assign). */
    fun invalidateCacheForVault(vaultId: String) {
        val keysToRemove = resolvedCache.snapshot().keys.filter { it.startsWith("$vaultId|") }
        keysToRemove.forEach { resolvedCache.remove(it) }
    }

    /** Clear all cached resolutions (e.g. after storage folder migration). */
    fun invalidateAllCache() {
        resolvedCache.evictAll()
        Log.d(TAG, "invalidateAllCache: cleared all $CACHE_MAX_ENTRIES entries")
    }

    fun toImageData(path: String?, vaultId: String? = null): Any? {
        if (path == null) return null

        val cacheKey = "${vaultId ?: ""}|$path"
        resolvedCache.get(cacheKey)?.let { cached ->
            if (cached !== SENTINEL_NULL) return cached
            // Don't return cached nulls — transient SAF/file-system failures
            // should be retried instead of permanently hiding artwork.
            resolvedCache.remove(cacheKey)
        }

        val result = resolveImageData(path)
        if (result != null) {
            resolvedCache.put(cacheKey, result)
        } else {
            Log.w(TAG, "toImageData: FAILED to resolve path=$path vaultId=$vaultId")
        }
        return result
    }

    private fun resolveImageData(path: String): Any? {
        // Handle the malformed "/content:/..." form that older code produced
        // by File-joining a content URI; fix it up before dispatching.
        val fixedPath = if (path.startsWith("/content:")) {
            path.replaceFirst("/content:/", "content://")
        } else {
            path
        }

        return when {
            fixedPath.startsWith("http://") || fixedPath.startsWith("https://") -> fixedPath
            fixedPath.startsWith("content://") -> {
                try {
                    Uri.parse(fixedPath)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse URI: $fixedPath", e)
                    fixedPath
                }
            }
            fixedPath.startsWith("file://") -> {
                try {
                    File(Uri.parse(fixedPath).path ?: fixedPath)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse file URI: $fixedPath", e)
                    fixedPath
                }
            }
            fixedPath.startsWith("/") -> {
                val file = File(fixedPath)
                if (file.exists()) file else fixedPath
            }
            else -> {
                // Relative paths (art/foo.jpg, map/foo.png, guide/foo.html)
                // are no longer supported. Every caller should now pass a URI or
                // absolute path; if we land here, the row is stale and would
                // resolve to an unreadable image anyway.
                Log.w(TAG, "Unrecognised image path form (relative paths no longer supported): $fixedPath")
                null
            }
        }
    }
}
