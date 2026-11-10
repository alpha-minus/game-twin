package com.twinlore.app.ui.utils

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.twinlore.app.di.ResourceResolverEntryPoint
import dagger.hilt.android.EntryPointAccessors

/**
 * Backoff for [rememberResourceImage]'s retries, in ms. A resource written moments ago can miss
 * on the first lookup — a SAF tree listing lags the write it just performed — and the screen
 * then showed no artwork at all until it was re-entered. Retrying a handful of times costs a
 * few DB reads and makes that self-heal.
 */
private val RESOURCE_RETRY_DELAYS_MS = longArrayOf(150, 350, 700, 1400)

/**
 * Resolves a `resource_files` row id to a Uri suitable for AsyncImage / ImagePathUtils.
 * Returns null while resolution is in flight or when the resource cannot be located.
 */
@Composable
fun rememberResourceImage(resourceId: String?): Uri? {
    val context = LocalContext.current
    val resolver = remember(context) {
        EntryPointAccessors
            .fromApplication(context.applicationContext, ResourceResolverEntryPoint::class.java)
            .resourceResolver()
    }
    var uri by remember(resourceId) { mutableStateOf<Uri?>(null) }
    LaunchedEffect(resourceId) {
        if (resourceId == null) {
            uri = null
            return@LaunchedEffect
        }
        uri = resolver.resolve(resourceId)
        for (delayMs in RESOURCE_RETRY_DELAYS_MS) {
            if (uri != null) break
            kotlinx.coroutines.delay(delayMs)
            uri = resolver.resolve(resourceId)
        }
    }
    return uri
}

/**
 * Tri-state variant of [rememberResourceImage]: returns the resolved Uri *and*
 * whether the resolution attempt has completed. Lets callers distinguish
 * "still loading" (don't surface error UI yet) from "resolved to null" (the
 * file is genuinely missing). The boolean stays false until the first
 * resolve() returns; it does not flip back when [resourceId] changes — a new
 * resourceId starts a fresh load.
 */
@Composable
fun rememberResourceImageWithResolution(resourceId: String?): Pair<Uri?, Boolean> {
    val context = LocalContext.current
    val resolver = remember(context) {
        EntryPointAccessors
            .fromApplication(context.applicationContext, ResourceResolverEntryPoint::class.java)
            .resourceResolver()
    }
    var uri by remember(resourceId) { mutableStateOf<Uri?>(null) }
    var resolved by remember(resourceId) { mutableStateOf(false) }
    LaunchedEffect(resourceId) {
        uri = resourceId?.let { resolver.resolve(it) }
        resolved = true
    }
    return uri to resolved
}

