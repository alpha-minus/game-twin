package com.twinlore.app.ui.screens

import kotlin.math.roundToInt

/**
 * Pure value mapping for the LED brightness / saturation sliders.
 *
 * The sliders operate on a normalized 0f..1f fraction (so d-pad stepping and the
 * Material Slider share one scale), while storage keeps brightness as an Int 0-255
 * and saturation as a Float 0f..1f. Extracted from the composables so the mapping
 * is unit-testable without a Compose harness.
 */
object LedSliderMath {

    /** Brightness slider granularity: 20 intervals → 5% steps. */
    const val BRIGHTNESS_STEP_COUNT = 20

    /** Saturation slider granularity: 10 intervals → 10% steps. */
    const val SATURATION_STEP_COUNT = 10

    fun brightnessToFraction(brightness: Int): Float =
        brightness.coerceIn(0, 255) / 255f

    fun fractionToBrightness(fraction: Float): Int =
        (fraction.coerceIn(0f, 1f) * 255f).roundToInt()

    fun brightnessPercentLabel(brightness: Int): String =
        "${(brightnessToFraction(brightness) * 100f).roundToInt()}%"

    /** 0 renders as "Off" (leaves the focus colour's saturation untouched). */
    fun saturationLabel(saturation: Float): String =
        if (saturation <= 0.001f) "Off"
        else "${(saturation.coerceIn(0f, 1f) * 100f).roundToInt()}%"

    /** Snap a raw fraction to the nearest discrete step so values don't drift. */
    fun snapFraction(fraction: Float, stepCount: Int): Float =
        ((fraction.coerceIn(0f, 1f) * stepCount).roundToInt().toFloat() / stepCount)
            .coerceIn(0f, 1f)
}
