package com.twinlore.app.ui

import android.util.Log
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import com.twinlore.app.domain.AppState
import com.twinlore.app.domain.VaultTab
import com.twinlore.app.ui.components.UpdateDialog
import com.twinlore.app.ui.screens.LibraryScreen
import com.twinlore.app.ui.screens.GuideViewerScreen
import com.twinlore.app.ui.screens.VaultSessionScreen
import com.twinlore.app.ui.screens.SettingsScreen
import com.twinlore.app.ui.screens.SleepScreen
import com.twinlore.app.ui.screens.RAProfileScreen


/**
 * Navigation host that observes state and renders appropriate screens
 * No overlays - each state renders full screen
 * 
 * CRITICAL: Uses collectAsState() to reactively observe state changes
 */
@Composable
fun NavigationHost(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    // CRITICAL FIX: Use collectAsState() to observe StateFlow changes
    // This ensures the composable recomposes when state changes
    val currentState by viewModel.currentState.collectAsState()
    
    // Debug logging to verify state changes trigger recomposition
    try {
        Log.d("NavigationHost", "Rendering AppState = $currentState")
    } catch (e: Exception) {
        // Log not available in unit tests, ignore
    }

    // Store in local variable for smart casting
    val state = currentState

    // Stable key for the screen fade: only animate when the screen type changes,
    // NOT when tab switches within VaultSession (those are handled internally).
    val screenKey = remember(state) { screenKeyFor(state) }

    // Last AppState seen for each screen key. Outgoing screens must keep
    // rendering while they fade out: once navigation moves on, the live state
    // no longer matches their key and the state-guarded branches below would
    // render NOTHING — an instant blank under the fade (the background-colour
    // flash). The fade-out branch falls back to this cache instead.
    val lastStateForKey = remember { mutableMapOf<String, AppState>() }
    lastStateForKey[screenKey] = state

    val updateState by viewModel.updateState.collectAsState()
    val context = LocalContext.current

    UpdateDialog(
        updateState = updateState,
        onDownload = { url, version -> viewModel.downloadUpdate(url, version) },
        onInstall = { file ->
            val intent = viewModel.installUpdate(file)
            context.startActivity(intent)
        },
        onDismiss = { version -> viewModel.skipUpdateVersion(version) },
        onRetry = { viewModel.dismissUpdate() }
    )

    // Animate on screenKey only — tab switches within VaultSession keep the same key
    // so the screen is NOT recreated (WebViews stay alive).
    //
    // SaveableStateProvider keyed by screenKey: rememberSaveable state — which
    // includes every rememberLazyGridState/rememberLazyListState scroll position —
    // survives leaving the composition and is restored on return. Without it,
    // coming back to the library or a picker rebuilt all lists at the top with
    // a visible flash. Cost is a handful of saved ints per visited screen key.
    //
    // ASYMMETRIC fade, not Crossfade: heavy screens (library grid, pickers)
    // spend their first frames composing/measuring and can only show their
    // background. A simultaneous crossfade exposes that as a background-colour
    // flash. Instead the OLD screen stays near-opaque while the incoming one
    // composes invisibly during the fade-in delay, then fades in over it.
    val saveableStateHolder = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    // Monotonic z so the INCOMING screen always draws on top of the outgoing one.
    val zCounter = remember { floatArrayOf(0f) }
    AnimatedContent(
        targetState = screenKey,
        transitionSpec = {
            // True dissolve, no opacity dip: the old screen stays FULLY OPAQUE
            // underneath while the new one composes (90ms budget) and fades in
            // on top; only then does the old screen leave. A symmetric
            // crossfade has both screens ~33% mid-transition, bleeding the
            // theme-coloured window background through as a flash.
            zCounter[0] += 1f
            (
                fadeIn(tween(durationMillis = 200, delayMillis = 90)) togetherWith
                    fadeOut(tween(durationMillis = 80, delayMillis = 300))
                ).apply { targetContentZIndex = zCounter[0] }
        },
        modifier = modifier,
        label = "NavigationCrossfade"
    ) { key ->
        saveableStateHolder.SaveableStateProvider(key) {
            // Read live state inside the lambda so tab switches update without
            // re-animating; fall back to the per-key cache while fading out.
            val liveState = viewModel.currentState.collectAsState().value
            val branchState = if (screenKeyFor(liveState) == key) liveState else lastStateForKey[key]
            when {
                key.startsWith("vault_") && branchState is AppState.VaultSession -> {
                    VaultSessionScreen(
                        vaultId = branchState.vaultId,
                        activeTab = branchState.activeTab,
                        mainViewModel = viewModel,
                        modifier = Modifier.fillMaxSize(),
                        preResolvedArtworkUri = branchState.artworkUri,
                        preResolvedAccentArgb = branchState.accentArgb
                    )
                }
                key.startsWith("vault_") && branchState is AppState.MapViewer -> {
                    VaultSessionScreen(
                        vaultId = branchState.vaultId,
                        activeTab = VaultTab.Map,
                        mainViewModel = viewModel,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                key.startsWith("guide_") && branchState is AppState.GuideViewer -> {
                    GuideViewerScreen(
                        vaultId = branchState.vaultId,
                        mainViewModel = viewModel,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                key == "library" -> {
                    LibraryScreen(
                        mainViewModel = viewModel,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                key == "settings" -> {
                    SettingsScreen(
                        expandAchievements = viewModel.expandAchievementsOnce.collectAsState().value,
                        onAchievementsExpanded = { viewModel.consumeExpandAchievements() },
                        onBackClick = { viewModel.exitSettings() },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                key == "sleep" -> {
                    SleepScreen(
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                key == "ra_profile" -> {
                    RAProfileScreen(
                        mainViewModel = viewModel,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                key == "app_catalog" -> {
                    com.twinlore.app.ui.screens.catalog.AppCatalogScreen(
                        onBack = { viewModel.navigateToLibrary() },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                key == "game_stats" -> {
                    com.twinlore.app.ui.screens.stats.GameStatsScreen(
                        onBack = { viewModel.handleBackNavigation() },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                key == "journey" -> {
                    val js = branchState as? AppState.Journey
                    com.twinlore.app.journey.ui.JourneyScreen(
                        lockedGameId = js?.lockedGameId,
                        lockedGameTitle = js?.lockedGameTitle,
                        onBack = { viewModel.handleBackNavigation() },
                        onOpenSettings = { viewModel.navigateToSettings(expandAchievements = true) },
                        onOpenStats = { viewModel.navigateToGameStats() },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                // Page-groups are rendered inside the vault session's Guide tab
                // via GuideTabContent, so there's no separate AppState branch here.
            }
        }
    }
}

/** Stable screen identity for the navigation fade + saveable-state keying. */
private fun screenKeyFor(state: AppState): String = when (state) {
    is AppState.VaultSession -> "vault_${state.vaultId}"
    is AppState.MapViewer -> "vault_${state.vaultId}"
    is AppState.GuideViewer -> "guide_${state.vaultId}"
    is AppState.Library -> "library"
    is AppState.Settings -> "settings"
    is AppState.Sleep -> "sleep"
    is AppState.RetroAchievementsProfile -> "ra_profile"
    is AppState.AppCatalog -> "app_catalog"
    is AppState.GameStats -> "game_stats"
    is AppState.Journey -> "journey"
}

