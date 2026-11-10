package com.twinlore.app.thor.led

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Singleton manager for Thor LED rings. Call [onFocusGained] when focus is gained;
 * call [setLedsOff] when LEDs should go dark (e.g. inactivity timeout);
 * call [releaseControl] when the app relinquishes LED control (feature toggle off / app backgrounding)
 * — this stops animations and timers but does NOT write to the LEDs, so whatever colour was showing stays.
 * Call [onAppForegrounded] when app returns (if LED enabled).
 * Turns LED off after configurable inactivity (or never); [onFocusGained] resets the timer when set.
 * Uses a single refined breath animation: starts from dimmed color and breathes up to full brightness.
 * On non-Thor devices, all operations no-op.
 *
 * Colour fidelity: the adaptive LED follows the focus colour's hue exactly. Saturation and
 * brightness only change via the user's Look & Feel controls (saturationBoost, brightness);
 * see [LedColorResolver].
 */
object ThorLedManager {

    private const val FRAME_MS = 32L
    private const val BREATH_DURATION_MS = 900L

    private var controller: LedController? = null
    private val handler = Handler(Looper.getMainLooper())

    private var animationRunnable: Runnable? = null
    private var endRunnable: Runnable? = null
    private var inactivityRunnable: Runnable? = null
    private var pulseRunnable: Runnable? = null

    /** The pulse's full-brightness colour while a pulse is held; null otherwise. */
    private var pulseTarget: Triple<Int, Int, Int>? = null

    private var lastR: Int? = null
    private var lastG: Int? = null
    private var lastB: Int? = null

    private fun getController(): LedController? {
        if (controller == null) {
            controller = try { LedController() } catch (e: Exception) { null }
        }
        return controller
    }

    /**
     * Resolve focus color to final RGB values. Hue is preserved; saturation and brightness only
     * move per the user's [saturationBoost] / [brightness] controls. See [LedColorResolver].
     */
    private fun resolveColor(
        focusColor: Color,
        brightness: Int,
        saturationBoost: Float
    ): Triple<Int, Int, Int> = LedColorResolver.resolve(focusColor, brightness, saturationBoost)

    private fun applyColor(ctrl: LedController, r: Int, g: Int, b: Int) {
        lastR = r
        lastG = g
        lastB = b
        try {
            ctrl.setLedColor(r, g, b, 255, true, true, true, true)
        } catch (_: Exception) { }
    }

    private fun scheduleInactivityTimer(inactivityMs: Long?) {
        if (inactivityMs != null && inactivityMs > 0) {
            inactivityRunnable = Runnable {
                inactivityRunnable = null
                setLedsOff()
            }
            handler.postDelayed(inactivityRunnable!!, inactivityMs)
        }
    }

    /**
     * Set LEDs to the given color immediately (no breath animation).
     * Use for focus events where the color does not change, or button presses.
     * Resets inactivity timer.
     */
    fun setColor(
        focusColor: Color,
        ledEnabled: Boolean,
        brightness: Int,
        saturationBoost: Float,
        inactivityMs: Long? = null
    ) {
        if (retargetPulse(focusColor, ledEnabled, brightness, saturationBoost, inactivityMs)) return
        cancelAnimation()
        cancelInactivityTimer()
        if (!ledEnabled) return
        val ctrl = getController() ?: return
        val (r, g, b) = resolveColor(focusColor, brightness, saturationBoost)
        applyColor(ctrl, r, g, b)
        scheduleInactivityTimer(inactivityMs)
    }

