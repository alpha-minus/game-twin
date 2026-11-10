package com.twinlore.app.journey.ui

import com.twinlore.app.journey.domain.LayoutEngine
import com.twinlore.app.journey.domain.model.AchievementType
import com.twinlore.app.journey.domain.model.AwardKind
import com.twinlore.app.journey.domain.model.AwardTally
import com.twinlore.app.journey.domain.model.GameRef
import com.twinlore.app.journey.domain.model.JourneyFrame
import com.twinlore.app.journey.domain.model.JourneyMode
import com.twinlore.app.journey.domain.model.PlayerJourney
import com.twinlore.app.journey.domain.model.Unlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The wording both strips share. The Compose chips and the export strip read these, so a change
 * here shows up in the player and in the shared video or image at once - which is the point of
 * their being here rather than in either.
 */
class JourneyStripTextTest {

    private val day = 86_400_000L

    private fun frameOf(
        mode: JourneyMode,
        totalAchievements: Int = 0,
        games: Int = 1,
        totalPoints: Int = 0,
    ): JourneyFrame {
        val unlocks = (1..3).map { i ->
            Unlock(
                id = i, title = "A$i", description = "", points = 10, badgeName = "b$i",
                type = AchievementType.STANDARD, earnedAtMillis = i * day, numAwarded = 5,
                gameId = (i % games) + 1,
            )
        }
        val refs = (1..games).associateWith {
            GameRef(
                id = it, title = "G$it", iconPath = null, artPaths = emptyList(), colorIndex = it,
                players = 10, award = null, totalAchievements = totalAchievements,
                totalPoints = totalPoints,
            )
        }
        return JourneyFrame(
            mode = mode,
            main = PlayerJourney("me", unlocks, refs, emptyList()),
            rivals = emptyList(),
            spanFromMillis = 0,
            spanToMillis = 4 * day,
        )
    }

    /** Two unlocks, one afternoon: the shortest journey `Ready` can carry. */
    private fun oneDayFrame(): JourneyFrame {
        val unlocks = (1..2).map { i ->
            Unlock(
                id = i, title = "A$i", description = "", points = 10, badgeName = "b$i",
                type = AchievementType.STANDARD, earnedAtMillis = day + i * 3_600_000L,
                numAwarded = 5, gameId = 1,
            )
        }
        val refs = mapOf(
            1 to GameRef(
                id = 1, title = "G1", iconPath = null, artPaths = emptyList(), colorIndex = 1,
                players = 10, award = null, totalAchievements = 40,
            ),
        )
        return JourneyFrame(
            mode = JourneyMode.GAME,
            main = PlayerJourney("me", unlocks, refs, emptyList()),
            rivals = emptyList(),
            spanFromMillis = 0,
            spanToMillis = 2 * day,
        )
    }

    /** A game cached before the total existed has nothing to divide by; the count alone is true. */
    @Test fun `an unknown achievement total leaves the counter undivided`() {
        assertEquals(0, JourneyStripText.totalAchievements(frameOf(JourneyMode.GAME)))
        assertEquals(40, JourneyStripText.totalAchievements(frameOf(JourneyMode.GAME, 40)))
        assertEquals(
            "All 3",
            headerOf(frameOf(JourneyMode.GAME), revealed = 3).achievements,
        )
    }

    /** The header at a point of playback, for the counting rules below. */
    private fun headerOf(frame: JourneyFrame, revealed: Int) = JourneyStripText.gameHeader(
        frame, LayoutEngine.layout(frame, includeNodes = true), 2 * day, revealed, points = 10,
    )

    /**
     * A solo single game gets its header rather than a row of counters, in the export exactly as on
     * the player: the game's name on one line, the counts and the date fused on the next.
     *
     * Two lines and two chips, not four counters on one baseline. The screen draws both headers
     * with the same `PlaybackHeader`, whose second row is a single `Text`, so the export's second
     * line is [GameHeader.countsLine] verbatim - the same string the screen lays out - rather than
     * label/value pairs carrying labels ("Achievements", "Points") the screen never writes.
     *
     * The game's name is a chip's VALUE with no label, so the plate draws it in the bright, large
     * run a label would not be - the way the screen's header draws the title.
     */
    @Test fun `the export strip carries the single-game header`() {
        val frame = frameOf(JourneyMode.GAME, totalAchievements = 40)
        val layout = LayoutEngine.layout(frame, includeNodes = true)
        val header = JourneyStripText.gameHeader(
            frame, layout, 2 * day, revealed = 2, points = 20,
            completed = false, awards = AwardTally(beaten = 1),
        )
        val chips = JourneyStripText.exportChips(
            frame = frame, layout = layout, journeyDateMillis = 2 * day,
            points = 20, awards = AwardTally(beaten = 1), revealed = 2, games = 1, completed = false, progress = 0.5f,
        )

        assertEquals(listOf("", "", ""), chips.map { it.label })
        assertEquals("a title line and a facts line", listOf(0, 1, 1), chips.map { it.line })
        assertEquals("G1", chips.first().value)
        assertTrue("the title is the header's bright run", chips.first().isTitle)
        // One chip per fact, in the screen's own wording, each in a fixed slot so a counter's
        // digits cannot shuffle the line. No cost while it is still playing.
        assertEquals("2 of 40 achievements", chips[1].value)
        assertEquals("20 of 30 points", chips[2].value)
        assertEquals(header.achievementsLine, chips[1].value)
        assertEquals(header.pointsLine, chips[2].value)
        chips.drop(1).forEach { assertTrue("every fact is slotted", it.slotDp > 0f) }
        // The running date is not among them any more; see the header's own test above.
        assertFalse(
            chips.any {
                it.value.contains(
                    JourneyStripText.whenLabel(frame, layout, 2 * day, completed = false),
                )
            },
        )
    }

    /**
     * The same shape the running span's header builds - a title over a line of slotted facts - so
     * neither can drift from the other.
     *
     * A running single game has exactly the span's two facts; its third slot is the cost, which
     * only exists once the journey has landed.
     */
    @Test fun `both playback headers stack a title over slotted facts`() {
        val frame = frameOf(JourneyMode.GAME, totalAchievements = 40)
        val layout = LayoutEngine.layout(frame, includeNodes = true)
        val game = JourneyStripText.gameHeaderChips(
            JourneyStripText.gameHeader(frame, layout, 2 * day, revealed = 2, points = 20),
            GAME_FACT_SLOTS,
        )
        val span = JourneyStripText.spanHeaderChips(
            JourneyStripText.spanHeader(
                whenLabel = "Aug 12", games = 3, gameTitle = "Zelda",
                achievements = 12, points = 240, awards = AwardTally(),
            ),
            SPAN_FACT_SLOTS,
        )

        assertEquals(span.map { it.line }, game.map { it.line })
        assertEquals(span.map { it.label }, game.map { it.label })
        assertEquals(span.map { it.isTitle }, game.map { it.isTitle })
        // And every fact on both is slotted - a chip with no slot is one that can still move.
        listOf("span" to span, "game" to game).forEach { (what, chips) ->
            chips.filter { !it.isTitle }.forEach {
                assertTrue("$what: \"${it.value}\" has no slot", it.slotDp > 0f)
            }
        }
    }

