package com.twinlore.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date
import java.util.concurrent.TimeUnit

class VaultCleanupClassifierTest {

    private val day = TimeUnit.DAYS.toMillis(1)
    private val now = 1_000 * day

    private fun vault(id: String, lastOpenedDaysAgo: Long?, createdDaysAgo: Long = 500) = Vault(
        id = id,
        name = id,
        romPath = null,
        romFilename = null,
        systemeid = null,
        gameId = null,
        source = null,
        lastOpened = lastOpenedDaysAgo?.let { Date(now - it * day) },
        createdAt = Date(now - createdDaysAgo * day),
    )

    private fun classifyOne(v: Vault, content: Int = 1, missing: Int = 0) =
        VaultCleanupClassifier.classify(listOf(v), mapOf(v.id to content), mapOf(v.id to missing), now).single()

    @Test
    fun opened_more_than_3_months_ago_is_stale() {
        val info = classifyOne(vault("a", lastOpenedDaysAgo = 91))
        assertTrue(info.isStale)
        assertEquals(listOf("Not opened in 91 days"), info.reasonLabels)
    }

    @Test
    fun opened_exactly_90_days_ago_is_not_stale() {
        assertFalse(classifyOne(vault("a", lastOpenedDaysAgo = 90)).isStale)
        assertFalse(classifyOne(vault("b", lastOpenedDaysAgo = 45)).isStale)
    }

    @Test
    fun never_opened_vault_ages_from_its_creation() {
        val old = classifyOne(vault("a", lastOpenedDaysAgo = null, createdDaysAgo = 100))
        assertTrue(old.isStale)
        assertEquals(listOf("Never opened"), old.reasonLabels)
        // A brand-new vault the user simply hasn't opened yet is not stale.
        assertFalse(classifyOne(vault("b", lastOpenedDaysAgo = null, createdDaysAgo = 2)).isStale)
    }

    @Test
    fun missing_and_empty_flags_come_from_the_counts() {
        val info = classifyOne(vault("a", lastOpenedDaysAgo = 1), content = 0, missing = 2)
        assertTrue(info.hasMissingFiles)
        assertTrue(info.isEmpty)
        assertEquals(listOf("2 missing files", "Empty"), info.reasonLabels)
        assertEquals(listOf("1 missing file"), classifyOne(vault("b", 1), missing = 1).reasonLabels)
    }

    @Test
    fun vault_without_a_content_row_counts_as_empty_and_order_is_kept() {
        val result = VaultCleanupClassifier.classify(
            listOf(vault("z", 1), vault("a", 1)), contentCounts = mapOf("a" to 3), missingCounts = emptyMap(), nowMs = now
        )
        assertEquals(listOf("z", "a"), result.map { it.vault.id })
        assertTrue(result[0].isEmpty)
        assertFalse(result[1].isEmpty)
        assertTrue(result[1].reasonLabels.isEmpty())
    }

    @Test
    fun sizes_are_carried_per_vault_and_formatted_readably() {
        val info = VaultCleanupClassifier.classify(
            listOf(vault("a", 1)), emptyMap(), emptyMap(), now, sizes = mapOf("a" to (3_000_000L to 1_024L))
        ).single()
        assertEquals(3_000_000L, info.totalBytes)
        assertEquals(1_024L, info.guideImageBytes)
        assertEquals("0 B", formatVaultSize(0))
        assertEquals("512 B", formatVaultSize(512))
        assertEquals("1.0 KB", formatVaultSize(1_024))
        assertEquals("2.9 MB", formatVaultSize(3_000_000))
        assertEquals("1.50 GB", formatVaultSize(1_610_612_736))
    }
}
