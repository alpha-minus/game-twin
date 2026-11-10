package com.twinlore.app.data

import kotlinx.coroutines.flow.Flow

/**
 * Single source for "which file to watch" for auto-refresh and startup focus.
 * Switch to a different file by changing the provider in DI (e.g. [AppModule.provideTrackedFileSource]).
 */
data class TrackedFileSource(
    val uriFlow: Flow<String?>,
    val enabledFlow: Flow<Boolean>,
    val sourceFlow: Flow<LastPlayedSource>
)
