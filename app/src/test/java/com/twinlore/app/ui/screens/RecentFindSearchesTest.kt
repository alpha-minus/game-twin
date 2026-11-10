package com.twinlore.app.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentFindSearchesTest {

    @Test
    fun `newest search comes first`() {
        val store = RecentFindSearchStore()
        store.record("chest")
        store.record("boss")
        store.record("key")
        assertEquals(listOf("key", "boss", "chest"), store.queries.value)
    }

    @Test
    fun `repeating a search moves it to the front instead of duplicating it`() {
        val store = RecentFindSearchStore()
        store.record("chest")
        store.record("boss")
        store.record("chest")
        assertEquals(listOf("chest", "boss"), store.queries.value)
    }

    @Test
    fun `duplicates are matched case-insensitively and the newest spelling wins`() {
        val store = RecentFindSearchStore()
        store.record("Master Sword")
        store.record("boss")
        store.record("master sword")
        assertEquals(listOf("master sword", "boss"), store.queries.value)
    }

    @Test
    fun `queries are trimmed and blanks are ignored`() {
        val store = RecentFindSearchStore()
        store.record("  heart piece  ")
        store.record("")
        store.record("   ")
        store.record(" heart piece")
        assertEquals(listOf("heart piece"), store.queries.value)
    }

    @Test
    fun `list is capped at five, dropping the oldest`() {
        val store = RecentFindSearchStore()
        listOf("a", "b", "c", "d", "e", "f", "g").forEach(store::record)
        assertEquals(RecentFindSearchStore.CAPACITY, store.queries.value.size)
        assertEquals(listOf("g", "f", "e", "d", "c"), store.queries.value)
    }

    @Test
    fun `re-recording an old entry at capacity keeps the other four`() {
        val store = RecentFindSearchStore()
        listOf("a", "b", "c", "d", "e").forEach(store::record)
        store.record("a")
        assertEquals(listOf("a", "e", "d", "c", "b"), store.queries.value)
    }

    @Test
    fun `clear empties the list`() {
        val store = RecentFindSearchStore()
        store.record("chest")
        store.clear()
        assertTrue(store.queries.value.isEmpty())
    }

    @Test
    fun `the process-wide store is one shared instance`() {
        RecentFindSearches.clear()
        try {
            RecentFindSearches.record("dungeon map")
            assertEquals(listOf("dungeon map"), RecentFindSearches.queries.value)
        } finally {
            RecentFindSearches.clear()
        }
    }
}
