package com.twinlore.app.thor.led

import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt

/**
 * Pure colour math for the adaptive LED.
 *
 * The adaptive LED follows the user's focus colour. Its **hue is preserved exactly** — the only
 * things that may change the resolved colour's saturation or brightness are the user's own
 * Look & Feel controls, [saturationBoost] and [brightness]. With both at their neutral values
 * (boost = 0, brightness = 255) the LED shows the focus colour untouched.
 *
 * There is intentionally NO channel normalization here. An earlier version scaled the brightest
 * channel up to 255 to better differentiate similar hues on the SN3112 hardware, but that
 * silently altered both saturation and brightness of the user's chosen colour. It was removed so
 * the adaptive control only ever changes the colour, not its saturation or brightness.
 */
object LedColorResolver {

    /**
     * Resolve [focusColor] to the RGB triple the LED hardware should display.
     *
     * @param brightness 0..255 linear scale applied to every channel (user's brightness control).
     * @param saturationBoost 0f..1f. 0 = leave saturation as-is; higher pushes channels away from
     *   their average, increasing saturation (user's saturation control).
     * @return Triple(r, g, b), each 0..255.
     */
    fun resolve(focusColor: Color, brightness: Int, saturationBoost: Float): Triple<Int, Int, Int> {
        var r = focusColor.red * 255f
        var g = focusColor.green * 255f
        var b = focusColor.blue * 255f

        if (saturationBoost > 0f) {
            val avg = (r + g + b) / 3f
            val factor = 1f + (saturationBoost * 2f)
            r = (avg + (r - avg) * factor).coerceIn(0f, 255f)
            g = (avg + (g - avg) * factor).coerceIn(0f, 255f)
            b = (avg + (b - avg) * factor).coerceIn(0f, 255f)
        }

        val scale = brightness.coerceIn(0, 255) / 255f
        return Triple(
            (r * scale).roundToInt().coerceIn(0, 255),
            (g * scale).roundToInt().coerceIn(0, 255),
            (b * scale).roundToInt().coerceIn(0, 255),
        )
    }
}
