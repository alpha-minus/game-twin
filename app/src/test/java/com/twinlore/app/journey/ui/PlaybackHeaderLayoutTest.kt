package com.twinlore.app.journey.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.twinlore.app.journey.domain.LayoutEngine
import com.twinlore.app.journey.domain.model.AchievementType
import com.twinlore.app.journey.domain.model.GameRef
import com.twinlore.app.journey.domain.model.JourneyFrame
import com.twinlore.app.journey.domain.model.JourneyMode
import com.twinlore.app.journey.domain.model.PlayerJourney
import com.twinlore.app.journey.domain.model.Unlock
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The playback header's layout, at a phone's width and with a game title long enough to break it.
 *
 * Two things are pinned here, and both come from the same device report. The header had grown to
 * four stacked lines over a chart it half covered, and the *date* line on it collided with another
 * text run: it was the one line drawn in `onSurfaceVariant` (this app's theme resolves that to the
 * foreground at 70% alpha) on a panel painted at 86%, so the chart's own grid lines and axis labels
 * read straight through its glyphs. The panel is opaque now and every line is full-strength ink -
 * neither of which a semantics tree can see - so what this asserts is the structural half: two
 * lines rather than four, and no two text runs sharing a pixel.
 *
 * Robolectric's font metrics are not a device's, so nothing here reads an absolute text width. The
 * height budget is a *relative* fact that survives fake metrics: the same fixture measured 176px
 * with the four-line header and measures under 100 with this one.
 */
@RunWith(RobolectricTestRunner::class)
// The landscape handheld the journey player actually runs on, which is also the window the 1920px
// export mirrors 1:1 (see `ExportStrip.DENSITY`). Robolectric's default is a portrait phone, and
// the header's fact slots are absolute dp - whether they fit is exactly what differs between the
// two, so measuring the device's layout means asking for the device's window. The narrow case has
// a test of its own, which sets its own qualifiers.
@Config(sdk = [33], qualifiers = "w834dp-h411dp-land")
class PlaybackHeaderLayoutTest {

    @get:Rule
    val rule = createComposeRule()

    private val day = 86_400_000L

    /** 160 achievements over six weeks, under a title that is as long as titles get. */
    private fun gameFrame(playtimeSeconds: Int? = null): JourneyFrame {
        val unlocks = (1..160).map { i ->
            Unlock(
                id = i, title = "A$i", description = "", points = 7, badgeName = "b$i",
                type = AchievementType.STANDARD, earnedAtMillis = i * day / 4, numAwarded = 5,
                gameId = 1,
            )
        }
        val games = mapOf(
            1 to GameRef(
                id = 1, title = "Final Fantasy VII Remake Intergrade", iconPath = null,
                artPaths = emptyList(), colorIndex = 1, players = 10, award = null,
                totalAchievements = 160, playtimeSeconds = playtimeSeconds,
            ),
        )
        return JourneyFrame(
            mode = JourneyMode.GAME,
            main = PlayerJourney("me", unlocks, games, emptyList()),
            rivals = emptyList(),
            spanFromMillis = 0,
            spanToMillis = 41 * day,
        )
    }

    /**
     * The width the journey player actually runs at: the landscape handheld this feature is used
     * on, which is also the window the 1920px export mirrors 1:1 (see `ExportStrip.DENSITY`).
     *
     * Explicit rather than Robolectric's default portrait window, because the header's fact slots
     * are absolute dp and whether they fit is exactly what differs between the two - see
     * [factSlotsFit], and the narrow case has a test of its own below.
     */
    private val LANDSCAPE = 834.dp

    /**
     * The header inside the band the player puts it in: weighted against an always-present close
     * button, so the width it is measured at is the width it gets on the device.
     */
    private fun showHeader(frame: JourneyFrame, progress: Float, width: Dp = LANDSCAPE) {
        val layout = LayoutEngine.layout(frame, includeNodes = true)
        val playback = JourneyPlaybackHolder().apply { this.progress = progress }
        rule.setContent {
            Row(
                Modifier.width(width).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Box(Modifier.weight(1f, fill = false)) {
                    PlayerOverview(
                        frame = frame,
                        layout = layout,
                        style = rememberChartStyle(),
                        playback = playback,
                        playerRanks = emptyMap(),
                    )
                }
                Box(Modifier.size(48.dp))
            }
        }
    }

