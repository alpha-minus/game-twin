package com.twinlore.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas

/**
 * Noir library mode: when the Noir app-icon style is the effective selection, every
 * library cover EXCEPT the focused one renders desaturated; the focused tile stays in
 * full colour. Saturation is animated so moving focus crossfades the outgoing tile to
 * grey and the incoming tile back to colour. The same animated saturation drives the
 * cover image ([colorFilterForSaturation]) and the per-tile achievement bar / award
 * circles ([Modifier.noirSaturation]), so a tile desaturates as one piece.
 *
 * Cost: a render-time [ColorFilter] on the already-decoded cover bitmap — no re-decode,
 * no extra Coil cache entry (the memoryCacheKey is untouched), no per-frame bitmap
 * allocation. A fully-coloured tile returns `null` (no filter at all); a settled grey
 * tile reuses one shared filter. Only a tile mid-crossfade builds a fresh matrix per
 * frame, and at most a couple of tiles crossfade at once.
 */
private const val NOIR_SATURATION_TWEEN_MS = 320

/** Shared instance for settled grey covers so the common case allocates nothing. */
private val FULL_GREY_FILTER: ColorFilter =
    ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

/**
 * The animated saturation (1 = full colour, 0 = grey) a library tile should render at.
 *
 * @param active Noir library mode is on.
 * @param inColor this tile is the focused one that should remain coloured.
 */
@Composable
fun rememberNoirSaturation(active: Boolean, inColor: Boolean): Float {
    val target = if (!active || inColor) 1f else 0f
    val saturation by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(NOIR_SATURATION_TWEEN_MS),
        label = "noirSaturation",
    )
    return saturation
}

/** [ColorFilter] for a cover image at [saturation], or `null` for full colour (cheapest path). */
fun colorFilterForSaturation(saturation: Float): ColorFilter? = when {
    saturation >= 0.999f -> null
    saturation <= 0.001f -> FULL_GREY_FILTER
    else -> ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(saturation) })
}

/** Convenience: the animated cover [ColorFilter] for a tile under Noir mode. */
@Composable
fun rememberNoirColorFilter(active: Boolean, inColor: Boolean): ColorFilter? =
    colorFilterForSaturation(rememberNoirSaturation(active, inColor))

/**
 * Desaturate an arbitrary composable subtree (brush fills, gradients, glows — anything
 * that isn't an image and so can't take a `colorFilter`) to [saturation]. Renders the
 * content into an offscreen layer with a saturation [ColorFilter]. No-op at full colour,
 * so coloured tiles pay nothing. Used for the achievement progress bar + award circles,
 * which are Compose-drawn gradients rather than bitmaps.
 */
fun Modifier.noirSaturation(saturation: Float): Modifier {
    if (saturation >= 0.999f) return this
    val paint = Paint().apply {
        colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(saturation) })
    }
    return this.drawWithContent {
        drawIntoCanvas { canvas ->
            canvas.saveLayer(Rect(Offset.Zero, size), paint)
            drawContent()
            canvas.restore()
        }
    }
}

/**
 * Muted saturation for the library blur backdrop under Noir mode: not fully grey, so the
 * scene keeps a deep colour wash / tone while the focused cover stays the only fully-saturated
 * element. Noir toggles only on an app-icon change (which restarts the app), so this is a
 * constant — no crossfade needed, and one shared filter instance is reused.
 */
private const val NOIR_BACKDROP_SATURATION = 0.35f

private val BACKDROP_WASH_FILTER: ColorFilter =
    ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(NOIR_BACKDROP_SATURATION) })

/** Filter for the library blur backdrop, or `null` when Noir mode is off (full colour). */
fun noirBackdropColorFilter(active: Boolean): ColorFilter? =
    if (active) BACKDROP_WASH_FILTER else null
