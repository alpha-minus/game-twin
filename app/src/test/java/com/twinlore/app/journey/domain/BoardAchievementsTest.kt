package com.twinlore.app.journey.domain

import com.twinlore.app.journey.domain.model.JourneyMode
import com.twinlore.app.journey.domain.model.GameSnapshot
import com.twinlore.app.journey.domain.model.AchSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The full badge list a single-game journey hands the achievements grid: everything the game has,
 * marked by whether this player has it. The journey's own frame keeps only the earned ones, so a
 * grid built from that would be a grid of what is already done.
 */
class BoardAchievementsTest {

    private fun ach(
        id: Int,
        badge: String,
        earned: String? = null,
        hardcore: String? = null,
        points: Int = 5,
    ) = AchSnapshot(
        id = id, title = "Ach $id", description = "", points = points, badgeName = badge,
        type = null, dateEarned = earned, dateEarnedHardcore = hardcore, numAwarded = 100,
    )

    private fun game(id: Int, achievements: List<AchSnapshot>) = GameSnapshot(
        gameId = id, title = "Game $id", iconPath = null, artPaths = emptyList(),
        players = 1, achievements = achievements,
    )

    @Test fun `every achievement is listed, earned or not`() {
        val snapshot = game(7, listOf(ach(1, "a", earned = "2026-01-01 00:00:00"), ach(2, "b")))
        val list = BoardAchievements.forGame(JourneyMode.GAME, 7, listOf(snapshot))
        assertEquals(2, list.size)
        assertEquals("a", list[0].badgeName)
        assertTrue("an earned achievement is earned", list[0].earned)
        assertEquals("b", list[1].badgeName)
        assertEquals("a locked one is not", false, list[1].earned)
    }

    /** Hardcore alone still counts as earned: it is an earn in the stricter mode, not a third state. */
    @Test fun `a hardcore-only earn counts as earned`() {
        val snapshot = game(7, listOf(ach(1, "a", hardcore = "2026-01-01 00:00:00")))
        assertTrue(BoardAchievements.forGame(JourneyMode.GAME, 7, listOf(snapshot))[0].earned)
    }

    /** Hardcore is the stricter half of "earned", not a third state - and the board rings it. */
    @Test fun `a hardcore earn is marked hardcore, a softcore one is not`() {
        val snapshot = game(
            7,
            listOf(
                ach(1, "hard", hardcore = "2026-01-01 00:00:00"),
                ach(2, "soft", earned = "2026-01-01 00:00:00"),
                ach(3, "locked"),
            ),
        )
        val list = BoardAchievements.forGame(JourneyMode.GAME, 7, listOf(snapshot))
        assertTrue("a hardcore earn is earned", list[0].earned)
        assertTrue("and is marked hardcore", list[0].hardcore)
        assertTrue("a softcore earn is earned", list[1].earned)
        assertEquals("but is not hardcore", false, list[1].hardcore)
        assertEquals("a locked one is neither", false, list[2].earned)
        assertEquals(false, list[2].hardcore)
    }

    /**
     * Every achievement's points come along, locked ones included: the board's points line needs
     * what the whole game is worth ("305 of 570 points"), which is the one number a journey has
     * nowhere else - `GameRef` counts achievements and the unlocks only add up to what was taken.
     */
    @Test fun `a locked achievement still carries what it is worth`() {
        val snapshot = game(
            7,
            listOf(
                ach(1, "a", earned = "2026-01-01 00:00:00", points = 10),
                ach(2, "b", points = 25),
            ),
        )
        val list = BoardAchievements.forGame(JourneyMode.GAME, 7, listOf(snapshot))

        assertEquals(35, list.sumOf { it.points })
        assertEquals("the locked one's points are not zeroed", 25, list[1].points)
    }

    /** The journey's game, not whichever snapshot happens to be first. */
    @Test fun `the right game's list is picked`() {
        val snapshots = listOf(game(1, listOf(ach(1, "wrong"))), game(7, listOf(ach(2, "right"))))
        assertEquals("right", BoardAchievements.forGame(JourneyMode.GAME, 7, snapshots)[0].badgeName)
    }

    /** A span is about many games; there is no one achievement list for the grid to be of. */
    @Test fun `a span has no board`() {
        val snapshot = game(7, listOf(ach(1, "a")))
        assertEquals(emptyList<Any>(), BoardAchievements.forGame(JourneyMode.SPAN, 7, listOf(snapshot)))
    }

    /** Nothing to show is an empty list rather than a guess at another game's badges. */
    @Test fun `an unknown game has no board`() {
        val snapshot = game(7, listOf(ach(1, "a")))
        assertEquals(emptyList<Any>(), BoardAchievements.forGame(JourneyMode.GAME, null, listOf(snapshot)))
        assertEquals(emptyList<Any>(), BoardAchievements.forGame(JourneyMode.GAME, 99, listOf(snapshot)))
        assertEquals(emptyList<Any>(), BoardAchievements.forGame(JourneyMode.GAME, 7, emptyList()))
    }
}