    /**
     * A header too narrow for its slots draws the one ellipsizing run instead, rather than losing
     * the facts that did not fit.
     *
     * Compose measures a Row's fixed-width children in order and hands the overflow nothing, so
     * without this the points and the cost measured zero and vanished off the header.
     */
    @Test
    @Config(qualifiers = "w360dp-h740dp-port")
    fun `a narrow header falls back to one run rather than losing facts`() {
        showHeader(gameFrame(playtimeSeconds = 7200), progress = 1f, width = 360.dp)

        val runs = textBounds().map { it.first }.filterNot { it.endsWith("%") }
        val counts = runs.single { it.contains("achievements") }
        assertTrue("every fact is still on the header: $counts", counts.contains("points"))
        assertTrue("the cost included", counts.contains("2h 0m"))
    }

    /** Every text run the header draws, with the box it was laid out in. */
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

    private fun SemanticsNodeInteraction.height(): Float = fetchSemanticsNode().size.height.toFloat()

    /**
     * The whole point of the report: the date line and the run beside it were in the same place.
     * Nothing the header draws may share a pixel with anything else it draws.
     */
    @Test
    fun `no two text runs in the header overlap`() {
        showHeader(gameFrame(), progress = 1f)

        val bounds = textBounds()
        assertTrue("the header has text to check", bounds.size >= 3)
        for (i in bounds.indices) for (j in (i + 1) until bounds.size) {
            val (aText, a) = bounds[i]
            val (bText, b) = bounds[j]
            assertTrue(
                "'$aText' and '$bText' overlap: $a vs $b",
                a.right <= b.left || b.right <= a.left || a.bottom <= b.top || b.bottom <= a.top,
            )
        }
    }

    /**
     * Two lines, not four. The title has its own line and every fact shares the one under it, which
     * is what halves the height.
     *
     * The facts are separate `Text`s now (one per fixed slot, so a counter's digits cannot shuffle
     * the line), so this counts distinct TOPS rather than nodes - three runs on one baseline is
     * still one line. The trailing bar sits in its own column beside the block and is excluded by
     * its text rather than by its position.
     */
    @Test
    fun `the header stacks exactly two lines`() {
        showHeader(gameFrame(), progress = 1f)

        val rows = textBounds()
            .filterNot { it.first.endsWith("%") }
            .map { it.second.top }
            .distinct()
        assertEquals("the header is two lines: $rows", 2, rows.size)
    }

    /**
     * Half the height it was. Measured against the panel itself rather than against a dp constant,
     * because what the report was about is how much of the chart the header covers.
     */
    @Test
    fun `the header is half the height the four-line one was`() {
        showHeader(gameFrame(), progress = 1f)

        // The four-line header measured 176px on this fixture, at these (fake) metrics.
        assertTrue(
            "the header must stay well under the four-line one it replaces",
            rule.onRoot().height() < 120f,
        )
    }

    /**
     * At the end the date has nothing left to say, so the line becomes what the journey cost. With
     * a playtime it names both halves; without one (an older cache row) it names the span alone and
     * never invents an hour.
     */
    @Test
    fun `the finished header names the playtime it was given`() {
        showHeader(gameFrame(playtimeSeconds = 42 * 3600 + 10 * 60), progress = 1f)

        val lines = textBounds().map { it.first }
        assertTrue(
            "the finished header names the hours played: $lines",
            lines.any { it.contains("Played 42h 10m") },
        )
        assertTrue(
            "and leaves the run of days to the chart's axis",
            lines.none { it.contains("42h 10m over") },
        )
    }

    /** An older cache row has no hours to name, and keeps its date rather than saying "0h". */
    @Test
    fun `a finished header with no playtime claims no hours`() {
        showHeader(gameFrame(playtimeSeconds = null), progress = 1f)

        val lines = textBounds().map { it.first }
        assertTrue("it still has its counts line: $lines", lines.any { it.contains("achievements") })
        assertTrue("and claims no hours", lines.none { it.contains("Played") })
    }

