package com.twinlore.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onKeyEvent
import android.view.KeyEvent
import kotlinx.coroutines.delay

/**
 * Long press threshold in milliseconds (≥500ms as per PRD)
 */
const val LONG_PRESS_THRESHOLD_MS = 500L

/**
 * Handler for long-press detection on controller buttons
 * 
 * @param onLongPress Callback when long press is detected
 * @param onPress Callback for regular press (if no long press)
 * @param modifier Modifier to apply
 */
@Composable
fun Modifier.longPressHandler(
    onLongPress: () -> Unit,
    onPress: () -> Unit = {},
    thresholdMs: Long = LONG_PRESS_THRESHOLD_MS
): Modifier {
    var pressStartTime by remember { mutableLongStateOf(0L) }
    var isPressing by remember { mutableStateOf(false) }
    var longPressTriggered by remember { mutableStateOf(false) }
    
    LaunchedEffect(isPressing) {
        if (isPressing) {
            pressStartTime = System.currentTimeMillis()
            longPressTriggered = false
            
            // Wait for threshold
            delay(thresholdMs)
            
            // If still pressing, trigger long press
            if (isPressing && !longPressTriggered) {
                longPressTriggered = true
                onLongPress()
            }
        } else {
            // Key released
            if (!longPressTriggered && pressStartTime > 0) {
                val pressDuration = System.currentTimeMillis() - pressStartTime
                if (pressDuration < thresholdMs) {
                    // Regular press
                    onPress()
                }
            }
            pressStartTime = 0L
            longPressTriggered = false
        }
    }
    
    return this.onKeyEvent { keyEvent ->
        when (keyEvent.nativeKeyEvent.keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_BUTTON_A,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_SPACE -> {
                when (keyEvent.nativeKeyEvent.action) {
                    KeyEvent.ACTION_DOWN -> {
                        isPressing = true
                        true
                    }
                    KeyEvent.ACTION_UP -> {
                        isPressing = false
                        true
                    }
                    else -> false
                }
            }
            else -> false
        }
    }
}



