package com.twinlore.app.journey.export

import android.util.Log
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.twinlore.app.journey.domain.AwardGrading
import com.twinlore.app.journey.domain.model.AwardKind
import com.twinlore.app.journey.ui.JourneyStripText
import com.twinlore.app.journey.ui.JourneyUrls
import com.twinlore.app.journey.ui.chart.ChartStyle
import com.twinlore.app.journey.ui.chart.JourneyChartRenderer
import com.twinlore.app.journey.ui.colorForRivalIndex
import kotlin.math.roundToInt

/**
 * The overview strip, drawn into an exported frame as one plate.
 *
 * A shared video or image used to arrive with only the chart on it: the counters that say what the
 * journey came to - the date it had reached, the points, the awards, how many games or how far
 * through one game's list - live in Compose chips over the player, and nothing composed is on the
 * canvas an encoder sees. So the same counters are drawn here, in frame pixels rather than in the
 * chart's virtual space, on a single panel above the plot with the game's icon at its head - the
 * shape the watcher saw, not a strip of boxes wrapped over two rows.
 *
 * The wording is not this file's: it comes from [JourneyStripText], which the player's own chips
 * read too, so the strip on a shared clip cannot drift from the strip that was watched.
 *
 * The layout arithmetic is pure and separate from the drawing ([chipContentWidth], [chipSegments],
 * [factsWidth], [plateWidth], [factsBudget], [chipsThatFit], and [compareColumns]/[compareLayout]
 * for the compare plate's rows) because the failure it prevents - a counter running off the right
 * of the frame, into the one beside it, or under the bar at the plate's far end - is invisible in a
 * rendered bitmap and obvious in a test.
 */
internal object ExportStrip {

    private const val TAG = "ExportStrip"

    /**
     * The dual bar a single-game plate carries at its far end: how much of the achievement list is
     * unlocked, and how much of it was taken in hardcore.
     *
     * The same two numbers the on-screen header's bar draws (`JourneyPlayer`'s `GameProgressBar`),
     * in the same two metals - the whole revealed count in silver, the hardcore share in gold over
     * it. A journey and the RA drawer for the same game are two views of one number.
     */
    data class BarState(
        val percent: Int,
        val hardcorePercent: Int,
        /**
         * The highest award this game has reached, drawn as the chart's own kind dot beside the
         * percentage under the bar - null until it has reached one.
         *
         * A dot and no word, because that is what the watcher saw: `JourneyPlayer`'s
         * `GameHeaderBar` puts a bare `AwardCircle` next to exactly this number. The plate used to
         * get the award as words on its title chip instead ("1 mastered"), which was a count of
         * one taking the width of a sentence on the one line that also has to hold the game's name.
         */
        val award: AwardKind? = null,
    )

    /** Where the strip starts, in frame pixels. Clear of the chart caption drawn above it. */
    const val LEFT = 64f
    const val TOP = 62f

    /**
     * Between one player's row on a compare plate and the next player's - the compare bar's own
     * `Arrangement.spacedBy(2.dp)` (`JourneyPlayer.PlayerCompareBar`'s `Column`) at [DENSITY].
     *
     * Compare-only, which is why it can be the screen's number outright rather than needing a
     * constant of its own: nothing but [compareRowTops] reads it. Contrast [ROW_ITEM_GAP], which
     * had to be introduced precisely because the horizontal gaps it replaces are shared.
     */
    const val ROW_GAP = 5f
    const val PAD_H = 18f

    /** Room between a chip's label and its value, on the ordinary/single-game plate. */
    const val LABEL_GAP = 10f

    // ---- the award breakdown a chip can carry (see [awardsWidth], [drawAwards]) ----

    /**
     * How big an award's kind dot is drawn, in frame pixels. 1.5x what it was (9f):
     * the dots read small on a 1920x1080 frame next to the counters beside them.
     *
     * Not a whole number's worth of caution the way [LABEL_SIZE] and its siblings are: those are
     * whole because the `TextMeasurer` cache is keyed on the whole text style, and this is a circle
     * radius handed straight to `drawCircle`, which has no such cache to miss. 13.5f rather than
     * 14f because nothing downstream needs a whole number here (see [DOT_RING] for the one thing
     * that *is* worth keeping clean at this size), and 13.5 is exactly 1.5x the old radius, which
     * 14 is not.
     *
     * Everything else this dot's size touches reads it live rather than carrying its own copy:
     * [readoutWidth] and [awardsWidth] compute the width they reserve as `DOT_R * 2f + DOT_GAP`
     * per dot, so the layout that makes room for this dot grows the moment this does, and
     * [drawAwardDot]'s own glow (`DOT_R * JourneyChartRenderer.AWARD_GLOW_SPAN`) scales with it
     * too, because [JourneyChartRenderer.AWARD_GLOW_SPAN] is a ratio, not a fixed span. Only
     * [DOT_RING] needed a hand-retune, since a stroke width is neither.
     */
    const val DOT_R = 13.5f

    /**
     * Between a dot and its own count, and between one kind and the next.
     *
     * Left as it was, deliberately: this is breathing room BESIDE the dot, not a measurement of
     * it, so it has no ratio to [DOT_R] to preserve the way [DOT_RING] does. An absolute 7px gap
     * still reads as a gap next to a 27px dot - if anything a fixed gap looks tighter, and more
     * clearly a single mark with the dot, as the dot it sits next to grows, never wider. What DOES
     * have to keep pace is the layout that reserves room for it, and [readoutWidth]/[awardsWidth]
     * already read [DOT_R] live for that (see [DOT_R]'s own doc) - this constant needing no change
     * is what makes that true rather than a coincidence.
     */
    const val DOT_GAP = 7f
    const val PART_GAP = 14f

    /**
     * How thick a hollow (completed) dot's outline is drawn, in frame pixels.
     *
     * 1.5x what it was (4f), the same factor [DOT_R] grew by: unlike [DOT_GAP], this IS a
     * measurement of the dot rather than room beside it (`drawAwardDot` draws it at
     * `DOT_R - DOT_RING / 2f` with a `Stroke(DOT_RING)`, so it is anchored to the dot's own edge),
     * and it had to scale with the radius to keep the same proportion of the dot's own disc. Left
     * at the old fixed 4f, a ring on a 13.5px dot would have read thinner relative to the mark it
     * outlines than it did on the 9px one, when the whole point of growing the dot was to make it
     * (and everything about it) easier to read. 6f, not a rounded 6.something: 4 times the same
     * 1.5 factor lands on a whole number outright.
     */
    const val DOT_RING = 6f

    /**
     * A SINGLE GAME's ordinary FACT chip - `GameHeader.countsLine`, drawn from
     * `header.achievements`/`points`/`date` fused into ONE `bodySmall` string ("12 achievements ·
     * 240 pts · Aug 12"), with no label separated from its value by size or weight; only the words
     * tell them apart. That plate's TITLE chip is the one exception - see [TITLE_SIZE].
     *
     * So unlike [END_LABEL_SIZE]/[END_VALUE_SIZE] below, these two are NOT a screen size times
     * [DENSITY], on purpose: there is no second on-screen style to trace a *label* to, only one
     * fused run that a *label* and a *value* were invented out of for legibility (dim word, bright
     * number) after the fact. Picking `bodySmall × DENSITY` for both would not be unfaithful so
     * much as beside the point - it would still draw two segments where the screen draws one - and
     * inventing a split the screen never had is what this pair was left un-traced to avoid.
     *
     * A RUNNING SPAN used to share them and no longer does. Its plate is not a row of invented
     * label/value counters any more but the screen's own two-line header (see [SPAN_FACT_SIZE]),
     * and a header that draws a title over a facts line has two real screen styles to trace -
     * which is exactly the split this pair never had. What is left here is the single-game plate,
     * whose shape is still a labelled row nothing on screen draws one chip at a time.
     *
     * Whole numbers, because the TextMeasurer's cache is keyed on the whole text style and a
     * fractional size would miss it on every frame of a video.
     */
    const val LABEL_SIZE = 20f
    const val VALUE_SIZE = 26f

    /**
     * A RUNNING SPAN's facts line - `PlaybackHeader`'s second row, one `bodySmall` `Text` (12sp at
     * [DENSITY]) carrying the date, the achievements and the points as one fused run.
     *
     * Traced, where [LABEL_SIZE]/[VALUE_SIZE] deliberately were not, and the reason is the shape
     * rather than a change of mind about the arithmetic. That pair was left un-traced because a
     * running span's plate was five label/value counters on one baseline and the screen's header
     * had no label/value split to map them onto - so any mapping would have been invented. The
     * plate is the header now: a title line and a facts line, which is a genuine two-style split,
     * and both halves have a real screen token behind them ([TITLE_SIZE]'s `titleSmall` for the
     * title, this `bodySmall` for the facts). Tracing it is no longer inventing anything.
     *
     * It also happens to be the user-visible half of the complaint this landed for: the finished
     * span's own bar reads well at [END_LABEL_SIZE]/[END_VALUE_SIZE] and the running one was
     * noticeably smaller beside it, because 20/26 were picked before either had a screen behind it.
     */
    const val SPAN_FACT_SIZE = 28f

    /**
     * The count beside a running span's award dots - `labelSmall` (11sp) at [DENSITY], bold, which
     * is what `AwardDots`' own `Text` defaults to on screen (`JourneyPlayer.AwardDots`, whose
     * `textStyle` the playback header leaves null).
     *
     * A size below [SPAN_FACT_SIZE] beside it, which is the screen's own ordering: the dots are a
     * tally hung off the end of the facts line, not another fact on it. It is numerically the same
     * as [ROW_RANK_SIZE] today and is its own constant for the reason [TITLE_SIZE] is not folded
     * into [END_LABEL_SIZE]: two different tokens on two different surfaces that agree by
     * arithmetic, not by design.
     */
    const val SPAN_AWARD_SIZE = 25f

    /**
     * A FINISHED span's ordinary chip - `EndPanel`'s own `label`/`value` pair, which is the one
     * ordinary-plate state that mirrors a chip exactly: `EndPanel` loops over the very same
     * `List<JourneyStripText.Chip>` this file draws (`labelLarge` for `chip.label`, bold
     * `titleMedium` for `chip.value`), so there is nothing invented in tracing them through
     * [DENSITY] the way [ROW_NAME_SIZE] and its siblings were - `labelLarge` is 14sp and
     * `titleMedium` is 16sp (`androidx.compose.material3.Typography()`'s own defaults; this
     * app's `buildTypography` only rescales for the Pixel font, never for the default one these
     * numbers assume - see `ExportStripTest`'s own DENSITY guard).
     *
     * `drawExportStrip` only reaches for these when its caller marks the chips as a finished
     * span's (see its own `finishedSpan` parameter); a running span's and a single game's fact
     * chips keep [LABEL_SIZE]/[VALUE_SIZE], which have no such screen counterpart to trace - see
     * that pair's own doc for why.
     */
    const val END_LABEL_SIZE = 32f
    const val END_VALUE_SIZE = 37f

    /**
     * A HEADER's TITLE chip ([Chip.isTitle]) - `PlaybackHeader`'s own title `Text`, bold
     * `titleSmall` (14sp, confirmed off `androidx.compose.material3.Typography()`'s own default
     * the same way [END_LABEL_SIZE]/[END_VALUE_SIZE] were, not carried over from them even though
     * it happens to round to the same pixel count - `titleSmall` and `labelLarge` are two
     * different tokens that could diverge on a future theme change, and coupling them because
     * today's numbers agree would be exactly the kind of drift [ExportStripTest]'s DENSITY guard
     * exists to catch).
     *
     * This is a per-CHIP size, not a per-PLATE one like [LABEL_SIZE]/[VALUE_SIZE]/
     * [END_LABEL_SIZE]/[END_VALUE_SIZE]: `drawChipRun` already colours a title chip differently
     * from the fact chips beside it (`style.accent`, see its own doc) because `GameHeaderBar`
     * hands `PlaybackHeader`'s title its own `iconRing` colour - the size was the other half of
     * that same distinction, left unmade. Was [VALUE_SIZE] (26px, unrelated to `titleSmall`)
     * until this constant; a single game's title is very often the widest run on its plate
     * (`ExportStripTest`'s own overflow/DENSITY guards below cover it growing to 32px).
     *
     * Both header shapes draw their title at it now: a single game's plate and a running span's
     * two-line one are the same `PlaybackHeader` on screen (`GameHeaderBar` IS a `PlaybackHeader`),
     * so a second title constant for the span would have been the same token twice.
     */
    const val TITLE_SIZE = 32f

