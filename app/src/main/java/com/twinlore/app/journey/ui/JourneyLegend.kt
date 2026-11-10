package com.twinlore.app.journey.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.twinlore.app.journey.domain.model.AchievementType
import com.twinlore.app.journey.domain.model.AwardKind
import com.twinlore.app.journey.ui.chart.ChartStyle
import com.twinlore.app.journey.ui.chart.ChartTypeIcons
import com.twinlore.app.journey.ui.chart.FLAME_INNER
import com.twinlore.app.journey.ui.chart.FLAME_OUTER
import com.twinlore.app.journey.ui.chart.JourneyChartRenderer

/**
 * What the chart's marks mean, in the settings sheet the player already opens.
 *
 * Every one of them - the ring on a hardcore badge, the gold and silver kind dots, the corner dot
 * that says whose badge it is, the burning streak line, the three type stamps - had to be learned by
 * opening a tooltip or inferred from context; there was no key anywhere in the feature. This is
 * that key, and it draws its swatches from the same palette and the same geometry the chart does,
 * so a legend cannot drift into explaining marks the chart has stopped making.
 *
 * Two columns, because the rows stacked would push the speed and follow controls off a
 * handheld's screen. One merged semantics node: a screen reader gets the whole key as one passage
 * rather than sixteen unattached fragments, and the swatches themselves say nothing out loud.
 *
 * [compare] is whether anybody else is on this chart, and it is the reason the key takes an
 * argument at all: one of its rows describes a mark the renderer draws only when somebody is (see
 * [legendEntries]).
 */
@Composable
internal fun JourneyLegend(compare: Boolean, modifier: Modifier = Modifier) {
    val style = rememberChartStyle()
    val entries = legendEntries(compare)
    Column(
        modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = LEGEND_TITLE + ". " + entries.joinToString(". ") { it.text }
            },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            LEGEND_TITLE,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        entries.chunked(2).forEach { pair ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                pair.forEach { entry ->
                    LegendRow(entry, style, Modifier.weight(1f))
                }
                // Keeps a lone last entry in its own column rather than letting it stretch across
                // both, so the two columns line up all the way down.
                if (pair.size == 1) Box(Modifier.weight(1f))
            }
        }
    }
}

/** The heading, and the first thing the merged description says. */
internal const val LEGEND_TITLE = "What the chart shows"

/** One line of the key: the mark, and the fact it stands for. */
internal class LegendEntry(val mark: LegendMark, val text: String)

/** Which swatch a [LegendEntry] draws. */
internal enum class LegendMark {
    HARDCORE, MASTERED, COMPLETED, BEATEN, BEATEN_CASUAL, OWNER, STREAK, MISSABLE, PROGRESSION, WIN,
}

/**
 * The key itself, in the order it reads: what a badge's own ring says, what an award's dot says,
 * whose badge it is, the streak mark, then the three corner stamps.
 *
 * [compare] drops the owner row on a chart that has nobody to own anything. The renderer tints a
 * badge in its owner's colour only when a rival has unlocks ("a solo chart's badges all belong to
 * the one player"), and a solo journey is the overwhelmingly common case - the vault's Achievements
 * entry cannot even add a rival - so the key was sending most of its readers to look for a mark the
 * chart was explicitly suppressing. The three coloured dots the row draws are themselves a claim
 * that more than one player is present.
 *
 * Gold turns up in three of these rows and that is deliberate: `GOLD` and the win-condition chip
 * are the same hex, pinned by `ChartColorsTest`, and two of the three awards are drawn in it. What
 * tells them apart is what each row names - which metal, whether the dot has a middle, or a medal
 * stamped in a badge's corner - and the swatches draw those shapes rather than one gold dot three
 * times.
 *
 * Internal and separate from the drawing so the wording can be pinned by a test without a canvas.
 */
