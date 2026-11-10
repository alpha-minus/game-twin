package com.twinlore.app.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * The "Show Last Played on Launch" toggle (DataStore key unchanged:
 * `open_last_played_on_launch`): on by default, and the value must survive a
 * repository re-instantiation (process restart).
 *
 * It used to be opt-in and default OFF. It is the reason most people configure
 * a detection source at all, so an install that sets one up and never opens
 * Settings again should get it.
 *
 * Same caveat as [SettingsRepositoryDetectedAccentTest]: the DataStore behind
 * `preferencesDataStore(name = "settings")` is a process-wide singleton, so every
 * assertion here reads back what the same test just wrote.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SettingsRepositoryOpenLastPlayedTest {

    @Test
    fun defaults_to_on_and_round_trips() = runTest {
        val repo = SettingsRepository(RuntimeEnvironment.getApplication())
        assertTrue(repo.openLastPlayedOnLaunch.first())

        repo.setOpenLastPlayedOnLaunch(false)
        assertFalse(repo.openLastPlayedOnLaunch.first())

        repo.setOpenLastPlayedOnLaunch(true)
        assertTrue(repo.openLastPlayedOnLaunch.first())
    }

    @Test
    fun survives_a_new_instance() = runTest {
        SettingsRepository(RuntimeEnvironment.getApplication())
            .setOpenLastPlayedOnLaunch(true)

        val afterRestart = SettingsRepository(RuntimeEnvironment.getApplication())
        assertTrue(afterRestart.openLastPlayedOnLaunch.first())
    }
}
