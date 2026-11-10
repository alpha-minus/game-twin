package com.twinlore.app.di

import com.twinlore.app.ui.navigation.GlobalFocusActionTracker
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Entry point for accessing GlobalFocusActionTracker from non-injectable contexts
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface FocusActionTrackerEntryPoint {
    fun focusActionTracker(): GlobalFocusActionTracker
}



