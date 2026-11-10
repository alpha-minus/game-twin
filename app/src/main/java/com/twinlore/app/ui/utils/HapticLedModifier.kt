package com.twinlore.app.ui.utils

import android.view.KeyEvent
import android.view.View
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import com.twinlore.app.data.HapticFeedbackIntensity
import com.twinlore.app.thor.led.ThorLedManager

/**
 * Runs the given [action] after triggering haptic and LED feedback. Use at the start of
 * onClick handlers (touch) so that touch-triggered actions also get feedback.
 *
 * [event] defaults to [FeedbackEvent.CONFIRM] so all existing call sites compile
 * unchanged; pass a different event (e.g. [FeedbackEvent.SWITCH]) at the call site
 * to emit the correct semantic sound+haptic.
 */
fun runWithHapticAndLed(
    view: View,
    hapticIntensity: HapticFeedbackIntensity,
    ledEnabled: Boolean,
    focusColor: Color,
    ledBrightness: Int,
    ledSaturationBoost: Float,
    ledTurnOffMs: Long?,
    event: FeedbackEvent = FeedbackEvent.CONFIRM,
    action: () -> Unit
) {
    FeedbackManager.play(view, event, hapticIntensity)
    if (ledEnabled) {
        ThorLedManager.setColor(
            focusColor,
            true,
            ledBrightness,
            ledSaturationBoost,
            ledTurnOffMs
        )
    }
    action()
}

/**
 * Modifier that triggers haptic and LED feedback when the user presses a confirm key
 * (D-pad center, A, Enter, Space). Returns false so the key event propagates to the next
 * handler (e.g. clickable), which will then trigger onClick. Use on focusable + clickable
 * components that do not use NavigableButton/NavigableIconButton/NavigableMenuItem.
 *
 * Fires [event] (default [FeedbackEvent.CONFIRM]). Pass the same event the component's
 * onClick uses so a confirm-key press and the propagated click stay within the same
 * event's debounce window instead of stacking two different sounds.
 */
fun Modifier.hapticAndLedOnConfirm(
    view: View,
    hapticIntensity: HapticFeedbackIntensity,
    ledEnabled: Boolean,
    focusColor: Color,
    ledBrightness: Int,
    ledSaturationBoost: Float,
    ledTurnOffMs: Long?,
    event: FeedbackEvent = FeedbackEvent.CONFIRM
): Modifier = this.onKeyEvent { keyEvent ->
    val nativeKeyEvent = keyEvent.nativeKeyEvent
    if (nativeKeyEvent.action == KeyEvent.ACTION_UP &&
        (nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
            nativeKeyEvent.keyCode == KeyEvent.KEYCODE_BUTTON_A ||
            nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER ||
            nativeKeyEvent.keyCode == KeyEvent.KEYCODE_SPACE)
    ) {
        FeedbackManager.play(view, event, hapticIntensity)
        if (ledEnabled) {
            ThorLedManager.setColor(
                focusColor,
                true,
                ledBrightness,
                ledSaturationBoost,
                ledTurnOffMs
            )
        }
    }
    false // Let key propagate so clickable still receives it
}