    /**
     * Call when focus color changes (e.g. vault navigation). If LED is enabled,
     * starts at dimmed color and breathes up to full brightness.
     * Cancels any in-progress animation first.
     * @param inactivityMs After this many ms with no focus change, LED turns off. null = never turn off.
     */
    fun onFocusGained(
        focusColor: Color,
        ledEnabled: Boolean,
        brightness: Int,
        saturationBoost: Float,
        inactivityMs: Long? = null
    ) {
        if (retargetPulse(focusColor, ledEnabled, brightness, saturationBoost, inactivityMs)) return
        cancelAnimation()
        cancelInactivityTimer()
        if (!ledEnabled) return
        val ctrl = getController() ?: return
        val (fr, fg, fb) = resolveColor(focusColor, brightness, saturationBoost)

        fun setStatic(breathFactor: Float = 1f) {
            val sr = (fr * breathFactor).roundToInt().coerceIn(0, 255)
            val sg = (fg * breathFactor).roundToInt().coerceIn(0, 255)
            val sb = (fb * breathFactor).roundToInt().coerceIn(0, 255)
            applyColor(ctrl, sr, sg, sb)
        }

        // Refined breath: start from dim (phase = -PI/2) and breathe up to full (phase -> PI/2).
        // One smooth sine half-cycle: dim -> full. Subtle and longer duration.
        var phase = -Math.PI / 2
        val maxFrames = (BREATH_DURATION_MS / FRAME_MS).toInt()
        val phaseIncrement = (Math.PI / maxFrames).toFloat()
        var frameCount = 0
        val runnable = object : Runnable {
            override fun run() {
                animationRunnable = null
                if (frameCount >= maxFrames) {
                    setStatic(1f)
                    return
                }
                val breath = ((sin(phase) + 1.0) / 2.0).coerceIn(0.0, 1.0)
                val breathFactor = (0.12 + 0.88 * breath).toFloat()
                setStatic(breathFactor)
                phase += phaseIncrement
                frameCount++
                animationRunnable = this
                handler.postDelayed(this, FRAME_MS)
            }
        }
        setStatic(0.12f)
        animationRunnable = runnable
        handler.postDelayed(runnable, FRAME_MS)
        endRunnable = Runnable {
            endRunnable = null
            animationRunnable?.let { handler.removeCallbacks(it) }
            animationRunnable = null
            setStatic(1f)
        }
        handler.postDelayed(endRunnable!!, BREATH_DURATION_MS)

        scheduleInactivityTimer(inactivityMs)
    }

    /**
     * Pulse the rings in [focusColor] until [stopPulse]: dim to full and back,
     * [LedPulse.HALF_PERIOD_MS] each way, starting dim. For a screen whose art
     * already pulses (the detected-game showcase), so the rings keep time with it.
     *
     * While a pulse is held, [setColor] and [onFocusGained] only change its
     * colour: a focus event under the showcase (or a late accent extraction)
     * must not flatten the pulse into a static colour.
     * [setLedsOff] and [releaseControl] end it.
     */
    fun startPulse(
        focusColor: Color,
        ledEnabled: Boolean,
        brightness: Int,
        saturationBoost: Float,
        inactivityMs: Long? = null
    ) {
        cancelAnimation()
        cancelInactivityTimer()
        cancelPulse()
        if (!ledEnabled) return
        val ctrl = getController() ?: return
        pulseTarget = resolveColor(focusColor, brightness, saturationBoost)
        pulseTarget?.let { (r, g, b) -> lastR = r; lastG = g; lastB = b }
        val start = SystemClock.uptimeMillis()
        val runnable = object : Runnable {
            override fun run() {
                val (r, g, b) = pulseTarget ?: return
                val factor = LedPulse.factor(SystemClock.uptimeMillis() - start)
                try {
                    ctrl.setLedColor(
                        (r * factor).roundToInt().coerceIn(0, 255),
                        (g * factor).roundToInt().coerceIn(0, 255),
                        (b * factor).roundToInt().coerceIn(0, 255),
                        255, true, true, true, true
                    )
                } catch (_: Exception) { }
                handler.postDelayed(this, FRAME_MS)
            }
        }
        pulseRunnable = runnable
        runnable.run()
        scheduleInactivityTimer(inactivityMs)
    }

