package com.twinlore.app.journey.export

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

/**
 * The video's art layer: the same dimmed, desaturated game art the screen shows behind the chart,
 * drawn into the exported frame under everything else.
 *
 * The exporter never fetches: art arrives as an already decoded `gameId -> ImageBitmap` map, and a
 * game that has none simply leaves that stretch of the video on the plain background. Crossfades
 * are deliberately not attempted here; the export cuts between games the way a slideshow does,
 * which is a v1 simplification against the screen's 800ms fades.
 *
 * Art is FITTED into its band, never cropped to fill it, which is the rule the screen follows
 * (`ContentScale.Fit`). [drawBackdropArt] takes a band rather than assuming the whole frame because
 * a compare export splits the frame between the two players' games - and because a band is what
 * makes the maths testable.
 */
object ExportBackdrop {

    /**
     * The fallback art alpha for a caller that names none. Production always passes the user's
     * Background dim setting through the export request; this default matches that setting's own
     * default (25% art alpha, a 75% dim) so a bare call and the shipped default agree.
     */
    const val ALPHA = 0.25f
    const val SATURATION = 0.6f

    private val filter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(SATURATION) })

    /** The Noir ambience's export: fully desaturated art, matching the live stage exactly. */
    private val monoFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

    private fun filterFor(monochrome: Boolean) = if (monochrome) monoFilter else filter

    /**
     * The destination rectangle a whole image lands in when it is FITTED to a [dstWidth] x
     * [dstHeight] band: scaled by whichever axis runs out first, centred, letterboxed on the other.
     *
     * The screen draws its backdrop with `ContentScale.Fit` (user request: fit the images, do not
     * stretch or crop them), and the export has to agree with it or the file a user shares is not
     * the picture they watched - a 4:3 title screen centre-cropped to 16:9 loses its top and its
     * bottom. Pure, so the maths is unit tested without a canvas (see `ExportBackdropTest`).
     */
    fun fitRect(srcWidth: Int, srcHeight: Int, dstWidth: Int, dstHeight: Int): IntRect {
        if (srcWidth <= 0 || srcHeight <= 0 || dstWidth <= 0 || dstHeight <= 0) {
            return IntRect(0, 0, 0, 0)
        }
        val scale = minOf(dstWidth.toFloat() / srcWidth, dstHeight.toFloat() / srcHeight)
        val width = (srcWidth * scale).roundToInt().coerceIn(1, dstWidth)
        val height = (srcHeight * scale).roundToInt().coerceIn(1, dstHeight)
        val left = (dstWidth - width) / 2
        val top = (dstHeight - height) / 2
        return IntRect(left, top, left + width, top + height)
    }

    /**
     * The source rectangle a centre crop reads from: the largest region of a [srcWidth] x
     * [srcHeight] image with the destination's aspect ratio, centred. Pure, so the crop maths is
     * unit tested without a canvas (see `ExportBackdropTest`).
     *
     * Kept for the letterbox's own sake - see [fitRect], which is what the backdrop actually uses.
     */
    fun cropRect(srcWidth: Int, srcHeight: Int, dstWidth: Int, dstHeight: Int): IntRect {
        if (srcWidth <= 0 || srcHeight <= 0 || dstWidth <= 0 || dstHeight <= 0) {
            return IntRect(0, 0, srcWidth.coerceAtLeast(0), srcHeight.coerceAtLeast(0))
        }
        val srcAspect = srcWidth.toFloat() / srcHeight
        val dstAspect = dstWidth.toFloat() / dstHeight
        return if (srcAspect > dstAspect) {
            // Source is wider than the target: keep the full height, trim both sides.
            val width = (srcHeight * dstAspect).roundToInt().coerceIn(1, srcWidth)
            val left = (srcWidth - width) / 2
            IntRect(left, 0, left + width, srcHeight)
        } else {
            val height = (srcWidth / dstAspect).roundToInt().coerceIn(1, srcHeight)
            val top = (srcHeight - height) / 2
            IntRect(0, top, srcWidth, top + height)
        }
    }

    /**
     * One game's art, already centre-cropped, scaled to the frame and colour-filtered.
     *
     * The scale is the expensive half and it was being redone per frame: on the mandated software
     * canvas that is a full 2-megapixel bilinear upscale plus a per-pixel colour-matrix pass,
     * identical for every one of the hundreds of consecutive frames the sweep spends on one game.
     * Only the *decode* was hoisted before this.
     *
     * Two slots, and the buffers are allocated once and redrawn in place.
     *
     * Neither detail is optional. `bgTrack` is per-unlock and chronological, so a player who was
     * switching between two games unlock by unlock makes the sweep alternate between them frame by
     * frame - which with one slot is a miss on every frame, and with a freshly allocated target per
     * miss is a 8 MB bitmap per frame: gigabytes of churn across one export, on a handheld. Two
     * slots cover the alternation, and reusing the buffer makes even a genuine miss cost the
     * resample alone. Slots are created on first use, so an export with no art allocates nothing.
     *
     * The redraw uses [BlendMode.Src] rather than clearing first: the art fills the whole buffer, so
     * replacing the destination outright is both correct (no blending with the previous game's
     * pixels through a transparent edge) and one pass instead of two.
     *
     * The alpha is deliberately NOT baked in. Blending once at draw time is what the per-frame path
     * did, and folding it into the cached copy as well would round the same pixel twice.
     */
    class ScaledArt(private val width: Int, private val height: Int) {

        private class Slot(width: Int, height: Int) {
            val bitmap: ImageBitmap = ImageBitmap(width, height)
            var gameId: Int? = null
        }

        private val slots = arrayOfNulls<Slot>(SLOTS)
        private var next = 0
        private val drawScope = CanvasDrawScope()

        fun forGame(gameId: Int?, source: ImageBitmap?, monochrome: Boolean = false): ImageBitmap? {
            if (gameId == null || source == null) return null
            slots.forEach { slot -> if (slot != null && slot.gameId == gameId) return slot.bitmap }
            val fit = fitRect(source.width, source.height, width, height)
            if (fit.width <= 0 || fit.height <= 0) return null

            val slot = slots[next] ?: Slot(width, height).also { slots[next] = it }
            next = (next + 1) % SLOTS
            // Cleared before it is claimed: a caller that saw the old game in this slot must not be
            // handed a half-redrawn buffer under its old id. Single-threaded by construction (one
            // export, one encode loop), so this only guards against the draw below throwing.
            slot.gameId = null
            drawScope.draw(
                density = Density(1f),
                layoutDirection = LayoutDirection.Ltr,
                canvas = Canvas(slot.bitmap),
                size = Size(width.toFloat(), height.toFloat()),
            ) {
                // The whole buffer is wiped first, which a centre crop did not need: a fitted image
                // leaves letterbox bars, and Src over only the fitted rect would show the previous
                // game's pixels in them.
                drawRect(
                    color = androidx.compose.ui.graphics.Color.Transparent,
                    size = Size(width.toFloat(), height.toFloat()),
                    blendMode = BlendMode.Src,
                )
                drawImage(
                    image = source,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(source.width, source.height),
                    dstOffset = IntOffset(fit.left, fit.top),
                    dstSize = IntSize(fit.width, fit.height),
                    // Constant per export (the ambience is snapshotted on the request), so the
                    // per-game slot cache never needs it in its key.
                    colorFilter = filterFor(monochrome),
                    blendMode = BlendMode.Src,
                )
            }
            slot.gameId = gameId
            return slot.bitmap
        }

        /**
         * Recycles the slots' native pixel memory immediately. The buffers are ~8 MB apiece and
         * native-backed, so waiting for a GC that feels no pressure from their tiny Java shells
         * leaves them parked long after the run that drew into them is gone. The next [forGame]
         * simply allocates fresh slots; a caller must never draw a bitmap it was handed before
         * release was called.
         */
        fun release() {
            slots.indices.forEach { i ->
                slots[i]?.bitmap?.asAndroidBitmap()?.recycle()
                slots[i] = null
            }
            next = 0
        }

        private companion object {
            /** Enough for the alternating case; a frame-sized ARGB buffer is ~8 MB apiece. */
            const val SLOTS = 2
        }
    }

    /** Blits an already-[ScaledArt]-sized copy, one to one, with only the dimming left to apply. */
    fun DrawScope.drawScaledBackdropArt(art: ImageBitmap, top: Int, alpha: Float = ALPHA) {
        drawImage(image = art, topLeft = Offset(0f, top.toFloat()), alpha = alpha)
    }

    /** Draws the whole of [art] fitted inside the [width] x [height] band starting at [top]. */
    fun DrawScope.drawBackdropArt(
        art: ImageBitmap?,
        top: Int,
        width: Int,
        height: Int,
        alpha: Float = ALPHA,
        monochrome: Boolean = false,
    ) {
        if (art == null || width <= 0 || height <= 0) return
        val fit = fitRect(art.width, art.height, width, height)
        if (fit.width <= 0 || fit.height <= 0) return
        drawImage(
            image = art,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(art.width, art.height),
            dstOffset = IntOffset(fit.left, top + fit.top),
            dstSize = IntSize(fit.width, fit.height),
            alpha = alpha,
            colorFilter = filterFor(monochrome),
        )
    }
}