    /**
     * The header is the same object both surfaces read, so what the bar shows and what an exported
     * frame shows cannot drift. The percentage is the achievement list's, not playback's.
     */
    @Test fun `the single-game header counts the achievement list`() {
        val frame = frameOf(JourneyMode.GAME, totalAchievements = 40)
        val layout = LayoutEngine.layout(frame, includeNodes = true)

        // The points total is unknown here (no `totalPoints` on the fixture's `GameRef`), so its
        // denominator falls back to the unlock sum (30) - and 100 earned against that fallback is
        // exactly the stale-fraction case [gameHeader] guards against, so it reads "30 of 30" rather
        // than the impossible "100 of 30". See the guard tests below for the case on its own.
        val part = JourneyStripText.gameHeader(frame, layout, 2 * day, revealed = 10, points = 100)
        assertEquals("10 of 40", part.achievements)
        assertEquals("10 of 40 achievements", part.achievementsLine)
        assertEquals("30 of 30 points", part.pointsLine)
        assertEquals(25, part.percent)

        val all = JourneyStripText.gameHeader(frame, layout, 4 * day, revealed = 40, points = 300)
        assertEquals("All 40", all.achievements)
        assertEquals("All 40 achievements", all.achievementsLine)
        assertEquals(100, all.percent)
    }

    /** A game cached before the total existed divides by the player's own count instead of by zero. */
    @Test fun `a header with no known total falls back to the unlock count`() {
        val frame = frameOf(JourneyMode.GAME)
        val layout = LayoutEngine.layout(frame, includeNodes = true)
        val header = JourneyStripText.gameHeader(frame, layout, 3 * day, revealed = 3, points = 30)

        assertEquals("All 3", header.achievements)
        assertEquals(100, header.percent)
    }

    /**
     * Once the journey has landed the running counters have nothing left to say (the date is the
     * whole window, the points are the chart's own axis), so the strip swaps to what the journey
     * came to.
     */
    /**
     * A finished span says exactly four things, always the same four and always in the same order.
     * It used to lead with the game it spent the longest on and close with a site rank, which made a
     * five-counter row where two of the five were a truncated title and a number about the site.
     */
    @Test fun `a finished span names its games, achievements, awards and streak`() {
        val frame = spanFrame()
        val layout = LayoutEngine.layout(frame, includeNodes = true)
        val chips = JourneyStripText.exportChips(
            frame = frame, layout = layout, journeyDateMillis = 6 * day,
            points = 60, awards = AwardTally(mastered = 1, beaten = 1), revealed = 6, games = 2,
            completed = true, progress = 1f,
        )

        // No Points chip since the chart's ceiling line carries the exact total itself
        // - the strip repeating it was the redundancy.
        assertEquals(
            listOf("Achievements", "", "Games", "Longest streak"),
            chips.map { it.label },
        )
        assertEquals(listOf("6", "", "2", "4 days"), chips.map { it.value })
        // The awards counter is drawn rather than written; the words are what it is spoken as.
        assertEquals("1 mastered · 1 beaten", chips[1].spoken)
    }

    @Test fun `a journey that never repeated a day has no streak to report`() {
        assertEquals("-", JourneyStripText.streakValue(0))
        assertEquals("a run of one day is not a run", "-", JourneyStripText.streakValue(1))
        assertEquals("2 days", JourneyStripText.streakValue(2))
        assertEquals("9 days", JourneyStripText.streakValue(9))
    }

    /**
     * The counter is always in the row now - four chips, always the same four - and a journey with
     * nothing to report says so with a dash rather than by leaving a hole where a counter was.
     */
    @Test fun `a one day journey finishes on a dashed streak rather than none at all`() {
        val frame = oneDayFrame()
        val chips = JourneyStripText.endChips(frame, awards = AwardTally(), revealed = 2, games = 1, points = 30)

        // The achievements count leads now: the exact points total lives on the chart's own
        // labelled ceiling line rather than in the strip.
        assertEquals(JourneyStripText.ACHIEVEMENTS_LABEL, chips.first().label)
        assertEquals("2", chips.first().value)

        assertEquals("-", chips.single { it.label == JourneyStripText.STREAK_LABEL }.value)
    }

    @Test fun `days back to back are a streak and are named`() {
        val chips = JourneyStripText.endChips(spanFrame(), awards = AwardTally(mastered = 1), revealed = 6, games = 2, points = 60)

        assertEquals("4 days", chips.single { it.label == JourneyStripText.STREAK_LABEL }.value)
    }

    /**
     * The denominator comes off a validator-matched Room snapshot and the numerator off the live
     * unlock list, so a game whose achievement list has been trimmed since could print "43/40". A
     * fraction over one is not a thing a progress counter can mean.
     */
    @Test fun `a count cannot exceed the total it is out of`() {
        assertEquals("All 40", headerOf(frameOf(JourneyMode.GAME, 40), revealed = 43).achievements)
        assertEquals("39 of 40", headerOf(frameOf(JourneyMode.GAME, 40), revealed = 39).achievements)
        assertEquals(100, headerOf(frameOf(JourneyMode.GAME, 40), revealed = 43).percent)
    }

    /**
     * The points line used to divide by what the player had EARNED (`unlocks.sumOf { it.points }`),
     * so a finished game always read "342 of 342" - the numerator and its own denominator were the
     * same running total. The achievements grid and the RA drawer both read "342 of 656" for the
     * same game (device finding): the header's denominator has to be the game's own point total,
     * exactly as [totalAchievements] already is for the achievement line above it.
     */
    @Test fun `the single-game header divides points by the game's own total, not the running one`() {
        val frame = frameOf(JourneyMode.GAME, totalAchievements = 69, totalPoints = 656)
        val layout = LayoutEngine.layout(frame, includeNodes = true)

        // Mid-playback the denominator is already the game's total, not whatever has revealed so far.
        val running = JourneyStripText.gameHeader(frame, layout, 2 * day, revealed = 50, points = 342)
        assertEquals("342 of 656", running.points)

        // At the end the two numbers still differ - a finished game must NOT read "N of N".
        val ended = JourneyStripText.gameHeader(
            frame, layout, 4 * day, revealed = 69, points = 342, completed = true,
        )
        assertEquals("342 of 656", ended.points)
    }

    /**
     * The same guard [gameHeader] already applies to the achievement count: the denominator comes
     * off a validator-matched Room snapshot and the numerator off the running sweep, so a stale
     * total must not print a fraction over one.
     */
    @Test fun `a points count cannot exceed the total it is out of`() {
        val frame = frameOf(JourneyMode.GAME, totalPoints = 40)
        val layout = LayoutEngine.layout(frame, includeNodes = true)
        val header = JourneyStripText.gameHeader(frame, layout, 2 * day, revealed = 3, points = 43)
        assertEquals("40 of 40", header.points)
    }

    /**
     * A game cached before the points total existed has nothing to divide by; the running total -
     * today's behaviour - is the fallback, the same shape [totalAchievements]'s own fallback takes.
     */
    @Test fun `an unknown points total falls back to the running total`() {
        val frame = frameOf(JourneyMode.GAME) // totalPoints defaults to 0: unknown
        val layout = LayoutEngine.layout(frame, includeNodes = true)
        val header = JourneyStripText.gameHeader(frame, layout, 2 * day, revealed = 3, points = 30)
        assertEquals("30 of 30", header.points)
    }

