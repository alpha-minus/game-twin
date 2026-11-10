package com.twinlore.app.ui.navigation

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.twinlore.app.data.FocusColor
import com.twinlore.app.data.HapticFeedbackIntensity
import com.twinlore.app.ui.LocalCachedFocusColor
import com.twinlore.app.ui.utils.FeedbackEvent
import com.twinlore.app.ui.utils.FeedbackManager
import com.twinlore.app.thor.led.ThorLedManager

/**
 * A compact circular icon button designed for controller-navigable action rows.
 *
 * Visual behaviour:
 * - When focused: circle background at accentColor with 35% alpha, icon tinted accentColor
 * - When not focused: no background, icon tinted defaultTint
 *
 * Interaction behaviour (mirrors NavigableIconButton):
 * - D-pad / A / Enter / Space confirm key → haptic + LED + onClick
 * - Touch click → haptic + LED + onClick
 * - Focus gained → focus sound + LED
 *
 * @param icon         Icon to display.
 * @param contentDescription Accessibility description.
 * @param onClick      Action invoked on confirm key or touch.
 * @param accentColor  Highlight colour used for focused background and icon tint.
 * @param defaultTint  Icon tint when the button is not focused.
 * @param modifier     Optional modifier applied to the outer Box.
 * @param enabled      Whether the button responds to input.
 * @param size         Overall button size (default 36.dp).
 * @param iconSize     Icon drawable size inside the button (default 18.dp).
 * @param focusRequester Optional external [FocusRequester] to request focus programmatically.
 */
@Composable
fun NavigableActionButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    accentColor: Color,
    defaultTint: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = 36.dp,
    iconSize: Dp = 18.dp,
    focusRequester: FocusRequester? = null
) {
    val internalFocusRequester = remember { FocusRequester() }
    val resolvedFocusRequester = focusRequester ?: internalFocusRequester

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
    val ledTurnOffTime by uiSettings.ledTurnOffTime.collectAsState(
        initial = com.twinlore.app.data.LedTurnOffTime.SEC_15
    )
    val hapticFeedbackIntensity by uiSettings.hapticFeedbackIntensity.collectAsState(
        initial = HapticFeedbackIntensity.MEDIUM
    )

    val view = LocalView.current

    // Play focus sound and update LED when focus is gained
    LaunchedEffect(isFocused) {
        if (isFocused) {
            FeedbackManager.play(view, FeedbackEvent.FOCUS, focusSoundEnabled, hapticFeedbackIntensity)
            ThorLedManager.setColor(
                focusColor,
                ledEnabled,
                ledBrightness,
                ledSaturationBoost,
                ledTurnOffTime.inactivityMs
            )
        }
    }

    val backgroundModifier = if (isFocused) {
        Modifier.background(color = accentColor.copy(alpha = 0.35f), shape = CircleShape)
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .then(backgroundModifier)
            .focusRequester(resolvedFocusRequester)
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
                    nativeKeyEvent.action == KeyEvent.ACTION_DOWN
                ) {
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
                                // Consume ACTION_DOWN to prevent default behaviour
                                nativeKeyEvent.action == KeyEvent.ACTION_DOWN
                            }
                        }
                        else -> false
                    }
                } else {
                    false
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (isFocused) accentColor else defaultTint,
            modifier = Modifier.size(iconSize)
        )
    }
}
