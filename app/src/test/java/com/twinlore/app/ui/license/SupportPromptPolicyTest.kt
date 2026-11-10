package com.twinlore.app.ui.license

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SupportPromptPolicyTest {

    private val day = 24L * 60 * 60 * 1000
    private val now = 1_000_000_000_000L
    private val week = 7L * day

    private fun milestone(
        nowMs: Long = now,
        lastShownMs: Long = 0L,
        licensed: Boolean = false,
        promptsShown: Int = 0,
        vaultCount: Int = 0,
        exportCount: Int = 0,
        consumed: Set<String> = emptySet(),
    ) = supportPromptMilestone(nowMs, lastShownMs, licensed, promptsShown, vaultCount, exportCount, consumed)

    @Test
    fun `licensed user never gets a milestone`() {
        assertNull(milestone(licensed = true, vaultCount = 10, exportCount = 10))
    }

    @Test
    fun `below every threshold nothing fires`() {
        assertNull(milestone(vaultCount = 2, exportCount = 2))
    }

    @Test
    fun `third vault fires the first prompt immediately`() {
        assertEquals(SupportMilestone.VAULTS_3, milestone(vaultCount = 3))
    }

    @Test
    fun `third export fires the first prompt immediately`() {
        assertEquals(SupportMilestone.EXPORTS_3, milestone(exportCount = 3))
    }

    @Test
    fun `first prompt ignores the spacing window`() {
        // lastShownMs > 0 with promptsShown == 0 models the legacy monthly
        // prompt timestamp before migration; a shown-count of 0 means the
        // milestone model has never prompted, so spacing does not apply.
        assertEquals(SupportMilestone.VAULTS_3, milestone(lastShownMs = now - day, vaultCount = 3))
    }

    @Test
    fun `vaults beat exports when both cross at once`() {
        assertEquals(SupportMilestone.VAULTS_3, milestone(vaultCount = 3, exportCount = 3))
    }

    @Test
    fun `consumed milestone does not fire again`() {
        assertNull(milestone(vaultCount = 3, promptsShown = 1, lastShownMs = now - 2 * week, consumed = setOf("vaults3")))
    }

    @Test
    fun `second prompt within seven days stays quiet even with a pending credit`() {
        assertNull(milestone(vaultCount = 3, exportCount = 3, promptsShown = 1, lastShownMs = now - day, consumed = setOf("vaults3")))
    }

    @Test
    fun `pending credit fires at the next event after the window`() {
        // The user crossed exports3 the same day vaults3 prompted; a week later
        // (say at the 7th export) the pending exports3 credit fires.
        assertEquals(
            SupportMilestone.EXPORTS_3,
            milestone(vaultCount = 3, exportCount = 7, promptsShown = 1, lastShownMs = now - week, consumed = setOf("vaults3")),
        )
    }

    @Test
    fun `exactly at the seven day boundary fires`() {
        assertEquals(
            SupportMilestone.VAULTS_10,
            milestone(vaultCount = 10, promptsShown = 1, lastShownMs = now - week, consumed = setOf("vaults3")),
        )
    }

    @Test
    fun `tenth vault is the second prompt`() {
        assertEquals(
            SupportMilestone.VAULTS_10,
            milestone(vaultCount = 10, exportCount = 0, promptsShown = 1, lastShownMs = now - 2 * week, consumed = setOf("vaults3")),
        )
    }

    @Test
    fun `after two prompts nothing ever fires again`() {
        assertNull(
            milestone(vaultCount = 100, exportCount = 100, promptsShown = 2, lastShownMs = now - 10 * week, consumed = setOf("vaults3", "exports3")),
        )
    }
}