    // ---- the plate the counters sit on ----

    /**
     * The plate's own padding, its leading game icon, and the room between its stacked lines.
     *
     * On screen the counters sit on ONE panel with a dim dot between them (see `JourneyPlayer`'s
     * `EndPanel` and `PlaybackHeader`); this is the geometry that panel is measured by.
     */
    const val PAD_V = 14f
    const val ICON = 56f
    const val ICON_CORNER = 8f
    const val ICON_GAP = 16f
    const val LINE_GAP = 8f

    /** Between the facts and the bar a single-game plate carries at its far end. */
    const val TRAILING_GAP = 24f
    const val BAR_W = 240f
    const val BAR_H = 12f

    /** Between the bar and the percentage drawn under it, in the same trailing block. */
    const val BAR_PERCENT_GAP = 6f

    /** The dim dot between one counter and the next, and the room either side of it. */
    const val SEP = "·"
    const val SEP_GAP = 10f

    // ---- the band's scrim (see [drawBandScrim]) ----

    /**
     * How much of [ChartStyle.background] the top of the band's scrim carries.
     *
     * The screen's own number: the player's top band is backed by
     * `Brush.verticalGradient(background.copy(alpha = 0.88f) to Transparent)` across its whole
     * width (`JourneyPlayer`, introduced when the band's readouts gave up their individual panels
     * for one shared scrim). The export had no equivalent at all, so the same strip of frame that
     * darkens on screen sat bare over the picture, and light backdrop art took the readouts with
     * it.
     *
     * Not [ChartStyle.panel]: that is `surfaceVariant`, the FOREGROUND colour at 12% alpha, meant
     * to be composited over the background rather than painted. The scrim is the background itself,
     * at the alpha the screen uses.
     */
    const val SCRIM_ALPHA = 0.96f

    /**
     * WHAT A SCREEN PIXEL IS WORTH HERE, which [ROW_H], [ROW_AVATAR], [ROW_NAME_SIZE],
     * [ROW_FACT_SIZE] and [ROW_RANK_SIZE] are all derived through.
     *
     * An exported frame is 1920x1080 LANDSCAPE, and so is the player it is a picture of: on the
     * device this feature is used on, the journey plays full screen at 1920x1080 with a display
     * density of 2.3, which is a window about 834dp wide. Frame pixel and screen pixel are the
     * SAME pixel, so a dp on that screen is worth exactly [DENSITY] of them - a compare row drawn
     * at this scale is the row the watcher saw, at the size they saw it.
     *
     * These five were once derived a different way, and it was wrong: they read the screen's dp as
     * a FRACTION of a 360dp-wide phone in portrait and applied that fraction to 1920px, which made
     * a 26dp row 7.22% of the frame - 139px, and a 12sp label 64px. That reference has nothing to
     * do with the export. It is neither the frame's aspect nor the device's width, and the numbers
     * it produced came out about 2.3x too big, which is the whole of the density it had left out:
     * an exported compare bar wore type more than twice the size of the bar it was copying, and
     * the facts under it truncated to "alphami..." because a row that size cannot hold a sentence.
     * Nothing here is a fraction of the frame. Every one of them is a dp off `JourneyPlayer`,
     * multiplied by [DENSITY] and rounded to a whole number.
     */
    const val DENSITY = 2.3f

    /**
     * Between the bar and the award circle at its end.
     *
     * `JourneyPlayer`'s own `HEADER_AWARD_GAP` - 8dp, itself `AchievementsBoard`'s spacer between
     * the same two things - through [DENSITY]. Declared here rather than beside [BAR_W] and
     * [BAR_PERCENT_GAP], which it belongs with, because a `const val` cannot read one declared
     * below it and [DENSITY] is what it is measured in.
     */
    const val BAR_AWARD_GAP = 8f * DENSITY

    /**
     * One player's row on a compare plate. Tall enough for a name beside its facts.
     *
     * `JourneyPlayer.COMPARE_LINE_HEIGHT`, 26dp, at [DENSITY] - the screen's own row height in
     * frame pixels, the same way [ROW_AVATAR] and the three text sizes are.
     *
     * A version of this row bound to the chart's own plot top edge was tried first and rejected -
     * not by this file, by the plate's own history. That bound was inherited from when a compare
     * row was an OPAQUE plate: a box drawn over the chart would have blocked it out, so the rows
     * had to stay inside the letterbox band above the plot, and a tripwire test
     * (`ExportStripTest`'s retired "compare rows stay clear of the plot area at the rival cap")
     * held them there. Once the plate went transparent (see [drawComparePlate]'s own doc) that
     * bound stopped meaning anything: the screen's own bar has always sat OVER the top of the
     * chart, never inside a band reserved for it. Keeping the export inside a band the plate no
     * longer needed is what was keeping the export from looking like the screen - so the rows now
     * overlay the chart too, same as the thing they are copying. What still has to hold is only
     * that they stay inside the FRAME itself; see `ExportStripTest`'s "compare rows stay inside
     * the frame at the rival cap" for that, and note it is now slack rather than tight - a row at
     * the screen's real size leaves most of the frame under it.
     */
    const val ROW_H = 60f

    /**
     * The compare row's completion bar, narrower and slimmer than the solo plate's [BAR_W] x
     * [BAR_H]: it shares its line with a name, the facts, the award dots and the rank, and it is
     * one of up to four stacked rows rather than a plate's single trailing block. The screen's
     * row makes the same trade against its own header bar (`JourneyPlayer.COMPARE_BAR_WIDTH`).
     */
    const val ROW_BAR_W = 120f
    const val ROW_BAR_H = 8f

    /**
     * The compare row's FIXED facts column: `JourneyPlayer.COMPARE_FACTS_COL` (240dp) through
     * [DENSITY], sized to the widest realistic run rather than to any frame's text, so the bar,
     * percent and dot columns after it hold still while the counts grow (owner request).
     */
    const val ROW_FACTS_COL = 240f * DENSITY

    /**
     * The disc a compare row's avatar is drawn on, leading the name the way the screen's own row
     * does - `JourneyPlayer.COMPARE_LINE_AVATAR`, 20dp, at [DENSITY].
     *
     * The dp, not a fraction of [ROW_H]. It was 0.77 of the row height, which is the same number
     * for as long as both are derived from the same reference and silently stops being the screen's
     * avatar the moment either one is retuned on its own.
     */
    const val ROW_AVATAR = 46f

    // Whole numbers, same reason as LABEL_SIZE/VALUE_SIZE above: the TextMeasurer's cache is
    // keyed on the whole text style. Their own constants rather than a reuse of LABEL_SIZE/
    // VALUE_SIZE, because those two are shared with the single-game/ordinary plate this task does
    // not touch - growing them would have resized counters nobody asked to change.

    /**
     * A compare row's name, in [FontWeight.Bold] - `labelMedium`'s 12sp at [DENSITY], which is what
     * the screen's own name is set in (`JourneyPlayer.PlayerCompareLine`).
     */
    const val ROW_NAME_SIZE = 28f

    /**
     * A compare row's facts (and its award counts) - `labelMedium` too, at the same 28px, because
     * the screen sets its facts in the same style as its name and only the colour tells them apart.
     *
     * This used to be `labelSmall`, the screen's RANK size, deliberately: the export had no
     * separate rank text to give the smaller size to, because `compareFacts` fused the rank into
     * the same run as the score, and a fused string set at the name's size would have drawn a
     * player's rank as loud as their name. The fused string is gone (see
     * [JourneyStripText.compareLineFacts]) and the rank has its own size again, so the facts can
     * take the size the screen gives them.
     */
    const val ROW_FACT_SIZE = 28f

    /**
     * A compare row's rank, right-aligned at the end of the line - `labelSmall`'s 11sp at
     * [DENSITY], the size and the ordinary weight the screen sets it in.
     *
     * A size below the facts beside it, which is the screen's own ordering: where a player stands
     * is the last thing on the row and the quietest.
     */
    const val ROW_RANK_SIZE = 25f

    /**
     * Between one thing on a compare row and the next: the screen's own
     * `Arrangement.spacedBy(6.dp)` (`JourneyPlayer.PlayerCompareLine`'s `Row`) at [DENSITY].
     *
     * ONE constant for all four gaps on the row - avatar to name, name to facts, facts to award
     * dots, dots to the rank - because that is exactly what `spacedBy` is: a single value the row
     * puts between every pair of children. Splitting it here would be inventing a distinction the
     * thing being copied does not make.
     *
     * Its own constant rather than a retune of [ICON_GAP] and [LABEL_GAP], which is what a compare
     * row used to borrow for this. Those two are SHARED with the ordinary/single-game plate -
     * [ICON_GAP] is the room after that plate's leading game icon, [LABEL_GAP] the room between a
     * chip's label, its value and its dots - and this task is not retuning that surface. Changing
     * them in place would have moved every counter on every solo export to fix a compare row, which
     * is a bad trade in either direction. Where the shared arithmetic ([chipContentWidth],
     * [chipSegments], [plateWidth]) has to lay out a compare row, it is handed this instead through
     * a parameter, so there is still exactly one function that knows what a gap costs.
     */
    const val ROW_ITEM_GAP = 14f

    /**
     * WHERE THE COMPARE ROW'S COLUMNS COME FROM, and why they are not the screen's.
     *
     * They were two fractions of the frame - the screen's own `COMPARE_NAME_COL`/
     * `COMPARE_FACTS_COL` (132dp and 200dp of a 360dp phone) read as 36.7% and 55.6% and applied
     * to 1920px. That is 1771px of columns before a gap, an avatar or an award dot, inside the
     * 1633px the plate actually has: a bare player row measured 1643 and a plate for it 1802, over
     * the 1792 the frame allows. The plate carried a date chip on its first row back then, and it
     * never fit on any frame of any compare export - it was dropped from all of them, with a
     * [Log.w] running a few hundred times per export on the encode thread to say so.
     *
     * The root cause is not the number, it is the reinterpretation. On screen those dp are ABSOLUTE
     * widths inside a `fillMaxWidth` row: the leftover after them - which on a phone is the awards
     * and the rank - is whatever is left, and a short name simply leaves 132dp of whitespace before
     * the score. Read as fractions of a frame five times wider, the same proportions hand a name
     * like "me" a 649px column and leave nothing behind it.
     *
     * Reading them as absolute dp at [DENSITY] - 304px and 460px, which is how the rest of this
     * row's geometry is derived - would be the faithful translation, and it is still not what this
     * does. The frame is 1920px wide where the phone those two columns were tuned on is about 828,
     * so fixed columns would leave most of the plate empty and ellipsize any name or facts run
     * wider than its column with hundreds of pixels of frame to spare.
     *
     * So the columns are MEASURED here: the widest name across the rows, and the widest facts run,
     * each capped by the room the plate genuinely has and shrunk together when they exceed it (see
     * [compareColumns], [compareLayout]). The table survives - every row's facts still start at one
     * shared x, which is the whole reason the screen fixes its columns - while a short name stops
     * charging the row for width no one is using, and the facts stop being ellipsized inside a
     * column narrower than the frame could afford. What is given up is that a column no longer
     * holds still frame to frame: the facts run grows as the counts do, and the award dots after it
     * move with it. That is the honest trade for a row that fits.
     */

