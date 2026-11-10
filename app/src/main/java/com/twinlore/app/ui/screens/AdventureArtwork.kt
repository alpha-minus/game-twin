package com.twinlore.app.ui.screens

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import kotlin.math.PI
import kotlin.math.sin

/** Gold-icon signature gold (matches the splash logo's gold wreath / firefly motif). */
val AdventureGold = Color(0xFFF6C64B)

/** Adventure signature: a cool, bright silver sheen for the focus shimmer. */
val ShimmerSilver = Color(0xFFEAEEF5)

/**
 * Adventure library mode: a static warm "golden hour" grade on the blurred library
 * wallpaper, giving the Adventure premium style an amber cinematic ambiance — the
 * counterpart to Noir's cold monochrome wash ([noirBackdropColorFilter]).
 *
 * Like the Noir backdrop filter this is a render-time [ColorFilter] on the already-blurred,
 * already-decoded backdrop image: no re-decode, no extra Coil cache entry, and — crucially —
 * no continuous animation, so it costs nothing while the library sits idle. One shared filter
 * instance is reused.
 */
private val ADVENTURE_WARM_FILTER: ColorFilter = ColorFilter.colorMatrix(
    // Tonal desaturation keeps it a cohesive wash rather than garish...
    ColorMatrix().apply { setToSaturation(0.78f) }.also { m ->
        // ...then push a strong amber cast: lift red, nudge green (gold = R+G), cut blue hard.
        m *= ColorMatrix(
            floatArrayOf(
                1.28f, 0f, 0f, 0f, 10f,
                0f, 1.07f, 0f, 0f, 5f,
                0f, 0f, 0.62f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            )
        )
    }
)

/** Warm grade for the library blur backdrop, or `null` when Adventure mode is off. */
fun adventureBackdropColorFilter(active: Boolean): ColorFilter? =
    if (active) ADVENTURE_WARM_FILTER else null

/**
 * One-shot metallic "shine" sweep for a cover, driven by a [progress] provider that a caller
 * animates 0→1 once when a tile gains focus. [color] picks the metal (gold for the Gold icon,
 * silver for Adventure). A diagonal translucent band travels across the cover, then stops — it
 * is NOT an infinite transition, so it adds no idle cost.
 *
 * [progress] is a lambda, not a value: it is read inside the [drawWithContent] lambda (the DRAW
 * phase), where the gradient's offset (`head`) and its stops' alpha both live — reading it here
 * instead of at the call site means an Animatable frame re-triggers only this tile's draw pass,
 * not a recomposition of the whole tile. At [progress] ≤ 0 or ≥ 1 the draw is skipped (still
 * calls `drawContent()`), matching the previous no-op-modifier behaviour for idle cost.
 */
fun Modifier.metallicShimmer(progress: () -> Float, color: Color): Modifier {
    return this.drawWithContent {
        drawContent()
        val p = progress()
        if (p <= 0f || p >= 1f) return@drawWithContent
        // Opacity envelope: 0 → 1 → 0 across the sweep (sine hump) so the shine fades in and
        // fades out smoothly instead of popping off when the band reaches the clip edge.
        val env = sin(p * PI).toFloat().coerceIn(0f, 1f)
        val diag = size.width + size.height
        // Head sweeps from fully off the top-left to fully off the bottom-right.
        val head = -diag + p * (2f * diag)
        val band = size.width * 0.6f
        val brush = Brush.linearGradient(
            colors = listOf(
                Color.Transparent,
                color.copy(alpha = 0.18f * env),
                color.copy(alpha = 0.55f * env),
                color.copy(alpha = 0.18f * env),
                Color.Transparent,
            ),
            start = Offset(head, 0f),
            end = Offset(head + band, size.height),
        )
        drawRect(brush = brush, blendMode = BlendMode.Plus)
    }
}
