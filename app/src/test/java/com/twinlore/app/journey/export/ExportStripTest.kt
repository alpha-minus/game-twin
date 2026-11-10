package com.twinlore.app.journey.export

import com.twinlore.app.journey.domain.LayoutEngine
import com.twinlore.app.journey.domain.model.AchievementType
import com.twinlore.app.journey.domain.model.AwardTally
import com.twinlore.app.journey.domain.model.GameRef
import com.twinlore.app.journey.domain.model.JourneyFrame
import com.twinlore.app.journey.domain.model.JourneyMode
import com.twinlore.app.journey.domain.model.PlayerJourney
import com.twinlore.app.journey.domain.model.Unlock
import com.twinlore.app.journey.ui.JourneyStripText
import com.twinlore.app.journey.ui.RIVAL_CAP
import com.twinlore.app.journey.ui.chart.ChartStyle
import com.twinlore.app.journey.ui.chart.JourneyChartRenderer
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

/**
 * The strip's arithmetic, which is the half of it a rendered frame cannot answer: a chip running
 * off the right of a 1920-wide image, or into the one beside it, is a picture somebody has already
 * shared by the time anyone notices.
 */
class ExportStripTest {

    /**
     * EVERY number a compare row's geometry is built from is a dp off `JourneyPlayer` carried
     * through [ExportStrip.DENSITY], and nothing else.
     *
     * This is the guard for the whole class of bug the row's sizes and spacing arrived through.
     * They were derived by reading a screen dp as a FRACTION of a 360dp-wide phone in portrait and
     * applying that fraction to a 1920px frame - a reference that is neither the frame's aspect nor
     * the device's width, and which came out about 2.3x too big across the board: a 26dp row became
     * 139px, a 12sp label 64px. The export is a 1920x1080 landscape frame and so is the landscape
     * player it mirrors, at density 2.3, so the mapping is 1:1 with that screen and a dp is worth
     * 2.3 frame pixels.
     *
     * A table rather than seven separate assertions because the failure mode is one constant being
     * retuned by hand and quietly leaving the derivation behind, which is precisely what happened
     * to the gaps: the sizes and the avatar were corrected first and [ExportStrip.ROW_GAP] and the
     * horizontal spacing were left on their old values, so the rows sat about twice as far apart as
     * the screen's for a while. Add a constant to the row, add it here.
     */
    @Test fun `every compare row constant is a screen dp at DENSITY`() {
        fun px(dp: Float) = (dp * ExportStrip.DENSITY).roundToInt().toFloat()

        listOf(
            Triple("COMPARE_LINE_HEIGHT 26dp -> ROW_H", 26f, ExportStrip.ROW_H),
            Triple("COMPARE_LINE_AVATAR 20dp -> ROW_AVATAR", 20f, ExportStrip.ROW_AVATAR),
            Triple("labelMedium 12sp -> ROW_NAME_SIZE", 12f, ExportStrip.ROW_NAME_SIZE),
            Triple("labelMedium 12sp -> ROW_FACT_SIZE", 12f, ExportStrip.ROW_FACT_SIZE),
            Triple("labelSmall 11sp -> ROW_RANK_SIZE", 11f, ExportStrip.ROW_RANK_SIZE),
            Triple("PlayerCompareBar's spacedBy(2.dp) -> ROW_GAP", 2f, ExportStrip.ROW_GAP),
            Triple("PlayerCompareLine's spacedBy(6.dp) -> ROW_ITEM_GAP", 6f, ExportStrip.ROW_ITEM_GAP),
        ).forEach { (what, dp, actual) ->
            assertEquals(what, px(dp), actual, 0.01f)
        }

        // And the text sizes stay whole: the TextMeasurer's cache is keyed on the whole style, so a
        // fractional size misses it on every one of the ~450 frames an export measures.
        listOf(ExportStrip.ROW_NAME_SIZE, ExportStrip.ROW_FACT_SIZE, ExportStrip.ROW_RANK_SIZE)
            .forEach { assertEquals("a text size must be whole", it.roundToInt().toFloat(), it, 0.001f) }
    }

    /**
     * The sibling of the guard above, for the ordinary plate's own finished-span sizes and its
     * single-game title.
     *
     * [ExportStrip.END_LABEL_SIZE]/[ExportStrip.END_VALUE_SIZE] exist because `EndPanel` is the
     * one ordinary-plate state that mirrors a chip one for one - it loops over the very same
     * `List<JourneyStripText.Chip>` this file draws, `labelLarge` for the label and bold
     * `titleMedium` for the value - so, like the compare row above, there is a real screen dp to
     * trace them through [ExportStrip.DENSITY] rather than a picked number.
     *
     * [ExportStrip.TITLE_SIZE] is the same class of fix, for a single game's own title chip
     * (`Chip.isTitle`): `PlaybackHeader`'s title `Text` is `titleSmall`, not the `bodySmall`/
     * `titleMedium` a fact chip's value is drawn at, and it happens to round to the same pixel
     * count as [ExportStrip.END_LABEL_SIZE] today (`labelLarge` and `titleSmall` are both 14sp in
     * the current default `Typography()`) - which is exactly why it gets pinned separately here
     * rather than folded into that constant: the two tokens could diverge on a future theme
     * change, and this table has to notice if [ExportStrip.TITLE_SIZE] stops being 14sp x
     * [ExportStrip.DENSITY] on its own, not only if it stops equalling [ExportStrip.END_LABEL_SIZE].
     *
     * [ExportStrip.SPAN_FACT_SIZE]/[ExportStrip.SPAN_AWARD_SIZE] joined it when a running span's
     * plate stopped being a row of invented counters and became the screen's own two-line header
     * (`PlaybackHeader`): a `bodySmall` facts row under a `titleSmall` title, with `labelSmall`
     * counts beside the award dots. Those are three real screen tokens, which is precisely what
     * that plate did not have while it was drawing a label/value split nothing on screen made.
     *
     * This is the guard for the same class of bug the compare row's own test guards: a constant
     * retuned by hand that quietly leaves its derivation behind. [ExportStrip.LABEL_SIZE]/
     * [ExportStrip.VALUE_SIZE] are deliberately NOT in this table - a SINGLE GAME's fact chips have
     * no second on-screen style to trace a label to (see their own doc) - which this test also
     * pins, so a future "well the other five are here, why not these two" cannot slip a made-up
     * mapping in beside the real ones.
     */
    @Test fun `the finished span's, the header's and the title's own constants are a screen dp at DENSITY too`() {
        fun px(dp: Float) = (dp * ExportStrip.DENSITY).roundToInt().toFloat()

        listOf(
            Triple("labelLarge 14sp -> END_LABEL_SIZE", 14f, ExportStrip.END_LABEL_SIZE),
            Triple("titleMedium 16sp -> END_VALUE_SIZE", 16f, ExportStrip.END_VALUE_SIZE),
            Triple("titleSmall 14sp -> TITLE_SIZE", 14f, ExportStrip.TITLE_SIZE),
            Triple("bodySmall 12sp -> SPAN_FACT_SIZE", 12f, ExportStrip.SPAN_FACT_SIZE),
            Triple("labelSmall 11sp -> SPAN_AWARD_SIZE", 11f, ExportStrip.SPAN_AWARD_SIZE),
        ).forEach { (what, dp, actual) ->
            assertEquals(what, px(dp), actual, 0.01f)
        }
        listOf(
            ExportStrip.END_LABEL_SIZE, ExportStrip.END_VALUE_SIZE, ExportStrip.TITLE_SIZE,
            ExportStrip.SPAN_FACT_SIZE, ExportStrip.SPAN_AWARD_SIZE,
        ).forEach { assertEquals("a text size must be whole", it.roundToInt().toFloat(), it, 0.001f) }

        assertTrue(
            "LABEL_SIZE/VALUE_SIZE must stay off this table - they trace to no second screen " +
                "style, see their own doc for why - or this test would be pinning a mapping " +
                "nobody found",
            ExportStrip.LABEL_SIZE != px(14f) && ExportStrip.VALUE_SIZE != px(16f),
        )
    }

