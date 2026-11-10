package com.twinlore.app.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import android.view.KeyEvent
import com.twinlore.app.data.FocusColor
import com.twinlore.app.data.HapticFeedbackIntensity
import com.twinlore.app.ui.LocalCachedFocusColor
import com.twinlore.app.ui.utils.FeedbackEvent
import com.twinlore.app.ui.utils.FeedbackManager
import com.twinlore.app.thor.led.ThorLedManager

/**
 * Navigable icon button that supports controller input and touch gestures
 * Implements Touch-to-Focus and Visible Selection features
 * 
 * Uses a custom Box-based implementation instead of IconButton to ensure
 * key events are properly handled
 */
@Composable
fun NavigableIconButton(
    imageVector: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusId: String = contentDescription ?: "icon_button",
    focusManager: FocusManager? = null,
    enabled: Boolean = true,
    tint: androidx.compose.ui.graphics.Color? = null,
    focusActionTracker: GlobalFocusActionTracker? = null,
    iconSize: Dp = 24.dp,
    contentPadding: Dp = 12.dp,
    externalFocusRequester: FocusRequester? = null
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

    // Register with focus manager if provided
    focusManager?.registerFocus(focusId, focusRequester)
    
    // Play sound, haptic, and update Thor LED when focus is gained
    LaunchedEffect(isFocused) {
        if (isFocused) {
            FeedbackManager.play(view, FeedbackEvent.FOCUS, focusSoundEnabled, hapticFeedbackIntensity)
            ThorLedManager.setColor(focusColor, ledEnabled, ledBrightness, ledSaturationBoost, ledTurnOffTime.inactivityMs)
        }
    }

    // Register/unregister action with global tracker when focus changes
    LaunchedEffect(isFocused, enabled) {
        if (isFocused && enabled && focusActionTracker != null) {
            focusActionTracker.registerAction(focusId, onClick)
        } else if (!isFocused && focusActionTracker != null) {
            focusActionTracker.clearAction(focusId)
        }
    }

    // Cleanup on dispose
    DisposableEffect(focusId) {
        onDispose {
            focusActionTracker?.clearAction(focusId)
        }
    }

    // Custom Box-based button with visible focus indicator.
    // When focused: circular background tint + border ring (same pattern as CompactBottomBar).
    // When unfocused: no decoration, icon uses default surface color.
    // When disabled: faded onSurface at 38% alpha, matching Material 3's disabled
    // convention (same look DropdownMenuItem gives its disabled icons).
    val iconTint = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        tint != null -> tint
        isFocused -> focusColor
        else -> MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = modifier
            .focusRequester(focusRequester)
            .focusable(interactionSource = interactionSource)
            .clickable(
                enabled = enabled,
                onClick = {
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
                },
                role = Role.Button
            )
            .onKeyEvent { keyEvent ->
                val nativeKeyEvent = keyEvent.nativeKeyEvent
                if (nativeKeyEvent.action == KeyEvent.ACTION_UP ||
                    nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    when (nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_CENTER,
                        KeyEvent.KEYCODE_BUTTON_A,
                        KeyEvent.KEYCODE_ENTER,
                        KeyEvent.KEYCODE_SPACE -> {
                            if (enabled && nativeKeyEvent.action == KeyEvent.ACTION_UP) {
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
                                true
                            } else {
                                nativeKeyEvent.action == KeyEvent.ACTION_DOWN
                            }
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
            .padding(contentPadding),
        contentAlignment = Alignment.Center
    ) {
        // Focus indicator: circular background + border when focused
        Box(
            modifier = Modifier
                .size(iconSize + 8.dp)
                .then(
                    if (isFocused) Modifier
                        .background(focusColor.copy(alpha = 0.22f), CircleShape)
                        .border(2.dp, focusColor, CircleShape)
                    else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                tint = iconTint,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

