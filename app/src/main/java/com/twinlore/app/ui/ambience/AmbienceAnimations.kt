package com.twinlore.app.ui.ambience

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.twinlore.app.data.ambience.AmbienceAnimation
import com.twinlore.app.data.ambience.concreteAnimations
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

private const val TAU = 6.2831855f

/**
 * Frames per second the ambience actually advances at.
 *
 * MUST DIVIDE THE DISPLAY REFRESH RATE. The panel here runs at 60 Hz, so 30 (two refreshes per
 * frame) and 20 (three) present evenly, while 24 does not: 60/24 = 2.5, so frames are held for
 * alternately two and three refreshes and continuous motion visibly judders. 24 looked worse than
 * 20 despite drawing more frames, which is the giveaway. 30 is the smoothest option that still
 * halves the work.
 *
 * See [AmbienceClock] for why the clock is a coroutine rather than an animation: an
 * InfiniteTransition wakes the main thread on every vsync whatever rate you sample it at, and
 * that wakeup turned out to be a large part of the cost on its own.
 */
internal const val AMBIENCE_FPS = 30f

/**
 * The loop clock every animation runs on, quantised to [ambienceFps].
 *
 * A raw `animateFloat` changes on every vsync, and any change repaints the whole screen — which
 * on a full-bleed view is the entire cost of these effects (measured: Rain with 150 particles and
 * Fireflies with 30 cost exactly the same, so it was never the particles). Derived state only
 * notifies when the *quantised* value changes, so the frames in between become real no-ops.
 * Read the result inside a draw lambda and the animation never recomposes anything either.
 */
/**
 * The ONE clock every ambient effect runs on, ticked at [AMBIENCE_FPS].
 *
 * Deliberately a single shared ticker rather than one per caller. Per-caller clocks tick at the
 * right rate individually but land at different moments, so a screen with several effects
 * invalidates on the UNION of their ticks — the play-stats page has five (three podium medals and
 * two firefly swarms) and was still repainting at 60 fps despite every clock being set to 24.
 * Sharing one instance means the whole screen advances together, exactly [AMBIENCE_FPS] times a
 * second. Refcounted, so the ticker stops when the last effect leaves the composition.
 */
private object AmbienceClock {
    val time = androidx.compose.runtime.mutableLongStateOf(0L)
    private val scope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.Dispatchers.Main.immediate
    )
    private var users = 0
    private var job: kotlinx.coroutines.Job? = null

    fun acquire() {
        users++
        if (job == null) {
            job = scope.launch {
                val stepMs = (1000f / AMBIENCE_FPS).toLong().coerceAtLeast(1L)
                var nextAt = android.os.SystemClock.elapsedRealtime() + stepMs
                while (true) {
                    // Deadline-paced, so the tick does not drift with however long the frame it
                    // triggered took to draw.
                    val wait = nextAt - android.os.SystemClock.elapsedRealtime()
                    if (wait > 0) kotlinx.coroutines.delay(wait)
                    time.longValue += stepMs
                    nextAt += stepMs
                }
            }
        }
    }

    fun release() {
        users = (users - 1).coerceAtLeast(0)
        if (users == 0) {
            job?.cancel()
            job = null
        }
    }
}

/**
 * Monotonic elapsed-milliseconds clock shared by every effect. Each one derives its own phases
 * from it, so the Journey exporter can feed [AmbiencePainter] the same kind of number from its
 * own frame loop. Read it inside a draw lambda and the animation never recomposes anything.
 */
@Composable
internal fun rememberAmbienceClockMs(): androidx.compose.runtime.LongState {
    androidx.compose.runtime.DisposableEffect(Unit) {
        AmbienceClock.acquire()
        onDispose { AmbienceClock.release() }
    }
    return AmbienceClock.time
}

/**
 * Turns a [AmbienceAnimation.RANDOM] request into a concrete effect, re-rolled whenever
 * [seed] changes (pass the game or system identity). Anything else passes through.
 *
 * Hosts call this themselves when they need to know what actually got picked — the
 * artwork greyscale under [AmbienceAnimation.NOIR] depends on the resolved value, not
 * on what was requested.
 */
@Composable
fun rememberResolvedAnimation(animation: AmbienceAnimation, seed: Any? = null): AmbienceAnimation {
    // The pool honours the user's Random exclusions; it is a CompositionLocal so every host gets
    // the same one without threading the setting through.
    val pool = com.twinlore.app.ui.LocalRandomAnimationPool.current
    return if (animation == AmbienceAnimation.RANDOM) {
        remember(seed, pool) { pool.random() }
    } else {
        animation
    }
}

/**
 * The animated overlay that sits **on top of the wallpaper but behind the
 * magnifier**. Renamed from the prototype's "Style" and reworked so that:
 *  - every animation is tinted by the chosen [accent];
 *  - the rain (Cyber, Noir) loops **seamlessly** — each drop falls an integer
 *    number of times per cycle, so its phase at the loop point equals its phase
 *    at the start and there is no visible restart seam;
 *  - the prototype's "Jewel" is now [AmbienceAnimation.FIREFLIES].
 *
 * These are all particle overlays (no full-bleed artwork), so they layer over an
 * arbitrary wallpaper without occluding it.
 */