    /**
     * A running span exports the player's own two-line header, not an approximation of it in
     * counters: the numbered game under the sweep on the first line, and the date, the achievements
     * and the points on the second, with the award dots after them.
     *
     * It used to be five label/value chips on one baseline - "Games 1 · Achievements 2 · Points 20
     * · Awards · Date ..." - which said the same facts in a shape the watcher never saw and left
     * the game the whole picture is of unnamed. What this pins is that both surfaces read the same
     * object: the chips' text is exactly what [JourneyStripText.spanHeader] built, and the player
     * hands `PlaybackHeader` the very same three fields.
     */
    @Test fun `a running span exports the player's two-line header`() {
        val frame = spanFrame()
        val layout = LayoutEngine.layout(frame, includeNodes = true)
        val awards = AwardTally(mastered = 1)
        val chips = JourneyStripText.exportChips(
            frame = frame, layout = layout, journeyDateMillis = 2 * day,
            points = 20, awards = awards, revealed = 2, games = 1, completed = false, progress = 0.5f,
            nowPlaying = "G1",
        )
        val header = JourneyStripText.spanHeader(
            whenLabel = JourneyStripText.whenLabel(frame, layout, 2 * day, completed = false),
            games = 1, gameTitle = "G1", achievements = 2, points = 20, awards = awards,
        )

        assertEquals("a title line and a facts line", listOf(0, 1, 1), chips.map { it.line })
        assertEquals("no line is a labelled counter", listOf("", "", ""), chips.map { it.label })
        assertEquals("1 - G1", chips[0].value)
        assertEquals("the title is the header's, drawn in the icon's ring colour", true, chips[0].isTitle)
        assertEquals(header.title, chips[0].value)
        // The screen's own two slots, in its order - and NO DATE. The player has never drawn one
        // here; this file built it into [SpanHeader.facts] and only the export showed it.
        assertEquals("2 achievements", chips[1].value)
        assertEquals("20 pts", chips[2].value)
        chips.drop(1).forEach {
            assertFalse(
                "the running date is off the header: \"${it.value}\"",
                it.value.contains(JourneyStripText.whenLabel(frame, layout, 2 * day, completed = false)),
            )
            assertTrue("every fact is slotted", it.slotDp > 0f)
        }
        assertEquals("the award dots ride the last fact", header.awards, chips[2].awards)
        assertTrue("the title line carries no dots of its own", chips[0].awards.isEmpty())
        assertTrue("nor does the first fact", chips[1].awards.isEmpty())
    }

    /**
     * A sweep that is between games, or on a chart whose track has no game under it, still gets a
     * first line - the player's own fallback, [JourneyStripText.GAMES_LABEL], rather than a blank
     * where the game's name goes.
     */
    @Test fun `a running span with no game under the sweep still names its line`() {
        val frame = spanFrame()
        val layout = LayoutEngine.layout(frame, includeNodes = true)
        val chips = JourneyStripText.exportChips(
            frame = frame, layout = layout, journeyDateMillis = 2 * day,
            points = 20, awards = AwardTally(), revealed = 2, games = 3, completed = false, progress = 0.5f,
        )
        assertEquals("3 - ${JourneyStripText.GAMES_LABEL}", chips[0].value)
    }

    /**
     * The closing site rank is off the end row entirely. A rank is a fact about the whole site, and
     * a solo journey's end has four counters about the journey itself; where a rank still belongs is
     * on a compare row, beside the player it ranks.
     */
    @Test fun `a solo end row carries no rank, whatever it is handed`() {
        val frame = spanFrame()
        val layout = LayoutEngine.layout(frame, includeNodes = true)
        val chips = JourneyStripText.exportChips(
            frame = frame, layout = layout, journeyDateMillis = 6 * day,
            points = 60, awards = AwardTally(mastered = 1, beaten = 1), revealed = 6, games = 2,
            completed = true, progress = 1f,
            ranks = JourneyStripText.Ranks(closing = RankChip(RANK_LABEL, "#812")),
        )
        assertTrue("no chip names a rank", chips.none { it.value.contains("#") })
        assertEquals(4, chips.size)
    }

    /**
     * A shared picture of a compare chart has to be the picture that was watched: the same lines
     * per player, at every point of playback rather than only at the end.
     */
    @Test fun `an exported compare chart draws the comparison, not one player's counters`() {
        val frame = compareFrame()
        val layout = LayoutEngine.layout(frame, includeNodes = false)
        val ranks = JourneyStripText.Ranks(perPlayer = mapOf("me" to 4, "rival" to 91))

        // At the end of playback the cut is a no-op, so this pins that the compare path is taken
        // whether or not the journey has landed rather than that it agrees with itself at one
        // instant - the sweep's own cut is pinned separately, below. The rank is the one thing
        // that DOES depend on `completed` rather than on the cut - see the test below.
        listOf(false, true).forEach { completed ->
            val chips = JourneyStripText.exportChips(
                frame = frame, layout = layout, journeyDateMillis = 3 * day,
                points = 30, awards = AwardTally(), revealed = 2, games = 1, completed = completed,
                progress = 1f,
                ranks = ranks,
            )
            assertEquals(listOf("me", "rival"), chips.map { it.label })
            // The screen's own facts string, and the screen's own split: the rank is beside them,
            // not inside them. See `a compare row's facts and rank are separate, in both modes`.
            assertEquals("30 pts · 2 achievements", chips.first().value)
        }
    }

    /**
     * The rank is a spoiler: a single-game compare chart is a race, and naming where it ENDS from
     * the first frame told the watcher who won before the curves had finished climbing. It has to
     * wait for [JourneyStripText.exportChips]'s own `completed` flag - the same "has this landed"
     * signal every other end-state fact in this file already waits for - rather than riding along
     * with `ranks` from frame one.
     */
    @Test fun `a running single-game compare row carries no rank, and a landed one does`() {
        val frame = compareFrame()
        val layout = LayoutEngine.layout(frame, includeNodes = false)
        val ranks = JourneyStripText.Ranks(perPlayer = mapOf("me" to 4, "rival" to 91))

        val running = JourneyStripText.exportChips(
            frame = frame, layout = layout, journeyDateMillis = 2 * day,
            points = 10, awards = AwardTally(), revealed = 1, games = 1, completed = false,
            progress = 0.3f, ranks = ranks,
        )
        assertTrue("a running row is a spoiler-free row", running.all { it.rank == null })

        val landed = JourneyStripText.exportChips(
            frame = frame, layout = layout, journeyDateMillis = 3 * day,
            points = 30, awards = AwardTally(), revealed = 2, games = 1, completed = true,
            progress = 1f, ranks = ranks,
        )
        assertEquals("the rival's rank arrives once the journey lands", "Rank #91", landed[1].rank)
    }

    /**
     * A single-game compare chart used to show its final totals from the first frame to the last,
     * on screen and in a shared clip alike. The whole point of the mode is who was ahead WHEN.
     */
    @Test fun `a single-game compare chart's chips move with the sweep`() {
        val frame = compareFrame()
        val layout = LayoutEngine.layout(frame, includeNodes = false)
        // The start of the race: each player is one unlock in, their own first, which is where
        // their curve starts too.
        val early = JourneyStripText.exportChips(
            frame = frame, layout = layout, journeyDateMillis = 2 * day,
            points = 10, awards = AwardTally(), revealed = 1, games = 1, completed = false, progress = 0f,
        )
        // And the end of it, where every player has their whole run.
        val late = JourneyStripText.exportChips(
            frame = frame, layout = layout, journeyDateMillis = 7 * day,
            points = 30, awards = AwardTally(), revealed = 2, games = 1, completed = true, progress = 1f,
        )
        assertNotEquals("the compare rows must not read the same at both ends", early, late)
        assertEquals("one row per player at both ends", early.size, late.size)
    }