    /**
     * The compare row's spacing stays on the compare row.
     *
     * [ExportStrip.ROW_ITEM_GAP] exists because the gaps a compare row used to borrow for this -
     * [ExportStrip.ICON_GAP] after the leading avatar, [ExportStrip.LABEL_GAP] between the name,
     * the facts and the dots - are SHARED with the ordinary/single-game plate, whose spacing nobody
     * asked to change. Retuning them in place would have moved every counter on every solo export
     * to fix a compare row.
     *
     * So the shared arithmetic takes the gap as a parameter, and what this pins is the DEFAULT:
     * called the way the ordinary plate calls it, [ExportStrip.chipContentWidth],
     * [ExportStrip.chipSegments] and [ExportStrip.plateWidth] still charge the ordinary plate's own
     * gaps. A future change that "simplifies" the parameter away by defaulting it to the compare
     * row's gap fails here rather than on a device.
     */
    @Test fun `the compare row's gaps do not reach the ordinary plate`() {
        assertTrue(
            "this test says nothing unless the two gaps genuinely differ",
            ExportStrip.ROW_ITEM_GAP != ExportStrip.LABEL_GAP &&
                ExportStrip.ROW_ITEM_GAP != ExportStrip.ICON_GAP,
        )
        assertEquals(
            "an ordinary chip's segments are still LABEL_GAP apart",
            40f + ExportStrip.LABEL_GAP + 60f,
            ExportStrip.chipContentWidth(labelWidth = 40f, valueWidth = 60f),
            0.001f,
        )
        assertEquals(
            "and so are the offsets they are drawn at",
            listOf(0f, 40f + ExportStrip.LABEL_GAP, null),
            ExportStrip.chipSegments(labelWidth = 40f, valueWidth = 60f),
        )
        assertEquals(
            "and the ordinary plate still leaves ICON_GAP after its game icon",
            ExportStrip.PAD_H * 2f + 300f + ExportStrip.ICON + ExportStrip.ICON_GAP,
            ExportStrip.plateWidth(300f, iconWidth = ExportStrip.ICON),
            0.001f,
        )
    }

    /** Each kind is a dot, its own gap and its count; the kinds are spaced from each other. */
    @Test fun `an award breakdown is as wide as its dots and its counts`() {
        assertEquals("no awards take no room", 0f, ExportStrip.awardsWidth(emptyList()), 0.001f)
        assertEquals(
            ExportStrip.DOT_R * 2 + ExportStrip.DOT_GAP + 50f,
            ExportStrip.awardsWidth(listOf(50f)),
            0.001f,
        )
        assertEquals(
            (ExportStrip.DOT_R * 2 + ExportStrip.DOT_GAP + 50f) * 2 + ExportStrip.PART_GAP,
            ExportStrip.awardsWidth(listOf(50f, 50f)),
            0.001f,
        )
    }

    /**
     * A single-game compare row's dots draw with no count (`ExportStrip.awardText` returns "" when
     * `Chip.awardCountsShown` is off), which measures at width 0 - a dot with its own gap and no
     * stray width for the absent number. Pinned here rather than trusted, because the failure mode
     * (a gap reserved for a number that is never drawn) reads as a slightly-too-wide plate rather
     * than as anything a rendered frame makes obvious.
     */
    @Test fun `a suppressed award count costs only its dot`() {
        assertEquals(
            ExportStrip.DOT_R * 2 + ExportStrip.DOT_GAP,
            ExportStrip.awardsWidth(listOf(0f)),
            0.001f,
        )
    }

    /**
     * The strip sits in the band under the chart's own caption and running date, and it is put
     * there by arithmetic rather than by measurement - so the arithmetic is what has to be pinned.
     *
     * The bound is read off the renderer's own constants rather than restated here, which is the
     * whole point of the test: growing the caption or moving the line it sits on fails this instead
     * of quietly painting the strip through it.
     *
     * The compact chart is the binding case. It is fitted by its height, which is the largest scale
     * any export uses, so it is where the chrome above reaches furthest down the frame.
     */
    @Test fun `the strip clears the chart's own chrome`() {
        val scale = ExportFrame.HEIGHT / LayoutEngine.H
        val chromeBottom = JourneyChartRenderer.chromeBottom * scale

        assertTrue(
            "the strip at ${ExportStrip.TOP} runs into the chart's chrome, which ends at $chromeBottom",
            ExportStrip.TOP >= chromeBottom,
        )
    }

    /**
     * A chip's content on the plate: its parts, with a gap between each pair that is actually
     * there. A segment that is not there costs nothing, gap included - the awards counter has no
     * label and no value (its dots say both), and padding for two empty runs would be a wide gap
     * in the middle of every plate.
     */
    @Test fun `a chip's content is its parts and the gaps between them`() {
        assertEquals(
            40f + ExportStrip.LABEL_GAP + 60f,
            ExportStrip.chipContentWidth(labelWidth = 40f, valueWidth = 60f),
            0.001f,
        )
        assertEquals(
            "an absent segment costs nothing, its gap included",
            90f,
            ExportStrip.chipContentWidth(labelWidth = 0f, valueWidth = 0f, awardsWidth = 90f),
            0.001f,
        )
        assertEquals(
            40f + ExportStrip.LABEL_GAP + 60f + ExportStrip.LABEL_GAP + 90f,
            ExportStrip.chipContentWidth(labelWidth = 40f, valueWidth = 60f, awardsWidth = 90f),
            0.001f,
        )
        assertEquals(
            "a chip with nothing in it takes no room on a plate",
            0f,
            ExportStrip.chipContentWidth(labelWidth = 0f, valueWidth = 0f),
            0.001f,
        )
    }

