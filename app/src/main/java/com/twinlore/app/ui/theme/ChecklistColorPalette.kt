package com.twinlore.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 12-color palette for checklist tab identity.
 *
 * Indices 0..6 preserve the original 7 hard-coded colors (Red..Pink) so migrated
 * data looks identical. Indices 7..11 are added for the expanded palette.
 *
 * Migration note: the legacy 7-color `tagId`-based items used 1-based indices
 * (1=Red..7=Pink). The migration code maps (1..7) -> (0..6) when creating tabs.
 */
object ChecklistColorPalette {
    val colors: List<Color> = listOf(
        Color(0xFFE53935), // 0  Red
        Color(0xFF1E88E5), // 1  Blue
        Color(0xFF43A047), // 2  Green
        Color(0xFF8E24AA), // 3  Purple
        Color(0xFFFB8C00), // 4  Amber
        Color(0xFF00ACC1), // 5  Teal
        Color(0xFFD81B60), // 6  Pink
        Color(0xFF6D4C41), // 7  Brown
        Color(0xFFFFD600), // 8  Yellow
        Color(0xFF00BFA5), // 9  Cyan-green
        Color(0xFF9E9E9E), // 10 Slate Grey
        Color(0xFFFF4081)  // 11 Hot Pink
    )

    val names: List<String> = listOf(
        "Red", "Blue", "Green", "Purple", "Amber", "Teal", "Pink",
        "Brown", "Yellow", "Cyan-green", "Slate", "Hot Pink"
    )

    const val SIZE: Int = 12
    const val LEGACY_SIZE: Int = 7

    /** Clamp any index into [0, SIZE). Robust against data from older rows or import edge cases. */
    fun color(index: Int): Color = colors[index.coerceIn(0, SIZE - 1)]

    fun name(index: Int): String = names[index.coerceIn(0, SIZE - 1)]

    /**
     * Index of the palette color visually closest to [target], using squared
     * RGB distance. Used to pick a sensible default tab color that matches
     * the app's current focus/accent color instead of always starting blue.
     */
    fun closestTo(target: Color): Int {
        var bestIdx = 0
        var bestDist = Float.MAX_VALUE
        colors.forEachIndexed { i, c ->
            val dr = c.red - target.red
            val dg = c.green - target.green
            val db = c.blue - target.blue
            val d = dr * dr + dg * dg + db * db
            if (d < bestDist) {
                bestDist = d
                bestIdx = i
            }
        }
        return bestIdx
    }
}