    /**
     * A single-game compare chart is a RACE: every player's x is time since their OWN first
     * unlock, so the shared sweep says "this much of your run" to each of them separately and the
     * calendar has nothing to do with it.
     *
     * The rows were cut at one absolute instant instead - `stats.journeyDateMillis`, the MAIN
     * player's last revealed unlock - which is a fact about one player's watch applied to
     * everybody's. A rival who played the game a year LATER than the main player had every unlock
     * of theirs filtered for the whole clip, and `compareLines` drops a player with no unlocks, so
     * their row vanished from the strip: from the running frames and from the landed state a still
     * export captures. That is what this pins - every player who is on the chart is on the strip,
     * at every point of playback.
     */
    @Test fun `a rival who played the game years from the main player keeps their row`() {
        val frame = offsetCompareFrame()
        val layout = LayoutEngine.layout(frame, includeNodes = false)

        (0..20).forEach { step ->
            val p = step / 20f
            val chips = JourneyStripText.exportChips(
                frame = frame, layout = layout, journeyDateMillis = 402 * day,
                points = 30, awards = AwardTally(), revealed = 2, games = 1, completed = p >= 1f,
                progress = p,
            )
            assertEquals(
                "every player on the chart has a row at progress $p",
                listOf("me", "before", "after"),
                chips.map { it.label },
            )
        }
    }

    /**
     * And the other half of the same bug: a rival who played the game EARLIER than the main player
     * had none of their unlocks filtered at all, so their row read their FINAL totals from the
     * first frame while their curve was still being drawn - they had finished before the race
     * started.
     *
     * The invariant both halves come from: a player's row agrees with their own curve at every
     * point of playback. At the start of the race that means one unlock each, each player's own
     * first, which is where their curve starts.
     */
    @Test fun `no player's row shows totals the sweep has not drawn yet`() {
        val frame = offsetCompareFrame()
        val layout = LayoutEngine.layout(frame, includeNodes = false)

        val start = JourneyStripText.exportChips(
            frame = frame, layout = layout, journeyDateMillis = 400 * day,
            points = 10, awards = AwardTally(), revealed = 1, games = 1, completed = false,
            progress = 0f,
        ).associate { it.label to it.value }
        val end = JourneyStripText.exportChips(
            frame = frame, layout = layout, journeyDateMillis = 402 * day,
            points = 30, awards = AwardTally(), revealed = 2, games = 1, completed = true,
            progress = 1f,
        ).associate { it.label to it.value }

        listOf("me" to "10 pts", "before" to "5 pts", "after" to "7 pts").forEach { (who, score) ->
            assertEquals(
                "$who starts the race on their own first unlock, not on their totals",
                "$score · 1 achievement",
                start.getValue(who),
            )
        }
        listOf("me" to "30 pts", "before" to "20 pts", "after" to "28 pts").forEach { (who, score) ->
            assertEquals("$who finishes on their whole run", "$score · 2 achievements", end.getValue(who))
        }
    }

    /**
     * A single-game compare chart answers a different question from the generic counters: not
     * "where are we now" but "how did these players do against each other".
     */
    @Test fun `the compare row gives every player a duration, a score and a rank`() {
        val frame = compareFrame()
        val lines = JourneyStripText.compareLines(frame, mapOf("me" to 4, "rival" to 91))

        assertEquals(listOf("me", "rival"), lines.map { it.name })
        assertTrue("the first line is the main player's", lines.first().isMain)
        assertEquals(listOf("2d", "1d"), lines.map { it.duration })
        assertEquals(listOf(30, 20), lines.map { it.points })
        assertEquals(listOf("Rank #4", "Rank #91"), lines.map { it.rank })
    }

    /**
     * The exported strip has to be able to colour a player's line the way the live compare row
     * does, and the only thing it needs for that is which slot in the roster the line belongs to.
     * Without it a shared clip carried a colour-coded chart over a colourless legend.
     */
    @Test fun `compare chips carry each player's roster slot`() {
        val chips = JourneyStripText.compareChips(compareFrame(), emptyMap())

        assertEquals(listOf("me", "rival"), chips.map { it.label })
        assertEquals("the main player's slot is -1", listOf(-1, 0), chips.map { it.rivalIndex })
    }

    /**
     * On one game every player's row carries their own completion percent (owner request): the
     * same fraction the solo header's bar draws, per player, so a race shows how far through the
     * LIST each player is beside how many points that got them. The denominator is the game's own
     * achievement count - shared by every player on the chart, it is one game.
     */
    @Test fun `compare lines on one game carry each player's completion percent`() {
        val lines = JourneyStripText.compareLines(compareFrame(), emptyMap())
        // 2 unlocks each of a 9-achievement game: 22% both.
        assertEquals(listOf(22, 22), lines.map { it.percent })
    }

    @Test fun `the hardcore share rides the compare percent like the solo header's gold layer`() {
        val frame = compareFrame()
        val hardcoreRival = frame.rivals.first().let { r ->
            r.copy(unlocks = r.unlocks.map { it.copy(hardcore = true) })
        }
        val lines = JourneyStripText.compareLines(frame.copy(rivals = listOf(hardcoreRival)), emptyMap())

        assertEquals(listOf(0, 22), lines.map { it.hardcorePercent })
    }

    /** A span has no single achievement list to be a fraction of: no percent, no bar. */
    @Test fun `compare lines over a span carry no percent`() {
        val lines = JourneyStripText.compareLines(spanCompareFrame(), emptyMap())
        assertTrue(lines.all { it.percent == null })
    }

    /** A snapshot cached before the total existed: better no bar than a made-up denominator. */
    @Test fun `an unknown achievement total means no percent rather than a lie`() {
        val noTotal = compareFrame().let { frame ->
            val games = mapOf(1 to GameRef(1, "G1", null, emptyList(), 0, 10, null))
            frame.copy(
                main = frame.main.copy(games = games),
                rivals = frame.rivals.map { it.copy(games = games) },
            )
        }
        val lines = JourneyStripText.compareLines(noTotal, emptyMap())
        assertTrue(lines.all { it.percent == null })
    }

    /**
     * On one game a row shows only the HIGHEST award (owner request): mastering a game implies
     * beating it, so "mastered + beaten" dots on the same row said one fact twice. A span keeps
     * the full breakdown - there forty games earned a mix, and the counts are the point.
     */
    @Test fun `a single-game compare row shows only the highest award`() {
        val line = JourneyStripText.PlayerLine(
            name = "me", isMain = true, rivalIndex = -1, duration = "2d", points = 30, rank = null,
            mode = JourneyMode.GAME, games = 1, achievements = 2,
            awards = AwardTally(mastered = 1, beaten = 1), streakDays = 0,
        )
        assertEquals(listOf(AwardKind.MASTERED), JourneyStripText.compareAwards(line).map { it.kind })
    }

