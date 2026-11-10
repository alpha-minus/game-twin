package com.twinlore.app.journey.ui.chart

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import com.twinlore.app.journey.domain.model.AchievementType

/**
 * What a badge draws for its achievement type - the one table both surfaces that mark a type read
 * from: the journey chart's badges ([JourneyChartRenderer]'s `drawOneNode`) and the settings-sheet
 * legend (`StampSwatch` in `JourneyLegend`). Neither draws its own copy of this decision, so a
 * type's mark can only ever be changed here, in one place, for both.
 *
 * All three types once stamped the vault tab's own Material icon (`AchievementTypeIcon` in
 * `RetroAchievementDrawer`, the vault's achievement list, still does - unaffected by any of this,
 * since it draws at a much larger size). The user saw all three on the *chart*, at the chart's
 * small chip, and asked for two back: missable and win condition stamp a single character instead
 * ("!" and "★" - the chart's own marks before it briefly matched the vault tab exactly).
 * Progression kept the icon; it was the one the user judged still legible that small. That is a
 * deliberate, permanent split, not a pending cleanup - the chart and the vault tab's own list are
 * allowed to disagree about which alphabet reads best at their very different sizes, as long as the
 * chart and the *legend that explains the chart* never disagree with each other.
 *
 * [AchievementType.STANDARD] has no mark and never had one - nothing about an ordinary achievement
 * needs calling out on a chart full of them.
 */
internal object ChartTypeIcons {

    /**
     * One type's mark: either a single glyph or the vault tab's own Material icon. `null` (never a
     * case of this type - see [markFor]) means the type is unmarked.
     */
    sealed interface TypeMark {
        /**
         * A single character, drawn in the caller's own text style - its size, its colour, its
         * weight - but always in [GLYPH_FONT] rather than in the font that style would otherwise
         * carry. See there for why.
         */
        data class Glyph(val text: String) : TypeMark

        /** The vault tab's own Material icon, drawn from its path data with no Compose available. */
        data class Icon(val vector: ImageVector) : TypeMark
    }

    /**
     * The font a [TypeMark.Glyph] is drawn in, on the chart and in the legend alike.
     *
     * Deliberately NOT the theme's own label font, which is what both surfaces used to pass. Under
     * the pixel theme the stamps came out too big for their chip and sitting high in it, and the
     * reason is in Press Start 2P's metrics rather than in either drawing site: its caps fill the
     * whole em box (cap height 1000 of 1000 units, no descent at all), while a stamp's size and its
     * centring are both derived from an ordinary font's proportions - the sizes are multiples of
     * the chip's radius on the chart (`r * 1.2f`, `chipR * 1.5f` in `JourneyChartRenderer`) and a
     * flat 13sp in the legend's swatch (`STAMP_GLYPH_SIZE`), all picked against those, and
     * `JourneyChartRenderer.glyphTopLeft`
     * centres the ink band it assumes sits `CAP_HEIGHT` (0.7 of the set size) above the baseline.
     * A glyph 1.0 em tall in a slot tuned for 0.7 overflows the chip and lands about 0.15 of its
     * own size above centre. Chasing that with a per-font correction would mean measuring real cap
     * height on every mark; the user's call was simpler and is what this pins - a stamp does not
     * have to be in the pixel font, since it is a mark rather than text - so it is drawn in the
     * platform default under every theme, which is exactly what the non-pixel themes were already
     * showing and what these sizes were tuned against.
     *
     * [TypeMark.Icon] needs no equivalent: an [ImageVector] carries its own path data and never
     * resolves through a font at all.
     *
     * Read by one thing that is not a stamp: the letter a rival's avatar draws when it has no
     * picture, which is sized off its disc's radius the same way and broke the same way. Anything
     * else drawn INSIDE a shape, at a size derived from that shape, belongs here too.
     */
    val GLYPH_FONT: FontFamily = FontFamily.Default

    /**
     * [type]'s mark. Switch on the returned [TypeMark] rather than asking "is this an icon" or
     * "is this a glyph" up front - a caller that special-cased one variant and fell through to the
     * other for everything else is exactly how the chart and the legend drifted apart before this
     * table existed.
     */
    fun markFor(type: AchievementType): TypeMark? = when (type) {
        AchievementType.MISSABLE -> TypeMark.Glyph("!")
        AchievementType.WIN_CONDITION -> TypeMark.Glyph("★")
        AchievementType.PROGRESSION -> TypeMark.Icon(Icons.Default.TaskAlt)
        AchievementType.STANDARD -> null
    }
}
