package com.twinlore.app.ui.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.View
import com.twinlore.app.R
import com.twinlore.app.data.HapticFeedbackIntensity

/**
 * Central UI feedback router: one semantic event plays its sound and haptic
 * together. Replaces FocusSoundManager.
 *
 * Loudness: SoundPool.play() volume is RELATIVE to the media stream volume —
 * the framework applies the stream volume on top. We therefore pass only the
 * constant per-event gain from [FeedbackTuning] and never read getStreamVolume
 * (doing both applies system volume twice; that was the old loudness bug).
 *
 * Threading: main-thread only for play/haptic paths, except [loadedSampleIds]
 * which is synchronized because SoundPool's load callback thread is an
 * implementation detail. [soundEnabled] and [hapticIntensity] are @Volatile —
 * written from a settings-collector coroutine in MainActivity, read on the
 * main thread.
 */
object FeedbackManager {
    private const val TAG = "FeedbackManager"
    // Overlap headroom: motifs run up to ~245 ms (refresh shimmer), so rapid
    // SWITCH/CONFIRM sequences need a third stream to avoid mid-motif steals.
    private const val MAX_STREAMS = 3

    /**
     * Process-wide cache of the "Navigation Sounds" setting, kept live by
     * MainActivity's settings collector. Used by the 3-arg [play] overload so
     * helper call sites don't each have to collect the flow.
     */
    @Volatile
    var soundEnabled: Boolean = true

    /**
     * Process-wide cache of the haptic intensity setting, kept live by
     * MainActivity's settings collector — lets chrome/overlay call sites play
     * with just a View in scope.
     */
    @Volatile
    var hapticIntensity: HapticFeedbackIntensity = HapticFeedbackIntensity.MEDIUM

    private var soundPool: SoundPool? = null
    private val soundIds: MutableMap<FeedbackEvent, Int> = mutableMapOf()
    private val loadedSampleIds = mutableSetOf<Int>()
    private val lastPlayedMs = mutableMapOf<FeedbackEvent, Long>()
    private var vibrator: Vibrator? = null
    private var primitivesSupported = false
    private var amplitudeControlSupported = false
    private var initialized = false

    private fun soundResFor(event: FeedbackEvent): Int = when (event) {
        FeedbackEvent.FOCUS -> R.raw.ui_focus
        FeedbackEvent.SWITCH -> R.raw.ui_switch
        FeedbackEvent.CONFIRM -> R.raw.ui_confirm
        FeedbackEvent.BACK -> R.raw.ui_back
        FeedbackEvent.REFRESH -> R.raw.ui_refresh
        FeedbackEvent.ERROR -> R.raw.ui_error
        // Reuse the short focus sample; distinct feel comes from lower gain + higher rate.
        FeedbackEvent.SCROLL_TICK -> R.raw.ui_focus
    }

    /** Preload the 6 event sounds and probe haptic capabilities. Cheap; loads are async. */
    fun initialize(context: Context) {
        if (initialized) return
        initialized = true
        val appContext = context.applicationContext
        try {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            soundPool = SoundPool.Builder()
                .setMaxStreams(MAX_STREAMS)
                .setAudioAttributes(audioAttributes)
                .build()
            soundPool?.setOnLoadCompleteListener { _, sampleId, status ->
                if (status == 0) {
                    synchronized(loadedSampleIds) { loadedSampleIds.add(sampleId) }
                } else {
                    Log.w(TAG, "Sound load failed: sampleId=$sampleId status=$status")
                }
            }
            for (event in FeedbackEvent.entries) {
                val id = soundPool?.load(appContext, soundResFor(event), 1) ?: 0
                if (id != 0) soundIds[event] = id
                else Log.w(TAG, "Failed to queue sound load for $event")
            }
        } catch (e: Exception) {
            // Reset so the lazy path in doPlaySound can retry on the next play.
            Log.e(TAG, "Error initializing sound pool", e)
            try { soundPool?.release() } catch (_: Exception) {}
            soundPool = null
            soundIds.clear()
            initialized = false
        }
        initVibrator(appContext)
    }

    private fun initVibrator(context: Context) {
        try {
            val manager =
                context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            val v = manager?.defaultVibrator ?: return
            vibrator = v
            amplitudeControlSupported = v.hasAmplitudeControl()
            // areAllPrimitivesSupported() alone is NOT trustworthy: the AYN Thor
            // lists TICK/LOW_TICK as supported but its HAL reports 0 ms duration
            // estimates for them and silently drops them at play time (dumpsys
            // vibrator_manager shows status: ignored_unsupported). Require a real
            // (>0 ms) duration estimate for every primitive we compose with.
            val primitives = intArrayOf(
                VibrationEffect.Composition.PRIMITIVE_LOW_TICK,
                VibrationEffect.Composition.PRIMITIVE_TICK,
                VibrationEffect.Composition.PRIMITIVE_CLICK,
            )
            primitivesSupported = v.areAllPrimitivesSupported(*primitives) &&
                v.getPrimitiveDurations(*primitives).all { it > 0 }
            Log.i(
                TAG,
                "Haptic tiers: primitives=$primitivesSupported amplitudeControl=$amplitudeControlSupported"
            )
        } catch (e: Exception) {
            Log.w(TAG, "Vibrator probe failed; using fallback haptics", e)
        }
    }

