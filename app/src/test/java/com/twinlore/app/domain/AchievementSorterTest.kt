package com.twinlore.app.domain

import com.twinlore.app.data.retroachievements.RaAchievementDisplay
import com.twinlore.app.data.steam.SteamAchievementDisplay
import org.junit.Assert.assertEquals
import org.junit.Test

class AchievementSorterTest {

    private fun ra(
        id: Int,
        title: String,
        dateAwarded: String? = null,
        displayOrder: Int = 0,
        points: Int = 0,
        numAwarded: Int? = null,
        type: String? = null
    ) = RaAchievementDisplay(
        id = id,
        title = title,
        description = "",
        points = points,
        badgeName = "",
        rarityPercent = null,
        dateAwarded = dateAwarded,
        hardcoreAchieved = false,
        displayOrder = displayOrder,
        type = type,
        numAwarded = numAwarded
    )

    private fun steam(
        name: String,
        achieved: Boolean = false,
        unlockTime: Long? = null,
        globalPercent: Double? = null
    ) = SteamAchievementDisplay(
        apiName = name,
        title = name,
        description = null,
        iconUrl = null,
        iconGrayUrl = null,
        achieved = achieved,
        unlockTime = unlockTime,
        hidden = false,
        globalPercent = globalPercent
    )

    private fun raIds(list: List<RaAchievementDisplay>) = list.map { it.id }
    private fun steamNames(list: List<SteamAchievementDisplay>) = list.map { it.apiName }

    // --- RetroAchievements ---

    private val raOld = ra(1, "Old", dateAwarded = "2024-01-01 10:00:00")
    private val raNew = ra(2, "New", dateAwarded = "2025-06-01 10:00:00")
    private val raLocked = ra(3, "Locked", dateAwarded = null)
    private val raList = listOf(raOld, raLocked, raNew)

    @Test
    fun `ra unlock time descending puts newest first and locked last`() {
        val sorted = RaAchievementSorter.sort(raList, RaAchievementSortField.UNLOCK_TIME, SortDirection.DESC)
        assertEquals(listOf(2, 1, 3), raIds(sorted))
    }

    @Test
    fun `ra unlock time ascending puts oldest first and still keeps locked last`() {
        val sorted = RaAchievementSorter.sort(raList, RaAchievementSortField.UNLOCK_TIME, SortDirection.ASC)
        assertEquals(listOf(1, 2, 3), raIds(sorted))
    }

    @Test
    fun `ra won by ranks unknown player counts last when ascending`() {
        val list = listOf(
            ra(1, "Common", numAwarded = 900),
            ra(2, "Rare", numAwarded = 10),
            ra(3, "Unknown", numAwarded = null)
        )
        assertEquals(
            listOf(2, 1, 3),
            raIds(RaAchievementSorter.sort(list, RaAchievementSortField.WON_BY, SortDirection.ASC))
        )
        assertEquals(
            listOf(1, 2, 3),
            raIds(RaAchievementSorter.sort(list, RaAchievementSortField.WON_BY, SortDirection.DESC))
        )
    }

    @Test
    fun `ra title sort is case insensitive and reversible`() {
        val list = listOf(ra(1, "banjo"), ra(2, "Alpha"), ra(3, "Crash"))
        assertEquals(
            listOf(2, 1, 3),
            raIds(RaAchievementSorter.sort(list, RaAchievementSortField.TITLE, SortDirection.ASC))
        )
        assertEquals(
            listOf(3, 1, 2),
            raIds(RaAchievementSorter.sort(list, RaAchievementSortField.TITLE, SortDirection.DESC))
        )
    }

    @Test
    fun `ra display order and points flip with direction`() {
        val list = listOf(ra(1, "A", displayOrder = 2, points = 5), ra(2, "B", displayOrder = 1, points = 25))
        assertEquals(
            listOf(2, 1),
            raIds(RaAchievementSorter.sort(list, RaAchievementSortField.DISPLAY_ORDER, SortDirection.ASC))
        )
        assertEquals(
            listOf(1, 2),
            raIds(RaAchievementSorter.sort(list, RaAchievementSortField.DISPLAY_ORDER, SortDirection.DESC))
        )
        assertEquals(
            listOf(2, 1),
            raIds(RaAchievementSorter.sort(list, RaAchievementSortField.POINTS, SortDirection.DESC))
        )
    }

    @Test
    fun `ra default directions match the wording shown in the menu`() {
        assertEquals(SortDirection.DESC, RaAchievementSortField.UNLOCK_TIME.defaultDirection)
        assertEquals("Newest", RaAchievementSortField.UNLOCK_TIME.directionLabel(SortDirection.DESC))
        assertEquals("Oldest", RaAchievementSortField.UNLOCK_TIME.directionLabel(SortDirection.ASC))
        assertEquals(SortDirection.ASC, RaAchievementSortField.TITLE.defaultDirection)
        assertEquals("A-Z", RaAchievementSortField.TITLE.directionLabel(SortDirection.ASC))
    }

    // --- Steam ---

    private val steamOld = steam("old", achieved = true, unlockTime = 100L)
    private val steamNew = steam("new", achieved = true, unlockTime = 900L)
    private val steamLocked = steam("locked", achieved = false, unlockTime = null)
    private val steamList = listOf(steamOld, steamLocked, steamNew)

    @Test
    fun `steam unlock time keeps locked last in both directions`() {
        assertEquals(
            listOf("new", "old", "locked"),
            steamNames(SteamAchievementSorter.sort(steamList, SteamAchievementSortField.UNLOCK_TIME, SortDirection.DESC))
        )
        assertEquals(
            listOf("old", "new", "locked"),
            steamNames(SteamAchievementSorter.sort(steamList, SteamAchievementSortField.UNLOCK_TIME, SortDirection.ASC))
        )
    }

    @Test
    fun `steam rarity ascending is rarest first`() {
        val list = listOf(
            steam("common", globalPercent = 80.0),
            steam("rare", globalPercent = 2.5),
            steam("unknown", globalPercent = null)
        )
        assertEquals(
            listOf("rare", "common", "unknown"),
            steamNames(SteamAchievementSorter.sort(list, SteamAchievementSortField.RARITY, SortDirection.ASC))
        )
        assertEquals(
            listOf("common", "rare", "unknown"),
            steamNames(SteamAchievementSorter.sort(list, SteamAchievementSortField.RARITY, SortDirection.DESC))
        )
    }

    @Test
    fun `steam rarity wording says rarest and most common`() {
        assertEquals(SortDirection.ASC, SteamAchievementSortField.RARITY.defaultDirection)
        assertEquals("Rarest", SteamAchievementSortField.RARITY.directionLabel(SortDirection.ASC))
        assertEquals("Most common", SteamAchievementSortField.RARITY.directionLabel(SortDirection.DESC))
    }

    @Test
    fun `direction toggle flips both ways`() {
        assertEquals(SortDirection.DESC, SortDirection.ASC.toggle())
        assertEquals(SortDirection.ASC, SortDirection.DESC.toggle())
    }
}
