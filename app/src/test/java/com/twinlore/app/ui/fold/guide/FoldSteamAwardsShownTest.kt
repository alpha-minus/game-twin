package com.twinlore.app.ui.fold.guide

import com.twinlore.app.data.steam.SteamAchievementDisplay
import com.twinlore.app.domain.SortDirection
import com.twinlore.app.domain.SteamAchievementSortField
import org.junit.Assert.assertEquals
import org.junit.Test

/** What a Steam list shows: locked-only, then search, then the app's own sorter. */
class FoldSteamAwardsShownTest {

    private fun a(title: String, achieved: Boolean, description: String? = null, percent: Double? = null) =
        SteamAchievementDisplay(
            apiName = title, title = title, description = description, iconUrl = null, iconGrayUrl = null,
            achieved = achieved, unlockTime = if (achieved) 1L else null, hidden = false, globalPercent = percent,
        )

    private val all = listOf(
        a("Mom", achieved = true, percent = 60.0),
        a("Isaac", achieved = false, description = "Beat the Cathedral", percent = 10.0),
        a("Lamb", achieved = false, percent = 5.0),
    )

    @Test
    fun `locked only drops what is unlocked`() {
        val shown = foldSteamAwardsShown(all, lockedOnly = true, query = "", SteamAchievementSortField.TITLE, SortDirection.ASC)
        assertEquals(listOf("Isaac", "Lamb"), shown.map { it.title })
    }

    @Test
    fun `search matches title or description, case blind`() {
        assertEquals(
            listOf("Isaac"),
            foldSteamAwardsShown(all, false, "cathedral", SteamAchievementSortField.TITLE, SortDirection.ASC).map { it.title },
        )
        assertEquals(
            listOf("Lamb"),
            foldSteamAwardsShown(all, false, " LAMB ", SteamAchievementSortField.TITLE, SortDirection.ASC).map { it.title },
        )
    }

    @Test
    fun `sorted with the app's steam sorter`() {
        assertEquals(
            listOf("Lamb", "Isaac", "Mom"),
            foldSteamAwardsShown(all, false, "", SteamAchievementSortField.RARITY, SortDirection.ASC).map { it.title },
        )
    }
}
