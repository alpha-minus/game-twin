package com.twinlore.app.journey.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.twinlore.app.journey.domain.LayoutEngine
import com.twinlore.app.journey.domain.model.AchievementType
import com.twinlore.app.journey.domain.model.Award
import com.twinlore.app.journey.domain.model.AwardKind
import com.twinlore.app.journey.domain.model.GameRef
import com.twinlore.app.journey.domain.model.JourneyFrame
import com.twinlore.app.journey.domain.model.JourneyMode
import com.twinlore.app.journey.domain.model.Medal
import com.twinlore.app.journey.domain.model.PlayerJourney
import com.twinlore.app.journey.domain.model.Unlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The compare span's two bars, at the width they actually get: a landscape phone.
 *
 * Both replace furniture that did not fit. While it played, the strip carried one player's running
 * counters - the wrong four numbers for a chart with three curves on it - and at the end a `FlowRow`
 * of two-line chips, four counters and a rank each, which wrapped to two lines per player and then
 * wrapped again onto a second row, so the awards on the last chip were regularly off the bottom of
 * the screen.
 *
 * Robolectric's font metrics are not a device's, so nothing here reads an absolute text width. What
 * is asserted is structural and survives fake metrics: one line per player, every line the same
 * height, nothing past the right edge of the bar, and every player's score on screen however long
 * their name is.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ComparePlayerBarTest {

    @get:Rule
    val rule = createComposeRule()

    private val day = 86_400_000L

    /** A landscape phone, which is the narrowest thing this bar has to fit. */
    private val landscape = 640.dp

    /**
     * Three players over a fortnight, with names as long as RetroAchievements lets them be: the
     * case the wrapped chips could not hold.
     */
    private fun spanCompareFrame(): JourneyFrame {
        val games = mapOf(
            1 to GameRef(1, "Game One", null, emptyList(), 0, 10, Award(AwardKind.MASTERED, 2 * day)),
            2 to GameRef(2, "Game Two", null, emptyList(), 1, 10, null),
        )
        fun player(name: String, points: Int, medals: List<Medal>) = PlayerJourney(
            name = name,
            unlocks = (1..6).map { i ->
                Unlock(
                    id = i, title = "A$i", description = "", points = points, badgeName = "b$i",
                    type = AchievementType.STANDARD, earnedAtMillis = day + i * day,
                    numAwarded = 5, gameId = if (i < 4) 1 else 2,
                )
            },
            games = games,
            medals = medals,
        )
        val main = player(
            "TheLongestNameOnTheSite",
            10,
            listOf(Medal(AwardKind.MASTERED, 2 * day, 1, null, "Game One")),
        )
        val rivals = listOf(
            player("AnotherVeryLongPlayerName", 20, listOf(Medal(AwardKind.BEATEN, 3 * day, "rivalone".let { 2 }, "AnotherVeryLongPlayerName", "Game Two"))),
            player("ThirdPlayerWithALongName", 5, emptyList()),
        )
        return JourneyFrame(JourneyMode.SPAN, main, rivals, 0L, 8 * day)
    }

    private fun showOverview(progress: Float, ranks: Map<String, Int> = emptyMap()) {
        val frame = spanCompareFrame()
        val layout = LayoutEngine.layout(frame, includeNodes = true)
        val playback = JourneyPlaybackHolder().apply { this.progress = progress }
        rule.setContent {
            Box(Modifier.width(landscape)) {
                PlayerOverview(
                    frame = frame,
                    layout = layout,
                    style = rememberChartStyle(),
                    playback = playback,
                    playerRanks = ranks,
                )
            }
        }
    }

    /** Every text run the bar draws, with the box it was laid out in. */
    private fun textBounds(): List<Pair<String, Rect>> {
        val nodes = rule.onAllNodes(
            SemanticsMatcher("has text") {
                it.config.getOrNull(SemanticsProperties.Text)?.isNotEmpty() == true
            },
            useUnmergedTree = true,
        )
        return (0 until nodes.fetchSemanticsNodes().size).map { i ->
            val node = nodes[i].fetchSemanticsNode()
            node.config[SemanticsProperties.Text].joinToString(" ") to node.boundsInRoot
        }
    }

    /**
     * One line per player, and every line the same height: the name is the only weighted element,
     * so a name as long as it likes ellipsizes into what the score, the awards and the rank leave
     * rather than pushing any of them onto a second line.
     */
    @Test
    fun `the end bar gives every player one line of the same height`() {
        showOverview(progress = 1f, ranks = mapOf("TheLongestNameOnTheSite" to 4))

        val names = listOf("TheLongestNameOnTheSite", "AnotherVeryLongPlayerName", "ThirdPlayerWithALongName")
        val lines = textBounds().filter { (text, _) -> names.any { text.startsWith(it.take(8)) } }

        assertEquals("one line per player: ${lines.map { it.first }}", 3, lines.size)
        val heights = lines.map { it.second.height }.distinct()
        assertEquals("every line is the same height: $heights", 1, heights.size)
        val tops = lines.map { it.second.top }.distinct()
        assertEquals("and no two players share one", 3, tops.size)
    }

    /** Nothing the end bar draws may run past the right edge of the phone it is drawn on. */
    @Test
    fun `the end bar fits a landscape phone with three long names`() {
        showOverview(progress = 1f, ranks = mapOf("TheLongestNameOnTheSite" to 4, "AnotherVeryLongPlayerName" to 91))

        val width = rule.onRoot().fetchSemanticsNode().size.width.toFloat()
        textBounds().forEach { (text, box) ->
            assertTrue("'$text' runs past the right edge: ${box.right} > $width", box.right <= width + 0.5f)
        }
    }

    /**
     * And every player's score survives, whatever their name costs. The score is the fact the bar
     * exists for; the name is what gives way.
     */
    @Test
    fun `every player's score is on the end bar`() {
        showOverview(progress = 1f)

        val texts = textBounds().map { it.first }
        // Points lead each line now ("240 pts · 12 unlocks"): a compare chart is a race up the
        // points axis, so the score is what belongs at the front of it.
        assertEquals(
            "three scores, one per player: $texts",
            3,
            texts.count { Regex("^\\d+ pts? · ").containsMatchIn(it) },
        )
    }

    /**
     * UI feedback: the running compare bar is the END bar with running numbers - one line per
     * player carrying their unlocks, points and awards as the sweep reveals them - and no date of
     * its own (the chart's corner readout carries the moving date now). The frame is truncated to
     * the sweep, so halfway through a span every count reads roughly half.
     */
    @Test
    fun `the running bar is the end bar with running numbers`() {
        showOverview(progress = 0.5f)

        val texts = textBounds()
        assertEquals(
            "one facts line per player: ${texts.map { it.first }}",
            3,
            texts.count { Regex("^\\d+ pts? · ").containsMatchIn(it.first) },
        )
        assertTrue(
            "every player's line counts their achievements too",
            texts.count { it.first.contains("achievement") } == 3,
        )
        // Running numbers, not the finished totals: the fixture's six unlocks per player are
        // spread over its span, so at halfway nobody's line reads all six.
        assertTrue(
            "the counts are running, not final: ${texts.map { it.first }}",
            texts.none { it.first.endsWith("6 achievements") },
        )
        // And it fits: the names give way, not the scores.
        val width = rule.onRoot().fetchSemanticsNode().size.width.toFloat()
        texts.forEach { (text, box) ->
            assertTrue("'$text' runs past the right edge: ${box.right} > $width", box.right <= width + 0.5f)
        }
    }

    /**
     * One game, three players who played it a year apart - which is what a head-to-head on a game
     * usually is, and which every other fixture here avoids by keeping everybody inside one week.
     */
    private fun gameCompareFrame(): JourneyFrame {
        val year = 365 * day
        val games = mapOf(1 to GameRef(1, "Game One", null, emptyList(), 0, 10, null))
        fun player(name: String, at: Long) = PlayerJourney(
            name = name,
            unlocks = (1..4).map { i ->
                Unlock(
                    id = i, title = "A$i", description = "", points = 10 * i, badgeName = "b$i",
                    type = AchievementType.STANDARD, earnedAtMillis = at + i * day,
                    numAwarded = 5, gameId = 1,
                )
            },
            games = games,
            medals = emptyList(),
        )
        return JourneyFrame(
            JourneyMode.GAME,
            player("Middle", 400 * day),
            listOf(player("PlayedItFirst", 400 * day - year), player("PlayedItLater", 400 * day + year)),
            0L, 0L,
        )
    }

    /**
     * A single game's compare bar is a RACE: the axis is time since each player's own first
     * unlock, so the sweep says the same thing to everyone on it and the calendar says nothing.
     *
     * The bar cut every player at one absolute instant instead - the main player's own last
     * revealed unlock - so a rival who played the game a year LATER had all of their unlocks
     * filtered and lost their line entirely (`compareLines` drops a player with none), while one
     * who played it a year EARLIER kept all of theirs and read their final totals from the first
     * frame. Both are the ordinary case, since a rival carries their own real timestamps.
     */
    @Test
    fun `a single game's bar keeps every player mid race, whenever they played it`() {
        val frame = gameCompareFrame()
        val layout = LayoutEngine.layout(frame, includeNodes = false)
        val playback = JourneyPlaybackHolder().apply { progress = 0.5f }
        rule.setContent {
            Box(Modifier.width(landscape)) {
                PlayerOverview(
                    frame = frame,
                    layout = layout,
                    style = rememberChartStyle(),
                    playback = playback,
                    playerRanks = emptyMap(),
                )
            }
        }

        val texts = textBounds().map { it.first }
        listOf("Middle", "PlayedItFirst", "PlayedItLater").forEach { name ->
            assertTrue("$name has no line on the bar: $texts", texts.any { it.startsWith(name) })
        }
        assertEquals(
            "one facts line per player: $texts",
            3,
            texts.count { Regex("^\\d+ pts? · ").containsMatchIn(it) },
        )
        assertTrue(
            "and nobody has finished the race halfway through it: $texts",
            texts.none { it.contains("4 achievements") },
        )
    }
}
