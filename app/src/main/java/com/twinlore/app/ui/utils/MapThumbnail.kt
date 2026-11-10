package com.twinlore.app.ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.twinlore.app.di.MapThumbnailCacheEntryPoint
import com.twinlore.app.di.ResourceResolverEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Resolves a `resource_files` row id to a thumbnail-sized image model suitable for AsyncImage.
 *
 * Priority:
 *  1. Return the cached WebP [File] immediately if present (fast path, no decode).
 *  2. Resolve the source URI from [ResourceResolver] (IO).
 *  3. Surface the source URI to AsyncImage while [MapThumbnailCache.getOrGenerate] runs.
 *  4. Swap to the generated [File] once the thumb is written.
 *
 * Returns null while loading, and null on resolution failure (missing resource). The null
 * case is safe for AsyncImage (it will show the placeholder/error content).
 *
 * Handles:
 *  - null [resourceId]: returns null immediately.
 *  - source-resolve failure: returns null.
 *  - generation failure: stays on the source URI as a fallback so the picker still shows
 *    something rather than an empty slot.
 */
@Composable
fun rememberMapThumbnail(resourceId: String?): Any? {
    val context = LocalContext.current
    val cache = remember(context) {
        EntryPointAccessors
            .fromApplication(context.applicationContext, MapThumbnailCacheEntryPoint::class.java)
            .mapThumbnailCache()
    }
    val resolver = remember(context) {
        EntryPointAccessors
            .fromApplication(context.applicationContext, ResourceResolverEntryPoint::class.java)
            .resourceResolver()
    }
    var model by remember(resourceId) { mutableStateOf<Any?>(null) }
    LaunchedEffect(resourceId) {
        if (resourceId == null) {
            model = null
            return@LaunchedEffect
        }
        // Fast path: cache hit.
        val cached = withContext(Dispatchers.IO) { cache.pathFor(resourceId) }
        if (cached != null) {
            model = cached
            return@LaunchedEffect
        }
        // Resolve source URI.
        val source = withContext(Dispatchers.IO) { resolver.resolve(resourceId) }
        if (source == null) {
            model = null
            return@LaunchedEffect
        }
        // Surface source URI while generation runs so the picker shows something immediately.
        model = source
        val generated = cache.getOrGenerate(resourceId) { source }
        if (generated != null) model = generated
        // else: stay on source URI as fallback
    }
    return model
}
