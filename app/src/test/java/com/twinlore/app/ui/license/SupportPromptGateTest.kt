package com.twinlore.app.ui.license

import com.twinlore.app.data.SettingsRepository
import com.twinlore.app.data.license.LicenseManagerUiStateContract
import com.twinlore.app.data.license.LicenseTier
import com.twinlore.app.data.license.LicenseUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class SupportPromptGateTest {

    private val day = 24L * 60 * 60 * 1000

    private class FakeLicense(private val licensed: Boolean) : LicenseManagerUiStateContract {
        override val licenseUiState: StateFlow<LicenseUiState> =
            MutableStateFlow(if (licensed) LicenseUiState.Licensed(null) else LicenseUiState.Free)
        override fun isLicensedNow(): Boolean = licensed
        override val licenseTier: StateFlow<LicenseTier> = MutableStateFlow(LicenseTier.NONE)
    }

    private suspend fun freshRepo(
        promptCount: Int = 0,
        consumed: Set<String> = emptySet(),
        lastShownAt: Long = 0L,
        exportCount: Int = 0,
    ): SettingsRepository {
        val repo = SettingsRepository(RuntimeEnvironment.getApplication())
        repo.setSupportPromptState(count = promptCount, consumed = consumed)
        repo.setLastSupportPromptAt(lastShownAt)
        repo.setJourneyExportCount(exportCount)
        return repo
    }

    private fun gate(
        repo: SettingsRepository,
        licensed: Boolean = false,
        vaults: Int = 0,
        now: Long = 100L * 24 * 60 * 60 * 1000,
    ) = SupportPromptGate(
        settings = repo,
        license = FakeLicense(licensed),
        vaultCount = { vaults },
        nowMs = { now },
    )

    @Test
    fun `third vault shows a non final prompt and records everything`() = runTest {
        val repo = freshRepo()
        val now = 100L * day
        val prompt = gate(repo, vaults = 3, now = now).onVaultCreated()

        assertEquals(SupportPromptGate.Prompt(isFinal = false), prompt)
        assertEquals(1, repo.supportPromptCount.first())
        assertEquals(setOf("vaults3"), repo.supportMilestonesConsumed.first())
        assertEquals(now, repo.lastSupportPromptAt.first())
    }

    @Test
    fun `licensed install never prompts and consumes nothing`() = runTest {
        val repo = freshRepo()
        assertNull(gate(repo, licensed = true, vaults = 10).onVaultCreated())
        assertEquals(0, repo.supportPromptCount.first())
    }

    @Test
    fun `export completion increments the counter even when no prompt fires`() = runTest {
        val repo = freshRepo(exportCount = 0)
        assertNull(gate(repo, vaults = 0).onExportCompleted())
        assertEquals(1, repo.journeyExportCount.first())
    }

    @Test
    fun `second prompt inside the week is deferred not lost`() = runTest {
        val now = 100L * day
        val repo = freshRepo(promptCount = 1, consumed = setOf("vaults3"), lastShownAt = now - day, exportCount = 2)
        // 3rd export, one day after the first prompt: blocked by spacing.
        assertNull(gate(repo, vaults = 3, now = now).onExportCompleted())
        assertEquals(1, repo.supportPromptCount.first())
        // 4th export, a week later: the pending exports3 credit fires as the final prompt.
        val later = gate(repo, vaults = 3, now = now + 7 * day).onExportCompleted()
        assertEquals(SupportPromptGate.Prompt(isFinal = true), later)
        assertEquals(2, repo.supportPromptCount.first())
    }

    @Test
    fun `after two prompts the gate is closed for good`() = runTest {
        val now = 200L * day
        val repo = freshRepo(promptCount = 2, consumed = setOf("vaults3", "exports3"), lastShownAt = now - 60 * day, exportCount = 50)
        assertNull(gate(repo, vaults = 50, now = now).onVaultCreated())
    }

    @Test
    fun `legacy monthly prompt timestamp migrates as one prompt already shown`() = runTest {
        val now = 100L * day
        // Legacy install: prompt was shown once by the old monthly logic, count never existed.
        val repo = freshRepo(promptCount = 0, consumed = emptySet(), lastShownAt = now - 2 * day)
        // Inside the 7-day window nothing fires (it would have, were this treated as a first prompt).
        assertNull(gate(repo, vaults = 5, now = now).onVaultCreated())
        assertEquals(1, repo.supportPromptCount.first())
        assertEquals(setOf("vaults3"), repo.supportMilestonesConsumed.first())
        // After the window the next credit (vaults10 here) fires as the final prompt.
        val later = gate(repo, vaults = 10, now = now + 7 * day).onVaultCreated()
        assertEquals(SupportPromptGate.Prompt(isFinal = true), later)
    }
}
