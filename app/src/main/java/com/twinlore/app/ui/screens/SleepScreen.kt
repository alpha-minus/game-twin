package com.twinlore.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import com.twinlore.app.thor.led.ThorLedManager
import com.twinlore.app.ui.MainViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Sleep mode screen - dimmed screen that restores on tap
 * Pauses rendering and animations to save battery
 */
@Composable
fun SleepScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    var isVisible by remember { mutableStateOf(false) }
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val scope = rememberCoroutineScope()

    // Hold the Android status bar hidden for the whole sleep (via the app's shared arbiter).
    // Sleep is its own navigation destination, so the vault/live screen that was holding the
    // bar hidden disposes shortly after we compose — without our own hold the bar pops back
    // over the black overlay.
    com.twinlore.app.ui.utils.HideStatusBarWhen(true)
    
    // Fade in animation when entering sleep mode
    LaunchedEffect(Unit) {
        isVisible = true
        // Keep the focus-colour LEDs lit for the whole sleep: cancel any
        // pending inactivity turn-off and re-assert the colour with no new
        // timeout (inactivityMs = null), so sleep dims the screen but the
        // ambient lighting stays on until the user wakes.
        // The detected accent when adaptive colour is on, not its palette
        // match: sleep used to relight a red vault's rings in Crimson.
        val accent = settingsViewModel.currentAccentColor()
        val ledEnabled = settingsViewModel.ledEnabled.first()
        val ledBrightness = settingsViewModel.ledBrightness.first()
        val ledSaturationBoost = settingsViewModel.ledSaturationBoost.first()
        ThorLedManager.setColor(
            focusColor = accent,
            ledEnabled = ledEnabled,
            brightness = ledBrightness,
            saturationBoost = ledSaturationBoost,
            inactivityMs = null
        )
    }
    
    // Dimmed overlay with smooth fade transition
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(animationSpec = tween(durationMillis = 500)),
        exit = fadeOut(animationSpec = tween(durationMillis = 300))
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            // Restore LED if control is on, then exit sleep
                            scope.launch {
                                val accent = settingsViewModel.currentAccentColor()
                                val ledEnabled = settingsViewModel.ledEnabled.first()
                                val ledBrightness = settingsViewModel.ledBrightness.first()
                                val ledSaturationBoost = settingsViewModel.ledSaturationBoost.first()
                                ThorLedManager.onAppForegrounded(
                                    ledEnabled,
                                    accent,
                                    ledBrightness,
                                    ledSaturationBoost
                                )
                                viewModel.exitSleepMode()
                            }
                        }
                    )
                }
        ) {
            // Empty - just a dimmed screen that responds to tap
            // Rendering is paused (no animations or updates)
        }
    }
}

