package com.twinlore.app.ui.screens

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.hilt.navigation.compose.hiltViewModel
import com.twinlore.app.data.FocusColor
import com.twinlore.app.ui.LocalThemePalette
import com.twinlore.app.ui.LocalCachedFocusColor
import com.twinlore.app.data.HapticFeedbackIntensity
import com.twinlore.app.data.LedTurnOffTime
import com.twinlore.app.ui.utils.FeedbackEvent
import com.twinlore.app.ui.utils.runWithHapticAndLed

/**
 * Confirmation dialog for deleting all markers
 * Uses app theme settings (background, focus color, etc.)
 */
@Composable
fun DeleteAllMarkersDialog(
    markerCount: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Get focus color, haptic/LED, and theme from settings
    val view = LocalView.current
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val focusColorState by settingsViewModel.focusColor.collectAsState(initial = FocusColor.CRIMSON)
    val cachedFocusColor = LocalCachedFocusColor.current?.value
    val focusColor = cachedFocusColor ?: focusColorState.color
    val hapticFeedbackIntensity by settingsViewModel.hapticFeedbackIntensity.collectAsState(initial = HapticFeedbackIntensity.MEDIUM)
    val ledEnabled by settingsViewModel.ledEnabled.collectAsState(initial = true)
    val ledBrightness by settingsViewModel.ledBrightness.collectAsState(initial = 127)
    val ledSaturationBoost by settingsViewModel.ledSaturationBoost.collectAsState(initial = 0.75f)
    val ledTurnOffTime by settingsViewModel.ledTurnOffTime.collectAsState(initial = LedTurnOffTime.SEC_15)
    val palette = LocalThemePalette.current
    val backgroundColor = MaterialTheme.colorScheme.surface
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete All Markers") },
        text = {
            Text(
                text = "Are you sure you want to delete all $markerCount marker${if (markerCount != 1) "s" else ""}?\n\nThis action cannot be undone.",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        containerColor = backgroundColor,
        confirmButton = {
            TextButton(
                onClick = {
                    runWithHapticAndLed(view, hapticFeedbackIntensity, ledEnabled, focusColor, ledBrightness, ledSaturationBoost, ledTurnOffTime.inactivityMs) {
                        onConfirm()
                    }
                },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Delete All")
            }
        },
        dismissButton = {
            TextButton(
                onClick = { runWithHapticAndLed(view, hapticFeedbackIntensity, ledEnabled, focusColor, ledBrightness, ledSaturationBoost, ledTurnOffTime.inactivityMs, event = FeedbackEvent.BACK, action = onDismiss) },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = focusColor
                )
            ) {
                Text("Cancel")
            }
        },
        modifier = modifier
    )
}
