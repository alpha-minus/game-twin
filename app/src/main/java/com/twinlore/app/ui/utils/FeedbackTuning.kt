package com.twinlore.app.ui.utils

import android.os.Build
import android.os.VibrationEffect
import android.view.HapticFeedbackConstants
import com.twinlore.app.data.HapticFeedbackIntensity

/** Semantic UI feedback events. Each maps to one sound, one gain, and one haptic recipe. */
enum class FeedbackEvent { FOCUS, SWITCH, CONFIRM, BACK, REFRESH, ERROR, SCROLL_TICK }

/** One primitive in a composed haptic. [delayMs] is the pause before this primitive. */
data class PrimitiveStep(val primitiveId: Int, val scale: Float, val delayMs: Int = 0)

data class HapticRecipe(val steps: List<PrimitiveStep>)

/**
 * One waveform for the amplitude-control tier: createWaveform(timings, amplitudes, -1).
 * Zero-amplitude segments are gaps.
 */
data class AmplitudeRecipe(val timingsMs: List<Long>, val amplitudes: List<Int>)

/**
 * Pure tuning tables and decision logic for UI feedback. Free of runtime Android
 * dependencies so it is plain-JUnit testable; only compile-time SDK constants
 * are referenced.
 */
object FeedbackTuning {

    /**
     * FOCUS within this window after CONFIRM/BACK/ERROR/SWITCH/REFRESH is suppressed — a touch
     * tap on an unfocused element fires the click now and the refocus next frame,
     * and the confirm subsumes the focus tick.
     */
    const val ACTION_SUPPRESSES_FOCUS_MS = 250L

    /**
     * Per-event sound gain passed to SoundPool.play(). The framework applies the
     * media stream volume on top of this exactly once — never multiply the system
     * volume in manually (that applies it twice and loudness falls off
     * quadratically as media volume drops).
     */
    fun gainFor(event: FeedbackEvent): Float = when (event) {
        FeedbackEvent.FOCUS -> 0.60f
        FeedbackEvent.SWITCH -> 0.75f
        FeedbackEvent.BACK -> 0.75f
        FeedbackEvent.CONFIRM -> 0.80f
        FeedbackEvent.REFRESH -> 0.80f
        FeedbackEvent.ERROR -> 0.80f
        // Softer than a discrete FOCUS tick — it fires rapidly while scrolling.
        FeedbackEvent.SCROLL_TICK -> 0.38f
    }

    /**
     * Playback rate (pitch) passed to SoundPool.play(). Reusing the FOCUS sample at a higher
     * rate gives the scroll tick a distinct, lighter "detent" character without a new asset.
     */
    fun rateFor(event: FeedbackEvent): Float = when (event) {
        FeedbackEvent.SCROLL_TICK -> 1.15f
        else -> 1.0f
    }

    /** Minimum interval between two plays of the same event. */
    fun debounceMsFor(event: FeedbackEvent): Long = when (event) {
        // Programmatic double-focus protection (e.g. tab switch closes a menu and
        // refocuses the bottom bar in quick succession).
        FeedbackEvent.FOCUS -> 150L
        // Fast scroll detent: cap to ~22 ticks/sec so a hard fling reads as a rapid wheel
        // rather than a solid tone, while a slow drag still ticks per item.
        FeedbackEvent.SCROLL_TICK -> 45L
        else -> 50L
    }

    /**
     * Whether [event] should fire at [nowMs] given when each event last fired.
     * Gates the whole event — sound and haptic share one decision.
     */
    fun shouldPlay(event: FeedbackEvent, nowMs: Long, lastPlayedMs: Map<FeedbackEvent, Long>): Boolean {
        val last = lastPlayedMs[event]
        if (last != null && nowMs - last < debounceMsFor(event)) return false
        if (event == FeedbackEvent.FOCUS) {
            val lastAction = listOf(FeedbackEvent.SWITCH, FeedbackEvent.CONFIRM, FeedbackEvent.BACK, FeedbackEvent.REFRESH, FeedbackEvent.ERROR)
                .mapNotNull { lastPlayedMs[it] }
                .maxOrNull()
            if (lastAction != null && nowMs - lastAction < ACTION_SUPPRESSES_FOCUS_MS) return false
        }
        return true
    }