    @Test fun `a span compare row keeps its whole award breakdown`() {
        val line = JourneyStripText.PlayerLine(
            name = "me", isMain = true, rivalIndex = -1, duration = "2d", points = 30, rank = null,
            mode = JourneyMode.SPAN, games = 4, achievements = 9,
            awards = AwardTally(mastered = 2, beaten = 3), streakDays = 0,
        )
        assertEquals(
            listOf(AwardKind.MASTERED, AwardKind.BEATEN),
            JourneyStripText.compareAwards(line).map { it.kind },
        )
    }

    /** The drawn strip needs the same number the live row shows; it travels on the chip. */
    @Test fun `compare chips carry the percent to the export`() {
        val chips = JourneyStripText.compareChips(compareFrame(), emptyMap())
        assertEquals(listOf(22, 22), chips.map { it.percent })
    }

    /** An ordinary counter has no player, and must not be coloured as if it did. */
    @Test fun `an ordinary counter chip has no player`() {
        val frame = frameOf(JourneyMode.SPAN)
        val layout = LayoutEngine.layout(frame, includeNodes = true)
        val chips = JourneyStripText.exportChips(
            frame = frame, layout = layout, journeyDateMillis = 2 * day,
            points = 30, awards = AwardTally(), revealed = 2, games = 1, completed = false, progress = 0.5f,
        )
        assertTrue("no generic counter may claim a player", chips.all { it.rivalIndex == null })
    }

    /**
     * One point is "1 pt", not "1 pts". The compare row appended a bare "pts" to whatever the score
     * was, while the chart's own tooltip for the same number said "1 pt".
     */
    @Test fun `a one point run reads in the singular`() {
        val line = JourneyStripText.PlayerLine(
            name = "me", isMain = true, rivalIndex = -1, duration = "2d", points = 1, rank = null,
            mode = JourneyMode.GAME, games = 1, achievements = 1, awards = AwardTally(), streakDays = 0,
        )
        assertEquals("1 pt · 1 achievement", JourneyStripText.compareLineFacts(line))
        assertEquals(
            "2 pts · 1 achievement",
            JourneyStripText.compareLineFacts(line.copy(points = 2)),
        )
    }

    /**
     * A compare row is one line and two numbers - the score and the unlocks it took - with the
     * awards drawn beside them and the site rank at the end of the line, in BOTH modes and on both
     * surfaces.
     *
     * The rank being apart from the facts is the point of the second half of this: the exported row
     * used to fold it into the same string, which is a sentence the watcher never saw and one long
     * enough that the column ellipsized it ("alphami...", "R..."). It travels on the chip's own
     * [JourneyStripText.Chip.rank] now, so the drawing can right-align it the way
     * `PlayerCompareLine` does.
     */
    @Test fun `a compare row's facts and rank are separate, in both modes`() {
        val spanLines = JourneyStripText.compareLines(spanCompareFrame(), mapOf("me" to 4))

        assertEquals("60 pts · 6 achievements", JourneyStripText.compareLineFacts(spanLines[0]))
        assertEquals("20 pts · 2 achievements", JourneyStripText.compareLineFacts(spanLines[1]))
        assertEquals("60 pts", JourneyStripText.comparePoints(spanLines[0]))

        listOf(
            "a span" to JourneyStripText.compareChips(spanCompareFrame(), mapOf("me" to 4)),
            "one game" to JourneyStripText.compareChips(compareFrame(), mapOf("me" to 4)),
        ).forEach { (what, chips) ->
            assertEquals("$what: the rank is not in the facts", listOf("Rank #4", null), chips.map { it.rank })
            chips.forEach {
                assertFalse("$what: '${it.value}' must not carry the rank", it.value.contains("Rank"))
            }
        }
    }

    @Test fun `a player with no rank yet still gets their line`() {
        val lines = JourneyStripText.compareLines(compareFrame(), emptyMap())
        assertEquals(2, lines.size)
        assertNull(lines.first().rank)
    }

    @Test fun `a race chart's when chip reads as elapsed time`() {
        val frame = frameOf(JourneyMode.SPAN)
        val layout = LayoutEngine.layout(frame, includeNodes = true)
        assertEquals("Date", JourneyStripText.whenChipLabel(layout))
    }

    /** Two games, one of them played across a week, and four days running at the start of it. */
    // --- the compact end row, and the comparison at the end of a compare span ---

    /**
     * UI feedback: a span with somebody else on it finishes on the per-player rows alone. The
     * journey's own totals used to stack above them as a second block of cards, which put one
     * player's counters at the top of a chart whose whole point is who did what against whom -
     * and on a handheld the two blocks together were most of the screen.
     */
    @Test fun `a compare span ends on the per-player comparison alone`() {
        val frame = spanCompareFrame()
        val layout = LayoutEngine.layout(frame, includeNodes = true)

        val chips = JourneyStripText.exportChips(
            frame = frame, layout = layout, journeyDateMillis = 6 * day,
            points = 60, awards = AwardTally(), revealed = 6, games = 2, completed = true, progress = 1f,
        )

        assertEquals(listOf("me", "rival"), chips.map { it.label })
        assertEquals("the main player's row keeps its own slot", listOf(-1, 0), chips.map { it.rivalIndex })
        assertTrue(
            "every row carries its player's own score",
            chips.all { it.value.contains("pts") },
        )
    }

    /** A solo span still ends on its own totals: there is nobody to compare it with. */
    /** The site rank a compare span's rows carry comes off the same summary call as the spellings. */
    @Test fun `a compare span's rows carry the site ranks they were given`() {
        val frame = spanCompareFrame()
        val layout = LayoutEngine.layout(frame, includeNodes = true)

        val chips = JourneyStripText.exportChips(
            frame = frame, layout = layout, journeyDateMillis = 6 * day,
            points = 60, awards = AwardTally(), revealed = 6, games = 2, completed = true, progress = 1f,
            ranks = JourneyStripText.Ranks(perPlayer = mapOf("me" to 4, "rival" to 91)),
        )

        // On the chip's own field, not fused into its facts: the row draws it right-aligned at the
        // end of the line, which a string inside the value could not be.
        val rows = chips.takeLast(2)
        assertEquals("the player's own row names their site rank", "Rank #4", rows[0].rank)
        assertEquals("and the rival's names theirs", "Rank #91", rows[1].rank)
    }

    /** A rank that never arrived is a row without it rather than a row that lies about it. */
    @Test fun `a span row with no rank simply omits it`() {
        val frame = spanCompareFrame()
        val layout = LayoutEngine.layout(frame, includeNodes = true)

        val chips = JourneyStripText.exportChips(
            frame = frame, layout = layout, journeyDateMillis = 6 * day,
            points = 60, awards = AwardTally(), revealed = 6, games = 2, completed = true, progress = 1f,
        )

        assertTrue(
            "no row claims a rank, in its own field or fused into its facts",
            chips.takeLast(2).none { it.rank != null || it.value.contains("Rank") },
        )
    }

