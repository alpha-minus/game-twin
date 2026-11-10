package com.twinlore.app.domain

import com.twinlore.app.data.retroachievements.RaAchievementDisplay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * The RA drawer's search field, which replaced the wallpaper-grid button in the header's action
 * row. It filters the same list the type chip and the locked-only chip filter, so what matters here
 * is that a blank query is a no-op, that a query reads the description as well as the title, and
 * that nothing is matched loosely - a search that quietly returns a near-miss is worse than one
 * that returns nothing, since the drawer offers no other signal that it guessed.
 */
class RaAchievementSearchTest {

    private fun achievement(id: Int, title: String, description: String) = RaAchievementDisplay(
        id = id,
        title = title,
        description = description,
        points = 10,
        badgeName = "badge$id",
        rarityPercent = null,
        dateAwarded = null,
        hardcoreAchieved = false,
    )

    // Ids deliberately out of order, so an order assertion below cannot pass by accident on a
    // filter that happened to sort.
    private val list = listOf(
        achievement(30, "Star Struck", "Collect all the stars"),
        achievement(10, "Speed Demon", "Finish the race under a minute"),
        achievement(20, "Cartographer", "Reveal every room on the map"),
    )

    @Test
    fun `a blank query narrows nothing and keeps the list itself`() {
        // Same instance, not just an equal one: this runs on every keystroke over sets of a few
        // hundred, and the empty case is the one the drawer spends most of its life in.
        assertSame(list, RaAchievementSearch.filter(list, ""))
        assertSame(list, RaAchievementSearch.filter(list, "   "))
    }

    @Test
    fun `a title match is found regardless of casing`() {
        assertEquals(listOf(30), RaAchievementSearch.filter(list, "star struck").map { it.id })
        assertEquals(listOf(30), RaAchievementSearch.filter(list, "STAR").map { it.id })
    }

    @Test
    fun `the description is searched too`() {
        // "what did it ask me to do" is as much of a handle as the name is.
        assertEquals(listOf(20), RaAchievementSearch.filter(list, "every room").map { it.id })
    }

    @Test
    fun `one query, one row per achievement, whichever field it hit`() {
        // "the" is in one achievement's title-side text and in all three descriptions; each is
        // listed once, and the one whose title also matches is not listed twice.
        val hits = listOf(
            achievement(1, "The Stars", "Collect the stars"),
            achievement(2, "Speed Demon", "Finish the race"),
        )
        assertEquals(listOf(1, 2), RaAchievementSearch.filter(hits, "the").map { it.id })
    }

    @Test
    fun `surrounding whitespace is ignored`() {
        assertEquals(listOf(10), RaAchievementSearch.filter(list, "  speed  ").map { it.id })
    }

    @Test
    fun `an unmatched query returns nothing rather than a near miss`() {
        assertEquals(emptyList<Int>(), RaAchievementSearch.filter(list, "Sturr Stuck").map { it.id })
    }

    @Test
    fun `matches keep the order they came in`() {
        // The drawer sorts BEFORE it searches and shows the result directly, so a filter that
        // reordered would quietly undo the sort the user picked. The fixture's ids are not
        // ascending, so this fails on a filter that sorts as well as on one that shuffles.
        assertEquals(listOf(30, 10, 20), RaAchievementSearch.filter(list, "e").map { it.id })
    }
}