internal fun legendEntries(compare: Boolean): List<LegendEntry> = listOfNotNull(
    LegendEntry(LegendMark.HARDCORE, "Yellow ring: earned in hardcore"),
    LegendEntry(LegendMark.MASTERED, "Gold, filled dot: mastered"),
    LegendEntry(LegendMark.COMPLETED, "Gold, hollow dot: completed"),
    LegendEntry(LegendMark.BEATEN, "Silver, filled dot: beaten"),
    LegendEntry(LegendMark.BEATEN_CASUAL, "Silver, hollow dot: beaten (casual)"),
    LegendEntry(LegendMark.OWNER, "Badge tint: whose badge it is").takeIf { compare },
    LegendEntry(LegendMark.STREAK, "Orange line: three days or more in a row"),
    LegendEntry(LegendMark.MISSABLE, "Missable stamp"),
    LegendEntry(LegendMark.PROGRESSION, "Progression stamp"),
    LegendEntry(LegendMark.WIN, "Gold star stamp: win condition"),
)

@Composable
private fun LegendRow(entry: LegendEntry, style: ChartStyle, modifier: Modifier = Modifier) {
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(SWATCH), contentAlignment = Alignment.Center) {
            LegendSwatch(entry.mark, style)
        }
        Text(
            entry.text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LegendSwatch(mark: LegendMark, style: ChartStyle) = when (mark) {
    // A badge, as the chart draws one: the panel plate first, then the ring around it. Both go
    // through the renderer's own functions, so the swatch carries the contrast hairline that keeps
    // a light yellow legible on a light theme without this file having to know about it - and so it
    // stays a rounded square of the same proportions if the chart's icons ever change shape.
    //
    // The plate is not decoration. Stroking the ring on its own left the one swatch of the eight
    // with a hole in the middle, which reads as a different kind of mark rather than as the badge
    // every row above and below it is about.
    LegendMark.HARDCORE -> Canvas(Modifier.size(SWATCH)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val r = size.minDimension / 2f - 2f
        with(JourneyChartRenderer) {
            fillIconSquare(style.panel, center, r)
            drawHardcoreRing(center, r, 1f, style)
        }
    }
    // A medallion as the chart draws one, not a bare dot. The three awards are told apart by the
    // pair of colours and by whether the dot has a middle, so a swatch has to carry both marks or it
    // says nothing at all; each of these goes through the renderer's own ring and dot, so the key
    // cannot drift from the chart.
    LegendMark.MASTERED -> MedalSwatch(AwardKind.MASTERED, style)
    LegendMark.COMPLETED -> MedalSwatch(AwardKind.COMPLETED, style)
    LegendMark.BEATEN -> MedalSwatch(AwardKind.BEATEN, style)
    LegendMark.BEATEN_CASUAL -> MedalSwatch(AwardKind.BEATEN_CASUAL, style)
    // Three squares rather than one: the point of this mark is that the colour changes per player,
    // which a single swatch would say the opposite of. Squares because what carries the colour is
    // the badge itself, washed in its owner's tint, rather than a mark stuck on the corner of one.
    LegendMark.OWNER -> Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        TintChip(style.accent)
        style.rivalColors.take(2).forEach { TintChip(it) }
    }
    LegendMark.STREAK -> StreakSwatch()
    LegendMark.MISSABLE -> StampSwatch(AchievementType.MISSABLE, style)
    LegendMark.PROGRESSION -> StampSwatch(AchievementType.PROGRESSION, style)
    LegendMark.WIN -> StampSwatch(AchievementType.WIN_CONDITION, style)
}

/** One player's badge tint, as a plate of their colour: what a compare chart washes their icons in. */
@Composable
private fun TintChip(color: Color) {
    Box(Modifier.size(OWNER_TINT_CHIP).background(color))
}

/**
 * A medallion the size a settings sheet can read it at: the plate, the ring around it, and the kind
 * dot in the corner the chart hangs it on.
 *
 * Every mark goes through the renderer's own geometry - [JourneyChartRenderer.fillIconSquare],
 * [JourneyChartRenderer.strokeIconSquare], [JourneyChartRenderer.medalRingColor] and
 * [JourneyChartRenderer.drawKindDot] - so the swatch is the shape and the colours the chart's icons
 * are without this file knowing what either is. A hand-made copy is how a key comes to explain marks
 * the chart has stopped making.
 */