    /**
     * How wide a chip's own content is: its label, its value and its award dots, with a gap
     * between each pair that is actually there.
     *
     * A segment that is not there costs nothing, gap included: the awards chip has no label and no
     * value (its dots say both), and padding for two empty text runs would be a wide empty gap in
     * the middle of every plate.
     */
    /**
     * The room the "·" leading an awards run claims, when it leads one at all: a chip whose dots
     * follow a label or a value on the same run gets a separator in front of them, the same mark
     * the plate already draws between chips (owner request - the dots read as part of the fact
     * before them without it). Zero when the chip is dots-only (the end strip's awards chip rides
     * between two chip-level separators already; a third would double them) or has no dots.
     */
    fun awardsSepLead(hasRunBefore: Boolean, awardsW: Float, sepW: Float, gap: Float): Float =
        if (hasRunBefore && awardsW > 0f) sepW + gap else 0f

    fun chipContentWidth(
        labelWidth: Float,
        valueWidth: Float,
        awardsWidth: Float = 0f,
        /** [LABEL_GAP] for an ordinary chip; a compare row passes its own [ROW_ITEM_GAP]. */
        gap: Float = LABEL_GAP,
        /**
         * The room this chip claims whatever it currently measures - [slotWidth] of a chip's own
         * [JourneyStripText.Chip.slotDp], 0 for a chip that takes its own width.
         *
         * A floor, not a cap: a run that outgrows its slot still gets the room it needs rather than
         * being drawn over its neighbour. The slot's whole job is to stop a counter's width moving
         * with its digits, and one that has to exceed it has already told the layout it was picked
         * too narrow.
         */
        slot: Float = 0f,
    ): Float {
        val parts = listOf(labelWidth, valueWidth, awardsWidth).filter { it > 0f }
        if (parts.isEmpty()) return 0f
        return maxOf(parts.sum() + gap * (parts.size - 1), slot)
    }

    /** A chip's slot in frame pixels: its [JourneyStripText.Chip.slotDp] through [DENSITY]. */
    fun slotWidth(chip: JourneyStripText.Chip): Float = chip.slotDp * DENSITY

    /**
     * Whether a dot is DRAWN between two neighbouring chips on a line: between every pair now.
     *
     * Slotted facts used to skip it - the slots held them apart, and the screen's header drew its
     * facts as plain runs with no glyph between them - but "271 achievements   1428 pts" read as
     * one blur across the gap (owner request), so the screen's slotted row grew the same "·" its
     * fused fallback string always carried, and the plate follows it. The room was reserved
     * either way all along ([factsWidth] charges one gap per pair before anything knows what is
     * drawn in it), so only the glyph changes.
     *
     * Kept as a named seam rather than inlined `true`: it is the one place "does a pair get a
     * dot" is decided, and the test that pins the rule names it.
     */
    fun dotBetween(chip: JourneyStripText.Chip, previous: JourneyStripText.Chip): Boolean = true

    /**
     * Where a chip's label, its value and its award dots start, measured from the chip's own left
     * edge - null for a segment that is not there. Index for index with [chipContentWidth]'s three
     * widths.
     *
     * This is the arithmetic [drawChipRun] positions its text by, so a chip cannot be drawn to a
     * different width than the plate reserved for it: the gap goes only BEFORE a segment that
     * follows one already placed, which is exactly [chipContentWidth]'s own rule. `drawChipRun`
     * used to keep its own running cursor and charge a gap after every segment it drew, whether or
     * not anything followed, so a chip with no awards - most of them, since every ordinary counter
     * inherits `awards = emptyList()` - advanced one gap further than the plate had reserved for
     * it. With two or more such chips on the same plate, the later counters drew past its
     * rounded-rect right edge, onto bare backdrop with no panel behind them. A duplicate of
     * `chipContentWidth` was tried as the fix, which made the test comparing the two a function
     * against its own copy; this is the version that cannot silently agree.
     */
    fun chipSegments(
        labelWidth: Float,
        valueWidth: Float,
        awardsWidth: Float = 0f,
        /** The same gap [chipContentWidth] was measured with, or the two will disagree. */
        gap: Float = LABEL_GAP,
    ): List<Float?> {
        var cursor = 0f
        var placed = false
        return listOf(labelWidth, valueWidth, awardsWidth).map { width ->
            if (width <= 0f) return@map null
            if (placed) cursor += gap
            val at = cursor
            cursor += width
            placed = true
            at
        }
    }

    /**
     * How much of a [maxPlate]-wide plate is left for the facts once its padding, its leading icon
     * and its trailing block have taken theirs - [plateWidth] read backwards, and never negative.
     *
     * The bound the plate had no version of: [plateWidth] said how wide a plate WANTED to be and
     * the caller clamped the rounded rect to the frame, but nothing clamped what was drawn on it,
     * so the facts walked their full width across a plate that had stopped early. See
     * [chipsThatFit] for what is done about it.
     */
    fun factsBudget(maxPlate: Float, iconWidth: Float = 0f, trailingWidth: Float = 0f): Float {
        var budget = maxPlate - PAD_H * 2f
        if (iconWidth > 0f) budget -= iconWidth + ICON_GAP
        if (trailingWidth > 0f) budget -= TRAILING_GAP + trailingWidth
        return maxOf(budget, 0f)
    }

    /**
     * How many of [contentWidths], in the order given, fit inside [budget] with a separator
     * between each pair - so the plate draws that many counters and drops the rest.
     *
     * From the front, and the tail is what goes: every list of chips this file draws is ordered
     * with the counter that says the most at its head (the game's own name, the score), so losing
     * the end of the line loses the least. Zero is a possible answer and an honest one - a frame
     * too narrow to hold one counter beside the icon and the bar should draw none rather than draw
     * one across the bar - but not one any real export reaches: nothing on the plate may measure
     * more than [MAX_TEXT_WIDTH] on its own, and a 1920-wide frame leaves well over that.
     */
    fun chipsThatFit(contentWidths: List<Float>, sepWidth: Float, budget: Float): Int {
        var fits = 0
        contentWidths.indices.forEach { i ->
            if (factsWidth(contentWidths.subList(0, i + 1), sepWidth) > budget) return fits
            fits = i + 1
        }
        return fits
    }

    /**
     * How wide [contentWidths] are when laid on one baseline with a [sepWidth] dot between each
     * pair - the plate's facts line.
     */
    fun factsWidth(contentWidths: List<Float>, sepWidth: Float): Float {
        if (contentWidths.isEmpty()) return 0f
        return contentWidths.sum() + (contentWidths.size - 1) * (SEP_GAP * 2f + sepWidth)
    }

    /**
     * How wide a single game's bar row is: the bar, and the award circle at its end when the sweep
     * has reached one.
     *
     * The award used to sit UNDER the bar beside the percentage. It is at the end of the bar now
     * (user request), which is where the vault's achievement tab and the library's board both draw
     * it - `AchievementsBoard` gives the bar the row and hangs the circle off its end, with the
     * percentage underneath - and this header is a third view of the same completion.
     *
     * The circle's room is reserved WHETHER OR NOT there is one yet. The row is right-aligned, so a
     * width that depended on having an award moved the bar 69px sideways the instant one landed -
     * on device, mid-replay (user report). An award arrives once in a journey and the bar should
     * not be the thing that announces it.
     */
    fun barRowWidth(): Float = BAR_W + BAR_AWARD_GAP + DOT_R * 2f

    /**
     * How tall that row is: the award circle, which is taller than the bar and centred on it.
     *
     * Reserved unconditionally for the same reason [barRowWidth]'s room is - a height that appeared
     * with the award would move the whole trailing block up as it landed.
     */
    fun barRowHeight(): Float = maxOf(BAR_H, DOT_R * 2f)

    /**
     * How wide a plate wrapping [contentWidth] has to be, with an icon in front of it and a bar
     * behind it when it has them. A zero costs nothing, its gap included.
     */
    fun plateWidth(
        contentWidth: Float,
        iconWidth: Float = 0f,
        trailingWidth: Float = 0f,
        /**
         * The room after the leading icon: [ICON_GAP] for the ordinary plate's game art, and a
         * compare plate's own [ROW_ITEM_GAP] after the avatar, which is a different surface's gap.
         */
        iconGap: Float = ICON_GAP,
    ): Float {
        var width = PAD_H * 2f + contentWidth
        if (iconWidth > 0f) width += iconWidth + iconGap
        if (trailingWidth > 0f) width += TRAILING_GAP + trailingWidth
        return width
    }

    /**
     * How tall a plate carrying [lineHeights] is: its lines and the gaps between them, or the icon
     * beside them when that is taller. A single short line over a 56px icon would otherwise clip it.
     */
    fun plateHeight(lineHeights: List<Float>): Float {
        if (lineHeights.isEmpty()) return 0f
        val text = lineHeights.sum() + LINE_GAP * (lineHeights.size - 1)
        return PAD_V * 2f + maxOf(text, ICON)
    }

    /**
     * The top edge that centers a [blockH]-tall block inside a [plateH]-tall plate starting at
     * [plateTop] - the rule the single-game plate's bar-and-percentage block is positioned by, and
     * the rule the plate's own stack of text lines is positioned by ([lineTops]).
     *
     * It used to be two offsets derived from `plateH / 2` with hardcoded constants, which landed
     * the percentage's own last row exactly on the plate's own bottom edge - for one plate height,
     * one bar height, and the line height of one font at one size. Production resolves a different
     * font family than the one that arithmetic was checked against, and this draws into a file
     * somebody shares; an offset that only accidentally fit is not something a different font's
     * line metrics can be trusted to keep fitting. Centering the block by its own measured height
     * instead means it cannot run past either edge of the plate, whatever a particular font's
     * metrics turn out to be - at the cost of clipping being possible only if the block itself is
     * taller than the plate, which is a plate the caller has to grow, not an offset this function
     * can fix.
     *
     * Named for the block rather than for the trailing one it was written for: a two-line header
     * needs the identical rule for its text stack, and a second copy under a second name is how the
     * plate and the thing drawn on it drift apart.
     */
    fun centeredBlockTop(plateTop: Float, plateH: Float, blockH: Float): Float =
        plateTop + (plateH - blockH) / 2f

    /**
     * Where each of a plate's stacked text lines starts, given what each of them measures - the
     * inverse of [plateHeight], which is what the plate was sized by.
     *
     * The stack is centered in the plate as a whole ([centeredBlockTop]) rather than started at its
     * top padding, because the plate is at least as tall as its leading icon ([ICON]) and a single
     * short line under a taller icon must sit beside the icon's middle, not above it. For one line
     * that reproduces exactly where the one-baseline plate has always drawn: the line is the block,
     * so its top is the plate's own center less half a line.
     */
    fun lineTops(plateTop: Float, plateH: Float, lineHeights: List<Float>): List<Float> {
        if (lineHeights.isEmpty()) return emptyList()
        val block = lineHeights.sum() + LINE_GAP * (lineHeights.size - 1)
        var y = centeredBlockTop(plateTop, plateH, block)
        return lineHeights.map { height ->
            val at = y
            y += height + LINE_GAP
            at
        }
    }

    /**
     * How many lines a plate's chips stack into: one more than the highest [JourneyStripText.Chip.line]
     * on it, which is 1 for every plate but a running span's two-line header.
     *
     * A data-borne tell, exactly like [isCompare]: the shape is a fact about which chips these are,
     * and `JourneyStripText` is the file that knows which shape it built. Nothing here has to be
     * told a [com.twinlore.app.journey.domain.model.JourneyMode] exists.
     */
    /**
     * Whether the chip at [index] is the only one on its line, and therefore has the plate's whole
     * width to itself rather than a share of a baseline.
     *
     * Both lines of a stacked header answer true - the title is alone on the first and the fused
     * facts run is alone on the second - and every counter on a one-baseline plate answers false,
     * because there are three others beside it. See [MAX_TEXT_WIDTH].
     */
    fun soleOnLine(chips: List<JourneyStripText.Chip>, index: Int): Boolean {
        val line = chips[index].line
        return chips.count { it.line == line } == 1
    }

    fun lineCount(chips: List<JourneyStripText.Chip>): Int =
        (chips.maxOfOrNull { it.line } ?: -1) + 1

    /** Where each of [count] stacked player rows starts, for a compare plate. */
    fun compareRowTops(count: Int, top: Float = TOP): List<Float> =
        (0 until count).map { top + it * (ROW_H + ROW_GAP) }

