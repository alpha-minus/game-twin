package com.twinlore.app.journey.export

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.text.font.FontFamily
import androidx.test.core.app.ApplicationProvider
import com.twinlore.app.journey.domain.LayoutEngine
import com.twinlore.app.journey.domain.model.AchievementType
import com.twinlore.app.journey.domain.model.AwardTally
import com.twinlore.app.journey.domain.model.AwardKind
import com.twinlore.app.journey.domain.model.GameRef
import com.twinlore.app.journey.domain.model.JourneyFrame
import com.twinlore.app.journey.domain.model.JourneyMode
import com.twinlore.app.journey.domain.model.Medal
import com.twinlore.app.journey.domain.model.PlayerJourney
import com.twinlore.app.journey.domain.model.Unlock
import com.twinlore.app.journey.ui.JourneyStripText
import com.twinlore.app.journey.ui.JourneyUrls
import com.twinlore.app.journey.ui.chart.ChartPaths
import com.twinlore.app.journey.ui.chart.ChartStyle
import com.twinlore.app.journey.ui.chart.ChartZoom
import com.twinlore.app.journey.ui.chart.FollowCamera
import com.twinlore.app.journey.ui.chart.JourneyChartRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLog

/**
 * The exported frame, rendered for real.
 *
 * The image export has no encoder and no gallery in a unit test, so what is worth pinning is the
 * render itself: that a whole 1920x1080 frame comes back with a chart and a strip actually painted
 * into it rather than a sheet of background - which is what a bad clip, a zero-sized image or a
 * text measure blowing up at draw time would leave behind.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ExportFrameTest {

    private val day = 86_400_000L
    private val t0 = 1_760_000_000_000L

    private fun unlock(id: Int, at: Long, pts: Int) = Unlock(
        id = id, title = "A$id", description = "d$id", points = pts, badgeName = "b$id",
        type = AchievementType.STANDARD, earnedAtMillis = at, numAwarded = 10, gameId = 1,
    )

    private fun fixture(mode: JourneyMode = JourneyMode.SPAN): JourneyFrame {
        val unlocks = (0 until 8).map { unlock(it, t0 + it * 5 * day, (it + 1) * 5) }
        val main = PlayerJourney(
            name = "me",
            unlocks = unlocks,
            games = mapOf(
                1 to GameRef(
                    1, "Game One", null, emptyList(), colorIndex = 0, players = 100, award = null,
                    totalAchievements = 20,
                ),
            ),
            medals = listOf(Medal(AwardKind.MASTERED, t0 + 20 * day, 1, null, "Game One")),
        )
        return JourneyFrame(
            mode = mode, main = main, rivals = emptyList(),
            spanFromMillis = 0, spanToMillis = t0 + 40 * day,
        )
    }

    private val style = ChartStyle(
        background = Color.Black, panel = Color(0xFF223344), grid = Color(0xFF404040),
        ink = Color.White, inkDim = Color(0xFFB0B0B0), accent = Color(0xFF4FD2FF),
        seriesColors = List(12) { Color(0xFFFF6EC7) }, rivalColors = List(3) { Color(0xFF5FF2D6) },
        typeColors = AchievementType.values().associateWith { Color(0xFFFFD54A) },
        silver = Color(0xFFCDD8EA), gold = Color(0xFFFFD54A), labelFont = FontFamily.Default,
    )

    private fun render(
        frame: JourneyFrame,
        progress: Float = 1f,
        art: Map<Int, androidx.compose.ui.graphics.ImageBitmap> = emptyMap(),
        view: ChartZoom? = null,
        effects: ExportEffects.Frame? = null,
        bitmaps: Map<String, androidx.compose.ui.graphics.ImageBitmap> = emptyMap(),
        backdropDim: Float = 0.16f,
    ) = ExportFrame.render(
        context = ApplicationProvider.getApplicationContext(),
        layout = LayoutEngine.layout(frame, includeNodes = true),
        frame = frame,
        style = style,
        bitmaps = bitmaps,
        art = art,
        userName = "me",
        progress = progress,
        view = view,
        effects = effects,
        backdropDim = backdropDim,
    )

    /** How many sampled pixels the two frames disagree about. */
    private fun differences(
        a: androidx.compose.ui.graphics.ImageBitmap,
        b: androidx.compose.ui.graphics.ImageBitmap,
    ): Int {
        val left = a.toPixelMap()
        val right = b.toPixelMap()
        var differing = 0
        // Every 4th pixel: eight million reads is not what this is testing.
        for (y in 0 until ExportFrame.HEIGHT step 4) for (x in 0 until ExportFrame.WIDTH step 4) {
            if (left[x, y] != right[x, y]) differing++
        }
        return differing
    }

    /** A flat block of one colour, standing in for a game's backdrop art. */
    private fun artOf(color: Color): androidx.compose.ui.graphics.ImageBitmap {
        val bitmap = androidx.compose.ui.graphics.ImageBitmap(64, 64)
        androidx.compose.ui.graphics.drawscope.CanvasDrawScope().draw(
            androidx.compose.ui.unit.Density(1f),
            androidx.compose.ui.unit.LayoutDirection.Ltr,
            androidx.compose.ui.graphics.Canvas(bitmap),
            androidx.compose.ui.geometry.Size(64f, 64f),
        ) { drawRect(color) }
        return bitmap
    }

    /**
     * A landed span rests on no picture at all.
     *
     * The art behind the chart belongs to the game under the sweep, and a journey over forty games
     * is not "about" whichever one happened to be unlocked last - but the still is taken from
     * exactly that closing frame, so a shared image used to carry that leftover game as its
     * background (user request). Mid-playback the art is still drawn: it is the game being played.
     */
    @Test fun `a finished span drops the backdrop, mid-playback keeps it`() {
        val art = mapOf(1 to artOf(Color(0xFF00FF00)))
        val frame = fixture()

        fun greenish(image: androidx.compose.ui.graphics.ImageBitmap): Int {
            val map = image.toPixelMap()
            var n = 0
            for (y in 0 until image.height step 8) for (x in 0 until image.width step 8) {
                val c = map[x, y]
                if (c.green > c.red + 0.02f && c.green > c.blue + 0.02f) n++
            }
            return n
        }

        val playing = greenish(render(frame, progress = 0.5f, art = art))
        val landed = greenish(render(frame, progress = 1f, art = art))

        assertTrue("the art must be drawn while the span plays ($playing)", playing > 200)
        assertTrue("a landed span must not carry the last game's art ($landed)", landed < playing / 4)
    }

    /**
     * The backdrop's `backdropDim` reaches the pixels: a stronger value paints a visibly brighter
     * (less dimmed) copy of the art than a weaker one, both for the whole-frame path and the
     * split-frame path a compare SPAN takes ([ExportBackdrop.drawScaledBackdropArt] and
     * [ExportBackdrop.drawBackdropArt] respectively - see `drawArtBehind`'s own routing).
     */
    @Test fun `a stronger backdropDim paints a brighter copy of the art`() {
        val art = mapOf(1 to artOf(Color(0xFF00FF00)))
        val frame = fixture()

        fun greenAt(x: Int, y: Int, dim: Float) =
            render(frame, progress = 0.5f, art = art, backdropDim = dim).toPixelMap()[x, y].green

        val dim = greenAt(ExportFrame.WIDTH / 2, ExportFrame.HEIGHT / 2, dim = 0.05f)
        val bright = greenAt(ExportFrame.WIDTH / 2, ExportFrame.HEIGHT / 2, dim = 0.4f)

        assertTrue("a higher backdropDim must read as more art, not less ($bright vs $dim)", bright > dim)
    }

    @Test fun `a rendered frame is the whole 1920x1080 and is not blank`() {
        val image = render(fixture())

        assertEquals(ExportFrame.WIDTH, image.width)
        assertEquals(ExportFrame.HEIGHT, image.height)

        val map = image.toPixelMap()
        var painted = 0
        // Every 4th pixel: eight million reads is not what this is testing.
        for (y in 0 until image.height step 4) for (x in 0 until image.width step 4) {
            if (map[x, y] != Color.Black) painted++
        }
        assertTrue("the frame came back blank", painted > 1000)
    }

    /**
     * The strip is the point of the exercise: a shared picture used to arrive with the chart on it
     * and none of the counters that say what the journey came to. It sits in the band above the
     * plot, so that band has to carry ink of its own.
     *
     * No panel check any more. There used to be one here (`panel > 2000`), pinning the opaque
     * `style.background` + `style.panel` plate this file painted behind the counters - the plate
     * `headerPanelColor` never draws on screen. That assertion is worth keeping as its own
     * negative below rather than dropped outright: it is the tripwire for the plate coming back.
     */
    @Test fun `the strip is painted into the band above the chart`() {
        val map = render(fixture()).toPixelMap()
        val top = ExportStrip.TOP.toInt()
        // The band is the plate's own height now, not a per-chip row's. A single VALUE_SIZE line
        // is shorter than the icon, so plateHeight returns PAD_V * 2 + ICON - the icon is what sets
        // the plate's height, exactly as it sets the drawn plate's.
        val bottom = (ExportStrip.TOP + ExportStrip.plateHeight(listOf(ExportStrip.VALUE_SIZE))).toInt()

        var panel = 0
        var ink = 0
        for (y in top until bottom) for (x in ExportStrip.LEFT.toInt() until ExportFrame.WIDTH) {
            val c = map[x, y]
            if (c == style.panel) panel++
            if (c.red > 0.6f && c.green > 0.6f && c.blue > 0.6f) ink++
        }
        assertEquals("the ordinary plate must stay unpainted, like the screen's", 0, panel)
        assertTrue("no text on the strip ($ink px)", ink > 100)
    }

    /**
     * The band's scrim: the top of the frame darkens toward the theme's background under the
     * readouts and is back to the untouched picture below the band.
     *
     * The screen's top band has carried a shared gradient scrim since its readouts gave up their
     * individual panels for one (`JourneyPlayer`); the export had taken the panels away and never
     * added the scrim, so the same strip of frame sat bare over the art and light backdrops took
     * the counters with them.
     *
     * Three things have to hold together, and only together: it darkens at all, it FADES rather
     * than ending on an edge, and it is not an opaque plate - the last two are what separate a
     * scrim from the flat box an earlier change deliberately removed. Measured against flat green
     * art with a black background in this fixture's [style], so "darker" is unambiguous.
     *
     * The band is measured from an UPPER BOUND on the plate rather than from its exact height, and
     * that is deliberate. A running span's plate is the two-line header now, whose height is the
     * two lines' MEASURED heights (see `ExportStrip.plateHeight`) - a font's line metrics, which
     * this test has no honest way to restate. Twice the font size is a bound no real font's line
     * height exceeds, so "gone by here" is a claim about a y the real scrim has certainly ended
     * before, which is exactly the claim worth making; a y taken from the font size itself would
     * land INSIDE the real gradient and fail for being early rather than for a scrim that stepped.
     */
    @Test fun `the band carries a gradient scrim that fades out`() {
        val art = mapOf(1 to artOf(Color(0xFF00FF00)))
        val map = render(fixture(), progress = 0.5f, art = art).toPixelMap()
        val band = ExportStrip.bandHeight(
            ExportStrip.TOP + ExportStrip.plateHeight(
                listOf(ExportStrip.TITLE_SIZE * 2f, ExportStrip.SPAN_FACT_SIZE * 2f),
            ),
        )

        // The MEDIAN of a row, across the strip of frame the fitted art actually covers (a square
        // bitmap fitted into 1920x1080 is 1080 wide and centred). The median rather than one
        // column: the counters, the chart's own curves and its grid all cross this band, and they
        // are a small minority of any row - so the middle value is the art with the scrim on it,
        // which is the thing being measured.
        fun green(y: Int): Float =
            (500 until 1400).map { map[it, y].green }.sorted()[(1400 - 500) / 2]
        val atTop = green(2)
        val midway = green((band / 2f).toInt())
        val pastTheBand = green((band - 4f).toInt())
        val below = green((band + 60f).toInt())

        // The art arrives dimmed (`ExportBackdrop.ALPHA` and its colour filter), so "green" here is
        // a fraction rather than a full channel - what matters is the four readings against each
        // other, not their absolute value.
        assertTrue("the art has to be there below the band to be dimmed above it ($below)", below > 0.05f)
        assertTrue("the top of the band must be dimmed ($atTop against $below)", atTop < below * 0.3f)
        assertTrue("the scrim must fade, not step ($atTop then $midway then $pastTheBand)", midway > atTop)
        assertTrue(
            "and it must be gone by the band's own bottom ($pastTheBand against $below)",
            pastTheBand >= below * 0.9f,
        )
        assertTrue("an opaque plate is exactly what this must not be ($midway)", midway > 0f)
    }

    /** Both modes render, and a single-game frame draws its own "x of N" counter without throwing. */
    @Test fun `a single-game frame renders too`() {
        val map = render(fixture(JourneyMode.GAME), progress = 0.5f).toPixelMap()
        var painted = 0
        for (y in 0 until ExportFrame.HEIGHT step 4) for (x in 0 until ExportFrame.WIDTH step 4) {
            if (map[x, y] != Color.Black) painted++
        }
        assertTrue("the frame came back blank", painted > 1000)
    }

    /**
     * The reveal effects reach the exported pixels.
     *
     * A video used to carry none of what the watcher saw: no pop, no floating "+N", no confetti and
     * no award flash, because the only clock the effects had was a wall clock and an export must be
     * the same file every run. [ExportEffects] is that clock as arithmetic, and this is the test
     * that says it is plugged in - rendered five frames into the award's own flash, where the
     * full-stage tint is at its loudest.
     */
    @Test fun `an exported frame with effects paints the award flash`() {
        val frame = fixture()
        val layout = LayoutEngine.layout(frame, includeNodes = true)
        val frames = ExportTimeline.frameCount(1f)
        val plan = ExportEffects.plan(layout, frames)
        val fired = (0 until frames).first { plan.at(it).medalWashSeconds(0) >= 0f }
        val at = fired + 5
        val progress = ExportTimeline.progressForFrame(at, 1f)

        val differing = differences(
            render(frame, progress = progress),
            render(frame, progress = progress, effects = plan.at(at)),
        )

        // The flash fills the frame, letterbox included, so "some of it changed" is far too weak a
        // claim: a tenth of the sampled pixels is a celebration rather than a stray badge edge.
        assertTrue("the effects never reached the pixels ($differing px)", differing > 12_000)
    }

    /**
     * The still image keeps the settled picture the video now animates through.
     *
     * A PNG is one instant and is a summary of a journey, so it must never catch a badge half
     * popped or a stage mid-flash. The still export passes no effects at all; this pins what that
     * means - the same frame rendered with a plan whose every effect has long since expired is the
     * identical picture, so "no effects" is exactly "everything settled" and nothing else.
     */
    @Test fun `the still image draws what a settled frame draws`() {
        val frame = fixture()
        val layout = LayoutEngine.layout(frame, includeNodes = true)
        val frames = ExportTimeline.frameCount(1f)
        // Positioned at the end of the clip, where every effect has expired, while the frame is
        // drawn at half playback: the plan's frame index and the chart's progress are independent.
        val settled = ExportEffects.plan(layout, frames).at(frames - 1)

        val differing = differences(
            render(frame, progress = 0.5f),
            render(frame, progress = 0.5f, effects = settled),
        )

        assertEquals("a still frame must be the settled frame", 0, differing)
    }

    /** The letterbox: a chart is fitted whole, so the binding axis is whichever runs out first. */
    @Test fun `the fit scale letterboxes rather than cropping`() {
        val compact = LayoutEngine.layout(fixture(), includeNodes = true)
        val wide = LayoutEngine.layout(fixture(), includeNodes = true, widthFactor = 4f)

        val compactScale = ExportFrame.scaleFor(compact)
        assertEquals(ExportFrame.HEIGHT / LayoutEngine.H, compactScale, 0.001f)
        assertTrue("the whole compact chart must fit", compact.width * compactScale <= ExportFrame.WIDTH)

        val wideScale = ExportFrame.scaleFor(wide)
        assertEquals(ExportFrame.WIDTH / wide.width, wideScale, 0.001f)
        assertTrue("a wide chart is fitted by its width, not cropped", wide.width * wideScale <= ExportFrame.WIDTH + 0.5f)
    }

    /**
     * The followed frame letterboxes a different box from the fitted one, and the two must not be
     * confused: [ExportFrame.scaleFor] fits the layout's own width (so a wide chart is shown whole),
     * while a view describes a position inside the fixed 1060x600 stage, which is the box its clamp
     * and its offsets are expressed in. On an unstretched chart the two numbers are identical - the
     * exact coincidence that would make sharing them look correct right up until a wide layout was
     * exported at the wrong scale.
     */
    @Test fun `a followed frame letterboxes the stage, not the layout`() {
        val compact = LayoutEngine.layout(fixture(), includeNodes = true)
        val wide = LayoutEngine.layout(fixture(), includeNodes = true, widthFactor = 4f)

        assertEquals(ExportFrame.HEIGHT / LayoutEngine.H, ExportFrame.STAGE_SCALE, 0.001f)
        assertEquals(ExportFrame.scaleFor(compact), ExportFrame.STAGE_SCALE, 0.001f)
        assertTrue(
            "a wide layout's fit must be tighter than the stage's",
            ExportFrame.scaleFor(wide) < ExportFrame.STAGE_SCALE - 0.01f,
        )
    }

    /**
     * A followed frame draws the chart, magnified, rather than culling it away.
     *
     * Worth its own render: a zoomed view is the first thing an export has ever handed the renderer
     * that turns [com.twinlore.app.journey.ui.chart.ViewportCull] on, and a cull rect that did not
     * describe the region actually being drawn would come back as an empty stage with a strip over
     * it. The comparison against the fitted frame is what says the view reached the pixels at all.
     */
    @Test fun `a followed frame draws a magnified chart`() {
        val frame = fixture()
        val layout = LayoutEngine.layout(frame, includeNodes = true)
        val paths = ChartPaths(layout)
        val progress = 0.5f
        val runner = paths.positionAt(
            ChartPaths.MAIN,
            JourneyChartRenderer.revealedLength(layout, paths, progress),
        )
        val view = FollowCamera.target(runner, layout.width)

        val followed = render(frame, progress = progress, view = view).toPixelMap()
        val fitted = render(frame, progress = progress).toPixelMap()

        var painted = 0
        var differ = 0
        for (y in 0 until ExportFrame.HEIGHT step 4) for (x in 0 until ExportFrame.WIDTH step 4) {
            if (followed[x, y] != Color.Black) painted++
            if (followed[x, y] != fitted[x, y]) differ++
        }
        assertTrue("the followed frame came back blank ($painted px)", painted > 1000)
        assertTrue("the view never reached the pixels ($differ px differ)", differ > 5000)
    }

    /**
     * NOTHING the strip draws may land above its own top edge, in any shape, at any point of
     * playback, fitted or followed.
     *
     * The guard for a reported artifact - a fragment of a game title above the counters row on a
     * finished span's export, clipped by the top of the 1920x1080 frame - and, more usefully, for
     * the whole class it belongs to. The two-line header is what made that class newly possible:
     * a plate that stacks has lines whose top is NOT the plate's top, so a stack laid from the
     * wrong origin, an end state that fell down the stacked path by mistake, or a run centered in a
     * line that was sized without it would all put ink where the band promises none.
     *
     * That last one was real and is fixed (see `ExportStrip`'s own `lineHeights`, which now measures
     * a line by everything drawn on it rather than by its values). The arithmetic tests cannot catch
     * any of them: they say where a line goes, not what reaches the pixels.
     *
     * Every shape that shares `drawExportStrip`'s body: a solo single game, a single game with
     * rivals (the compare plate), a running span (the two-line header) and a finished span (the end
     * panel) - plus the followed video's hold tail, which is the only path in the export that can
     * push the CHART's own text up the frame (a magnified, panned view: see `ExportCamera.path`,
     * whose glide out to the overview has not arrived by the last frames).
     *
     * Rendered with real icons in `bitmaps`, which a device export has and the other fixtures in
     * this file do not - the leading icon is the one piece of plate furniture whose presence moves
     * where content starts. [ExportStrip.TOP] is the bound rather than the plate's first line,
     * because that is what the strip promises ("clear of the chart caption drawn above it"; see
     * `the strip clears the chart's own chrome`). Bright ink is the test: the band's own scrim is a
     * dark gradient and the chart's grid is dim, so only text and award dots read as bright.
     */
    @Test fun `the strip draws nothing above its own top edge, in every mode`() {
        val icon = artOf(Color(0xFFFFFFFF))
        val titles = listOf("Mega Man Legends", "The Legend of Zelda", "Bugs Bunny Lost in Time")
        val games = (1..3).associateWith { id ->
            GameRef(
                id, titles[id - 1], "/Images/00$id.png", emptyList(), colorIndex = id, players = 10,
                award = null, totalAchievements = 20,
            )
        }
        val bitmaps = (1..3).associate { JourneyUrls.media("/Images/00$it.png") to icon }
        val spanUnlocks = (0 until 60).map { i -> unlock(i, t0 + i * day / 2, 10).copy(gameId = (i % 3) + 1) }
        val gameUnlocks = (0 until 20).map { i -> unlock(i, t0 + i * day / 4, 10) }
        val medals = listOf(Medal(AwardKind.MASTERED, t0 + day, 1, null, titles[0]))
        fun frameOf(mode: JourneyMode, unlocks: List<Unlock>, rivals: List<PlayerJourney>) = JourneyFrame(
            mode = mode,
            main = PlayerJourney("me", unlocks, games, medals),
            rivals = rivals,
            spanFromMillis = t0,
            spanToMillis = t0 + 31 * day,
        )
        val span = frameOf(JourneyMode.SPAN, spanUnlocks, emptyList())
        val rival = PlayerJourney("rival", gameUnlocks.take(12), games, emptyList())
        // The followed video's last frames: the camera is gliding out to the overview and has not
        // got there, so the chart is still magnified and panned under the band.
        val spanLayout = LayoutEngine.layout(span, includeNodes = true)
        val frames = ExportTimeline.frameCount(1f)
        val tail = ExportCamera.path(spanLayout, frames, 1f, ChartPaths(spanLayout))[frames - 15]

        listOf(
            Quad("a finished span", span, 1f, null),
            Quad("a running span", span, 0.5f, null),
            Quad("a solo single game", frameOf(JourneyMode.GAME, gameUnlocks, emptyList()), 0.5f, null),
            Quad("a single game with rivals", frameOf(JourneyMode.GAME, gameUnlocks, listOf(rival)), 0.5f, null),
            Quad("a followed video's hold tail", span, 1f, tail),
        ).forEach { (what, frame, progress, view) ->
            val map = render(frame, progress = progress, view = view, bitmaps = bitmaps).toPixelMap()
            val offending = (0 until ExportStrip.TOP.toInt()).flatMap { y ->
                (0 until ExportFrame.WIDTH).filter { x ->
                    val c = map[x, y]
                    c.red > 0.5f && c.green > 0.5f && c.blue > 0.5f
                }.map { x -> "($x,$y)" }
            }
            assertEquals(
                "$what painted ink above the strip's own top edge at ${ExportStrip.TOP}: " +
                    offending.take(12).joinToString(" ") + " (${offending.size} px in total)",
                emptyList<String>(),
                offending.take(12),
            )
        }
    }

    private data class Quad(
        val what: String,
        val frame: JourneyFrame,
        val progress: Float,
        val view: ChartZoom?,
    )

    /**
     * A landed span's counters still fit the frame at [ExportStrip.END_LABEL_SIZE]/
     * [ExportStrip.END_VALUE_SIZE] - the DENSITY-traced sizes that replaced the untraced
     * [ExportStrip.LABEL_SIZE]/[ExportStrip.VALUE_SIZE] for a finished span's plate.
     *
     * Growing a plate's type grows its measured width, and [ExportStrip.chipsThatFit] answers
     * an overflow by silently dropping trailing counters and logging a [android.util.Log.w] - a
     * real failure mode, just not a crashing one, so this is the guard for it: a journey
     * deliberately larger than real RetroAchievements data ever produces (4000 achievements
     * across 200 games, all four award kinds, a year-long streak) rendered through the actual
     * production path, with nothing logged under the `ExportStrip` tag. If a future retune of
     * either constant makes a realistic finished span stop fitting, this is meant to fail here
     * rather than in a shared image with its streak counter quietly missing.
     */
    @Test fun `a landed span's counters fit the frame at the finished-span type size`() {
        ShadowLog.stream = System.out
        val unlocks = (0 until 4000).map { i ->
            unlock(i, t0 + i * day / 100, 10).copy(gameId = (i % 200) + 1)
        }
        val games = (1..200).associateWith { id ->
            GameRef(
                id = id, title = "Game $id", iconPath = null, artPaths = emptyList(),
                colorIndex = id % 12, players = 10, award = null, totalAchievements = 20,
            )
        }
        val medals = listOf(
            Medal(AwardKind.MASTERED, t0, 1, null, "Game 1"),
            Medal(AwardKind.COMPLETED, t0, 2, null, "Game 2"),
            Medal(AwardKind.BEATEN, t0, 3, null, "Game 3"),
            Medal(AwardKind.BEATEN_CASUAL, t0, 4, null, "Game 4"),
        )
        val frame = JourneyFrame(
            mode = JourneyMode.SPAN,
            main = PlayerJourney("me", unlocks, games, medals),
            rivals = emptyList(),
            spanFromMillis = t0,
            spanToMillis = t0 + 40 * day + 400 * day,
        )

        render(frame, progress = 1f)

        val dropped = ShadowLog.getLogs().filter { it.tag == "ExportStrip" }
        assertTrue(
            "a finished span's counters were dropped at the new type size: " +
                dropped.joinToString(" | ") { it.msg },
            dropped.isEmpty(),
        )
    }

    /**
     * A RUNNING span's two-line header fits the frame at its own type sizes - the sibling of the
     * landed-span guard above, for the plate that changed shape rather than only size.
     *
     * Both halves are worth the render. The title line grew from nothing at all (a running span
     * never named its game) to a [ExportStrip.TITLE_SIZE] run carrying the longest game name the
     * span has under its sweep, and the facts line grew from [ExportStrip.VALUE_SIZE] to
     * [ExportStrip.SPAN_FACT_SIZE] with up to four award dots hung off its end. Either could
     * overflow the plate's inner right edge, which `ExportStrip.chipsThatFit` answers by silently
     * dropping the line and logging - a real failure mode, and on a two-line plate a much louder
     * one than a missing trailing counter, because a dropped line is HALF the header.
     *
     * The date is why the facts line is the long one: a running span's second row carries the full
     * date-time on a short journey (`TickGenerator.showsTimeOfDay`), which is what this fixture's
     * two-day span produces.
     */
    @Test fun `a running span's two-line header fits the frame`() {
        ShadowLog.stream = System.out
        val titles = listOf(
            "Final Fantasy VII Remake Intergrade",
            "The Legend of Zelda: Breath of the Wild",
        )
        titles.forEach { title ->
            ShadowLog.reset()
            // Two days, so the date reads with its clock time - the widest the facts line gets.
            val unlocks = (0 until 40).map { i -> unlock(i, t0 + i * day / 20, 10) }
            val games = mapOf(
                1 to GameRef(
                    1, title, null, emptyList(), colorIndex = 0, players = 100, award = null,
                    totalAchievements = 40,
                ),
            )
            val frame = JourneyFrame(
                mode = JourneyMode.SPAN,
                main = PlayerJourney(
                    "me", unlocks, games,
                    listOf(
                        Medal(AwardKind.MASTERED, t0, 1, null, title),
                        Medal(AwardKind.COMPLETED, t0, 1, null, title),
                        Medal(AwardKind.BEATEN, t0, 1, null, title),
                        Medal(AwardKind.BEATEN_CASUAL, t0, 1, null, title),
                    ),
                ),
                rivals = emptyList(), spanFromMillis = t0, spanToMillis = t0 + 2 * day,
            )

            render(frame, progress = 0.6f)

            val dropped = ShadowLog.getLogs().filter { it.tag == "ExportStrip" }
            assertTrue(
                "\"$title\" (${title.length} chars) lost part of the running span's header: " +
                    dropped.joinToString(" | ") { it.msg },
                dropped.isEmpty(),
            )
        }
    }

    /**
     * A single game's title chip still fits at [ExportStrip.TITLE_SIZE] (32px, `titleSmall` x
     * `DENSITY`) rather than the untraced 26px it drew at before - the sibling of the finished-span
     * guard above, for the one other constant this feature's DENSITY retune touched.
     *
     * The title is very often the widest run on a single-game plate (see [ExportStrip.TITLE_SIZE]'s
     * own doc), so growing it by 6px is a real width change worth checking for real rather than by
     * arithmetic alone. Two titles as long as real RetroAchievements game names get - both drawn
     * from actual games, not invented for width - render with nothing dropped.
     *
     * A THIRD, deliberately manufactured 77-character title (no real game is titled anything close)
     * was checked by hand during this change and found to already drop the trailing Date chip at
     * the OLD 26px size too, for the identical reason and the identical measured width - the title
     * hits [ExportStrip]'s own `MAX_TEXT_WIDTH` ellipsis cap (900px) regardless of font size, so its
     * measured width, and therefore whether anything after it fits, does not change between 26px and
     * 32px for a title that long. That is a pre-existing edge case this retune left exactly as it
     * found it, not a new one - not pinned as a test here because it is not a real game's title and
     * asserting on invented data would test the ellipsis cap, not this constant.
     */
    @Test fun `a single game's title fits the frame at TITLE_SIZE`() {
        ShadowLog.stream = System.out
        val titles = listOf(
            "Final Fantasy VII Remake Intergrade", // PlaybackHeaderLayoutTest's own stress title
            "The Legend of Zelda: Breath of the Wild",
        )
        titles.forEach { title ->
            ShadowLog.reset()
            val unlocks = (0 until 160).map { i -> unlock(i, t0 + i * day / 4, 7) }
            val games = mapOf(
                1 to GameRef(
                    id = 1, title = title, iconPath = null, artPaths = emptyList(), colorIndex = 1,
                    players = 10, award = null, totalAchievements = 160, playtimeSeconds = 100_000,
                ),
            )
            val frame = JourneyFrame(
                mode = JourneyMode.GAME,
                main = PlayerJourney("me", unlocks, games, emptyList()),
                rivals = emptyList(),
                spanFromMillis = 0,
                spanToMillis = 41 * day,
            )

            render(frame, progress = 0.5f)

            val dropped = ShadowLog.getLogs().filter { it.tag == "ExportStrip" }
            assertTrue(
                "\"$title\" (${title.length} chars) dropped a counter at TITLE_SIZE: " +
                    dropped.joinToString(" | ") { it.msg },
                dropped.isEmpty(),
            )
        }
    }

    /**
     * AN EXPORTED FRAME CARRIES EXACTLY ONE TYPEFACE: [ChartStyle.labelFont], everywhere but the
     * type stamps.
     *
     * The one exception is deliberate and is pinned by `ChartTypeIconsTest`: a badge's `!`/`★`
     * mark, and the bullet the missing-art disc falls back to, are drawn in
     * [com.twinlore.app.journey.ui.chart.ChartTypeIcons.GLYPH_FONT] - the platform default - because
     * their size and centring are derived from an ordinary font's cap height and the pixel theme's
     * font has none to speak of (its caps fill the em box). They are marks rather than text, they
     * are measured through the same `drawGlyph` the rest of the chart uses, and there are four of
     * them on a frame, so none of the reasoning below is weakened by them - but this test cannot
     * see them either way: they are drawn in `style.panel`, and the scan below counts only pixels
     * that are bright in all three channels.
     *
     * The class guard, not an instance one. The chart's axis labels, its badge glyphs and the
     * strip's counters are one picture; a run that quietly fell back to the platform's proportional
     * default would read as somebody else's text sitting on top of ours, and - worse than ugly - it
     * would be MEASURED in a face the layout did not assume, so its height and width would be wrong
     * and every position derived from them would be wrong with it.
     *
     * There is no seam to intercept (`TextMeasurer` is final, and `ExportFrame.render` builds its
     * own), so this asks the pixels instead: render the identical frame twice, changing nothing but
     * [ChartStyle.labelFont], and require that almost all of the ink MOVED. A run whose typeface is
     * pinned to something other than `labelFont` draws the identical pixels in both renders, so it
     * shows up as a block of bright ink that did not move.
     *
     * The threshold is measured, not guessed, in both directions. `Monospace` against `Serif` at
     * the same sizes shifts every advance width, so genuinely font-dependent ink overlaps only
     * incidentally: the floor is **7.7%** of the band's bright pixels on a finished span and
     * **6.5%** on a running one. Pinning the plate's LABELS to the platform default - the exact bug
     * this guards - takes the finished span to **16.7%**. 12% sits between them.
     *
     * Its sensitivity scales with how much ink the mispainted run contributes, which is a real
     * limit worth naming: the same mutation leaves a RUNNING span at 6.5%, because that plate's
     * chips carry empty labels and so contribute no label ink at all. That is why this is the
     * second of two guards rather than the only one - `ExportStripTest`'s
     * `every text run on the export's render path names the chart's own font` reads the source
     * instead and catches a run that draws no pixels in any particular fixture.
     */
    @Test fun `every text run on an exported frame is in the chart's own font`() {
        val games = mapOf(
            1 to GameRef(
                1, "Mega Man Legends", null, emptyList(), colorIndex = 1, players = 10,
                award = null, totalAchievements = 20,
            ),
        )
        val unlocks = (0 until 60).map { i -> unlock(i, t0 + i * day / 2, 10) }
        val frame = JourneyFrame(
            mode = JourneyMode.SPAN,
            main = PlayerJourney(
                "me", unlocks, games,
                listOf(Medal(AwardKind.MASTERED, t0 + day, 1, null, "Mega Man Legends")),
            ),
            rivals = emptyList(), spanFromMillis = t0, spanToMillis = t0 + 31 * day,
        )
        fun renderIn(font: FontFamily, progress: Float) = ExportFrame.render(
            context = ApplicationProvider.getApplicationContext(),
            layout = LayoutEngine.layout(frame, includeNodes = true),
            frame = frame, style = style.copy(labelFont = font), bitmaps = emptyMap(),
            art = emptyMap(), userName = "me", progress = progress,
        ).toPixelMap()

        // A finished span (the end panel) and a running one (the two-line header) - the two shapes
        // whose plate this task rewrote.
        listOf("a finished span" to 1f, "a running span" to 0.5f).forEach { (what, progress) ->
            val mono = renderIn(FontFamily.Monospace, progress)
            val serif = renderIn(FontFamily.Serif, progress)

            var brightInEither = 0
            var brightAndIdentical = 0
            // The whole band, generously: the two-line header's own is 241px tall.
            for (y in 0 until (ExportStrip.TOP + 260f).toInt()) for (x in 0 until ExportFrame.WIDTH) {
                val a = mono[x, y]
                val b = serif[x, y]
                fun bright(c: Color) = c.red > 0.5f && c.green > 0.5f && c.blue > 0.5f
                if (bright(a) || bright(b)) brightInEither++
                if (bright(a) && bright(b) && a == b) brightAndIdentical++
            }

            assertTrue("$what drew no text to speak of ($brightInEither px)", brightInEither > 2000)
            val stuck = brightAndIdentical.toFloat() / brightInEither
            assertTrue(
                "$what: ${(stuck * 100).toInt()}% of the band's ink did not move when labelFont " +
                    "changed from Monospace to Serif ($brightAndIdentical of $brightInEither px). " +
                    "Incidental glyph overlap is 6-8%; a figure like this means a text run is " +
                    "pinned to a typeface that is not the chart's - check every TextStyle( and " +
                    "every raw android.graphics.Paint on the render path",
                stuck < 0.12f,
            )
        }
    }

    /**
     * A FINISHED span's plate has no title on it, and is one line.
     *
     * The other half of the same report: a title drawn on the end panel, which builds none. Pinned
     * on the chips rather than on the pixels because that is where it would go wrong - `isTitle` and
     * [com.twinlore.app.journey.ui.JourneyStripText.Chip.line] are the two tells `ExportStrip`
     * branches on, and `endChips` sets neither. Only `gameHeaderChips` and `spanHeaderChips` build a
     * title chip, and `exportChips` cannot reach either once a span has landed.
     */
    @Test fun `a landed span builds no title chip and stays on one line`() {
        val unlocks = (0 until 60).map { i -> unlock(i, t0 + i * day / 2, 10) }
        val games = mapOf(
            1 to GameRef(
                1, "Mega Man Legends", null, emptyList(), colorIndex = 1, players = 10,
                award = null, totalAchievements = 20,
            ),
        )
        val frame = JourneyFrame(
            mode = JourneyMode.SPAN,
            main = PlayerJourney("me", unlocks, games, emptyList()),
            rivals = emptyList(), spanFromMillis = t0, spanToMillis = t0 + 31 * day,
        )
        val layout = LayoutEngine.layout(frame, includeNodes = true)
        val chips = JourneyStripText.exportChips(
            frame = frame, layout = layout, journeyDateMillis = t0 + 30 * day,
            points = 600, awards = AwardTally(mastered = 1), revealed = 60, games = 1,
            completed = true, progress = 1f, nowPlaying = "Mega Man Legends",
        )

        assertTrue("a landed span must not name a game on its plate", chips.none { it.isTitle })
        assertEquals("and must stay on one baseline", 1, ExportStrip.lineCount(chips))
    }

    /**
     * And it carries no game ICON either, for the same reason it carries no title.
     *
     * The counters a span lands on are `EndPanel`, a different widget from the header that ran, and
     * `EndPanel` has no icon. The icon slot belongs to the running header, and it went on being
     * drawn after the chips beneath it had been swapped - so the finished plate led with whichever
     * game the sweep happened to stop on, in front of totals about every game in the span.
     *
     * Pixels rather than chips here, because the icon never was a chip: it is a separate argument to
     * `drawExportStrip` and the drift was in the caller.
     */
    @Test fun `a landed span draws no game icon on its plate`() {
        val unlocks = (0 until 60).map { i -> unlock(i, t0 + i * day / 2, 10) }
        val games = mapOf(
            1 to GameRef(
                1, "Mega Man Legends", "/Images/001.png", emptyList(), colorIndex = 1, players = 10,
                award = null, totalAchievements = 20,
            ),
        )
        val frame = JourneyFrame(
            mode = JourneyMode.SPAN,
            main = PlayerJourney("me", unlocks, games, emptyList()),
            rivals = emptyList(), spanFromMillis = t0, spanToMillis = t0 + 31 * day,
        )
        // Flat magenta, a colour nothing else on the frame draws, so finding it in the icon's slot
        // is finding the icon.
        val marker = Color(0xFFFF00FF)
        val bitmaps = mapOf(JourneyUrls.media("/Images/001.png") to artOf(marker))

        fun markerPixels(progress: Float): Int {
            val map = render(frame, progress = progress, bitmaps = bitmaps).toPixelMap()
            val x0 = (ExportStrip.LEFT + ExportStrip.PAD_H).toInt()
            val x1 = (ExportStrip.LEFT + ExportStrip.PAD_H + ExportStrip.ICON).toInt()
            val y0 = ExportStrip.TOP.toInt()
            val y1 = y0 + ExportStrip.ICON.toInt()
            var found = 0
            for (y in y0 until y1) for (x in x0 until x1) {
                val c = map[x, y]
                if (c.red > 0.5f && c.blue > 0.5f && c.green < 0.4f) found++
            }
            return found
        }

        // While it runs the header has its icon, which is what makes the landed check meaningful:
        // without this the test would pass on a frame that never drew an icon at all.
        assertTrue("a running span's header draws the game's icon", markerPixels(0.5f) > 100)
        assertEquals("a landed span must not", 0, markerPixels(1f))
    }

    /**
     * NOTHING IS EVER DRAWN IN THE RESERVED GAP ABOVE THE STRIP.
     *
     * Chasing a reported artifact: a faint two-character fragment ("ge") sitting above the counters
     * row on an exported landed span's PNG, at roughly frame (355-420, 25-75) - well inside
     * [ExportStrip.TOP]'s own reserved band (`"Clear of the chart caption drawn above it"`), and well
     * above where any badge or medal can legally reach (`LayoutEngine`'s node/medal y-clamp keeps
     * every icon's top edge at or below 108 virtual units, ~194 frame px at this frame's fit).
     *
     * The fragment's own colour and size trace unambiguously to [JourneyChartRenderer.drawOneNode]'s
     * three `style.panel`-in-`labelFont` glyph draws (the fallback disc and the corner chip a badge
     * wears when its own art fails to decode) - same ink, same font, same `r * 1.2` / `chipR * 1.5`
     * scaling. But every one of those three call sites is provably limited to "!", "★", "•" or the
     * [ChartTypeIcons.TypeMark.Icon] path (confirmed by reading [ChartTypeIcons.markFor], which is
     * an exhaustive `when` over four literal branches with no string ever built from a title, a game
     * name or any other collection indexed by node position), and an instrumented render of a large,
     * dense, mixed-art fixture - closer to the reported frame's own shape than this one, 113
     * achievements across 7 games including one missing its badge art - logged every single glyph
     * drawn and found nothing but those four marks. No reproduction was found.
     *
     * This guard exists anyway, and belongs regardless of whether that investigation ever lands on
     * a live bug: geometrically, this gap must ALWAYS be empty on chrome-off export ([showChartChrome]
     * is false there), so any stray ink here - from this cause or a different one, today or in a
     * future change - is a real defect. Deliberately renders through the exact condition named as
     * easiest to reproduce with: a node whose badge art never decoded.
     */
    @Test fun `nothing is drawn in the reserved gap above the strip on a landed span`() {
        val unlocks = listOf(
            unlock(0, t0, 10),
            Unlock(
                1, "Master Sword", "Find it", 20, "missing-badge", AchievementType.WIN_CONDITION,
                t0 + 5 * day, numAwarded = 10, gameId = 2,
            ),
            unlock(2, t0 + 20 * day, 30),
        )
        val frame = JourneyFrame(
            mode = JourneyMode.SPAN,
            main = PlayerJourney(
                name = "me",
                unlocks = unlocks,
                games = mapOf(
                    1 to GameRef(1, "Game One", null, emptyList(), colorIndex = 0, players = 100, award = null, totalAchievements = 20),
                    2 to GameRef(2, "The Legend of Zelda", null, emptyList(), colorIndex = 1, players = 100, award = null, totalAchievements = 20),
                ),
                medals = listOf(Medal(AwardKind.MASTERED, t0 + 20 * day, 1, null, "Game One")),
            ),
            rivals = emptyList(), spanFromMillis = 0, spanToMillis = t0 + 40 * day,
        )
        // Nobody's badge art decoded - the WIN_CONDITION unlock's fallback disc AND corner chip
        // (once its game icon is missing too) both fall back to the typed mark, and every other
        // node falls back to its own mark or the plain dot.
        val bitmap = render(frame, progress = 1f, bitmaps = emptyMap())
        val map = bitmap.toPixelMap()

        var strayInk = 0
        var firstStray: Pair<Int, Int>? = null
        for (y in 0 until ExportStrip.TOP.toInt()) for (x in 0 until ExportFrame.WIDTH step 2) {
            if (map[x, y] != style.background) {
                strayInk++
                if (firstStray == null) firstStray = x to y
            }
        }
        assertEquals(
            "stray ink in the reserved gap above the strip, first at ${firstStray} " +
                "($strayInk sampled pixels differ from the background)",
            0,
            strayInk,
        )
    }
}
