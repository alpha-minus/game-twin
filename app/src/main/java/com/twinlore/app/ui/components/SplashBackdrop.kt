package com.twinlore.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.twinlore.app.R
import com.twinlore.app.data.AppIcon
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

private const val TAU = 6.2831855f

/**
 * Looping, thematic splash background drawn BEHIND the logo. Each premium icon
 * gets a motif matching its art. It loops for however long the app-open window
 * lasts, with no fixed duration. Default draws nothing.
 */
@Composable
fun SplashBackdrop(icon: AppIcon, modifier: Modifier = Modifier) {
    when (icon) {
        AppIcon.CYBER -> CyberBackdrop(modifier)
        AppIcon.DEMONIC -> RuneBackdrop(modifier, Color(0xFFE21414))
        AppIcon.GOLD -> SoftGlowBackdrop(modifier, Color(0xFFF7CE5B))
        AppIcon.ADVENTURE -> JewelBackdrop(modifier)
        AppIcon.NOIR -> NoirBackdrop(modifier)
        AppIcon.PIXEL -> PixelSparkleBackdrop(modifier)
        AppIcon.DEFAULT -> Unit
    }
}

/** Deterministic pseudo-random in [0,1) from an int seed (no Random state). */
private fun rnd(seed: Int): Float {
    val x = sin(seed * 12.9898f) * 43758.5453f
    return x - floor(x)
}

// ── Cyberpunk: blurred neon city + dense slow rain ────────────────────────
@Composable
private fun CyberBackdrop(modifier: Modifier, color: Color = Color(0xFF3FE0F5)) {
    Box(modifier) {
        Image(
            painter = painterResource(R.drawable.splash_cyber_city),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alpha = 0.9f,
        )
        val t by rememberInfiniteTransition("rain").animateFloat(
            0f, 1f, infiniteRepeatable(tween(3200, easing = LinearEasing)), label = "t",
        )
        // Each drop carries an INDEPENDENT phase (d[3]); tying phase to x is what
        // produced a diagonal "rain front" with empty wedges. Independent phases
        // scatter the drops across the whole splash from the first frame.
        val drops = remember {
            List(150) { i -> floatArrayOf(rnd(i * 4), 0.6f + rnd(i * 4 + 1) * 0.7f, 0.4f + rnd(i * 4 + 2), rnd(i * 4 + 3)) }
        }
        Canvas(Modifier.fillMaxSize()) {
            val span = size.height + size.minDimension * 1.2f
            drops.forEach { d ->
                val phase = (t * d[1] + d[3]) % 1f
                val y = phase * span - size.minDimension * 0.6f
                val h = size.minDimension * 0.09f * (0.6f + d[2])
                val px = d[0] * size.width
                drawLine(
                    color.copy(alpha = 0.07f + 0.18f * d[2]),
                    Offset(px + h * 0.06f, y), Offset(px, y + h), strokeWidth = 1.5f,
                )
            }
        }
    }
}

// ── Demonic: circulating glowing runes (no rings, no clash) ───────────────
private const val RUNES = "ᚠᚢᚦᚨᚱᚲᚷᚹᚾᛁᛃᛇᛈᛉᛊᛏᛒᛖᛗᛚᛜᛞᛟᚺᚲᛦᛥ"

@Composable
private fun RuneBackdrop(modifier: Modifier, color: Color) {
    val it = rememberInfiniteTransition("rune")
    val rot by it.animateFloat(0f, 360f, infiniteRepeatable(tween(22000, easing = LinearEasing)), label = "rot")
    val rot2 by it.animateFloat(0f, 360f, infiniteRepeatable(tween(34000, easing = LinearEasing)), label = "rot2")
    val pulse by it.animateFloat(
        0.4f, 1f, infiniteRepeatable(tween(1700, easing = LinearEasing), RepeatMode.Reverse), label = "pulse",
    )
    Canvas(modifier) {
        val base = size.minDimension
        drawRing(RUNES, 20, base * 0.47f, rot, base * 0.050f, color, pulse, seed = 0)
        drawRing(RUNES, 13, base * 0.35f, rot2, base * 0.044f, color, pulse * 0.8f, seed = 7)
    }
}