    /**
     * End a pulse started by [startPulse] and leave the rings steady on its colour.
     * No-op when no pulse is running.
     */
    fun stopPulse() {
        val target = pulseTarget ?: return
        cancelPulse()
        val ctrl = getController() ?: return
        applyColor(ctrl, target.first, target.second, target.third)
    }

    /** While a pulse is held, retarget it instead of replacing it. True when handled. */
    private fun retargetPulse(
        focusColor: Color,
        ledEnabled: Boolean,
        brightness: Int,
        saturationBoost: Float,
        inactivityMs: Long?
    ): Boolean {
        if (pulseTarget == null) return false
        if (!ledEnabled) {
            cancelPulse()
            return false
        }
        val target = resolveColor(focusColor, brightness, saturationBoost)
        pulseTarget = target
        lastR = target.first; lastG = target.second; lastB = target.third
        cancelInactivityTimer()
        scheduleInactivityTimer(inactivityMs)
        return true
    }

    /**
     * Call when app returns to foreground. If LED is enabled, restores last color (no breath).
     * When there is no last color (e.g. process was killed), applies [focusColor] with [brightness]
     * and [saturationBoost] so LEDs are on when returning to foreground.
     */
    fun onAppForegrounded(
        ledEnabled: Boolean,
        focusColor: Color? = null,
        brightness: Int = 255,
        saturationBoost: Float = 0f
    ) {
        if (!ledEnabled) return
        val ctrl = getController() ?: return
        val (r, g, b) = when {
            lastR != null && lastG != null && lastB != null ->
                intArrayOf(lastR!!, lastG!!, lastB!!)
            focusColor != null -> {
                val (cr, cg, cb) = LedColorResolver.resolve(focusColor, brightness, saturationBoost)
                lastR = cr
                lastG = cg
                lastB = cb
                intArrayOf(cr, cg, cb)
            }
            else -> return
        }
        try {
            ctrl.setLedColor(r, g, b, 255, true, true, true, true)
        } catch (_: Exception) { }
    }

    /**
     * Turn LEDs off (e.g. after inactivity timeout). Keeps last color for restore on foreground.
     */
    fun setLedsOff() {
        cancelInactivityTimer()
        cancelAnimation()
        cancelPulse()
        try {
            getController()?.setLedColor(0, 0, 0, 0, true, true, true, true)
        } catch (_: Exception) { }
    }

    /**
     * Release LED control without changing the current LED colour.
     * Stops animations and timers so the app no longer drives the LEDs;
     * the last colour written to hardware stays visible (SN3112 sysfs is write-only,
     * so we cannot read and later restore the pre-app state).
     * Use when the feature is toggled off or the app goes to background.
     */
    fun releaseControl() {
        cancelInactivityTimer()
        cancelAnimation()
        cancelPulse()
    }

    /**
     * Reset the inactivity countdown (e.g. on touch/scroll). Does not change LED or play animation.
     * No-op if [inactivityMs] is null or <= 0.
     */
    fun resetInactivityTimer(inactivityMs: Long?) {
        if (inactivityMs == null || inactivityMs <= 0) return
        cancelInactivityTimer()
        inactivityRunnable = Runnable {
            inactivityRunnable = null
            setLedsOff()
        }
        handler.postDelayed(inactivityRunnable!!, inactivityMs)
    }

    private fun cancelInactivityTimer() {
        inactivityRunnable?.let { handler.removeCallbacks(it) }
        inactivityRunnable = null
    }

    private fun cancelPulse() {
        pulseRunnable?.let { handler.removeCallbacks(it) }
        pulseRunnable = null
        pulseTarget = null
    }

    private fun cancelAnimation() {
        animationRunnable?.let { handler.removeCallbacks(it) }
        animationRunnable = null
        endRunnable?.let { handler.removeCallbacks(it) }
        endRunnable = null
    }
}