    /**
     * Play the full feedback (sound + haptic) for a semantic event.
     * One debounce decision gates both channels.
     */
    fun play(
        view: View,
        event: FeedbackEvent,
        soundEnabled: Boolean,
        hapticIntensity: HapticFeedbackIntensity,
    ) {
        if (!gate(event)) return
        doPlaySound(view.context, event, soundEnabled)
        doPlayHaptic(view, event, hapticIntensity)
    }

    /** [play] using the process-wide cached [soundEnabled] flag. */
    fun play(view: View, event: FeedbackEvent, hapticIntensity: HapticFeedbackIntensity) {
        play(view, event, soundEnabled, hapticIntensity)
    }

    /** [play] using both process-wide cached settings. */
    fun play(view: View, event: FeedbackEvent) {
        play(view, event, soundEnabled, hapticIntensity)
    }

    /** Sound-only variant for call sites that manage their own haptics. */
    fun playSound(context: Context, event: FeedbackEvent, enabled: Boolean) {
        if (!enabled) return
        if (!gate(event)) return
        doPlaySound(context, event, true)
    }

    /** Haptic-only variant (e.g. intensity preview in Settings). Not debounced. */
    fun playHaptic(view: View, event: FeedbackEvent, intensity: HapticFeedbackIntensity) {
        doPlayHaptic(view, event, intensity)
    }

    /**
     * Cancel any in-flight vibration. Used when the user selects intensity NONE:
     * the triggering component's own click haptic (played at the OLD intensity a
     * moment earlier) would otherwise be the "one last buzz" after choosing silence —
     * normally the preview vibration pre-empts it, but a NONE preview is a no-op.
     */
    fun cancelHaptic() {
        try {
            vibrator?.cancel()
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling vibration", e)
        }
    }

    private fun gate(event: FeedbackEvent): Boolean {
        // Monotonic clock: wall clock (currentTimeMillis) can jump backwards on
        // NTP/timezone changes, which would mute feedback for the jump duration.
        val now = SystemClock.uptimeMillis()
        if (!FeedbackTuning.shouldPlay(event, now, lastPlayedMs)) return false
        lastPlayedMs[event] = now
        return true
    }

    private fun doPlaySound(context: Context, event: FeedbackEvent, enabled: Boolean) {
        if (!enabled) return
        try {
            if (soundPool == null) {
                initialize(context.applicationContext)
                return // sounds not loaded yet; haptic still fires
            }
            val soundId = soundIds[event] ?: return
            val loaded = synchronized(loadedSampleIds) { soundId in loadedSampleIds }
            if (!loaded) return
            val gain = FeedbackTuning.gainFor(event)
            val rate = FeedbackTuning.rateFor(event)
            soundPool?.play(soundId, gain, gain, 1, 0, rate)
        } catch (e: Exception) {
            Log.e(TAG, "Error playing sound for $event", e)
        }
    }

    private fun doPlayHaptic(view: View, event: FeedbackEvent, intensity: HapticFeedbackIntensity) {
        if (intensity == HapticFeedbackIntensity.NONE) return
        try {
            val v = vibrator
            if (primitivesSupported && v != null) {
                val recipe = FeedbackTuning.primitiveRecipeFor(event, intensity) ?: return
                val composition = VibrationEffect.startComposition()
                for (step in recipe.steps) {
                    composition.addPrimitive(step.primitiveId, step.scale, step.delayMs)
                }
                // USAGE_TOUCH so the system touch-feedback setting scales/gates this
                // exactly like the performHapticFeedback fallback path.
                v.vibrate(
                    composition.compose(),
                    VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH),
                )
            } else if (v != null) {
                // Amplitude tier: no primitives (e.g. AYN Thor) — explicit waveforms
                // calibrated in FeedbackTuning feel far stronger than the thin
                // HapticFeedbackConstants pulses on these motors. Binary motors
                // (no amplitude control) get duration-scaled 0/255 recipes; the
                // 255s are mapped to DEFAULT_AMPLITUDE as the documented-safe value.
                val recipe = FeedbackTuning.amplitudeRecipeFor(event, intensity, amplitudeControlSupported)
                    ?: return
                val amplitudes = if (amplitudeControlSupported) {
                    recipe.amplitudes.toIntArray()
                } else {
                    recipe.amplitudes.map { if (it > 0) VibrationEffect.DEFAULT_AMPLITUDE else 0 }.toIntArray()
                }
                v.vibrate(
                    VibrationEffect.createWaveform(recipe.timingsMs.toLongArray(), amplitudes, -1),
                    VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH),
                )
            } else {
                val constant = FeedbackTuning.fallbackConstantFor(
                    event, intensity, Build.VERSION.SDK_INT
                ) ?: return
                view.performHapticFeedback(constant)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error playing haptic for $event", e)
        }
    }

    fun release() {
        try {
            soundPool?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing sound pool", e)
        }
        soundPool = null
        soundIds.clear()
        synchronized(loadedSampleIds) { loadedSampleIds.clear() }
        lastPlayedMs.clear()
        vibrator = null
        primitivesSupported = false
        amplitudeControlSupported = false
        initialized = false
    }
}