    /**
     * A running compare span used to say only "who scored what" while it played, and save "how"
     * (the unlocks it took, the awards taken along the way) for the end - which made the
     * comparison the mode exists for incomplete for most of the clip. [comparePlaybackChips]
     * carries the same two extra facts a landed row always has, and moves them with the sweep the
     * way the score already does.
     *
     * The rank stays off even so: it is not a running fact, it is where the journey lands, and this
     * pins that adding the other two did not smuggle it back in.
     */
    @Test fun `a running compare span row carries unlocks and awards, not just points`() {
        val frame = spanCompareFrame()
        val layout = LayoutEngine.layout(frame, includeNodes = true)

        val chips = JourneyStripText.exportChips(
            frame = frame, layout = layout, journeyDateMillis = 5 * day,
            points = 60, awards = AwardTally(mastered = 1), revealed = 3, games = 2, completed = false,
            progress = 0.9f,
            rivalPoints = listOf(20), rivalRevealed = listOf(2), rivalAwards = listOf(AwardTally(beaten = 1)),
            ranks = JourneyStripText.Ranks(perPlayer = mapOf("me" to 4, "rival" to 91)),
        )

        val mine = chips.first { it.rivalIndex == -1 }
        assertEquals("the running row states the achievements behind the score", "60 pts · 3 achievements", mine.value)
        assertEquals(listOf(AwardKind.MASTERED), mine.awards.map { it.kind })

        val theirs = chips.first { it.rivalIndex == 0 }
        assertEquals("20 pts · 2 achievements", theirs.value)
        assertEquals(listOf(AwardKind.BEATEN), theirs.awards.map { it.kind })

        assertTrue("no rank while it plays, even with one on hand", chips.none { it.rank != null })
    }

    @Test fun `a solo span still ends on its totals`() {
        val frame = spanFrame()
        val layout = LayoutEngine.layout(frame, includeNodes = true)

        val chips = JourneyStripText.exportChips(
            frame = frame, layout = layout, journeyDateMillis = 6 * day,
            points = 60, awards = AwardTally(mastered = 1, beaten = 1), revealed = 6, games = 2,
            completed = true, progress = 1f,
        )

        assertEquals(
            listOf("Achievements", "", "Games", "Longest streak"),
            chips.map { it.label },
        )
    }

    // --- what a finished single game cost, and the awards breakdown ---

    /**
     * At the end, the running date has nothing left to say - the sweep has stopped on the last day,
     * which the axis under it already ends on - so the header's second line becomes what the game
     * cost: the hours RetroAchievements has the player down for. With no award to name them, the
     * hours are plainly "Played".
     */
    @Test fun `a finished single game header names the playtime`() {
        val frame = playedFrame(playtimeSeconds = 42 * 3600 + 10 * 60)
        val layout = LayoutEngine.layout(frame, includeNodes = true)

        val header = JourneyStripText.gameHeader(
            frame, layout, 3 * day, revealed = 3, points = 30, completed = true,
        )

        assertEquals("Played", header.dateLabel)
        assertEquals("42h 10m", header.dateValue)
        assertEquals("Played 42h 10m", header.date)
        assertFalse(
            "the run of days belongs to the axis, not to this line",
            header.date.contains("days"),
        )
    }

    /** With an award, the award is what names the hours: "Mastered in 42h 10m". */
    @Test fun `a finished single game header names its award and the hours it took`() {
        val frame = playedFrame(playtimeSeconds = 42 * 3600 + 10 * 60)
        val layout = LayoutEngine.layout(frame, includeNodes = true)

        val mastered = JourneyStripText.gameHeader(
            frame, layout, 3 * day, revealed = 3, points = 30, completed = true,
            awards = AwardTally(mastered = 1),
        )
        assertEquals("Mastered in", mastered.dateLabel)
        assertEquals("42h 10m", mastered.dateValue)
        assertEquals("Mastered in 42h 10m", mastered.date)

        // The highest award is the one that names them, never a lesser one it came with.
        val both = JourneyStripText.gameHeader(
            frame, layout, 3 * day, revealed = 3, points = 30, completed = true,
            awards = AwardTally(completed = 1, beaten = 1),
        )
        assertEquals("Completed in 42h 10m", both.date)

        val beaten = JourneyStripText.gameHeader(
            frame, layout, 3 * day, revealed = 3, points = 30, completed = true,
            awards = AwardTally(beaten = 1),
        )
        assertEquals("Beaten in 42h 10m", beaten.date)
    }

    /**
     * A game cached before `UserTotalPlaytime` was threaded through has no hours to name, and there
     * is no second call to be made for them: the line keeps its date rather than inventing an hour
     * or repeating the run of days the chart's own axis is labelled with.
     */
    @Test fun `a header with no playtime claims no hours`() {
        val frame = playedFrame(playtimeSeconds = null)
        val layout = LayoutEngine.layout(frame, includeNodes = true)

        val header = JourneyStripText.gameHeader(
            frame, layout, 3 * day, revealed = 3, points = 30, completed = true,
            awards = AwardTally(mastered = 1),
        )

        assertEquals(JourneyStripText.DATE_LABEL, header.dateLabel)
        assertFalse("nothing may claim hours nobody knows", header.date.contains("h "))
        assertFalse("and nothing repeats the axis", header.date.contains("days"))
    }

    /** While it plays, the same line is the running date: the cost is an end-state fact. */
    @Test fun `a running single game header keeps its date`() {
        val frame = playedFrame(playtimeSeconds = 3600)
        val layout = LayoutEngine.layout(frame, includeNodes = true)

        val header = JourneyStripText.gameHeader(frame, layout, 2 * day, revealed = 2, points = 20)

        assertEquals("Date", header.dateLabel)
        assertEquals(
            JourneyStripText.whenLabel(frame, layout, 2 * day, completed = false),
            header.dateValue,
        )
    }

    /** The exported strip carries the same end line, split into a panel's label and value. */
    @Test fun `the export strip carries the finished header's cost line`() {
        val frame = playedFrame(playtimeSeconds = 7200)
        val layout = LayoutEngine.layout(frame, includeNodes = true)

        val chips = JourneyStripText.exportChips(
            frame = frame, layout = layout, journeyDateMillis = 3 * day,
            points = 30, awards = AwardTally(mastered = 1), revealed = 3, games = 1, completed = true, progress = 1f,
        )

        assertEquals("G1", chips.first().value)
        // The cost is the third fact on the facts line, and only exists once the journey has landed
        // - exactly as the screen's header gains it.
        assertEquals("a title and three facts", 4, chips.size)
        assertEquals("Mastered in 2h 0m", chips.last().value)
        assertEquals("it sits on the facts line", 1, chips.last().line)
        assertTrue("and it has a slot of its own", chips.last().slotDp > 0f)
    }

    @Test fun `playtime reads as hours and minutes, and drops an empty hour`() {
        assertEquals("42h 10m", JourneyStripText.fmtPlaytime(42 * 3600 + 10 * 60))
        assertEquals("1h 0m", JourneyStripText.fmtPlaytime(3600))
        assertEquals("35m", JourneyStripText.fmtPlaytime(35 * 60))
        assertEquals("0m", JourneyStripText.fmtPlaytime(0))
    }