private fun DrawScope.drawRing(
    glyphs: String, n: Int, radius: Float, angleDeg: Float, textPx: Float, color: Color, alpha: Float, seed: Int,
) {
    val paint = android.graphics.Paint().apply {
        isAntiAlias = true
        this.color = color.copy(alpha = alpha.coerceIn(0f, 1f)).toArgb()
        textSize = textPx
        textAlign = android.graphics.Paint.Align.CENTER
        setShadowLayer(textPx * 0.7f, 0f, 0f, color.toArgb())
    }
    val fm = paint.fontMetrics
    for (i in 0 until n) {
        val a = Math.toRadians((angleDeg + i * 360.0 / n))
        val x = center.x + radius * cos(a).toFloat()
        val y = center.y + radius * sin(a).toFloat() - (fm.ascent + fm.descent) / 2f
        drawContext.canvas.nativeCanvas.drawText(glyphs[(i + seed) % glyphs.length].toString(), x, y, paint)
    }
}

// ── Gold: minimal soft breathing glow (premium, understated) ──────────────
@Composable
private fun SoftGlowBackdrop(modifier: Modifier, color: Color) {
    val p by rememberInfiniteTransition("glow").animateFloat(
        0.72f, 1f, infiniteRepeatable(tween(2800, easing = LinearEasing), RepeatMode.Reverse), label = "p",
    )
    Canvas(modifier) {
        val r = size.minDimension * (0.42f + 0.12f * p)
        drawCircle(
            brush = Brush.radialGradient(
                listOf(color.copy(alpha = 0.14f * p), color.copy(alpha = 0.045f * p), Color.Transparent),
                center = center, radius = r,
            ),
            radius = r, center = center,
        )
    }
}

// ── Adventure: gold glow dots (fireflies), clear of the centre ────────────
// Single warm gold to match the logo's gold wreath/frame (not multi-jewel).
private val JEWELS = listOf(Color(0xFFF6C64B))

@Composable
private fun JewelBackdrop(modifier: Modifier) {
    val t by rememberInfiniteTransition("jewel").animateFloat(
        0f, 1f, infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "t",
    )
    val flies = remember { List(26) { i -> floatArrayOf(rnd(i * 5), rnd(i * 5 + 1), 0.02f + rnd(i * 5 + 2) * 0.04f, rnd(i * 5 + 3), 0.8f + rnd(i * 5 + 4) * 1.4f) } }
    Canvas(modifier) {
        flies.forEachIndexed { i, f ->
            val x = f[0] + f[2] * sin(TAU * (t + f[3]))
            val y = f[1] + f[2] * 0.8f * cos(TAU * (t * 0.85f + f[3]))
            val o = Offset(x * size.width, (((y % 1f) + 1f) % 1f) * size.height)
            // keep the area around the centred logo clear (no star cutting through it)
            val dx = (o.x - center.x) / size.minDimension
            val dy = (o.y - center.y) / size.minDimension
            if (sqrt(dx * dx + dy * dy) < 0.24f) return@forEachIndexed
            val b = 0.5f + 0.5f * sin(TAU * (t * f[4] + f[3] * 3f))
            val a = b * b
            val c = JEWELS[i % JEWELS.size]
            val r = size.minDimension * 0.009f
            drawCircle(Brush.radialGradient(listOf(c.copy(alpha = 0.5f * a), Color.Transparent), o, r * 4f), r * 4f, o)
            drawCircle(c.copy(alpha = 0.95f * a), r, o)
        }
    }
}

// Soft retro pastels that sit well on the pixel icon's purple field + mint logo.
private val PIXEL_PALETTE = listOf(
    Color(0xFFB2ECC6), // mint (matches the logo)
    Color(0xFFF3E7A6), // pale gold
    Color(0xFFF3B0DA), // soft pink
    Color(0xFFA9E4F0), // light cyan
    Color(0xFFD9C6FF), // lavender
)

