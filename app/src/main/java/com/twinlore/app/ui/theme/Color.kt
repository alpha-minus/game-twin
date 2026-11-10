package com.twinlore.app.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.pow

// Color palette will be defined here
val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

/**
 * Calculate the relative luminance of a color (0.0 to 1.0)
 * Based on WCAG 2.0 formula: https://www.w3.org/TR/WCAG20/#relativeluminancedef
 */
fun Color.luminance(): Float {
    val r = if (red <= 0.03928f) red / 12.92f else ((red + 0.055f) / 1.055f).pow(2.4f)
    val g = if (green <= 0.03928f) green / 12.92f else ((green + 0.055f) / 1.055f).pow(2.4f)
    val b = if (blue <= 0.03928f) blue / 12.92f else ((blue + 0.055f) / 1.055f).pow(2.4f)
    return 0.2126f * r + 0.7152f * g + 0.0722f * b
}

/**
 * Get a contrasting text color (white or black) based on the background color's brightness
 */
fun Color.getContrastingTextColor(): Color {
    return if (luminance() > 0.5f) {
        Color.Black
    } else {
        Color.White
    }
}

/**
 * Get a focus color container (background) with appropriate opacity
 */
fun Color.getFocusContainer(): Color {
    return this.copy(alpha = 0.2f)
}

/**
 * Adjust an accent color extracted from artwork so it stays readable on the
 * current theme background.  Operates in HSV: clamps Value (brightness) and
 * boosts Saturation when the raw color would be invisible or near-invisible.
 */
fun Color.ensureReadableAccent(isDarkTheme: Boolean): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV(
        (red * 255).toInt(),
        (green * 255).toInt(),
        (blue * 255).toInt(),
        hsv
    )
    if (isDarkTheme) {
        if (hsv[2] < 0.40f) hsv[2] = 0.40f
        if (hsv[1] < 0.15f && hsv[2] < 0.70f) hsv[2] = 0.70f
    } else {
        if (hsv[2] > 0.92f && hsv[1] < 0.20f) {
            hsv[1] = 0.40f
            hsv[2] = 0.70f
        }
    }
    val adjusted = android.graphics.Color.HSVToColor(hsv)
    return Color(
        red   = (adjusted shr 16 and 0xFF) / 255f,
        green = (adjusted shr  8 and 0xFF) / 255f,
        blue  = (adjusted        and 0xFF) / 255f,
        alpha = this.alpha
    )
}