    /**
     * The invariant a green suite once missed: `drawChipRun` draws pixels rather than returning a
     * number, so what it puts on the plate has to end exactly where [ExportStrip.chipContentWidth]
     * said it would. It used to keep its own cursor and charge a gap after every segment it drew
     * rather than between the ones actually drawn, so a chip with no award dots - most chips, since
     * an ordinary counter's `awards` is always empty - advanced one `LABEL_GAP` further than the
     * plate had reserved for it; with two or more such chips on one plate, later counters were
     * drawn past its rounded-rect edge, onto bare backdrop.
     *
     * The first fix was a copy of `chipContentWidth` under another name, which left the test
     * comparing a function to its own duplicate - green whatever either of them said, and green
     * whatever `drawChipRun` did. So the two share ONE arithmetic now: `drawChipRun` places every
     * segment at [ExportStrip.chipSegments]'s offset, and what this pins is that the last segment
     * placed ends exactly on the measured width. Getting that wrong in the drawing loop is no
     * longer possible without failing here.
     *
     * label+value with no awards, value only, awards only, label+value+awards, and all-empty are
     * the cases that matter: every count of present/absent segment a chip can actually carry.
     */
    @Test fun `a chip's drawn segments end exactly on its measured content width`() {
        fun assertEndsOnMeasuredWidth(what: String, label: Float, value: Float, awards: Float) {
            val widths = listOf(label, value, awards)
            val segments = ExportStrip.chipSegments(label, value, awards)

            widths.indices.forEach { i ->
                assertEquals(
                    "$what: a segment is placed if and only if it has width",
                    widths[i] > 0f,
                    segments[i] != null,
                )
            }
            val last = widths.indices.lastOrNull { segments[it] != null }
            val drawnRight = last?.let { segments[it]!! + widths[it] } ?: 0f
            assertEquals(
                "$what: what is drawn must end on what the plate reserved",
                ExportStrip.chipContentWidth(label, value, awards),
                drawnRight,
                0.001f,
            )
        }

        assertEndsOnMeasuredWidth("an ordinary counter", label = 70f, value = 30f, awards = 0f)
        assertEndsOnMeasuredWidth("value only", label = 0f, value = 30f, awards = 0f)
        assertEndsOnMeasuredWidth("the awards chip", label = 0f, value = 0f, awards = 90f)
        assertEndsOnMeasuredWidth("all three", label = 40f, value = 60f, awards = 90f)
        assertEndsOnMeasuredWidth("nothing in it", label = 0f, value = 0f, awards = 0f)
    }

    /**
     * And the segments themselves are laid out the way the plate is measured: a gap before each
     * one that follows another, and none in front of the first.
     */
    @Test fun `a chip's segments are gapped only between the parts that are there`() {
        assertEquals(
            listOf(0f, 40f + ExportStrip.LABEL_GAP, 40f + 60f + ExportStrip.LABEL_GAP * 2f),
            ExportStrip.chipSegments(labelWidth = 40f, valueWidth = 60f, awardsWidth = 90f),
        )
        assertEquals(
            "an absent label costs no gap in front of the value",
            listOf(null, 0f, 30f + ExportStrip.LABEL_GAP),
            ExportStrip.chipSegments(labelWidth = 0f, valueWidth = 30f, awardsWidth = 20f),
        )
        assertEquals(
            listOf(null, null, 0f),
            ExportStrip.chipSegments(labelWidth = 0f, valueWidth = 0f, awardsWidth = 90f),
        )
    }

    /** Facts on one baseline: the contents, plus a separator and its gaps between each pair. */
    @Test fun `a facts line is its chips plus the separators between them`() {
        assertEquals("nothing has no width", 0f, ExportStrip.factsWidth(emptyList(), 8f), 0.001f)
        assertEquals("one chip needs no separator", 50f, ExportStrip.factsWidth(listOf(50f), 8f), 0.001f)
        assertEquals(
            50f + 70f + ExportStrip.SEP_GAP * 2f + 8f,
            ExportStrip.factsWidth(listOf(50f, 70f), 8f),
            0.001f,
        )
        assertEquals(
            50f + 70f + 30f + 2f * (ExportStrip.SEP_GAP * 2f + 8f),
            ExportStrip.factsWidth(listOf(50f, 70f, 30f), 8f),
            0.001f,
        )
    }

    /**
     * A plate pays for the icon and the trailing bar only when it has them. A span's plate has an
     * icon and no bar; a finished span's has neither; a single game's has both.
     */
    @Test fun `a plate pays for the furniture it has`() {
        assertEquals(
            "bare plate is its padding and its content",
            ExportStrip.PAD_H * 2f + 300f,
            ExportStrip.plateWidth(contentWidth = 300f),
            0.001f,
        )
        assertEquals(
            ExportStrip.PAD_H * 2f + 300f + ExportStrip.ICON + ExportStrip.ICON_GAP,
            ExportStrip.plateWidth(contentWidth = 300f, iconWidth = ExportStrip.ICON),
            0.001f,
        )
        assertEquals(
            ExportStrip.PAD_H * 2f + 300f + ExportStrip.ICON + ExportStrip.ICON_GAP +
                ExportStrip.TRAILING_GAP + ExportStrip.BAR_W,
            ExportStrip.plateWidth(
                contentWidth = 300f,
                iconWidth = ExportStrip.ICON,
                trailingWidth = ExportStrip.BAR_W,
            ),
            0.001f,
        )
    }

    /**
     * The bound the plate had none of: it clamped its own rounded rect to the frame and then drew
     * the full width of its facts across it regardless, so a wide enough chip set was drawn under
     * the trailing bar and then off the right of the frame onto bare backdrop. Nothing failed when
     * that happened - it is a picture, and the picture had already been shared.
     *
     * This is the invariant that replaces the deleted `chipsThatFit`/`chipRows` wrapping: whatever
     * is drawn ends at or before the plate's own inner right edge. The chips here are deliberately
     * far too wide for the frame (four counters near [ExportStrip] own per-text ceiling), which is
     * the case the old code drew straight through.
     */
    @Test fun `content never runs past the plate's inner right edge`() {
        val frame = ExportFrame.WIDTH.toFloat()
        val maxPlate = frame - ExportStrip.LEFT * 2f
        val sep = 8f
        // A single game's plate: the icon at its head and the bar-and-percentage block at its far
        // end, which is the shape with the least room left for its facts.
        val trailing = ExportStrip.BAR_W
        val overWide = listOf(900f, 800f, 700f, 600f)

        val budget = ExportStrip.factsBudget(maxPlate, ExportStrip.ICON, trailing)
        val shown = ExportStrip.chipsThatFit(overWide, sep, budget)

        assertTrue("this fixture has to actually overflow, or it proves nothing", shown < overWide.size)
        val facts = ExportStrip.factsWidth(overWide.take(shown), sep)
        assertTrue("what is kept has to fit the budget it was cut to", facts <= budget)

        // The plate the strip then draws, and where its facts and its trailing block sit on it -
        // the same arithmetic drawExportStrip uses.
        val width = ExportStrip.plateWidth(facts, ExportStrip.ICON, trailing)
        assertTrue("the plate itself still fits the frame", width <= maxPlate)
        val factsRight = ExportStrip.LEFT + ExportStrip.PAD_H + ExportStrip.ICON +
            ExportStrip.ICON_GAP + facts
        val trailingLeft = ExportStrip.LEFT + width - ExportStrip.PAD_H - trailing
        assertTrue(
            "the facts end at $factsRight, past the trailing block at $trailingLeft - the bar " +
                "would be drawn over the last counter's own text",
            factsRight <= trailingLeft,
        )
        assertTrue(
            "and everything stays inside the plate's own padding",
            factsRight <= ExportStrip.LEFT + width - ExportStrip.PAD_H,
        )
    }

    /**
     * The budget is [ExportStrip.plateWidth] read backwards: what a plate of a given width has
     * left for its facts once its furniture has taken its share. The two have to agree exactly, or
     * the cut above would leave a gap or an overlap at the plate's edge.
     */
    @Test fun `the facts budget is the plate's own arithmetic, backwards`() {
        val maxPlate = 1000f
        assertEquals(
            "a bare plate",
            maxPlate,
            ExportStrip.plateWidth(ExportStrip.factsBudget(maxPlate)),
            0.001f,
        )
        assertEquals(
            "with an icon and a bar",
            maxPlate,
            ExportStrip.plateWidth(
                contentWidth = ExportStrip.factsBudget(maxPlate, ExportStrip.ICON, ExportStrip.BAR_W),
                iconWidth = ExportStrip.ICON,
                trailingWidth = ExportStrip.BAR_W,
            ),
            0.001f,
        )
        assertEquals(
            "a plate with no room at all for facts owes them nothing rather than a negative",
            0f,
            ExportStrip.factsBudget(10f, ExportStrip.ICON, ExportStrip.BAR_W),
            0.001f,
        )
    }