// ── Noir: white rain + lightning flashes (rainy-night noir) ──────────────
@Composable
private fun NoirBackdrop(modifier: Modifier) {
    val inf = rememberInfiniteTransition("noir")
    val t by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "rain")
    val lt by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(3600, easing = LinearEasing)), label = "lightning")
    val rain = Color(0xFFEAEAF2)
    // x, speed, length, phase
    val drops = remember {
        List(150) { i -> floatArrayOf(rnd(i * 4), 0.6f + rnd(i * 4 + 1) * 0.7f, 0.4f + rnd(i * 4 + 2), rnd(i * 4 + 3)) }
    }
    Canvas(modifier) {
        val f = lightningLevel(lt)
        // Lightning: brief white flash over the whole splash (double-flash then dark).
        if (f > 0.001f) drawRect(Color.White.copy(alpha = 0.22f * f), size = size)
        // Rain: dense near-vertical white streaks; brightens during a flash.
        val span = size.height + size.minDimension * 1.2f
        drops.forEach { d ->
            val phase = (t * d[1] + d[3]) % 1f
            val y = phase * span - size.minDimension * 0.6f
            val h = size.minDimension * 0.09f * (0.6f + d[2])
            val px = d[0] * size.width
            drawLine(
                rain.copy(alpha = (0.12f + 0.18f * d[2] + 0.3f * f).coerceAtMost(1f)),
                Offset(px + h * 0.05f, y), Offset(px, y + h), strokeWidth = 1.6f,
            )
        }
    }
}

/** Lightning intensity over a [0,1] cycle: a sharp double-flash, then darkness. */
private fun lightningLevel(p: Float): Float = when {
    p < 0.03f -> p / 0.03f
    p < 0.06f -> 1f - (p - 0.03f) / 0.03f
    p < 0.09f -> 0.5f * ((p - 0.06f) / 0.03f)
    p < 0.12f -> 0.5f * (1f - (p - 0.09f) / 0.03f)
    else -> 0f
}

// ── Pixel: retro grid-aligned sparkles (dot → plus → star → plus → dot) ───
@Composable
private fun PixelSparkleBackdrop(modifier: Modifier) {
    val t by rememberInfiniteTransition("pixsp").animateFloat(
        0f, 1f, infiniteRepeatable(tween(12000, easing = LinearEasing)), label = "t",
    )
    // Stratified placement: one sparkle per grid cell (jittered) so they spread
    // evenly across the whole splash instead of clustering near the centre.
    val cols = 5
    val rows = 8
    val stars = remember {
        List(cols * rows) { i ->
            val col = i % cols
            val row = i / cols
            // Push y away from the vertical centre (behind the logo) toward the
            // top and bottom: exponent < 1 spreads mid values outward.
            val yRaw = (row + 0.1f + 0.8f * rnd(i * 4 + 1)) / rows
            val d = yRaw - 0.5f
            val y = 0.5f + (if (d < 0f) -1f else 1f) * (abs(d) * 2f).pow(0.5f) * 0.5f
            floatArrayOf(
                (col + 0.1f + 0.8f * rnd(i * 4)) / cols, // x
                y,                                       // y (top/bottom biased)
                rnd(i * 4 + 2),                          // phase
                0.5f + rnd(i * 4 + 3) * 0.6f,            // twinkle-speed
            )
        }
    }
    Canvas(modifier) {
        val cell = size.minDimension * 0.011f // chunky pixel; everything snaps to it
        fun snap(v: Float) = floor(v / cell) * cell
        stars.forEachIndexed { i, s ->
            val ph = (t * s[3] + s[2]) % 1f
            val level = sin(TAU * ph).coerceAtLeast(0f) // on for half the cycle, off the rest
            if (level < 0.06f) return@forEachIndexed
            drawSparkle(Offset(snap(s[0] * size.width), snap(s[1] * size.height)), cell, level, PIXEL_PALETTE[i % PIXEL_PALETTE.size])
        }
    }
}

/** A classic RPG twinkle drawn from square cells: core → plus → star, by [level]. */
private fun DrawScope.drawSparkle(c: Offset, cell: Float, level: Float, color: Color) {
    fun cellAt(gx: Int, gy: Int, alpha: Float) =
        drawRect(color.copy(alpha = (alpha * level).coerceIn(0f, 1f)), Offset(c.x + gx * cell, c.y + gy * cell), Size(cell, cell))
    cellAt(0, 0, 1f)
    if (level > 0.30f) {
        cellAt(1, 0, 1f); cellAt(-1, 0, 1f); cellAt(0, 1, 1f); cellAt(0, -1, 1f)
    }
    if (level > 0.62f) {
        cellAt(2, 0, 0.55f); cellAt(-2, 0, 0.55f); cellAt(0, 2, 0.55f); cellAt(0, -2, 0.55f)
    }
}
