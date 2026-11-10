package com.twinlore.app.di

import com.twinlore.app.data.privacy.PrivacyPreferences
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Entry point for accessing [PrivacyPreferences] from non-injectable contexts
 * (composables that need to read the privacy-blocking toggle synchronously
 * inside a WebViewClient callback).
 *
 * Visibility is `internal` because [PrivacyPreferences] is `internal`.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface PrivacyPreferencesEntryPoint {
    fun privacyPreferences(): PrivacyPreferences
}
