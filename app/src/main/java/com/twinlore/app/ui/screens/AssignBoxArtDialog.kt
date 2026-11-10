package com.twinlore.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.twinlore.app.data.HapticFeedbackIntensity
import com.twinlore.app.data.LedTurnOffTime
import com.twinlore.app.ui.utils.FeedbackEvent
import com.twinlore.app.ui.utils.runWithHapticAndLed
import java.net.URL
import com.twinlore.app.ui.LocalCachedFocusColor

/**
 * Dialog for assigning box art from image URL
 * Per PRD: Long-press image → "Assign Image as Box Art" (used only in Library View)
 */
@Composable
fun AssignBoxArtDialog(
    vaultName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var imageUrl by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val textFieldFocusRequester = remember { FocusRequester() }
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

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Assign Box Art") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Enter image URL for $vaultName",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                NavigableTextField(
                    value = imageUrl,
                    onValueChange = {
                        imageUrl = it
                        errorMessage = null
                    },
                    label = "Image URL",
                    placeholder = "https://example.com/image.jpg",
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
                text = "Assign",
                onClick = {
                    runWithHapticAndLed(view, hapticFeedbackIntensity, ledEnabled, ledColor, ledBrightness, ledSaturationBoost, ledTurnOffTime.inactivityMs) {
                        val trimmedUrl = imageUrl.trim()
                        when {
                            trimmedUrl.isBlank() -> {
                                errorMessage = "URL cannot be empty"
                            }
                            !isValidImageUrl(trimmedUrl) -> {
                                errorMessage = "Please enter a valid image URL"
                            }
                            else -> {
                                onConfirm(trimmedUrl)
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

/**
 * Check if a string is a valid image URL
 */
private fun isValidImageUrl(url: String): Boolean {
    return try {
        val parsedUrl = URL(url)
        val protocol = parsedUrl.protocol.lowercase()
        val path = parsedUrl.path.lowercase()
        val validProtocols = listOf("http", "https")
        val validExtensions = listOf(".jpg", ".jpeg", ".png", ".gif", ".webp")
        
        protocol in validProtocols && validExtensions.any { path.endsWith(it) }
    } catch (e: Exception) {
        false
    }
}

