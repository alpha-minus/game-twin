package com.twinlore.app.ui.screens.guide

import org.junit.Assert.assertTrue
import org.junit.Test

class FineRotateJsTest {

    @Test
    fun `drives the template rotation globals`() {
        val js = fineRotateJs(1f / 6f)
        assertTrue("reads the wheel's own detent", js.contains("typeof angled === 'number'"))
        assertTrue("advances the shared angle", js.contains("currAngle += step * f"))
        assertTrue("rotates the active target", js.contains("target.rotate({ animateTo: currAngle })"))
        assertTrue("persists to the active layer", js.contains("storeAngles()"))
        assertTrue("keeps drag consistent", js.contains("prevAngle = currAngle"))
    }

    @Test
    fun `embeds the fraction and preserves sign`() {
        assertTrue("positive fraction passed through", fineRotateJs(1f / 6f).contains("})(0.16666667"))
        assertTrue("negative fraction rotates the other way", fineRotateJs(-1f / 6f).contains("})(-0.16666667"))
    }

    @Test
    fun `no-ops when globals are missing`() {
        assertTrue("falls through to a harmless marker", fineRotateJs(0.1f).contains("return 'nofine'"))
    }
}
