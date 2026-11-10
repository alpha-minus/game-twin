package com.twinlore.app.ui

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.MutableState
import androidx.compose.ui.graphics.Color
import coil.ImageLoader
import com.twinlore.app.data.ThemePalette

/**
 * Optional cached focus color from MainActivity, so the first frame can show the correct color
 * before DataStore flow emits (avoids bottom bar color flicker when entering vault session).
 * When null, composables use their normal flow/initial.
 */
val LocalCachedFocusColor = compositionLocalOf<MutableState<Color?>?> { null }

/**
 * Shared Coil ImageLoader for the app. Provided at the root to avoid creating a new loader
 * (and its caches) every time Library or carousel is composed, which caused memory growth
 * when navigating vault ↔ library repeatedly.
 */
val LocalImageLoader = compositionLocalOf<ImageLoader> {
    error("LocalImageLoader not provided. Provide an ImageLoader in the root CompositionLocalProvider.")
}

/**
 * The currently resolved ThemePalette. Provided by ThemedContent so any composable
 * can access palette.isDark or palette colors without collecting from ViewModel.
 */
val LocalThemePalette = compositionLocalOf<ThemePalette> {
    error("LocalThemePalette not provided. Wrap content in ThemedContent.")
}

/**
 * The effects [com.twinlore.app.data.ambience.AmbienceAnimation.RANDOM] may pick from, after the user's
 * exclusions. Provided at the root from settings so every ambience host draws from one pool without
 * having to know where the setting lives. Defaults to everything, which is also what an empty
 * exclusion set means.
 */
val LocalRandomAnimationPool =
    compositionLocalOf { com.twinlore.app.data.ambience.concreteAnimations() }

/**
 * Noir library mode: true when the Noir app-icon style is the effective selection.
 * Library cover tiles ([VaultGridItem]/[VaultCarouselItemFixed]) read this to desaturate
 * every cover except the focused one. Defaults to false so any tile composed outside the
 * library (dialogs, pickers) renders in full colour.
 */
val LocalNoirLibraryMode = compositionLocalOf { false }

/**
 * Adventure library mode: true when the Adventure app-icon style is the effective selection.
 * Library cover tiles read this to fire a one-shot silver shimmer on focus. Defaults to false.
 */
val LocalAdventureLibraryMode = compositionLocalOf { false }

/**
 * Gold library mode: true when the Gold app-icon style is the effective selection.
 * Library cover tiles read this to fire a one-shot gold shimmer on focus. Defaults to false.
 */
val LocalGoldLibraryMode = compositionLocalOf { false }
