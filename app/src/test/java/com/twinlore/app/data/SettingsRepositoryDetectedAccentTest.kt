package com.twinlore.app.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * The detected-accent pair (quantized [FocusColor] + exact raw ARGB) must move as one.
 *
 * Writing the two keys in separate transactions let MainActivity's accent collector observe the
 * new focus colour still paired with the PREVIOUS game's raw colour, and repaint every accent
 * surface with the stale colour for a few frames — the ES-DE app strip's lit screens tile
 * flickered on every game change with adaptive accent on.
 *
 * Same caveat as [SettingsRepositoryScreensTileTest]: the DataStore behind
 * `preferencesDataStore(name = "settings")` is a process-wide singleton, so every assertion here
 * reads back what the same test just wrote.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SettingsRepositoryDetectedAccentTest {

    @Test
    fun detected_accent_lands_in_both_keys() = runTest {
        val repo = SettingsRepository(RuntimeEnvironment.getApplication())
        repo.setDetectedAccent(FocusColor.TURQUOISE, 0xFF40E0D0.toInt())

        assertEquals(FocusColor.TURQUOISE, repo.focusColor.first())
        assertEquals(0xFF40E0D0.toInt(), repo.rawAccentColorArgb.first())
    }

    @Test
    fun detected_accent_survives_a_new_instance() = runTest {
        SettingsRepository(RuntimeEnvironment.getApplication())
            .setDetectedAccent(FocusColor.CRIMSON, 0xFFAA1133.toInt())

        val afterRestart = SettingsRepository(RuntimeEnvironment.getApplication())
        assertEquals(FocusColor.CRIMSON, afterRestart.focusColor.first())
        assertEquals(0xFFAA1133.toInt(), afterRestart.rawAccentColorArgb.first())
    }

    @Test
    fun accent_pair_never_mixes_a_new_focus_colour_with_the_old_raw() = runTest {
        val repo = SettingsRepository(RuntimeEnvironment.getApplication())
        val oldRaw = 0xFF111111.toInt()
        repo.setDetectedAccent(FocusColor.GOLD, oldRaw)

        val seen = mutableListOf<Pair<FocusColor, Int?>>()
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) {
            repo.accentColors.collect { seen += it }
        }
        repo.setDetectedAccent(FocusColor.EMERALD, 0xFF22CC66.toInt())
        // Wait for the write's emission to reach the collector before judging.
        repo.accentColors.first { it == FocusColor.EMERALD to 0xFF22CC66.toInt() }
        collector.cancel()

        assertFalse(
            "stale pair observed: $seen",
            seen.contains(FocusColor.EMERALD to oldRaw),
        )
        assertEquals(FocusColor.EMERALD to 0xFF22CC66.toInt(), seen.last())
    }
}