@Composable
fun AmbienceOverlay(
    animation: AmbienceAnimation,
    accent: Color,
    modifier: Modifier = Modifier,
    /** Key for the RANDOM mode's pick — pass the game/system identity so each one re-rolls. */
    randomSeed: Any? = null,
    /**
     * Read in the LAYER phase, so a host that fades the effect out never recomposes
     * anything to do it.
     */
    alpha: () -> Float = { 1f },
) {
    val resolved = rememberResolvedAnimation(animation, randomSeed)
    if (resolved == AmbienceAnimation.NONE || resolved == AmbienceAnimation.RANDOM) return
    val painter = remember(resolved, accent) { AmbiencePainter(resolved, accent) }
    val clock = rememberAmbienceClockMs()
    Canvas(
        modifier.graphicsLayer { this.alpha = alpha() }
    ) { painter.draw(this, clock.longValue) }
}

/** Deterministic pseudo-random in [0,1) from an int seed (no Random state). */
private fun rnd(seed: Int): Float {
    val x = sin(seed * 12.9898f) * 43758.5453f
    return x - floor(x)
}


/**
 * Every effect's per-frame drawing, and the state it needs, in one place.
 *
 * Split out of the composable so the per-frame drawing is pure and independently testable: a
 * host builds a painter and hands it an elapsed-millisecond clock; nothing about an effect
 * knows which host is drawing into it.
 *
 * State that costs something to build (particle fields, accent-derived palettes, tiled brushes) is
 * `by lazy`, so constructing a painter only pays for the one effect it will draw.
 */
internal class AmbiencePainter(private val animation: AmbienceAnimation, private val accent: Color) {

