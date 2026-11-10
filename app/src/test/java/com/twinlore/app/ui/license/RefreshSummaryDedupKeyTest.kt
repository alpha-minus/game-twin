package com.twinlore.app.ui.license

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class RefreshSummaryDedupKeyTest {

    @Test
    fun `same set of names produces same key regardless of order`() {
        val a = refreshSummaryDedupKey(listOf("Elden Ring", "Hollow Knight"))
        val b = refreshSummaryDedupKey(listOf("Hollow Knight", "Elden Ring"))
        assertEquals(a, b)
    }

    @Test
    fun `different sets produce different keys`() {
        val a = refreshSummaryDedupKey(listOf("Elden Ring"))
        val b = refreshSummaryDedupKey(listOf("Hollow Knight"))
        assertNotEquals(a, b)
    }

    @Test
    fun `adding one name changes the key`() {
        val a = refreshSummaryDedupKey(listOf("Elden Ring"))
        val b = refreshSummaryDedupKey(listOf("Elden Ring", "Celeste"))
        assertNotEquals(a, b)
    }

    @Test
    fun `empty list produces a stable key`() {
        assertEquals(
            refreshSummaryDedupKey(emptyList()),
            refreshSummaryDedupKey(emptyList()),
        )
    }
}
