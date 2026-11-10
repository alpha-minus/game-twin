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
import com.twinlore.app.data.HapticFeedbackIntensity
import com.twinlore.app.data.LedTurnOffTime
import com.twinlore.app.domain.Vault
import com.twinlore.app.ui.utils.FeedbackEvent
import com.twinlore.app.ui.utils.runWithHapticAndLed
import com.twinlore.app.ui.LocalCachedFocusColor

/**
 * Confirmation dialog for deleting a vault
 * Uses app theme settings (background, focus color, etc.)
 */
@Composable
fun DeleteVaultDialog(
    vault: Vault,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Get focus color, haptic/LED, and theme from settings
    val view = LocalView.current
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val focusColorState by settingsViewModel.focusColor.collectAsState(initial = FocusColor.CRIMSON)
    val focusColor = focusColorState.color
    // What the rings show: the detected accent when adaptive colour is on.
    // The palette match above is for this dialog's own tint; driving the LED
    // with it flipped a red vault's rings to its nearest palette entry.
    val ledColor = LocalCachedFocusColor.current?.value ?: focusColor
    val hapticFeedbackIntensity by settingsViewModel.hapticFeedbackIntensity.collectAsState(initial = HapticFeedbackIntensity.MEDIUM)
    val ledEnabled by settingsViewModel.ledEnabled.collectAsState(initial = true)
    val ledBrightness by settingsViewModel.ledBrightness.collectAsState(initial = 127)
    val ledSaturationBoost by settingsViewModel.ledSaturationBoost.collectAsState(initial = 0.75f)
    val ledTurnOffTime by settingsViewModel.ledTurnOffTime.collectAsState(initial = LedTurnOffTime.SEC_15)
    val palette = LocalThemePalette.current
    val backgroundColor = palette.background
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete") },
        text = {
            Text(
                text = "Are you sure you want to delete \"${vault.displayName}\"?\n\nThis also removes its files from your library folder. Linked files in other folders are not affected. This cannot be undone.",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        containerColor = backgroundColor,
        confirmButton = {
            TextButton(
                onClick = {
                    runWithHapticAndLed(view, hapticFeedbackIntensity, ledEnabled, ledColor, ledBrightness, ledSaturationBoost, ledTurnOffTime.inactivityMs) {
                        onConfirm() // onConfirm will handle dismissal
                    }
                },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = focusColor
                )
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(
                onClick = { runWithHapticAndLed(view, hapticFeedbackIntensity, ledEnabled, ledColor, ledBrightness, ledSaturationBoost, ledTurnOffTime.inactivityMs, event = FeedbackEvent.BACK, action = onDismiss) },
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



