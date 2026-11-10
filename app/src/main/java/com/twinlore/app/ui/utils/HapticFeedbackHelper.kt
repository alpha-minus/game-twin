package com.twinlore.app.ui.utils

import android.view.HapticFeedbackConstants
import android.view.View
import com.twinlore.app.data.HapticFeedbackIntensity

/**
 * Helper function to perform haptic feedback based on intensity setting
 */
fun View.performHapticFeedback(intensity: HapticFeedbackIntensity) {
    val constant = when (intensity) {
        HapticFeedbackIntensity.NONE -> return // Skip haptic feedback
        HapticFeedbackIntensity.LIGHT -> HapticFeedbackConstants.CLOCK_TICK
        HapticFeedbackIntensity.MEDIUM -> HapticFeedbackConstants.CONFIRM
        HapticFeedbackIntensity.STRONG -> HapticFeedbackConstants.VIRTUAL_KEY
    }
    performHapticFeedback(constant)
}
