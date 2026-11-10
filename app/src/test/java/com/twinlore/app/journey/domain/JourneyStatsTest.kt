package com.twinlore.app.journey.domain

import com.twinlore.app.journey.domain.model.AchievementType
import com.twinlore.app.journey.domain.model.Unlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The journey's own arithmetic: which day an unlock belongs to, which badge speaks for that day on
 * a compare chart, how long the player kept turning up, and which game held them longest.
 *
 * All four decide something visible - two of them decide what is drawn - so the tie-breaks are
 * pinned as hard as the answers.
 */
class JourneyStatsTest {

    private val day = JourneyStats.DAY_MILLIS

    private fun unlock(
        id: Int,
        at: Long,
        points: Int = 5,
        gameId: Int = 1,
    ) = Unlock(
        id = id, title = "A$id", description = "", points = points, badgeName = "b$id",
        type = AchievementType.STANDARD, earnedAtMillis = at, numAwarded = 10, gameId = gameId,
    )

    // ---- days ----

    @Test fun `a day is whole days since the epoch, before it as well as after`() {
        assertEquals(0L, JourneyStats.dayOf(0L))
        assertEquals(0L, JourneyStats.dayOf(day - 1))
        assertEquals(1L, JourneyStats.dayOf(day))
        assertEquals(-1L, JourneyStats.dayOf(-1L))
    }

    // ---- one badge per day ----

    @Test fun `each day elects its highest scorer`() {
        val unlocks = listOf(
            unlock(1, 10 * day + 1_000, points = 5),
            unlock(2, 10 * day + 2_000, points = 50),
            unlock(3, 10 * day + 3_000, points = 25),
            unlock(4, 11 * day + 1_000, points = 1),
        )
        assertEquals(listOf(1, 3), JourneyStats.dailyBadgeIndices(unlocks))
    }

    @Test fun `a tie on points goes to the earlier unlock, then to the lower id`() {
        val equalTimes = listOf(
            unlock(9, 4 * day + 5_000, points = 10),
            unlock(2, 4 * day + 5_000, points = 10),
        )
        assertEquals(listOf(1), JourneyStats.dailyBadgeIndices(equalTimes))

        val differentTimes = listOf(
            unlock(1, 4 * day + 9_000, points = 10),
            unlock(2, 4 * day + 1_000, points = 10),
        )
        assertEquals(listOf(1), JourneyStats.dailyBadgeIndices(differentTimes))
    }

    @Test fun `every day with an unlock on it is represented exactly once`() {
        val unlocks = (0 until 40).map { unlock(it, (it / 3) * day + it * 60_000L, points = it % 7) }
        val picked = JourneyStats.dailyBadgeIndices(unlocks)
        val days = unlocks.map { JourneyStats.dayOf(it.earnedAtMillis) }.distinct()

        assertEquals(days.size, picked.size)
        assertEquals(
            days.sorted(),
            picked.map { JourneyStats.dayOf(unlocks[it].earnedAtMillis) }.sorted(),
        )
        assertEquals("the picks must be ascending", picked.sorted(), picked)
    }

    @Test fun `an empty journey elects nothing`() {
        assertEquals(emptyList<Int>(), JourneyStats.dailyBadgeIndices(emptyList()))
    }

    // ---- streaks ----

    @Test fun `three consecutive days is a streak and two is not`() {
        val two = listOf(unlock(1, 3 * day), unlock(2, 4 * day))
        assertTrue(JourneyStats.streaks(two).isEmpty())

        val three = listOf(unlock(1, 3 * day), unlock(2, 4 * day), unlock(3, 5 * day))
        val run = JourneyStats.streaks(three).single()
        assertEquals(3, run.days)
        assertEquals(3 * day, run.startMillis)
        assertEquals(5 * day, run.endMillis)
    }

