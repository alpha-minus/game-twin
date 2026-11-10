package com.twinlore.app.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEvent as ComposeKeyEvent
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import android.view.KeyEvent
import androidx.compose.material3.MaterialTheme
import com.twinlore.app.ui.LocalThemePalette
import com.twinlore.app.data.FocusColor
import com.twinlore.app.data.HapticFeedbackIntensity
import com.twinlore.app.ui.LocalCachedFocusColor
import com.twinlore.app.ui.theme.getContrastingTextColor
import com.twinlore.app.ui.theme.getFocusContainer
import com.twinlore.app.ui.utils.FeedbackEvent
import com.twinlore.app.ui.utils.FeedbackManager
import com.twinlore.app.thor.led.ThorLedManager

/**
 * Navigable menu item for controller navigation
 */
@Composable
fun NavigableMenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusId: String = text,
    focusManager: FocusManager? = null,
    enabled: Boolean = true,
    onKeyEvent: (ComposeKeyEvent) -> Boolean = { false }
) {
    val focusRequester = remember { FocusRequester() }
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

    // Get theme palette for dark/light detection
    val palette = LocalThemePalette.current
    val isDarkTheme = palette.isDark
    
    // Register with focus manager if provided
    focusManager?.registerFocus(focusId, focusRequester)
    
    // Play sound and update Thor LED when focus is gained
    LaunchedEffect(isFocused) {
        if (isFocused) {
            FeedbackManager.play(view, FeedbackEvent.FOCUS, focusSoundEnabled, hapticFeedbackIntensity)
            ThorLedManager.setColor(focusColor, ledEnabled, ledBrightness, ledSaturationBoost, ledTurnOffTime.inactivityMs)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .focusable(interactionSource = interactionSource)
            // Directional keys (d-pad Left/Right/Up) must be caught in the preview phase —
            // in the bubble phase the focusable's own traversal consumes them first, so a
            // caller's onKeyEvent (e.g. slider adjust) would never fire. See NavigableSliderRow.
            .onPreviewKeyEvent { onKeyEvent(it) }
            .clickable(enabled = enabled) {
                // Touch-to-Focus: Request focus when tapped
                focusRequester.requestFocus()
                FeedbackManager.play(view, FeedbackEvent.CONFIRM, focusSoundEnabled, hapticFeedbackIntensity)
                onClick()
            }
            .background(
                if (isFocused) {
                    focusColor.getFocusContainer()
                } else {
                    Color.Transparent
                },
                shape = RoundedCornerShape(8.dp)
            )
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
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .onKeyEvent { keyEvent ->
                if (onKeyEvent(keyEvent)) return@onKeyEvent true
                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    when (keyEvent.nativeKeyEvent.keyCode) {
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
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
    ) {
        Text(
            text = text,
            // Don't change text color when focused - keep it consistent for readability
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * Navigable menu item with custom content (e.g. icon only).
 */
@Composable
fun NavigableMenuItem(
    content: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusId: String,
    focusManager: FocusManager? = null,
    enabled: Boolean = true,
    onKeyEvent: (ComposeKeyEvent) -> Boolean = { false }
) {
    val focusRequester = remember { FocusRequester() }
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    // Not a ViewModel: this is composed inside the fold panel's overlay
    // window too, where there is no activity for hiltViewModel() to scope
    // one to. See AppUiSettings.
    val uiSettings = com.twinlore.app.ui.rememberAppUiSettings()
    val focusColorState by uiSettings.focusColor.collectAsState(initial = FocusColor.CRIMSON)
    val cachedFocusColorState2 = LocalCachedFocusColor.current?.value
    val focusColor = cachedFocusColorState2 ?: focusColorState.color
    val focusSoundEnabled by uiSettings.focusSoundEnabled.collectAsState(initial = true)
    val ledEnabled by uiSettings.ledEnabled.collectAsState(initial = true)
    val ledBrightness by uiSettings.ledBrightness.collectAsState(initial = 127)
    val ledSaturationBoost by uiSettings.ledSaturationBoost.collectAsState(initial = 0.75f)
    val ledTurnOffTime by uiSettings.ledTurnOffTime.collectAsState(initial = com.twinlore.app.data.LedTurnOffTime.SEC_15)
    val hapticFeedbackIntensity by uiSettings.hapticFeedbackIntensity.collectAsState(initial = HapticFeedbackIntensity.MEDIUM)
    val view = LocalView.current

    focusManager?.registerFocus(focusId, focusRequester)

    LaunchedEffect(isFocused) {
        if (isFocused) {
            FeedbackManager.play(view, FeedbackEvent.FOCUS, focusSoundEnabled, hapticFeedbackIntensity)
            ThorLedManager.setColor(focusColor, ledEnabled, ledBrightness, ledSaturationBoost, ledTurnOffTime.inactivityMs)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .focusable(interactionSource = interactionSource)
            // Directional keys (d-pad Left/Right/Up) must be caught in the preview phase —
            // in the bubble phase the focusable's own traversal consumes them first, so a
            // caller's onKeyEvent (e.g. slider adjust) would never fire. See NavigableSliderRow.
            .onPreviewKeyEvent { onKeyEvent(it) }
            .clickable(enabled = enabled) {
                focusRequester.requestFocus()
                FeedbackManager.play(view, FeedbackEvent.CONFIRM, focusSoundEnabled, hapticFeedbackIntensity)
                onClick()
            }
            .background(
                if (isFocused) focusColor.getFocusContainer() else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .then(
                if (isFocused) {
                    Modifier.border(4.dp, focusColor, RoundedCornerShape(8.dp))
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .onKeyEvent { keyEvent ->
                if (onKeyEvent(keyEvent)) return@onKeyEvent true
                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_CENTER,
                        KeyEvent.KEYCODE_BUTTON_A,
                        KeyEvent.KEYCODE_ENTER,
                        KeyEvent.KEYCODE_SPACE -> {
                            if (enabled) {
                                FeedbackManager.play(view, FeedbackEvent.CONFIRM, focusSoundEnabled, hapticFeedbackIntensity)
                                if (ledEnabled) {
                                    ThorLedManager.setColor(
                                        focusColor, true, ledBrightness, ledSaturationBoost, ledTurnOffTime.inactivityMs
                                    )
                                }
                                onClick()
                            }
                            true
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        content()
    }
}

