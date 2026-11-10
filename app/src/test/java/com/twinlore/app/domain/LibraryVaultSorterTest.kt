package com.twinlore.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Date

class LibraryVaultSorterTest {

    private fun vault(
        id: String,
        name: String,
        created: Long,
        opened: Long?
    ) = Vault(
        id = id,
        name = name,
        romPath = null,
        romFilename = null,
        systemeid = null,
        gameId = null,
        source = null,
        lastOpened = opened?.let { Date(it) },
        createdAt = Date(created)
    )

    private val a = vault("a", "Banjo", created = 100, opened = 50)
    private val b = vault("b", "alpha", created = 300, opened = null)
    private val c = vault("c", "Crash", created = 200, opened = 999)

    private val info = mapOf(
        "a" to AchievementSortInfo(softcoreFraction = 0.2f, hardcoreFraction = 0.1f),
        "b" to AchievementSortInfo(softcoreFraction = 0.9f, hardcoreFraction = 0.0f),
        // "c" intentionally absent -> treated as 0
    )

    @Test
    fun name_ascending_is_case_insensitive() {
        val sorted = LibraryVaultSorter.sort(listOf(a, b, c), LibrarySortField.NAME, SortDirection.ASC, info)
        assertEquals(listOf("b", "a", "c"), sorted.map { it.id }) // alpha, Banjo, Crash
    }

    @Test
    fun name_descending() {
        val sorted = LibraryVaultSorter.sort(listOf(a, b, c), LibrarySortField.NAME, SortDirection.DESC, info)
        assertEquals(listOf("c", "a", "b"), sorted.map { it.id })
    }

    @Test
    fun date_added_descending_newest_first() {
        val sorted = LibraryVaultSorter.sort(listOf(a, b, c), LibrarySortField.DATE_ADDED, SortDirection.DESC, info)
        assertEquals(listOf("b", "c", "a"), sorted.map { it.id })
    }

    @Test
    fun last_opened_descending_null_sorts_last() {
        val sorted = LibraryVaultSorter.sort(listOf(a, b, c), LibrarySortField.LAST_OPENED, SortDirection.DESC, info)
        assertEquals(listOf("c", "a", "b"), sorted.map { it.id }) // 999, 50, null(0)
    }

    @Test
    fun achievements_descending_missing_info_is_zero() {
        // Sort by overall completion (softcoreFraction): b(0.9), a(0.2), c(0.0 missing).
        val sorted = LibraryVaultSorter.sort(listOf(a, b, c), LibrarySortField.ACHIEVEMENTS, SortDirection.DESC, info)
        assertEquals(listOf("b", "a", "c"), sorted.map { it.id })
    }

    @Test
    fun achievements_ascending_ties_keep_input_order() {
        // 0.0(c missing), 0.2(a), 0.9(b). c first (only one at 0 here).
        val sorted = LibraryVaultSorter.sort(listOf(a, b, c), LibrarySortField.ACHIEVEMENTS, SortDirection.ASC, info)
        assertEquals(listOf("c", "a", "b"), sorted.map { it.id })
    }

    @Test
    fun achievements_ascending_two_way_tie_keeps_input_order() {
        // Two vaults tie at 0 (b stripped of info + c missing) -> input order b before c, then a.
        val infoTie = mapOf("a" to AchievementSortInfo(softcoreFraction = 0.5f, hardcoreFraction = 0.5f))
        val sorted = LibraryVaultSorter.sort(listOf(a, b, c), LibrarySortField.ACHIEVEMENTS, SortDirection.ASC, infoTie)
        assertEquals(listOf("b", "c", "a"), sorted.map { it.id }) // 0.0(b), 0.0(c), 0.5(a)
    }

    @Test
    fun sortField_next_cycles_forward_and_wraps() {
        // R1 bumper: NAME -> DATE_ADDED -> LAST_OPENED -> ACHIEVEMENTS -> NAME.
        assertEquals(LibrarySortField.DATE_ADDED, LibrarySortField.NAME.next())
        assertEquals(LibrarySortField.LAST_OPENED, LibrarySortField.DATE_ADDED.next())
        assertEquals(LibrarySortField.ACHIEVEMENTS, LibrarySortField.LAST_OPENED.next())
        assertEquals(LibrarySortField.NAME, LibrarySortField.ACHIEVEMENTS.next())
    }

    @Test
    fun sortField_previous_cycles_backward_and_wraps() {
        assertEquals(LibrarySortField.ACHIEVEMENTS, LibrarySortField.NAME.previous())
        assertEquals(LibrarySortField.NAME, LibrarySortField.DATE_ADDED.previous())
        assertEquals(LibrarySortField.LAST_OPENED, LibrarySortField.ACHIEVEMENTS.previous())
    }
}