    // ── per-effect state, built once ──────────────────────────────────────
    private val rainDrops by lazy {
        // x, INTEGER speed (1..3), length, phase-offset. An integer speed is what
        // makes frac(t*speed + phase) identical at t=0 and t=1: seamless.
        List(150) { i ->
            floatArrayOf(rnd(i * 4), (1 + (rnd(i * 4 + 1) * 3f).toInt()).toFloat(),
                0.4f + rnd(i * 4 + 2), rnd(i * 4 + 3))
        }
    }
    // Noir keeps its dramatic near-white rain, nudged toward the accent so it
    // still belongs to the theme; Cyber is fully accent.
    private val rainColor by lazy {
        if (animation == AmbienceAnimation.NOIR) lerp(Color(0xFFEAEAF2), accent, 0.25f) else accent
    }
    private val warpStars by lazy {
        // angle, INTEGER speed (1..2, keeps the loop seamless), phase.
        List(110) { i ->
            floatArrayOf(rnd(i * 3) * TAU, (1 + (rnd(i * 3 + 1) * 2f).toInt()).toFloat(), rnd(i * 3 + 2))
        }
    }
    private val warpBright by lazy { lerp(accent, Color.White, 0.5f) }
    private val flies by lazy { firefliesField() }
    private val pixelStars by lazy {
        val cols = 6; val rows = 5
        List(cols * rows) { i ->
            val col = i % cols
            val row = i / cols
            floatArrayOf(
                (col + 0.1f + 0.8f * rnd(i * 4)) / cols,
                (row + 0.1f + 0.8f * rnd(i * 4 + 1)) / rows,
                rnd(i * 4 + 2),
                // INTEGER twinkle rate so (t*rate + phase) % 1 is seamless at the loop.
                (1 + (rnd(i * 4 + 3) * 3f).toInt()).toFloat(),
            )
        }
    }
    private val pixelBright by lazy { lerp(accent, Color.White, 0.4f) }
    private val embers by lazy {
        // baseX, rise(int 1..3), size(0.5..1.5), phase, swayRate(int 1..2), flickerRate(int 1..3), colorPick.
        List(56) { i ->
            floatArrayOf(
                rnd(i * 7), (1 + (rnd(i * 7 + 1) * 3f).toInt()).toFloat(), 0.5f + rnd(i * 7 + 2),
                rnd(i * 7 + 3), (1 + (rnd(i * 7 + 4) * 2f).toInt()).toFloat(),
                (1 + (rnd(i * 7 + 5) * 3f).toInt()).toFloat(), rnd(i * 7 + 6),
            )
        }
    }
    private val emberTints by lazy { emberPalette(accent) }
    private val auroraPalette by lazy {
        fun shift(deg: Float, satMul: Float, valMul: Float): Color {
            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(accent.toArgb(), hsv)
            hsv[0] = (hsv[0] + deg + 360f) % 360f
            hsv[1] = (hsv[1] * satMul).coerceIn(0f, 1f)
            hsv[2] = (hsv[2] * valMul).coerceIn(0.35f, 1f)
            return Color(android.graphics.Color.HSVToColor(hsv))
        }
        listOf(shift(0f, 1f, 1f), shift(35f, 0.9f, 1f), shift(-35f, 0.85f, 1.05f))
    }
    private val auroraBlobs by lazy {
        // colorPick, baseX, driftRate(int 1..2), y, radius, xStretch, pulseRate(int 1..3), phase, tilt°.
        List(8) { i ->
            floatArrayOf(
                (i % 3).toFloat(), rnd(i * 8), (1 + (rnd(i * 8 + 1) * 2f).toInt()).toFloat(),
                (i + rnd(i * 8 + 2)) / 8f * 0.9f + 0.05f,
                0.20f + rnd(i * 8 + 3) * 0.18f, 2.2f + rnd(i * 8 + 4) * 1.2f,
                (1 + (rnd(i * 8 + 5) * 3f).toInt()).toFloat(), rnd(i * 8 + 6),
                (rnd(i * 8 + 7) - 0.5f) * 16f,
            )
        }
    }
    private val snowFlakes by lazy {
        // baseX, fall(int 1..2), size(0.5..1.5), phase, swayRate(int 1..2), swayAmp.
        List(70) { i ->
            floatArrayOf(
                rnd(i * 6), (1 + (rnd(i * 6 + 1) * 2f).toInt()).toFloat(), 0.5f + rnd(i * 6 + 2),
                rnd(i * 6 + 3), (1 + (rnd(i * 6 + 4) * 2f).toInt()).toFloat(),
                0.01f + rnd(i * 6 + 5) * 0.03f,
            )
        }
    }
    private val snowColor by lazy { lerp(Color.White, accent, 0.15f) }
    private val skyStars by lazy {
        // x, y, twinkleRate(int 1..4), phase, size(0.5..1.5 with a bright tail), colorPick.
        List(90) { i ->
            val magnitude = rnd(i * 6 + 4)
            floatArrayOf(
                rnd(i * 6), rnd(i * 6 + 1),
                // 1..2 twinkles per 26s cycle — a lazy, believable night-sky shimmer.
                (1 + (rnd(i * 6 + 2) * 2f).toInt()).toFloat(), rnd(i * 6 + 3),
                // Mostly faint pinpricks; roughly one in six is a bright star.
                if (magnitude > 0.85f) 1.6f + (magnitude - 0.85f) * 5f else 0.4f + magnitude,
                rnd(i * 6 + 5),
            )
        }
    }
    // Real skies aren't monochrome: white, ice blue and a warm ember, all nudged to accent.
    private val skyPalette by lazy {
        listOf(
            lerp(Color.White, accent, 0.25f),
            lerp(Color(0xFFBFD9FF), accent, 0.2f),
            lerp(Color(0xFFFFE7C4), accent, 0.2f),
        )
    }
    // One tiled gradient rather than a per-column loop: at a 9px period this is a single draw
    // the GPU repeats, where column rects would be several hundred ops every frame.
    private val triads by lazy {
        Brush.horizontalGradient(
            0.00f to Color(0xFFFF3B30).copy(alpha = 0.05f),
            0.33f to Color(0xFF34C759).copy(alpha = 0.05f),
            0.66f to Color(0xFF3B7BFF).copy(alpha = 0.05f),
            1.00f to Color(0xFFFF3B30).copy(alpha = 0.05f),
            startX = 0f, endX = 9f, tileMode = TileMode.Repeated,
        )
    }
    private val beamPalette by lazy {
        listOf(
            accent,
            accent.shiftHsv(degrees = 150f, satMul = 1f, valMul = 1.1f),
            accent.shiftHsv(degrees = -120f, satMul = 1f, valMul = 1.1f),
        )
    }
    private val beamLanes by lazy {
        // y, phase, thickness(0.7..1.3), colourPick, direction(0 = L→R, 1 = R→L).
        val laneCount = 16
        List(laneCount) { i ->
            val slot = (i + 0.5f) / laneCount
            floatArrayOf(
                0.03f + slot * 0.94f + (rnd(i * 6) - 0.5f) * 0.02f,
                (i.toFloat() / laneCount + rnd(i * 6 + 2) * 0.05f) % 1f,
                0.7f + rnd(i * 6 + 3) * 0.6f,
                rnd(i * 6 + 4),
                if (rnd(i * 6 + 5) < 0.5f) 0f else 1f,
            )
        }
    }
    private val gridGlow by lazy { accent.shiftHsv(degrees = 40f, satMul = 1f, valMul = 1.15f) }
    private val petals by lazy {
        // baseX, fall(int 1..2), size(0.6..1.6), phase, drift(int 1..2 = wind),
        // swayRate(int 1..2), spinRate(int 1..2), flutterRate(int 1..3), colorPick.
        List(48) { i ->
            floatArrayOf(
                rnd(i * 9), (1 + (rnd(i * 9 + 1) * 2f).toInt()).toFloat(), 0.6f + rnd(i * 9 + 2),
                rnd(i * 9 + 3), (1 + (rnd(i * 9 + 4) * 2f).toInt()).toFloat(),
                (1 + (rnd(i * 9 + 5) * 2f).toInt()).toFloat(),
                (1 + (rnd(i * 9 + 6) * 2f).toInt()).toFloat(),
                (1 + (rnd(i * 9 + 7) * 3f).toInt()).toFloat(), rnd(i * 9 + 8),
            )
        }
    }
    private val sakuraTints by lazy { SAKURA_PINKS.map { lerp(it, accent, 0.18f) } }

