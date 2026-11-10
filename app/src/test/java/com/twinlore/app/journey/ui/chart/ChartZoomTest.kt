package com.twinlore.app.journey.ui.chart

import androidx.compose.ui.geometry.Offset
import com.twinlore.app.journey.domain.LayoutEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartZoomTest {

    private val w = LayoutEngine.W
    private val h = LayoutEngine.H

    @Test fun `1x is the overview of an unstretched chart and cannot be panned`() {
        val z = ChartZoom.NONE.pannedBy(Offset(200f, 200f))
        assertTrue(z.isOverview)
        assertEquals(Offset.Zero, z.offset)
        assertEquals(Offset(120f, 45f), z.toVirtual(Offset(120f, 45f)))
    }

    @Test fun `zoom is clamped to the 1x to 8x range`() {
        assertEquals(8f, ChartZoom.NONE.zoomedTo(9f, Offset(w / 2, h / 2)).zoom, 0.001f)
        assertEquals(1f, ChartZoom(zoom = 3f).zoomedTo(0.2f, Offset(w / 2, h / 2)).zoom, 0.001f)
    }

    @Test fun `zooming keeps the focal point under the finger`() {
        val focus = Offset(300f, 400f)
        val before = ChartZoom.NONE.toVirtual(focus)
        val after = ChartZoom.NONE.zoomedTo(2.5f, focus)
        assertEquals(before.x, after.toVirtual(focus).x, 0.01f)
        assertEquals(before.y, after.toVirtual(focus).y, 0.01f)
    }

    /** Focal zoom near an edge has to give way to the clamp; the content edge stays put. */
    @Test fun `content edges never leave the viewport`() {
        listOf(Offset(0f, 0f), Offset(w, h), Offset(w, 0f), Offset(12f, h - 4f)).forEach { focus ->
            listOf(1.2f, 2.5f, 4f).forEach { target ->
                val z = ChartZoom.NONE.zoomedTo(target, focus).pannedBy(Offset(9000f, -9000f))
                assertTrue("left gap at $focus/$target", z.offset.x <= 0.01f)
                assertTrue("top gap at $focus/$target", z.offset.y <= 0.01f)
                assertTrue("right gap at $focus/$target", z.offset.x + w * z.zoom >= w - 0.01f)
                assertTrue("bottom gap at $focus/$target", z.offset.y + h * z.zoom >= h - 0.01f)
            }
        }
    }

    @Test fun `toStage and toVirtual are inverses`() {
        val z = ChartZoom.NONE.zoomedTo(3f, Offset(700f, 120f))
        val virtual = Offset(512f, 333f)
        val roundTrip = z.toVirtual(z.toStage(virtual))
        assertEquals(virtual.x, roundTrip.x, 0.01f)
        assertEquals(virtual.y, roundTrip.y, 0.01f)
    }

    @Test fun `panning into view brings an off screen point back`() {
        val z = ChartZoom.NONE.zoomedTo(4f, Offset(w - 20f, h - 20f))
        val target = Offset(40f, 40f)
        assertTrue("fixture must place the point off screen", z.toStage(target).x < 0f)

        val moved = z.panIntoView(target)
        val at = moved.toStage(target)
        assertTrue("point still off screen at $at", at.x >= -0.01f && at.x <= w)
        assertTrue("point still off screen at $at", at.y >= -0.01f && at.y <= h)
        assertEquals("panning must not change the zoom", z.zoom, moved.zoom, 0.001f)
    }

    // --- pinch ---

    private val focus = Offset(w / 2f, h / 2f)

    /**
     * The reported bug: fully zoomed out with follow-the-runner on, pinching in did nothing at all.
     * A pinch arrives as a stream of small events, so every intermediate view on the way in sits
     * below the follow camera's exit zoom - and the overview rule used to snap each one of them
     * back to 1x, so the fingers never got anywhere.
     */
    @Test fun `pinching in from the overview works while follow mode is on`() {
        var view = ChartZoom.NONE
        var previous = view.zoom
        repeat(30) { step ->
            view = view.pinched(focus, gestureZoom = 1.03f, pan = Offset.Zero, followMode = true)
            assertTrue("zoom went backwards at step $step: ${view.zoom}", view.zoom > previous)
            previous = view.zoom
        }
        assertTrue("pinch never got past the exit zoom: ${view.zoom}", view.zoom > FollowCamera.EXIT_ZOOM)
    }

    @Test fun `a single pinch in step leaves the identity view behind`() {
        val view = ChartZoom.NONE.pinched(focus, gestureZoom = 1.05f, pan = Offset.Zero, followMode = true)
        assertFalse("a pinch in must not be snapped back to the overview", view.isOverview)
        assertEquals(1.05f, view.zoom, 0.001f)
    }

    /** The rule the exit zoom is actually for: pinching back out asks for the whole chart. */
    @Test fun `pinching out past the exit zoom lands on the overview`() {
        val following = FollowCamera.target(Offset(520f, 300f))
        val view = following.pinched(focus, gestureZoom = 0.5f, pan = Offset.Zero, followMode = true)
        assertEquals(ChartZoom.NONE, view)
    }

    @Test fun `pinching out only a little keeps the zoom`() {
        val view = ChartZoom(zoom = 2.2f).clamped()
            .pinched(focus, gestureZoom = 0.9f, pan = Offset.Zero, followMode = true)
        assertEquals(1.98f, view.zoom, 0.001f)
    }

    @Test fun `without follow mode the exit zoom rule does not apply`() {
        val view = ChartZoom(zoom = 2f).clamped()
            .pinched(focus, gestureZoom = 0.6f, pan = Offset.Zero, followMode = false)
        assertEquals(1.2f, view.zoom, 0.001f)
    }

    @Test fun `a pan only pinch keeps the zoom and moves the view`() {
        val start = ChartZoom.NONE.zoomedTo(2.5f, focus)
        val view = start.pinched(focus, gestureZoom = 1f, pan = Offset(30f, 0f), followMode = true)
        assertEquals(start.zoom, view.zoom, 0.001f)
        assertEquals(start.offset.x + 30f, view.offset.x, 0.001f)
    }

    @Test fun `panning into view does nothing at the overview`() {
        assertEquals(ChartZoom.NONE, ChartZoom.NONE.panIntoView(Offset(-500f, -500f)))
        assertFalse(ChartZoom.NONE.zoomedTo(2f, Offset(0f, 0f)).isOverview)
    }

    // --- wide charts ---

    private val wide = ChartZoom.overviewFor(w * 3f)

    /** Zooming out on a wide chart means "show me all of it", not "show me 1060 units of it". */
    @Test fun `the overview of a wide chart is zoomed out below 1`() {
        assertEquals(1f / 3f, wide.minZoom, 0.0001f)
        assertEquals(1f / 3f, wide.zoom, 0.0001f)
        assertTrue(wide.isOverview)
        // Whole content across the viewport, and the shorter content centred vertically.
        assertEquals(0f, wide.offset.x, 0.01f)
        assertEquals((h - h / 3f) / 2f, wide.offset.y, 0.01f)
    }

    @Test fun `an unstretched chart never zooms out past 1`() {
        assertEquals(1f, ChartZoom.NONE.minZoom, 0.0001f)
        assertEquals(1f, ChartZoom.NONE.zoomedTo(0.2f, focus).zoom, 0.0001f)
    }

    /** At 1x a 3x-wide chart shows a third of itself, and pans across the other two thirds. */
    @Test fun `a wide chart pans across the whole timeline`() {
        val view = wide.zoomedTo(1f, Offset(0f, h / 2f))
        assertEquals(1f, view.zoom, 0.0001f)

        val toTheEnd = view.pannedBy(Offset(-9000f, 0f))
        assertEquals("the right edge must land on the viewport's right edge", w - w * 3f, toTheEnd.offset.x, 0.01f)
        val toTheStart = toTheEnd.pannedBy(Offset(9000f, 0f))
        assertEquals(0f, toTheStart.offset.x, 0.01f)
    }

    @Test fun `a wide chart's content edges never leave the viewport`() {
        listOf(0f, w * 1.5f, w * 3f).forEach { x ->
            listOf(0.5f, 1f, 2.5f, 4f).forEach { target ->
                val z = wide.zoomedTo(target, Offset(x.coerceAtMost(w), h / 2f))
                    .pannedBy(Offset(9000f, -9000f))
                assertTrue("left gap at $x/$target", z.offset.x <= 0.01f)
                assertTrue(
                    "right gap at $x/$target",
                    z.offset.x + w * 3f * z.zoom >= w - 0.01f,
                )
            }
        }
    }

    /**
     * The overview rule scales with the floor: it fires at 1.3 times "the whole chart is visible",
     * not at an absolute 1.3x. On a wide chart 1.3x absolute is still most of the timeline off
     * screen, so snapping there would end a pinch-out well short of what it asked for.
     */
    @Test fun `pinching out on a wide chart lands on its own overview`() {
        var view = FollowCamera.target(Offset(1500f, 300f), contentWidth = w * 3f)
        val once = view.pinched(focus, gestureZoom = 0.9f, pan = Offset.Zero, followMode = true)
        assertFalse("one step out is not the overview yet: ${once.zoom}", once.isOverview)

        repeat(30) { view = view.pinched(focus, gestureZoom = 0.9f, pan = Offset.Zero, followMode = true) }
        assertEquals(wide, view)
        assertEquals(w * 3f, view.contentWidth, 0.01f)
    }

    @Test fun `a wide chart keeps its content width through zoom, pan and pinch`() {
        val widths = listOf(
            wide.zoomedTo(2f, focus),
            wide.pannedBy(Offset(-40f, 0f)),
            wide.pinched(focus, gestureZoom = 1.4f, pan = Offset(-10f, 0f), followMode = false),
        ).map { it.contentWidth }
        assertEquals(listOf(w * 3f, w * 3f, w * 3f), widths)
    }
}
