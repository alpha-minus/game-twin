package com.twinlore.app.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Round-trip tests for the support-prompt milestone state. Same discipline as
 * [SettingsRepositoryLibraryBackgroundStyleTest]: the settings DataStore is a
 * process-wide singleton in the test JVM, so every test writes its own
 * baseline first and asserts only that.
 */
@RunWith(RobolectricTestRunner::class)
class SettingsRepositorySupportPromptStateTest {

    private fun repo() = SettingsRepository(RuntimeEnvironment.getApplication())

    @Test
    fun export_count_increments_from_a_set_baseline() = runTest {
        val repo = repo()
        repo.setJourneyExportCount(4)
        repo.incrementJourneyExportCount()
        assertEquals(5, repo.journeyExportCount.first())
    }

    @Test
    fun record_prompt_shown_bumps_count_timestamp_and_consumed_atomically() = runTest {
        val repo = repo()
        repo.setSupportPromptState(count = 0, consumed = emptySet())
        repo.setLastSupportPromptAt(0L)

        repo.recordSupportPromptShown(milestone = "vaults3", nowMs = 123_456L)

        assertEquals(1, repo.supportPromptCount.first())
        assertEquals(123_456L, repo.lastSupportPromptAt.first())
        assertEquals(setOf("vaults3"), repo.supportMilestonesConsumed.first())
    }

    @Test
    fun consumed_set_accumulates_across_prompts() = runTest {
        val repo = repo()
        repo.setSupportPromptState(count = 1, consumed = setOf("vaults3"))
        repo.recordSupportPromptShown(milestone = "exports3", nowMs = 1L)
        assertEquals(2, repo.supportPromptCount.first())
        assertTrue(repo.supportMilestonesConsumed.first().containsAll(setOf("vaults3", "exports3")))
    }

    @Test
    fun set_support_prompt_state_does_not_touch_the_timestamp() = runTest {
        val repo = repo()
        repo.setLastSupportPromptAt(9_999L)
        repo.setSupportPromptState(count = 1, consumed = setOf("vaults3"))
        assertEquals(9_999L, repo.lastSupportPromptAt.first())
    }

    @Test
    fun state_survives_a_new_repository_instance() = runTest {
        repo().setSupportPromptState(count = 2, consumed = setOf("vaults3", "exports10"))
        val afterRestart = repo()
        assertEquals(2, afterRestart.supportPromptCount.first())
        assertEquals(setOf("vaults3", "exports10"), afterRestart.supportMilestonesConsumed.first())
    }
}
