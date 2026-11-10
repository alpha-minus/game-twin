package com.twinlore.app.di

import com.twinlore.app.data.library.PageGroupSaveCoordinator
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Entry point for the bulk-save pipeline. Exposes the singleton that
 * [com.twinlore.app.ui.screens.browser.BrowserScreen] needs in order to drive
 * the page-group save coordinator.
 *
 * Composables can't @Inject directly, so we mirror the existing
 * BrowserSaveCoordinatorEntryPoint pattern: lookup via
 * dagger.hilt.EntryPoints.get(appContext, this::class.java).
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface BulkSaveEntryPoint {
    fun coordinator(): PageGroupSaveCoordinator
}