    /**
     * How far down the frame the band's scrim reaches: from the frame's own top edge to [top] below
     * the strip's last line at [contentBottom].
     *
     * The band is the strip with the same inset under it that [TOP] puts above it, which is how the
     * screen's band is built - one `Row` with even padding, and the scrim is that Row's background,
     * so it covers the readouts and their breathing room and nothing else. It is measured from the
     * strip rather than fixed because a compare chart's band is as tall as it has players and an
     * ordinary one is a single line: a constant would either clip the third rival's row or darken a
     * fifth of the picture on a solo export.
     */
    fun bandHeight(contentBottom: Float, top: Float = TOP): Float = maxOf(contentBottom + top, 0f)

    /** How wide the dots and counts of [partWidths] are together, or 0 for a chip with no awards. */
    fun awardsWidth(partWidths: List<Float>): Float {
        if (partWidths.isEmpty()) return 0f
        return partWidths.sumOf { (DOT_R * 2f + DOT_GAP + it).toDouble() }.toFloat() +
            PART_GAP * (partWidths.size - 1)
    }

    /**
     * The colour one chip is drawn in: dim label and bright value for an ordinary counter, and the
     * player's own chart colour - the accent for the main player, their slot in the rival palette
     * for a rival - for the per-player lines of a compare chart.
     *
     * Which is the whole point of a compare view: "this line is that colour, find it on the chart".
     * The live row ([com.twinlore.app.journey.ui.JourneyPlayer]'s `PlayerCompareRow`) has always
     * coloured its chips this way; the exported strip drew every one of them in the same grey, so a
     * shared video or image of a head-to-head journey carried a colour-coded chart over a legend
     * that told you nothing about who was who.
     */
    private fun playerColor(chip: JourneyStripText.Chip, style: ChartStyle): Color? = when {
        chip.rivalIndex == null -> null
        chip.rivalIndex < 0 -> style.accent
        else -> style.colorForRivalIndex(chip.rivalIndex)
    }

    /**
     * Whether these counters are a compare chart's players rather than one journey's facts.
     *
     * [JourneyStripText.Chip.rivalIndex] is set only on the per-player lines of a compare chart
     * (-1 for the main player, 0-based for a rival), which is exactly the distinction the plate
     * needs: players stack, facts share a baseline.
     */
    fun isCompare(chips: List<JourneyStripText.Chip>): Boolean = chips.any { it.rivalIndex != null }

    /**
     * How many rows a compare plate stacks: one per chip that actually names a player, and none
     * for anything else.
     *
     * Every chip on a compare plate names a player today, so this is `chips.size` on every list the
     * app builds - and it is still a count of players rather than of chips, because [isCompare] only
     * asks whether ANY chip carries one. A plate whose first chip was the date once drew that date
     * as a fake third player (see [drawComparePlate]'s own doc); this is the guard that stopped it,
     * and the cost of keeping it honest is one predicate.
     *
     * Pure and exposed on its own so that fact is testable without a [DrawScope] - a bitmap
     * cannot show that a count came out right by accident.
     */
    fun compareRowCount(chips: List<JourneyStripText.Chip>): Int = chips.count { it.rivalIndex != null }

    /**
     * The two columns a compare row draws in: [nameWidth] and [factsWidth] - the widest name and
     * the widest facts run across the rows - shrunk together, and only together, when [budget]
     * cannot hold both.
     *
     * Shrinking one alone would fix the overflow and break the one thing shared columns exist for:
     * every row's facts would stop starting at the same x as every other row's, since "smaller"
     * would mean a different amount depending on which column happened to be the long one. Scaling
     * both by the same factor keeps the table whatever the frame leaves for it.
     *
     * The gap between them is taken off the budget before the scale rather than scaled with it, and
     * it is taken from [chipContentWidth] - at the compare row's own [ROW_ITEM_GAP] - rather than
     * restated, so the function that measures the row's content width and the one that sizes its
     * columns cannot come to disagree about what a gap costs. Not charging for it at all is
     * precisely what made a bare row measure one gap more than its budget, which is where the
     * dropped date came from.
     */
    fun compareColumns(nameWidth: Float, factsWidth: Float, budget: Float): Pair<Float, Float> {
        if (budget <= 0f) return 0f to 0f
        val wanted = chipContentWidth(nameWidth, factsWidth, gap = ROW_ITEM_GAP)
        if (wanted <= budget) return nameWidth to factsWidth
        val gap = wanted - nameWidth - factsWidth
        val room = maxOf(budget - gap, 0f)
        val scale = room / (nameWidth + factsWidth)
        return (nameWidth * scale) to (factsWidth * scale)
    }

    /**
     * Everything a compare plate's rows are drawn from, worked out from what its text measures:
     * the two shared columns, each row's own content width, and the plate that wraps them.
     *
     * Pure, and separate from the drawing, for the same reason the rest of the arithmetic in this
     * file is: a row that runs off the right of the frame is invisible in a rendered bitmap and
     * obvious in a test. [ExportStripTest]'s own horizontal guard derives its positions from this
     * and from [chipSegments], the way [drawComparePlate] does.
     *
     * The budget is spent in this order, which is the order of how much each part is worth: the
     * award dots and the rank are facts about the player, so their widest row is reserved for
     * first; the columns are then sized into whatever is left, which is what the row is mostly
     * about. Everything together is held inside the room the plate has after its padding and its
     * avatar, which is what makes the drawn row end at or before the plate's inner right edge by
     * construction.
     */
    data class CompareLayout(
        val nameCol: Float,
        val factsCol: Float,
        /** Each row's content, index for index with the rows: the two columns and its own dots. */
        val rowContents: List<Float>,
        /**
         * The room held clear at the plate's inner right edge for the rank, on every row - the
         * widest rank across them, or 0 when no player on this chart has one.
         *
         * A column rather than each row's own width, even though the rank is drawn right-aligned
         * and so does not need one: the reserve is what stops a row's award dots from reaching into
         * the space another row's rank is drawn in. Sizing it per row would let a row with a short
         * rank ("Rank #4") license dots that then collide with the "Rank #1204" on the row below.
         */
        val rankCol: Float,
        /** The plate the rows are drawn on, never wider than the frame allows. */
        val width: Float,
    )

    /** Where a compare row's content starts: after the plate's padding and its leading avatar. */
    fun compareContentLeft(left: Float): Float = left + PAD_H + ROW_AVATAR + ROW_ITEM_GAP

    /** The edge nothing drawn on a [left]-anchored, [width]-wide plate may pass. */
    fun plateInnerRight(left: Float, width: Float): Float = left + width - PAD_H

    /**
     * Where a [rankWidth]-wide rank starts, given the plate's own [innerRight] - flush against it,
     * the way the screen's rank owns the end of its line (`JourneyPlayer.PlayerCompareLine`).
     *
     * Right-aligned rather than laid after the awards for the reason the single-game plate's own
     * bar is: what precedes it varies in width every reported step, and a rank that slid sideways
     * frame to frame would be the noisiest thing on the picture. The screen gets the same result
     * from the awards taking a weighted middle; an export has no layout pass to lean on, so it
     * measures the reserve ([CompareLayout.rankCol]) and anchors the draw.
     */
    fun compareRankLeft(innerRight: Float, rankWidth: Float): Float = innerRight - rankWidth

    fun compareLayout(
        frameWidth: Float,
        left: Float,
        /** What each row's name measures, unconstrained by the columns. */
        nameWidths: List<Float>,
        /** The same for each row's facts run. */
        factsWidths: List<Float>,
        /** And its award dots, which are drawn at their own measured width rather than in a column. */
        awardWidths: List<Float>,
        /** What each row's rank measures, 0 for a player whose rank nobody knows. */
        rankWidths: List<Float> = emptyList(),
    ): CompareLayout {
        val maxPlate = frameWidth - left * 2f
        // The room the columns actually have: the plate's own padding and the avatar (with its own
        // gap) taken off first, so they are sized against what is genuinely left for them rather
        // than against the frame's raw width.
        val columnBudget = maxOf(maxPlate - PAD_H * 2f - ROW_AVATAR - ROW_ITEM_GAP, 0f)
        val nameWant = nameWidths.maxOrNull() ?: 0f
        val factsWant = factsWidths.maxOrNull() ?: 0f
        val widestAwards = awardWidths.maxOrNull() ?: 0f
        val rankCol = rankWidths.maxOrNull() ?: 0f
        val awardsReserve = if (widestAwards > 0f) ROW_ITEM_GAP + widestAwards else 0f
        val rankReserve = if (rankCol > 0f) ROW_ITEM_GAP + rankCol else 0f
        val budget = columnBudget - awardsReserve - rankReserve
        val (nameCol, factsCol) = compareColumns(nameWant, factsWant, maxOf(budget, 0f))
        val rowContents = awardWidths.map { chipContentWidth(nameCol, factsCol, it, gap = ROW_ITEM_GAP) }
        // The shared row width every row draws at, so the names line up under one another and the
        // plate reads as a table rather than as a ragged stack: the widest row's own content, plus
        // the rank's column, which is added rather than folded into either because it is held clear
        // on EVERY row.
        val content = (rowContents.maxOrNull() ?: 0f) + rankReserve
        // Every row carries the same leading avatar, so it costs ROW_AVATAR and its own gap on
        // every row rather than only on the widest - which is exactly what plateWidth's iconWidth
        // charges, at the compare row's own gap rather than the ordinary plate's icon gap. The
        // clamp is the last word, for a frame too narrow to hold even the avatar.
        val width =
            minOf(plateWidth(content, iconWidth = ROW_AVATAR, iconGap = ROW_ITEM_GAP), maxPlate)
        return CompareLayout(nameCol, factsCol, rowContents, rankCol, width)
    }

