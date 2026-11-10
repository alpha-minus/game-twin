package com.twinlore.app.thor.led

import kotlin.math.cos

/**
 * Pure timing for [ThorLedManager.startPulse], kept apart from the manager so it
 * can be tested without a main looper.
 */
object LedPulse {

    /**
     * One leg of the pulse, dim to full or full to dim. Matches the showcase's
     * cover-art glow (a 1200ms tween, reversed), so the rings and the halo
     * breathe at the same rate.
     */
    const val HALF_PERIOD_MS = 1200L

    /** The dim end, as a fraction of the resolved colour. The glow's own floor. */
    const val MIN_FACTOR = 0.15f

    /**
     * Brightness factor [elapsedMs] into a pulse: [MIN_FACTOR] at the start, 1
     * after one half period, back to [MIN_FACTOR] after two. Cosine-eased so the
     * turnarounds are soft rather than a triangle wave's hard corners.
     */
    fun factor(elapsedMs: Long): Float {
        val t = elapsedMs.mod(HALF_PERIOD_MS * 2).toDouble() / HALF_PERIOD_MS // 0..2
        val leg = if (t <= 1.0) t else 2.0 - t
        val eased = (1.0 - cos(Math.PI * leg)) / 2.0
        return (MIN_FACTOR + (1f - MIN_FACTOR) * eased).toFloat()
    }
}