    fun primitiveScaleFor(intensity: HapticFeedbackIntensity): Float? = when (intensity) {
        HapticFeedbackIntensity.NONE -> null
        HapticFeedbackIntensity.LIGHT -> 0.4f
        HapticFeedbackIntensity.MEDIUM -> 0.7f
        HapticFeedbackIntensity.STRONG -> 1.0f
    }

    /** Haptic recipe for the primitive-composition path (API 31+, motor support checked at init). */
    fun primitiveRecipeFor(event: FeedbackEvent, intensity: HapticFeedbackIntensity): HapticRecipe? {
        val scale = primitiveScaleFor(intensity) ?: return null
        return when (event) {
            FeedbackEvent.FOCUS -> HapticRecipe(
                listOf(PrimitiveStep(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, scale))
            )
            FeedbackEvent.SWITCH -> HapticRecipe(
                listOf(PrimitiveStep(VibrationEffect.Composition.PRIMITIVE_TICK, scale))
            )
            FeedbackEvent.CONFIRM -> HapticRecipe(
                listOf(PrimitiveStep(VibrationEffect.Composition.PRIMITIVE_CLICK, scale))
            )
            FeedbackEvent.BACK -> HapticRecipe(
                listOf(PrimitiveStep(VibrationEffect.Composition.PRIMITIVE_TICK, scale))
            )
            FeedbackEvent.REFRESH -> HapticRecipe(
                // Fade-in crescendo: low tick → tick → click with rising scale.
                listOf(
                    PrimitiveStep(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, (scale * 0.5f).coerceAtLeast(0.1f)),
                    PrimitiveStep(VibrationEffect.Composition.PRIMITIVE_TICK, (scale * 0.75f).coerceAtLeast(0.1f), delayMs = 30),
                    PrimitiveStep(VibrationEffect.Composition.PRIMITIVE_CLICK, scale, delayMs = 30),
                )
            )
            FeedbackEvent.ERROR -> HapticRecipe(
                listOf(
                    PrimitiveStep(VibrationEffect.Composition.PRIMITIVE_TICK, scale),
                    PrimitiveStep(VibrationEffect.Composition.PRIMITIVE_TICK, scale, delayMs = 65), // 65 ms after the previous tick ENDS (addPrimitive delay semantics) —
                    // close to ui_error.wav's 65 ms onset-to-onset double-thud spacing
                )
            )
            // Lightest possible detent — fires per item while scrolling.
            FeedbackEvent.SCROLL_TICK -> HapticRecipe(
                listOf(PrimitiveStep(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, (scale * 0.5f).coerceAtLeast(0.1f)))
            )
        }
    }

    /**
     * HapticFeedbackConstants fallback for devices without primitive support —
     * exactly the legacy HapticFeedbackHelper mapping, plus REJECT for ERROR on R+.
     */
    fun fallbackConstantFor(event: FeedbackEvent, intensity: HapticFeedbackIntensity, sdkInt: Int): Int? {
        if (intensity == HapticFeedbackIntensity.NONE) return null
        if (event == FeedbackEvent.ERROR && sdkInt >= Build.VERSION_CODES.R) {
            return HapticFeedbackConstants.REJECT
        }
        return when (intensity) {
            HapticFeedbackIntensity.LIGHT -> HapticFeedbackConstants.CLOCK_TICK
            HapticFeedbackIntensity.MEDIUM -> HapticFeedbackConstants.CONFIRM
            HapticFeedbackIntensity.STRONG -> HapticFeedbackConstants.VIRTUAL_KEY
            HapticFeedbackIntensity.NONE -> null
        }
    }