    /**
     * Origin-centred halo gradients, cached per palette colour and rebuilt only when the drawing
     * size changes. Snow and Bonfire were allocating one radial gradient PER PARTICLE PER FRAME
     * (seventy and fifty-six of them), which is the same defect the firefly swarm had; centred on
     * the origin the brush is reusable, and per-particle size becomes a scale on the transform.
     */
    private var haloBrushes: Array<Brush>? = null
    private var haloRefRadius = 0f
    private var haloForSize = 0f

    private fun halos(refRadius: Float, sizeKey: Float, colors: List<Color>, baseAlpha: Float): Array<Brush> {
        val cached = haloBrushes
        if (cached != null && haloForSize == sizeKey) return cached
        val built = Array(colors.size) { i ->
            Brush.radialGradient(
                listOf(colors[i].copy(alpha = baseAlpha), Color.Transparent),
                center = Offset.Zero, radius = refRadius,
            )
        }
        haloBrushes = built
        haloRefRadius = refRadius
        haloForSize = sizeKey
        return built
    }

    /** Phase in [0,1) for a loop of [loopMs], from the shared elapsed clock. */
    private fun phase(elapsedMs: Long, loopMs: Int): Float = (elapsedMs % loopMs) / loopMs.toFloat()

    fun draw(scope: DrawScope, elapsedMs: Long) = with(scope) {
        when (animation) {
            AmbienceAnimation.NONE, AmbienceAnimation.RANDOM -> Unit
            AmbienceAnimation.CYBER, AmbienceAnimation.NOIR -> drawRain(
                phase(elapsedMs, 3000), phase(elapsedMs, 3600),
                lightning = animation == AmbienceAnimation.NOIR,
            )
            AmbienceAnimation.RUNE -> drawRunes(
                phase(elapsedMs, 22000), phase(elapsedMs, 34000), phase(elapsedMs, 3400),
            )
            AmbienceAnimation.GLOW -> drawGlow(phase(elapsedMs, 5600))
            AmbienceAnimation.WARP -> drawWarp(phase(elapsedMs, 2600))
            AmbienceAnimation.FIREFLIES -> drawFireflies(phase(elapsedMs, FIREFLIES_LOOP_MS))
            AmbienceAnimation.PIXEL -> drawPixelSparkle(phase(elapsedMs, 12000))
            AmbienceAnimation.BONFIRE -> drawBonfire(phase(elapsedMs, 8000))
            AmbienceAnimation.AURA -> drawAurora(phase(elapsedMs, 20000))
            AmbienceAnimation.SNOW -> drawSnow(phase(elapsedMs, 11000))
            AmbienceAnimation.STARFIELD -> drawStarfield(phase(elapsedMs, 26000))
            AmbienceAnimation.SCANLINES -> drawScanlines(phase(elapsedMs, 9000))
            AmbienceAnimation.BEAM -> drawBeam(phase(elapsedMs, 11000))
            AmbienceAnimation.GRID -> drawNeonGrid(phase(elapsedMs, 6000))
            AmbienceAnimation.SAKURA -> drawSakura(phase(elapsedMs, 9000))
        }
    }

    // ── Rain (Cyber / Noir) — seamless loop via integer fall speeds ──────────
    private fun DrawScope.drawRain(t: Float, lt: Float, lightning: Boolean) {
        val flash = if (lightning) lightningLevel(lt) else 0f
        if (flash > 0.001f) drawRect(Color.White.copy(alpha = 0.20f * flash), size = size)
        val span = size.height + size.minDimension * 1.2f
        rainDrops.forEach { d ->
            val phase = (t * d[1] + d[3]) % 1f
            val y = phase * span - size.minDimension * 0.6f
            val h = size.minDimension * 0.09f * (0.6f + d[2])
            val px = d[0] * size.width
            drawLine(
                rainColor.copy(alpha = (0.07f + 0.18f * d[2] + 0.3f * flash).coerceAtMost(1f)),
                Offset(px + h * 0.06f, y), Offset(px, y + h), strokeWidth = 1.6f,
            )
        }
    }

    // ── Rune: two counter-rotating rings of glowing glyphs ────────────────────
    private fun DrawScope.drawRunes(rotT: Float, rot2T: Float, pulseT: Float) {
        val rot = rotT * 360f
        val rot2 = rot2T * 360f
        // The pulse was a Reverse repeat, which is a triangle wave, so it is one linear loop of
        // double the length folded back on itself.
        val pulse = 0.4f + 0.6f * (1f - abs(2f * pulseT - 1f))
        val base = size.minDimension
        drawRing(RUNES, 20, base * 0.47f, rot, base * 0.050f, accent, pulse, seed = 0)
        drawRing(RUNES, 13, base * 0.35f, rot2, base * 0.044f, accent, pulse * 0.8f, seed = 7)
    }

    // ── Soft breathing glow — brighter, and covering a larger area ────────────
    private fun DrawScope.drawGlow(pT: Float) {
        val p = 0.72f + 0.28f * (1f - abs(2f * pT - 1f))
        // Scale off the LONGER edge and reach past it, so the glow fills the
        // screen rather than sitting as a small disc in the middle.
        val r = maxOf(size.width, size.height) * (0.7f + 0.25f * p)
        drawCircle(
            brush = Brush.radialGradient(
                listOf(
                    accent.copy(alpha = 0.34f * p),
                    accent.copy(alpha = 0.16f * p),
                    accent.copy(alpha = 0.05f * p),
                    Color.Transparent,
                ),
                center = center, radius = r,
            ),
            radius = r, center = center,
        )
    }