    /**
     * The span rounds to the unit a person would say: hours inside
     * a day, whole days under two months, months under a year, then years with leftover months.
     * "364 days, 2 hours" was pedantically true and useless; it reads "1 year" now.
     */
    @Test fun `a span rounds to a friendly unit`() {
        val hour = 3_600_000L
        assertEquals("5 hours", JourneyStripText.fmtSpanDaysHours(5 * hour))
        assertEquals("0 hours", JourneyStripText.fmtSpanDaysHours(0))
        assertEquals("1 day", JourneyStripText.fmtSpanDaysHours(day))
        // Hours no longer ride along once there is a day to talk about.
        assertEquals("21 days", JourneyStripText.fmtSpanDaysHours(21 * day + 4 * hour))
        assertEquals("59 days", JourneyStripText.fmtSpanDaysHours(59 * day))
        assertEquals("2 months", JourneyStripText.fmtSpanDaysHours(61 * day))
        assertEquals("3 months", JourneyStripText.fmtSpanDaysHours(92 * day))
        assertEquals("6 months", JourneyStripText.fmtSpanDaysHours(183 * day))
        assertEquals("1 year", JourneyStripText.fmtSpanDaysHours(364 * day + 2 * hour))
        assertEquals("1 year", JourneyStripText.fmtSpanDaysHours(365 * day))
        assertEquals("1 year, 3 months", JourneyStripText.fmtSpanDaysHours(455 * day))
        assertEquals("2 years", JourneyStripText.fmtSpanDaysHours(730 * day))
    }

    /** The breakdown names the kinds that happened, best first, and leaves out the ones that did not. */
    @Test fun `an award breakdown drops the kinds nobody earned`() {
        val parts = JourneyStripText.awardParts(AwardTally(mastered = 3, beaten = 2))

        assertEquals(listOf(AwardKind.MASTERED, AwardKind.BEATEN), parts.map { it.kind })
        assertEquals(listOf(3, 2), parts.map { it.count })
        assertEquals("3 mastered · 2 beaten", JourneyStripText.awardsWords(parts))
    }

    /**
     * A journey that has not reached its first award still needs the counter to be there: a chip
     * that appeared partway through would move every counter beside it.
     */
    @Test fun `an empty tally keeps a counter reading zero`() {
        val chip = JourneyStripText.awardsChip(AwardTally())

        assertEquals(JourneyStripText.AWARDS_LABEL, chip.label)
        assertEquals("0", chip.value)
        assertTrue(chip.awards.isEmpty())
    }

    /** With awards to draw, the dots replace both the label and the value, and speak for them. */
    @Test fun `an awards chip draws its kinds and speaks them`() {
        val chip = JourneyStripText.awardsChip(AwardTally(mastered = 1, completed = 2, beaten = 4))

        assertEquals("", chip.label)
        assertEquals("", chip.value)
        assertEquals(3, chip.awards.size)
        assertEquals("1 mastered · 2 completed · 4 beaten", chip.spoken)
    }

    /** A compare row's awards are dots beside its facts, and never words inside them. */
    @Test fun `a compare row hands its awards to the dots`() {
        val line = JourneyStripText.PlayerLine(
            name = "me", isMain = true, rivalIndex = -1, duration = "2d", points = 30, rank = null,
            mode = JourneyMode.SPAN, games = 2, achievements = 6,
            awards = AwardTally(mastered = 1, beaten = 1), streakDays = 4,
        )

        assertFalse("no award words in the facts", JourneyStripText.compareLineFacts(line).contains("award"))
        assertEquals(
            listOf(AwardKind.MASTERED, AwardKind.BEATEN),
            JourneyStripText.compareAwards(line).map { it.kind },
        )
    }

    /**
     * One game yields one award per player, so the count beside its dot can only ever read "1".
     * A span's rows keep theirs: "20 mastered" is the fact that row exists to carry.
     */
    @Test fun `a single game's award dots carry no count`() {
        assertEquals(false, JourneyStripText.awardCountsShown(JourneyMode.GAME))
        assertEquals(true, JourneyStripText.awardCountsShown(JourneyMode.SPAN))
    }

    /**
     * The mode never reaches [ExportStrip] directly - the chip is what travels there - so the
     * suppression has to ride on the chip itself, decided here where the frame's mode is still in
     * hand. A single-game compare chart's rows carry the flag switched off; a span's compare rows
     * (the end state of a compare span, see [endChipsOrCompare]) keep it on.
     */
    @Test fun `compare chips carry whether their dots may show a count`() {
        val gameChips = JourneyStripText.compareChips(compareFrame(), emptyMap())
        assertTrue(
            "one game's rows show no award count",
            gameChips.all { !it.awardCountsShown },
        )

        val spanChips = JourneyStripText.compareChips(spanCompareFrame(), emptyMap())
        assertTrue(
            "a span's rows keep their award count",
            spanChips.all { it.awardCountsShown },
        )
    }

    /**
     * UI feedback: the running span's second line leads with where in time the sweep is - the
     * date-time the first line used to carry - then the achievements and the points. The game
     * count moved into the title ("3 - Zelda"), so this line lost its "N games" and the date came
     * back down to sit with the counts.
     */
    @Test fun `the span playback line leads with the date, then achievements and points`() {
        assertEquals(
            "Jun 14 · 12 achievements · 120 pts",
            JourneyStripText.spanPlaybackFacts(whenLabel = "Jun 14", achievements = 12, points = 120),
        )
        assertEquals(
            "everything singular reads singular",
            "Jun 14 06:00 · 1 achievement · 1 pt",
            JourneyStripText.spanPlaybackFacts(whenLabel = "Jun 14 06:00", achievements = 1, points = 1),
        )
    }

    /** The running game count rides the title: "3 - Zelda" is the third game the sweep has entered. */
    @Test fun `the running span title carries the game count`() {
        assertEquals("3 - Zelda", JourneyStripText.numberedTitle(3, "Zelda"))
        assertEquals("1 - Games", JourneyStripText.numberedTitle(1, "Games"))
    }

    /**
     * The single-game header's second line while it runs: how far through the list and what that is
     * worth, under a first line that is the game's name alone.
     *
     * NO DATE (user request). The header still knows one - [GameHeader.date] is what the running
     * readout says - but the line does not carry it: on a followed chart the corner readout is
     * where the sweep's date lives, and this was the same fact twice.
     */
    @Test fun `the running single-game header names its achievements and its points, and no date`() {
        val frame = frameOf(JourneyMode.GAME, totalAchievements = 40)
        val layout = LayoutEngine.layout(frame, includeNodes = true)

        val header = JourneyStripText.gameHeader(frame, layout, 2 * day, revealed = 10, points = 100)

        // As above: no known points total on this fixture, so the fallback denominator is the
        // unlock sum (30), and the guard coerces 100 down to it.
        assertEquals("10 of 40 achievements · 30 of 30 points", header.countsLine)
        assertTrue(
            "the achievements come first",
            header.countsLine.indexOf("achievements") < header.countsLine.indexOf("points"),
        )
        // The date is still known - it is simply not on this line any more.
        assertTrue(header.date.isNotEmpty())
        assertFalse("the running date is off the line", header.countsLine.contains(header.date))
        assertNull("and there is no cost until it lands", header.cost)
    }

    /**
     * Once it lands, what the game cost joins the line. That is not a date and nothing else on the
     * frame says it, so it is the one thing in the old date slot that stays.
     */
    @Test fun `the landed single-game header names what the game cost`() {
        val frame = playedFrame(playtimeSeconds = 7200)
        val layout = LayoutEngine.layout(frame, includeNodes = true)

        val header = JourneyStripText.gameHeader(
            frame, layout, 3 * day, revealed = 3, points = 30,
            completed = true, awards = AwardTally(mastered = 1),
        )

        assertEquals("Mastered in 2h 0m", header.cost)
        assertTrue(header.countsLine.endsWith("· Mastered in 2h 0m"))
    }