    /**
     * Draws [chips] as ONE plate across the top of a [frameWidth]-wide frame: the game's icon, then
     * the counters on a shared baseline with a dim dot between them.
     *
     * This is the shape the watcher saw. On screen the same counters sit on a single panel
     * (`JourneyPlayer`'s `EndPanel`, `PlaybackHeader` for a running journey, and `GameHeaderBar` -
     * itself a `PlaybackHeader` - for a single game); the export used to draw each of them as a
     * rounded box of its own, laid left to right and wrapped over two rows, so a shared video or
     * picture arrived looking like a different app from the one that made it.
     *
     * No panel is painted behind them any more, for the reason [drawComparePlate]'s own doc gives
     * for its rows: `headerPanelColor` returns `Color.Transparent` on screen, so the backdrop art
     * shows straight through every one of these panels, not only the compare row's. This one used
     * to paint an opaque `style.background` plus a `style.panel` tint - the chart's own tooltip is
     * painted exactly that way, which is where the pairing came from - but a tooltip is a popover
     * the watcher summoned by holding a finger on the chart; this bar is furniture drawn over the
     * picture on every single frame, and the screen deliberately gives it none. "Plate" stays as
     * the name for the LAYOUT this function measures and positions the icon, the facts and the
     * trailing block against (see [plateWidth], [plateHeight]); it is no longer a shape drawn on
     * the canvas. What IS drawn on the canvas behind the counters is the band's shared gradient
     * scrim ([drawBandScrim]), which is the other half of the same screen change: the readouts gave
     * up their panels to one scrim behind the whole band, and the export had taken only the first
     * half of that.
     *
     * ONE baseline or a STACK, from the chips themselves ([lineCount]). A finished span and a single
     * game are one line of counters; a running span is the screen's two-line header - the game's
     * numbered title over the date, the counts and the award dots - and stacking it is not a second
     * layout but the same one run per line. For a single line the arithmetic reduces to exactly what
     * it always did (the line is the block [lineTops] centers, so every run lands where it landed
     * before), which is why the stack is a generalisation here rather than a fork alongside.
     */
    fun DrawScope.drawExportStrip(
        chips: List<JourneyStripText.Chip>,
        style: ChartStyle,
        tm: TextMeasurer,
        frameWidth: Float,
        /**
         * Every bitmap the chart already decoded, keyed by [JourneyUrls.media] or
         * [JourneyUrls.userPic] - a compare plate's own rows read a player's avatar out of this,
         * the same map the running icon below comes from, so drawing one costs no fetch.
         *
         * Required, with no `emptyMap()` default: this file draws in the background, on nothing
         * but what it is handed, so a caller that forgets this parameter is a caller that forgot
         * the map exists - not a caller that meant "no avatars", which a silently-accepted empty
         * default would have looked exactly like. The one production caller (`ExportFrame`)
         * already has the map in scope two lines below this call, for the running game's icon.
         */
        bitmaps: Map<String, ImageBitmap>,
        /** The game under the sweep, drawn at the head of the plate as the screen's header draws it. */
        icon: ImageBitmap? = null,
        /**
         * The ring around it, which the caller picks the way the screen does: the accent for a
         * single game (`GameHeaderBar`, so a "Your Color" override reaches the export too), that
         * game's own series colour on a span, where the ring changes with the picture behind it.
         * The accent when nothing says.
         */
        iconRing: Color? = null,
        /** The single game's progress bar, at the plate's far end; null on every other journey. */
        bar: BarState? = null,
        left: Float = LEFT,
        top: Float = TOP,
        /**
         * Whether [chips] is a finished span's counters (`JourneyStripText.endChips`) rather than
         * a running span's or a single game's - the one ordinary-plate shape that mirrors a real
         * screen widget one chip at a time (`JourneyPlayer.EndPanel`), so it is the one that gets
         * [END_LABEL_SIZE]/[END_VALUE_SIZE] instead of [LABEL_SIZE]/[VALUE_SIZE]. The caller is
         * the one place that knows which of `exportChips`'s branches built this list -
         * `ExportFrame.drawStrip` sets it from exactly the condition `endChips` is returned under
         * (a span, landed, with nobody else on the chart) - this file cannot recover that from the
         * chips themselves: a finished span's `Chip`s carry no [Chip.isTitle] and no
         * [Chip.rivalIndex] any more than a running span's or a single game's do, so there is no
         * mark on the data itself to branch on the way [isCompare] branches on [Chip.rivalIndex].
         */
        finishedSpan: Boolean = false,
    ) {
        if (chips.isEmpty()) return
        // A compare chart is one row per player, the way the live compare bar is. Everything below
        // lays counters on one baseline, which for a scoreboard reads as a sentence about a single
        // journey rather than as a comparison.
        if (isCompare(chips)) {
            drawComparePlate(chips, style, tm, frameWidth, bitmaps, left, top)
            return
        }
        // Which of the three ordinary-plate shapes this is, and therefore what type it wears. See
        // each constant's own doc: the finished span mirrors `EndPanel` chip for chip, the running
        // span is `PlaybackHeader`'s two-line header, and the single game is the one shape left
        // with no screen counterpart to trace a label/value split to.
        val stacked = lineCount(chips) > 1
        val labelSize = when {
            stacked -> SPAN_FACT_SIZE
            finishedSpan -> END_LABEL_SIZE
            else -> LABEL_SIZE
        }
        val valueSize = when {
            stacked -> SPAN_FACT_SIZE
            finishedSpan -> END_VALUE_SIZE
            else -> VALUE_SIZE
        }
        // Each award kind's count, measured like any other value, so the dots are laid out from the
        // same arithmetic the rest of the plate is rather than from a guessed width. `EndPanel`'s
        // own AwardDots call hands its count text `titleMedium` - the same style as its value, not
        // its label - so a finished span's count takes valueSize here too; a running span's takes
        // the `labelSmall` its header's own dots draw at, and a single game's stays on the untraced
        // LABEL_SIZE it always drew at.
        val awardSize = when {
            stacked -> SPAN_AWARD_SIZE
            finishedSpan -> valueSize
            else -> LABEL_SIZE
        }
        // The dim dot between one counter and the next. `EndPanel` draws its own at `labelLarge` -
        // the label's own style, not the value's - so a finished span's sep takes labelSize; a
        // single game's facts are one fused string with no separator glyph of their own on screen
        // at all (see [LABEL_SIZE]'s doc), so it keeps drawing this one at the old VALUE_SIZE
        // rather than a size traced to a glyph the screen never draws. A stacked header draws one
        // chip per line and so never draws a separator at all; it is measured anyway rather than
        // branched around, because the TextMeasurer's cache makes it free after the first frame.
        val sepSize = if (finishedSpan) labelSize else VALUE_SIZE
        // The room the facts have, worked out BEFORE the text is measured, because a run that has
        // its line to itself is measured against it (see [MAX_TEXT_WIDTH] and [soleOnLine] below).
        // Nothing here reads a label or a value, so hoisting it costs nothing: the icon is the
        // caller's, and the percentage beside the bar is the bar's own number.
        val iconWidth = if (icon != null) ICON else 0f
        val percent = bar?.let { measure(tm, "${it.percent}%", VALUE_SIZE, FontWeight.Bold, style) }
        val barRow = if (bar == null) 0f else barRowWidth()
        val readout = if (percent == null) 0f else percent.size.width.toFloat()
        val trailing = if (bar == null) 0f else maxOf(barRow, readout)
        val maxPlate = frameWidth - left * 2f
        val budget = factsBudget(maxPlate, iconWidth, trailing)
        val labels = chips.map { measure(tm, it.label, labelSize, FontWeight.Normal, style) }
        // A header's title chip is titleSmall on screen, not the bodySmall/titleMedium a fact
        // chip's value is - see [TITLE_SIZE]'s own doc. `isTitle` only appears on a header's own
        // title chip (`gameHeaderChips`, `spanHeaderChips`), never on a finished span's, so this
        // cannot collide with `valueSize`'s own finishedSpan branch above.
        val values = chips.indices.map { i ->
            val chip = chips[i]
            // A chip with its line to itself is measured against the plate's own room, not against
            // [MAX_TEXT_WIDTH], which is a bound for counters SHARING a baseline with three others.
            // A stacked header's second line is one fused run by design - it is what the screen
            // draws - and a single game's is the longest of them ("50 of 69 achievements · 342 of
            // 656 points · Beaten in 17h 48m"), so the flat ceiling ellipsized it at "Beaten ..."
            // with a third of the plate still empty beside it.
            val room = if (soleOnLine(chips, i)) budget else MAX_TEXT_WIDTH
            measure(tm, chip.value, if (chip.isTitle) TITLE_SIZE else valueSize, FontWeight.Bold, style, room)
        }
        val awards = chips.map { chip ->
            chip.awards.map {
                measure(tm, awardText(it, chip.awardCountsShown), awardSize, FontWeight.Bold, style)
            }
        }
        val sep = measure(tm, SEP, sepSize, FontWeight.Normal, style)
        val contents = chips.indices.map {
            val labelW = labels[it].size.width.toFloat()
            val valueW = values[it].size.width.toFloat()
            val awardsW = awardsWidth(awards[it].map { part -> part.size.width.toFloat() })
            chipContentWidth(
                labelWidth = labelW,
                valueWidth = valueW,
                // Plus the "·" that leads a run of dots sharing the chip with a fact - the same
                // lead [drawChipRun] draws, reserved here so the two cannot disagree.
                awardsWidth = awardsW + awardsSepLead(
                    hasRunBefore = labelW > 0f || valueW > 0f,
                    awardsW = awardsW, sepW = sep.size.width.toFloat(), gap = LABEL_GAP,
                ),
                // The room a header's fact claims whatever it currently reads. This is what stops
                // the line - and the plate, and the bar at its end - moving as a counter's digits
                // change. See [JourneyStripText.Chip.slotDp].
                slot = slotWidth(chips[it]),
            )
        }

        // The chips of each line, in the order they were built. One group for every plate but the
        // running span's header, where it is the title's line and the facts'.
        val rows = (0 until lineCount(chips)).map { line -> chips.indices.filter { chips[it].line == line } }
        val sepW = sep.size.width.toFloat()
        // Never wider than the frame allows, and never fuller than the plate is wide. A custom
        // range's date pair is most of a thousand pixels on its own; left alone, the facts ran
        // under the trailing block (bar and percentage drawn straight over the last counter's
        // text) and then off the right edge onto bare backdrop, which is a picture somebody has
        // already shared by the time anyone notices. So the facts are cut to the room the frame
        // actually leaves them, from the back: the plate's chips are ordered with the ones that
        // say the most at the front, so the tail is the right thing to lose.
        //
        // Per LINE, and each line against the same budget: every line of a stacked header starts at
        // the same x behind the same icon, so what one of them can afford is what any of them can.
        // [maxPlate] and [budget] are measured above, where the text that has to fit inside them is.
        val shownPerRow = rows.map { row -> chipsThatFit(row.map { contents[it] }, sepW, budget) }
        val dropped = rows.indices.sumOf { rows[it].size - shownPerRow[it] }
        if (dropped > 0) {
            Log.w(
                TAG,
                "drawExportStrip: dropped $dropped of ${chips.size} counter(s) - a " +
                    "${maxPlate}px plate leaves ${budget}px for facts that measure " +
                    "${rows.indices.map { factsWidth(rows[it].map { i -> contents[i] }, sepW) }}px",
            )
        }
        val rowWidths = rows.indices.map { factsWidth(rows[it].take(shownPerRow[it]).map { i -> contents[i] }, sepW) }
        // The widest line is what the plate has to hold: the lines stack, so they share one width
        // the way a compare plate's rows do rather than each claiming their own.
        val facts = rowWidths.maxOrNull() ?: 0f
        // Inside maxPlate by construction now ([factsBudget] is [plateWidth]'s own inverse); the
        // clamp stays as the last word, for a frame so narrow that the icon and the trailing block
        // would not fit it even with no facts at all.
        val width = minOf(plateWidth(facts, iconWidth, trailing), maxPlate)
        // How tall each line is: the tallest thing ACTUALLY DRAWN on it - its labels, its values and
        // its award counts alike - not the tallest of its values.
        //
        // The distinction did not exist while every plate was one line, because a line was then the
        // whole plate and `plateHeight` padded it to at least [ICON]; a label was centered in that
        // and could not escape it however tall it measured. A stacked plate centers each run in its
        // OWN line ([lineTops]), so a line sized off one of the three runs on it and drawn with all
        // three is a run hanging out of the top of its line by half the difference - which on the
        // first line is a run hanging above the plate, and above the frame. Every shape the app
        // builds today happens to put its largest type on the value ([TITLE_SIZE] over
        // [SPAN_FACT_SIZE], [END_VALUE_SIZE] over [END_LABEL_SIZE], [VALUE_SIZE] over
        // [LABEL_SIZE]), so the two answers agree on every one of them - which is exactly why this
        // is worth writing down rather than leaving to hold by luck: the first retune that made a
        // label the loud half of a counter, or a font whose label metrics beat its value metrics,
        // would have drawn it off the top of a shared picture with nothing failing.
        val lineHeights = rows.indices.map { line ->
            rows[line].flatMap { i ->
                listOf(labels[i].size.height, values[i].size.height) + awards[i].map { it.size.height }
            }.maxOrNull()?.toFloat() ?: 0f
        }
        val height = plateHeight(lineHeights)

        // The band's scrim first, so everything below is drawn on it rather than under it. Same
        // scrim for every mode, because on screen it is one band that all of them share.
        drawBandScrim(style, frameWidth, bandHeight(top + height, top))

        val contentLeft = left + PAD_H + if (icon != null) ICON + ICON_GAP else 0f
        if (icon != null) {
            drawPlateIcon(icon, iconRing ?: style.accent, left + PAD_H, top + (height - ICON) / 2f)
        }
        val tops = lineTops(top, height, lineHeights)
        rows.indices.forEach { line ->
            val lineTop = tops[line]
            val lineHeight = lineHeights[line]
            var cursor = contentLeft
            rows[line].take(shownPerRow[line]).forEachIndexed { position, i ->
                if (position > 0) {
                    cursor += SEP_GAP
                    // The dot itself is skipped between slotted facts - see [dotBetween]. The
                    // cursor advances the same either way, because the plate reserved the same room
                    // either way.
                    if (dotBetween(chips[i], chips[rows[line][position - 1]])) {
                        drawText(
                            textLayoutResult = sep,
                            color = style.inkDim,
                            topLeft = Offset(cursor, lineTop + (lineHeight - sep.size.height) / 2f),
                        )
                    }
                    cursor += sep.size.width + SEP_GAP
                }
                cursor = drawChipRun(
                    chip = chips[i],
                    label = labels[i],
                    value = values[i],
                    awards = awards[i],
                    style = style,
                    left = cursor,
                    // Centred in its own line, the way the label and the award dots beside it
                    // already were (`drawChipRun` centres both in the height it is handed). The
                    // values used to be top-aligned to the tallest run on the plate instead, so on
                    // a single game's plate its `titleSmall` title sat level with the top of the
                    // smaller facts beside it rather than level with their middle - which is not
                    // what the screen's own `verticalAlignment = CenterVertically` row does.
                    baseline = lineTop + (lineHeight - values[i].size.height) / 2f,
                    plateTop = lineTop,
                    plateH = lineHeight,
                    // The screen colours a header's title with the icon's own ring - the accent for
                    // a single game (a "Your Color" override included), the game's series colour on
                    // a span, where it changes with the picture behind it. See `Chip.isTitle`.
                    // The screen's header title inherits the strip ink now, not the ring colour.
                    titleColor = style.ink,
                    awardEmPx = scaledTextSize(awardSize, style),
                    awardsSep = sep,
                )
            }
        }
        if (bar != null && percent != null) {
            // Right-aligned against the plate's own edge rather than laid after the facts: the
            // facts line varies in width every reported step, and a bar that slid sideways with it
            // would be the only thing on the frame that moved without meaning anything.
            val blockLeft = left + width - PAD_H - trailing
            // The block's own height, from its real parts rather than a fraction of the plate's -
            // see [centeredBlockTop] for why that used to be able to clip. The bar's row is as tall
            // as the award hanging off it, which is taller than the bar: sized off BAR_H alone, the
            // circle would hang out of the block and, on a short plate, out of the frame.
            val barRowH = barRowHeight()
            val blockH = barRowH + BAR_PERCENT_GAP + percent.size.height
            val blockTop = centeredBlockTop(top, height, blockH)
            // The bar row and the percentage under it are centred on each other, the way the
            // screen's header centres its own column. The row is the wider of the two for every
            // real game, so this normally just draws it flush; the arithmetic is written for either
            // being wider so that a font whose percentage measures past the row cannot push the
            // number out from under the bar it belongs to.
            val barLeft = blockLeft + (trailing - barRow) / 2f
            val barCenterY = blockTop + barRowH / 2f
            drawPlateBar(bar, style, barLeft, barCenterY)
            // The award as the chart's own dot and nothing more, at the END of the bar and centred
            // on it - `AchievementsBoard`'s pairing, which the screen's header now draws too. See
            // [BarState.award] and [barRowWidth].
            bar.award?.let { kind ->
                drawAwardDot(kind, style, Offset(barLeft + BAR_W + BAR_AWARD_GAP + DOT_R, barCenterY))
            }
            drawText(
                textLayoutResult = percent,
                // The theme's own ink, like every other value on the plate (user request): the
                // percentage is a fact, and it was the one fact wearing the player's colour. The
                // screen's header made the same move - see `GameHeaderBar`'s percent Text.
                color = style.ink,
                topLeft = Offset(
                    blockLeft + (trailing - readout) / 2f,
                    blockTop + barRowH + BAR_PERCENT_GAP,
                ),
            )
        }
    }