    // ── Warp: stars streaking outward from the centre (hyperspace) ────────────
    private fun DrawScope.drawWarp(t: Float) {
        // Reach past the edges so stars leave the view before they wrap.
        val maxR = size.minDimension * 1.05f
        // Faint central glow the stars stream out of.
        drawCircle(
            Brush.radialGradient(listOf(accent.copy(alpha = 0.14f), Color.Transparent), center, size.minDimension * 0.22f),
            size.minDimension * 0.22f, center,
        )
        warpStars.forEach { s ->
            val ang = s[0]
            val prog = (t * s[1] + s[2]) % 1f
            // Fade in at the centre, bright mid-flight, fade out at the rim -> the
            // wrap (rim -> centre) is invisible, so the loop is seamless.
            val alpha = sin(TAU * 0.5f * prog) * 0.9f
            val dirX = cos(ang)
            val dirY = sin(ang)
            val r = prog * maxR
            val tailR = (r - maxR * 0.10f * prog).coerceAtLeast(0f)
            val head = Offset(center.x + dirX * r, center.y + dirY * r)
            val tail = Offset(center.x + dirX * tailR, center.y + dirY * tailR)
            drawLine(warpBright.copy(alpha = alpha.coerceIn(0f, 1f)), tail, head, strokeWidth = 1f + prog * 2f)
        }
    }

    // ── Fireflies (was "Jewel"): drifting accent glow dots ────────────────────
    private fun DrawScope.drawFireflies(t: Float) {
        val haloBrush = firefliesHaloBrush(accent, firefliesHaloRadius(size.minDimension))
        drawFirefliesFrame(t, accent, flies, haloBrush)
    }

    // ── Pixel: retro grid-aligned sparkles ────────────────────────────────────
    private fun DrawScope.drawPixelSparkle(t: Float) {
        val cell = size.minDimension * 0.012f
        fun snap(v: Float) = floor(v / cell) * cell
        pixelStars.forEachIndexed { i, s ->
            val ph = (t * s[3] + s[2]) % 1f
            val level = sin(TAU * ph).coerceAtLeast(0f)
            if (level < 0.06f) return@forEachIndexed
            // Two accent-derived tints so the field is not a single flat colour.
            val color = if (i % 2 == 0) accent else pixelBright
            drawSparkle(Offset(snap(s[0] * size.width), snap(s[1] * size.height)), cell, level, color)
        }
    }

    // ── Bonfire: embers rising from a warm breathing base glow ────────────────
    private fun DrawScope.drawBonfire(t: Float) {
        // 0.45 is baked into each ember-colour brush; per-ember brightness rides on the alpha.
        val refR = size.minDimension * 0.006f * 4f
        val halo = halos(refR, size.minDimension, emberTints, baseAlpha = 0.45f)
        // Base glow along the bottom, breathing with sin(TAU*t) (period 1 → seamless).
        val breathe = 0.85f + 0.15f * sin(TAU * t)
        drawRect(
            Brush.verticalGradient(
                0.55f to Color.Transparent,
                1f to emberTints[1].copy(alpha = 0.30f * breathe),
            ),
            size = size,
        )
        embers.forEach { e ->
            val phase = (t * e[1] + e[3]) % 1f            // 0 = below bottom, 1 = burnt out
            val y = size.height * (1.06f - phase * 1.12f) // enter under the edge, die near the top
            val sway = 0.03f * e[2] * sin(TAU * (t * e[4] + e[3] * 2f))
            val x = ((((e[0] + sway) % 1f) + 1f) % 1f) * size.width
            val o = Offset(x, y)
            val flicker = 0.6f + 0.4f * sin(TAU * (t * e[5] + e[3] * 3f))
            // Bright near the fire, gone by the top.
            val a = ((1f - phase) * (1f - phase) * flicker).coerceIn(0f, 1f)
            if (a < 0.02f) return@forEach
            val ci = ((e[6] * emberTints.size).toInt()).coerceIn(0, emberTints.size - 1)
            val color = emberTints[ci]
            val r = size.minDimension * 0.006f * e[2] * (1f - phase * 0.5f)
            withTransform({
                translate(o.x, o.y)
                scale(r * 4f / refR, r * 4f / refR, pivot = Offset.Zero)
            }) {
                drawCircle(halo[ci], refR, Offset.Zero, alpha = a)
            }
            drawCircle(color.copy(alpha = 0.9f * a), r, o)
        }
    }

