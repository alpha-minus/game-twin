package com.twinlore.app.journey.ui.chart

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The settled-icon layer's invalidation, which is the whole of its risk.
 *
 * What it caches is written once and never repainted, so a bake that survives a change it should
 * not have survived is a wrong pixel for the life of the chart - and unlike an ordinary cache there
 * is no later frame that puts it right. These pin the two rules that stop that: the layer is
 * cleared whenever the transform or the canvas it was built for moves, and it is NOT cleared when
 * neither did (or the cache would be pure overhead, repainting every icon every frame).
 *
 * Robolectric because the bitmap is a real one - [IconLayer.ensure] erases it through the Android
 * bitmap underneath - but nothing here touches a composition.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IconLayerTest {

    private val density = Density(1f)

    @Test
    fun `a degenerate canvas is refused rather than allocated for`() {
        val layer = IconLayer(4, 2)

        assertFalse("a zero-width canvas has no layer", layer.ensure(0, 100, 2f, 0f, 0f))
        assertFalse("nor a zero-height one", layer.ensure(100, 0, 2f, 0f, 0f))
        assertFalse("nor a collapsed transform", layer.ensure(100, 100, 0f, 0f, 0f))
        assertNull("and none of them may allocate a bitmap", layer.bitmap)
    }

    @Test
    fun `the first call allocates and reports the transform back`() {
        val layer = IconLayer(3, 1)

        assertTrue(layer.ensure(120, 80, 2.5f, 7f, -3f))

        val bitmap = layer.bitmap
        assertNotNull("the first ensure must allocate", bitmap)
        assertEquals(120, bitmap!!.width)
        assertEquals(80, bitmap.height)
        assertEquals(2.5f, layer.k, 0f)
        assertEquals(7f, layer.tx, 0f)
        assertEquals(-3f, layer.ty, 0f)
    }

    @Test
    fun `an unchanged transform and canvas keep everything already baked`() {
        val layer = IconLayer(3, 2)
        layer.ensure(64, 64, 2f, 0f, 0f)
        layer.nodeDrawn[1] = true
        layer.medalDrawn[0] = true
        val first = layer.bitmap

        assertTrue(layer.ensure(64, 64, 2f, 0f, 0f))

        assertSame("the buffer must be reused, not reallocated", first, layer.bitmap)
        assertTrue("a settled badge must stay settled", layer.nodeDrawn[1])
        assertTrue(layer.medalDrawn[0])
    }

    @Test
    fun `a moved transform drops every bake`() {
        listOf(
            Triple(3f, 0f, 0f),   // zoomed
            Triple(2f, 5f, 0f),   // panned in x
            Triple(2f, 0f, 5f),   // panned in y
        ).forEach { (k, tx, ty) ->
            val layer = IconLayer(3, 2)
            layer.ensure(64, 64, 2f, 0f, 0f)
            layer.nodeDrawn.fill(true)
            layer.medalDrawn.fill(true)

            layer.ensure(64, 64, k, tx, ty)

            assertTrue(
                "k=$k tx=$tx ty=$ty must invalidate every badge",
                layer.nodeDrawn.none { it },
            )
            assertTrue("and every medallion", layer.medalDrawn.none { it })
            assertEquals(k, layer.k, 0f)
            assertEquals(tx, layer.tx, 0f)
            assertEquals(ty, layer.ty, 0f)
        }
    }

    @Test
    fun `a resized canvas drops every bake and takes a new buffer`() {
        val layer = IconLayer(3, 2)
        layer.ensure(64, 64, 2f, 0f, 0f)
        layer.nodeDrawn.fill(true)
        val first = layer.bitmap

        layer.ensure(64, 96, 2f, 0f, 0f)

        assertTrue(first !== layer.bitmap)
        assertEquals(96, layer.bitmap!!.height)
        assertTrue(layer.nodeDrawn.none { it })
    }

    /**
     * The pixels an invalidation leaves behind matter as much as the flags: the buffer is reused
     * in place when only the transform moved, so anything not erased would show through the next
     * bake as a ghost of the previous view.
     */
    @Test
    fun `a moved transform erases the pixels, not only the flags`() {
        val layer = IconLayer(1, 0)
        layer.ensure(32, 32, 2f, 0f, 0f)
        layer.update(density, LayoutDirection.Ltr) {
            drawRect(color = Color.Red, topLeft = Offset.Zero, size = Size(16f, 16f))
        }
        assertTrue(
            "the fixture must actually paint something",
            layer.bitmap!!.toPixelMap()[1, 1].alpha > 0f,
        )

        layer.ensure(32, 32, 2f, 4f, 0f)

        assertEquals(
            "a stale pixel would ghost through the next bake",
            0f,
            layer.bitmap!!.toPixelMap()[1, 1].alpha,
            0f,
        )
    }

    /**
     * The layer bakes in canvas pixels with the stage transform applied, so that an icon added to
     * it lands on exactly the pixels the live pass drew it on. A virtual point p must therefore
     * come out at `p * k + t`.
     */
    @Test
    fun `update draws through the same virtual to pixel transform the live pass uses`() {
        val layer = IconLayer(1, 0)
        val k = 2f
        val tx = 6f
        val ty = 4f
        layer.ensure(64, 64, k, tx, ty)

        // A 4x4 virtual square at virtual (10, 5) -> pixels (26, 14) to (34, 22).
        layer.update(density, LayoutDirection.Ltr) {
            drawRect(color = Color.Green, topLeft = Offset(10f, 5f), size = Size(4f, 4f))
        }

        val pixels = layer.bitmap!!.toPixelMap()
        assertTrue("inside the mapped rect must be painted", pixels[28, 16].alpha > 0f)
        assertEquals("just left of it must not be", 0f, pixels[25, 16].alpha, 0f)
        assertEquals("nor just above it", 0f, pixels[28, 13].alpha, 0f)
        assertEquals("nor just past its far edge", 0f, pixels[35, 16].alpha, 0f)
    }

    @Test
    fun `update before ensure is a no-op rather than a crash`() {
        val layer = IconLayer(1, 0)
        var ran = false

        layer.update(density, LayoutDirection.Ltr) { ran = true }

        assertFalse("there is nowhere to draw yet", ran)
    }
}
