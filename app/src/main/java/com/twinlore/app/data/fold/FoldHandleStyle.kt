package com.twinlore.app.data.fold

import kotlin.math.roundToInt

/**
 * The two numbers the folded handle is drawn from: how solid it is, and how
 * big.
 *
 * These were two enums, Visible / Faint / Transparent and Small / Medium /
 * Big, and the names were the argument for them: "Faint" said what it was
 * where 0.35 on a slider did not. What the names could not do is land between
 * two of themselves. Three opacities over a game whose brightness the app
 * does not control is a coarse instrument, and the same is true of three sizes
 * across the handhelds this runs on. Both are now continuous and snapped to a
 * grain, so the readout stays tidy without the value being one of three.
 *
 * The legacy mappings below are what an installed copy's stored enum name is
 * read as, once, until the slider writes a number over it. Dropping them would
 * quietly reset every existing user's handle to the default.
 */
object FoldHandleStyle {

    /**
     * A true zero is reachable on purpose. The handle is then invisible but
     * still occupies its window and still takes touches, so a tap where it was
     * left still unfolds, and both Settings surfaces are the way back.
     *
     * The default is fully visible. It used to be the old "Faint" 0.35, which
     * hid the one control that leads back out of Overlay Mode from anyone who
     * had not yet learned where it sits; fading it is a choice to make once
     * the handle is familiar, not the first impression.
     */
    const val MIN_ALPHA = 0f
    const val MAX_ALPHA = 1f
    const val DEFAULT_ALPHA = 1f

    /** Whole percents read as noise on a control this small. */
    private const val ALPHA_GRAIN = 0.05f

    /** The steps a d-pad walks the app's own slider row in. */
    const val ALPHA_STEP_COUNT = 20

    /**
     * 44dp is the platform's minimum touch target: the floor the handle may
     * shrink to, not a size it should arrive at. 96 is a third past what the
     * old "Big" gave, which is what a handheld held in two hands wants.
     */
    const val MIN_SIZE_DP = 44
    const val MAX_SIZE_DP = 96
    const val DEFAULT_SIZE_DP = 56

    /** A dp either way is invisible; two is the smallest step worth a redraw. */
    private const val SIZE_GRAIN_DP = 2

    const val SIZE_STEP_COUNT = (MAX_SIZE_DP - MIN_SIZE_DP) / SIZE_GRAIN_DP

    /**
     * How big the handle is drawn while the panel is open, as a fraction of
     * the size above.
     *
     * The handle has two audiences. Over a running game it competes with
     * whatever the game is drawing and has to be findable. Over the panel it
     * only has to be tappable: the surface under it is the app's own and calm,
     * and the thing it would otherwise obscure is the page somebody is
     * reading. How much that trade is worth depends on the guide, the screen
     * and the reader, which is why it is a slider rather than the 0.62 it was
     * hardcoded to.
     *
     * [DEFAULT_RECESS] is 1f: no shrink at all unless it is asked for. The
     * floor is a quarter rather than zero because this scales the mark and not
     * the window, so a handle drawn at 25% still has its whole touch target
     * and is simply hard to see.
     */
    const val MIN_RECESS = 0.25f
    const val MAX_RECESS = 1f
    const val DEFAULT_RECESS = 1f

    private const val RECESS_GRAIN = 0.05f

    const val RECESS_STEP_COUNT = 15

    /** The nearest opacity the slider may stop on, clamped to the range. */
    fun alpha(raw: Float): Float {
        if (raw.isNaN()) return DEFAULT_ALPHA
        val snapped = (raw / ALPHA_GRAIN).roundToInt() * ALPHA_GRAIN
        return snapped.coerceIn(MIN_ALPHA, MAX_ALPHA)
    }

    /** The nearest reading size the slider may stop on. */
    fun recess(raw: Float): Float {
        if (raw.isNaN()) return DEFAULT_RECESS
        val snapped = (raw / RECESS_GRAIN).roundToInt() * RECESS_GRAIN
        return snapped.coerceIn(MIN_RECESS, MAX_RECESS)
    }

    /**
     * The reading size as a 0..1 fraction of its own range, and back. The
     * app's settings rows are all normalised, so the mapping lives here rather
     * than inline in a composable.
     */
    fun recessFraction(scale: Float): Float =
        ((recess(scale) - MIN_RECESS) / (MAX_RECESS - MIN_RECESS)).coerceIn(0f, 1f)

    fun recessFromFraction(fraction: Float): Float =
        recess(MIN_RECESS + fraction.coerceIn(0f, 1f) * (MAX_RECESS - MIN_RECESS))

    fun recessPercentLabel(scale: Float): String = "${(recess(scale) * 100).roundToInt()}%"

    /** And the nearest size, in dp. */
    fun sizeDp(raw: Int): Int {
        val snapped = MIN_SIZE_DP +
            ((raw - MIN_SIZE_DP).toFloat() / SIZE_GRAIN_DP).roundToInt() * SIZE_GRAIN_DP
        return snapped.coerceIn(MIN_SIZE_DP, MAX_SIZE_DP)
    }

    /**
     * The size as a 0..1 fraction and back, for the app's own settings row:
     * [com.twinlore.app.ui.screens.SettingsScreen]'s slider rows are all
     * normalised, so the dp lives on this side of the boundary.
     */
    fun sizeFraction(dp: Int): Float =
        (dp - MIN_SIZE_DP).toFloat() / (MAX_SIZE_DP - MIN_SIZE_DP)

    fun sizeDpFromFraction(fraction: Float): Int =
        sizeDp(MIN_SIZE_DP + (fraction.coerceIn(0f, 1f) * (MAX_SIZE_DP - MIN_SIZE_DP)).roundToInt())

    fun alphaPercentLabel(alpha: Float): String = "${(alpha(alpha) * 100).roundToInt()}%"

    fun sizeLabel(dp: Int): String = "${sizeDp(dp)}dp"

    /**
     * What a stored `fold_dim` enum name meant, or null for a name that was
     * never one. Null is the signal to fall back to [DEFAULT_ALPHA] rather
     * than to a value some corrupt string happened to be nearest.
     */
    fun alphaFromLegacyName(name: String?): Float? = when (name) {
        "VISIBLE" -> 1f
        "FAINT" -> 0.35f
        "TRANSPARENT" -> 0f
        else -> null
    }

    /** The same for a stored `fold_tab_size`. */
    fun sizeDpFromLegacyName(name: String?): Int? = when (name) {
        "SMALL" -> 44
        "MEDIUM" -> 56
        "BIG" -> 72
        else -> null
    }
}
