package com.twinlore.app.ui.screens

import android.graphics.Bitmap
import coil.size.Size
import coil.transform.Transformation

/**
 * Three-pass box blur, in place, on packed ARGB.
 *
 * Three box passes approximate a gaussian closely enough that the result is
 * indistinguishable at wallpaper scale, and a box blur is O(pixels) regardless of radius
 * because each step of the window is one add and one subtract. Pure Kotlin with no Android
 * types so the kernel is unit-testable on the JVM.
 *
 * Alpha is carried through from the source rather than blurred: the wallpaper is always
 * fully opaque, and blurring alpha would only round-trip the same value.
 */
fun blurArgb(pixels: IntArray, width: Int, height: Int, radius: Int) {
    if (radius <= 0 || width <= 0 || height <= 0) return
    require(pixels.size >= width * height) { "pixels too small for ${width}x$height" }
    val scratch = IntArray(pixels.size)
    repeat(3) {
        // Each pass writes its output transposed, so running it twice — the second time with
        // the dimensions swapped — blurs both axes and lands back in the original orientation.
        // One kernel, no separate vertical version.
        boxBlurTransposing(pixels, scratch, width, height, radius)
        boxBlurTransposing(scratch, pixels, height, width, radius)
    }
}

/**
 * One horizontal box pass over [src], written column-major into [dst]. [srcWidth] is the row
 * length of [src]; [dst] comes out with rows of length [srcHeight].
 */
private fun boxBlurTransposing(
    src: IntArray,
    dst: IntArray,
    srcWidth: Int,
    srcHeight: Int,
    radius: Int,
) {
    val window = radius * 2 + 1
    for (y in 0 until srcHeight) {
        val row = y * srcWidth
        var r = 0; var g = 0; var b = 0
        // Prime the window at x = 0, clamping at the edge so borders do not darken.
        for (i in -radius..radius) {
            val p = src[row + i.coerceIn(0, srcWidth - 1)]
            r += (p shr 16) and 0xFF; g += (p shr 8) and 0xFF; b += p and 0xFF
        }
        for (x in 0 until srcWidth) {
            val a = src[row + x].toLong() and 0xFF000000L
            val out = (a or
                (((r / window).toLong()) shl 16) or
                (((g / window).toLong()) shl 8) or
                ((b / window).toLong())).toInt()
            dst[x * srcHeight + y] = out
            val leaving = src[row + (x - radius).coerceIn(0, srcWidth - 1)]
            val entering = src[row + (x + radius + 1).coerceIn(0, srcWidth - 1)]
            r += ((entering shr 16) and 0xFF) - ((leaving shr 16) and 0xFF)
            g += ((entering shr 8) and 0xFF) - ((leaving shr 8) and 0xFF)
            b += (entering and 0xFF) - (leaving and 0xFF)
        }
    }
}

/**
 * Blurs the library wallpaper ONCE, at load time, instead of every frame.
 *
 * The wallpaper used to be a live `RenderEffect.createBlurEffect(200f, 200f)` over a
 * full-screen image, with a second one stacked during a cross-dissolve and a third behind the
 * top bar. Profiled 2026-08-08 on an AYN Thor: the dissolve's frames cost 15.1ms of CPU each
 * against 3.9ms for an ordinary animated frame, and removing the live blur alone was worth
 * 13 points of a core. The source request is already 270x480, so a radius-24 blur at that
 * size scales up to the same look.
 */
class WallpaperBlurTransformation(private val radius: Int = 24) : Transformation {

    override val cacheKey: String = "wallpaper-blur:$radius"

    // Coil disables hardware bitmaps for any request carrying a transformation, so `input` is
    // always software-backed and getPixels is safe here.
    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val w = input.width
        val h = input.height
        if (w <= 0 || h <= 0) return input
        val pixels = IntArray(w * h)
        input.getPixels(pixels, 0, w, 0, 0, w, h)
        blurArgb(pixels, w, h, radius)
        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    }

    override fun equals(other: Any?): Boolean =
        other is WallpaperBlurTransformation && other.radius == radius

    override fun hashCode(): Int = radius
}
