package com.twinlore.app.ui.ambience

import androidx.compose.ui.graphics.Color
import kotlin.math.abs

/**
 * Pure HSV maths for the animation palettes.
 *
 * Deliberately not `android.graphics.Color.colorToHSV`: that is a framework call, which would
 * force Robolectric onto every test that touches a palette. This is the same conversion in plain
 * Kotlin, so the colour rules can be pinned by ordinary unit tests.
 */

/** Hue 0..360, saturation 0..1, value 0..1. Alpha is not carried; callers keep their own. */
internal fun rgbToHsv(c: Color): FloatArray {
    val r = c.red
    val g = c.green
    val b = c.blue
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val d = max - min
    val h = when {
        d < 1e-6f -> 0f
        max == r -> 60f * (((g - b) / d) % 6f)
        max == g -> 60f * (((b - r) / d) + 2f)
        else -> 60f * (((r - g) / d) + 4f)
    }
    return floatArrayOf((h + 360f) % 360f, if (max <= 0f) 0f else d / max, max)
}

internal fun hsvToRgb(h: Float, s: Float, v: Float): Color {
    val hh = ((h % 360f) + 360f) % 360f
    val c = v * s
    val x = c * (1f - abs((hh / 60f) % 2f - 1f))
    val m = v - c
    val (r, g, b) = when ((hh / 60f).toInt()) {
        0 -> Triple(c, x, 0f)
        1 -> Triple(x, c, 0f)
        2 -> Triple(0f, c, x)
        3 -> Triple(0f, x, c)
        4 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(r + m, g + m, b + m)
}

/**
 * A sibling of this colour: hue rotated by [degrees], saturation and value scaled.
 *
 * Value is floored at 0.35 so a near-black accent still produces something visible rather than
 * three shades of nothing.
 */
internal fun Color.shiftHsv(degrees: Float, satMul: Float, valMul: Float): Color {
    val hsv = rgbToHsv(this)
    return hsvToRgb(
        hsv[0] + degrees,
        (hsv[1] * satMul).coerceIn(0f, 1f),
        (hsv[2] * valMul).coerceIn(0.35f, 1f),
    )
}

/**
 * Three ember colours built from [accent]: the accent itself, a brighter sibling and a deeper
 * one. Bonfire used to pin amber/orange and only nudge them a quarter of the way toward the
 * accent, which meant an extracted artwork colour barely showed.
 */
internal fun emberPalette(accent: Color): List<Color> = listOf(
    accent,
    accent.shiftHsv(degrees = 22f, satMul = 0.85f, valMul = 1.15f),
    accent.shiftHsv(degrees = -22f, satMul = 1f, valMul = 0.8f),
)