    @Test fun `a streak's bounds are its first and last unlock, not the day boundaries`() {
        val unlocks = listOf(
            unlock(1, 3 * day + 7_200_000),
            unlock(2, 4 * day + 100),
            unlock(3, 5 * day + 60_000),
            unlock(4, 5 * day + 3_600_000),
        )
        val run = JourneyStats.streaks(unlocks).single()
        assertEquals(3 * day + 7_200_000, run.startMillis)
        assertEquals(5 * day + 3_600_000, run.endMillis)
    }

    @Test fun `a gap ends a run and a later run is reported separately`() {
        val unlocks = listOf(
            unlock(1, 1 * day), unlock(2, 2 * day), unlock(3, 3 * day),
            unlock(4, 9 * day), unlock(5, 10 * day),
            unlock(6, 20 * day), unlock(7, 21 * day), unlock(8, 22 * day), unlock(9, 23 * day),
        )
        val runs = JourneyStats.streaks(unlocks)
        assertEquals(2, runs.size)
        assertEquals(3, runs[0].days)
        assertEquals(4, runs[1].days)
        assertTrue("runs must be in order", runs[0].startMillis < runs[1].startMillis)
    }

    @Test fun `several unlocks on one day are still one day of the run`() {
        val unlocks = listOf(
            unlock(1, 1 * day), unlock(2, 1 * day + 1_000), unlock(3, 1 * day + 2_000),
            unlock(4, 2 * day),
        )
        assertTrue(JourneyStats.streaks(unlocks).isEmpty())
        assertEquals(2, JourneyStats.longestStreakDays(unlocks))
    }

    @Test fun `unordered unlocks give the same runs as ordered ones`() {
        val ordered = listOf(unlock(1, 5 * day), unlock(2, 6 * day), unlock(3, 7 * day))
        assertEquals(JourneyStats.streaks(ordered), JourneyStats.streaks(ordered.reversed()))
    }

    @Test fun `the longest run counts a lone day as one and nothing as zero`() {
        assertEquals(0, JourneyStats.longestStreakDays(emptyList()))
        assertEquals(1, JourneyStats.longestStreakDays(listOf(unlock(1, 4 * day))))
        assertEquals(
            4,
            JourneyStats.longestStreakDays(
                listOf(
                    unlock(1, 1 * day), unlock(2, 2 * day),
                    unlock(3, 8 * day), unlock(4, 9 * day), unlock(5, 10 * day), unlock(6, 11 * day),
                ),
            ),
        )
    }

    // ---- longest played game ----

    @Test fun `the longest played game is the one whose unlocks are furthest apart`() {
        val unlocks = listOf(
            unlock(1, 0, gameId = 1), unlock(2, 2 * day, gameId = 1),
            unlock(3, 0, gameId = 2), unlock(4, 30 * day, gameId = 2),
            unlock(5, 0, gameId = 3), unlock(6, 60_000, gameId = 3), unlock(7, 120_000, gameId = 3),
        )
        assertEquals(2, JourneyStats.longestPlayedGameId(unlocks))
    }

    @Test fun `an equal span goes to the game with more unlocks, then to the lower id`() {
        val moreUnlocks = listOf(
            unlock(1, 0, gameId = 7), unlock(2, 5 * day, gameId = 7),
            unlock(3, 0, gameId = 8), unlock(4, 2 * day, gameId = 8), unlock(5, 5 * day, gameId = 8),
        )
        assertEquals(8, JourneyStats.longestPlayedGameId(moreUnlocks))

        val identical = listOf(
            unlock(1, 0, gameId = 4), unlock(2, 5 * day, gameId = 4),
            unlock(3, 0, gameId = 9), unlock(4, 5 * day, gameId = 9),
        )
        assertEquals(4, JourneyStats.longestPlayedGameId(identical))
    }

    @Test fun `a journey with nothing in it has no longest game and no span`() {
        assertNull(JourneyStats.longestPlayedGameId(emptyList()))
        assertNull(JourneyStats.playerSpanMillis(emptyList()))
        assertEquals(5 * day, JourneyStats.playerSpanMillis(listOf(unlock(1, 0), unlock(2, 5 * day))))
    }
}
