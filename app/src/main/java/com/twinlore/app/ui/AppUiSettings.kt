package com.twinlore.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.twinlore.app.data.AppFont
import com.twinlore.app.data.FocusColor
import com.twinlore.app.data.HapticFeedbackIntensity
import com.twinlore.app.data.LedTurnOffTime
import com.twinlore.app.data.SettingsRepository
import com.twinlore.app.di.SettingsRepositoryEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * The settings the app's chrome reads, without a ViewModel.
 *
 * Every one of these was a straight pass-through of a [SettingsRepository]
 * flow on SettingsViewModel, and the repository is a singleton on the
 * application component. Reading it directly is the same value from the same
 * place, and it also works in a window with no activity to scope a ViewModel
 * to, which is what the fold panel is.
 *
 * That is not a nicety. Every Navigable button reads focus colour, haptics and
 * LED, and the app's guide viewers are built out of them, so a viewer hosted
 * in the panel crashed on its own top bar until these came from here.
 *
 * Deliberately not every settings flow: a value SettingsViewModel computes
 * rather than passes through is that ViewModel's answer, not the repository's,
 * and copying one here would quietly change what a screen reads. [ledTurnOffTime]
 * is the one that already differs, and it is reproduced rather than corrected.
 */
class AppUiSettings(private val repository: SettingsRepository) {
    val guideThemeInject: Flow<Boolean> get() = repository.guideThemeInject
    val guideSmartFormatting: Flow<Boolean> get() = repository.guideSmartFormatting
    val appFont: Flow<AppFont> get() = repository.appFont
    val focusColor: Flow<FocusColor> get() = repository.focusColor
    val focusSoundEnabled: Flow<Boolean> get() = repository.focusSoundEnabled
    val hapticFeedbackIntensity: Flow<HapticFeedbackIntensity>
        get() = repository.hapticFeedbackIntensity
    val ledEnabled: Flow<Boolean> get() = repository.ledEnabled
    val ledBrightness: Flow<Int> get() = repository.ledBrightness
    val ledSaturationBoost: Flow<Float> get() = repository.ledSaturationBoost

    /**
     * Locked to NEVER, exactly as SettingsViewModel locks it. The repository
     * column is still there and still ignored; matching the ViewModel is the
     * point, because both feed the same LED.
     */
    val ledTurnOffTime: Flow<LedTurnOffTime> get() = flowOf(LedTurnOffTime.NEVER)
}

@Composable
fun rememberAppUiSettings(): AppUiSettings {
    val context = LocalContext.current
    return remember(context) {
        AppUiSettings(
            EntryPointAccessors.fromApplication(
                context.applicationContext,
                SettingsRepositoryEntryPoint::class.java,
            ).settingsRepository()
        )
    }
}