    // ── Clouds (AURA): drifting glow field built entirely from the accent ──────
    private fun DrawScope.drawAurora(t: Float) {
        auroraBlobs.forEach { b ->
            val color = auroraPalette[b[0].toInt()]
            // Drift across (and past) the sky; the sin(π·xn) envelope makes each glow fade
            // out before it wraps, so the loop never pops.
            val xn = (b[1] + t * b[2]) % 1f
            val cx = xn * (size.width * 1.5f) - size.width * 0.25f
            val edgeFade = sin(TAU * 0.5f * xn)
            val cy = size.height * b[3]
            val r = size.height * b[4]
            val pulse = 0.55f + 0.45f * sin(TAU * (t * b[6] + b[7] * 2f))
            val alpha = (0.085f + 0.075f * pulse) * edgeFade
            if (alpha < 0.008f) return@forEach
            // The glow is a CHAIN of soft circles strung along a waving path — diffuse like
            // a fog bank, but the string of centres undulates like a curtain in the wind.
            val links = 7
            val spread = r * b[5] * 0.30f
            for (k in 0 until links) {
                val kc = k - (links - 1) / 2f
                val x = cx + kc * spread
                val wave = 0.055f * sin(TAU * (x / size.width * 1.4f + t * b[6] + b[7])) +
                    0.025f * sin(TAU * (x / size.width * 3.1f - t * 2f + b[7] * 2f))
                val y = cy + size.height * wave
                val o = Offset(x, y)
                val kr = r * (1f - abs(kc) * 0.09f)
                drawCircle(
                    Brush.radialGradient(
                        listOf(color.copy(alpha = alpha), color.copy(alpha = alpha * 0.35f), Color.Transparent),
                        center = o, radius = kr,
                    ),
                    radius = kr, center = o,
                )
            }
        }
    }

    // ── Snow: soft flakes drifting down with a gentle sway ─────────────────────
    private fun DrawScope.drawSnow(t: Float) {
        // 0.35 is baked into the brush; the per-flake twinkle rides on drawCircle's alpha.
        val refR = size.minDimension * 0.004f * 3f
        val halo = halos(refR, size.minDimension, listOf(snowColor), baseAlpha = 0.35f)
        val margin = size.minDimension * 0.08f
        val span = size.height + margin * 2f
        snowFlakes.forEach { f ->
            val yPhase = (t * f[1] + f[3]) % 1f
            val y = yPhase * span - margin
            val x = ((((f[0] + f[5] * sin(TAU * (t * f[4] + f[3]))) % 1f) + 1f) % 1f) * size.width
            val o = Offset(x, y)
            val r = size.minDimension * 0.004f * f[2]
            val a = 0.35f + 0.45f * f[2] / 1.5f
            withTransform({
                translate(o.x, o.y)
                scale(r * 3f / refR, r * 3f / refR, pivot = Offset.Zero)
            }) {
                drawCircle(halo[0], refR, Offset.Zero, alpha = a)
            }
            drawCircle(snowColor.copy(alpha = a), r, o)
        }
    }

    // ── Starfield: a calm twinkling night sky with the odd shooting star ───────
    private fun DrawScope.drawStarfield(t: Float) {
        skyStars.forEach { s ->
            val tw = sin(TAU * (t * s[2] + s[3]))
            val a = 0.25f + 0.6f * tw * tw
            val o = Offset(s[0] * size.width, s[1] * size.height)
            val color = skyPalette[((s[5] * skyPalette.size).toInt()).coerceIn(0, skyPalette.size - 1)]
            val r = size.minDimension * 0.0030f * s[4]
            // Bright stars get a soft halo and, at full twinkle, a small 4-point sparkle.
            if (s[4] > 1.5f) {
                drawCircle(
                    Brush.radialGradient(
                        listOf(color.copy(alpha = 0.30f * a), Color.Transparent),
                        center = o, radius = r * 5f,
                    ),
                    radius = r * 5f, center = o,
                )
                if (a > 0.6f) {
                    val spike = r * 4.5f * (a - 0.6f) / 0.25f
                    drawLine(color.copy(alpha = 0.5f * a), Offset(o.x - spike, o.y), Offset(o.x + spike, o.y), 1f)
                    drawLine(color.copy(alpha = 0.5f * a), Offset(o.x, o.y - spike), Offset(o.x, o.y + spike), 1f)
                }
            }
            drawCircle(color.copy(alpha = a), r, o)
        }
        // Two fixed-trajectory shooting stars, one per half cycle; each streaks for the
        // first 14% of its half, so most of the time the sky is calm.
        repeat(2) { half ->
            val local = ((t * 2f - half) % 2f + 2f) % 2f  // position of t within this half's cycle
            if (local in 0f..0.28f) {
                val prog = local / 0.28f
                val startX = if (half == 0) 0.15f else 0.75f
                val startY = if (half == 0) 0.12f else 0.30f
                val dir = if (half == 0) 1f else -1f
                val head = Offset(
                    (startX + dir * 0.35f * prog) * size.width,
                    (startY + 0.18f * prog) * size.height,
                )
                val tail = Offset(
                    head.x - dir * size.width * 0.08f * (1f - prog * 0.5f),
                    head.y - size.height * 0.04f * (1f - prog * 0.5f),
                )
                val a = sin(TAU * 0.5f * prog)  // fade in and out
                drawLine(skyPalette[0].copy(alpha = 0.8f * a), tail, head, strokeWidth = 2f)
            }
        }
    }