    /**
     * The bar's two metals, on one denominator: the whole revealed count in silver, the hardcore
     * share of it in gold over the top - the RA achievement tab's own dual bar
     * (`RetroAchievementDrawer`, `AchievementsBoard`), which this mirrors so the same game reads the
     * same way in both places.
     */
    @Test fun `the header counts the hardcore share on the same total`() {
        val frame = frameOf(JourneyMode.GAME, totalAchievements = 40)
        val layout = LayoutEngine.layout(frame, includeNodes = true)

        val header = JourneyStripText.gameHeader(
            frame, layout, 2 * day, revealed = 20, points = 100, hardcore = 10,
        )

        assertEquals(50, header.percent)
        assertEquals(25, header.hardcorePercent)

        val softcore = JourneyStripText.gameHeader(frame, layout, 2 * day, revealed = 20, points = 100)
        assertEquals("no hardcore, no gold", 0, softcore.hardcorePercent)
    }

    /** The gold may never run past the silver, however the two counts were sourced. */
    @Test fun `the hardcore share cannot exceed the count it is part of`() {
        val frame = frameOf(JourneyMode.GAME, totalAchievements = 40)
        val layout = LayoutEngine.layout(frame, includeNodes = true)

        val header = JourneyStripText.gameHeader(
            frame, layout, 2 * day, revealed = 10, points = 100, hardcore = 40,
        )

        assertEquals(25, header.percent)
        assertEquals(25, header.hardcorePercent)
    }

    /**
     * The percentage and the award are the trailing block's, once and nowhere else.
     *
     * Both used to ride the title chip as well - "Kirby 100% 1 mastered" beside a bar that then
     * drew the same 100% under it, on every frame of every solo single-game export. The plate
     * draws the percentage under its bar and the award as the same bare circle the screen's header
     * draws (see `ExportStrip.BarState.award`), so the chips carry neither: the highest award the
     * block draws is [JourneyStripText.awardParts]'s first, which is the one this used to put into
     * words.
     */
    @Test fun `the exported single-game chips name the percentage and the award nowhere`() {
        val frame = frameOf(JourneyMode.GAME, totalAchievements = 40)
        val layout = LayoutEngine.layout(frame, includeNodes = true)
        val awards = AwardTally(mastered = 1, beaten = 1)

        val chips = JourneyStripText.exportChips(
            frame = frame, layout = layout, journeyDateMillis = 2 * day,
            points = 20, awards = awards, revealed = 2, games = 1,
            completed = false, progress = 0.5f,
        )

        assertTrue("no chip carries an award", chips.all { it.awards.isEmpty() })
        assertTrue(
            "and no chip carries the percentage the bar's own block draws",
            chips.none { it.value.endsWith("%") },
        )
        // What the plate draws instead, from the same tally: the best award, one of them.
        assertEquals(
            listOf(AwardKind.MASTERED),
            JourneyStripText.awardParts(awards).take(1).map { it.kind },
        )
    }

    /** Two unlocks two days apart, in a game with a known playtime. */
    private fun playedFrame(playtimeSeconds: Int?): JourneyFrame {
        val unlocks = (1..3).map { i ->
            Unlock(
                id = i, title = "A$i", description = "", points = 10, badgeName = "b$i",
                type = AchievementType.STANDARD, earnedAtMillis = i * day, numAwarded = 5, gameId = 1,
            )
        }
        val games = mapOf(
            1 to GameRef(
                id = 1, title = "G1", iconPath = null, artPaths = emptyList(), colorIndex = 0,
                players = 10, award = null, totalAchievements = 3, playtimeSeconds = playtimeSeconds,
            ),
        )
        return JourneyFrame(
            mode = JourneyMode.GAME,
            main = PlayerJourney("me", unlocks, games, emptyList()),
            rivals = emptyList(),
            spanFromMillis = 0,
            spanToMillis = 4 * day,
        )
    }

    private fun spanFrame(): JourneyFrame {
        val unlocks = listOf(
            u(1, 1 * day, gameId = 1), u(2, 1 * day + 3_600_000, gameId = 1),
            u(3, 2 * day, gameId = 2), u(4, 3 * day, gameId = 2), u(5, 6 * day, gameId = 2),
            u(6, 4 * day, gameId = 1),
        )
        val games = mapOf(
            1 to GameRef(1, "G1", null, emptyList(), 0, 10, null),
            2 to GameRef(2, "G2", null, emptyList(), 1, 10, null),
        )
        return JourneyFrame(
            mode = JourneyMode.SPAN,
            main = PlayerJourney("me", unlocks.sortedBy { it.earnedAtMillis }, games, emptyList()),
            rivals = emptyList(),
            spanFromMillis = 0, spanToMillis = 7 * day,
        )
    }

    /** One game, two players, different runs at it. */
    private fun compareFrame(): JourneyFrame {
        val games = mapOf(1 to GameRef(1, "G1", null, emptyList(), 0, 10, null, totalAchievements = 9))
        val main = PlayerJourney(
            "me",
            listOf(u(1, 1 * day, points = 10), u(2, 3 * day, points = 20)),
            games, emptyList(),
        )
        val rival = PlayerJourney(
            "rival",
            listOf(u(3, 2 * day, points = 5), u(4, 3 * day, points = 15)),
            games, emptyList(),
        )
        return JourneyFrame(JourneyMode.GAME, main, listOf(rival), 0, 7 * day)
    }

    /**
     * The same one game, played by three people YEARS apart - which is the ordinary shape of a
     * head-to-head on a game, and the shape every fixture above quietly avoided by putting both
     * players inside the same three days.
     *
     * "before" ran at it a year before the main player and "after" a year after, so nothing about
     * the main player's calendar says anything true about either of them. Each run is its own
     * length (2 days, 1 day, 3 days), so the race is a race.
     */
    private fun offsetCompareFrame(): JourneyFrame {
        val year = 365 * day
        val games = mapOf(1 to GameRef(1, "G1", null, emptyList(), 0, 10, null, totalAchievements = 9))
        val main = PlayerJourney(
            "me",
            listOf(u(1, 400 * day, points = 10), u(2, 402 * day, points = 20)),
            games, emptyList(),
        )
        val before = PlayerJourney(
            "before",
            listOf(u(3, 400 * day - year, points = 5), u(4, 401 * day - year, points = 15)),
            games, emptyList(),
        )
        val after = PlayerJourney(
            "after",
            listOf(u(5, 400 * day + year, points = 7), u(6, 403 * day + year, points = 21)),
            games, emptyList(),
        )
        return JourneyFrame(JourneyMode.GAME, main, listOf(before, after), 0, 0)
    }

    /** Two games over a week, two players: a compare SPAN, which has no leaderboard to rank on. */
    private fun spanCompareFrame(): JourneyFrame {
        val base = spanFrame()
        val rival = PlayerJourney(
            "rival",
            listOf(u(7, 2 * day, points = 5, gameId = 1), u(8, 5 * day, points = 15, gameId = 2)),
            base.main.games, emptyList(),
        )
        return base.copy(rivals = listOf(rival))
    }

    private fun u(id: Int, at: Long, points: Int = 10, gameId: Int = 1) = Unlock(
        id = id, title = "A$id", description = "", points = points, badgeName = "b$id",
        type = AchievementType.STANDARD, earnedAtMillis = at, numAwarded = 5, gameId = gameId,
    )
}
