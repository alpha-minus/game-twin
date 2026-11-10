package com.twinlore.app.journey.ui

import androidx.compose.ui.graphics.Color
import com.twinlore.app.journey.domain.model.AchievementType
import com.twinlore.app.journey.ui.chart.FLAME_INNER
import com.twinlore.app.journey.ui.chart.FLAME_OUTER
import com.twinlore.app.journey.ui.chart.HARDCORE_RING
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The chart's fixed palette, checked for the one property that cannot be seen by reading any single
 * line of it: that no two of these colours are the exact same hex standing for two unrelated facts.
 *
 * Every colour here marks something, and the marks share a screen. A `»` progression chip used to be
 * `0xFF4FD2FF`, which is `SERIES[0]` exactly, so on a span whose current game landed on colour index
 * 0 the chip on a badge and the ring on the now-playing tag were the identical blue for two facts
 * with nothing to do with each other. Gold used to be four things at once, one of which was the ring
 * on a hardcore badge. These are the assertions that keep a new colour from quietly landing on one
 * that is already spoken for.
 *
 * **What this file does not assert, deliberately: that the marks are far apart in hue.**
 * [HARDCORE_RING] is a warm yellow close to [JourneyPalette.GOLD], because the product owner asked
 * for RetroAchievements' own hardcore badge border and that is what the site uses. Matching the site
 * is worth more than separating the two here: somebody who knows what a hardcore badge looks like on
 * RA reads the ring without being told. What is still required, and is what these tests check, is
 * that the two are distinct hexes - and the chart keeps them apart by place as well, the ring going
 * around the whole plate while gold is the kind dot in an award's corner. Any future "these two are
 * too similar" assertion would be fighting an explicit decision; tighten the hexes with the owner
 * instead.
 *
 * Pure Kotlin: [JourneyPalette] is a plain object precisely so this needs no Compose runtime and no
 * theme.
 */
class ChartColorsTest {

    /**
     * What each colour outside the series palette is supposed to be saying.
     *
     * The win-condition chip is deliberately absent: it is [JourneyPalette.GOLD] on purpose (see
     * the test below), so listing it here would be asserting against a decision rather than for it.
     */
    private fun marks(): Map<String, Color> = mapOf(
        "hardcore ring" to HARDCORE_RING,
        "mastered" to JourneyPalette.GOLD,
        "beaten or completed" to JourneyPalette.SILVER,
        "missable chip" to JourneyPalette.TYPES.getValue(AchievementType.MISSABLE),
        "progression chip" to JourneyPalette.TYPES.getValue(AchievementType.PROGRESSION),
        "streak flame outer" to FLAME_OUTER,
        "streak flame inner" to FLAME_INNER,
    )

    @Test fun `no two marks share a colour`() {
        val marks = marks()
        marks.entries.forEachIndexed { i, a ->
            marks.entries.drop(i + 1).forEach { b ->
                assertTrue(
                    "'${a.key}' and '${b.key}' are the same colour, so neither can be told from the other",
                    a.value != b.value,
                )
            }
        }
    }

    /**
     * The one hex two marks are allowed to share, pinned as a choice rather than left to be
     * rediscovered as a bug: gold means an achievement or an award that finishes a game, and the
     * `★` chip and the mastery dot are the two ways of saying that. They also never sit in the same
     * place - a corner stamp on a badge, and the kind dot on an award disc.
     */
    @Test fun `the win condition chip is gold on purpose`() {
        assertEquals(JourneyPalette.GOLD, JourneyPalette.TYPES.getValue(AchievementType.WIN_CONDITION))
    }

    /**
     * The type chips are the ones that sit on the same stage as a now-playing ring, and the ones a
     * collision was actually found in.
     */
    @Test fun `no type chip is an exact series colour`() {
        JourneyPalette.TYPES.forEach { (type, color) ->
            assertFalse(
                "the $type chip is series colour ${JourneyPalette.SERIES.indexOf(color)}",
                color in JourneyPalette.SERIES,
            )
        }
    }

    /** And the same for the rival palette, which the type chips share a compare chart with. */
    @Test fun `no type chip is an exact rival colour`() {
        JourneyPalette.TYPES.forEach { (type, color) ->
            assertFalse("the $type chip is a rival colour", color in JourneyPalette.RIVALS)
        }
    }

    /**
     * The award colours are marks too, and a game's identifying colour is furniture in the same
     * overview strip an award can be revealed under. `SERIES[2]` was gold to the hex until this
     * test was written.
     */
    @Test fun `no award colour is an exact series or rival colour`() {
        listOf("gold" to JourneyPalette.GOLD, "silver" to JourneyPalette.SILVER).forEach { (name, color) ->
            assertFalse("$name is a series colour", color in JourneyPalette.SERIES)
            assertFalse("$name is a rival colour", color in JourneyPalette.RIVALS)
        }
    }

    /**
     * The streak flame is fixed like the hardcore ring and the awards, and a badge with a flame
     * over it shares a stage with the game and player palettes too.
     */
    @Test fun `no streak flame colour is an exact series or rival colour`() {
        listOf("flame outer" to FLAME_OUTER, "flame inner" to FLAME_INNER).forEach { (name, color) ->
            assertFalse("$name is a series colour", color in JourneyPalette.SERIES)
            assertFalse("$name is a rival colour", color in JourneyPalette.RIVALS)
        }
    }

    /**
     * The hardcore ring is the fix this file was written for: it was `GOLD`, which is also the
     * mastery dot, the mastery wash and the win-condition chip, so one hex was carrying four
     * unrelated facts. It has to stay clear of the game and player palettes too, since a hardcore
     * badge is drawn on the same stage as both.
     */
    @Test fun `the hardcore ring is its own colour`() {
        assertTrue(HARDCORE_RING !in JourneyPalette.SERIES)
        assertTrue(HARDCORE_RING !in JourneyPalette.RIVALS)
        assertTrue(HARDCORE_RING != JourneyPalette.GOLD)
        assertTrue(HARDCORE_RING != JourneyPalette.SILVER)
    }

    /**
     * The rival palette is deliberately three of the series colours, and this pins that as a choice
     * rather than letting it drift: a change to either list that broke the relationship should be
     * made on purpose.
     */
    @Test fun `the rival palette is the series colours it claims to be`() {
        assertEquals(
            listOf(JourneyPalette.SERIES[1], JourneyPalette.SERIES[3], JourneyPalette.SERIES[5]),
            JourneyPalette.RIVALS,
        )
    }

    /** No palette may repeat itself either: two games or two rivals in one hex is the same failure. */
    @Test fun `each palette is free of duplicates`() {
        assertEquals(JourneyPalette.SERIES.size, JourneyPalette.SERIES.distinct().size)
        assertEquals(JourneyPalette.RIVALS.size, JourneyPalette.RIVALS.distinct().size)
        assertEquals(JourneyPalette.TYPES.size, JourneyPalette.TYPES.values.distinct().size)
    }
}