    /**
     * One row per player, stacked: their picture on a disc of their own colour, their name in
     * that same colour, their facts beside it, their awards after those - the same line the live
     * compare row draws, in the same order (`JourneyPlayer`'s `PlayerCompareLine`). Which is the
     * whole point of a compare view: "this line is that colour, find it on the chart" - and,
     * riding the right edge of the first (main player's) row rather than a row of its own, the one
     * non-player fact a compare plate still needs: the date.
     *
     * No plate, and no plate PER ROW. The live row sits on nothing - `headerPanelColor` returns
     * `Color.Transparent`, so the backdrop art shows straight through it - and this used to paint
     * an opaque `background` plus a `panel` tint under every row, which is exactly the flat box the
     * screen deliberately does not have.
     *
     * What the screen does have, and what this draws instead, is one gradient scrim behind the
     * whole BAND (see [drawBandScrim]): the legibility work is done once, for everything in the top
     * band together, and it fades out rather than ending on an edge. Per-row plates and a shared
     * scrim are not two spellings of the same thing - one is a row of widgets parked over the
     * picture, the other is the picture darkening under its own furniture.
     *
     * Only a chip carrying a [JourneyStripText.Chip.rivalIndex] is ever stacked as a row -
     * belt-and-braces on top of [isCompare]'s own check, which only asks whether ANY chip in the
     * list has one. `comparePlaybackChips` builds exactly one chip without one, the date, on
     * purpose (see its own doc): a solo running export's strip still carries a Date chip, and a
     * compare export silently carrying no date at all was an inconsistency, not a design. But the
     * date is a fact about the FRAME, not about a player, so it must not occupy a player row -
     * that was tried first (a bare `rivalIndex`-less chip mixed in with the rows) and it read as
     * a fake third player, bold name-sized label over dim value-sized fact, on every frame of a
     * compare video but the last one. It cannot live on the chart's own corner readout either.
     * That readout is drawn only while genuinely zoomed in (`JourneyChartRenderer`'s `drawJourney`
     * gates it on `!view.isOverview`), which a fitted export never is - and a FOLLOWED export now
     * genuinely is, since the camera zooms in on the runner (`ExportFrame` passes
     * `showZoomDate = view != null` for exactly that reason). It still cannot carry this date: the
     * readout answers with a calendar date off the axis, and `zoomDateLabel` returns null outright
     * on a race chart, which every single-game compare chart is. So the plate is the only place
     * left for it, and it goes on the first row rather than a new one - zero extra vertical cost,
     * which keeps the rows inside the frame itself (see [ExportStripTest]'s `compare rows stay
     * inside the frame at the rival cap`; the older bound to the plot area was retired with the
     * opaque plate that needed it).
     *
     * If a non-player chip cannot fit the first row's own width, it is dropped rather than drawn
     * overlapping the player it shares a row with - loud on a debug build (see [Log.w] below)
     * rather than a silently wrong frame. That has to stay a genuine last resort: it used to fire
     * on EVERY frame of every compare export, from a column budget that could not hold a bare row
     * (see [CompareLayout] and the columns' own note above), which is a few hundred log lines per
     * export on the encode thread and a date that never once got drawn.
     *
     * The name and facts are drawn at shared column offsets ([compareLayout]) rather than through
     * [drawChipRun]'s own [chipSegments] over their measured widths - which is exactly right for
     * the ordinary/single-game plate's ragged counters, and exactly wrong for a table, where a
     * short name must not pull the score after it left. `drawChipRun` is still what draws the date
     * riding the first row's edge, since that one is not a column.
     */
    private fun DrawScope.drawComparePlate(
        chips: List<JourneyStripText.Chip>,
        style: ChartStyle,
        tm: TextMeasurer,
        frameWidth: Float,
        bitmaps: Map<String, ImageBitmap>,
        left: Float,
        top: Float,
    ) {
        val players = chips.filter { it.rivalIndex != null }

        val maxPlate = frameWidth - left * 2f
        // The columns are measured rather than assumed - see the note on [compareColumns] and the
        // one above [compareLayout] for what the assumed ones cost. Measured against the room the
        // plate has for them rather than this file's general per-text ceiling: on a compare row
        // that room IS the ceiling, and a facts run cut at 900px inside a column the frame could
        // have made 1300 is a row ellipsized for nothing.
        val columnRoom = maxOf(maxPlate - PAD_H * 2f - ROW_AVATAR - ROW_ITEM_GAP, 0f)
        val rawLabels = players.map { measure(tm, it.label, ROW_NAME_SIZE, FontWeight.Bold, style, columnRoom) }
        // Bold, like the name beside it: on screen the name and the facts are the same `labelMedium`
        // in the same weight, and only their COLOUR tells them apart (the player's own for the name,
        // the ordinary ink for the facts). See `JourneyPlayer.PlayerCompareLine`.
        val rawValues = players.map { measure(tm, it.value, ROW_FACT_SIZE, FontWeight.Bold, style, columnRoom) }
        // The rank, at its own smaller size, measured per row and never squeezed into a column: it
        // is a handful of characters and it owns the end of the line. See [compareRankLeft].
        val ranks = players.map { chip ->
            chip.rank?.let { measure(tm, it, ROW_RANK_SIZE, FontWeight.Normal, style) }
        }
        // The completion percent beside its bar, on a single-game race's rows (owner request) -
        // the same number the live row shows, off the same chip field. Measured here so the
        // trailing reserve below can hold room for the whole block.
        val percents = players.map { chip ->
            chip.percent?.let { measure(tm, "$it%", ROW_RANK_SIZE, FontWeight.Bold, style) }
        }
        // Only the rank claims the right edge now: it lands at the end of the race without
        // moving anything, because everything else on the row sits in FIXED columns (owner
        // request - the right-anchored bar block used to shift left the frame the rank arrived).
        val rankWidths = ranks.map { it?.size?.width?.toFloat() ?: 0f }
        // The bar and its percent live in fixed columns straight after the facts - bar, then
        // percent, then the dots - the order and the stability the live row has: the percent
        // column is sized for "100%" whatever this frame's numbers say, so nothing shifts as
        // they grow.
        val hasBars = percents.any { it != null }
        val pctCol = if (hasBars) measure(tm, "100%", ROW_RANK_SIZE, FontWeight.Bold, style).size.width.toFloat() else 0f
        // The award dot rides the end of the bar, exactly as the solo plate's does (owner
        // request) - its slot is reserved for every row whether or not that player has an award
        // yet, so the percent column cannot jump when one lands mid-race.
        val barDotSlot = if (hasBars) BAR_AWARD_GAP + DOT_R * 2f else 0f
        val barPctExtra = if (hasBars) ROW_ITEM_GAP + ROW_BAR_W + barDotSlot + BAR_PERCENT_GAP + pctCol else 0f
        val awards = players.map { chip ->
            chip.awards.map {
                measure(tm, awardText(it, chip.awardCountsShown), ROW_FACT_SIZE, FontWeight.Bold, style)
            }
        }
        // The "·" leading each row's dots, the same mark the live row draws before its own (owner
        // request); a SPAN row's facts always precede its dots, so the lead applies whenever dots
        // exist there. A row with a bar carries its award ON the bar instead - no middle dots, no
        // lead - which is the live row's own split.
        val rowSep = measure(tm, SEP, ROW_FACT_SIZE, FontWeight.Normal, style)
        val awardSepLeads = awards.mapIndexed { i, row ->
            if (percents[i] != null) 0f else awardsSepLead(
                hasRunBefore = true,
                awardsW = awardsWidth(row.map { it.size.width.toFloat() }),
                sepW = rowSep.size.width.toFloat(),
                gap = ROW_ITEM_GAP,
            )
        }
        val awardWidths = awards.mapIndexed { i, row ->
            if (percents[i] != null) 0f
            else awardsWidth(row.map { it.size.width.toFloat() }) + awardSepLeads[i]
        }


        // One arithmetic for the whole plate, so what is drawn below cannot be laid out to widths
        // the plate was never measured at. See [compareLayout].
        val geometry = compareLayout(
            frameWidth = frameWidth,
            left = left,
            nameWidths = rawLabels.map { it.size.width.toFloat() },
            // A FIXED facts column - the live row's own 240dp through DENSITY - plus the bar and
            // percent columns riding inside it: sized to the widest realistic run rather than to
            // this frame's text, so the columns after it hold still while the counts grow (owner
            // request; measuring per frame walked the bar rightward through the whole replay).
            factsWidths = players.map { ROW_FACTS_COL + barPctExtra },
            awardWidths = awardWidths,
            rankWidths = rankWidths,
        )
        val nameCol = geometry.nameCol
        val factsCol = geometry.factsCol
        val width = geometry.width
        // Measured again only where a column came out narrower than the text wants, so what is
        // drawn ellipsizes to the column it is actually drawn in. The common case reuses the
        // measurement the columns were sized from rather than paying for a second one.
        val labels = players.indices.map {
            fitted(tm, players[it].label, ROW_NAME_SIZE, FontWeight.Bold, style, rawLabels[it], nameCol)
        }
        // The text's own portion of the facts column - the bar and percent columns ride at its
        // far end, so the run must ellipsize before it reaches them.
        val factsTextCol = factsCol - barPctExtra
        val values = players.indices.map {
            fitted(tm, players[it].value, ROW_FACT_SIZE, FontWeight.Bold, style, rawValues[it], factsTextCol)
        }
        val tops = compareRowTops(compareRowCount(chips), top)
        val contentLeft = compareContentLeft(left)
        val innerRight = plateInnerRight(left, width)

        // The band's scrim, under everything the rows draw and over whatever the chart put here.
        // See [drawBandScrim]; the rows themselves stay bare, exactly as the screen's do.
        drawBandScrim(style, frameWidth, bandHeight((tops.lastOrNull() ?: top) + ROW_H, top))

        players.indices.forEach { i ->
            val rowTop = tops[i]
            val chip = players[i]
            // The disc first, the picture over it: the media host is case sensitive
            // (`JourneyUrls.userPic` interpolates the name exactly as typed), so a row whose
            // picture 404s still shows the player's own colour instead of a hole - the same
            // reason the live row's own `Box` is drawn in that colour before its `AsyncImage`
            // (`JourneyPlayer.PlayerCompareLine`).
            val discColor = playerColor(chip, style) ?: style.inkDim
            val avatarCenter = Offset(left + PAD_H + ROW_AVATAR / 2f, rowTop + ROW_H / 2f)
            drawAvatarDisc(bitmaps[JourneyUrls.userPic(chip.label)], discColor, avatarCenter, ROW_AVATAR / 2f)

            // Shared offsets from the two columns, not from what this row's own name and facts
            // measure - chipSegments still does the "which gaps are actually owed" arithmetic,
            // just over column widths instead of measured ones. See this function's own doc.
            val name = labels[i]
            val facts = values[i]
            val rowAwards = awards[i]
            val (nameAt, factsAt, awardsAt) =
                chipSegments(nameCol, factsCol, awardWidths[i], gap = ROW_ITEM_GAP)
            nameAt?.let {
                drawText(
                    textLayoutResult = name,
                    color = discColor,
                    topLeft = Offset(contentLeft + it, rowTop + (ROW_H - name.size.height) / 2f),
                )
            }
            factsAt?.let {
                drawText(
                    textLayoutResult = facts,
                    // The ordinary bright ink, which is `onSurface` - what the screen's own facts
                    // are drawn in. They used to take the dim ink, which was the right pairing for
                    // a chip whose label is a name and whose value is a secondary fact, and the
                    // wrong one for a row where the dim ink is spent on the rank instead.
                    color = style.ink,
                    topLeft = Offset(contentLeft + it, rowTop + (ROW_H - facts.size.height) / 2f),
                )
                // The bar, its award and its percent in their fixed columns at the far end of
                // the facts column - the live row's own order, and positions that hold still
                // from the first frame to the landed one (owner request; they used to be
                // right-anchored, and the rank's arrival at the end shoved them left). The award
                // rides the bar's end exactly as the solo plate's does; its slot is held whether
                // or not one has landed.
                percents[i]?.let { pct ->
                    val barLeft = contentLeft + it + factsTextCol + ROW_ITEM_GAP
                    drawCompletionBar(
                        chip.percent ?: 0, chip.hardcorePercent, style,
                        left = barLeft,
                        centerY = rowTop + ROW_H / 2f,
                        width = ROW_BAR_W,
                        height = ROW_BAR_H,
                    )
                    chip.awards.firstOrNull()?.let { part ->
                        drawAwardDot(
                            part.kind, style,
                            Offset(barLeft + ROW_BAR_W + BAR_AWARD_GAP + DOT_R, rowTop + ROW_H / 2f),
                        )
                    }
                    drawText(
                        textLayoutResult = pct,
                        color = style.ink,
                        topLeft = Offset(
                            barLeft + ROW_BAR_W + barDotSlot + BAR_PERCENT_GAP,
                            rowTop + (ROW_H - pct.size.height) / 2f,
                        ),
                    )
                }
            }
            awardsAt?.let {
                if (awardSepLeads[i] > 0f) {
                    drawText(
                        textLayoutResult = rowSep,
                        color = style.inkDim,
                        topLeft = Offset(contentLeft + it, rowTop + (ROW_H - rowSep.size.height) / 2f),
                    )
                }
                drawAwards(
                    chip.awards, rowAwards, style, contentLeft + it + awardSepLeads[i], rowTop, ROW_H,
                    textEmPx = scaledTextSize(ROW_FACT_SIZE, style),
                )
            }
            // Flush against the plate's inner edge, in the dim ink - `onSurfaceVariant` on screen.
            // The reserve that keeps this clear of the dots above is [CompareLayout.rankCol].
            // Alone at the edge: it lands at the end of the race without moving the fixed
            // columns to its left.
            ranks[i]?.let { rank ->
                drawText(
                    textLayoutResult = rank,
                    color = style.inkDim,
                    topLeft = Offset(
                        compareRankLeft(innerRight, rank.size.width.toFloat()),
                        rowTop + (ROW_H - rank.size.height) / 2f,
                    ),
                )
            }
        }
    }

