package com.twinlore.app.journey.ui.chart

import androidx.compose.ui.geometry.Offset
import com.twinlore.app.journey.domain.LayoutEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewportCullTest {

    private val w = LayoutEngine.W
    private val h = LayoutEngine.H

    // --- visibleRect ---

    @Test fun `identity view sees the whole stage`() {
        val rect = ViewportCull.visibleRect(ChartZoom.NONE, w, h)
        assertEquals(VirtualRect(0f, 0f, w, h), rect)
    }

    @Test fun `a zoomed, panned view maps back through offset and zoom by hand`() {
        // screen = offset + zoom * virtual, so virtual = (screen - offset) / zoom. At the corners
        // of the stage (0,0) and (w,h): left = (0 - -200) / 2 = 100, right = (w - -200) / 2,
        // top = (0 - -100) / 2 = 50, bottom = (h - -100) / 2.
        val view = ChartZoom(zoom = 2f, offset = Offset(-200f, -100f))
        val rect = ViewportCull.visibleRect(view, w, h)
        assertEquals(100f, rect.left, 0.001f)
        assertEquals(50f, rect.top, 0.001f)
        assertEquals((w + 200f) / 2f, rect.right, 0.001f)
        assertEquals((h + 100f) / 2f, rect.bottom, 0.001f)
    }

    @Test fun `a zoomed view shows a smaller virtual rectangle than identity`() {
        val view = ChartZoom.NONE.zoomedTo(4f, Offset(w / 2f, h / 2f))
        val rect = ViewportCull.visibleRect(view, w, h)
        val width = rect.right - rect.left
        val height = rect.bottom - rect.top
        // Quartered in each axis at 4x, modulo the edge clamp - well under half either way.
        assertTrue("rect too wide: $width", width < w / 2f)
        assertTrue("rect too tall: $height", height < h / 2f)
    }

    // --- stageViewport: what the canvas actually shows of the stage ---

    @Test fun `a canvas of the stage's own aspect shows exactly the stage`() {
        // 2120x1200 is 1060x600 doubled, so the fit is exact and there is no letterbox at all.
        val stage = ViewportCull.stageViewport(w, h, 2120f, 1200f)
        assertEquals(0f, stage.left, 0.01f)
        assertEquals(0f, stage.top, 0.01f)
        assertEquals(w, stage.right, 0.01f)
        assertEquals(h, stage.bottom, 0.01f)
    }

    @Test fun `a portrait canvas shows most of a stage again above and below the chart`() {
        // Fitted by width (1080/1060 is far tighter than 2340/600), so the extra room is vertical.
        val stage = ViewportCull.stageViewport(w, h, 1080f, 2340f)
        assertEquals(0f, stage.left, 0.5f)
        assertEquals(w, stage.right, 0.5f)
        assertTrue("top not letterboxed: ${stage.top}", stage.top < -800f)
        assertTrue("bottom not letterboxed: ${stage.bottom}", stage.bottom > h + 800f)
    }

    @Test fun `an unknown canvas size falls back to the fitted stage box`() {
        assertEquals(VirtualRect(0f, 0f, w, h), ViewportCull.stageViewport(w, h, 0f, 0f))
    }

    /**
     * The bug this fixture was written for: culling against the fitted content box alone hid icons
     * the user was plainly looking at. On a phone-shaped canvas the chart is fitted by its width
     * and the whole of the stage's height is on screen, so the follow camera's 2.2x view shows every
     * icon in its column - while the content-box rect covered only the middle 272 of 600 units.
     */
    @Test fun `the fitted content box alone culls icons a portrait canvas is showing`() {
        val view = FollowCamera.target(Offset(520f, 300f))
        val fitted = ViewportCull.visibleRect(view, w, h)
        val real = ViewportCull.visibleRect(view, w, h, 1080f, 2340f)
        val icon = Offset(520f, 40f)
        assertFalse(
            "fixture: the content-box rect must be the one that hides this icon",
            ViewportCull.intersects(fitted, icon.x, icon.y, 14f, allowance = 40f),
        )
        assertTrue(
            "an icon on screen must never be culled",
            ViewportCull.intersects(real, icon.x, icon.y, 14f),
        )
    }

    /**
     * The cull has to agree with the forward transform, not approximate it. This walks a grid of
     * candidate icon positions across the stage under a realistic follow-camera view, decides
     * independently whether each one's disc lands anywhere on a portrait canvas by mapping it
     * *forwards* (virtual -> stage -> screen pixels, exactly as `withStage` composes it), and
     * requires the cull to keep every one of those.
     */
    @Test fun `nothing the forward transform puts on screen is culled`() {
        val canvasW = 1080f
        val canvasH = 2340f
        val view = FollowCamera.target(Offset(520f, 300f))
        assertEquals("fixture must be the follow camera's zoom", FollowCamera.ZOOM, view.zoom, 0.001f)
        val rect = ViewportCull.visibleRect(view, w, h, canvasW, canvasH)

        val fit = minOf(canvasW / w, canvasH / h)
        val originX = (canvasW - w * fit) / 2f
        val originY = (canvasH - h * fit) / 2f
        val r = 14f
        val discPx = r * view.zoom * fit
        var onScreen = 0
        var x = 0f
        while (x <= w) {
            var y = 0f
            while (y <= h) {
                val stage = view.toStage(Offset(x, y))
                val px = originX + fit * stage.x
                val py = originY + fit * stage.y
                // A 1px inset keeps the sweep off the exact boundary, where the two sides agreeing
                // is a float comparison rather than a statement about the geometry.
                val visible = px + discPx >= 1f && px - discPx <= canvasW - 1f &&
                    py + discPx >= 1f && py - discPx <= canvasH - 1f
                if (visible) {
                    onScreen++
                    assertTrue(
                        "icon at ($x, $y) is drawn at ($px, $py) on a ${canvasW}x$canvasH canvas but was culled",
                        ViewportCull.intersects(rect, x, y, r),
                    )
                }
                y += 10f
            }
            x += 10f
        }
        assertTrue("fixture drew nothing on screen", onScreen > 200)
    }

    @Test fun `visibleRect and toVirtual agree on the stage corners`() {
        val view = ChartZoom.NONE.zoomedTo(3f, Offset(700f, 120f))
        val rect = ViewportCull.visibleRect(view, w, h)
        val topLeft = view.toVirtual(Offset.Zero)
        val bottomRight = view.toVirtual(Offset(w, h))
        assertEquals(topLeft.x, rect.left, 0.001f)
        assertEquals(topLeft.y, rect.top, 0.001f)
        assertEquals(bottomRight.x, rect.right, 0.001f)
        assertEquals(bottomRight.y, rect.bottom, 0.001f)
    }

    /**
     * A wide chart is culled against the same 1060x600 viewport - that box is what the letterbox
     * fits - with the view's offset carrying where along the widened timeline the camera is. Culling
     * against the layout's own width instead would describe a viewport three times too wide and keep
     * every icon on the chart.
     */
    @Test fun `a wide chart culls against the viewport, not its own width`() {
        val contentWidth = w * 3f
        val runner = Offset(contentWidth / 2f, h / 2f)
        val view = FollowCamera.target(runner, contentWidth)
        val rect = ViewportCull.visibleRect(view, w, h)

        assertTrue("the runner must be on screen", ViewportCull.intersects(rect, runner.x, runner.y, 14f))
        assertFalse(
            "an icon a full timeline away must be culled",
            ViewportCull.intersects(rect, 40f, runner.y, 14f, allowance = 40f),
        )
        // 2.2x of a 1060-wide viewport is ~482 virtual units of a wide chart, same as a narrow one.
        assertEquals(w / FollowCamera.ZOOM, rect.right - rect.left, 1f)
    }

    // --- intersects ---

    private val rect = VirtualRect(0f, 0f, 100f, 100f)

    @Test fun `a circle well inside the rect intersects`() {
        assertTrue(ViewportCull.intersects(rect, 50f, 50f, 10f))
    }

    @Test fun `a circle well outside the rect does not intersect`() {
        assertFalse(ViewportCull.intersects(rect, 300f, 50f, 10f))
    }

    @Test fun `a circle straddling the right edge intersects`() {
        // spans x in 95..115; 95 <= rect.right (100).
        assertTrue(ViewportCull.intersects(rect, 105f, 50f, 10f))
    }

    @Test fun `a circle exactly touching the edge intersects (inclusive boundary)`() {
        // spans x in 100..120; the near edge lands exactly on rect.right.
        assertTrue(ViewportCull.intersects(rect, 110f, 50f, 10f))
    }

    @Test fun `a circle just past the touching boundary does not intersect`() {
        assertFalse(ViewportCull.intersects(rect, 110.1f, 50f, 10f))
    }

    @Test fun `outside on the y axis does not intersect even if x overlaps`() {
        assertFalse(ViewportCull.intersects(rect, 50f, 200f, 10f))
    }

    @Test fun `allowance brings an otherwise-clear circle back into range`() {
        // Circle spans x in 125..145 - clear of the rect with no allowance...
        assertFalse(ViewportCull.intersects(rect, 135f, 50f, 10f))
        // ...but a 40-unit label allowance pulls the padded edge back to 85, inside the rect.
        assertTrue(ViewportCull.intersects(rect, 135f, 50f, 10f, allowance = 40f))
    }

    @Test fun `allowance is not enough to save a circle far outside`() {
        assertFalse(ViewportCull.intersects(rect, 300f, 50f, 10f, allowance = 40f))
    }
}
