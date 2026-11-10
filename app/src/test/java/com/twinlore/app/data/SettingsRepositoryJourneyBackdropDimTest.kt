package com.twinlore.app.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Round-trip tests for the journey backdrop's intensity setting. Same discipline as
 * [SettingsRepositorySupportPromptStateTest]: the settings DataStore is a process-wide singleton
 * in the test JVM, so every test writes its own baseline first and asserts only that.
 */
@RunWith(RobolectricTestRunner::class)
class SettingsRepositoryJourneyBackdropDimTest {

    private fun repo() = SettingsRepository(RuntimeEnvironment.getApplication())

    @Test
    fun value_round_trips() = runTest {
        val repo = repo()
        repo.setJourneyBackdropDim(28)
        assertEquals(28, repo.journeyBackdropDim.first())
    }

    @Test
    fun value_survives_a_new_repository_instance() = runTest {
        repo().setJourneyBackdropDim(37)
        val afterRestart = repo()
        assertEquals(37, afterRestart.journeyBackdropDim.first())
    }

    @Test
    fun a_later_write_overwrites_an_earlier_one() = runTest {
        val repo = repo()
        repo.setJourneyBackdropDim(9)
        repo.setJourneyBackdropDim(22)
        assertEquals(22, repo.journeyBackdropDim.first())
    }
}