    /** Counters are kept from the front and dropped from the back, one whole counter at a time. */
    @Test fun `the facts that fit are the leading ones`() {
        val sep = 10f
        val widths = listOf(100f, 100f, 100f)
        val two = ExportStrip.factsWidth(widths.take(2), sep)

        assertEquals("all three when there is room", 3, ExportStrip.chipsThatFit(widths, sep, 1000f))
        assertEquals("exactly two at exactly two's width", 2, ExportStrip.chipsThatFit(widths, sep, two))
        assertEquals("a pixel short of the third is still two", 2, ExportStrip.chipsThatFit(widths, sep, two + 1f))
        assertEquals("one when only one fits", 1, ExportStrip.chipsThatFit(widths, sep, 150f))
        assertEquals("none when not even one does", 0, ExportStrip.chipsThatFit(widths, sep, 50f))
        assertEquals("nothing to fit", 0, ExportStrip.chipsThatFit(emptyList(), sep, 500f))
    }

    /**
     * A single game's bar row is the bar and the award circle hanging off its END - the board's own
     * pairing (user request), where the award used to sit under the bar beside the percentage.
     *
     * The circle's room is reserved WHETHER OR NOT the sweep has reached an award. The row is
     * right-aligned against the plate's edge, so a width that grew when one landed jerked the bar
     * sideways mid-replay - 69px, measured on device.
     */
    @Test fun `the bar row always holds room for the award circle`() {
        assertEquals(
            ExportStrip.BAR_W + ExportStrip.BAR_AWARD_GAP + ExportStrip.DOT_R * 2f,
            ExportStrip.barRowWidth(),
            0.001f,
        )
        assertTrue(
            "the row is wider than the bar alone, so the circle has somewhere to go",
            ExportStrip.barRowWidth() > ExportStrip.BAR_W,
        )
    }

    /**
     * And it is as tall as that circle, not as tall as the bar: the circle is centred on the bar and
     * is the larger of the two, so a row sized off the bar alone would let it hang out of the block
     * the plate centres - and, on a short plate, out of the frame. Unconditional for the same reason
     * the width is: a height that appeared with the award would lift the block as one landed.
     */
    @Test fun `the bar row is as tall as the award hanging off it`() {
        assertEquals(ExportStrip.DOT_R * 2f, ExportStrip.barRowHeight(), 0.001f)
        assertTrue("an award circle is taller than the bar", ExportStrip.barRowHeight() > ExportStrip.BAR_H)
    }

    /**
     * A plate's chips say how many lines they stack into, and everything but a running span's
     * header says one.
     *
     * The shape is read off the chips ([com.twinlore.app.journey.ui.JourneyStripText.Chip.line])
     * the way a compare plate's is read off `rivalIndex`, so `ExportStrip` never has to be told
     * which of `exportChips`' branches built the list it is drawing.
     */
    /**
     * A chip with its line to itself gets the plate's room; one sharing a baseline gets the general
     * per-counter ceiling. This is what decides which, and it is the difference between a header's
     * fused facts line being drawn whole and being ellipsized with the plate half empty beside it.
     */
    @Test fun `a chip knows whether it has its line to itself`() {
        val chip = JourneyStripText.Chip("Points", "240")
        val stacked = listOf(chip.copy(line = 0), chip.copy(line = 1))

        // Both lines of a stacked header are one chip each.
        assertTrue(ExportStrip.soleOnLine(stacked, 0))
        assertTrue(ExportStrip.soleOnLine(stacked, 1))

        // Nobody on a one-baseline plate is.
        val row = listOf(chip, chip, chip, chip)
        row.indices.forEach { assertFalse("chip $it shares its baseline", ExportStrip.soleOnLine(row, it)) }

        // A single chip on a single line is, which is the degenerate case the branch must not miss.
        assertTrue(ExportStrip.soleOnLine(listOf(chip), 0))
    }

    @Test fun `a plate's line count comes off its own chips`() {
        val chip = JourneyStripText.Chip("Points", "240")
        assertEquals("no chips is no lines", 0, ExportStrip.lineCount(emptyList()))
        assertEquals("one baseline", 1, ExportStrip.lineCount(listOf(chip, chip)))
        assertEquals(
            "a running span's header is two",
            2,
            ExportStrip.lineCount(listOf(chip, chip.copy(line = 1))),
        )
    }

    /**
     * A stacked plate's lines are centered in it as a BLOCK, with [ExportStrip.LINE_GAP] between
     * them - and for a single line that reduces to exactly where the one-baseline plate has always
     * drawn, which is what makes the stack a generalisation rather than a second layout.
     *
     * Centered rather than started at the plate's top padding because a plate is at least as tall
     * as its leading icon: a short line pinned to the padding would sit above the icon's middle
     * instead of beside it.
     */
    @Test fun `a stacked plate's lines are one centered block`() {
        val plateTop = 62f
        val plateH = ExportStrip.plateHeight(listOf(40f, 30f))

        val tops = ExportStrip.lineTops(plateTop, plateH, listOf(40f, 30f))

        assertEquals("one top per line", 2, tops.size)
        assertEquals("the second line follows the first by a gap", 40f + ExportStrip.LINE_GAP, tops[1] - tops[0], 0.001f)
        assertEquals(
            "the block is centered - the same room above the first line as below the last",
            tops[0] - plateTop,
            (plateTop + plateH) - (tops[1] + 30f),
            0.001f,
        )
        assertTrue("nothing starts above the plate", tops[0] >= plateTop)
        assertTrue("and nothing ends past it", tops[1] + 30f <= plateTop + plateH)

        // The one-line case, which is every plate but the running span's header: the single line IS
        // the block, so it lands exactly where the old `top + (height - lineHeight) / 2` put it.
        val onePlate = ExportStrip.plateHeight(listOf(36f))
        assertEquals(
            listOf(plateTop + (onePlate - 36f) / 2f),
            ExportStrip.lineTops(plateTop, onePlate, listOf(36f)),
        )
        assertEquals(emptyList<Float>(), ExportStrip.lineTops(plateTop, onePlate, emptyList()))
    }

