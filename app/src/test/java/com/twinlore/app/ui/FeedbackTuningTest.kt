package com.twinlore.app.ui

import android.view.HapticFeedbackConstants
import com.twinlore.app.data.HapticFeedbackIntensity
import com.twinlore.app.ui.utils.FeedbackEvent
import com.twinlore.app.ui.utils.FeedbackTuning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedbackTuningTest {

    @Test
    fun gains_are_audible_and_focus_is_quietest() {
        val gains = FeedbackEvent.entries.associateWith { FeedbackTuning.gainFor(it) }
        gains.values.forEach { assertTrue("gain $it out of range", it in 0.1f..1.0f) }
        // SCROLL_TICK fires rapidly while scrolling, so it sits below everything;
        // FOCUS is the quietest of the discrete one-shot events.
        assertEquals(gains.getValue(FeedbackEvent.SCROLL_TICK), gains.values.min())
        assertEquals(
            gains.getValue(FeedbackEvent.FOCUS),
            gains.filterKeys { it != FeedbackEvent.SCROLL_TICK }.values.min(),
        )
    }

    @Test
    fun first_play_is_always_allowed() {
        FeedbackEvent.entries.forEach { e ->
            assertTrue(FeedbackTuning.shouldPlay(e, nowMs = 1000L, lastPlayedMs = emptyMap()))
        }
    }

    @Test
    fun rapid_same_event_replay_is_debounced() {
        val last = mapOf(FeedbackEvent.FOCUS to 1000L)
        assertFalse(FeedbackTuning.shouldPlay(FeedbackEvent.FOCUS, 1100L, last))
        assertTrue(FeedbackTuning.shouldPlay(FeedbackEvent.FOCUS, 1151L, last))
    }

    @Test
    fun confirm_uses_short_debounce() {
        val last = mapOf(FeedbackEvent.CONFIRM to 1000L)
        assertFalse(FeedbackTuning.shouldPlay(FeedbackEvent.CONFIRM, 1040L, last))
        assertTrue(FeedbackTuning.shouldPlay(FeedbackEvent.CONFIRM, 1051L, last))
    }

    @Test
    fun focus_is_suppressed_shortly_after_an_action_event() {
        // Touch tap = CONFIRM now, FOCUS on the next frame — the FOCUS must be swallowed.
        val last = mapOf(FeedbackEvent.CONFIRM to 1000L)
        assertFalse(FeedbackTuning.shouldPlay(FeedbackEvent.FOCUS, 1200L, last))
        assertTrue(FeedbackTuning.shouldPlay(FeedbackEvent.FOCUS, 1251L, last))
    }

    @Test
    fun confirm_is_not_suppressed_after_focus() {
        val last = mapOf(FeedbackEvent.FOCUS to 1000L)
        assertTrue(FeedbackTuning.shouldPlay(FeedbackEvent.CONFIRM, 1060L, last))
    }

    @Test
    fun none_intensity_has_no_recipe_and_no_fallback() {
        FeedbackEvent.entries.forEach { e ->
            assertNull(FeedbackTuning.primitiveRecipeFor(e, HapticFeedbackIntensity.NONE))
            assertNull(FeedbackTuning.fallbackConstantFor(e, HapticFeedbackIntensity.NONE, sdkInt = 34))
        }
    }

    @Test
    fun error_recipe_is_a_double_pulse() {
        val recipe = FeedbackTuning.primitiveRecipeFor(FeedbackEvent.ERROR, HapticFeedbackIntensity.MEDIUM)!!
        assertEquals(2, recipe.steps.size)
        assertTrue(recipe.steps[1].delayMs > 0)
    }

    @Test
    fun primitive_scale_grows_with_intensity() {
        val l = FeedbackTuning.primitiveScaleFor(HapticFeedbackIntensity.LIGHT)!!
        val m = FeedbackTuning.primitiveScaleFor(HapticFeedbackIntensity.MEDIUM)!!
        val s = FeedbackTuning.primitiveScaleFor(HapticFeedbackIntensity.STRONG)!!
        assertTrue(l < m && m < s)
    }

    @Test
    fun error_fallback_uses_reject_on_r_and_above() {
        assertEquals(
            HapticFeedbackConstants.REJECT,
            FeedbackTuning.fallbackConstantFor(FeedbackEvent.ERROR, HapticFeedbackIntensity.MEDIUM, sdkInt = 30)
        )
        assertEquals(
            HapticFeedbackConstants.CONFIRM,
            FeedbackTuning.fallbackConstantFor(FeedbackEvent.ERROR, HapticFeedbackIntensity.MEDIUM, sdkInt = 29)
        )
    }

    @Test
    fun non_error_fallback_matches_legacy_intensity_mapping() {
        assertEquals(
            HapticFeedbackConstants.CLOCK_TICK,
            FeedbackTuning.fallbackConstantFor(FeedbackEvent.FOCUS, HapticFeedbackIntensity.LIGHT, sdkInt = 34)
        )
        assertEquals(
            HapticFeedbackConstants.CONFIRM,
            FeedbackTuning.fallbackConstantFor(FeedbackEvent.CONFIRM, HapticFeedbackIntensity.MEDIUM, sdkInt = 34)
        )
        assertEquals(
            HapticFeedbackConstants.VIRTUAL_KEY,
            FeedbackTuning.fallbackConstantFor(FeedbackEvent.BACK, HapticFeedbackIntensity.STRONG, sdkInt = 34)
        )
    }

    @Test
    fun switch_suppresses_subsequent_focus() {
        val last = mapOf(FeedbackEvent.SWITCH to 1000L)
        assertFalse(FeedbackTuning.shouldPlay(FeedbackEvent.FOCUS, 1200L, last))
        assertTrue(FeedbackTuning.shouldPlay(FeedbackEvent.FOCUS, 1251L, last))
    }

    @Test
    fun refresh_suppresses_subsequent_focus() {
        val last = mapOf(FeedbackEvent.REFRESH to 1000L)
        assertFalse(FeedbackTuning.shouldPlay(FeedbackEvent.FOCUS, 1100L, last))
    }

    @Test
    fun switch_and_refresh_have_primitive_recipes() {
        assertNotNull(FeedbackTuning.primitiveRecipeFor(FeedbackEvent.SWITCH, HapticFeedbackIntensity.MEDIUM))
        val refresh = FeedbackTuning.primitiveRecipeFor(FeedbackEvent.REFRESH, HapticFeedbackIntensity.MEDIUM)!!
        assertTrue(refresh.steps.size >= 2)
        assertTrue(refresh.steps[1].delayMs > 0)
    }

    @Test
    fun amplitude_recipes_exist_for_every_event_and_are_valid() {
        listOf(true, false).forEach { hasAmpControl ->
            FeedbackEvent.entries.forEach { e ->
                val r = FeedbackTuning.amplitudeRecipeFor(e, HapticFeedbackIntensity.MEDIUM, hasAmpControl)!!
                assertEquals(r.timingsMs.size, r.amplitudes.size)
                assertTrue(r.timingsMs.all { it > 0 })
                assertTrue(r.amplitudes.all { it in 0..255 })
                assertTrue("at least one audible pulse", r.amplitudes.any { it > 0 })
            }
        }
    }

    @Test
    fun amplitude_recipes_are_null_for_none_and_scale_with_intensity() {
        FeedbackEvent.entries.forEach { e ->
            assertNull(FeedbackTuning.amplitudeRecipeFor(e, HapticFeedbackIntensity.NONE, true))
            assertNull(FeedbackTuning.amplitudeRecipeFor(e, HapticFeedbackIntensity.NONE, false))
        }
        val light = FeedbackTuning.amplitudeRecipeFor(FeedbackEvent.CONFIRM, HapticFeedbackIntensity.LIGHT, true)!!
        val strong = FeedbackTuning.amplitudeRecipeFor(FeedbackEvent.CONFIRM, HapticFeedbackIntensity.STRONG, true)!!
        assertTrue(light.amplitudes[0] < strong.amplitudes[0])
    }

    @Test
    fun intensity_scales_pulse_duration_so_binary_motors_feel_the_setting() {
        // Devices without amplitude control (AYN Thor) express strength via pulse
        // length — durations MUST differ across intensities or the setting is a no-op.
        FeedbackEvent.entries.forEach { e ->
            val light = FeedbackTuning.amplitudeRecipeFor(e, HapticFeedbackIntensity.LIGHT, false)!!
            val strong = FeedbackTuning.amplitudeRecipeFor(e, HapticFeedbackIntensity.STRONG, false)!!
            val lightOn = light.timingsMs.filterIndexed { i, _ -> light.amplitudes[i] > 0 }.sum()
            val strongOn = strong.timingsMs.filterIndexed { i, _ -> strong.amplitudes[i] > 0 }.sum()
            assertTrue("$e: strong on-time ($strongOn) must exceed light ($lightOn)", strongOn > lightOn)
        }
    }

    @Test
    fun refresh_with_amplitude_control_is_a_rising_crescendo() {
        val r = FeedbackTuning.amplitudeRecipeFor(FeedbackEvent.REFRESH, HapticFeedbackIntensity.MEDIUM, true)!!
        val pulses = r.amplitudes.filter { it > 0 }
        assertTrue("needs >= 3 rising steps", pulses.size >= 3)
        assertEquals("must be non-decreasing (fade-in)", pulses, pulses.sorted())
        assertTrue("must actually rise", pulses.first() < pulses.last())
    }

    @Test
    fun refresh_without_amplitude_control_emulates_fade_in_via_growing_duty_cycle() {
        val r = FeedbackTuning.amplitudeRecipeFor(FeedbackEvent.REFRESH, HapticFeedbackIntensity.MEDIUM, false)!!
        val onDurations = r.timingsMs.filterIndexed { i, _ -> r.amplitudes[i] > 0 }
        val gaps = r.timingsMs.filterIndexed { i, _ -> r.amplitudes[i] == 0 }
        assertTrue("needs >= 3 pulses", onDurations.size >= 3)
        assertEquals("on-times must grow (fade-in)", onDurations, onDurations.sorted())
        assertTrue("on-times must actually grow", onDurations.first() < onDurations.last())
        assertEquals("gaps must shrink", gaps, gaps.sortedDescending())
    }

    @Test
    fun refresh_primitive_recipe_is_a_rising_three_step_crescendo() {
        val r = FeedbackTuning.primitiveRecipeFor(FeedbackEvent.REFRESH, HapticFeedbackIntensity.MEDIUM)!!
        assertEquals(3, r.steps.size)
        val scales = r.steps.map { it.scale }
        assertEquals("scales must be non-decreasing", scales, scales.sorted())
        assertTrue(scales.first() < scales.last())
        assertTrue(r.steps.drop(1).all { it.delayMs > 0 })
    }

    @Test
    fun binary_recipes_use_only_full_or_zero_amplitude() {
        FeedbackEvent.entries.forEach { e ->
            val r = FeedbackTuning.amplitudeRecipeFor(e, HapticFeedbackIntensity.MEDIUM, false)!!
            assertTrue("$e binary amps must be 0 or 255", r.amplitudes.all { it == 0 || it == 255 })
        }
    }
}