    /** Intensity → amplitude multiplier for the amplitude-waveform tier. */
    fun amplitudeScaleFor(intensity: HapticFeedbackIntensity): Float? = when (intensity) {
        HapticFeedbackIntensity.NONE -> null
        HapticFeedbackIntensity.LIGHT -> 0.55f
        HapticFeedbackIntensity.MEDIUM -> 0.80f
        HapticFeedbackIntensity.STRONG -> 1.00f
    }

    /**
     * Intensity → pulse-duration multiplier for the amplitude-waveform tier.
     * Critical on motors WITHOUT amplitude control (AYN Thor): every non-zero
     * amplitude plays at full strength there, so pulse length is the ONLY axis
     * that can express the user's Light/Medium/Strong setting.
     */
    fun durationScaleFor(intensity: HapticFeedbackIntensity): Float? = when (intensity) {
        HapticFeedbackIntensity.NONE -> null
        HapticFeedbackIntensity.LIGHT -> 0.65f
        HapticFeedbackIntensity.MEDIUM -> 1.00f
        HapticFeedbackIntensity.STRONG -> 1.50f
    }

    /**
     * Haptic recipe for devices without composition primitives (e.g. AYN Thor) —
     * calibrated against ChecklistTapGame's proven feel on that motor.
     *
     * [hasAmplitudeControl] = false produces binary (0/255) waveforms: intensity is
     * expressed purely through pulse duration, and REFRESH's fade-in becomes a
     * growing-duty-cycle pulse train (short bursts with shrinking gaps read as a
     * crescendo on binary motors). Gap segments never scale with intensity — only
     * the pulses do, so rhythm stays stable across settings.
     */
    fun amplitudeRecipeFor(
        event: FeedbackEvent,
        intensity: HapticFeedbackIntensity,
        hasAmplitudeControl: Boolean,
    ): AmplitudeRecipe? {
        val ampScale = amplitudeScaleFor(intensity) ?: return null
        val durScale = durationScaleFor(intensity) ?: return null
        fun ms(base: Long): Long = (base * durScale).toLong().coerceAtLeast(10L)
        fun amp(base: Int): Int =
            if (hasAmplitudeControl) (base * ampScale).toInt().coerceIn(1, 255) else 255
        return when (event) {
            FeedbackEvent.FOCUS -> AmplitudeRecipe(listOf(ms(22)), listOf(amp(140)))
            FeedbackEvent.SWITCH -> AmplitudeRecipe(listOf(ms(30)), listOf(amp(185)))
            FeedbackEvent.BACK -> AmplitudeRecipe(listOf(ms(35)), listOf(amp(200)))
            FeedbackEvent.CONFIRM -> AmplitudeRecipe(listOf(ms(42)), listOf(amp(235)))
            FeedbackEvent.REFRESH -> if (hasAmplitudeControl) {
                // True fade-in: continuous ramp, no gaps.
                AmplitudeRecipe(
                    listOf(ms(18), ms(18), ms(18), ms(18), ms(26)),
                    listOf(amp(60), amp(100), amp(150), amp(200), amp(255)),
                )
            } else {
                // PWM fade-in: growing on-times, shrinking gaps.
                AmplitudeRecipe(
                    listOf(ms(8), 30L, ms(13), 24L, ms(19), 17L, ms(27), 10L, ms(40)),
                    listOf(255, 0, 255, 0, 255, 0, 255, 0, 255),
                )
            }
            FeedbackEvent.ERROR -> AmplitudeRecipe(
                listOf(ms(35), 65L, ms(35)),
                listOf(amp(255), 0, amp(255)),
            )
            // Short, light detent for the scroll wheel.
            FeedbackEvent.SCROLL_TICK -> AmplitudeRecipe(listOf(ms(14)), listOf(amp(90)))
        }
    }
}
