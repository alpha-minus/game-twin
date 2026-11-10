package com.twinlore.app.journey.export

import com.twinlore.app.journey.domain.ChartLayout
import com.twinlore.app.journey.domain.LayoutEngine
import com.twinlore.app.journey.domain.model.AchievementType
import com.twinlore.app.journey.domain.model.AwardKind
import com.twinlore.app.journey.domain.model.GameRef
import com.twinlore.app.journey.domain.model.JourneyFrame
import com.twinlore.app.journey.domain.model.JourneyMode
import com.twinlore.app.journey.domain.model.Medal
import com.twinlore.app.journey.domain.model.PlayerJourney
import com.twinlore.app.journey.domain.model.Unlock
import com.twinlore.app.journey.ui.chart.ChartPaths
import com.twinlore.app.journey.ui.chart.JourneyChartRenderer
import com.twinlore.app.journey.ui.chart.JourneyEffectClock
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The exported reveal effects: when each badge pops, each award bursts and each flash fires.
 *
 * Robolectric only because [ChartPaths] measures real `android.graphics` paths to find the reveal;
 * nothing here draws a pixel. That is the point of testing it this way - on screen the effects are
 * driven by a wall clock, and a shared video is only shareable if the same journey encodes to the
 * same file every time, which is a property of numbers rather than of anything the encoder did.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ExportEffectsTest {

    private val day = 86_400_000L
    private val hour = 3_600_000L
    private val t0 = 1_760_000_000_000L

    /** The three awards of the cluster, and the lone one far past the cooldown. */
    private val clusterAt = listOf(t0 + 60 * day, t0 + 60 * day + hour, t0 + 60 * day + 2 * hour)
    private val loneAt = t0 + 118 * day

    private fun unlock(id: Int, at: Long, pts: Int) = Unlock(
        id = id, title = "A$id", description = "d$id", points = pts, badgeName = "b$id",
        type = AchievementType.STANDARD, earnedAtMillis = at, numAwarded = 10, gameId = 1,
    )

    private fun fixtureFrame(): JourneyFrame {
        val unlocks = (0 until 24).map { unlock(it, t0 + it * 5 * day, (it + 1) * 5) }
        val medals = (clusterAt + loneAt).mapIndexed { i, at ->
            Medal(AwardKind.MASTERED, at, i + 1, null, "Game ${i + 1}")
        }
        val main = PlayerJourney(
            name = "me",
            unlocks = unlocks,
            games = (1..4).associateWith { id ->
                GameRef(
                    id, "Game $id", null, emptyList(), colorIndex = 0, players = 100, award = null,
                    totalAchievements = 40,
                )
            },
            medals = medals,
        )
        return JourneyFrame(
            mode = JourneyMode.SPAN, main = main, rivals = emptyList(),
            spanFromMillis = 0, spanToMillis = t0 + 120 * day,
        )
    }

    private fun fixtureLayout(): ChartLayout = LayoutEngine.layout(fixtureFrame(), includeNodes = true)

    /** The layout index of the award won at [whenMillis]; the placement pass may reorder them. */
    private fun medalIndex(layout: ChartLayout, whenMillis: Long): Int =
        layout.medals.indexOfFirst { it.medal.whenMillis == whenMillis }
            .also { assertTrue("no medal at $whenMillis", it >= 0) }

    /** The layout index of the badge earned by unlock [id]; the placement pass may reorder them. */
    private fun nodeIndex(layout: ChartLayout, id: Int): Int =
        layout.nodes.indexOfFirst { it.unlock.id == id }
            .also { assertTrue("no node for unlock $id", it >= 0) }

    /** Every number the plan would hand the renderer, over the whole run. Two runs must agree. */
    private fun trace(layout: ChartLayout, frames: Int, speed: Float = 1f): List<Float> {
        val plan = ExportEffects.plan(layout, frames, speed)
        val out = ArrayList<Float>(frames * (layout.nodes.size * 3 + layout.medals.size * 2))
        for (i in 0 until frames) {
            val at = plan.at(i)
            layout.nodes.indices.forEach {
                out += at.nodeSeconds(it)
                out += at.pointsSeconds(it)
                out += at.nodeAppearProgress(it)
            }
            layout.medals.indices.forEach {
                out += at.medalSeconds(it)
                out += at.medalWashSeconds(it)
            }
        }
        return out
    }

    /**
     * A minimal fixture for the "+N" throttle: two badges an hour apart (which collapses to a
     * handful of export frames, comfortably inside [JourneyChartRenderer
     * .FLOATING_POINTS_COOLDOWN_SECONDS]) and a third 118 days later (comfortably past it). Mirrors
     * [clusterAt]/[loneAt] above, but for unlocks rather than medals - the "+N" rides the badge's
     * own reveal, not an award's.
     */
    private val pointsPairAt = listOf(t0, t0 + hour)
    private val pointsLoneAt = t0 + 118 * day

    private fun pointsFixtureFrame(): JourneyFrame {
        val unlocks = (pointsPairAt + pointsLoneAt).mapIndexed { i, at -> unlock(i, at, 5) }
        val main = PlayerJourney(
            name = "me",
            unlocks = unlocks,
            games = mapOf(
                1 to GameRef(
                    1, "Game 1", null, emptyList(), colorIndex = 0, players = 100, award = null,
                    totalAchievements = 40,
                ),
            ),
            medals = emptyList(),
        )
        return JourneyFrame(
            mode = JourneyMode.SPAN, main = main, rivals = emptyList(),
            spanFromMillis = 0, spanToMillis = t0 + 120 * day,
        )
    }

    private fun pointsFixtureLayout(): ChartLayout = LayoutEngine.layout(pointsFixtureFrame(), includeNodes = true)

    /**
     * The effect clock starts at zero on the frame the icon appears on, and then simply counts
     * frames at the export's own fixed cadence. Confetti a few frames early or late is the whole
     * failure mode here, so the reveal frame is pinned against the renderer's own gate.
     */
    @Test fun `a node reports zero seconds on the frame it appears and grows from there`() {
        val layout = fixtureLayout()
        val paths = ChartPaths(layout)
        val frames = ExportTimeline.frameCount(1f)
        val plan = ExportEffects.plan(layout, frames)

        // A badge in the middle of the journey, and the frame the renderer first draws it on.
        val node = layout.nodes.size / 2
        val reveal = (0 until frames).first { i ->
            node in JourneyChartRenderer.visibleNodeIndices(layout, paths, ExportTimeline.progressForFrame(i, 1f))
        }

        assertEquals(0f, plan.at(reveal).nodeSeconds(node), 0f)
        assertEquals(1f / ExportTimeline.FPS, plan.at(reveal + 1).nodeSeconds(node), 1e-6f)
        assertEquals(0.5f, plan.at(reveal + 15).nodeSeconds(node), 1e-6f)
    }

    /**
     * The effects fire on exactly the frames the icons are drawn on. Not "about then": the burst
     * is thrown from the point on the curve where the award was won, and a plan that ran even a
     * frame ahead of the draw pass would bloom out of stage the line has not reached.
     */
    @Test fun `the plan fires on the renderer's own reveal gate, frame for frame`() {
        val layout = fixtureLayout()
        val paths = ChartPaths(layout)
        val frames = ExportTimeline.frameCount(1f)
        val plan = ExportEffects.plan(layout, frames)

        for (i in 0 until frames) {
            val progress = ExportTimeline.progressForFrame(i, 1f)
            val at = plan.at(i)
            assertEquals(
                "badges at frame $i",
                JourneyChartRenderer.visibleNodeIndices(layout, paths, progress).toSet(),
                layout.nodes.indices.filter { at.nodeSeconds(it) >= 0f }.toSet(),
            )
            assertEquals(
                "awards at frame $i",
                JourneyChartRenderer.visibleMedalIndices(layout, paths, progress).toSet(),
                layout.medals.indices.filter { at.medalSeconds(it) >= 0f }.toSet(),
            )
        }
    }

    /**
     * An icon the sweep has not reached reports the renderer's "effects off" value, never a
     * positive one: a positive second is what draws a "+N" over blank stage.
     */
    @Test fun `an icon the sweep has not reached reports the off value`() {
        val layout = fixtureLayout()
        val paths = ChartPaths(layout)
        val frames = ExportTimeline.frameCount(1f)
        val plan = ExportEffects.plan(layout, frames)

        val node = layout.nodes.size - 1
        val reveal = (0 until frames).first { i ->
            node in JourneyChartRenderer.visibleNodeIndices(layout, paths, ExportTimeline.progressForFrame(i, 1f))
        }
        assertTrue("the last badge must not be revealed on frame 0", reveal > 0)
        for (i in 0 until reveal) {
            assertEquals("frame $i", ExportEffects.OFF, plan.at(i).nodeSeconds(node), 0f)
        }
        // And an unrevealed badge is a settled badge, not a badge caught at zero size.
        assertEquals(1f, plan.at(0).nodeAppearProgress(node), 0f)
    }

    /**
     * The same journey and the same frame count produce the same numbers. Nothing about the
     * machine, the encoder's throughput or the order the frames were asked for may reach a pixel.
     */
    @Test fun `the effect path is reproducible`() {
        val layout = fixtureLayout()
        val frames = ExportTimeline.frameCount(1f)

        assertEquals(trace(layout, frames), trace(layout, frames))
        // A separately built layout of the same journey is the same journey.
        assertEquals(trace(layout, frames), trace(fixtureLayout(), frames))
    }

    /**
     * The flash is throttled where the confetti and the pop are not: three masteries inside a
     * second wash the stage once, not three times. It is the difference between a celebration and
     * a strobe, and it is the behaviour [JourneyChartRenderer.WASH_COOLDOWN_SECONDS] exists for.
     */
    @Test fun `a cluster of awards flashes once and keeps its confetti`() {
        val layout = fixtureLayout()
        val frames = ExportTimeline.frameCount(1f)
        val plan = ExportEffects.plan(layout, frames)
        val cluster = clusterAt.map { medalIndex(layout, it) }

        // The cluster really is a cluster in export time: all three inside one second.
        val revealed = cluster.map { index ->
            (0 until frames).first { plan.at(it).medalSeconds(index) >= 0f }
        }
        assertTrue(
            "the fixture's awards are not clustered: $revealed",
            (revealed.max() - revealed.min()) < ExportTimeline.FPS,
        )

        val flashed = cluster.filter { index -> (0 until frames).any { plan.at(it).medalWashSeconds(index) >= 0f } }
        assertEquals("one flash for the cluster, not three", 1, flashed.size)
        // The two that lost the flash keep their burst - that is what marks them at the icon.
        cluster.forEach { index ->
            assertTrue(
                "award $index lost its confetti",
                (0 until frames).any { plan.at(it).medalSeconds(index) >= 0f },
            )
        }
    }

    /**
     * The cooldown is quiet time, not a one-flash-per-video rule: an award far enough past the
     * last flash gets its own.
     */
    @Test fun `an award past the cooldown gets its own flash`() {
        val layout = fixtureLayout()
        val frames = ExportTimeline.frameCount(1f)
        val plan = ExportEffects.plan(layout, frames)

        fun firedAt(index: Int): Int? = (0 until frames).firstOrNull { plan.at(it).medalWashSeconds(index) >= 0f }

        val first = firedAt(medalIndex(layout, clusterAt.first()))
        val lone = firedAt(medalIndex(layout, loneAt))
        assertNotEquals("the first award never flashed", null, first)
        assertNotEquals("the lone award never flashed", null, lone)
        val gap = (lone!! - first!!) / ExportTimeline.FPS.toFloat()
        assertTrue(
            "two flashes inside the cooldown ($gap s)",
            gap >= JourneyChartRenderer.WASH_COOLDOWN_SECONDS,
        )
    }

    // ---- a rival's award flashes too ----

    /**
     * How many real days apart two "pockets" of clustered awards have to land before the export
     * timeline's compression alone (not the calendar) puts them outside the shared cooldown - see
     * [rivalWashFixtureFrame]'s own doc for the arithmetic this is built from. Comfortably over the
     * ~401-day threshold the fixture's own window and cooldown constant work out to.
     */
    private val pocketGapDays = 450L

    /** aria's mastery, an hour before the main player's, then brix's completion well clear of both. */
    private val rivalClusterAt = listOf(t0 + 60 * day, t0 + 60 * day + hour)
    private val rivalLoneAt = t0 + (60 + 2 * pocketGapDays) * day

    /**
     * cass's low-tier award, an hour before dez's mastery - the exact shape of the bug report: "I
     * couldn't see yellow flash when mastery award was won." A full [pocketGapDays] clear of
     * [rivalClusterAt] so cass's own award is not itself caught by aria's cooldown - the point of
     * this fixture is testing what a mastery does to the cooldown a lesser award *right next to it*
     * just spent, not what an unrelated distant cluster does.
     */
    private val masteryOverrideAt = listOf(
        t0 + (60 + pocketGapDays) * day,
        t0 + (60 + pocketGapDays) * day + hour,
    )

    /**
     * A compare journey: the main player and four rivals, each with one award, in three "pockets"
     * [pocketGapDays] apart.
     * - aria's and the main player's masteries land an hour apart (the same cluster shape as
     *   [clusterAt]/[loneAt], mirrored across owners) - two masteries close together, which still
     *   respect a cooldown between themselves.
     * - cass's beaten-casual award lands an hour before dez's mastery - a lesser award immediately
     *   ahead of a mastery, which must not cost the mastery its flash.
     * - brix's completion lands in a third pocket of its own, comfortably clear of every cooldown.
     *
     * The pockets have to be [pocketGapDays] apart rather than some smaller "clearly enough" gap
     * because of how the export timeline actually compresses calendar time: with this fixture's
     * ~1001-day axis window and `ExportEffects`' 450-frame reveal, one day of calendar time is
     * roughly 450/1001 ≈ 0.45 export frames, so the cooldown's 180 frames (6s at 30fps) needs about
     * 180 / 0.45 ≈ 401 real days of separation before two events actually land more than a cooldown
     * apart in the video. A gap that felt "obviously far enough" on the calendar (the first attempt
     * at this fixture used 30 days) was in practice roughly 13x too short and silently failed the
     * very assertion it was trying to set up cleanly. [pocketGapDays] keeps this margin explicit
     * rather than a number pulled from a first guess.
     */
    private fun rivalWashFixtureFrame(): JourneyFrame {
        fun journey(name: String, idOffset: Int, points: Int, medal: Medal) = PlayerJourney(
            name = name,
            unlocks = (0 until 6).map { unlock(idOffset + it, t0 + it * 5 * day, points) },
            games = mapOf(
                1 to GameRef(
                    1, "Game $name", null, emptyList(), colorIndex = 0, players = 100, award = null,
                    totalAchievements = 40,
                ),
            ),
            medals = listOf(medal),
        )
        val main = journey(
            "me", 0, 10,
            Medal(AwardKind.MASTERED, rivalClusterAt[1], 1, null, "Game One"),
        )
        val aria = journey(
            "aria", 100, 8,
            Medal(AwardKind.MASTERED, rivalClusterAt[0], 1, "aria", "aria - Game Two"),
        )
        val brix = journey(
            "brix", 200, 6,
            Medal(AwardKind.COMPLETED, rivalLoneAt, 1, "brix", "brix - Game Three"),
        )
        val cass = journey(
            "cass", 300, 4,
            Medal(AwardKind.BEATEN_CASUAL, masteryOverrideAt[0], 1, "cass", "cass - Game Four"),
        )
        val dez = journey(
            "dez", 400, 12,
            Medal(AwardKind.MASTERED, masteryOverrideAt[1], 1, "dez", "dez - Game Five"),
        )
        // An explicit window rather than the 0 sentinel [fixtureFrame] uses: with a 0 sentinel the
        // axis is stretched to cover only the UNLOCKS (LayoutEngine's `windowAxis`), and this
        // fixture's unlocks stop at day 25 while its medals run out past day 900 - every medal past
        // day 25 would clamp onto the same axis edge and land at the same x, which defeats the whole
        // point of a fixture built to tell "clustered" apart from "clear of the cluster". A window
        // that actually covers the medals keeps their x positions distinct. Sized with a buffer past
        // the last pocket (day 60 + 2 * pocketGapDays = 960) rather than pinned exactly to it, so a
        // future change to a pocket's own offset does not also have to move this.
        return JourneyFrame(
            mode = JourneyMode.SPAN, main = main, rivals = listOf(aria, brix, cass, dez),
            spanFromMillis = t0 - day, spanToMillis = t0 + 1000 * day,
        )
    }

    private fun rivalWashFixtureLayout(): ChartLayout = LayoutEngine.layout(rivalWashFixtureFrame(), includeNodes = true)

    /**
     * Pins the bug the user reported: a compare journey's rival mastery never flashed the stage at
     * all, on either surface. `ExportEffects.plan` used to skip a medal entirely once it saw a
     * non-null `ownerName`, so a rival's `washFrame` entry stayed [ExportEffects.NEVER] forever -
     * not suppressed by the cooldown, simply never considered.
     */
    @Test fun `a rival's award produces a flash, not just the main player's`() {
        val layout = rivalWashFixtureLayout()
        val frames = ExportTimeline.frameCount(1f)
        val plan = ExportEffects.plan(layout, frames)

        val aria = medalIndex(layout, rivalClusterAt[0])
        assertEquals("aria's fixture must carry the rival owner", "aria", layout.medals[aria].medal.ownerName)

        val flashed = (0 until frames).any { plan.at(it).medalWashSeconds(aria) >= 0f }
        assertTrue("a rival's mastery must flash the stage; it never fired", flashed)
    }

    /**
     * The cooldown decision this task makes: one shared quiet interval across every player on the
     * chart, not one per player. Two players mastering games an hour apart now compete for the same
     * flash slot exactly as two of the main player's own awards would - whichever fired first (aria,
     * chronologically first in the fixture) keeps it, and the later one settles for its confetti and
     * pop, same as any other award the cooldown swallows.
     */
    @Test fun `a cluster shares one cooldown across players - whoever flashed first keeps it`() {
        val layout = rivalWashFixtureLayout()
        val frames = ExportTimeline.frameCount(1f)
        val plan = ExportEffects.plan(layout, frames)

        val aria = medalIndex(layout, rivalClusterAt[0])
        val main = medalIndex(layout, rivalClusterAt[1])

        fun revealedAt(index: Int) = (0 until frames).first { plan.at(it).medalSeconds(index) >= 0f }
        assertTrue(
            "the fixture's cluster is not actually clustered in export time: " +
                "${revealedAt(aria)} vs ${revealedAt(main)}",
            abs(revealedAt(aria) - revealedAt(main)) < ExportTimeline.FPS,
        )

        fun flashedAt(index: Int) = (0 until frames).any { plan.at(it).medalWashSeconds(index) >= 0f }
        assertTrue("aria's earlier mastery must win the cluster's one flash", flashedAt(aria))
        assertFalse(
            "the main player's later award must not steal a second flash out of the same cluster - " +
                "one shared cooldown pool, not one per player",
            flashedAt(main),
        )
        // brix's award is clear of the cluster and must flash on its own, exactly like [loneAt]'s.
        val brix = layout.medals.indices.first { layout.medals[it].medal.ownerName == "brix" }
        assertTrue("brix's award, well clear of the cluster, must get its own flash", flashedAt(brix))
    }

    /**
     * The exact case the user hit: "I couldn't see yellow flash when mastery award was won." A
     * rival's low-tier award (cass's beaten-casual) fires first and spends the shared cooldown; the
     * player's own mastery (dez's) an hour later must still flash rather than being silenced by it.
     * cass's own award still flashes too - a mastery overriding a lesser award's cooldown does not
     * retroactively take the lesser award's flash away from it.
     */
    @Test fun `a mastery is not silenced by a rival's lesser award landing just before it`() {
        val layout = rivalWashFixtureLayout()
        val frames = ExportTimeline.frameCount(1f)
        val plan = ExportEffects.plan(layout, frames)

        val cass = medalIndex(layout, masteryOverrideAt[0])
        val dez = medalIndex(layout, masteryOverrideAt[1])
        assertEquals(AwardKind.BEATEN_CASUAL, layout.medals[cass].medal.kind)
        assertEquals(AwardKind.MASTERED, layout.medals[dez].medal.kind)

        fun revealedAt(index: Int) = (0 until frames).first { plan.at(it).medalSeconds(index) >= 0f }
        assertTrue(
            "cass's and dez's awards are not actually close together in export time: " +
                "${revealedAt(cass)} vs ${revealedAt(dez)}",
            abs(revealedAt(cass) - revealedAt(dez)) < ExportTimeline.FPS,
        )

        fun flashedAt(index: Int) = (0 until frames).any { plan.at(it).medalWashSeconds(index) >= 0f }
        assertTrue("cass's beaten-casual award, first in the window, must still get its own flash", flashedAt(cass))
        assertTrue(
            "dez's mastery must flash even though cass's lesser award just spent the shared cooldown",
            flashedAt(dez),
        )
    }

    /**
     * The assertion that matters most: for one journey, the medals that flash on a video must be
     * exactly the medals a live [JourneyEffectClock] would have flashed on screen - main player's or
     * rival's alike - or a shared clip celebrates a different set of awards than the person who
     * exported it watched.
     *
     * Same shape as "the live clock and the export plan pop exactly the same badges" below, over a
     * fixture that actually gives the shared cooldown pool cross-owner competition to arbitrate: both
     * paths are driven through the same per-frame walk ([JourneyChartRenderer.forEachEffectReveal])
     * [plan] itself is built from, with export-cadence nanos standing in for the wall clock the live
     * chart would have read.
     */
    @Test fun `the live clock and the export plan flash exactly the same medals`() {
        val layout = rivalWashFixtureLayout()
        val paths = ChartPaths(layout)
        val frames = ExportTimeline.frameCount(1f)

        val plan = ExportEffects.plan(layout, frames, paths = paths)
        val exportFlashed = layout.medals.indices.filter { idx ->
            (0 until frames).any { plan.at(it).medalWashSeconds(idx) >= 0f }
        }.toSet()

        val clock = JourneyEffectClock()
        val liveT0 = 5_000_000_000L
        val nanosPerFrame = 1_000_000_000.0 / ExportTimeline.FPS
        val liveFlashed = HashSet<Int>()
        for (i in 0 until frames) {
            val progress = ExportTimeline.progressForFrame(i, 1f)
            val now = liveT0 + Math.round(i * nanosPerFrame)
            JourneyChartRenderer.forEachEffectReveal(
                layout = layout,
                paths = paths,
                progress = progress,
                onNode = {},
                onMedal = { idx ->
                    clock.medalSeconds(idx, now, true)
                    val kind = layout.medals[idx].medal.kind
                    if (clock.medalWashSeconds(idx, kind, now, true) >= 0f) liveFlashed += idx
                },
            )
        }

        assertTrue(
            "the fixture must actually throttle something across players for this test to mean " +
                "anything",
            exportFlashed.size < layout.medals.size,
        )
        assertEquals(exportFlashed, liveFlashed)
    }

    /**
     * The badge grows in and then stays: a video's icons pop the way the watched chart's did, and
     * every one of them has settled long before the clip ends.
     */
    @Test fun `a badge pops in and then settles`() {
        val layout = fixtureLayout()
        val paths = ChartPaths(layout)
        val frames = ExportTimeline.frameCount(1f)
        val plan = ExportEffects.plan(layout, frames)

        val node = layout.nodes.size / 2
        val reveal = (0 until frames).first { i ->
            node in JourneyChartRenderer.visibleNodeIndices(layout, paths, ExportTimeline.progressForFrame(i, 1f))
        }
        assertEquals(0f, plan.at(reveal).nodeAppearProgress(node), 0f)
        val settled = reveal + (JourneyChartRenderer.POP_IN_SECONDS * ExportTimeline.FPS).toInt() + 1
        assertTrue("the pop must finish inside the clip", settled < frames)
        assertEquals(1f, plan.at(settled).nodeAppearProgress(node), 0f)

        // The hold tail is what makes that true of every icon: the clip never ends mid-pop.
        val last = plan.at(frames - 1)
        layout.nodes.indices.forEach { assertEquals("badge $it", 1f, last.nodeAppearProgress(it), 0f) }
    }

    /** A degenerate timeline must not throw on a background thread, and must animate nothing. */
    @Test fun `an empty timeline has no effects`() {
        val layout = fixtureLayout()
        val plan = ExportEffects.plan(layout, frames = 0)
        assertEquals(ExportEffects.OFF, plan.at(0).nodeSeconds(0), 0f)
        assertEquals(ExportEffects.OFF, plan.at(0).pointsSeconds(0), 0f)
        assertEquals(ExportEffects.OFF, plan.at(0).medalSeconds(0), 0f)
        assertEquals(ExportEffects.OFF, plan.at(0).medalWashSeconds(0), 0f)
        assertEquals(1f, plan.at(0).nodeAppearProgress(0), 0f)
    }

    // ---- the "+N" pop's own throttle ----

    /**
     * The same noise guard as the flash, applied to the "+N": two badges revealed inside a couple
     * of export frames collapse to one pop, not two. The one that loses it keeps everything else -
     * it still appears, and it still pops in - only the number is throttled.
     */
    @Test fun `two badges revealed close together pop once, not twice`() {
        val layout = pointsFixtureLayout()
        val frames = ExportTimeline.frameCount(1f)
        val plan = ExportEffects.plan(layout, frames)
        val a = nodeIndex(layout, 0)
        val b = nodeIndex(layout, 1)

        fun revealedAt(index: Int) = (0 until frames).first { plan.at(it).nodeSeconds(index) >= 0f }
        val gapFrames = revealedAt(b) - revealedAt(a)
        val cooldownFrames = JourneyChartRenderer.FLOATING_POINTS_COOLDOWN_SECONDS * ExportTimeline.FPS
        assertTrue(
            "the fixture's badges are not close together: $gapFrames frames apart",
            gapFrames < cooldownFrames,
        )

        fun poppedAt(index: Int): Int? = (0 until frames).firstOrNull { plan.at(it).pointsSeconds(index) >= 0f }
        val popped = listOfNotNull(poppedAt(a), poppedAt(b))
        assertEquals("one pop for the pair, not two", 1, popped.size)

        // The badge that lost its number still appeared and still popped in - only the "+N" was
        // throttled, never the badge itself.
        assertEquals(0f, plan.at(revealedAt(b)).nodeAppearProgress(b), 0f)
    }

    /** The cooldown is quiet time, not a one-pop-per-video rule. */
    @Test fun `a badge past the cooldown gets its own pop`() {
        val layout = pointsFixtureLayout()
        val frames = ExportTimeline.frameCount(1f)
        val plan = ExportEffects.plan(layout, frames)

        fun poppedAt(index: Int): Int? = (0 until frames).firstOrNull { plan.at(it).pointsSeconds(index) >= 0f }

        val first = poppedAt(nodeIndex(layout, 0))
        val lone = poppedAt(nodeIndex(layout, 2))
        assertNotEquals("the first badge never popped", null, first)
        assertNotEquals("the lone badge never popped", null, lone)
        val gap = (lone!! - first!!) / ExportTimeline.FPS.toFloat()
        assertTrue(
            "two pops inside the cooldown ($gap s)",
            gap >= JourneyChartRenderer.FLOATING_POINTS_COOLDOWN_SECONDS,
        )
    }

    /** A pop that loses its slot never fires late, exactly like a suppressed flash. */
    @Test fun `a badge that loses its pop never fires it late`() {
        val layout = pointsFixtureLayout()
        val frames = ExportTimeline.frameCount(1f)
        val plan = ExportEffects.plan(layout, frames)
        val a = nodeIndex(layout, 0)
        val b = nodeIndex(layout, 1)

        fun poppedAt(index: Int): Int? = (0 until frames).firstOrNull { plan.at(it).pointsSeconds(index) >= 0f }
        assertNotEquals(null, poppedAt(a))
        // Guards the fixture's own assumption: b must actually be the one that lost the slot for
        // the rest of this test to mean anything.
        assertEquals("badge b must lose its pop for this test to mean anything", null, poppedAt(b))

        for (i in 0 until frames) {
            assertTrue("frame $i", plan.at(i).pointsSeconds(b) < 0f)
        }
    }

    /**
     * The assertion that matters most: for one journey, the badges whose "+N" pops on a video must
     * be exactly the badges a live [JourneyEffectClock] would have popped on screen, or a shared
     * clip shows a different set of numbers than the person who exported it watched.
     *
     * Both paths are driven through the SAME per-frame walk
     * ([JourneyChartRenderer.forEachEffectReveal]) that [plan] itself is built from, with
     * export-cadence nanos standing in for the wall clock the live chart would have read. The two
     * throttles are the same arithmetic against the same constant
     * ([JourneyChartRenderer.FLOATING_POINTS_COOLDOWN_SECONDS]) - one counting frames, one counting
     * nanoseconds - so if the fixture is dense enough to make the throttle do anything at all, the
     * two sets either agree or one of the implementations has drifted from the other.
     */
    @Test fun `the live clock and the export plan pop exactly the same badges`() {
        val layout = fixtureLayout()
        val paths = ChartPaths(layout)
        val frames = ExportTimeline.frameCount(1f)

        val plan = ExportEffects.plan(layout, frames, paths = paths)
        val exportPopped = layout.nodes.indices.filter { idx ->
            (0 until frames).any { plan.at(it).pointsSeconds(idx) >= 0f }
        }.toSet()

        val clock = JourneyEffectClock()
        val liveT0 = 5_000_000_000L
        val nanosPerFrame = 1_000_000_000.0 / ExportTimeline.FPS
        val livePopped = HashSet<Int>()
        for (i in 0 until frames) {
            val progress = ExportTimeline.progressForFrame(i, 1f)
            val now = liveT0 + Math.round(i * nanosPerFrame)
            JourneyChartRenderer.forEachEffectReveal(
                layout = layout,
                paths = paths,
                progress = progress,
                onNode = { idx ->
                    // The pop-in registers first, exactly as the live draw pass registers it
                    // before the "+N" - see JourneyChart's wiring. It does not affect the points
                    // decision (separate bookkeeping) but keeping the order matches production.
                    clock.nodeSeconds(idx, now, true)
                    if (clock.nodePointsSeconds(idx, now, true) >= 0f) livePopped += idx
                },
                onMedal = {},
            )
        }

        assertTrue(
            "the fixture must actually throttle something for this test to mean anything",
            exportPopped.size < layout.nodes.size,
        )
        assertEquals(exportPopped, livePopped)
    }
}
