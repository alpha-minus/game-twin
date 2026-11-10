package com.twinlore.app.ui.fold.guide

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.twinlore.app.data.retroachievements.RaAchievementDisplay
import com.twinlore.app.domain.RaAchievementSortField
import com.twinlore.app.domain.RaAchievementTypeFilter
import com.twinlore.app.domain.SortDirection
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A tap on the achievements surface brings the panel's bars back.
 *
 * It used to be a tap on an achievement row and nothing else, so once the bar
 * was hidden the header, the filter strip, the empty states and every gap
 * between rows were all dead ground: on a filtered-to-nothing or unlinked
 * vault there was no way at all to get the bar back.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class FoldAchievementsChromeTapTest {

    @get:Rule
    val compose = createComposeRule()

    private var chromeToggles = 0
    private var lockedOnlyToggles = 0

    private fun render(awards: FoldAwards) {
        compose.setContent {
            FoldAchievementsContent(
                awards = awards,
                loading = false,
                refreshing = false,
                typeFilter = RaAchievementTypeFilter.ALL,
                lockedOnly = false,
                sortField = RaAchievementSortField.UNLOCK_TIME,
                sortDirection = SortDirection.DESC,
                steamSortField = com.twinlore.app.domain.SteamAchievementSortField.UNLOCK_TIME,
                steamSortDirection = SortDirection.DESC,
                commentsFor = null,
                comments = emptyList(),
                commentsLoading = false,
                onRefresh = {},
                onCycleTypeFilter = {},
                onToggleLockedOnly = { lockedOnlyToggles++ },
                onCycleSort = {},
                onToggleSortDirection = {},
                onShowComments = {},
                onDismissComments = {},
                onBlockedLink = {},
                searchQuery = "",
                onSearchChanged = {},
                onWantsKeyboard = {},
                onToggleChrome = { chromeToggles++ },
                onOpenApp = {},
                scroll = null,
                onScrollChanged = { _, _ -> },
            )
        }
    }

    @Test
    fun `a tap on the empty message moves the bar`() {
        render(FoldAwards(linked = true))
        compose.onNodeWithText(FOLD_AWARDS_NONE).performClick()
        assertEquals(1, chromeToggles)
    }

    @Test
    fun `a tap on the header moves the bar`() {
        render(FoldAwards(linked = true, unlocked = 3, total = 10, points = 30, totalPoints = 100))
        compose.onNodeWithText("3/10 · 30/100 pts").performClick()
        assertEquals(1, chromeToggles)
    }

    @Test
    fun `a tap on a filter pill filters and leaves the bar alone`() {
        render(FoldAwards(linked = true))
        compose.onNodeWithText(FOLD_AWARDS_LOCKED_ONLY).performClick()
        assertEquals(1, lockedOnlyToggles)
        assertEquals(0, chromeToggles)
    }

    @Test
    fun `a tap on a row moves the bar once, not twice`() {
        render(FoldAwards(linked = true, achievements = listOf(anAchievement)))
        compose.onNodeWithText("Slay the first boss").performClick()
        assertEquals(1, chromeToggles)
    }

    @Test
    fun `a scroll is not a tap`() {
        render(FoldAwards(linked = true, achievements = List(30) { anAchievement.copy(id = it) }))
        compose.onNodeWithText(FOLD_AWARDS_LOCKED_ONLY).performTouchInput { swipeUp() }
        assertEquals(0, chromeToggles)
    }

    private val anAchievement = RaAchievementDisplay(
        id = 1,
        title = "Slay the first boss",
        description = "",
        points = 5,
        // Blank on purpose: a badge name is a CDN URL, and this test is about
        // where a tap lands, not about what Coil does with a network image.
        badgeName = "",
        rarityPercent = null,
        dateAwarded = null,
        hardcoreAchieved = false,
    )
}
