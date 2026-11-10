package com.twinlore.app.di

import com.twinlore.app.data.library.BrowserSaveCoordinator
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Entry point for accessing BrowserSaveCoordinator from non-injectable contexts
 * (e.g. composables in the Browser screen).
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface BrowserSaveCoordinatorEntryPoint {
    fun browserSaveCoordinator(): BrowserSaveCoordinator
}
