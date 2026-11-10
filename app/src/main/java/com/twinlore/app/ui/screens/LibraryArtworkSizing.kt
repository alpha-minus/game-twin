package com.twinlore.app.ui.screens

import android.util.Log
import androidx.compose.ui.unit.IntSize
import com.twinlore.app.data.library.ArtworkThumbnailCache
import kotlin.math.roundToInt

/**
 * Every horizontal gap in the library grid is this many dp: the grid's
 * contentPadding start/end, its horizontalArrangement spacing, and each tile's own
 * padding. Callers convert once at the current density and pass the px value to
 * [gridDecodeSize].
 */
const val GRID_SPACING_DP = 8

/**
 * Decode sizes are snapped up to a multiple of this. The grid measures its real
 * container while the cold-start preload derives the width from the window, and the
 * two can differ by a few pixels of insets and dp truncation. Since the size is baked
 * into Coil's memoryCacheKey, a mismatch sends the preload to a slot the grid never
 * reads and silently turns the whole preload into dead weight.
 *
 * This is a TOLERANCE, not a guarantee. A round-up bucket only makes two widths agree
 * when both land in the same 32-wide band, so the headroom depends on where the real
 * width sits inside its band. On the AYN Thor (1920 px, density 369) the 3-column art
 * width is 580, three above the 577 floor of bucket 608, leaving ~9 container px of
 * slack. Configurations that shrink `Configuration.screenWidthDp` further relative to
 * the window (a persistent system bar, a display cutout) can cross the boundary; that
 * is what [reportPreloadDecode] exists to surface. Compose 1.5 has no
 * `LocalWindowInfo.containerSize`, which would remove the dp-truncation half of the
 * drift outright, so this stays a tolerance until the Compose BOM moves.
 *
 * 32 rather than 64: the bucket only has to absorb small measurement noise, and 64
 * would push the 3-column case from 580 to 640, 23% more pixels decoded and held in
 * memory for nothing.
 */
private const val BUCKET_PX = 32

/**
 * Last decode size the cold-start preload used, so the grid can notice when its own
 * measurement disagrees. Purely diagnostic: the consequence of a disagreement is a
 * wasted preload and a slower cold start, never a wrong image, so this logs rather
 * than corrects. Without it the failure is completely invisible.
 */
@Volatile
private var lastPreloadDecode: IntSize? = null

/** Called by the cold-start preload with the size it keyed its requests on. */
fun reportPreloadDecode(size: IntSize) {
    lastPreloadDecode = size
}

/**
 * Called by the grid with the size it actually measured. Logs when the two differ,
 * which means every preloaded bitmap went to a Coil slot no tile will read.
 */
fun checkPreloadDecodeAgreement(gridSize: IntSize) {
    val preload = lastPreloadDecode ?: return
    if (preload != gridSize) {
        Log.w(
            "LibraryArtworkSizing",
            "preload/grid decode mismatch: preload=$preload grid=$gridSize — " +
                "cold-start preload wrote Coil slots the grid will not read"
        )
    }
}

/**
 * LIST mode (1 column) spans the window, but its decode width is capped at the thumbnail
 * width on purpose: buildGridArtRequest serves the 768-px thumb only for requests that fit
 * inside it, and the 960 this used to ask for pushed LIST off the thumb path onto full
 * multi-MB source decodes during scroll — the first drag through a cold list stuttered row
 * by row until Coil's memory cache was warm. A 768 thumb drawn into the wider row is a
 * ~1.25× upsample of a 3:1 crop, invisible next to that stutter.
 */
private const val LIST_WIDTH_PX = ArtworkThumbnailCache.TARGET_WIDTH_PX
private const val LIST_MIN_HEIGHT_PX = 160
private const val LIST_MAX_HEIGHT_PX = 1280

/**
 * Coil decode size for one library grid tile.
 *
 * Before this existed the grid decoded every non-LIST tile at a fixed 256x341,
 * which on a 3-column grid (the default view mode) meant a ~2.25x upsample and
 * visibly soft cover art. Deriving from the measured container fixes the large
 * modes and, as a bonus, makes the 9-column mode cheaper than it used to be.
 *
 * @param containerWidthPx width of the grid's own container, from BoxWithConstraints
 * @param cols column count; 1 means LIST mode
 * @param aspectRatio tile width/height, from the user's GridDisplayRatio setting
 * @param spacingPx [GRID_SPACING_DP] converted at the current density
 */
fun gridDecodeSize(
    containerWidthPx: Int,
    cols: Int,
    aspectRatio: Float,
    spacingPx: Int,
): IntSize {
    val safeRatio = if (aspectRatio > 0f) aspectRatio else 0.75f
    if (cols <= 1) {
        val height = (LIST_WIDTH_PX / safeRatio).roundToInt()
            .coerceIn(LIST_MIN_HEIGHT_PX, LIST_MAX_HEIGHT_PX)
        return IntSize(LIST_WIDTH_PX, height)
    }

    // contentPadding on both edges, then (cols - 1) inter-item gaps, then the tile's
    // own padding on both sides of the artwork.
    val usable = containerWidthPx - 2 * spacingPx - (cols - 1) * spacingPx
    val artPx = usable / cols - 2 * spacingPx

    val bucketed = ((artPx + BUCKET_PX - 1) / BUCKET_PX) * BUCKET_PX
    val width = bucketed.coerceIn(BUCKET_PX, ArtworkThumbnailCache.TARGET_WIDTH_PX)
    val height = (width / safeRatio).roundToInt().coerceAtLeast(1)
    return IntSize(width, height)
}