    // ── Scanlines: a CRT face ─────────────────────────────────────────────────
    private fun DrawScope.drawScanlines(t: Float) {
        drawRect(triads, size = size)
        // Raster: alternating dark/faint pairs, like a shadow-mask CRT.
        val gap = (size.minDimension * 0.007f).coerceAtLeast(3f)
        var y = 0f
        var odd = false
        while (y < size.height) {
            drawLine(
                Color.Black.copy(alpha = if (odd) 0.16f else 0.07f),
                Offset(0f, y), Offset(size.width, y), strokeWidth = 1f,
            )
            odd = !odd
            y += gap
        }
        // Curved glass: the corners fall away into shadow.
        val vigR = maxOf(size.width, size.height) * 0.70f
        drawRect(
            Brush.radialGradient(
                listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.42f)),
                center = center, radius = vigR,
            ),
            size = size,
        )
        // Roll bar: one soft band drifting down, brightest through its middle. It enters from
        // above the top edge and leaves past the bottom, so nothing pops into existence.
        val bandH = size.height * 0.22f
        val top = t * (size.height + bandH) - bandH
        drawRect(
            Brush.verticalGradient(
                0f to Color.Transparent,
                0.5f to lerp(accent, Color.White, 0.5f).copy(alpha = 0.07f),
                1f to Color.Transparent,
                startY = top,
                endY = top + bandH,
            ),
            topLeft = Offset(0f, top),
            size = Size(size.width, bandH),
        )
    }

    // ── Beam: fighting-game energy blasts firing across the screen ─────────────
    private fun DrawScope.drawBeam(t: Float) {
        beamLanes.forEach { l ->
            val local = (t + l[1]) % 1f
            val y = l[0] * size.height
            val color = beamPalette[((l[3] * beamPalette.size).toInt()).coerceIn(0, beamPalette.size - 1)]
            val thickness = size.minDimension * 0.007f * l[2]
            val leftToRight = l[4] < 0.5f
            val originX = if (leftToRight) 0f else size.width

            if (local < BEAM_CHARGE) {
                // Charge: a ball of light swelling at the muzzle, easing in rather than popping.
                val k = smooth(local / BEAM_CHARGE)
                val r = thickness * (1.5f + 5f * k)
                val o = Offset(originX, y)
                drawCircle(
                    Brush.radialGradient(listOf(color.copy(alpha = 0.55f * k), Color.Transparent), o, r),
                    r, o,
                )
                return@forEach
            }

            // One brightness envelope across fire AND afterglow. Two separate curves used to
            // meet at zero and then restart at full, which read as the lane blinking back on
            // after it had gone out.
            val fireEnd = BEAM_CHARGE + BEAM_FIRE
            val firing = local < fireEnd
            val progress = if (firing) (local - BEAM_CHARGE) / BEAM_FIRE else 1f
            val strength = if (firing) {
                // Ramp in over the opening quarter of the pass, then hold: the taper belongs to
                // the afterglow, which picks up exactly where this leaves off.
                smooth(progress / 0.25f)
            } else {
                smooth(1f - (local - fireEnd) / (1f - fireEnd))
            }
            if (strength <= 0.01f) return@forEach

            val headX = if (leftToRight) progress * size.width else size.width - progress * size.width
            drawLine(color.copy(alpha = 0.20f * strength), Offset(originX, y), Offset(headX, y), strokeWidth = thickness * 6f)
            drawLine(color.copy(alpha = 0.70f * strength), Offset(originX, y), Offset(headX, y), strokeWidth = thickness * 2.6f)
            drawLine(Color.White.copy(alpha = 0.80f * strength), Offset(originX, y), Offset(headX, y), strokeWidth = thickness * 0.9f)
            // The head only exists while something is still travelling.
            if (firing) {
                val head = Offset(headX, y)
                val hr = thickness * 5.5f
                drawCircle(
                    Brush.radialGradient(listOf(Color.White, Color.Transparent), head, hr),
                    hr, head, alpha = 0.9f * strength,
                )
            }
        }
    }

    // ── Neon Grid: a synthwave floor receding to the horizon ───────────────────
    private fun DrawScope.drawNeonGrid(t: Float) {
        val horizon = size.height * 0.52f
        val depth = size.height - horizon
        val vanishX = size.width / 2f

        // Horizon haze, so the grid emerges from light rather than from nothing.
        drawRect(
            Brush.verticalGradient(
                0f to Color.Transparent,
                0.5f to gridGlow.copy(alpha = 0.22f),
                1f to Color.Transparent,
                startY = horizon - size.height * 0.05f,
                endY = horizon + size.height * 0.03f,
            ),
            topLeft = Offset(0f, horizon - size.height * 0.05f),
            size = Size(size.width, size.height * 0.08f),
        )

        // Verticals: evenly spaced at the bottom edge, all converging on the vanishing point.
        val columns = 13
        for (i in 0..columns) {
            val f = i / columns.toFloat()
            // Spread beyond the edges so the outermost lines still cross the bottom corners.
            val bottomX = (f * 2.4f - 0.7f) * size.width
            drawLine(
                accent.copy(alpha = 0.35f),
                Offset(vanishX, horizon), Offset(bottomX, size.height), strokeWidth = 1.5f,
            )
        }

        // Horizontals: depth 0 at the horizon, 1 at the bottom edge.
        val rows = 12
        for (i in 0 until rows) {
            val d = (t + i / rows.toFloat()) % 1f
            val y = horizon + depth * d * d
            // Near the horizon the lines are faint and dense; they brighten as they approach.
            val a = (0.10f + 0.45f * d).coerceAtMost(0.55f)
            drawLine(gridGlow.copy(alpha = a), Offset(0f, y), Offset(size.width, y), strokeWidth = 1f + 1.5f * d)
        }
    }

    // ── Sakura: pink cherry-blossom petals drifting on the wind ────────────────
    private fun DrawScope.drawSakura(t: Float) {
        // Let petals enter above and exit below the frame so the top/bottom edges
        // never show a hard spawn/despawn line.
        val margin = size.minDimension * 0.15f
        val span = size.height + margin * 2f
        petals.forEach { p ->
            val yPhase = (t * p[1] + p[3]) % 1f
            val cy = yPhase * span - margin
            val sway = 0.05f * sin(TAU * (t * p[5] + p[3]))
            val xNorm = (((p[0] + t * p[4] + sway) % 1f) + 1f) % 1f
            val cx = xNorm * size.width
            val c = Offset(cx, cy)

            val len = size.minDimension * 0.022f * p[2]          // petal length
            val wid = len * 0.62f                                 // petal width
            // Edge-on flutter: width squashes toward 0 as the petal turns.
            val flutter = 0.25f + 0.75f * abs(sin(TAU * (t * p[7] + p[3])))
            val angle = 360f * ((t * p[6] + p[3]) % 1f)
            val color = sakuraTints[((p[8] * sakuraTints.size).toInt()).coerceIn(0, sakuraTints.size - 1)]

            withTransform({
                rotate(angle, pivot = c)
                scale(flutter, 1f, pivot = c)
            }) {
                // Soft petal: a filled oval with a lighter core for a little depth.
                drawOval(
                    color = color.copy(alpha = 0.85f),
                    topLeft = Offset(c.x - wid / 2f, c.y - len / 2f),
                    size = Size(wid, len),
                )
                drawOval(
                    color = lerp(color, Color.White, 0.35f).copy(alpha = 0.5f),
                    topLeft = Offset(c.x - wid / 4f, c.y - len / 3f),
                    size = Size(wid / 2f, len * 0.5f),
                )
            }
        }
    }
}

