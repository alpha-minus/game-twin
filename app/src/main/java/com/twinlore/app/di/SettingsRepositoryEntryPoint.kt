package com.twinlore.app.di

import com.twinlore.app.data.SettingsRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Entry point for reading settings where there is no ViewModel to hold them.
 *
 * The fold panel's overlay window has no activity, so hiltViewModel() cannot
 * resolve there, and the app's guide viewers are hosted in both places.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface SettingsRepositoryEntryPoint {
    fun settingsRepository(): SettingsRepository
}
