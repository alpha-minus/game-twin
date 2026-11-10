package com.twinlore.app.journey.ui.chart

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import com.twinlore.app.journey.domain.model.AchievementType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How big the x-axis tick labels are drawn, which is not the same in every font.
 *
 * The pixel font's glyphs are wide and fixed-width, so a date that fits between two gridlines in
 * every other font overruns the next one and the axis reads as one smeared row (user report). The
 * chart's text sizes are raw numbers rather than `Typography` styles, so the 0.7 scale the app
 * already applies to every pixel-font text style never reached them.
 *
 * Pinned on the rule rather than on pixels because the rule is the whole of it - the live chart and
 * the export share this renderer, so both follow whatever this answers.
 */
class TickLabelSizeTest {

    private fun style(pixelFont: Boolean) = ChartStyle(
        background = Color.Black, panel = Color.DarkGray, grid = Color.Gray,
        ink = Color.White, inkDim = Color.LightGray, accent = Color.Cyan,
        seriesColors = List(12) { Color.Magenta }, rivalColors = List(3) { Color.Green },
        typeColors = AchievementType.values().associateWith { Color.Red },
        silver = Color.LightGray, gold = Color.Yellow, labelFont = FontFamily.Default,
        pixelFont = pixelFont,
    )

    @Test fun `the pixel font's ticks are drawn smaller than everyone else's`() {
        val ordinary = JourneyChartRenderer.tickLabelSize(style(pixelFont = false))
        val pixel = JourneyChartRenderer.tickLabelSize(style(pixelFont = true))

        assertTrue("the pixel font's ticks must be the smaller pair: $pixel vs $ordinary", pixel < ordinary)
    }

    /**
     * Whole numbers, both of them. The measurer's cache is keyed on the whole text style, so a
     * fractional size (14 * 0.7 = 9.8) would miss it on every frame of a playback.
     */
    @Test fun `both sizes are whole numbers`() {
        listOf(style(pixelFont = false), style(pixelFont = true)).forEach {
            val size = JourneyChartRenderer.tickLabelSize(it)
            assertEquals("$size is not whole", size, size.toInt().toFloat(), 0f)
        }
    }

    /** Nothing else about a style may change the answer - only which font it is. */
    @Test fun `only the font decides`() {
        val dim = style(pixelFont = true).copy(inkDim = Color.Blue, accent = Color.Red)

        assertEquals(
            JourneyChartRenderer.tickLabelSize(style(pixelFont = true)),
            JourneyChartRenderer.tickLabelSize(dim),
            0f,
        )
    }

    /**
     * The same rule for the y-axis point values (owner report, with an exported frame: the
     * ceiling's five pixel-font digits overran the left gutter and were clipped at the frame's
     * edge, and every round value under it lost its leading digits the same way).
     */
    @Test fun `the pixel font's axis values are drawn smaller than everyone else's`() {
        val ordinary = JourneyChartRenderer.axisValueSize(style(pixelFont = false))
        val pixel = JourneyChartRenderer.axisValueSize(style(pixelFont = true))

        assertTrue("the pixel font's values must be the smaller pair: $pixel vs $ordinary", pixel < ordinary)
    }

    @Test fun `axis value sizes are whole numbers too`() {
        listOf(style(pixelFont = false), style(pixelFont = true)).forEach {
            val size = JourneyChartRenderer.axisValueSize(it)
            assertEquals("$size is not whole", size, size.toInt().toFloat(), 0f)
        }
    }
}