// ── shared helpers ────────────────────────────────────────────────────────

/** Lightning intensity over a [0,1] cycle: a sharp double-flash, then darkness. */
private fun lightningLevel(p: Float): Float = when {
    p < 0.03f -> p / 0.03f
    p < 0.06f -> 1f - (p - 0.03f) / 0.03f
    p < 0.09f -> 0.5f * ((p - 0.06f) / 0.03f)
    p < 0.12f -> 0.5f * (1f - (p - 0.09f) / 0.03f)
    else -> 0f
}

private const val RUNES = "ᚠᚢᚦᚨᚱᚲᚷᚹᚾᛁᛃᛇᛈᛉᛊᛏᛒᛖᛗᛚᛜᛞᛟᚺᚲᛦᛥ"

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

private const val BEAM_CHARGE = 0.16f  // fraction of a lane's cycle spent charging
private const val BEAM_FIRE = 0.26f    // fraction spent crossing the screen

/** Smoothstep: 0 and 1 with zero slope at both ends, so nothing snaps on or off. */
private fun smooth(x: Float): Float {
    val k = x.coerceIn(0f, 1f)
    return k * k * (3f - 2f * k)
}

private val SAKURA_PINKS = listOf(
    Color(0xFFFFB7C5), // classic cherry-blossom
    Color(0xFFFF9EB5), // deeper rose
    Color(0xFFFFD1DC), // pale blush
)

// ── Fireflies field, shared with the surface renderer ─────────────────────

internal const val FIREFLIES_LOOP_MS = 9000

internal fun firefliesField(): List<FloatArray> = List(30) { i ->
    floatArrayOf(
        rnd(i * 5), rnd(i * 5 + 1), 0.02f + rnd(i * 5 + 2) * 0.05f,
        rnd(i * 5 + 3), (1 + (rnd(i * 5 + 4) * 3f).toInt()).toFloat(),
    )
}

internal fun firefliesHaloRadius(minDimension: Float): Float = minDimension * 0.009f * 4f

internal fun firefliesHaloBrush(accent: Color, halo: Float): Brush = Brush.radialGradient(
    listOf(accent.copy(alpha = 0.5f), Color.Transparent),
    center = Offset.Zero,
    radius = halo,
)

internal fun DrawScope.drawFirefliesFrame(
    t: Float, accent: Color, flies: List<FloatArray>, haloBrush: Brush,
) {
    val r = size.minDimension * 0.009f
    val halo = r * 4f
    flies.forEach { f ->
        val x = f[0] + f[2] * sin(TAU * (t + f[3]))
        val y = f[1] + f[2] * 0.8f * cos(TAU * (t + f[3]))
        val o = Offset(
            (((x % 1f) + 1f) % 1f) * size.width,
            (((y % 1f) + 1f) % 1f) * size.height,
        )
        val b = 0.5f + 0.5f * sin(TAU * (t * f[4] + f[3] * 3f))
        val a = b * b
        translate(o.x, o.y) { drawCircle(haloBrush, halo, Offset.Zero, alpha = a) }
        drawCircle(accent.copy(alpha = 0.95f * a), r, o)
    }
}