    /**
     * EVERY text run on the export's render path names a font family explicitly, and nothing on it
     * reaches past Compose to the platform's own text stack.
     *
     * Which family is [ChartStyle.labelFont] everywhere except the achievement-type stamps, whose
     * `!`/`★`/`•` marks name
     * [com.twinlore.app.journey.ui.chart.ChartTypeIcons.GLYPH_FONT] on purpose - see there, and see
     * `ChartTypeIconsTest`, which pins that the other way round. That exception is exactly why what
     * this test bans is a `TextStyle(` with NO family rather than one naming a family that is not
     * `labelFont`: an unnamed family is the resolver's fallback, which is a run nobody chose, while
     * a named one is a decision somebody can be held to.
     *
     * A frame with an unchosen typeface on it is always a bug in this renderer. The chart's axis labels,
     * its badge glyphs and the strip's counters are one picture, and a run that fell back to the
     * platform's proportional default would not merely look foreign - it would be MEASURED in a
     * face the layout did not assume, so its height and width would be wrong and every position
     * derived from them would be wrong with it.
     *
     * This reads the SOURCE, which is unusual here and is the point. `TextMeasurer` is final and
     * `ExportFrame.render` builds its own, so there is no seam to intercept; and the pixel guard
     * that covers the same rule from the other side (`ExportFrameTest`'s `every text run on an
     * exported frame is in the chart's own font`) can only see a run in proportion to how much ink
     * it puts on the frame - it cannot see a run that happens to draw nothing in the fixture that
     * was rendered. The rule being enforced is a rule about the code, so the code is what is read.
     *
     * Two things are banned. A `TextStyle(` that does not name a `fontFamily` inherits the
     * measurer's fallback resolver, which is the platform default. And a raw
     * `android.graphics.Paint` / `nativeCanvas.drawText` bypasses Compose's font resolution
     * altogether - that is not hypothetical, `ui/ambience/AmbienceAnimations.kt`'s own `drawRing`
     * does exactly that for its rune ring, and it is drawn into an exported frame whenever an
     * ambience is on. It is out of this feature's scope to fix, and it is in scope to make sure the
     * export's own files never grow a second one.
     */
    @Test fun `every text run on the export's render path names the chart's own font`() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app/src/main/java/com/twinlore/app/journey") }
            .firstOrNull { it.isDirectory }
        // Loudly, rather than vacuously: a test that quietly passes because it could not find the
        // source it was written to read is worse than no test.
        assertTrue("could not locate the journey sources to read", root != null)
        val files = (
            File(root, "export").listFiles { f: File -> f.extension == "kt" }?.toList().orEmpty() +
                File(root, "ui/chart/JourneyChartRenderer.kt")
            ).filter { it.isFile }
        assertTrue("expected to find the export sources, found ${files.size}", files.size >= 5)

        val offences = mutableListOf<String>()
        files.forEach { file ->
            val text = file.readText()
            Regex("TextStyle\\(").findAll(text).forEach { hit ->
                // THIS call's own argument list, found by balancing its parentheses rather than by
                // taking a fixed window after it. A window was tried first and is not good enough:
                // a `if (x) TextStyle(a) else TextStyle(a, fontFamily = f)` has a `fontFamily`
                // a few characters past the offending call, so the window reads the wrong branch's
                // argument and passes. That is not a hypothetical - it is the exact mutation this
                // guard was checked against.
                val open = hit.range.last
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
                val args = text.substring(open, minOf(end + 1, text.length))
                if (!args.contains("fontFamily")) {
                    offences += "${file.name}: a TextStyle(...) with no fontFamily - " +
                        "\"${args.take(80).replace('\n', ' ')}\""
                }
            }
            listOf("android.graphics.Paint(", "nativeCanvas.drawText").forEach { banned ->
                if (text.contains(banned)) {
                    offences += "${file.name}: $banned bypasses Compose's font resolution"
                }
            }
        }

