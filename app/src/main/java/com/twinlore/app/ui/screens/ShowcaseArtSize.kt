package com.twinlore.app.ui.screens

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/**
 * How much of its slot the showcase's cover art may take, in the limiting
 * dimension. The whole slot was a poster edge to edge below the bar; this
 * leaves it room to be a picture on a screen.
 */
internal const val SHOWCASE_ART_FILL = 0.7f

/**
 * How far past its own pixels the art may be stretched. Before any of this
 * the art drew at native size and was never upscaled, which looked fine and
 * merely small; a third to a half larger is what was asked for, and past that
 * a scraped 300px cover turns to mush. High-resolution art is never upscaled
 * at all: the slot cap wins long before this one does.
 */
internal const val SHOWCASE_ART_UPSCALE = 1.4f

/**
 * The size the showcase draws its cover art at.
 *
 * The largest rectangle of the art's [aspect] that fits inside
 * [SHOWCASE_ART_FILL] of the slot, then no larger than [SHOWCASE_ART_UPSCALE]
 * times the art's own pixels. An explicit size rather than fill-plus-aspect:
 * a fill fixes both dimensions and an aspect cannot be satisfied inside
 * that, so the node became the whole slot with the picture letterboxed in
 * it and the glow drawn around the slot.
 *
 * [nativePx] null (bounds decode failed) skips the resolution cap.
 * [aspect] null (no art at all) returns the slot cap unshaped, which only
 * matters for the placeholder text drawn in its place.
 */
internal fun showcaseArtSize(
    slotWidth: Dp,
    slotHeight: Dp,
    aspect: Float?,
    nativePx: IntSize?,
    density: Float,
): Pair<Dp, Dp> {
    var maxW = slotWidth * SHOWCASE_ART_FILL
    var maxH = slotHeight * SHOWCASE_ART_FILL
    if (nativePx != null && nativePx.width > 0 && nativePx.height > 0 && density > 0f) {
        maxW = minOf(maxW, (nativePx.width * SHOWCASE_ART_UPSCALE / density).dp)
        maxH = minOf(maxH, (nativePx.height * SHOWCASE_ART_UPSCALE / density).dp)
    }
    if (aspect == null || aspect <= 0f) return maxW to maxH
    var w = maxW
    var h = w / aspect
    if (h > maxH) {
        h = maxH
        w = h * aspect
    }
    return w to h
}
