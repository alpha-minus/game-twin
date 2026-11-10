package com.twinlore.app.journey.ui

import androidx.compose.ui.graphics.Color
import com.twinlore.app.journey.domain.model.AchievementType

/**
 * Every fixed hex the chart draws with, in one place and out of the composable that assembles them.
 *
 * These are the colours that deliberately do *not* follow the theme: a game has to stay
 * recognisable by its colour and a player by theirs, in light and dark alike. The structural half
 * of the palette (background, panel, grid, ink, accent) is read from the colour scheme by
 * `rememberChartStyle`, which is the only thing that builds a `ChartStyle` for the app.
 *
 * Pure Kotlin, no Compose runtime, because the one property that matters across these lists -
 * that no two of them are the exact same colour standing for two unrelated facts - is arithmetic.
 * See `ChartColorsTest`.
 */
internal object JourneyPalette {

    /**
     * The per-game palette. Only the now-playing chip's ring draws with it now; the chart itself
     * colours by player.
     *
     * Index 2 was `0xFFFFD54A`, which is [GOLD] to the hex - so a game that happened to land on
     * colour index 2 gave the overview strip a ring in the exact colour a mastered award is marked
     * in, on a screen that could be showing both. It is a warmer amber now.
     */
    val SERIES: List<Color> = listOf(
        Color(0xFF4FD2FF), Color(0xFFFF6EC7), Color(0xFFFFC233), Color(0xFFB48BFF),
        Color(0xFFFF9A3C), Color(0xFF5FF2D6), Color(0xFFFF8A8A), Color(0xFFA8E05F),
        Color(0xFF7FA8FF), Color(0xFFF2A2FF), Color(0xFFE0C98F), Color(0xFF8EF0B9),
    )

    /**
     * Which rival, by roster position: their curve, their unlock dots, their end avatar, their
     * badges and medallions, their compare-row chip.
     *
     * Three of the series colours over again, which is a known and accepted overlap: a rival's hue
     * can equal the now-playing chip's on a compare span. Kept because these three are the most
     * distinguishable from each other and from the usual accent, and the two things they mark sit
     * in different places (a chip in the strip, curves on the stage).
     */
    val RIVALS: List<Color> = listOf(Color(0xFFFF6EC7), Color(0xFFB48BFF), Color(0xFF5FF2D6))

    /**
     * The colours the player and rival pickers offer, in spectrum order - warm to cool, one
     * neutral last (owner request; the lilac was cut and the earlier order read as a jumble).
     * The yellow is a shade off [GOLD] so a player's curve never reads as a mastery mark.
     * The last entry is canonical WHITE but theme-dependent in practice: [choiceColor] serves
     * white on a dark theme and black on a light one, so the neutral always contrasts the
     * stage instead of vanishing into it. Defaults: the rival opens on the red (index 0), the
     * player on the blue (index 2) - see JourneyViewModel.
     */
    val RIVAL_CHOICES: List<Color> = listOf(
        Color(0xFFFF3B30), Color(0xFFFF6EC7), Color(0xFF3D7BFF),
        Color(0xFF5FF2D6), Color(0xFFA8E05F), Color(0xFFFFE83B),
        Color(0xFFFFFFFF),
    )

    /**
     * The picker colour at [index], resolved for the active theme: every entry is fixed except
     * the trailing neutral, which is white on a dark theme and black on a light one. The one
     * place an index becomes a colour - the chart style, the swatch fill and the export all
     * read through here, so no surface can disagree about what the neutral means.
     */
    fun choiceColor(index: Int, isDark: Boolean): Color {
        val i = index.coerceIn(0, RIVAL_CHOICES.lastIndex)
        return if (i == RIVAL_CHOICES.lastIndex && !isDark) Color(0xFF000000) else RIVAL_CHOICES[i]
    }

    /**
     * The badge corner chip, by achievement type. `STANDARD` has no mark and so never draws a
     * chip; `rememberChartStyle` fills its entry from the theme's dim ink.
     *
     * Missable was red and progression was blue; the user asked for each to move one step along -
     * missable takes the blue progression used to wear (`0xFF3FB8E8`, a shade off `SERIES[0]`
     * rather than equal to it, since at the old `0xFF4FD2FF` a chip and a now-playing ring for a
     * game on colour index 0 were the identical blue), and progression takes a green picked to
     * match that blue's weight and to stay clear of every green in [SERIES] and [RIVALS].
     *
     * Clear of those two lists, which is what `ChartColorsTest` can check. It is NOT clear of every
     * theme accent: HEAL's `#00C853` is close to this green, the way BLOOD_MOON's red was close to
     * the missable red before it. A chip and an accent are never the same shape or the same place,
     * and a palette that had to dodge twenty accents as well would have nothing left to pick from.
     */
    val TYPES: Map<AchievementType, Color> = mapOf(
        AchievementType.MISSABLE to Color(0xFF3FB8E8),
        AchievementType.PROGRESSION to Color(0xFF3FCB6E),
        AchievementType.WIN_CONDITION to Color(0xFFFFD54A),
    )

    /** Mastery: the medallion's kind dot, its wash and its confetti. */
    val GOLD: Color = Color(0xFFFFD54A)

    /** The other two awards, beaten and completed, everywhere they are drawn. */
    val SILVER: Color = Color(0xFFCDD8EA)
}
