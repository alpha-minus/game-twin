package com.twinlore.app.journey.ui.chart

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.ui.text.font.FontFamily
import com.twinlore.app.journey.domain.model.AchievementType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * [ChartTypeIcons] is a lookup table now, not a renderer - the vector-path parsing that used to
 * live here (nested groups, clip paths, a per-type path cache) collapsed into
 * [JourneyChartRenderer]'s own single cached mark once the chart stopped drawing all three icons.
 * What is worth pinning here is that [ChartTypeIcons.markFor] is the single answer both the chart's
 * badges and the settings-sheet legend read - missable and win condition are glyphs, progression is
 * the vault tab's own icon, standard is unmarked. The chart's own use of these marks is pinned in
 * `JourneyChartRendererTest` against the pixels it actually draws; the legend's is pinned in
 * `JourneyPlayerInputTest` against the words it reads out.
 */
class ChartTypeIconsTest {

    @Test fun `missable and win condition are glyphs, progression is the vault tab's icon`() {
        assertEquals(
            ChartTypeIcons.TypeMark.Glyph("!"),
            ChartTypeIcons.markFor(AchievementType.MISSABLE),
        )
        assertEquals(
            ChartTypeIcons.TypeMark.Glyph("★"),
            ChartTypeIcons.markFor(AchievementType.WIN_CONDITION),
        )
        val progression = ChartTypeIcons.markFor(AchievementType.PROGRESSION)
        assertTrue(progression is ChartTypeIcons.TypeMark.Icon)
        assertSame(Icons.Default.TaskAlt, (progression as ChartTypeIcons.TypeMark.Icon).vector)
    }

    /**
     * The stamp font is pinned to the platform default rather than following the theme, which is
     * the fix for the pixel theme: Press Start 2P's caps fill its whole em box, so marks sized and
     * centred for an ordinary font's cap height overflowed their chip and sat high in it. Pinned
     * here as a decision - see [ChartTypeIcons.GLYPH_FONT] for the arithmetic - because "the stamps
     * stopped following the theme's font" reads like a regression to anyone who did not see the
     * chips it produced.
     */
    @Test fun `glyph stamps are drawn in the default font, not the theme's`() {
        assertSame(FontFamily.Default, ChartTypeIcons.GLYPH_FONT)
    }

    /**
     * And the pin is actually USED: every place a [ChartTypeIcons.TypeMark.Glyph] is drawn names
     * [ChartTypeIcons.GLYPH_FONT], on the chart and in the legend alike.
     *
     * Reads the source, which is unusual and is the point - the same reason `ExportStripTest`'s own
     * font guard does. There is no seam: `drawGlyph` is private, `TextMeasurer` is final, and the
     * legend's swatch is a composable whose typeface is settled inside Compose's text layout. The
     * property that matters is a property of the code ("no stamp call site reaches for the theme's
     * font"), so the code is what is read. Without this, the assertion above compares a constant
     * with itself: every call site could go back to `style.labelFont` and the suite would stay
     * green while the pixel theme's chips broke again.
     */
    @Test fun `every stamp call site draws in the pinned font`() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app/src/main/java/com/twinlore/app/journey") }
            .firstOrNull { it.isDirectory }
        // Loudly, rather than vacuously - a guard that passes because it could not find what it
        // reads is worse than no guard.
        assertNotNull("could not locate the journey sources to read", root)
        val files = listOf(
            File(root, "ui/chart/JourneyChartRenderer.kt"),
            File(root, "ui/JourneyLegend.kt"),
        )
        files.forEach { assertTrue("missing source: $it", it.isFile) }

        // The call a match makes, taken by balancing its parentheses rather than by a fixed window:
        // a window would run into the NEXT branch's arguments and read a `GLYPH_FONT` that belongs
        // to somebody else.
        fun callAfter(text: String, from: Int): String {
            val open = text.indexOf('(', from)
            var depth = 0
            var end = open
            while (end < text.length) {
                if (text[end] == '(') depth++
                if (text[end] == ')') {
                    depth--
                    if (depth == 0) break
                }
                end++
            }
            return text.substring(open, minOf(end + 1, text.length))
        }

        val offences = mutableListOf<String>()
        var sites = 0
        files.forEach { file ->
            val text = file.readText()
            val calls = Regex("""TypeMark\.Glyph\s*->""").findAll(text)
                .map { callAfter(text, it.range.last) }
                // The standard type has no mark, but the disc a badge with no art falls back to
                // still draws a bullet at stamp sizes through the same path, so it is held to the
                // same rule - it is the one glyph on that disc.
                .plus(
                    Regex("""drawGlyph\(""").findAll(text)
                        .map { callAfter(text, it.range.last - 1) }
                        .filter { it.contains("\"•\"") },
                )
            calls.forEach { call ->
                sites++
                if (!call.contains("GLYPH_FONT")) {
                    offences += "${file.name}: a glyph stamp drawn without GLYPH_FONT - " +
                        "\"${call.take(90).replace('\n', ' ')}\""
                }
            }
        }

        // Two in the renderer (the corner chip and the missing-art fallback disc) plus its bullet
        // stand-in, and one in the legend's swatch.
        assertEquals("expected every stamp call site to be found", 4, sites)
        assertEquals(
            "a type stamp must not follow the theme's font: " + offences.joinToString(" | "),
            emptyList<String>(),
            offences,
        )
    }

    /** A standard achievement had no glyph and must still have no mark of any kind. */
    @Test fun `standard has no mark at all`() {
        assertNull(ChartTypeIcons.markFor(AchievementType.STANDARD))
    }
}