    /**
     * The scrim behind the whole top band: the theme's background at [SCRIM_ALPHA] under the
     * frame's top edge, fading to nothing by [height].
     *
     * The screen's own, in frame pixels. There the player's top band carries
     * `Brush.verticalGradient(background.copy(alpha = 0.88f) to Transparent)` across its full width,
     * and every readout on it - the back arrow, the counters, a compare chart's player rows - sits
     * on that one scrim with no panel of its own. The export drew the readouts and none of the
     * scrim, so a shared picture put the same text over undimmed art.
     *
     * A GRADIENT, and this is the part that matters: an opaque plate behind the band is precisely
     * what an earlier change removed (see [drawComparePlate] and [drawExportStrip]), because it
     * read as a widget parked over the picture. Fading to transparent has no edge to read as one -
     * the picture is darkest where the text is and untouched a row below it.
     *
     * Full frame width, like the screen's `fillMaxWidth` band, rather than the plate's width: the
     * plate is a layout, not a shape, and a scrim that stopped at the last counter would put a
     * vertical seam down the middle of the frame.
     */
    private fun DrawScope.drawBandScrim(style: ChartStyle, frameWidth: Float, height: Float) {
        if (height <= 0f || frameWidth <= 0f) return
        drawRect(
            // The same three stops the live band wears (owner request: stronger, for readability
            // over bright art): near-opaque where the readouts sit, falling off below them.
            brush = Brush.verticalGradient(
                0f to style.background.copy(alpha = SCRIM_ALPHA),
                0.55f to style.background.copy(alpha = 0.78f),
                1f to Color.Transparent,
                startY = 0f,
                endY = height,
            ),
            topLeft = Offset.Zero,
            size = Size(frameWidth, height),
        )
    }

    /**
     * The game's icon at the head of the plate, inside the ring the screen's header draws around
     * it (see `JourneyPlayer`'s `PlaybackHeader`).
     */
    private fun DrawScope.drawPlateIcon(icon: ImageBitmap, ring: Color, left: Float, top: Float) {
        val rect = Size(ICON, ICON)
        drawImage(
            image = icon,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(icon.width, icon.height),
            dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
            dstSize = IntSize(ICON.roundToInt(), ICON.roundToInt()),
        )
        drawRoundRect(
            color = ring,
            topLeft = Offset(left, top),
            size = rect,
            cornerRadius = CornerRadius(ICON_CORNER, ICON_CORNER),
            style = Stroke(2f),
        )
    }

    /**
     * A compare row's leading avatar: a disc in [color] - the player's own chart colour - with
     * [image] cropped into a circle over it, or nothing over it at all when [image] is null.
     *
     * The disc is always drawn, and drawn first, so a missing bitmap is that player's own colour
     * rather than a hole. That is not a hypothetical for this app: `JourneyUrls.userPic`
     * interpolates the name exactly as it was typed, into a media host path that is case
     * sensitive, so a name spelled differently from its UserPic entry 404s - the same failure
     * `JourneyChartRenderer`'s own avatars (the runner, a rival's end marker) are built around.
     * Unlike those, this draws no ring or letter fallback: the live row it is copying
     * (`JourneyPlayer.PlayerCompareLine`) has neither, only the coloured disc showing through
     * wherever the picture does not cover it.
     */
    private fun DrawScope.drawAvatarDisc(image: ImageBitmap?, color: Color, center: Offset, radius: Float) {
        drawCircle(color, radius, center)
        if (image == null || radius <= 0.5f) return
        val clip = Path().apply {
            addOval(Rect(center.x - radius, center.y - radius, center.x + radius, center.y + radius))
        }
        clipPath(clip) {
            drawImage(
                image = image,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(image.width, image.height),
                dstOffset = IntOffset((center.x - radius).roundToInt(), (center.y - radius).roundToInt()),
                dstSize = IntSize((radius * 2f).roundToInt(), (radius * 2f).roundToInt()),
            )
        }
    }

    /**
     * The bar, and the percentage under it.
     *
     * Silver for the whole revealed share and gold for the hardcore part of it, which is not this
     * file's rule: it is the achievement tab's dual bar (`RetroAchievementDrawer`,
     * `AchievementsBoard`) and the journey header's, so the same game reads the same way wherever
     * it is drawn. Flat colours rather than the screen's gradients - a 240px bar in a 1920px frame
     * is too small for a three-stop ramp to say anything, and the two metals have to stay
     * distinguishable at a glance in a shared picture.
     */
    private fun DrawScope.drawPlateBar(bar: BarState, style: ChartStyle, left: Float, centerY: Float) =
        drawCompletionBar(bar.percent, bar.hardcorePercent, style, left, centerY, BAR_W, BAR_H)

    /** [drawPlateBar]'s metals at any size: the compare rows draw the same bar smaller. */
    private fun DrawScope.drawCompletionBar(
        percent: Int,
        hardcorePercent: Int,
        style: ChartStyle,
        left: Float,
        centerY: Float,
        width: Float,
        height: Float,
    ) {
        val top = centerY - height / 2f
        val radius = CornerRadius(height / 2f, height / 2f)
        drawRoundRect(
            color = style.accent.copy(alpha = 0.22f),
            topLeft = Offset(left, top),
            size = Size(width, height),
            cornerRadius = radius,
        )
        val whole = width * (percent.coerceIn(0, 100) / 100f)
        if (whole > 0f) {
            drawRoundRect(
                color = style.silver,
                topLeft = Offset(left, top),
                size = Size(whole, height),
                cornerRadius = radius,
            )
        }
        val hardcore = width * (hardcorePercent.coerceIn(0, 100) / 100f)
        if (hardcore > 0f) {
            drawRoundRect(
                color = style.gold,
                topLeft = Offset(left, top),
                size = Size(hardcore, height),
                cornerRadius = radius,
            )
        }
    }

