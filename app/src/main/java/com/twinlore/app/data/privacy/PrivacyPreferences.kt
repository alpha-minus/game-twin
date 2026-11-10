package com.twinlore.app.data.privacy

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Collects the privacy-blocking enabled flow into a volatile field readable from any thread
 * without locking. Used by [PrivacyInterceptor] which runs on the WebView's IO thread.
 *
 * Single-writer (the coroutine scope), many-reader. @Volatile is sufficient.
 */
internal class PrivacyPreferences(
    enabledFlow: Flow<Boolean>,
    scope: CoroutineScope,
) {
    @Volatile var enabled: Boolean = true
        private set

    init {
        enabledFlow
            .onEach { enabled = it }
            .launchIn(scope)
    }
}
