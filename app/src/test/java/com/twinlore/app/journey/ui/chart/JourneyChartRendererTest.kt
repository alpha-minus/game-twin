package com.twinlore.app.journey.ui.chart

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ApplicationProvider
import com.twinlore.app.data.retroachievements.RaUrls
import com.twinlore.app.journey.domain.ChartLayout
import com.twinlore.app.journey.domain.LayoutEngine
import com.twinlore.app.journey.domain.TickGenerator
import com.twinlore.app.journey.domain.model.AchievementType
import com.twinlore.app.journey.domain.model.GameRef
import com.twinlore.app.journey.domain.model.JourneyFrame
import com.twinlore.app.journey.domain.model.AwardKind
import com.twinlore.app.journey.domain.model.JourneyMode
import com.twinlore.app.journey.domain.model.Medal
import com.twinlore.app.journey.domain.model.PlayerJourney
import com.twinlore.app.journey.domain.model.Unlock
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Reveal-completeness guard for the chart renderer. The trap this pins down: in compare mode the
 * sweep runs across the whole plot area, so a rival who kept unlocking after the main player has
 * a curve that extends past the main player's last point. If the sweep were derived from the main
 * curve's length (as the solo path does), that rival would still be partly hidden at the end of
 * playback. At progress = 1 every curve must be fully drawn.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class JourneyChartRendererTest {

    private val day = 86_400_000L
    private val t0 = 1_760_000_000_000L

    private fun unlock(id: Int, at: Long, pts: Int, gameId: Int = 1) = Unlock(
        id = id, title = "A$id", description = "d$id", points = pts, badgeName = "b$id",
        type = AchievementType.STANDARD, earnedAtMillis = at, numAwarded = 10, gameId = gameId,
    )

    private fun player(name: String, unlocks: List<Unlock>) = PlayerJourney(
        name = name,
        unlocks = unlocks.sortedBy { it.earnedAtMillis },
        games = unlocks.map { it.gameId }.distinct().associateWith {
            GameRef(it, "G$it", null, emptyList(), colorIndex = it, players = 100, award = null)
        },
        medals = emptyList(),
    )

    private fun layoutOf(
        main: PlayerJourney,
        rivals: List<PlayerJourney>,
        includeNodes: Boolean = false,
    ): ChartLayout = LayoutEngine.layout(
        JourneyFrame(
            mode = JourneyMode.SPAN, main = main, rivals = rivals,
            spanFromMillis = 0, spanToMillis = t0 + 100 * day,
        ),
        includeNodes = includeNodes,
    )

    /** Main stops early, rival keeps going: the classic partially-revealed-rival regression. */
    private fun compareLayout(): ChartLayout {
        val main = player(
            "me",
            listOf(unlock(1, t0, 10), unlock(2, t0 + 5 * day, 20), unlock(3, t0 + 20 * day, 30)),
        )
        val rival = player(
            "rival",
            listOf(
                unlock(11, t0 + 2 * day, 5),
                unlock(12, t0 + 30 * day, 25),
                unlock(13, t0 + 60 * day, 40),   // strictly later than the main player's last
            ),
        )
        return layoutOf(main, listOf(rival))
    }

    @Test
    fun `compare mode reveals every curve completely at the end of playback`() {
        val layout = compareLayout()
        val paths = ChartPaths(layout)

        assertTrue("fixture must be compare mode", layout.rivals.any { it.points.size > 1 })
        assertTrue(
            "fixture rival must outlast the main curve",
            layout.rivals.first().maxX > layout.main.maxX,
        )

        assertEquals(
            paths.mainLength,
            JourneyChartRenderer.revealedLength(layout, paths, 1f),
            0.5f,
        )

        val sweep = JourneyChartRenderer.sweepX(layout, paths, 1f)
        layout.rivals.forEachIndexed { i, rival ->
            assertTrue(
                "sweep $sweep must reach rival $i maxX ${rival.maxX}",
                sweep >= rival.maxX - 0.5f,
            )
            val (_, fullLength) = paths.rivalPaths[i]
            assertEquals(
                "rival $i must be fully revealed",
                fullLength,
                JourneyChartRenderer.rivalRevealedLength(layout, paths, i, sweep),
                0.5f,
            )
        }
    }

    @Test
    fun `solo mode reveals the whole main curve at the end of playback`() {
        val main = player("me", listOf(unlock(1, t0, 10), unlock(2, t0 + 5 * day, 20), unlock(3, t0 + 20 * day, 30)))
        val layout = layoutOf(main, emptyList(), includeNodes = true)
        val paths = ChartPaths(layout)

        assertEquals(paths.mainLength, JourneyChartRenderer.revealedLength(layout, paths, 1f), 0.5f)
        assertEquals(0f, JourneyChartRenderer.revealedLength(layout, paths, 0f), 0.5f)
        assertTrue(
            "solo sweep must reach the main curve's end",
            JourneyChartRenderer.sweepX(layout, paths, 1f) >= layout.main.maxX - 0.5f,
        )
        // Every badge node must be visible once the curve is fully drawn.
        val revealed = JourneyChartRenderer.revealedLength(layout, paths, 1f)
        assertTrue("fixture must have nodes", layout.nodes.isNotEmpty())
        paths.nodeLengths.forEachIndexed { i, len ->
            assertTrue("node $i hidden at the end (len $len > revealed $revealed)", len <= revealed + 0.5f)
        }
    }

    @Test
    fun `lengthAtX is monotonic across the plot area`() {
        val layout = compareLayout()
        val paths = ChartPaths(layout)
        val from = layout.padL
        val to = layout.width - layout.padR

        listOf(ChartPaths.MAIN, 0).forEach { pathIndex ->
            var previous = -1f
            repeat(20) { step ->
                val x = from + (to - from) * step / 19f
                val len = paths.lengthAtX(pathIndex, x)
                assertTrue("path $pathIndex not monotonic at x=$x ($len < $previous)", len >= previous - 0.01f)
                assertTrue("path $pathIndex length out of range at x=$x", len >= 0f)
                previous = len
            }
        }
    }

    /**
     * Smoke test for the draw pass itself: the composable around it is only compile-verified, so
     * this is what catches a bad clip, a zero-sized image or a text measure blowing up at runtime.
     * It draws the busiest frame available (nodes, medals, a rival, a tooltip) with no bitmaps at
     * all, which is also the state the chart is in while images are still loading.
     */
    @Test
    fun `drawing a full frame does not throw`() {
        val main = player(
            "me",
            listOf(
                unlock(1, t0, 10),
                unlock(2, t0 + 5 * day, 20, gameId = 2).copy(type = AchievementType.MISSABLE),
                unlock(3, t0 + 20 * day, 30).copy(type = AchievementType.WIN_CONDITION),
            ),
        ).let { it.copy(medals = listOf(Medal(AwardKind.MASTERED, t0 + 10 * day, 1, null, "G1"))) }
        val rival = player("rival", listOf(unlock(11, t0 + 2 * day, 5), unlock(12, t0 + 60 * day, 40)))
        val frame = JourneyFrame(
            mode = JourneyMode.SPAN, main = main, rivals = listOf(rival),
            spanFromMillis = 0, spanToMillis = t0 + 100 * day,
        )
        val layout = LayoutEngine.layout(frame, includeNodes = true)
        val paths = ChartPaths(layout)

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val measurer = TextMeasurer(
            fallbackFontFamilyResolver = createFontFamilyResolver(context),
            fallbackDensity = Density(1f),
            fallbackLayoutDirection = LayoutDirection.Ltr,
        )
        val target = ImageBitmap(LayoutEngine.W.toInt(), LayoutEngine.H.toInt())
        val style = ChartStyle(
            background = Color.Black, panel = Color.DarkGray, grid = Color.Gray,
            ink = Color.White, inkDim = Color.LightGray, accent = Color.Cyan,
            seriesColors = List(12) { Color.Magenta }, rivalColors = List(3) { Color.Green },
            typeColors = AchievementType.values().associateWith { Color.Red },
            silver = Color.LightGray, gold = Color.Yellow, labelFont = FontFamily.Default,
        )

        listOf(0f, 0.5f, 1f).forEach { progress ->
            CanvasDrawScope().draw(
                Density(1f), LayoutDirection.Ltr, Canvas(target),
                Size(LayoutEngine.W, LayoutEngine.H),
            ) {
                with(JourneyChartRenderer) {
                    drawJourney(
                        layout = layout, paths = paths, frame = frame, style = style,
                        textMeasurer = measurer, bitmaps = emptyMap(), userName = "me",
                        progress = progress, cursorNodeIndex = 0, nodeAppearProgress = { 1f },
                        focused = true, scale = 1f,
                    )
                }
            }
        }
    }

    /**
     * The tooltip regression the chart was reported for: only the game name showed up.
     *
     * [ChartStyle.panel] is the theme's `surfaceVariant`, which this app defines as the foreground
     * colour at 12% alpha; the tooltip repainted it at 0.96 alpha and drew `ink`-coloured text onto
     * a now-foreground-coloured panel. This renders a tooltip with both the fixed (composited) and
     * the raw translucent panel colour and insists that in each case the panel reads as a dark
     * surface with a modest amount of light text on it, rather than a solid block of ink.
     */
    @Test
    fun `tooltip text contrasts with its panel`() {
        listOf(
            "composited panel" to Color(0xFF1F1F1F),
            "translucent theme panel" to Color.White.copy(alpha = 0.12f),
        ).forEach { (name, panel) ->
            val style = tooltipStyle(panel)
            val (frame, layout, paths) = tooltipFixture()
            val plain = renderToBitmap(frame, layout, paths, style, cursorNodeIndex = null)
            val withTip = renderToBitmap(frame, layout, paths, style, cursorNodeIndex = 1)

            val box = changedBounds(plain, withTip)
                ?: throw AssertionError("$name: the cursor drew nothing at all")
            val area = (box[2] - box[0] + 1) * (box[3] - box[1] + 1)
            assertTrue("$name: tooltip area $area is too small to be a panel", area > 20_000)

            val map = withTip.toPixelMap()
            var ink = 0
            for (y in box[1]..box[3]) for (x in box[0]..box[2]) {
                val c = map[x, y]
                if (c.red > 0.6f && c.green > 0.6f && c.blue > 0.6f) ink++
            }
            assertTrue("$name: no text pixels inside the tooltip", ink > 200)
            assertTrue(
                "$name: the panel itself is ink coloured ($ink of $area px), which is the bug",
                ink < area / 3,
            )
        }
    }

    /**
     * The reveal-effect clock must only ever be asked about icons that are actually on screen.
     *
     * [JourneyEffectClock.nodeSeconds] is not a pure read: when armed it *stamps* "this node
     * appeared now" the first time it is asked about one. Asking before the reveal gate - which a
     * draw-order "optimisation" once did, on the reasoning that the clock lookup was the cheap half
     * - stamps the entire layout on the first frame, so by the time the sweep actually reaches a
     * badge its pop-in has finished and its floating "+N" has expired. Every reveal effect in the
     * chart dies 1.2 seconds into any playback, silently, with nothing failing.
     *
     * The clock is the thing to assert on rather than the pixels: a stamp is invisible in the frame
     * that makes it.
     */
    @Test
    fun `an armed effect clock is not stamped for nodes the sweep has not reached`() {
        val main = player("me", (1..40).map { unlock(it, t0 + it * day, 10) })
        val frame = JourneyFrame(
            mode = JourneyMode.SPAN, main = main, rivals = emptyList(),
            spanFromMillis = 0, spanToMillis = t0 + 100 * day,
        )
        val layout = LayoutEngine.layout(frame, includeNodes = true)
        val paths = ChartPaths(layout)
        assertTrue("the fixture needs enough nodes to tell the two orderings apart", layout.nodes.size >= 20)

        val clock = JourneyEffectClock()
        val start = 1_000_000_000L
        // One frame near the beginning of playback, with the clock armed exactly as the player
        // arms it while a journey is running. A tenth in, so a few badges are genuinely on stage
        // and the rest of the chart is still ahead of the sweep.
        renderToBitmap(
            frame, layout, paths, tooltipStyle(Color(0xFF1F1F1F)),
            progress = 0.1f,
            nodeEffectSeconds = { index -> clock.nodeSeconds(index, start, true) },
        )

        // arm = false, so probing cannot itself stamp anything: -1 means "never revealed".
        val later = start + 3_000_000_000L
        val stamped = layout.nodes.indices.count { clock.nodeSeconds(it, later, false) >= 0f }
        assertTrue("nothing was drawn at all - the fixture or the gate is wrong", stamped >= 1)
        assertTrue(
            "the first frame stamped $stamped of ${layout.nodes.size} nodes; only what the sweep " +
                "has reached may be stamped",
            stamped <= layout.nodes.size / 3,
        )

        // And the concrete consequence: the last badge on the chart still has its whole effect
        // ahead of it three seconds in, rather than one that expired before the sweep arrived.
        val last = layout.nodes.indices.maxByOrNull { layout.nodes[it].x }!!
        assertEquals(
            "a node the sweep never reached must start its effect when it is revealed, not at frame one",
            0f,
            clock.nodeSeconds(last, later, true),
            1e-4f,
        )
    }

    /** The busiest frame the renderer can be asked for: cursors, effects mid-flight, no images. */
    @Test
    fun `drawing with cursors and live effects does not throw`() {
        val (frame, layout, paths) = tooltipFixture()
        val style = tooltipStyle(Color(0xFF1F1F1F))
        listOf(0f to 0f, 0.4f to 0.6f, 1.9f to 1.1f).forEach { (medalSeconds, nodeSeconds) ->
            listOf(0 to null, null to 0).forEach { (node, medal) ->
                renderToBitmap(
                    frame, layout, paths, style,
                    cursorNodeIndex = node,
                    cursorMedalIndex = medal,
                    nodeEffectSeconds = { nodeSeconds },
                    medalEffectSeconds = { medalSeconds },
                    medalWashSeconds = { medalSeconds },
                )
            }
        }
    }

    /**
     * Medal playback must key off the anchor, never off the relaxed centre.
     *
     * Two medals won two hours apart sit within four virtual units of each other on the curve, and
     * the relaxation pass fans them ~50 units apart to stop the discs overlapping. Judged by the
     * drawn x, the right-hand one would stay hidden long after the sweep passed the moment it was
     * actually won, and its leader line would have pointed at curve the reveal had not drawn yet.
     */
    @Test
    fun `medal visibility follows the anchor, not the relaxed position`() {
        val (_, layout, paths) = fannedMedalFixture()

        val count = layout.medals.size
        assertEquals(4, count)
        assertTrue(
            "fixture medals must be near-simultaneous (${layout.medals.map { it.anchorX }})",
            layout.medals.maxOf { it.anchorX } - layout.medals.minOf { it.anchorX } < 6f,
        )
        assertTrue(
            "fixture must actually fan the cluster apart (${layout.medals.map { it.x }})",
            layout.medals.maxOf { it.x } - layout.medals.minOf { it.x } > 20f,
        )
        val rightmost = layout.medals.maxOf { it.x }
        val lastAnchor = layout.medals.maxOf { it.anchorX }
        assertTrue(
            "fixture's fan must push a medal well past its own anchor",
            rightmost > lastAnchor + 15f,
        )

        // The discriminating case: the sweep has passed every anchor but not the drawn position of
        // the ones the fan pushed ahead. Gated on the relaxed x this reveals only some of them;
        // gated on the anchor, which is where they were actually won, it reveals the lot.
        val justPast = progressForSweep(layout, paths, lastAnchor + 1f)
        assertEquals(
            "every medal was won before this sweep",
            count,
            JourneyChartRenderer.visibleMedalIndices(layout, paths, justPast).size,
        )

        // ... while the discs the fan pushed ahead of the line wait for the line: the award's
        // effects fire at the moment it was won, the medallion lands when the sweep gets to where
        // it is drawn.
        val drawn = JourneyChartRenderer.drawnMedalIndices(layout, paths, justPast)
        assertEquals(
            "only the medals the sweep has reached should be painted",
            layout.medals.indices.filter { layout.medals[it].x <= lastAnchor + 1f },
            drawn,
        )
        assertTrue("the fan must leave at least one medallion unpainted", drawn.size < count)
        val pastBoth = progressForSweep(layout, paths, rightmost + 2f)
        assertEquals(
            "every medallion is painted once the sweep has passed every disc",
            count,
            JourneyChartRenderer.drawnMedalIndices(layout, paths, pastBoth).size,
        )

        // Before the first anchor, none of them has happened yet.
        val first = layout.medals.minByOrNull { it.anchorX }!!
        val before = progressForSweep(layout, paths, layout.medals.minOf { it.anchorX } - 1f)
        assertEquals(0, JourneyChartRenderer.visibleMedalIndices(layout, paths, before).size)
        assertEquals(0, JourneyChartRenderer.drawnMedalIndices(layout, paths, before).size)
        assertNull(JourneyChartRenderer.medalAt(layout, paths, before, first.x, first.y, 12f))
        assertEquals(count, JourneyChartRenderer.drawnMedalIndices(layout, paths, 1f).size)
    }

    /**
     * Everything the user can point at follows the painted disc.
     *
     * A tap and a D-pad step land on a medallion, so neither may reach one the sweep has not
     * arrived at yet: tapping blank stage would open a tooltip pointing at nothing, and stepping
     * onto it would pan a zoomed viewport off to an empty patch of chart.
     */
    @Test
    fun `medal hit testing and cursor stops wait for the painted disc`() {
        val (_, layout, paths) = fannedMedalFixture()
        val lastAnchor = layout.medals.maxOf { it.anchorX }
        val rightmost = layout.medals.maxOf { it.x }
        // The outermost disc of the cluster is the one the fan pushed furthest past its moment;
        // the innermost is one the sweep has certainly reached by the last anchor.
        val ahead = layout.medals.indices.maxByOrNull { layout.medals[it].x }!!
        val behind = layout.medals.indices.minByOrNull { layout.medals[it].x }!!
        val fanned = layout.medals[ahead]
        val reached = layout.medals[behind]

        // Every award has happened; the outermost disc is not on stage yet.
        val justPast = progressForSweep(layout, paths, lastAnchor + 1f)
        assertNull(
            "a medallion the sweep has not reached must not be tappable",
            JourneyChartRenderer.medalAt(layout, paths, justPast, fanned.x, fanned.y, 12f),
        )
        assertEquals(
            behind,
            JourneyChartRenderer.medalAt(layout, paths, justPast, reached.x, reached.y, 12f),
        )
        val stops = mergeCursorStops(
            layout,
            JourneyChartRenderer.visibleNodeIndices(layout, paths, justPast),
            JourneyChartRenderer.drawnMedalIndices(layout, paths, justPast),
        )
        assertTrue(
            "the D-pad must not stop on an unpainted medallion",
            ChartCursor.Medal(ahead) !in stops,
        )
        assertTrue(
            "the painted medallion must still be a stop",
            ChartCursor.Medal(behind) in stops,
        )

        // Once the sweep has passed every disc, all of them are reachable again.
        val pastBoth = progressForSweep(layout, paths, rightmost + 2f)
        assertEquals(
            ahead,
            JourneyChartRenderer.medalAt(layout, paths, pastBoth, fanned.x, fanned.y, 12f),
        )
        val laterStops = mergeCursorStops(
            layout,
            JourneyChartRenderer.visibleNodeIndices(layout, paths, pastBoth),
            JourneyChartRenderer.drawnMedalIndices(layout, paths, pastBoth),
        )
        assertTrue(ChartCursor.Medal(ahead) in laterStops)
        assertTrue(ChartCursor.Medal(behind) in laterStops)
    }

    /**
     * The award's effects fire at the moment it was won, so they have to be thrown from the point
     * on the curve where it was won. Centred on the medallion instead, a fanned-out award's
     * confetti bursts out of the patch of empty stage where its disc has not landed yet.
     */
    @Test
    fun `confetti is thrown from the anchor, not from the fanned-out disc`() {
        val (frame, layout, paths) = fannedMedalFixture()
        val style = tooltipStyle(Color(0xFF1F1F1F))
        val ahead = layout.medals.indices.maxByOrNull { layout.medals[it].x }!!
        val fanned = layout.medals[ahead]
        val justPast = progressForSweep(layout, paths, layout.medals.maxOf { it.anchorX } + 1f)

        val quiet = renderToBitmap(frame, layout, paths, style, progress = justPast)
        val bursting = renderToBitmap(
            frame, layout, paths, style, progress = justPast,
            // Only the fanned-out award is mid-burst; the wash stays off so the confetti is the
            // only thing that changed between the two renders.
            medalEffectSeconds = { index -> if (index == ahead) 0.3f else -1f },
        )

        val box = changedBounds(quiet, bursting)
            ?: throw AssertionError("the burst drew nothing at all")
        val centerX = (box[0] + box[2]) / 2f
        assertTrue(
            "the burst is centred at $centerX, nearer the disc at ${fanned.x} than the anchor at ${fanned.anchorX}",
            abs(centerX - fanned.anchorX) < abs(centerX - fanned.x),
        )
        assertTrue(
            "the burst must stay around its anchor (${box[0]}..${box[2]} vs ${fanned.anchorX})",
            abs(centerX - fanned.anchorX) < 20f,
        )
    }

    /**
     * Badge pop timing: a badge waits for the line, not just for the moment it was won.
     *
     * The collision pass can push a badge in a cluster tens of units to the right of its anchor.
     * Gated on the anchor alone (the old rule) that badge pops in over blank stage well before the
     * curve reaches it, which is what the chart was reported for. Both gates have to open: the
     * anchor has to be revealed *and* the sweep has to have passed where the disc is drawn.
     */
    @Test
    fun `a badge pushed ahead of its anchor waits for the sweep to reach it`() {
        // A tight cluster early on, then room to breathe: the cluster is what the relaxation pass
        // has to fan out, and the later unlocks keep the sweep from finishing while it does.
        val unlocks = listOf(
            unlock(1, t0, 100),
            unlock(2, t0 + 3_600_000L, 100),
            unlock(3, t0 + 2 * 3_600_000L, 100),
            unlock(4, t0 + 3 * 3_600_000L, 100),
            unlock(5, t0 + 40 * day, 10),
            unlock(6, t0 + 90 * day, 10),
        )
        val main = player("me", unlocks)
        val layout = layoutOf(main, emptyList(), includeNodes = true)
        val paths = ChartPaths(layout)

        val pushed = layout.nodes.indices
            .filter { layout.nodes[it].x > layout.nodes[it].anchorX + 12f }
            .maxByOrNull { layout.nodes[it].x - layout.nodes[it].anchorX }
            ?: throw AssertionError("fixture never fanned a badge ahead of its anchor")
        val node = layout.nodes[pushed]

        // Between the two: the anchor is revealed, the drawn disc is not yet behind the sweep.
        val between = progressForSweep(layout, paths, (node.anchorX + node.x) / 2f)
        assertTrue(
            "the anchor must already be revealed at this sweep",
            paths.nodeLengths[pushed] <= JourneyChartRenderer.revealedLength(layout, paths, between) + 0.5f,
        )
        assertTrue(
            "badge $pushed is drawn at ${node.x} but the sweep is only at " +
                "${JourneyChartRenderer.sweepX(layout, paths, between)}",
            pushed !in JourneyChartRenderer.visibleNodeIndices(layout, paths, between),
        )
        assertNull(
            "an unpainted badge must not be hit testable either",
            JourneyChartRenderer.nodeAt(layout, paths, between, node.x, node.y, 12f),
        )

        // Past the drawn position: both gates are open.
        val past = progressForSweep(layout, paths, node.x + 2f)
        assertTrue(
            "badge $pushed must appear once the sweep has passed it",
            pushed in JourneyChartRenderer.visibleNodeIndices(layout, paths, past),
        )
        assertEquals(
            pushed,
            JourneyChartRenderer.nodeAt(layout, paths, past, node.x, node.y, 12f),
        )

        // ... and nothing is left behind at the end of playback, however far the fan pushed it.
        assertEquals(
            layout.nodes.size,
            JourneyChartRenderer.visibleNodeIndices(layout, paths, 1f).size,
        )
    }

    /**
     * Z-order: the curves draw over the icons.
     *
     * The line is the story the chart tells, and an icon parked on top of it reads as a break in
     * it. Asserted on a MEDALLION rather than a badge: badges are now held a radius clear of their
     * anchor's height precisely so the line cannot cross their art (see
     * [LayoutEngine]'s curve clearance), while a medal still sits on its anchor - which is a point
     * on the curve - so the line passes straight through the middle of the disc. With the curve on
     * top the accent colour is present at the centre of it, and with the icon on top it is not.
     */
    @Test
    fun `the main curve draws over the icons`() {
        val main = player("me", listOf(unlock(1, t0, 10), unlock(2, t0 + 40 * day, 40), unlock(3, t0 + 90 * day, 20)))
            .let { it.copy(medals = listOf(Medal(AwardKind.MASTERED, t0 + 40 * day, 1, null, "Game One"))) }
        val frame = JourneyFrame(
            mode = JourneyMode.SPAN, main = main, rivals = emptyList(),
            spanFromMillis = 0, spanToMillis = t0 + 90 * day,
        )
        val layout = LayoutEngine.layout(frame, includeNodes = true)
        val paths = ChartPaths(layout)
        val accent = Color(0xFF4FD2FF)
        val style = tooltipStyle(Color(0xFF1F1F1F))

        val medal = layout.medals.singleOrNull() ?: throw AssertionError("fixture must have one medallion")
        // Every icon is held clear of the line now, medallions included - but only by a shade
        // under its own radius, so the anchor (the point the curve passes through) still lands
        // inside the disc. That overlap is what makes the sample below discriminating.
        assertTrue(
            "the curve must still cross this medallion (${medal.y} vs anchor ${medal.anchorY}, r ${medal.radius})",
            abs(medal.y - medal.anchorY) < medal.radius,
        )

        val bitmap = renderToBitmap(frame, layout, paths, style, cursorNodeIndex = null)
        val map = bitmap.toPixelMap()
        var accentPixels = 0
        // Sampled where the LINE is - the anchor, a point on the curve - which this medallion's
        // disc covers well inside its radius. That is the one place the two layers compete: with
        // the curve on top the accent is there, with the icon on top it is not.
        val cx = medal.anchorX.toInt()
        val cy = medal.anchorY.toInt()
        for (y in (cy - 2)..(cy + 2)) for (x in (cx - 2)..(cx + 2)) {
            if (x !in 0 until map.width || y !in 0 until map.height) continue
            val c = map[x, y]
            if (abs(c.red - accent.red) < 0.15f &&
                abs(c.green - accent.green) < 0.15f &&
                abs(c.blue - accent.blue) < 0.15f
            ) {
                accentPixels++
            }
        }
        assertTrue(
            "the curve is hidden behind the medallion at (${medal.anchorX}, ${medal.anchorY})",
            accentPixels > 0,
        )
    }

    /** Solo mode sweeps along the main curve, so invert it to get the progress for an x. */
    private fun progressForSweep(layout: ChartLayout, paths: ChartPaths, x: Float): Float {
        var lo = 0f
        var hi = 1f
        repeat(40) {
            val mid = (lo + hi) / 2f
            if (JourneyChartRenderer.sweepX(layout, paths, mid) < x) lo = mid else hi = mid
        }
        return (lo + hi) / 2f
    }

    @Test
    fun `medal hit testing follows the sweep`() {
        val (_, layout, paths) = tooltipFixture()
        assertTrue("fixture must have a medal", layout.medals.isNotEmpty())
        val medal = layout.medals.first()
        assertEquals(
            0,
            JourneyChartRenderer.medalAt(layout, paths, 1f, medal.x, medal.y, 10f),
        )
        assertNull(
            "a medal past the sweep must not be hit testable",
            JourneyChartRenderer.medalAt(layout, paths, 0f, medal.x, medal.y, 10f),
        )
    }

    @Test
    fun `progress moves the reveal forward monotonically`() {
        val layout = compareLayout()
        val paths = ChartPaths(layout)
        var previous = -1f
        repeat(11) { step ->
            val len = JourneyChartRenderer.revealedLength(layout, paths, step / 10f)
            assertTrue("reveal went backwards at ${step / 10f}", len >= previous - 0.01f)
            previous = len
        }
    }

    /**
     * Viewport culling is what makes raising the icon budget for a long or dense journey safe: the
     * layout may place hundreds of badges, but a zoomed-in frame must only ever pay to draw the
     * ones actually inside the viewport. This does not exercise the real pan/zoom canvas transform
     * (that lives in `JourneyChart.kt`'s `withStage`, which this harness never wraps the draw call
     * in) - [ChartZoom] only ever feeds `drawJourney` a rectangle to test icons against here, so
     * proving a far-away badge's plate never gets painted when [view] excludes it is exactly what
     * the renderer's half of the feature has to guarantee. [ViewportCullTest] covers the maths.
     */
    @Test
    fun `a zoomed view culls a badge placed far outside the viewport`() {
        val main = player(
            "me",
            listOf(unlock(1, t0, 10), unlock(2, t0 + 45 * day, 10), unlock(3, t0 + 90 * day, 10)),
        )
        val frame = JourneyFrame(
            mode = JourneyMode.SPAN, main = main, rivals = emptyList(),
            spanFromMillis = 0, spanToMillis = t0 + 90 * day,
        )
        val layout = LayoutEngine.layout(frame, includeNodes = true)
        val paths = ChartPaths(layout)
        assertEquals(3, layout.nodes.size)
        val near = layout.nodes[0]
        val far = layout.nodes[1]
        // The runner sits on the last curve point at progress = 1; picking the *middle* unlock as
        // the culled one keeps its check box well clear of the runner's own panel-coloured backing.
        assertTrue(
            "fixture badges must be far apart for the zoom below to only cover one",
            far.x - near.x > 400f,
        )

        val style = tooltipStyle(Color(0xFF223344))
        // The plate a badge's art sits on, sampled with (blank) art supplied for every badge.
        // The badge's own colour is the player's now, which is also the colour of the curve
        // running through the very place a culled badge would have been - so the plate is the one
        // thing on this fixture that says "a badge was painted here" and nothing else does.
        val art = layout.nodes.associate { RaUrls.badge(it.unlock.badgeName) to ImageBitmap(8, 8) }
        val badgeColor = style.panel
        // 4x zoom anchored on the first badge: at 4x the visible span is W/4 x H/4 (plus whatever
        // the edge clamp adds), comfortably short of the 400+ units separating the two badges.
        val view = ChartZoom.NONE.zoomedTo(4f, Offset(near.x, near.y))
        // Viewport dimensions, the way the renderer itself calls this (see visibleBounds).
        val rect = ViewportCull.visibleRect(view, LayoutEngine.W, LayoutEngine.H)
        assertFalse(
            "fixture must actually place the far badge outside the culled rect",
            ViewportCull.intersects(rect, far.x, far.y, far.r, 40f),
        )

        val zoomed = renderToBitmap(frame, layout, paths, style, view = view, bitmaps = art)
        val identity = renderToBitmap(frame, layout, paths, style, view = ChartZoom.NONE, bitmaps = art)

        assertTrue(
            "the near badge's plate must still be painted",
            countColorNear(zoomed, near.x, near.y, badgeColor) > 30,
        )
        // A stray anti-aliased pixel from the grid or the curve passing nearby can land within the
        // colour tolerance by chance; the badge itself (plate, ring, halo) would paint dozens, so a
        // handful is still a clean "nothing was actually drawn here" signal.
        assertTrue(
            "the far badge must be culled out of the zoomed render (found ${countColorNear(zoomed, far.x, far.y, badgeColor)} matching px)",
            countColorNear(zoomed, far.x, far.y, badgeColor) < 5,
        )
        assertTrue(
            "baseline: the far badge is painted without culling",
            countColorNear(identity, far.x, far.y, badgeColor) > 30,
        )
    }

    // --- tooltip placement inside what is on screen ---

    /**
     * The bug: a tooltip is clamped so it fits the chart, and on a widened chart "the chart" runs
     * for thousands of virtual units past the right of the window. A badge tapped near the right
     * edge of a follow-camera view therefore had its panel placed to the *right* of it - inside the
     * layout, entirely off screen.
     *
     * Placement is checked directly rather than through a render: the box lands at virtual x's far
     * outside the 1060-wide test bitmap, so the pixels cannot answer this question.
     */
    @Test
    fun `a tooltip stays inside the visible window on a wide chart`() {
        val (frame, _, _) = tooltipFixture()
        val wide = LayoutEngine.layout(frame, includeNodes = true, widthFactor = 3f)
        val runner = Offset(wide.width * 0.6f, wide.height / 2f)
        val view = FollowCamera.target(runner, wide.width)
        val bounds = JourneyChartRenderer.visibleBounds(wide, view, Size.Zero)
        assertTrue(
            "fixture must be a window onto the middle of a wide chart",
            bounds.left > 1f && bounds.right < wide.width - 1f,
        )

        // An icon just inside the right edge of the window: the side the tooltip cannot open to.
        val tip = JourneyChartRenderer.TipContent(
            originX = bounds.right - 20f, originY = (bounds.top + bounds.bottom) / 2f,
            originR = 14f, accent = Color.Cyan, iconUrl = null,
            title = "Near the edge", body = listOf("a line of body text" to 1),
        )
        val boxWidth = 320f
        val boxHeight = 120f

        val at = JourneyChartRenderer.tooltipTopLeft(bounds, tip, boxWidth, boxHeight)

        assertTrue("tooltip starts left of the window: ${at.x}", at.x >= bounds.left - 0.01f)
        assertTrue(
            "tooltip runs past the right of the window: ${at.x + boxWidth} > ${bounds.right}",
            at.x + boxWidth <= bounds.right + 0.01f,
        )
        assertTrue("tooltip above the window: ${at.y}", at.y >= bounds.top - 0.01f)
        assertTrue(
            "tooltip below the window: ${at.y + boxHeight} > ${bounds.bottom}",
            at.y + boxHeight <= bounds.bottom + 0.01f,
        )
        // And it opened to the left of the icon rather than off the edge to the right.
        assertTrue("tooltip must open on the side that fits", at.x < tip.originX)
    }

    /** The unwindowed case is unchanged: the whole chart is visible, so the whole chart is the bound. */
    @Test
    fun `visible bounds are the whole chart at the overview`() {
        val (frame, layout, _) = tooltipFixture()
        val bounds = JourneyChartRenderer.visibleBounds(layout, ChartZoom.NONE, Size.Zero)
        assertEquals(0f, bounds.left, 0.01f)
        assertEquals(0f, bounds.top, 0.01f)
        assertEquals(layout.width, bounds.right, 0.01f)
        assertEquals(layout.height, bounds.bottom, 0.01f)
    }

    /**
     * At the identity view nothing is culled: the smoke test above already draws a full frame
     * without a view argument (defaulting to [ChartZoom.NONE]), and this pins that a chart with
     * more icons than fit the old fixed budget draws every one of them once revealed, unculled.
     */
    @Test
    fun `identity view draws every revealed icon, no culling`() {
        val many = (0 until 250).map { unlock(it, t0 + it * day, (it % 40) + 1) }
        val main = player("me", many)
        val frame = JourneyFrame(
            mode = JourneyMode.SPAN, main = main, rivals = emptyList(),
            spanFromMillis = 0, spanToMillis = t0 + 250 * day,
        )
        val layout = LayoutEngine.layout(frame, includeNodes = true)
        // The x-axis grid thins a 250-badge chart wherever a division carried more than the cap, so
        // what "every icon" means here is every icon the layout placed, not every unlock.
        assertTrue("fixture must still be a busy chart", layout.nodes.size > 60)
        val paths = ChartPaths(layout)
        assertEquals(
            "every badge the layout placed must be revealed and drawable at the end of playback",
            layout.nodes.size,
            JourneyChartRenderer.visibleNodeIndices(layout, paths, 1f).size,
        )
        // Renders without throwing at the identity view - the "avoid the rect math" path.
        renderToBitmap(frame, layout, paths, tooltipStyle(Color(0xFF1F1F1F)), view = ChartZoom.NONE)
    }

    /**
     * The empty-icon report: a badge whose art never decoded drew the panel plate and a ring around
     * nothing at all, which on screen is a hollow circle. Rendered twice - once with no bitmaps at
     * all, once with every badge's art present - the two must not look the same: the first fills
     * the plate with the node's own colour, the second leaves the plate to the art.
     */
    @Test
    fun `a badge with no art falls back to a filled disc, never an empty plate`() {
        val (frame, layout, paths) = tooltipFixture()
        val panelColor = Color(0xFF223344)
        val style = tooltipStyle(panelColor)
        assertTrue("fixture must have badges to draw", layout.nodes.isNotEmpty())
        val art = layout.nodes.associate { RaUrls.badge(it.unlock.badgeName) to ImageBitmap(8, 8) }

        val fallback = renderToBitmap(frame, layout, paths, style)
        val withArt = renderToBitmap(frame, layout, paths, style, bitmaps = art)

        // The runner is drawn in the accent too, and at the end of playback it sits on the last
        // badge of the curve: its disc and its halo would be counted as that badge's own fill in
        // both renders, which says nothing about whether the badge took the fallback. Stepped over
        // rather than sampled away, so the rest of the chart is still checked at full strength.
        val runner = paths.positionAt(ChartPaths.MAIN, paths.mainLength)
        layout.nodes
            .filterNot { hypot(it.x - runner.x, it.y - runner.y) < it.r + RUNNER_FOOTPRINT }
            .also { assertTrue("the fixture must keep badges clear of the runner", it.isNotEmpty()) }
            .forEach { node: com.twinlore.app.journey.domain.NodeLayout ->
            val box = (node.r * 0.6f).toInt()
            val filled = nodeColorPixels(fallback, node.x, node.y, box, style.accent)
            assertTrue(
                "badge at (${node.x}, ${node.y}) drew an empty plate: only $filled px of its own colour",
                filled > 40,
            )
            assertTrue(
                "a badge whose art loaded must keep its plate for the art, not take the fallback",
                nodeColorPixels(withArt, node.x, node.y, box, style.accent) < filled / 4,
            )
            // The other half of the same claim: the fallback *replaces* the plate rather than
            // decorating it, so the panel colour is what a badge with art shows and the fallback
            // does not (beyond the panel-coloured glyph it puts in the middle of the disc).
            assertTrue(
                "a badge with art must still sit on its panel plate",
                countColorNear(withArt, node.x, node.y, panelColor, halfBox = box) >
                    countColorNear(fallback, node.x, node.y, panelColor, halfBox = box),
            )
        }
    }

    /**
     * How far the runner's own accent-coloured mark reaches from the point it is drawn at: its halo,
     * with a pixel of slack. Mirrors the renderer's RUNNER_HALO_R.
     */
    private val RUNNER_FOOTPRINT = 23f

    /**
     * Pixels around ([x], [y]) painted in the node's own colour: a warm pink dominated by red,
     * which nothing else on these fixtures is (the plate is a dark blue-grey, the curve over it a
     * cyan, the background black). Counted by hue rather than by an exact match because the main
     * curve's translucent glow passes over every badge and shifts the exact value.
     */
    /**
     * Pixels of a badge's own colour around ([x], [y]).
     *
     * That colour is the player's - the accent for the main player, the rival's palette slot for a
     * rival - since the badges stopped being coloured by their type and their game. What a missing
     * image leaves behind is a solid disc of it.
     */
    private fun nodeColorPixels(
        bitmap: ImageBitmap,
        x: Float,
        y: Float,
        halfBox: Int,
        color: Color,
    ): Int = countColorNear(bitmap, x, y, color, halfBox)

    /**
     * The overlap the player was reported for: its overview strip (date, points, awards) sits along
     * the top of the stage, exactly where the chart draws its own caption and running date. In
     * player mode the chart gives that band up; everywhere else (the summary preview, the video
     * export, both of which have nothing above them) it keeps it.
     */
    @Test
    fun `player mode leaves the top band empty for the overview strip`() {
        val (frame, layout, paths) = tooltipFixture()
        val style = tooltipStyle(Color(0xFF223344))

        val withChrome = renderToBitmap(frame, layout, paths, style)
        val withoutChrome = renderToBitmap(frame, layout, paths, style, showChartChrome = false)

        assertTrue(
            "the caption and the date overlay must paint the top band by default",
            inkPixelsInBand(withChrome, top = 6, bottom = 36) > 200,
        )
        assertEquals(
            "player mode must leave the top band completely clear",
            0,
            inkPixelsInBand(withoutChrome, top = 6, bottom = 36),
        )
        // Everything below the band is untouched: this suppresses two labels, not the chart.
        assertTrue(
            "suppressing the chrome must not change the chart itself",
            changedBounds(withChrome, withoutChrome)!![3] < 36,
        )
    }

    /**
     * A rival's identity marker at the end of their curve, when their picture never arrived.
     *
     * The report this pins: rival avatars that were simply not there on the device. The disc was
     * drawn, but with no bitmap it was the bare panel plate with a thin ring around it, which on a
     * dark stage is a smudge rather than a marker - and a rival's picture is missing far more often
     * than a badge's, because the URL interpolates the name exactly as it was typed into the rival
     * field while the media host's path is case sensitive. It now falls back to a disc in the
     * rival's own colour with the initial of their name in it, the same idea the badges got.
     */
    @Test
    fun `a rival with no avatar still gets a coloured identity disc at their curve's end`() {
        val (frame, layout, paths) = rivalMedalFixture()
        val style = ownerColorStyle()
        val ariaColor = style.rivalColors[0]

        // Deliberately no avatar bitmaps at all, which is what a 404 on the picture leaves behind.
        val render = renderToBitmap(frame, layout, paths, style, bitmaps = emptyMap())

        val aria = layout.rivals.single { it.playerName == "aria" }
        val end = paths.positionAt(layout.rivals.indexOf(aria), paths.lengthOf(layout.rivals.indexOf(aria)))
        // A filled disc, not a ring: the centre itself carries the rival's colour now.
        // Sampled over the disc rather than over the handful of pixels at its dead centre: the
        // initial is punched out of the middle, and a compare chart now draws badges as well, so
        // the marker's own fill is what has to be counted.
        assertTrue(
            "the fallback disc must be filled in the rival's own colour",
            countColorNear(render, end.x, end.y, ariaColor, halfBox = 8) > 60,
        )
        // And the initial is punched out of it in the panel colour, so the disc reads as a face
        // rather than as a dot. The name that used to be printed beside the disc is gone (the main
        // player's runner never had one, and on a three-way compare it put two or three captions
        // into the busiest corner of the stage); the ring colour and the roster say who is who.
        assertTrue(
            "the initial must be drawn inside the fallback disc",
            countColorNear(render, end.x, end.y, style.panel, halfBox = 5) > 0,
        )
    }

    /**
     * And nothing else: the name that used to be printed beside the disc is gone.
     *
     * The main player's own runner never carried a caption, and on a three-way compare it put two
     * or three of them into the busiest corner of the stage; the ring colour, the face inside it,
     * and the roster and compare row in words are what say who is who.
     *
     * Asserted by rendering the same chart twice under two rival names of very different lengths.
     * A caption is the only thing on this frame that is a function of how long a name is - the
     * fallback disc draws one uppercase initial, and both names start with the same letter - so if
     * one were drawn the two bitmaps would differ, and they must not. Sampling a band beside the
     * avatar could not do this job: the caption is right-anchored when the curve ends near the
     * right edge, which puts a short name directly on top of the disc it labels.
     */
    @Test
    fun `no name is printed beside a rival's end avatar`() {
        val style = ownerColorStyle()
        val (shortFrame, shortLayout, shortPaths) = rivalMedalFixture(firstRival = "aria")
        val (longFrame, longLayout, longPaths) = rivalMedalFixture(firstRival = "ariadneofthelabyrinth")
        assertEquals(
            "the two fixtures must be the same picture apart from any caption",
            shortLayout.rivals.map { it.points },
            longLayout.rivals.map { it.points },
        )

        // No avatar bitmaps, so both draw the fallback disc with the same initial in it.
        val short = renderToBitmap(shortFrame, shortLayout, shortPaths, style, bitmaps = emptyMap())
        val long = renderToBitmap(longFrame, longLayout, longPaths, style, bitmaps = emptyMap())

        assertNull(
            "a name printed beside the end avatar would make these two frames differ",
            changedBounds(short, long),
        )
    }

    /**
     * The three awards, graded exactly as the rest of the app grades them (see `AwardGrading`, and
     * the library's own `HighestAwardIcon`): a MASTERED medal is gold with a FILLED dot, a
     * COMPLETED one is gold with a HOLLOW dot, and a BEATEN one is silver with a filled dot. The
     * metal says which tier the award is in; whose it is is said by the owner-coloured halo behind
     * the disc and the owner-coloured leader back to their curve.
     *
     * The chart used to put a filled SILVER dot on a completion and leave the hollow one for
     * beaten, which graded the two lesser awards back to front against the tile, the tab and the
     * drawer showing the same game elsewhere in this app.
     *
     * Two rivals rather than the main player, and neither medal is his: the main curve is drawn in
     * the main colour with a wide glow over the top of the icons (deliberately - see the z-order
     * note in the renderer), which washes the colour of anything sitting on it. A rival's own line
     * is a plain stroke, so a rival's medallion is the one that can be sampled honestly.
     */
    @Test
    fun `award square rings dim toward their owner while the kind dot stays pure metal`() {
        val (frame, layout, paths) = rivalMedalFixture()
        val style = ownerColorStyle()
        val mastered = layout.medals.single { it.medal.ownerName == "aria" }
        val completed = layout.medals.single { it.medal.ownerName == "brix" }
        val ariaColor = style.rivalColors[0]

        val render = renderToBitmap(frame, layout, paths, style)

        // Ring, sampled at whichever edge midpoint is clear. Every curve carries a wide glow now
        // (the rivals' lines gained the main line's weight), and a glow crossing one edge of a
        // medallion washes the expected colour out of tolerance at that point - but no single
        // stroke crosses three edges at once.
        //
        // The rule: every SQUARE boundary takes the owner
        // dim - the award's metal ring included, exactly as the badges' rings do - while every
        // CIRCLE keeps its original colour, so the kind dot stays pure metal over its plain dark
        // hairline.
        assertTrue(
            "a mastery's ring is gold dimmed toward aria's colour",
            ringColorFound(render, mastered.x, mastered.y, mastered.radius, lerp(style.gold, ariaColor, 0.5f)),
        )
        val brixColor = style.rivalColors[1]
        assertTrue(
            "a completion's ring is gold dimmed toward brix's colour",
            ringColorFound(render, completed.x, completed.y, completed.radius, lerp(style.gold, brixColor, 0.5f)),
        )
        assertEquals(
            "never pure metal on the square ring of a compare chart",
            0,
            countColorNear(render, mastered.x - mastered.radius, mastered.y, style.gold, halfBox = 1),
        )

        // Kind dot, in the corner the badge nodes hang their type chip on: a circle, so pure metal.
        assertTrue(
            "a mastered medal's dot is pure gold",
            countColorNear(
                render, mastered.x + mastered.radius * 0.72f, mastered.y - mastered.radius * 0.72f,
                style.gold, halfBox = 4,
            ) > 0,
        )
        // The completion's dot is the hollow one, so it is looked for out at the outline's own
        // radius rather than in the hole in the middle of it.
        val completedDotX = completed.x + completed.radius * 0.72f
        val completedDotY = completed.y - completed.radius * 0.72f
        val completedDotR = max(4.5f, completed.radius * 0.34f)
        assertTrue(
            "a completed medal's dot is the same gold tier",
            countColorNear(
                render, completedDotX, completedDotY, style.gold,
                halfBox = (completedDotR + 2f).toInt(),
            ) > 0,
        )
        assertEquals(
            "and it is never silver, which is the beaten tier's metal",
            0,
            countColorNear(
                render, completedDotX, completedDotY, style.silver,
                halfBox = (completedDotR + 2f).toInt(),
            ),
        )
    }

    /**
     * A BEATEN award is the silver one, and its dot is a disc: the metal is what says it is the
     * lesser award, exactly as it is on the library tile.
     *
     * Rendered against the same fixture with the kind swapped, so the only thing that differs
     * between the two readings is the award's grade.
     */
    @Test
    fun `a beaten award carries a filled dot on a silver ring`() {
        val (frame, layout, paths) = rivalMedalFixture(firstRivalAward = AwardKind.BEATEN)
        val style = ownerColorStyle()
        val beaten = layout.medals.single { it.medal.ownerName == "aria" }

        val render = renderToBitmap(frame, layout, paths, style)

        assertTrue(
            "a beaten award's square ring is silver dimmed toward its winner (a compare chart)",
            ringColorFound(
                render, beaten.x, beaten.y, beaten.radius,
                lerp(style.silver, style.rivalColors[0], 0.5f),
            ),
        )
        val dotX = beaten.x + beaten.radius * 0.72f
        val dotY = beaten.y - beaten.radius * 0.72f
        val dotR = max(4.5f, beaten.radius * 0.34f)
        assertTrue(
            "the dot is filled through the middle, in silver",
            // Loose tolerance on purpose: a curve's wide glow can wash the dot a few percent
            // toward its owner's colour. Silver and gold sit 0.6 apart in blue, so even the
            // widened window cannot mistake one metal for the other - and the never-gold
            // assertion below keeps the strict check.
            countColorNear(
                render, dotX, dotY, style.silver, halfBox = (dotR / 3f).toInt(), tolerance = 0.2f,
            ) > 0,
        )
        assertEquals(
            "and it is never gold, which is the mastery tier's metal",
            0,
            countColorNear(render, dotX, dotY, style.gold, halfBox = (dotR + 2f).toInt()),
        )
    }

    /**
     * And a COMPLETED award's dot is the outline: its middle is whatever is behind it, which is the
     * only thing separating it from the mastery it shares its metal with.
     */
    @Test
    fun `a completed award carries a hollow gold dot`() {
        val (frame, layout, paths) = rivalMedalFixture(firstRivalAward = AwardKind.COMPLETED)
        val style = ownerColorStyle()
        val completed = layout.medals.single { it.medal.ownerName == "aria" }

        val render = renderToBitmap(frame, layout, paths, style)

        val dotX = completed.x + completed.radius * 0.72f
        val dotY = completed.y - completed.radius * 0.72f
        // The dot's own radius, as the renderer sizes it, so the sample box reaches the outline
        // rather than stopping inside the hole it is checking for.
        val dotR = max(4.5f, completed.radius * 0.34f)
        val outline = style.gold
        assertTrue(
            "the outline itself is drawn, in pure gold",
            countColorNear(render, dotX, dotY, outline, halfBox = (dotR + 2f).toInt()) > 0,
        )
        assertEquals(
            "and the middle of it is not filled",
            0,
            countColorNear(render, dotX, dotY, outline, halfBox = (dotR / 3f).toInt()),
        )
    }

    /**
     * No medal captions anywhere, in any mode. Single-game mode used to spell the kind out beside
     * the disc; what that bought was a word in the owner's colour hanging off the busiest part of
     * the chart, saying what the ring and the dot on the disc now say at a glance.
     *
     * Asserted as a difference rather than by looking for the caption's own pixels, for the reason
     * the caption test always had: a caption was drawn in its owner's colour, and the main player's
     * owner colour is the accent the main curve is drawn in, which crosses the same band. The same
     * journey in the two modes is otherwise the same picture - the palette below gives badges one
     * colour in either mode, and the chart chrome (whose caption line does differ) is off - so a
     * caption in either mode is the only thing the two renders could disagree about.
     */
    @Test
    fun `no mode captions its medals`() {
        val base = tooltipStyle(Color(0xFF223344))
        val style = base.copy(typeColors = base.typeColors.mapValues { base.seriesColors.first() })
        val (spanFrame, spanLayout, spanPaths) = tooltipFixture()
        val (gameFrame, gameLayout, gamePaths) = tooltipFixture(mode = JourneyMode.GAME)
        assertEquals(
            "the two fixtures must be the same picture apart from any caption",
            spanLayout.medals.map { it.x to it.y },
            gameLayout.medals.map { it.x to it.y },
        )

        val span = renderToBitmap(spanFrame, spanLayout, spanPaths, style, showChartChrome = false)
        val game = renderToBitmap(gameFrame, gameLayout, gamePaths, style, showChartChrome = false)

        assertNull(
            "one of the two modes still captions its medallions",
            changedBounds(span, game),
        )
    }

    /** Height of one medal caption line, mirroring the renderer's own MEDAL_LABEL size. */
    private val MEDAL_CAPTION_LINE = 12f

    /**
     * Pixels between [top] and [bottom] that are close to [color] (the chart's ink by default),
     * i.e. text drawn into that horizontal band of the stage.
     */
    private fun inkPixelsInBand(
        bitmap: ImageBitmap,
        top: Int,
        bottom: Int,
        color: Color = Color.White,
        xFrom: Int = 0,
        xTo: Int = Int.MAX_VALUE,
        skip: (Int, Int) -> Boolean = { _, _ -> false },
    ): Int {
        val map = bitmap.toPixelMap()
        var count = 0
        for (y in max(0, top)..min(map.height - 1, bottom))
        for (x in max(0, xFrom)..min(map.width - 1, xTo)) {
            if (skip(x, y)) continue
            val c = map[x, y]
            if (abs(c.red - color.red) < 0.15f &&
                abs(c.green - color.green) < 0.15f &&
                abs(c.blue - color.blue) < 0.15f
            ) {
                count++
            }
        }
        return count
    }

    /**
     * Count of pixels within a small box around ([x], [y]) that match [color] closely.
     *
     * [tolerance] is per channel. The default is tight enough that no two of the chart's marks can
     * be confused for each other; a caller looking for a *blend* (a translucent stroke over a known
     * background) can widen it, because where such a stroke lands between two pixel columns decides
     * how much of its alpha each one gets, and the answer is a range rather than a value.
     */
    private fun countColorNear(
        bitmap: ImageBitmap,
        x: Float,
        y: Float,
        color: Color,
        halfBox: Int = 10,
        tolerance: Float = 0.05f,
    ): Int {
        val map = bitmap.toPixelMap()
        val cx = x.toInt()
        val cy = y.toInt()
        var count = 0
        for (yy in (cy - halfBox)..(cy + halfBox)) for (xx in (cx - halfBox)..(cx + halfBox)) {
            if (xx !in 0 until map.width || yy !in 0 until map.height) continue
            val c = map[xx, yy]
            if (abs(c.red - color.red) < tolerance &&
                abs(c.green - color.green) < tolerance &&
                abs(c.blue - color.blue) < tolerance
            ) {
                count++
            }
        }
        return count
    }

    /**
     * UI feedback: the header's interpolated date and the axis regularly disagreed. The zoomed-in
     * overlay is quantized to the axis grid - at twice its resolution, ticking over on gridlines
     * AND halfway between them ("1 Jan, 15 Jan, 1 Feb") - so it and the axis are one clock by
     * construction; a race chart, whose axis is elapsed time, answers nothing.
     */
    @Test
    fun `the zoomed date is the last half-gridline the sweep has passed`() {
        val unlocks = (0..5).map { unlock(it, t0 + it * 12 * day, 10) }
        val frame = JourneyFrame(
            mode = JourneyMode.SPAN, main = player("me", unlocks), rivals = emptyList(),
            spanFromMillis = 0, spanToMillis = t0 + 60 * day,
        )
        val layout = LayoutEngine.layout(frame, includeNodes = true)
        val midX = layout.padL + (layout.width - layout.padL - layout.padR) / 2f

        val label = requireNotNull(JourneyChartRenderer.zoomDateLabel(layout, midX))
        val tMid = layout.axisT0 + (layout.axisT1 - layout.axisT0) / 2
        val passed = layout.ticks.last { it.tMillis <= tMid }
        val next = layout.ticks.first { it.tMillis > passed.tMillis }
        val half = (passed.tMillis + next.tMillis) / 2
        val expected = if (tMid >= half) half else passed.tMillis
        assertEquals(TickGenerator.fmtDate(expected), label)

        // A race chart's axis is elapsed time; there is no calendar date to say.
        val race = JourneyFrame(
            mode = JourneyMode.GAME, main = player("me", unlocks.take(2)),
            rivals = listOf(player("rival", listOf(unlock(90, t0 + day, 10)))),
            spanFromMillis = 0, spanToMillis = t0 + 60 * day,
        )
        val raceLayout = LayoutEngine.layout(race, includeNodes = true)
        assertTrue(raceLayout.raceMode)
        assertEquals(null, JourneyChartRenderer.zoomDateLabel(raceLayout, midX))
    }

    // ---- the badge's own language: hardcore ring, owner dot, streak marks ----

    /**
     * UI feedback: on a compare chart every icon boundary takes its owner's colour the way the
     * art does - the badge's hardcore ring and the medallion's award ring alike. They all wore
     * identical bright champagne or metal there, which was the one set of marks on an icon that
     * said nothing about whose achievement it was.
     */
    @Test
    fun `icon boundaries carry their owner's colour only on a compare chart`() {
        val owner = Color(0xFF3366FF)
        val gold = Color(0xFFFFD54A)

        assertEquals(HARDCORE_RING, JourneyChartRenderer.ownerBoundary(HARDCORE_RING, owner, rivalsPresent = false))
        assertEquals(gold, JourneyChartRenderer.ownerBoundary(gold, owner, rivalsPresent = false))
        val tinted = JourneyChartRenderer.ownerBoundary(HARDCORE_RING, owner, rivalsPresent = true)
        assertTrue("the boundary must move toward the owner's colour", tinted != HARDCORE_RING)
        assertEquals(lerp(HARDCORE_RING, owner, 0.5f), tinted)
        assertEquals(lerp(gold, owner, 0.5f), JourneyChartRenderer.ownerBoundary(gold, owner, rivalsPresent = true))
    }

    /**
     * The ring around a badge says one thing: whether the achievement was earned hardcore. It used
     * to say what type the achievement was (and on a span, what game it belonged to), which is a
     * fact the corner chip already carries, so the loudest thing on an icon was being spent on it.
     *
     * The ring is [HARDCORE_RING], not gold - a fixed pale champagne rather than anything out of
     * the theme or the award palette. It was gold, which meant a hardcore badge and a mastered
     * medal made the identical mark for two unrelated facts; this asserts both halves, that the
     * ring is there in its own colour and that it is not the awards' gold.
     *
     * Counted over the whole badge rather than sampled at one point on its edge: a badge sits ON
     * the curve, and the main line (with its twelve-unit glow) crosses the plate's edge wherever
     * the sample would be taken. Nothing else on the fixture is either colour.
     */
    @Test
    fun `a hardcore badge is ringed in the hardcore colour and a softcore one is not`() {
        val (frame, layout, paths) = hardcoreFixture()
        val style = ownerColorStyle().copy(gold = Color(0xFFFFAA00))
        val art = layout.nodes.associate { RaUrls.badge(it.unlock.badgeName) to ImageBitmap(8, 8) }
        val hardcore = layout.nodes.single { it.unlock.hardcore }
        val softcore = layout.nodes.last { !it.unlock.hardcore }

        val render = renderToBitmap(frame, layout, paths, style, bitmaps = art)

        val box = (hardcore.r + 2f).toInt()
        assertTrue(
            "a hardcore badge must be ringed in the hardcore colour",
            countColorNear(render, hardcore.x, hardcore.y, HARDCORE_RING, halfBox = box) > 60,
        )
        assertEquals(
            "the hardcore ring must not be the awards' gold",
            0,
            countColorNear(render, hardcore.x, hardcore.y, style.gold, halfBox = box),
        )
        assertEquals(
            "a softcore badge must not be ringed at all",
            0,
            countColorNear(render, softcore.x, softcore.y, HARDCORE_RING, halfBox = box),
        )
        // The softcore badge still has an edge: without one it would bleed into whatever art the
        // backdrop is showing behind the stage.
        assertTrue(
            "a softcore badge must keep its plate outline",
            countColorNear(render, softcore.x, softcore.y, style.background, halfBox = box) > 40,
        )
    }

    /**
     * Badges are sharp squares: the plate's ring reaches its own corner instead of being cut away
     * by a radius.
     *
     * Sampled at the TOP-LEFT corner, the one corner of the four with nothing of its own on it and
     * nothing crossing it on this fixture (the type chip hangs off the top-right, the owner dot off
     * the bottom-left, and the main curve runs under the badge's bottom-left). With the old
     * fifth-of-the-width radius the arc's centre sits 0.4r in from each edge, so the corner point is
     * 0.57r away from it and a good way outside the shape: this assertion is the difference between
     * a square and the "curved square" the owner asked us to stop drawing.
     */
    @Test
    fun `a badge's ring reaches the corner of its square`() {
        val (frame, layout, paths) = hardcoreFixture()
        val style = ownerColorStyle().copy(gold = Color(0xFFFFAA00))
        val art = layout.nodes.associate { RaUrls.badge(it.unlock.badgeName) to ImageBitmap(8, 8) }
        val hardcore = layout.nodes.single { it.unlock.hardcore }

        val render = renderToBitmap(frame, layout, paths, style, bitmaps = art)

        assertTrue(
            "the hardcore ring must be drawn at the badge's top-left corner, not curved away",
            countColorNear(render, hardcore.x - hardcore.r, hardcore.y - hardcore.r, HARDCORE_RING, halfBox = 1) > 0,
        )
    }

    /**
     * On a compare chart every badge's ART is washed in its owner's colour, because the art says
     * which achievement it is and nothing at all about whose day it was.
     *
     * A corner dot was tried for this first and taken back out: seven pixels on a badge among two
     * hundred badges is not a mark anybody reads at playback speed. The tint colours the whole
     * icon, so a player's badges read as a family of their colour.
     *
     * Asserted as a comparison rather than against a computed blend: the art is drawn over a plate,
     * at [COMPARE_ART_ALPHA], through a `SrcAtop` filter, so the exact pixel is a stack of four
     * numbers - while "aria's badge is nearer aria's colour than brix's, and brix's the other way
     * round" is the whole of what the tint is for, and it cannot pass if the tint is missing (both
     * badges would be the same grey) or if it is applied from the wrong player.
     *
     * Read off the rivals' badges. The main player's line carries a wide glow in the main player's
     * own colour and every main badge sits on it, so a main badge cannot be sampled honestly; the
     * rivals' lines are plain strokes.
     */
    @Test
    fun `a compare chart tints each badge in its owner's colour`() {
        val (frame, layout, paths) = rivalMedalFixture()
        val style = ownerColorStyle()
        val art = layout.nodes.associate { RaUrls.badge(it.unlock.badgeName) to whiteBitmap() }
        val aria = layout.nodes.first { it.ownerName == "aria" && it.x in 300f..900f }
        val brix = layout.nodes.first { it.ownerName == "brix" && it.x in 300f..900f }

        val render = renderToBitmap(frame, layout, paths, style, bitmaps = art)
        val pixels = render.toPixelMap()

        val ariaPixel = pixels[aria.x.toInt(), aria.y.toInt()]
        val brixPixel = pixels[brix.x.toInt(), brix.y.toInt()]
        assertTrue(
            "aria's badge must read nearer aria's colour than brix's",
            distance(ariaPixel, style.rivalColors[0]) < distance(ariaPixel, style.rivalColors[1]),
        )
        assertTrue(
            "and brix's the other way round",
            distance(brixPixel, style.rivalColors[1]) < distance(brixPixel, style.rivalColors[0]),
        )
        assertTrue(
            "a rival's badge must not read as the main player's colour",
            distance(ariaPixel, style.rivalColors[0]) < distance(ariaPixel, style.accent),
        )
    }

    /**
     * And a solo chart tints nothing: there is one player, every badge is theirs, and washing the
     * art in the accent would spend the art on a fact that is true of the whole screen.
     */
    @Test
    fun `a solo chart leaves its badge art alone`() {
        val (frame, layout, paths) = hardcoreFixture()
        val style = ownerColorStyle()
        val art = layout.nodes.associate { RaUrls.badge(it.unlock.badgeName) to whiteBitmap() }

        val render = renderToBitmap(frame, layout, paths, style, bitmaps = art)
        val node = layout.nodes.last()

        // Counted over the whole badge rather than at its centre: the main line is drawn over the
        // icons by design and its glow crosses the middle of every badge on it, so the untouched
        // art is what is left around the stroke.
        assertTrue(
            "white art on a solo chart stays white",
            countColorNear(render, node.x, node.y, Color.White, halfBox = node.r.toInt()) > 10,
        )
    }

    /** How far apart two colours are, per channel, for a "nearer to" comparison. */
    private fun distance(a: Color, b: Color): Float =
        abs(a.red - b.red) + abs(a.green - b.green) + abs(a.blue - b.blue)

    /** A solid white badge, so a tint over it is the only thing that can change its colour. */
    private fun whiteBitmap(): ImageBitmap {
        val bitmap = ImageBitmap(8, 8)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(8f, 8f)) {
            drawRect(Color.White, size = Size(8f, 8f))
        }
        return bitmap
    }

    /**
     * The tooltip's "Hardcore" is picked out on the meta row and nowhere else.
     *
     * The first cut searched the whole body for the word, and an achievement's description is prose
     * the site's own authors wrote: "Beat the game in Hardcore mode" would have had a word of it lit
     * up in the ring's colour for no reason at all. The highlight names its row by index now, so
     * this asserts the index lands on the row that carries the mode rather than on the description
     * above it, on a fixture built to make the wrong answer look right.
     */
    @Test
    fun `the tooltip highlights Hardcore on the meta row only`() {
        val hard = unlock(1, t0, 10).copy(
            hardcore = true,
            description = "Beat the game in Hardcore mode",
        )
        val frame = JourneyFrame(
            mode = JourneyMode.GAME, main = player("me", listOf(hard)), rivals = emptyList(),
            spanFromMillis = 0, spanToMillis = t0 + 10 * day,
        )
        val layout = LayoutEngine.layout(frame, includeNodes = true)
        val style = ownerColorStyle()

        val tip = JourneyChartRenderer.nodeTip(layout.nodes.first(), layout, frame, style)
        val highlight = requireNotNull(tip.highlight) { "a hardcore unlock must highlight its mode" }

        val row = tip.body[highlight.line].first
        assertEquals("Hardcore", highlight.word)
        assertTrue(
            "the highlighted row must be the meta row, not the description: '$row'",
            row.endsWith("pts · Hardcore"),
        )
        assertTrue(
            "the description must be a row of its own and must not be the highlighted one",
            tip.body.any { it.first == hard.description } && row != hard.description,
        )
        // The date is a row of its own, after the meta row: sharing the meta line meant the
        // ellipsis took the date on the long type labels.
        assertEquals(
            TickGenerator.fmtDate(hard.earnedAtMillis),
            tip.body[highlight.line + 1].first,
        )
    }

    /**
     * A softcore unlock has nothing to pick out: "Softcore" is the absence of the fact the ring
     * marks, and colouring it would make the tooltip say something the chart does not.
     */
    @Test
    fun `a softcore tooltip highlights nothing`() {
        val soft = unlock(1, t0, 10).copy(hardcore = false, description = "Anything at all")
        val frame = JourneyFrame(
            mode = JourneyMode.GAME, main = player("me", listOf(soft)), rivals = emptyList(),
            spanFromMillis = 0, spanToMillis = t0 + 10 * day,
        )
        val layout = LayoutEngine.layout(frame, includeNodes = true)

        val tip = JourneyChartRenderer.nodeTip(layout.nodes.first(), layout, frame, ownerColorStyle())

        assertEquals(null, tip.highlight)
    }

    /**
     * The word darkens on a light theme and does not on a dark one.
     *
     * RA's hardcore yellow is a light colour, which is fine on the dark stage the chart usually
     * draws on and is roughly 1.6:1 against a light theme's panel - a word you cannot read. The ring
     * solves this with a contrast edge; a word has no room for one, so it is pulled toward the ink
     * instead, staying recognisably the same yellow.
     */
    @Test
    fun `the tooltip's hardcore word darkens on a light theme`() {
        val dark = ownerColorStyle().copy(background = Color.Black, ink = Color.White)
        val light = ownerColorStyle().copy(background = Color.White, ink = Color.Black)

        assertEquals(HARDCORE_RING, hardcoreTextColor(dark))
        val onLight = hardcoreTextColor(light)
        assertTrue("the light-theme word must not be the bare ring colour", onLight != HARDCORE_RING)
        assertTrue(
            "the light-theme word must be darker than the ring, not lighter",
            onLight.luminance() < HARDCORE_RING.luminance(),
        )
    }

    /**
     * The ring carries a hairline of the theme's own ink just outside it, which is what stops a
     * light yellow ring from disappearing into a light theme's background.
     *
     * Sampled on a light-theme style, where the edge is the whole point: with the plate, the ring
     * and the background all pale, ink pixels near the badge can only have come from this stroke.
     */
    @Test
    fun `the hardcore ring carries a contrast edge`() {
        val (frame, layout, paths) = hardcoreFixture()
        val style = ownerColorStyle().copy(background = Color.White, ink = Color(0xFF101010))
        val art = layout.nodes.associate { RaUrls.badge(it.unlock.badgeName) to ImageBitmap(8, 8) }
        val hardcore = layout.nodes.single { it.unlock.hardcore }
        val softcore = layout.nodes.last { !it.unlock.hardcore }

        val render = renderToBitmap(frame, layout, paths, style, bitmaps = art)

        // The stroke is drawn at 30% alpha over whatever is behind it, so the sample looks for the
        // blend rather than for the ink itself - and with a wider per-channel tolerance than the
        // chart's marks are told apart by. The hairline is one unit wide on a sharp-cornered square,
        // so it lands split across two pixel columns and each of them gets a share of its alpha; the
        // blend that reaches the buffer is a range around 30%, not 30% exactly. What is being
        // asserted is that there is an ink edge outside the yellow at all.
        val edge = lerp(Color.White, style.ink, 0.3f)
        val box = (hardcore.r + 4f).toInt()
        assertTrue(
            "the hardcore ring must be bounded by an ink hairline",
            countColorNear(render, hardcore.x, hardcore.y, edge, halfBox = box, tolerance = 0.1f) > 20,
        )
        assertTrue(
            "the yellow ring itself must still be drawn",
            countColorNear(render, hardcore.x, hardcore.y, HARDCORE_RING, halfBox = box) > 60,
        )
        assertEquals(
            "a softcore badge gets no hardcore edge",
            0,
            countColorNear(render, softcore.x, softcore.y, edge, halfBox = box),
        )
    }

    /**
     * The cursor highlight belongs to whoever owns the icon it is drawn around.
     *
     * It used to be [ChartStyle.accent] unconditionally, and accent is the main player's identity
     * colour everywhere else on this chart - curve, badge fill, medal ring, floating points - so
     * cursoring a rival's badge on a compare chart ringed someone else's icon in the main player's
     * colour, in the one mode where whose-icon-is-it is the entire point.
     *
     * Sampled just outside the plate, where the ring is drawn (`r + 4`), and on a rival whose badge
     * is clear of the main player's own glowing line.
     */
    @Test
    fun `the cursor ring is drawn in the icon owner's colour`() {
        val (frame, layout, paths) = rivalMedalFixture()
        val style = ownerColorStyle()
        val art = layout.nodes.associate { RaUrls.badge(it.unlock.badgeName) to ImageBitmap(8, 8) }
        val index = layout.nodes.indexOfFirst { it.ownerName == "aria" && it.x in 300f..900f }
        val node = layout.nodes[index]

        val render = renderToBitmap(frame, layout, paths, style, cursorNodeIndex = index, bitmaps = art)

        val box = (node.r + 6f).toInt()
        assertTrue(
            "a rival's cursor ring must be in the rival's own colour",
            countColorNear(render, node.x, node.y, style.rivalColors[0], halfBox = box) > 40,
        )
        assertEquals(
            "a rival's cursor ring must not be in the main player's accent",
            0,
            countColorNear(render, node.x, node.y, style.accent, halfBox = box),
        )
    }

    /**
     * A beaten award is silver at every one of its marks.
     *
     * `medalColor` used to give `BEATEN` its own [ChartStyle.inkDim] while `drawKindDot` and
     * `drawMedalWash` collapsed to "gold if mastered, else silver" - so the same award, in the same
     * reveal moment, showed a silver disc dot and threw dull grey confetti, with no caption on a
     * rivals chart to reconcile the two.
     */
    @Test
    fun `a beaten award throws confetti in its own silver`() {
        val at = t0 + 10 * day
        val main = player("me", (0 until 6).map { unlock(it, t0 + it * 4 * day, 20) })
            .let { it.copy(medals = listOf(Medal(AwardKind.BEATEN, at, 1, null, "Game One"))) }
        val frame = JourneyFrame(
            mode = JourneyMode.SPAN, main = main, rivals = emptyList(),
            spanFromMillis = 0, spanToMillis = t0 + 100 * day,
        )
        val layout = LayoutEngine.layout(frame, includeNodes = true)
        val style = ownerColorStyle().copy(silver = Color(0xFFB0C4DE), inkDim = Color(0xFF808080))

        val render = renderToBitmap(
            frame, layout, ChartPaths(layout), style,
            medalEffectSeconds = { 0.4f },
        )

        val medal = layout.medals.single()
        val box = (JourneyChartRendererBurstReach).toInt()
        assertTrue(
            "a beaten award's confetti must be its own silver",
            countColorNear(render, medal.anchorX, medal.anchorY, style.silver, halfBox = box) > 4,
        )
        assertEquals(
            "a beaten award must not throw dim-ink confetti",
            0,
            countColorNear(render, medal.anchorX, medal.anchorY, style.inkDim, halfBox = box),
        )
    }

    /**
     * A rival's mastery washes the stage exactly the way the main player's does.
     *
     * `drawMedalWash` used to skip every medal with a non-null `ownerName` before it ever asked its
     * clock a question, so a rival's award - however loud its confetti and however bright its own
     * ring - never earned the full-stage flash that says "this one mattered". This is the bug the
     * user reported: a compare journey's mastery landed with no flash at all.
     *
     * Compared as two whole-frame renders rather than sampled at the medal's own anchor pixel: the
     * anchor sits on the rival's curve and inside the medallion's own footprint, both of which paint
     * over the wash regardless of whether it fired. The wash is a full-stage radial gradient, so
     * turning it on must move a wide swath of the canvas even where nothing else is drawn.
     */
    @Test
    fun `a rival's award washes the stage, not just the main player's`() {
        val (frame, layout, paths) = rivalMedalFixture()
        val style = ownerColorStyle()
        val ariaMedal = layout.medals.indexOfFirst { it.medal.ownerName == "aria" }
        assertTrue("fixture must carry aria's award", ariaMedal >= 0)

        val off = renderToBitmap(frame, layout, paths, style, medalWashSeconds = { -1f })
        val on = renderToBitmap(
            frame, layout, paths, style,
            medalWashSeconds = { index -> if (index == ariaMedal) 1f else -1f },
        )

        val bounds = changedBounds(off, on)
        assertTrue("a rival's award must tint the stage; nothing changed", bounds != null)
        val width = bounds!![2] - bounds[0]
        assertTrue(
            "the change must span a meaningful part of the stage, not a sliver a badge or curve " +
                "happened to redraw: $width px wide",
            width > 200,
        )
    }

    /** How far a confetti burst reaches from its anchor, per the renderer's own BURST_MAX. */
    private val JourneyChartRendererBurstReach = 80f

    /**
     * The flame glyphs are gone from the curve. They marked where a streak *was*, permanently, one
     * per run - a dozen little fires stuck to a span's line, competing with the badges for the same
     * strip of stage and saying nothing the burning line underneath was not already saying.
     */
    @Test
    fun `no flame glyph is drawn over the curve`() {
        val style = ownerColorStyle()
        val (twoFrame, twoLayout, _) = streakFixture(days = 2)
        assertTrue("two days is not a streak", twoLayout.streaks.isEmpty())
        assertTrue("fixture must have a journey to draw", twoFrame.main.unlocks.isNotEmpty())

        val (frame, layout, paths) = streakFixture(days = 4)
        val run = layout.streaks.single()
        // Where the glyph used to hang: the midpoint of the run, a little above the curve.
        val midX = (run.startX + run.endX) / 2f
        val midY = LayoutEngine.curveYAt(layout.main, midX, 0f) - 14f

        val render = renderToBitmap(frame, layout, paths, style)

        assertEquals(
            "nothing is drawn where the flame used to hang",
            0,
            countColorNear(render, midX, midY, FLAME_OUTER, halfBox = 7),
        )
    }


    /**
     * A glyph is centred on its own baseline, so that two glyphs resolved from two different fonts
     * - which `!` and `★` are - land in the same place in their chip rather than one of them
     * sitting low. Measured in Robolectric with the real fallback resolver at an 18-unit size in a
     * 24-unit chip, the star's ink went from 2.3 units below the centre (the old radius-fraction
     * offset) to 0.2; centring the measured line box, the step in between, leaves it 1.2 low.
     */
    @Test
    fun `a chip glyph is centred on its baseline whatever box the font gives it`() {
        val center = Offset(100f, 200f)
        val size = 18f
        // Two glyphs of the same size whose fonts put the baseline in different places: the cap
        // band of each has to end up straddling the chip's centre, which is what makes them look
        // like one another. Centring the line box instead would put these 4 units apart.
        listOf(20f, 24f).forEach { baseline ->
            val at = JourneyChartRenderer.glyphTopLeft(center, width = 9, sizePx = size, baseline = baseline)
            val capBottom = at.y + baseline
            val capTop = capBottom - size * 0.7f
            assertEquals(center.y, (capTop + capBottom) / 2f, 0.001f)
            assertEquals(center.x, at.x + 9 / 2f, 0.001f)
        }
    }

    // ---- helpers ----

    /**
     * A main player and a rival with one award each, the fixture for "who won this one". The
     * rival's award lands well after the main player's so the relaxation pass has no reason to put
     * the two discs near each other.
     */
    private fun rivalMedalFixture(
        mode: JourneyMode = JourneyMode.SPAN,
        /** The first rival's name. Only the caption test varies it; everything else reads "aria". */
        firstRival: String = "aria",
        /** Which award the first rival won. Only the hollow-dot test varies it. */
        firstRivalAward: AwardKind = AwardKind.MASTERED,
    ): Triple<JourneyFrame, ChartLayout, ChartPaths> {
        val main = player("me", (0 until 4).map { unlock(it, t0 + it * 5 * day, 20) })
        val aria = player(firstRival, (0 until 4).map { unlock(10 + it, t0 + it * 5 * day, 15) })
            .copy(medals = listOf(Medal(firstRivalAward, t0 + 4 * day, 1, firstRival, "$firstRival · Game One")))
        val brix = player("brix", (0 until 4).map { unlock(20 + it, t0 + it * 5 * day, 10) })
            .copy(medals = listOf(Medal(AwardKind.COMPLETED, t0 + 14 * day, 1, "brix", "brix · Game One")))
        val frame = JourneyFrame(
            mode = mode, main = main, rivals = listOf(aria, brix),
            spanFromMillis = 0, spanToMillis = t0 + 100 * day,
        )
        val layout = LayoutEngine.layout(frame, includeNodes = true)
        return Triple(frame, layout, ChartPaths(layout))
    }

    /**
     * Whether [color] shows at the left, right or bottom midpoint of a square ring of [radius]
     * around ([cx], [cy]). Any-of-three because every curve carries a wide glow that can wash the
     * colour out of tolerance where it crosses an edge - but no single stroke crosses three edges
     * of one medallion at once. The top is left out for the reason the old single-point sample
     * avoided it: the main line's glow rides it on these fixtures.
     */
    private fun ringColorFound(
        render: androidx.compose.ui.graphics.ImageBitmap,
        cx: Float,
        cy: Float,
        radius: Float,
        color: Color,
        tolerance: Float = 0.05f,
    ): Boolean = listOf(
        cx - radius to cy,
        cx + radius to cy,
        cx to cy + radius,
    ).any { (px, py) -> countColorNear(render, px, py, color, halfBox = 1, tolerance = tolerance) > 0 }

    /**
     * The y axis speaks in round numbers: 0 plus multiples of a 1/2/5-shaped step, never the
     * ceiling divided into quarters ("17646, 35293, ..."). The exact final score leads the
     * strip's end panel instead.
     */
    @Test fun `y axis values are round multiples of a 1-2-5 step`() {
        assertEquals(listOf(0, 20000, 40000, 60000), JourneyChartRenderer.niceAxisValues(70585))
        assertEquals(listOf(0, 200, 400, 600, 800), JourneyChartRenderer.niceAxisValues(800))
        assertEquals(listOf(0, 10, 20, 30), JourneyChartRenderer.niceAxisValues(34))
        assertEquals(listOf(0, 500, 1000, 1500, 2000), JourneyChartRenderer.niceAxisValues(2300))
        // A tiny chart labels its integers rather than inventing a step.
        assertEquals(listOf(0, 1, 2, 3), JourneyChartRenderer.niceAxisValues(3))
        // The compact chart caps the y grid at three intervals (owner request); the 2.5-shaped
        // step exists so a cap of three does not collapse to a single faraway line.
        assertEquals(listOf(0, 1000, 2000), JourneyChartRenderer.niceAxisValues(2300, maxIntervals = 3))
        assertEquals(listOf(0, 25000, 50000), JourneyChartRenderer.niceAxisValues(70585, maxIntervals = 3))
    }

    /** One hardcore unlock and one softcore one, far enough apart to sample either separately. */
    private fun hardcoreFixture(): Triple<JourneyFrame, ChartLayout, ChartPaths> {
        val main = player(
            "me",
            listOf(
                unlock(1, t0, 10).copy(hardcore = true),
                unlock(2, t0 + 40 * day, 10),
            ),
        )
        val frame = JourneyFrame(
            mode = JourneyMode.SPAN, main = main, rivals = emptyList(),
            spanFromMillis = 0, spanToMillis = t0 + 100 * day,
        )
        val layout = LayoutEngine.layout(frame, includeNodes = true)
        return Triple(frame, layout, ChartPaths(layout))
    }

    /** A journey that unlocked something on [days] days running, and nothing else. */
    private fun streakFixture(days: Int): Triple<JourneyFrame, ChartLayout, ChartPaths> {
        val main = player("me", (0 until days).map { unlock(it, t0 + it * day + 3_600_000L, 10) })
        val frame = JourneyFrame(
            mode = JourneyMode.SPAN, main = main, rivals = emptyList(),
            spanFromMillis = 0, spanToMillis = t0 + days * day,
        )
        val layout = LayoutEngine.layout(frame, includeNodes = true)
        return Triple(frame, layout, ChartPaths(layout))
    }

    /** Player, rival and award-kind colours far enough apart that a sampled pixel names one. */
    private fun ownerColorStyle() = tooltipStyle(Color(0xFF223344)).copy(
        accent = Color(0xFF00FFFF),
        rivalColors = listOf(Color(0xFFFF00FF), Color(0xFF7CFF00), Color(0xFFFF7A00)),
        gold = Color(0xFFFFD54A),
        silver = Color(0xFFCDD8EA),
    )

    private fun tooltipStyle(panel: Color) = ChartStyle(
        background = Color.Black, panel = panel, grid = Color(0xFF404040),
        ink = Color.White, inkDim = Color.White.copy(alpha = 0.7f), accent = Color(0xFF4FD2FF),
        seriesColors = List(12) { Color(0xFFFF6EC7) }, rivalColors = List(3) { Color(0xFF5FF2D6) },
        typeColors = AchievementType.values().associateWith { Color(0xFFFFD54A) },
        silver = Color(0xFFCDD8EA), gold = Color(0xFFFFD54A), labelFont = FontFamily.Default,
    )

    /**
     * Two awards won two hours apart: their anchors sit within four virtual units of each other on
     * the curve, and the relaxation pass fans the discs ~50 units apart so they do not overlap. The
     * fixture for everything that has to tell "when it was won" apart from "where it is drawn".
     */
    private fun fannedMedalFixture(): Triple<JourneyFrame, ChartLayout, ChartPaths> {
        val unlocks = (0 until 6).map { unlock(it, t0 + it * 4 * day, 20) }
        val at = t0 + 10 * day
        // Four awards inside an afternoon, not two. The collision response damps its sideways
        // component (see LayoutEngine.PUSH_X_DAMP - an icon's x is its moment, so the spread is
        // spent on the height), so a pair of medallions no longer fans far enough in x to make
        // the anchor-versus-drawn distinction visible. A tighter cluster does: the discs still
        // have to stop overlapping, and with four of them the outermost is pushed well past the
        // moment it was won, which is the whole condition this fixture exists to create.
        val main = player("me", unlocks).let {
            it.copy(
                medals = listOf(
                    Medal(AwardKind.MASTERED, at, 1, null, "Game One"),
                    Medal(AwardKind.COMPLETED, at + 3_600_000L, 1, null, "Game Two"),
                    Medal(AwardKind.BEATEN, at + 2 * 3_600_000L, 1, null, "Game Three"),
                    Medal(AwardKind.MASTERED, at + 3 * 3_600_000L, 1, null, "Game Four"),
                ),
            )
        }
        val frame = JourneyFrame(
            mode = JourneyMode.SPAN, main = main, rivals = emptyList(),
            spanFromMillis = 0, spanToMillis = t0 + 100 * day,
        )
        val layout = LayoutEngine.layout(frame, includeNodes = true)
        return Triple(frame, layout, ChartPaths(layout))
    }

    /**
     * A single game with a hundred earned achievements and one award: the shape that showed up the
     * caption limit. Nothing is dropped in this mode, so the icon count sails past any threshold
     * an icon-counting rule could use, while the thing captions actually crowd - medallions - is
     * still one.
     */
    private fun bigGameFixture(): Triple<JourneyFrame, ChartLayout, ChartPaths> {
        val unlocks = (0 until 100).map { unlock(it, t0 + it * 3_600_000L, (it % 10) + 1) }
        val main = player("me", unlocks).let {
            it.copy(medals = listOf(Medal(AwardKind.MASTERED, t0 + 50 * 3_600_000L, 1, null, "Game One")))
        }
        val frame = JourneyFrame(
            mode = JourneyMode.GAME, main = main, rivals = emptyList(),
            spanFromMillis = 0, spanToMillis = t0 + 100 * day,
        )
        val layout = LayoutEngine.layout(frame, includeNodes = true)
        return Triple(frame, layout, ChartPaths(layout))
    }

    private fun tooltipFixture(
        mode: JourneyMode = JourneyMode.SPAN,
    ): Triple<JourneyFrame, ChartLayout, ChartPaths> {
        val unlocks = listOf(
            unlock(1, t0, 10),
            unlock(2, t0 + 5 * day, 25).copy(
                title = "A Long Achievement Title",
                description = "Beat the final boss without taking a single hit on hard mode",
                type = AchievementType.WIN_CONDITION,
            ),
            unlock(3, t0 + 20 * day, 40),
        )
        val main = player("me", unlocks).let {
            it.copy(medals = listOf(Medal(AwardKind.MASTERED, t0 + 10 * day, 1, null, "Game One")))
        }
        val frame = JourneyFrame(
            mode = mode, main = main, rivals = emptyList(),
            spanFromMillis = 0, spanToMillis = t0 + 100 * day,
        )
        val layout = LayoutEngine.layout(frame, includeNodes = true)
        return Triple(frame, layout, ChartPaths(layout))
    }

    // ---- the awards' own guarantee, at the renderer's end of the pipeline ----

    /**
     * The last two gates a medal passes through, for every kind: the reveal gate that decides
     * whether a disc is painted at a given point of playback, and the viewport cull.
     *
     * The layout gates are pinned in `LayoutEngineTest`; this is the half of the guarantee that
     * lives in the renderer. Every award of every kind must be drawn by the end of playback, must
     * be drawn as soon as the sweep has passed both its anchor and where the collision pass put its
     * disc, and must survive the cull at the overview - where by definition nothing is off screen.
     */
    @Test
    fun `every award kind is drawn by the end of playback`() {
        val kinds = AwardKind.values()
        val awards = 21
        val medals = (0 until awards).map {
            Medal(kinds[it % kinds.size], t0 + it * 3 * day, it + 1, null, "G$it")
        }
        val main = player("me", (0 until 60).map { unlock(it, t0 + it * day, 10, gameId = (it % awards) + 1) })
            .copy(medals = medals)
        val frame = JourneyFrame(
            mode = JourneyMode.SPAN, main = main, rivals = emptyList(),
            spanFromMillis = 0, spanToMillis = t0 + awards * 3 * day,
        )
        val layout = LayoutEngine.layout(frame, includeNodes = true)
        val paths = ChartPaths(layout)

        assertEquals("the layout must place every award", awards, layout.medals.size)
        val drawn = JourneyChartRenderer.drawnMedalIndices(layout, paths, 1f)
        assertEquals("an award is not painted at the end of playback", awards, drawn.size)
        kinds.forEach { kind ->
            assertEquals(
                "a $kind award is not painted at the end of playback",
                medals.count { it.kind == kind },
                drawn.count { layout.medals[it].medal.kind == kind },
            )
        }
        // And the disc's own gate never outlives the reveal: everything the anchor has revealed is
        // painted once the sweep has also reached where it was drawn, so no medal can be left in
        // the revealed-but-never-painted state for the whole of a playback.
        assertEquals(
            "the reveal and the paint must agree at the end",
            JourneyChartRenderer.visibleMedalIndices(layout, paths, 1f).sorted(),
            drawn.sorted(),
        )
        // The cull is what a zoomed chart uses; at the overview nothing may be dropped by it.
        val style = tooltipStyle(Color(0xFF223344))
        assertNull(
            "the overview cull must not change the picture",
            changedBounds(
                renderToBitmap(frame, layout, paths, style, view = ChartZoom.NONE),
                renderToBitmap(frame, layout, paths, style, view = ChartZoom.overviewFor(layout.width)),
            ),
        )
    }

    // ---- who is on the chart while it is playing ----

    /**
     * The reported bug, in both modes it was reported in: during playback the only player on the
     * stage with a face was the main one.
     *
     * A rival's avatar used to be drawn only once their line was *complete*, which on a span is the
     * last moment of the replay and on a single-game race is whenever that player happened to
     * finish - so for the whole of an animation two or three players were coloured lines with dots
     * on them and nothing said who any of them were. The face now rides the tip of each rival's
     * curve exactly as the runner rides the main one.
     *
     * Sampled at the tip itself, in that rival's own colour: with no bitmaps the avatar falls back
     * to a disc of it, so a face and no face are the difference between a ring of that colour and a
     * solid mark of it. The dots on the line are three units across and the fallback disc is ten,
     * so the count discriminates rather than merely detecting the curve.
     */
    @Test
    fun `every rival carries a face on the tip of their curve mid-playback`() {
        listOf(JourneyMode.SPAN, JourneyMode.GAME).forEach { mode ->
            val (frame, layout, paths) = rivalMedalFixture(mode = mode)
            val style = ownerColorStyle()
            val progress = 0.5f
            val render = renderToBitmap(frame, layout, paths, style, progress = progress)
            val sweep = JourneyChartRenderer.sweepX(layout, paths, progress)

            layout.rivals.forEachIndexed { i, rival ->
                assertTrue(
                    "$mode: the fixture's rival $i must be mid-curve at this point",
                    sweep > rival.points.first().x && sweep < rival.maxX,
                )
                val tip = paths.positionAt(
                    i, JourneyChartRenderer.rivalRevealedLength(layout, paths, i, sweep),
                )
                // Sampled BELOW the disc's centre, not on it: the fallback face is a disc of the
                // rival's colour with their initial stamped over the middle in the panel colour,
                // and at the runner-sized radius the faces ride at now, a centre box is mostly
                // letter ink. The offset point is inside the disc and under the glyph, so a solid
                // face fills the whole box while a bare curve (4.5 units of stroke) cannot.
                val faceR = minOf(17f, layout.fitRadius * 0.85f)
                val found = countColorNear(
                    render, tip.x, tip.y + faceR * 0.55f, style.rivalColors[i], halfBox = 3,
                )
                assertTrue(
                    "$mode: rival $i has no face on the tip of their curve ($found px)",
                    found > 35,
                )
            }
        }
    }

    /**
     * And nothing before the sweep gets to them. A face parked on a rival's starting point while
     * the stage in front of it is still blank is a player who has not started yet, drawn as though
     * they had.
     */
    @Test
    fun `a rival has no face before the sweep reaches their first unlock`() {
        // A rival whose first unlock is well into the window, with the sweep still short of it.
        val main = player("me", (0 until 6).map { unlock(it, t0 + it * 10 * day, 20) })
        val late = player("late", (0 until 3).map { unlock(10 + it, t0 + (40 + it * 5) * day, 15) })
        val frame = JourneyFrame(
            mode = JourneyMode.SPAN, main = main, rivals = listOf(late),
            spanFromMillis = 0, spanToMillis = t0 + 60 * day,
        )
        val layout = LayoutEngine.layout(frame, includeNodes = false)
        val paths = ChartPaths(layout)
        val style = ownerColorStyle()
        val start = layout.rivals.first().points.first()

        val early = renderToBitmap(frame, layout, paths, style, progress = 0.1f)
        assertTrue(
            "setup: the sweep must still be short of the rival's first unlock",
            JourneyChartRenderer.sweepX(layout, paths, 0.1f) < start.x,
        )
        assertEquals(
            "a rival who has not started yet must not be sitting on the stage",
            0,
            countColorNear(early, start.x, start.y, style.rivalColors[0], halfBox = 6),
        )
    }

    /**
     * A single-game race draws every player's whole achievement list rather than one badge a day
     * each.
     *
     * The daily rationing is what keeps a compare SPAN from being a wall of thumbnails, and on a
     * span it is right. On one game it was the second half of the reported bug: a game beaten in an
     * evening is a single calendar day, so a two-player race put exactly one badge on the stage per
     * player and there was nothing on either line to watch.
     */
    @Test
    fun `a single-game race draws both players' badges, not one a day each`() {
        // Two players, one game, a dozen unlocks each, all inside one calendar day.
        val hour = 3_600_000L
        val me = player("me", (0 until 12).map { unlock(it, t0 + it * hour, 10) })
        val rival = player("aria", (0 until 12).map { unlock(20 + it, t0 + it * hour + hour / 2, 10) })
        val frame = JourneyFrame(
            mode = JourneyMode.GAME, main = me, rivals = listOf(rival),
            spanFromMillis = 0, spanToMillis = t0 + day,
        )
        val layout = LayoutEngine.layout(frame, includeNodes = false)

        val mine = layout.nodes.count { it.ownerName == null }
        val theirs = layout.nodes.count { it.ownerName == "aria" }
        assertTrue("the main player got one badge a day, not a list ($mine)", mine > 6)
        assertTrue("the rival got one badge a day, not a list ($theirs)", theirs > 6)
        assertTrue(
            "and neither player was crowded out of the chart by the other",
            minOf(mine, theirs) * 2 >= maxOf(mine, theirs),
        )
    }

    /** A compare SPAN keeps the daily rationing, which is what it is for. */
    @Test
    fun `a compare span still draws one badge a day per player`() {
        val me = player("me", (0 until 12).map { unlock(it, t0 + it * 3_600_000L, 10) })
        val rival = player("aria", (0 until 12).map { unlock(20 + it, t0 + it * 3_600_000L, 10) })
        val frame = JourneyFrame(
            mode = JourneyMode.SPAN, main = me, rivals = listOf(rival),
            spanFromMillis = 0, spanToMillis = t0 + day,
        )
        val layout = LayoutEngine.layout(frame, includeNodes = false)

        assertEquals("one calendar day, one badge each", 2, layout.nodes.size)
    }

    // ---- the line's own fire, inside a streak ----

    /**
     * While the sweep is inside a streak run the stretch of line being revealed is restroked in the
     * flame's own orange, so the run is marked as it happens rather than only by the flame hanging
     * over it afterwards. Past the end of the run the line goes back to the accent: the fire is what
     * a streak looks like while it is happening, and the flame is what marks it once it has.
     *
     * The fixture is deliberately part streak and part not - three days running, then two unlocks a
     * month apart - so both readings come off the same chart at the same point on it.
     *
     * Sampled a quarter of the way into the run rather than at the tip: the runner is drawn over the
     * tip, in the accent, at seventeen units across.
     */
    /**
     * A streak is the line itself burning in the flame's fixed two-tone orange - static, cheap,
     * distinct from every player's colour - and the fire KEEPS burning once the sweep is past its
     * run, so a finished chart shows every streak it earned.
     */
    @Test
    fun `the streak fire burns orange and stands on the finished chart`() {
        val main = player(
            "me",
            (0 until 3).map { unlock(it, t0 + it * day + 3_600_000L, 10) } +
                listOf(unlock(10, t0 + 30 * day, 10), unlock(11, t0 + 60 * day, 10)),
        )
        val frame = JourneyFrame(
            mode = JourneyMode.SPAN, main = main, rivals = emptyList(),
            spanFromMillis = 0, spanToMillis = t0 + 60 * day,
        )
        val layout = LayoutEngine.layout(frame, includeNodes = true)
        val paths = ChartPaths(layout)
        val style = tooltipStyle(Color(0xFF223344))
        val (from, to) = paths.streakLengths.single()
        assertTrue("the run must be a fraction of the curve, not all of it", to < paths.mainLength * 0.9f)
        val sample = paths.positionAt(ChartPaths.MAIN, from + (to - from) * 0.25f)

        val before = renderToBitmap(frame, layout, paths, style, progress = 0f)
        val finished = renderToBitmap(frame, layout, paths, style, progress = 1f)

        assertEquals(
            "no fire before the reveal has reached the run",
            0,
            flamePixels(before, sample.x, sample.y, halfBox = 6),
        )
        assertTrue(
            "the orange fire stands on the finished chart, well behind the runner",
            flamePixels(finished, sample.x, sample.y, halfBox = 6) > 0,
        )
        assertTrue(
            "and the player's own line runs OVER the fire, not under a repaint",
            countColorNear(finished, sample.x, sample.y, style.accent, halfBox = 2) > 0,
        )
    }

    /**
     * Pixels around ([x], [y]) drawn in the flame's own two-tone orange, or in any blend of the
     * two: the fire is a gradient from [FLAME_OUTER] to [FLAME_INNER] along the burning stretch, so
     * the midpoint of a run is neither hex exactly. The band below is the two colours' own channel
     * ranges with a little slack, and nothing else on these fixtures is remotely in it (the
     * background is black, the plate a dark blue-grey, the line a cyan).
     */
    private fun flamePixels(bitmap: ImageBitmap, x: Float, y: Float, halfBox: Int): Int {
        val map = bitmap.toPixelMap()
        val cx = x.toInt()
        val cy = y.toInt()
        var count = 0
        for (yy in (cy - halfBox)..(cy + halfBox)) for (xx in (cx - halfBox)..(cx + halfBox)) {
            if (xx !in 0 until map.width || yy !in 0 until map.height) continue
            val c = map[xx, yy]
            // A little slack under the red floor: the fire is a bed UNDER the line now, and the
            // main curve's own translucent glow washes the rim by up to ~16% of the accent.
            if (c.red > 0.8f && c.green in 0.4f..0.8f && c.blue in 0.1f..0.5f) count++
        }
        return count
    }


    /** The layout's runs are the days the fire burns: ascending, disjoint, and one per streak. */
    @Test
    fun `a streak's x range is ascending and disjoint`() {
        val (_, layout, _) = streakFixture(5)
        assertEquals(1, layout.streaks.size)
        layout.streaks.zipWithNext().forEach { (a, b) ->
            assertTrue("runs must not overlap", a.endX < b.startX)
        }
        layout.streaks.forEach { assertTrue("a run reaches forwards", it.endX > it.startX) }
    }

    private fun renderToBitmap(
        frame: JourneyFrame,
        layout: ChartLayout,
        paths: ChartPaths,
        style: ChartStyle,
        cursorNodeIndex: Int? = null,
        cursorMedalIndex: Int? = null,
        nodeEffectSeconds: (Int) -> Float = { -1f },
        medalEffectSeconds: (Int) -> Float = { -1f },
        medalWashSeconds: (Int) -> Float = { -1f },
        progress: Float = 1f,
        view: ChartZoom = ChartZoom.NONE,
        bitmaps: Map<String, ImageBitmap> = emptyMap(),
        showChartChrome: Boolean = true,
    ): ImageBitmap {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val measurer = TextMeasurer(
            fallbackFontFamilyResolver = createFontFamilyResolver(context),
            fallbackDensity = Density(1f),
            fallbackLayoutDirection = LayoutDirection.Ltr,
        )
        val target = ImageBitmap(LayoutEngine.W.toInt(), LayoutEngine.H.toInt())
        CanvasDrawScope().draw(
            Density(1f), LayoutDirection.Ltr, Canvas(target),
            Size(LayoutEngine.W, LayoutEngine.H),
        ) {
            drawRect(style.background, size = Size(LayoutEngine.W, LayoutEngine.H))
            with(JourneyChartRenderer) {
                drawJourney(
                    layout = layout, paths = paths, frame = frame, style = style,
                    textMeasurer = measurer, bitmaps = bitmaps, userName = "me",
                    progress = progress, cursorNodeIndex = cursorNodeIndex, nodeAppearProgress = { 1f },
                    focused = false, scale = 1f,
                    cursorMedalIndex = cursorMedalIndex,
                    nodeEffectSeconds = nodeEffectSeconds,
                    medalEffectSeconds = medalEffectSeconds,
                    medalWashSeconds = medalWashSeconds,
                    view = view,
                    showChartChrome = showChartChrome,
                )
            }
        }
        return target
    }

    /**
     * Every marked type's badge stamps its chip, and a standard badge stamps nothing.
     *
     * Progression is still the vault tab's Material icon (`TaskAlt`); missable and win condition
     * went back to their text glyphs ("!" and "★") once the user saw all three Material icons on
     * a real chart and judged only progression's still legible at the chip's small size - so this
     * test does not (and must not) distinguish icon ink from glyph ink, only that each marked type
     * puts *some* panel-coloured ink in its chip and standard puts none.
     *
     * Sampled well inside the chip: the chip's own hairline is the panel colour too, and so is the
     * plate's edge around the art, so a box that reached either would count them and say nothing
     * about the mark. Inside the chip the only thing that can be panel-coloured is the stamp -
     * which is why the standard badge, whose type is deliberately unmarked, must have none there.
     */
    @Test fun `every marked type stamps its chip and a standard badge stamps nothing`() {
        val (frame, layout, paths) = markedTypesFixture()
        val panelColor = Color(0xFF223344)
        val style = tooltipStyle(panelColor)
        // Art on every badge, so the chip is what carries the mark (a badge with no art shows it
        // on the fallback disc instead - the case below). Opaque art, unlike the empty tiles the
        // other tests hand over: a transparent one leaves the panel plate showing through the very
        // pixels this test reads as the stamp.
        val tile = solidArt(Color(0xFF7B2D8E))
        val art = layout.nodes.associate { RaUrls.badge(it.unlock.badgeName) to tile }
        val render = renderToBitmap(frame, layout, paths, style, bitmaps = art)

        val standard = layout.nodes.first { it.unlock.type == AchievementType.STANDARD }
        val marked = layout.nodes.filter { it.unlock.type != AchievementType.STANDARD }
        assertEquals("fixture must carry all three marked types", 3, marked.size)

        marked.forEach { node ->
            val box = chipBox(node)
            assertTrue(
                "a ${node.unlock.type} chip must carry its mark in the chip's ink",
                countColorNear(render, chipX(node), chipY(node), panelColor, halfBox = box) > 0,
            )
        }
        assertEquals(
            "a standard badge has no chip and no mark of any kind",
            0,
            countColorNear(render, chipX(standard), chipY(standard), panelColor, halfBox = chipBox(standard)),
        )
    }

    /**
     * How far from a badge's chip centre it is safe to sample: comfortably inside the panel-ink
     * ring the chip is drawn with, so the box only ever counts the mark itself.
     *
     * A quarter of the chip's radius less than [drawOneNode]'s own 0.5-ish "well inside" margin
     * used for the Material icon chip: the exclamation mark's ink is a bar and a separated dot
     * rather than a shape that fills the box, and a box tight enough to sit only between the two
     * (which the icon-only margin was) samples the gap and finds nothing.
     */
    private fun chipBox(node: com.twinlore.app.journey.domain.NodeLayout): Int =
        (max(4.5f, node.r * 0.42f) * 0.65f).toInt()

    /**
     * The disc a badge with no art falls back to carries the same mark at full size - progression's
     * icon or missable/win condition's glyph, whichever the type draws - and a standard achievement,
     * which has no mark anywhere in the app, keeps the plain dot that stands in for one rather than
     * an empty disc.
     */
    @Test fun `the fallback disc carries the type mark, and a plain dot when there is none`() {
        val (frame, layout, paths) = markedTypesFixture()
        val panelColor = Color(0xFF223344)
        val render = renderToBitmap(frame, layout, paths, tooltipStyle(panelColor))

        val standard = layout.nodes.first { it.unlock.type == AchievementType.STANDARD }
        val marked = layout.nodes.filter { it.unlock.type != AchievementType.STANDARD }
        assertEquals("fixture must carry all three marked types", 3, marked.size)

        marked.forEach { node ->
            assertTrue(
                "a ${node.unlock.type} fallback disc must show its mark",
                countColorNear(render, node.x, node.y, panelColor, halfBox = (node.r * 0.3f).toInt()) > 0,
            )
        }
        assertTrue(
            "an unmarked type's fallback disc must still show its dot",
            countColorNear(render, standard.x, standard.y, panelColor, halfBox = (standard.r * 0.3f).toInt()) > 0,
        )
    }

    /** A fixture carrying all three marked achievement types plus one standard, unmarked one. */
    private fun markedTypesFixture(): Triple<JourneyFrame, ChartLayout, ChartPaths> {
        val unlocks = listOf(
            unlock(1, t0, 10),
            unlock(2, t0 + 5 * day, 20).copy(type = AchievementType.PROGRESSION),
            unlock(3, t0 + 10 * day, 30).copy(type = AchievementType.WIN_CONDITION),
            unlock(4, t0 + 15 * day, 40).copy(type = AchievementType.MISSABLE),
        )
        val main = player("me", unlocks)
        val frame = JourneyFrame(
            mode = JourneyMode.SPAN, main = main, rivals = emptyList(),
            spanFromMillis = 0, spanToMillis = t0 + 100 * day,
        )
        val layout = LayoutEngine.layout(frame, includeNodes = true)
        return Triple(frame, layout, ChartPaths(layout))
    }

    /** A badge tile that actually covers its plate, for the tests that read pixels off one. */
    private fun solidArt(color: Color): ImageBitmap {
        val tile = ImageBitmap(8, 8)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(tile), Size(8f, 8f)) {
            drawRect(color, size = Size(8f, 8f))
        }
        return tile
    }

    /** Where the renderer hangs a badge's corner chip: [drawOneNode]'s own 0.72 of the radius. */
    private fun chipX(node: com.twinlore.app.journey.domain.NodeLayout) = node.x + node.r * 0.72f

    private fun chipY(node: com.twinlore.app.journey.domain.NodeLayout) = node.y - node.r * 0.72f

    /** [minX, minY, maxX, maxY] of the pixels that differ between two renders, or null. */
    private fun changedBounds(a: ImageBitmap, b: ImageBitmap): IntArray? {
        val pa = a.toPixelMap()
        val pb = b.toPixelMap()
        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE
        var maxX = -1
        var maxY = -1
        for (y in 0 until pa.height) for (x in 0 until pa.width) {
            if (pa[x, y] != pb[x, y]) {
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
        }
        return if (maxX < 0) null else intArrayOf(minX, minY, maxX, maxY)
    }

    // ---- burningAt: whether a face rides a streak right now ----

    /**
     * The avatar's halo turns flame while its OWN curve burns under it (owner request): the
     * streak stretches are per-curve length bounds, and the face rides at a length, so "is this
     * face on fire" is containment - inclusive at both ends, with the same playback tolerance
     * every other reveal comparison uses, so the glow lights the same frame the fire starts.
     */
    @Test
    fun `a face inside a streak stretch burns and one outside does not`() {
        val bounds = listOf(100f to 220f, 400f to 460f)

        assertFalse(JourneyChartRenderer.burningAt(bounds, 50f))
        assertTrue(JourneyChartRenderer.burningAt(bounds, 100f))
        assertTrue(JourneyChartRenderer.burningAt(bounds, 160f))
        assertTrue(JourneyChartRenderer.burningAt(bounds, 220f))
        assertFalse(JourneyChartRenderer.burningAt(bounds, 300f))
        assertTrue(JourneyChartRenderer.burningAt(bounds, 430f))
        assertFalse(JourneyChartRenderer.burningAt(bounds, 470f))
    }

    @Test
    fun `no streaks means no fire at any length`() {
        assertFalse(JourneyChartRenderer.burningAt(emptyList(), 0f))
    }

    // ---- tooltipScale: the info box grows with the lean-in, to a cap ----

    /**
     * The tooltip used to pin itself to constant screen size (1/zoom), which deep in a follow
     * zoom read as a shrinking box beside badges that had grown huge (owner report) - the same
     * complaint the corner date had, cured the same way: the box grows naturally with the zoom
     * until [JourneyChartRenderer.TIP_MAX_SCALE], then holds that screen size.
     */
    @Test
    fun `the tooltip grows with the zoom until the cap and then holds`() {
        // In virtual units the renderer draws box * tooltipScale, and the stage multiplies by
        // zoom: on-screen factor = tooltipScale(z) * z.
        fun onScreen(z: Float) = JourneyChartRenderer.tooltipScale(z) * maxOf(z, 1f)

        assertEquals(1f, onScreen(1f), 1e-4f)
        assertEquals(1.3f, onScreen(1.3f), 1e-4f)
        assertEquals(JourneyChartRenderer.TIP_MAX_SCALE, onScreen(JourneyChartRenderer.TIP_MAX_SCALE), 1e-4f)
        assertEquals(JourneyChartRenderer.TIP_MAX_SCALE, onScreen(5f), 1e-4f)
        assertEquals(JourneyChartRenderer.TIP_MAX_SCALE, onScreen(8f), 1e-4f)
    }

    /** Zoomed out past 1 the box must not grow either - the other end of the same illegibility. */
    @Test
    fun `an overview below 1x keeps the authored size`() {
        assertEquals(1f, JourneyChartRenderer.tooltipScale(0.4f) * 1f, 1e-4f)
    }
}
