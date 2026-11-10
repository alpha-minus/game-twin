package com.twinlore.app.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class MultiDeleteQuickSelectTest {
    @Test
    fun adds_matches_to_the_existing_selection() {
        assertEquals(setOf("a", "b", "c"), applyQuickSelect(setOf("a"), listOf("b", "c")))
    }

    @Test
    fun tapping_again_when_all_matches_are_ticked_unticks_only_them() {
        assertEquals(setOf("a"), applyQuickSelect(setOf("a", "b", "c"), listOf("b", "c")))
    }

    @Test
    fun partly_ticked_matches_get_completed_not_cleared() {
        assertEquals(setOf("b", "c"), applyQuickSelect(setOf("b"), listOf("b", "c")))
    }

    @Test
    fun no_matches_changes_nothing() {
        assertEquals(setOf("a"), applyQuickSelect(setOf("a"), emptyList()))
    }

    private fun info(id: String, total: Long, guides: Long) = com.twinlore.app.domain.VaultCleanupInfo(
        vault = com.twinlore.app.domain.Vault(
            id = id, name = id, romPath = null, romFilename = null, systemeid = null, gameId = null,
            source = null, lastOpened = null, createdAt = java.util.Date(0),
        ),
        daysIdle = 0, neverOpened = false, missingFileCount = 0, isEmpty = false,
        totalBytes = total, guideImageBytes = guides,
    )

    @Test
    fun rows_sort_largest_first_by_the_active_mode_and_ties_keep_order() {
        val rows = listOf(info("a", 10, 9), info("b", 30, 1), info("c", 10, 0), info("d", 20, 5))
        assertEquals(listOf("b", "d", "a", "c"), sortBySize(rows, guidesOnly = false).map { it.vault.id })
        assertEquals(listOf("a", "d", "b", "c"), sortBySize(rows, guidesOnly = true).map { it.vault.id })
    }

    @Test
    fun guide_and_image_summary_reads_naturally() {
        assertEquals("No guides or images", guideImageSummary(0, 0))
        assertEquals("1 guide · 0 images", guideImageSummary(1, 0))
        assertEquals("3 guides · 1 image", guideImageSummary(3, 1))
    }
}