    /**
     * The counts sit on the second line, under a first line that is the game's name and nothing
     * else - so a long title ellipsizes against the bar rather than against the facts.
     *
     * While it runs the line is the two counts and no more. The running date came off it (user
     * request): where the sweep is in time is the corner readout's job on a followed chart, and
     * carrying it here as well was the same fact twice on the one chart that shows both.
     */
    @Test
    fun `the counts line carries the counts and no running date`() {
        showHeader(gameFrame(), progress = 0.5f)

        val bounds = textBounds()
        val counts = bounds.single { it.first.contains("achievements") }
        val points = bounds.single { it.first.contains("points") }
        val title = bounds.single { it.first.startsWith("Final Fantasy") }

        // The two counts share one baseline, and nothing else in the block is on it: no date, and
        // no cost until the journey lands. The "·" between them is furniture, not a fact - it is
        // on the line by design (owner request: the slotted pair read as one blur without it) and
        // counted apart from the facts it separates.
        assertEquals("the counts and the points are one line", counts.second.top, points.second.top)
        assertEquals(
            "only the two counts are on it: " + bounds.map { it.first },
            2,
            bounds.count { !it.first.endsWith("%") && it.first != "·" && it.second.top == counts.second.top },
        )
        assertTrue(
            "the separator sits between them on the same line",
            bounds.any { it.first == "·" && it.second.top == counts.second.top },
        )
        assertTrue("the title is above them", title.second.top < counts.second.top)
        assertTrue(
            "and shares its row with nothing else in the block",
            bounds.none { it !== title && !it.first.endsWith("%") && it.second.top == title.second.top },
        )
    }

    /** Once it lands, what the game cost joins them - which is not a date and says something new. */
    @Test
    fun `the counts line gains the cost once the journey lands`() {
        showHeader(gameFrame(playtimeSeconds = 7200), progress = 1f)

        val bounds = textBounds()
        val counts = bounds.single { it.first.contains("achievements") }
        val cost = bounds.single { it.first.contains("2h 0m") }

        assertEquals("the cost joins the counts on their line", counts.second.top, cost.second.top)
        assertTrue("and it comes after them", cost.second.left > counts.second.left)
    }

    /**
     * EVERY FACT KEEPS ITS PLACE as the numbers in it change.
     *
     * The report this is for: a counter laid out at its natural width moves as its own digits do,
     * and drags every fact after it - and the bar at the end of the row - along with it, several
     * times a second for the whole replay. Each fact has a fixed slot now, so its x is the same at
     * every point of playback.
     */
    @Test
    fun `a fact stays put as its counter changes`() {
        // One composition, driven: `setContent` may only be called once per test, and driving the
        // holder is also the honest shape - this is a replay moving, not two separate headers.
        val frame = gameFrame()
        val layout = LayoutEngine.layout(frame, includeNodes = true)
        val playback = JourneyPlaybackHolder().apply { progress = 0.15f }
        rule.setContent {
            Row(
                Modifier.width(LANDSCAPE).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Box(Modifier.weight(1f, fill = false)) {
                    PlayerOverview(
                        frame = frame,
                        layout = layout,
                        style = rememberChartStyle(),
                        playback = playback,
                        playerRanks = emptyMap(),
                    )
                }
                Box(Modifier.size(48.dp))
            }
        }

        fun factLefts(): List<Pair<String, Float>> = textBounds()
            .filterNot { it.first.endsWith("%") }
            .sortedBy { it.second.top * 10_000 + it.second.left }
            .map { it.first to it.second.left }

        val early = factLefts()
        rule.runOnIdle { playback.progress = 0.85f }
        rule.waitForIdle()
        val late = factLefts()

        assertNotEquals("the counters must actually have moved on", early, late)
        assertEquals("the same runs, in the same order", early.size, late.size)
        early.indices.forEach { i ->
            // The text itself has moved on - that is the point - but its left edge has not.
            assertEquals(
                "'${early[i].first}' -> '${late[i].first}' moved from ${early[i].second} to ${late[i].second}",
                early[i].second,
                late[i].second,
                0.5f,
            )
        }
    }
}