    /** One counter on the plate: its label dim, its value bright, its award dots after them. Returns
     * where the next one starts.
     *
     * A compare chart's chip is one player and its label is their name: drawn in their own chart
     * colour with the facts after it dim, exactly the way the live compare row is laid out. An
     * ordinary counter keeps the dim-label/bright-value pairing - except the one chip whose value
     * IS the game's title ([JourneyStripText.Chip.isTitle]), which takes [titleColor] instead of
     * the ordinary ink, the way `PlaybackHeader`'s own title does on screen.
     */
    private fun DrawScope.drawChipRun(
        chip: JourneyStripText.Chip,
        label: TextLayoutResult,
        value: TextLayoutResult,
        awards: List<TextLayoutResult>,
        style: ChartStyle,
        left: Float,
        baseline: Float,
        plateTop: Float,
        // Named plateH, not plateHeight: this object has a plateHeight() function, and a parameter
        // of that name would shadow it for the whole body.
        plateH: Float,
        /**
         * What a title chip's value is drawn in - the plate's own icon ring, which is what the
         * screen hands `PlaybackHeader` as the title's colour. The accent when a caller has no ring
         * of its own, which is every caller that draws no title (the compare plate's date chip).
         *
         * A parameter rather than a straight `style.accent` because the ring is not the accent on a
         * span: it is the series colour of the game under the sweep, so the title changes colour
         * with the art behind it. Hardcoding the accent here is what made the exported span header
         * the one place in the feature that could not do that.
         */
        titleColor: Color = style.ink,
        /** The em [awards] were drawn at, for the dot centring; see [awardDotCenterY]. */
        awardEmPx: Float = 0f,
        /** The measured "·" that leads a run of dots sharing the chip with a fact; see [awardsSepLead]. */
        awardsSep: TextLayoutResult? = null,
    ): Float {
        val player = playerColor(chip, style)
        val labelW = label.size.width.toFloat()
        val valueW = value.size.width.toFloat()
        val dotsW = awardsWidth(awards.map { it.size.width.toFloat() })
        val sepLead = awardsSepLead(
            hasRunBefore = labelW > 0f || valueW > 0f,
            awardsW = dotsW,
            sepW = awardsSep?.size?.width?.toFloat() ?: 0f,
            gap = LABEL_GAP,
        )
        val awardsW = dotsW + sepLead

        // Every segment is placed at the offset the plate's own measurement puts it at, rather
        // than at a cursor this loop keeps for itself: one arithmetic, so what is drawn and what
        // was reserved for it cannot drift apart. See [chipSegments] for the drift this replaces.
        val (labelAt, valueAt, awardsAt) = chipSegments(labelW, valueW, awardsW)
        labelAt?.let {
            drawText(
                textLayoutResult = label,
                color = player ?: style.inkDim,
                topLeft = Offset(left + it, plateTop + (plateH - label.size.height) / 2f),
            )
        }
        valueAt?.let {
            val valueColor = when {
                chip.isTitle -> titleColor
                player != null -> style.inkDim
                else -> style.ink
            }
            drawText(
                textLayoutResult = value,
                color = valueColor,
                topLeft = Offset(left + it, baseline),
            )
        }
        awardsAt?.let {
            if (sepLead > 0f && awardsSep != null) {
                drawText(
                    textLayoutResult = awardsSep,
                    color = style.inkDim,
                    topLeft = Offset(left + it, plateTop + (plateH - awardsSep.size.height) / 2f),
                )
            }
            drawAwards(chip.awards, awards, style, left + it + sepLead, plateTop, plateH, awardEmPx)
        }

        // Advanced by the chip's reserved width, not by what it drew: a run inside a slot is
        // drawn from the slot's own left edge and the next chip starts a whole slot along, however
        // short this one's text happens to be right now.
        return left + chipContentWidth(labelW, valueW, awardsW, slot = slotWidth(chip))
    }

    /**
     * One kind's count as the strip writes it beside its dot: "3", and not "3 mastered" - or
     * nothing at all when [showCount] is off.
     *
     * The dot already says which kind - the metal is the tier and the fill is hardcore
     * ([AwardGrading]), and the chart's own legend spells the pairing out - so the word beside it
     * was the same fact twice at the price of most of the plate's width. Which is exactly the call
     * every surface on screen makes: `JourneyPlayer`'s `EndPanel`, its compare row and its
     * playback header all draw the dot with no kind name. The words are still what a chip is
     * SPOKEN as ([JourneyStripText.Chip.spoken]), where width costs nothing.
     *
     * [showCount] comes off the chip ([JourneyStripText.Chip.awardCountsShown]) rather than a mode
     * this file would otherwise have to import: a single-game compare row's dot can only ever read
     * "1" - one award per kind per game - so [JourneyStripText.compareChips] switches it off before
     * this file ever sees the chip. An empty string rather than an omitted call, so the dot is
     * still measured and drawn the ordinary way and only the number beside it goes; see
     * [awardsWidth] for why that costs nothing beyond the dot and its own gap.
     */
    private fun awardText(part: JourneyStripText.AwardPart, showCount: Boolean): String =
        if (showCount) "${part.count}" else ""

    /**
     * One award's kind dot, as the chart's own medallion draws it: the metal and the fill through
     * [AwardGrading], the colour and the halo through [JourneyChartRenderer], so an exported frame
     * cannot grade an award differently from the medallion above it - filled for a mastery and for
     * beaten, hollow for a completion, gold for the first two and silver for the third.
     */
    private fun DrawScope.drawAwardDot(kind: AwardKind, style: ChartStyle, center: Offset) {
        val color = JourneyChartRenderer.medalColor(kind, style)
        // The same halo the medallion's own kind dot carries, so an award reads as the same mark
        // on the chart and in the counter beside it. See [JourneyChartRenderer.AWARD_GLOW_ALPHA].
        val glow = DOT_R * JourneyChartRenderer.AWARD_GLOW_SPAN
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(color.copy(alpha = JourneyChartRenderer.AWARD_GLOW_ALPHA), Color.Transparent),
                center = center,
                radius = glow,
            ),
            radius = glow,
            center = center,
        )
        if (AwardGrading.filledDot(kind)) {
            drawCircle(color, DOT_R, center)
        } else {
            drawCircle(color, DOT_R - DOT_RING / 2f, center, style = Stroke(DOT_RING))
        }
    }

    /**
     * The award breakdown of one chip: a kind dot and its count, left to right from [left].
     *
     * Every dot is [drawAwardDot]'s, which is also the one the single-game plate's own trailing
     * block draws, so a breakdown and a lone award cannot come to look like different marks.
     */
    private fun DrawScope.drawAwards(
        parts: List<JourneyStripText.AwardPart>,
        texts: List<TextLayoutResult>,
        style: ChartStyle,
        left: Float,
        top: Float,
        height: Float,
        /** The em [texts] were actually drawn at - the [scaledTextSize] result; see [awardDotCenterY]. */
        textEmPx: Float,
    ) {
        if (parts.isEmpty()) return
        var cursor = left
        parts.forEachIndexed { index, part ->
            val text = texts[index]
            val dotY = awardDotCenterY(
                top = top, height = height, textHeight = text.size.height,
                firstBaseline = text.firstBaseline, textEmPx = textEmPx,
                pixelFont = style.pixelFont,
            )
            drawAwardDot(part.kind, style, Offset(cursor + DOT_R, dotY))
            cursor += DOT_R * 2f + DOT_GAP
            drawText(
                textLayoutResult = text,
                color = style.ink,
                topLeft = Offset(cursor, top + (height - text.size.height) / 2f),
            )
            cursor += text.size.width + PART_GAP
        }
    }

    /**
     * Where an award dot's centre sits against the count drawn beside it. The ordinary fonts
     * straddle the middle of their own line box, so the row's centre IS the digits' centre.
     * Press Start 2P's digits do not: the font has no descenders and its ascent overshoots its
     * em, so the glyphs sit low in the box and a row-centred dot rode visibly high beside them
     * (owner report, two exported frames running). The pixel branch centres on the digits
     * themselves - the em square above the baseline - which is why it needs the text's own
     * baseline and its drawn em, not just the row.
     */
    internal fun awardDotCenterY(
        top: Float,
        height: Float,
        textHeight: Int,
        firstBaseline: Float,
        textEmPx: Float,
        pixelFont: Boolean,
    ): Float =
        if (!pixelFont) top + height / 2f
        else top + (height - textHeight) / 2f + firstBaseline - textEmPx / 2f

    private fun DrawScope.measure(
        tm: TextMeasurer,
        text: String,
        sizePx: Float,
        weight: FontWeight,
        chart: ChartStyle,
        // A compare row passes its own room here - the plate's whole column room while it is
        // working out what its columns should be, then the column itself for anything that has to
        // ellipsize into one (see [fitted]) - rather than this file's general ceiling, which is a
        // bound for the counters that share ONE baseline with three others.
        maxWidth: Float = MAX_TEXT_WIDTH,
    ): TextLayoutResult = tm.measure(
        text = AnnotatedString(text),
        style = measureStyle(sizePx, weight, chart),
        overflow = TextOverflow.Ellipsis,
        maxLines = 1,
        constraints = Constraints(maxWidth = maxWidth.roundToInt()),
    )

    /**
     * The style EVERY run this file measures is measured with - the one place a size and a weight
     * are turned into a `TextStyle`, and therefore the one place the frame's typeface is decided.
     *
     * Its own function, and internal rather than private, so that "the strip's text is always in
     * [ChartStyle.labelFont]" is a thing a test can assert directly rather than a property of a
     * `TextStyle` literal buried in a measuring call. A frame with two typefaces on it is always a
     * bug in this renderer: the chart's axis labels, its badge glyphs and the strip's counters are
     * one picture, and a run that quietly fell back to the platform's proportional default would
     * announce itself as somebody else's text sitting on top of ours.
     *
     * Carries no colour, on purpose - colour has no effect on layout, and baking it in would make
     * the same glyph in two colours two entries in the `TextMeasurer`'s cache. It is applied at
     * `drawText` time instead. (The chart's own `JourneyChartRenderer.measureStyle` is the same
     * seam for the same reason, and additionally memoizes; this one leans on the measurer's cache,
     * since the strip measures a couple of dozen runs a frame rather than four hundred.)
     */
    internal fun Density.measureStyle(sizePx: Float, weight: FontWeight, chart: ChartStyle): TextStyle =
        TextStyle(fontSize = scaledTextSize(sizePx, chart).toSp(), fontWeight = weight, fontFamily = chart.labelFont)

    /**
     * [measureStyle]'s size rule: the pixel font draws at the 0.7 scale every pixel-font style in
     * the rest of the app already gets from `buildTypography` - the strip's sizes are raw numbers,
     * so that scale never reached them, and full-size Press Start 2P glyphs overran the strip and
     * sat visibly off-balance against the award dots beside them (owner report, with an exported
     * frame). Applied at the ONE seam every measured run passes through, so no single counter can
     * be forgotten at full size; rounded whole for the same measurer-cache reason the chart's
     * tick sizes are whole ([JourneyChartRenderer.tickLabelSize]).
     */
    internal fun scaledTextSize(sizePx: Float, chart: ChartStyle): Float =
        if (chart.pixelFont) (sizePx * PIXEL_TEXT_SCALE).roundToInt().toFloat() else sizePx

    /** The scale `buildTypography` applies to every pixel-font style; see [scaledTextSize]. */
    private const val PIXEL_TEXT_SCALE = 0.7f

    /**
     * [natural] again, ellipsized to [column] - or [natural] itself when it already fits inside it.
     *
     * A compare row's columns are measured from the text ([compareLayout]), which means measuring
     * the text before there is a column to measure it against. Re-measuring only where a column
     * came out narrower than its text wants keeps the ordinary frame - where nothing is shrunk - at
     * one measurement per run, and the [TextMeasurer]'s cache is keyed on the constraints, so the
     * second call for a shrunk row is a cache hit after its first frame.
     */
    private fun DrawScope.fitted(
        tm: TextMeasurer,
        text: String,
        sizePx: Float,
        weight: FontWeight,
        chart: ChartStyle,
        natural: TextLayoutResult,
        column: Float,
    ): TextLayoutResult =
        if (natural.size.width <= column) natural else measure(tm, text, sizePx, weight, chart, column)

    /**
     * How much of the frame one counter SHARING a baseline may claim.
     *
     * Its job is to keep [chipsThatFit] from answering zero: a single run wide enough to blow the
     * whole budget would leave the plate with nothing drawn on it at all. Against three neighbours
     * that is the right bound, and a 1920-wide frame leaves well over it.
     *
     * It is the wrong bound for a run that HAS its line - a stacked header's title, or the fused
     * facts line under it - which is entitled to the plate's own room, and got ellipsized at
     * "Beaten ..." with a third of the plate empty beside it while this was applied flat. Those are
     * measured against the budget instead; see [soleOnLine].
     */
    private const val MAX_TEXT_WIDTH = 900f
}
