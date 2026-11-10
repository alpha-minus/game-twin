package com.twinlore.app.journey.domain

import com.twinlore.app.journey.domain.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JourneyBuilderTest {
    private val D = 86_400_000L
    private val base = 1_760_000_000_000L                     // inside window
    private fun ts(millis: Long): String =                    // SQL shape for parseWhen
        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .format(java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneOffset.UTC))

    private fun ach(id: Int, earned: Long?, earnedHc: Long? = null, pts: Int = 10, type: String? = null) =
        AchSnapshot(id, "A$id", "", pts, "b$id", type, earned?.let(::ts), earnedHc?.let(::ts), 5)
    private fun snap(gameId: Int, achs: List<AchSnapshot>) =
        GameSnapshot(gameId, "G$gameId", "/i.png", listOf("/a.png"), 100, achs)
    private fun row(gameId: Int, lastAward: Long?, soft: Int = 1, hc: Int = 0, awardKind: String? = null, awardAt: Long? = null) =
        CompletionRow(gameId, "G$gameId", "/i.png", soft, hc, lastAward?.let(::ts), awardKind, awardAt?.let(::ts))

    @Test fun `pickSpanGames orders in-window desc then after-window asc and drops zero-award`() {
        val from = base; val to = base + 30 * D
        val rows = listOf(
            row(1, base + 5 * D), row(2, base + 20 * D), row(3, base + 40 * D),
            row(4, base + 35 * D), row(5, base + 10 * D, soft = 0), row(6, base - 5 * D),
        )
        val picked = JourneyBuilder.pickSpanGames(rows, from, to)
        assertEquals(listOf(2, 1, 4, 3), picked.map { it.gameId })
    }

    @Test fun `deriveUnlocks includes every earned achievement and filters the window`() {
        val s = snap(1, listOf(
            ach(1, earned = base + D),
            ach(2, earned = base + 40 * D),               // outside window
            ach(3, earned = null, earnedHc = base + 2 * D),
            ach(4, earned = null),                        // never earned
        ))
        val inWindow = JourneyBuilder.deriveUnlocks(s, windowFilter = true, base, base + 30 * D)
        assertEquals(listOf(1, 3), inWindow.map { it.id })
        val noFilter = JourneyBuilder.deriveUnlocks(s, windowFilter = false, 0, 0)
        assertEquals(listOf(1, 3, 2), noFilter.map { it.id })   // ascending by time
    }

    /**
     * One rule, no mode: softcore-only and hardcore unlocks are all on the chart, and an
     * achievement earned both ways lands on its hardcore date - "if hardcore exists, that version".
     */
    @Test fun `deriveUnlocks prefers the hardcore date and flags the unlock`() {
        val s = snap(1, listOf(
            ach(1, earned = base + D),                                  // softcore only
            ach(2, earned = base + 2 * D, earnedHc = base + 5 * D),      // both, hardcore later
            ach(3, earned = null, earnedHc = base + 3 * D),              // hardcore only
        ))
        val unlocks = JourneyBuilder.deriveUnlocks(s, windowFilter = true, base, base + 30 * D)

        assertEquals(listOf(1, 3, 2), unlocks.map { it.id })            // ordered by the earn used
        assertEquals(base + D, unlocks.single { it.id == 1 }.earnedAtMillis)
        assertEquals(base + 5 * D, unlocks.single { it.id == 2 }.earnedAtMillis)
        assertEquals(base + 3 * D, unlocks.single { it.id == 3 }.earnedAtMillis)
        assertEquals(listOf(false, true, true), unlocks.map { it.hardcore })
    }

    /**
     * A hardcore field in a shape parseWhen does not know must not cost the player the unlock: the
     * softcore date is still there, and the field that failed to parse is the one saying they
     * earned it twice.
     */
    @Test fun `deriveUnlocks falls back to the softcore date when the hardcore one will not parse`() {
        val s = snap(1, listOf(
            AchSnapshot(1, "A1", "", 10, "b1", null, ts(base + D), "not a date", 5),
            AchSnapshot(2, "A2", "", 10, "b2", null, null, "not a date either", 5),
        ))
        val unlocks = JourneyBuilder.deriveUnlocks(s, windowFilter = true, base, base + 30 * D)

        assertEquals(listOf(1), unlocks.map { it.id })
        assertEquals(base + D, unlocks[0].earnedAtMillis)
    }

    @Test fun `milestones mastered suppresses completed`() {
        val allHc = snap(1, listOf(ach(1, base, base + D), ach(2, base, base + 2 * D)))
        val m = JourneyBuilder.computeGameMilestones(allHc)
        assertEquals(listOf(AwardKind.MASTERED), m.map { it.first })
        assertEquals(base + 2 * D, m[0].second)
    }

    @Test fun `milestones completed when all softcore only`() {
        val s = snap(1, listOf(ach(1, base), ach(2, base + D)))
        val m = JourneyBuilder.computeGameMilestones(s)
        assertEquals(listOf(AwardKind.COMPLETED), m.map { it.first })
    }

    /**
     * Beating a game softcore is the site's `beaten-softcore` - the hollow silver circle - and
     * beating it with every one of those achievements in hardcore is `beaten-hardcore`. The
     * milestone used to be the hardcore form either way, which drew a casual beat as though it
     * had been earned the hard way.
     */
    @Test fun `milestones beaten from progression and win condition, hardcore or not`() {
        val softcore = snap(1, listOf(
            ach(1, base + D, type = "progression"),
            ach(2, base + 3 * D, type = "win_condition"),
            ach(3, null, type = "standard"),               // unearned standard doesn't block beaten
        ))
        val m = JourneyBuilder.computeGameMilestones(softcore)
        assertEquals(listOf(AwardKind.BEATEN_CASUAL), m.map { it.first })
        assertEquals(base + 3 * D, m[0].second)            // max(progression latest, win earliest)

        val hardcore = snap(1, listOf(
            ach(1, base + D, earnedHc = base + D, type = "progression"),
            ach(2, base + 3 * D, earnedHc = base + 3 * D, type = "win_condition"),
            ach(3, null, type = "standard"),
        ))
        assertEquals(
            listOf(AwardKind.BEATEN),
            JourneyBuilder.computeGameMilestones(hardcore).map { it.first },
        )
    }

    @Test fun `buildPlayer span drops empty games assigns colors and medals in window`() {
        val from = base; val to = base + 30 * D
        val snaps = listOf(
            snap(1, listOf(ach(1, base + D))),
            snap(2, listOf(ach(2, base + 40 * D))),        // all outside window -> dropped
            snap(3, listOf(ach(3, base + 2 * D))),
        )
        val completion = mapOf(
            1 to row(1, base + D, awardKind = "mastered", awardAt = base + 5 * D),
            3 to row(3, base + 2 * D, awardKind = "beaten-softcore", awardAt = base + 40 * D), // award outside window
        )
        val p = JourneyBuilder.buildPlayer("me", JourneyMode.SPAN, from, to, snaps, completion, failedFetches = 1)
        assertEquals(setOf(1, 3), p.games.keys)
        assertEquals(listOf(1, 3), p.unlocks.map { it.id })
        assertEquals(1, p.medals.size)                     // game 3's award is outside the window
        assertEquals(AwardKind.MASTERED, p.medals[0].kind)
        assertEquals(1, p.failedFetches)
        assertTrue(p.games.values.map { it.colorIndex }.all { it in 0..11 })
    }

    // The fact cards ask a GameRef for its title screen, so the snapshot's copy has to survive the
    // build in both modes rather than stopping at the cache.
    @Test fun `buildPlayer carries the title and boxart paths onto the game`() {
        val snapshot = snap(1, listOf(ach(1, base + D)))
            .copy(titlePath = "/title.png", boxArtPath = "/box.png")

        val span = JourneyBuilder.buildPlayer(
            "me", JourneyMode.SPAN, base, base + 30 * D, listOf(snapshot), emptyMap(), 0,
        )
        assertEquals("/title.png", span.games.getValue(1).titlePath)
        assertEquals("/box.png", span.games.getValue(1).boxArtPath)

        val game = JourneyBuilder.buildPlayer(
            "me", JourneyMode.GAME, 0, 0, listOf(snapshot), emptyMap(), 0,
        )
        assertEquals("/title.png", game.games.getValue(1).titlePath)
        assertEquals("/box.png", game.games.getValue(1).boxArtPath)
    }

    /**
     * The denominator of the single-game strip's "x of N": how many achievements the game has, not
     * how many were earned. Only one of these three was.
     */
    @Test fun `buildPlayer carries the game's whole achievement count`() {
        val snapshot = snap(1, listOf(ach(1, base + D), ach(2, null), ach(3, null)))

        val game = JourneyBuilder.buildPlayer(
            "me", JourneyMode.GAME, 0, 0, listOf(snapshot), emptyMap(), 0,
        )
        assertEquals(1, game.unlocks.size)
        assertEquals(3, game.games.getValue(1).totalAchievements)

        val span = JourneyBuilder.buildPlayer(
            "me", JourneyMode.SPAN, base, base + 30 * D, listOf(snapshot), emptyMap(), 0,
        )
        assertEquals(3, span.games.getValue(1).totalAchievements)
    }
}
