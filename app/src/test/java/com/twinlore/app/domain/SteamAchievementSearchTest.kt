package com.twinlore.app.domain

import com.twinlore.app.data.steam.SteamAchievementDisplay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * The Steam drawer's search field, the RA one's twin. The rule that is Steam's alone is the
 * nullable description: a hidden achievement ships without one, and that must narrow the row out
 * rather than throw or match everything.
 */
class SteamAchievementSearchTest {

    private fun achievement(api: String, title: String, description: String?) =
        SteamAchievementDisplay(
            apiName = api,
            title = title,
            description = description,
            iconUrl = null,
            iconGrayUrl = null,
            achieved = false,
            unlockTime = null,
            hidden = description == null,
        )

    private val list = listOf(
        achievement("a3", "Star Struck", "Collect all the stars"),
        achievement("a1", "Speed Demon", null),
        achievement("a2", "Cartographer", "Reveal every room on the map"),
    )

    @Test
    fun `a blank query narrows nothing and keeps the list itself`() {
        assertSame(list, SteamAchievementSearch.filter(list, ""))
        assertSame(list, SteamAchievementSearch.filter(list, "   "))
    }

    @Test
    fun `titles and descriptions both match, whatever the casing`() {
        assertEquals(listOf("a3"), SteamAchievementSearch.filter(list, "STAR").map { it.apiName })
        assertEquals(listOf("a2"), SteamAchievementSearch.filter(list, "every room").map { it.apiName })
    }

    @Test
    fun `an achievement with no description still matches on its title`() {
        assertEquals(listOf("a1"), SteamAchievementSearch.filter(list, "demon").map { it.apiName })
    }

    @Test
    fun `an achievement with no description is simply not a description match`() {
        assertEquals(emptyList<String>(), SteamAchievementSearch.filter(list, "zzz").map { it.apiName })
    }

    @Test
    fun `matches keep the order they came in`() {
        // Fixture ids are not ascending, so this fails on a filter that sorts as well as on one
        // that shuffles. The drawer sorts before it searches.
        assertEquals(listOf("a3", "a1", "a2"), SteamAchievementSearch.filter(list, "e").map { it.apiName })
    }
}
