package com.twinlore.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import com.twinlore.app.ui.navigation.NavigableButton
import com.twinlore.app.ui.navigation.NavigableTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.twinlore.app.data.FocusColor
import com.twinlore.app.ui.LocalThemePalette
import com.twinlore.app.data.HapticFeedbackIntensity
import com.twinlore.app.data.LedTurnOffTime
import com.twinlore.app.ui.utils.FeedbackEvent
import com.twinlore.app.ui.utils.runWithHapticAndLed
import com.twinlore.app.ui.LocalCachedFocusColor

/**
 * Dialog for renaming a vault
 */
@Composable
fun RenameVaultDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // Get focus color and haptic/LED from settings
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

    // Use theme palette for background color
    val palette = LocalThemePalette.current
    val containerColor = palette.background
    
    var newName by remember { mutableStateOf(currentName) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val textFieldFocusRequester = remember { FocusRequester() }

    AlertDialog(
        containerColor = containerColor,
        onDismissRequest = onDismiss,
        title = { Text("Rename") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                NavigableTextField(
                    value = newName,
                    onValueChange = {
                        newName = it
                        errorMessage = null
                    },
                    label = "Name",
                    isError = errorMessage != null,
                    modifier = Modifier.fillMaxWidth(),
                    externalFocusRequester = textFieldFocusRequester
                )
                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                LaunchedEffect(Unit) {
                    textFieldFocusRequester.requestFocus()
                }
            }
        },
        confirmButton = {
            NavigableButton(
                text = "Rename",
                onClick = {
                    runWithHapticAndLed(view, hapticFeedbackIntensity, ledEnabled, ledColor, ledBrightness, ledSaturationBoost, ledTurnOffTime.inactivityMs) {
                        val trimmedName = newName.trim()
                        when {
                            trimmedName.isBlank() -> {
                                errorMessage = "Name cannot be empty"
                            }
                            trimmedName.length > 100 -> {
                                errorMessage = "Name is too long (max 100 characters)"
                            }
                            else -> {
                                onConfirm(trimmedName)
                                onDismiss()
                            }
                        }
                    }
                },
                containerColor = Color.Transparent,
                contentColor = focusColor,
                compact = true
            )
        },
        dismissButton = {
            NavigableButton(
                text = "Cancel",
                onClick = { runWithHapticAndLed(view, hapticFeedbackIntensity, ledEnabled, ledColor, ledBrightness, ledSaturationBoost, ledTurnOffTime.inactivityMs, event = FeedbackEvent.BACK, action = onDismiss) },
                containerColor = Color.Transparent,
                contentColor = focusColor,
                compact = true
            )
        },
        modifier = modifier
    )
}



