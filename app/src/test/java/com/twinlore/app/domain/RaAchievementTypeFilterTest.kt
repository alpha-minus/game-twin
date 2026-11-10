package com.twinlore.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The RA drawer's one type chip cycles all -> progression -> missable. The rules that matter are
 * that the cycle wraps, that ALL hides nothing, and that PROGRESSION keeps `win_condition`.
 */
class RaAchievementTypeFilterTest {

    @Test
    fun `cycle wraps back to ALL`() {
        assertEquals(RaAchievementTypeFilter.PROGRESSION, RaAchievementTypeFilter.ALL.next())
        assertEquals(RaAchievementTypeFilter.MISSABLE, RaAchievementTypeFilter.PROGRESSION.next())
        assertEquals(RaAchievementTypeFilter.ALL, RaAchievementTypeFilter.MISSABLE.next())
    }

    @Test
    fun `three taps return to where you started`() {
        var f = RaAchievementTypeFilter.ALL
        repeat(3) { f = f.next() }
        assertEquals(RaAchievementTypeFilter.ALL, f)
    }

    @Test
    fun `ALL hides nothing, including untyped achievements`() {
        for (type in listOf("progression", "win_condition", "missable", "", null)) {
            assertTrue("ALL should keep type=$type", RaAchievementTypeFilter.ALL.matches(type))
        }
    }

    @Test
    fun `PROGRESSION keeps the win condition too`() {
        assertTrue(RaAchievementTypeFilter.PROGRESSION.matches("progression"))
        assertTrue(RaAchievementTypeFilter.PROGRESSION.matches("win_condition"))
        assertFalse(RaAchievementTypeFilter.PROGRESSION.matches("missable"))
        assertFalse(RaAchievementTypeFilter.PROGRESSION.matches(null))
    }

    @Test
    fun `MISSABLE keeps only missable`() {
        assertTrue(RaAchievementTypeFilter.MISSABLE.matches("missable"))
        assertFalse(RaAchievementTypeFilter.MISSABLE.matches("progression"))
        assertFalse(RaAchievementTypeFilter.MISSABLE.matches("win_condition"))
        assertFalse(RaAchievementTypeFilter.MISSABLE.matches(null))
    }

    @Test
    fun `type matching tolerates casing and stray whitespace`() {
        // The API has been seen returning "Type" vs "type" and padded values; the drawer used to
        // normalize inline, so the filter has to keep doing it.
        assertTrue(RaAchievementTypeFilter.MISSABLE.matches("  Missable "))
        assertTrue(RaAchievementTypeFilter.PROGRESSION.matches("PROGRESSION"))
        assertTrue(RaAchievementTypeFilter.PROGRESSION.matches(" Win_Condition"))
    }

    @Test
    fun `filtering a mixed list keeps the expected members`() {
        val types = listOf("progression", "win_condition", "missable", null, "progression")
        assertEquals(5, types.count { RaAchievementTypeFilter.ALL.matches(it) })
        assertEquals(3, types.count { RaAchievementTypeFilter.PROGRESSION.matches(it) })
        assertEquals(1, types.count { RaAchievementTypeFilter.MISSABLE.matches(it) })
    }

    @Test
    fun `labels are what the chip renders`() {
        assertEquals("Type: All", RaAchievementTypeFilter.ALL.label)
        assertEquals("Progression", RaAchievementTypeFilter.PROGRESSION.label)
        assertEquals("Missable", RaAchievementTypeFilter.MISSABLE.label)
    }
}
