package com.twinlore.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.twinlore.app.data.AppFont
import com.twinlore.app.data.ThemePalette
import com.twinlore.app.ui.theme.TwinLoreTheme

/**
 * Apply a resolved theme palette + font around content. Takes the values as
 * parameters rather than fetching SettingsViewModel itself — the caller
 * (MainActivity in practice) reads them off SettingsRepository directly, so
 * we don't construct SettingsViewModel and its ~20-dep singleton graph just
 * to render the theme. That construction (~170ms cold) used to sit on the
 * critical path between setContent's body entry and first frame.
 */
@Composable
fun ThemedContent(
    paletteSelection: ThemePalette?,
    appFont: AppFont,
    content: @Composable () -> Unit
) {
    val isSystemDark = isSystemInDarkTheme()
    val resolvedPalette = paletteSelection
        ?: if (isSystemDark) ThemePalette.OBSIDIAN else ThemePalette.LOBBY

    CompositionLocalProvider(LocalThemePalette provides resolvedPalette) {
        TwinLoreTheme(
            palette = resolvedPalette,
            appFont = appFont,
            content = content
        )
    }
}