@Composable
private fun MedalSwatch(kind: AwardKind, style: ChartStyle) {
    Canvas(Modifier.size(SWATCH)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val r = size.minDimension / 2f - 2f
        with(JourneyChartRenderer) {
            fillIconSquare(style.panel, center, r)
            strokeIconSquare(medalRingColor(kind, style), center, r, MEDAL_SWATCH_RING)
            drawKindDot(kind, center, r, style)
        }
    }
}

/**
 * The corner chip a badge carries, at the size a settings sheet can read it at.
 *
 * Ringed in the panel colour like the chart's own, which is what keeps the gold medal a *stamp*
 * next to the plain gold dot four rows above it: the two facts share a hex, so the shape is the
 * only thing that tells them apart, and a swatch that dropped the ring was half of one.
 *
 * The mark itself comes from [ChartTypeIcons.markFor] - the one table the chart's own badges read
 * too - so the key cannot explain a shape the chart is not stamping. Progression's is the vault
 * tab's own icon; missable and win condition are single characters, because the icon is what the
 * chart drew for all three for one release and the user asked two of them back after seeing them
 * on a real chart (see [ChartTypeIcons]'s own doc). Whichever it is, this switches on the same
 * [ChartTypeIcons.TypeMark] the renderer does rather than assuming one shape and falling back to
 * the other - that assumption is exactly how this swatch and the chart drifted apart before.
 *
 * A glyph mark is drawn in [ChartTypeIcons.GLYPH_FONT] rather than in whatever the theme is
 * setting, for the reason given there - the same pin the chart's own stamps take.
 */
@Composable
private fun StampSwatch(type: AchievementType, style: ChartStyle) {
    val mark = ChartTypeIcons.markFor(type) ?: return
    Box(
        Modifier
            .size(STAMP)
            .clip(CircleShape)
            .background(style.typeColors[type] ?: style.inkDim)
            .border(STAMP_RING, style.panel, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        // Named by the row's own text, which the merged description already reads out; a second
        // voice here would say every stamp twice - true of either branch below.
        when (mark) {
            is ChartTypeIcons.TypeMark.Icon -> Icon(
                mark.vector,
                contentDescription = null,
                modifier = Modifier.size(STAMP_ICON),
                tint = style.panel,
            )
            is ChartTypeIcons.TypeMark.Glyph -> Text(
                mark.text,
                color = style.panel,
                fontFamily = ChartTypeIcons.GLYPH_FONT,
                fontWeight = FontWeight.Bold,
                fontSize = STAMP_GLYPH_SIZE,
            )
        }
    }
}

/**
 * The mark a streak actually makes: a stretch of the line burning in the flame's own two-tone
 * orange, standing on every run the reveal has passed. The swatch is a segment of that line, in
 * the same two colours and the same order the renderer strokes it with.
 */
@Composable
private fun StreakSwatch() {
    Canvas(Modifier.size(SWATCH)) {
        val y = size.height / 2f
        drawLine(
            brush = Brush.horizontalGradient(listOf(FLAME_OUTER, FLAME_INNER)),
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = size.height * 0.28f,
            cap = StrokeCap.Round,
        )
    }
}

private val SWATCH = 18.dp
private val OWNER_TINT_CHIP = 5.dp
private val STAMP = 18.dp

/**
 * How big an icon-shaped stamp's mark is: the same share of the chip the chart gives its own icon
 * mark (a box of about 1.15 chip radii), so the key shows the badge's corner at reading size rather
 * than a different composition of the same two shapes. [ChartTypeIcons.TypeMark.Icon] only - a
 * glyph-shaped stamp is sized by [STAMP_GLYPH_SIZE] instead, the way [Text] sizes its own content.
 */
private val STAMP_ICON = 10.dp

/**
 * How big a glyph-shaped stamp's character is drawn, in the swatch. Bold, and sized to sit
 * comfortably inside the [STAMP] circle's own ring - a font-size box, unlike [STAMP_ICON]'s, is not
 * the character's own bounding box, so this is picked smaller than the circle itself rather than
 * derived from it the way the icon box is.
 */
private val STAMP_GLYPH_SIZE = 13.sp

/** How thick a medallion swatch's ring is, in the swatch's own pixels. */
private const val MEDAL_SWATCH_RING = 2f

/** The panel-coloured hairline the chart strokes its corner chips with. */
private val STAMP_RING = 1.5.dp