        assertEquals(
            "the exported frame must carry exactly one typeface: " + offences.joinToString(" | "),
            emptyList<String>(),
            offences,
        )
    }

    /**
     * A plate's FIRST line can never start above the plate itself - for any line heights at all,
     * including ones no font would produce.
     *
     * This is the invariant behind a reported artifact (a run of text drawn above the counters and
     * clipped by the top of the exported frame), stated as arithmetic rather than as pixels so it
     * holds for the production font as well as the one a Robolectric render resolves. It falls out
     * of [ExportStrip.plateHeight] and [ExportStrip.lineTops] being the same function read in
     * opposite directions: the plate is `PAD_V * 2 + max(block, ICON)` and the block is centered in
     * it, so the first line's top is at worst `plateTop + PAD_V` and is further down whenever the
     * icon is what set the height.
     *
     * The heights swept here are deliberately absurd at the top end - a run taller than the whole
     * frame - because the failure this pins would arrive through a font's metrics, which this file
     * has no way to bound and should not pretend to.
     */
    @Test fun `no line ever starts above the plate it is on`() {
        val plateTop = ExportStrip.TOP
        val cases = listOf(
            listOf(1f), listOf(36f), listOf(ExportStrip.ICON), listOf(400f), listOf(2000f),
            listOf(43f, 38f), listOf(1f, 1f), listOf(2000f, 2000f), listOf(0f, 51f), listOf(51f, 0f),
            listOf(30f, 30f, 30f),
        )
        cases.forEach { heights ->
            val plateH = ExportStrip.plateHeight(heights)
            val tops = ExportStrip.lineTops(plateTop, plateH, heights)

            assertTrue(
                "$heights: the first line starts at ${tops.first()}, above the plate at $plateTop",
                tops.first() >= plateTop,
            )
            assertEquals(
                "$heights: and never higher than the plate's own vertical padding allows",
                maxOf(plateTop + ExportStrip.PAD_V, tops.first()),
                tops.first(),
                0.001f,
            )
            assertTrue(
                "$heights: the last line ends at ${tops.last() + heights.last()}, past the plate's " +
                    "bottom at ${plateTop + plateH}",
                tops.last() + heights.last() <= plateTop + plateH + 0.001f,
            )
        }
    }

    /**
     * The vertical half of the horizontal bound below: a running span's two-line header is TALLER
     * than the single line it replaced, and the band it darkens has to stay inside the 1080px image.
     *
     * Bounded rather than measured, for the reason `ExportFrameTest`'s own scrim test states: the
     * lines' real heights are a font's line metrics, which this file cannot restate honestly. Twice
     * the font size is a bound no real font exceeds, so a plate that fits AT the bound fits for
     * certain - and the margin here is not close (a ~280px band in a 1080px frame), which is the
     * answer to "does the taller header still clear what it must".
     */
    @Test fun `the two-line header's band stays inside the frame`() {
        val generous = listOf(ExportStrip.TITLE_SIZE * 2f, ExportStrip.SPAN_FACT_SIZE * 2f)
        val plate = ExportStrip.plateHeight(generous)
        val band = ExportStrip.bandHeight(ExportStrip.TOP + plate, ExportStrip.TOP)

        assertTrue(
            "a two-line header must be taller than the one-line plate it replaced, or this test " +
                "is guarding nothing",
            plate > ExportStrip.plateHeight(listOf(ExportStrip.VALUE_SIZE)),
        )
        assertTrue(
            "the header's band runs to $band, past the frame's own bottom edge at " +
                "${ExportFrame.HEIGHT}",
            band <= ExportFrame.HEIGHT,
        )
    }

    /**
     * A plate is as tall as its lines, or as tall as the icon beside them - whichever binds. A
     * single-line plate whose icon is taller than its text must not clip the icon.
     */
    @Test fun `a plate is as tall as its lines or its icon`() {
        assertEquals(
            ExportStrip.PAD_V * 2f + 30f + ExportStrip.LINE_GAP + 24f,
            ExportStrip.plateHeight(listOf(30f, 24f)),
            0.001f,
        )
        assertEquals(
            "one short line still clears the icon",
            ExportStrip.PAD_V * 2f + ExportStrip.ICON,
            ExportStrip.plateHeight(listOf(10f)),
            0.001f,
        )
        assertEquals("no lines is no plate", 0f, ExportStrip.plateHeight(emptyList()), 0.001f)
    }

    /**
     * The single-game plate's bar-and-percentage block is centered inside the plate rather than
     * pinned by an offset derived from half the plate's height - the offset a real font's line
     * metrics could push past the plate's own edge (see [ExportStrip.centeredBlockTop]'s own doc:
     * it used to land the percentage's last row exactly on the plate's bottom edge for one font at
     * one size, and nothing said a different font would keep it there). A block sized off a
     * realistic percent line has to stay inside the plate on both edges.
     */
    @Test fun `the trailing block stays inside the plate at a realistic text height`() {
        val plateH = ExportStrip.plateHeight(listOf(ExportStrip.VALUE_SIZE))
        val plateTop = 62f
        // BAR_H, the gap, and a percent line taller than the 36px Robolectric measured for "42%"
        // at VALUE_SIZE bold in FontFamily.Default - some room for a heavier production font.
        val blockH = ExportStrip.BAR_H + ExportStrip.BAR_PERCENT_GAP + 40f

        val blockTop = ExportStrip.centeredBlockTop(plateTop, plateH, blockH)

        assertTrue("the block must not start above the plate's own top edge", blockTop >= plateTop)
        assertTrue(
            "the block must not run past the plate's own bottom edge",
            blockTop + blockH <= plateTop + plateH,
        )
    }

    /**
     * A block taller than the plate cannot fit either edge - that is the plate's own height
     * falling short of what a tall enough font would need, not something an offset can hide. What
     * [ExportStrip.centeredBlockTop] owes the caller in that case is an overflow centered on both
     * edges rather than one that eats only the bottom, which is exactly what the old
     * `height / 2f`-derived offset did.
     */
    @Test fun `a block taller than the plate overflows both edges evenly, not just the bottom`() {
        val plateH = ExportStrip.plateHeight(listOf(ExportStrip.VALUE_SIZE))
        val plateTop = 62f
        val blockH = plateH + 40f // deliberately too tall for the plate to hold

        val blockTop = ExportStrip.centeredBlockTop(plateTop, plateH, blockH)
        val topOverflow = plateTop - blockTop
        val bottomOverflow = (blockTop + blockH) - (plateTop + plateH)

        assertTrue("an oversized block must actually overflow here, or this case proves nothing", topOverflow > 0f)
        assertEquals(
            "the overflow must be even on both edges - a lopsided one is the bug this replaces",
            topOverflow,
            bottomOverflow,
            0.01f,
        )
    }

    /**
     * A compare chart stacks one row per player, the way the live compare bar does. Packed left to
     * right they read as a sentence about one journey rather than as a scoreboard.
     */
    @Test fun `compare rows stack one per player`() {
        assertEquals(emptyList<Float>(), ExportStrip.compareRowTops(0, top = 100f))
        assertEquals(listOf(100f), ExportStrip.compareRowTops(1, top = 100f))
        assertEquals(
            listOf(100f, 100f + ExportStrip.ROW_H + ExportStrip.ROW_GAP),
            ExportStrip.compareRowTops(2, top = 100f),
        )
        val tops = ExportStrip.compareRowTops(4, top = 100f)
        assertEquals(4, tops.size)
        assertTrue("rows descend", tops.zipWithNext().all { (a, b) -> b > a })
    }

    /**
     * A compare chart's chips are its players, one each, marked by the roster slot their curve is
     * drawn from. Every other journey's counters have no player on them at all.
     */
    @Test fun `a compare plate is recognised by its players`() {
        val counter = JourneyStripText.Chip("Points", "300")
        val main = JourneyStripText.Chip("Mert", "300 pts", rivalIndex = -1)
        val rival = JourneyStripText.Chip("Someone", "280 pts", rivalIndex = 0)
        assertTrue(ExportStrip.isCompare(listOf(main, rival)))
        assertTrue("the main player alone is still a compare row", ExportStrip.isCompare(listOf(main)))
        assertFalse(ExportStrip.isCompare(listOf(counter, counter)))
        assertFalse(ExportStrip.isCompare(emptyList()))
    }

    /**
     * The regression that started this whole task: `comparePlaybackChips` mixes a non-player date
     * chip in with the player chips on purpose (it rides the first row rather than the chart,
     * which cannot draw it - see that function's own doc), and `isCompare` only asks whether ANY
     * chip in a list has a player, which the date chip alone would already satisfy. If row COUNT
     * used that same any-match logic, a date beside two players would stack three rows instead of
     * two - which is exactly what happened before `compareRowCount` existed to count more
     * narrowly than `isCompare` classifies.
     */
    @Test fun `a non-player chip does not add a row`() {
        val date = JourneyStripText.Chip("Date", "Jun 14")
        val main = JourneyStripText.Chip("Mert", "300 pts", rivalIndex = -1)
        val rival = JourneyStripText.Chip("Someone", "280 pts", rivalIndex = 0)

        assertEquals(2, ExportStrip.compareRowCount(listOf(date, main, rival)))
        assertEquals(
            "the row count does not depend on where the non-player chip sits in the list",
            2,
            ExportStrip.compareRowCount(listOf(main, date, rival)),
        )
        assertEquals("no non-player chip at all is still two rows", 2, ExportStrip.compareRowCount(listOf(main, rival)))
    }

    private val day = 86_400_000L

    private fun unlock(id: Int, at: Long) = Unlock(
        id = id, title = "A$id", description = "", points = 10, badgeName = "b$id",
        type = AchievementType.STANDARD, earnedAtMillis = at, numAwarded = 5, gameId = 1,
    )

    /**
     * A running SPAN compare frame with one rival per [RIVAL_CAP] slot, each with an unlock of
     * their own - so none of them gets filtered out of `comparePlaybackChips` the way an
     * empty-handed rival would be. The shape a real running compare export actually draws from,
     * not a guess at what its row count would be.
     */
    private fun runningCompareFrame(rivalCount: Int): JourneyFrame {
        val games = mapOf(1 to GameRef(1, "G1", null, emptyList(), 0, 10, null))
        val main = PlayerJourney("me", listOf(unlock(1, 1 * day)), games, emptyList())
        val rivals = (0 until rivalCount).map { i ->
            PlayerJourney("rival$i", listOf(unlock(100 + i, 2 * day)), games, emptyList())
        }
        return JourneyFrame(
            mode = JourneyMode.SPAN, main = main, rivals = rivals,
            spanFromMillis = 0, spanToMillis = 7 * day,
        )
    }

    /**
     * RETIRED DELIBERATELY - read this before reinstating anything like it from history.
     *
     * This used to be `compare rows stay clear of the plot area at the rival cap`, and it bound
     * player rows to the letterbox band above the chart's own plot. That bound was inherited from
     * when a compare row was an OPAQUE plate (`drawPlate`, painted per row): a box drawn over the
     * chart would have blocked it out, so the rows had to stay above the plot, inside the band.
     *
     * The plate is gone (see `ExportStrip.drawComparePlate`'s own doc) and a compare row is
     * transparent now, the same way the screen's own bar has always been - and the screen's bar
     * has always sat OVER the top of the chart, never confined to a band reserved for it.
     * Overlapping the plot is not a regression to catch here any more; it is the point. Keeping
     * the export inside a band a transparent row no longer needs was itself what was keeping the
     * export from looking like the screen.
     *
     * `ExportStrip.ROW_H` passed through 139px on the way here, from a bad derivation (26dp read as
     * a fraction of a 360dp phone and applied to 1920px) that this doc used to quote approvingly.
     * It is 60px now - the same 26dp at the export's real reference, `ExportStrip.DENSITY` - which
     * makes the surviving guard below slack rather than tight. That is fine: it is a guard against
     * a row count and a row height nobody has checked against the frame, not a claim that the
     * current pair is close to the edge.
     *
     * What still matters, replacing this: `compare rows stay inside the frame at the rival cap`,
     * below - the rows may cover the chart, but they still must not run off the bottom of the
     * 1080px image itself. The row-COUNT guarding this test also did (`RIVAL_CAP + 1`, not a
     * fake third row for the date chip) is unaffected by any of this and is still covered on its
     * own, by `a non-player chip does not add a row` above - that guard was never about the plot.
     */
    @Test fun `compare rows stay inside the frame at the rival cap`() {
        val frame = runningCompareFrame(RIVAL_CAP)
        val layout = LayoutEngine.layout(frame, includeNodes = true)
        val chips = JourneyStripText.exportChips(
            frame = frame, layout = layout, journeyDateMillis = 2 * day,
            points = 10, awards = AwardTally(), revealed = 1, games = 1, completed = false,
            progress = 0.5f, rivalPoints = List(RIVAL_CAP) { 5 },
        )
        // The fixture must actually be exercising the real shape of a running compare frame - one
        // chip per player and nothing else, the date having been dropped from the plate (user
        // request) - or this test proves nothing about the split it exists to guard.
        assertEquals(
            "one chip per player (main + RIVAL_CAP rivals) and no others",
            RIVAL_CAP + 1,
            chips.size,
        )
        assertTrue("a compare plate carries players only", chips.all { it.rivalIndex != null })
        val rows = ExportStrip.compareRowCount(chips)
        assertEquals("main player plus one row per rival", RIVAL_CAP + 1, rows)

        val bottom = ExportStrip.compareRowTops(rows, ExportStrip.TOP).last() + ExportStrip.ROW_H

        assertTrue(
            "compare rows for $rows players (RIVAL_CAP=$RIVAL_CAP) run to $bottom, past the " +
                "frame's own bottom edge at ${ExportFrame.HEIGHT} - overlaying the chart is " +
                "intentional now, but running off the bottom of the image itself is not",
            bottom <= ExportFrame.HEIGHT,
        )
    }

    /**
     * The columns are what the row's own text measures when there is room for it - a name column
     * sized for the name, not for a fraction of the frame that leaves nothing behind it.
     */
    @Test fun `compare columns are the text's own width when it fits`() {
        val (name, facts) = ExportStrip.compareColumns(nameWidth = 120f, factsWidth = 800f, budget = 1600f)
        assertEquals(120f, name, 0.01f)
        assertEquals(800f, facts, 0.01f)
    }

    /**
     * When they do not fit, both columns shrink by the same factor rather than one of them alone.
     * Shrinking only one would break the one thing shared columns exist for: every row's facts
     * would stop starting at the same x as every other row's, since "smaller" would mean a
     * different amount on a row whose own facts happened to be short.
     *
     * And what they shrink into is the budget MINUS the gap between them - the compare row's own
     * [ExportStrip.ROW_ITEM_GAP], not the ordinary plate's [ExportStrip.LABEL_GAP]. Not charging
     * for that gap at all is exactly the arithmetic that made a bare compare row measure one gap
     * more than the plate had, and with it dropped the date from every frame of every compare
     * export.
     */
    @Test fun `compare columns that do not fit shrink together, gap included`() {
        val budget = 1000f
        val nameWant = 600f
        val factsWant = 900f
        val gap = ExportStrip.ROW_ITEM_GAP
        assertTrue(
            "budget must be narrower than the pair, or this proves nothing",
            ExportStrip.chipContentWidth(nameWant, factsWant, gap = gap) > budget,
        )

        val (name, facts) = ExportStrip.compareColumns(nameWant, factsWant, budget)

        assertEquals(
            "the columns and the gap between them must fill the budget exactly",
            budget,
            ExportStrip.chipContentWidth(name, facts, gap = gap),
            0.5f,
        )
        assertEquals(
            "shrinking together keeps the two in proportion, which is what keeps the table",
            nameWant / factsWant,
            name / facts,
            0.001f,
        )
    }

    /** A zero or negative budget - a frame too narrow for even the avatar - draws no columns at all. */
    @Test fun `compare columns never go negative`() {
        assertEquals(0f to 0f, ExportStrip.compareColumns(120f, 800f, budget = 0f))
        assertEquals(0f to 0f, ExportStrip.compareColumns(120f, 800f, budget = -10f))
    }

    /** One fixture for the horizontal bound below: what two rows' text wants, before any columns. */
    private data class BoundFixture(
        val names: List<Float>,
        val facts: List<Float>,
        /** Per row, 0 for a player whose rank nobody knows. Two different widths on purpose. */
        val ranks: List<Float> = listOf(0f, 0f),
    )

    /**
     * THE HORIZONTAL BOUND, which nothing tested: everything a compare row draws - its award dots
     * AND the rank at the end of the line - has to end at or before the plate's inner right edge.
     * The retired tripwire was vertical and so is its replacement, and the award dots were being
     * laid out from the COLUMN widths on a plate whose columns were wider than it was: at 1920 they
     * started at x=1858 with 62px of frame left, which one two-digit count on a span row runs
     * straight off, and two kinds always do.
     *
     * The rank is covered here rather than left out because it is drawn RIGHT-ALIGNED now, which is
     * a second way to run past an edge and the one this class of bug arrived through: it used to be
     * fused into the facts, where a column ellipsized it to "R..." rather than letting it overflow.
     * A right-aligned element cannot overflow the right edge it is aligned to - so what this has to
     * catch is the other end of it, the rank reaching back over the award dots of its own row.
     *
     * The positions are derived the way [ExportStrip.drawComparePlate] derives them - the same
     * [ExportStrip.compareLayout], the same [ExportStrip.chipSegments] over the same column widths,
     * and the same [ExportStrip.compareRankLeft] - rather than restated, so this cannot pass by
     * agreeing with a copy of the drawing.
     *
     * The widths are deliberately far too wide for the frame: two rows whose names and facts each
     * want most of the plate, each carrying two award kinds with two-digit counts (a SPAN row, the
     * one that keeps its counts), and a rank of its own.
     */
    @Test fun `a compare row's content never runs past the plate's inner right edge`() {
        val frameWidth = ExportFrame.WIDTH.toFloat()
        val left = ExportStrip.LEFT
        val twoDigits = 40f
        val awards = ExportStrip.awardsWidth(listOf(twoDigits, twoDigits))
        // "Rank #4" beside "Rank #1204": the reserve is the widest of them, on every row, so the
        // short one cannot license dots that collide with the long one on the row below.
        val ranks = listOf(88f, 150f)

        listOf(
            "the ordinary frame" to BoundFixture(listOf(90f, 260f), listOf(760f, 820f), ranks),
            "text far too wide for the frame" to
                BoundFixture(listOf(1400f, 1500f), listOf(1600f, 1500f), ranks),
            "no ranks at all" to BoundFixture(listOf(90f, 260f), listOf(760f, 820f)),
            "ranks far too wide for the frame" to
                BoundFixture(listOf(1400f, 1500f), listOf(1600f, 1500f), listOf(700f, 900f)),
        ).forEach { (what, fixture) ->
            val (names, facts, rankWidths) = fixture
            val geometry = ExportStrip.compareLayout(
                frameWidth = frameWidth,
                left = left,
                nameWidths = names,
                factsWidths = facts,
                awardWidths = listOf(awards, awards),
                rankWidths = rankWidths,
            )
            val contentLeft = ExportStrip.compareContentLeft(left)
            val innerRight = ExportStrip.plateInnerRight(left, geometry.width)

            assertTrue("$what: the plate itself has to fit the frame", left + geometry.width <= frameWidth - left)
            geometry.rowContents.indices.forEach { row ->
                val (nameAt, factsAt, awardsAt) = ExportStrip.chipSegments(
                    geometry.nameCol, geometry.factsCol, awards, gap = ExportStrip.ROW_ITEM_GAP,
                )
                val drawnRight = contentLeft + awardsAt!! + awards
                assertTrue(
                    "$what: row $row draws its name at ${nameAt?.plus(contentLeft)}, its facts at " +
                        "${factsAt?.plus(contentLeft)} and its award dots out to $drawnRight, past " +
                        "the plate's inner right edge at $innerRight",
                    drawnRight <= innerRight + 0.01f,
                )
                val rankWidth = rankWidths[row]
                if (rankWidth > 0f) {
                    val rankLeft = ExportStrip.compareRankLeft(innerRight, rankWidth)
                    assertTrue(
                        "$what: row $row's rank ends at ${rankLeft + rankWidth}, past the plate's " +
                            "inner right edge at $innerRight",
                        rankLeft + rankWidth <= innerRight + 0.01f,
                    )
                    assertTrue(
                        "$what: row $row's rank starts at $rankLeft, back over its own award dots " +
                            "which end at $drawnRight",
                        rankLeft >= drawnRight - 0.01f,
                    )
                }
            }
        }
    }

    /**
     * And the regression that bound is the other half of: at the only width an export ever runs at,
     * a compare row's columns must not be shrunk when the plate has room for what they want.
     *
     * The columns were two fractions of the FRAME (132dp and 200dp of a 360dp phone, read as 36.7%
     * and 55.6% of 1920px), which came to 1633px of column budget spent by 1643px of bare player
     * row - so the plate had nothing left over, and the date chip it carried on its first row back
     * then was dropped from every frame of every export, with a warning running a few hundred times
     * per export on the encode thread. The chip is gone (the date is the corner readout's job now)
     * and the arithmetic that was wrong under it is what this holds.
     */
    @Test fun `a compare plate at frame width does not shrink columns that fit`() {
        val geometry = ExportStrip.compareLayout(
            frameWidth = ExportFrame.WIDTH.toFloat(),
            left = ExportStrip.LEFT,
            // A short name and a long fused facts run ("3h 20m · 12 achievements · 240 pts ·
            // Rank #91"), which is the ordinary single-game compare row.
            nameWidths = listOf(70f, 240f),
            factsWidths = listOf(900f, 980f),
            awardWidths = listOf(0f, ExportStrip.awardsWidth(listOf(0f, 0f))),
        )

        assertEquals("a name column that is not shrunk is the name's own width", 240f, geometry.nameCol, 0.01f)
        assertEquals("as is the facts column", 980f, geometry.factsCol, 0.01f)
    }

    /** And a row that genuinely cannot fit spends every pixel the plate has on its columns. */
    @Test fun `a compare plate too narrow for its text fills the plate with it`() {
        val geometry = ExportStrip.compareLayout(
            frameWidth = ExportFrame.WIDTH.toFloat(),
            left = ExportStrip.LEFT,
            nameWidths = listOf(1400f),
            factsWidths = listOf(1400f),
            awardWidths = listOf(0f),
        )

        assertEquals(
            "the columns take the whole plate rather than leaving room empty",
            ExportStrip.plateInnerRight(ExportStrip.LEFT, geometry.width),
            ExportStrip.compareContentLeft(ExportStrip.LEFT) + geometry.rowContents.first(),
            0.5f,
        )
    }

    /**
     * The "·" is drawn between slotted facts too now (owner request): the header's
     * "271 achievements   1428 pts" pair read as one blur without it, and the room was reserved
     * all along - only the glyph was skipped. See [ExportStrip.dotBetween].
     */
    @Test fun `a slotted pair gets its separator drawn too`() {
        val slotted = JourneyStripText.Chip("", "12 achievements", slotDp = 150f)
        val alsoSlotted = JourneyStripText.Chip("", "240 pts", slotDp = 90f)
        assertTrue(ExportStrip.dotBetween(alsoSlotted, previous = slotted))
    }

    // ---- the pixel font's text scale ----

    private fun fontStyle(pixelFont: Boolean) = ChartStyle(
        background = androidx.compose.ui.graphics.Color.Black,
        panel = androidx.compose.ui.graphics.Color.DarkGray,
        grid = androidx.compose.ui.graphics.Color.Gray,
        ink = androidx.compose.ui.graphics.Color.White,
        inkDim = androidx.compose.ui.graphics.Color.LightGray,
        accent = androidx.compose.ui.graphics.Color.Cyan,
        seriesColors = List(12) { androidx.compose.ui.graphics.Color.Magenta },
        rivalColors = List(3) { androidx.compose.ui.graphics.Color.Green },
        typeColors = AchievementType.values().associateWith { androidx.compose.ui.graphics.Color.Red },
        silver = androidx.compose.ui.graphics.Color.LightGray,
        gold = androidx.compose.ui.graphics.Color.Yellow,
        labelFont = androidx.compose.ui.text.font.FontFamily.Default,
        pixelFont = pixelFont,
    )

    /**
     * The strip's text in the pixel font is 0.7 scale, like every pixel-font style in the rest of
     * the app (owner report, with an exported frame: full-size Press Start 2P glyphs overran the
     * strip and sat visibly off-centre against the award dots beside them). One rule at the one
     * seam every measured run passes through ([ExportStrip.measureStyle] via this), so no single
     * counter can be forgotten at full size.
     */
    @Test fun `the pixel font's strip text is drawn at the app's own 0-7 scale`() {
        assertEquals(20f, ExportStrip.scaledTextSize(28f, fontStyle(pixelFont = true)), 0f)
        assertEquals(28f, ExportStrip.scaledTextSize(28f, fontStyle(pixelFont = false)), 0f)
    }

    /** Whole numbers, for the same measurer-cache reason the chart's tick sizes are whole. */
    @Test fun `a scaled size lands on a whole number`() {
        listOf(22f, 26f, 28f, 34f).forEach { raw ->
            val scaled = ExportStrip.scaledTextSize(raw, fontStyle(pixelFont = true))
            assertEquals("$raw scales to non-whole $scaled", scaled, scaled.toInt().toFloat(), 0f)
        }
    }

    /**
     * Where an award dot's centre sits against the count beside it. In the ordinary fonts the
     * text's glyphs straddle the middle of their own line box, so centring the dot on the row is
     * centring it on the digits. Press Start 2P's digits do not: the font has no descenders and
     * its ascent overshoots its em, so the glyphs sit low in the box and a row-centred dot rides
     * visibly high beside them (owner report, two exported frames). The pixel branch centres on
     * the digits themselves - the em square above the baseline - which needs the text's own
     * baseline and drawn size, not just the row.
     */
    @Test fun `an ordinary font's dot centres on the row`() {
        assertEquals(
            30f,
            ExportStrip.awardDotCenterY(
                top = 0f, height = 60f, textHeight = 28, firstBaseline = 26f,
                textEmPx = 20f, pixelFont = false,
            ),
            0f,
        )
    }

    @Test fun `the pixel font's dot centres on the digits, below the row centre`() {
        // Text box 28 tall centred in a 60 row starts at 16; digits span [baseline-em, baseline] =
        // [6, 26] inside the box, so their centre is 16 + 26 - 10 = 32 - two px below row centre.
        assertEquals(
            32f,
            ExportStrip.awardDotCenterY(
                top = 0f, height = 60f, textHeight = 28, firstBaseline = 26f,
                textEmPx = 20f, pixelFont = true,
            ),
            0f,
        )
    }
}
