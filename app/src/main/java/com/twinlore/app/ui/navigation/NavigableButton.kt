package com.twinlore.app.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import android.view.KeyEvent
import com.twinlore.app.data.FocusColor
import com.twinlore.app.data.HapticFeedbackIntensity
import com.twinlore.app.ui.LocalCachedFocusColor
import androidx.compose.material3.MaterialTheme
import com.twinlore.app.ui.utils.FeedbackEvent
import com.twinlore.app.ui.utils.FeedbackManager
import com.twinlore.app.thor.led.ThorLedManager

/**
 * Navigable button that supports controller input
 */
@Composable
fun NavigableButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusId: String = text,
    focusManager: FocusManager? = null,
    enabled: Boolean = true,
    externalFocusRequester: FocusRequester? = null,
    containerColor: Color? = null,
    contentColor: Color? = null,
    suppressInitialFocusSound: Boolean = false,
    compact: Boolean = false,
    icon: ImageVector? = null
) {
    val internalFocusRequester = remember { FocusRequester() }
    val focusRequester = externalFocusRequester ?: internalFocusRequester
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    // Not a ViewModel: this is composed inside the fold panel's overlay
    // window too, where there is no activity for hiltViewModel() to scope
    // one to. See AppUiSettings.
    val uiSettings = com.twinlore.app.ui.rememberAppUiSettings()
    val focusColorState by uiSettings.focusColor.collectAsState(initial = FocusColor.CRIMSON)
    val cachedFocusColorState = LocalCachedFocusColor.current?.value
    val focusColor = cachedFocusColorState ?: focusColorState.color
    val focusSoundEnabled by uiSettings.focusSoundEnabled.collectAsState(initial = true)
    val ledEnabled by uiSettings.ledEnabled.collectAsState(initial = true)
    val ledBrightness by uiSettings.ledBrightness.collectAsState(initial = 127)
    val ledSaturationBoost by uiSettings.ledSaturationBoost.collectAsState(initial = 0.75f)
    val ledTurnOffTime by uiSettings.ledTurnOffTime.collectAsState(initial = com.twinlore.app.data.LedTurnOffTime.SEC_15)
    val hapticFeedbackIntensity by uiSettings.hapticFeedbackIntensity.collectAsState(initial = HapticFeedbackIntensity.MEDIUM)
    val view = LocalView.current
    var initialFocusSoundSuppressed by remember { mutableStateOf(false) }
    
    // Use theme-based container colors so buttons stay readable when focus color is white or black
    // Allow override for empty-state screens to match system theme (black/white)
    val containerColorResolved = containerColor ?: if (isFocused) {
        MaterialTheme.colorScheme.surface
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    
    // Use theme-based text color for contrast on theme background
    val textColorResolved = contentColor ?: MaterialTheme.colorScheme.onSurfaceVariant
    
    // Register with focus manager if provided
    focusManager?.registerFocus(focusId, focusRequester)
    
    // Play sound and update Thor LED when focus is gained
    LaunchedEffect(isFocused) {
        if (isFocused) {
            val shouldSuppressNow = suppressInitialFocusSound && !initialFocusSoundSuppressed
            if (shouldSuppressNow) {
                initialFocusSoundSuppressed = true
            } else {
                FeedbackManager.play(view, FeedbackEvent.FOCUS, focusSoundEnabled, hapticFeedbackIntensity)
            }
            ThorLedManager.setColor(focusColor, ledEnabled, ledBrightness, ledSaturationBoost, ledTurnOffTime.inactivityMs)
        }
    }
    
    Button(
        onClick = {
            // Touch-to-Focus: Request focus when tapped
            focusRequester.requestFocus()
            FeedbackManager.play(view, FeedbackEvent.CONFIRM, focusSoundEnabled, hapticFeedbackIntensity)
            onClick()
        },
        modifier = modifier
            .focusRequester(focusRequester)
            .focusable(interactionSource = interactionSource)
            .then(
                // Visible Selection: 4dp border for high-contrast focus state
                if (isFocused) {
                    Modifier.border(
                        width = 4.dp,
                        color = focusColor,
                        shape = RoundedCornerShape(8.dp)
                    )
                } else {
                    Modifier
                }
            )
            .onKeyEvent { keyEvent ->
                val nativeKeyEvent = keyEvent.nativeKeyEvent
                // Only handle confirm keys, let D-pad UP/DOWN/LEFT/RIGHT pass through for focus traversal
                if (nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    when (nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_CENTER,
                        KeyEvent.KEYCODE_BUTTON_A,
                        KeyEvent.KEYCODE_ENTER,
                        KeyEvent.KEYCODE_SPACE -> {
                            if (enabled) {
                                FeedbackManager.play(view, FeedbackEvent.CONFIRM, focusSoundEnabled, hapticFeedbackIntensity)
                                if (ledEnabled) {
                                    ThorLedManager.setColor(
                                        focusColor,
                                        true,
                                        ledBrightness,
                                        ledSaturationBoost,
                                        ledTurnOffTime.inactivityMs
                                    )
                                }
                                onClick()
                            }
                            true // Consume confirm keys
                        }
                        // Don't consume D-pad navigation keys - let Compose handle focus traversal
                        KeyEvent.KEYCODE_DPAD_UP,
                        KeyEvent.KEYCODE_DPAD_DOWN,
                        KeyEvent.KEYCODE_DPAD_LEFT,
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            false // Don't consume - allow focus traversal
                        }
                        else -> false
                    }
                } else {
                    false
                }
            },
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColorResolved,
            contentColor = textColorResolved
        ),
        contentPadding = if (compact) PaddingValues(horizontal = 12.dp, vertical = 6.dp) else PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    ) {
        if (icon != null) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(text)
            }
        } else {
            Text(text)
        }
    }
}

