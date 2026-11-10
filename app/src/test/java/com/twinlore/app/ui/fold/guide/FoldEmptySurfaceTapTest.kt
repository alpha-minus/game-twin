package com.twinlore.app.ui.fold.guide

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The "open the app" screens answer a tap too.
 *
 * A surface with nothing on it has nothing that takes a touch, so on a vault
 * with no guide and no images a hidden bar could not be brought back from
 * either one - the two surfaces where you are most likely to be looking for
 * the bar, because there is nothing else on them to look at.
 *
 * The app button still gets its own taps: it consumes them, so the surface
 * never sees them and one press does one thing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class FoldEmptySurfaceTapTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `a tap on the empty guide brings the bar back`() {
        var chrome: Boolean? = null
        compose.setContent {
            FoldGuideContent(
                guide = null,
                loading = false,
                onOpenApp = {},
                onSetChrome = { chrome = it },
                chromeVisible = false,
            )
        }
        compose.onNodeWithText(FOLD_GUIDE_NONE).performClick()
        assertEquals(true, chrome)
    }

    @Test
    fun `and a second tap clears it again`() {
        var chrome: Boolean? = null
        compose.setContent {
            FoldGuideContent(
                guide = null,
                loading = false,
                onOpenApp = {},
                onSetChrome = { chrome = it },
                chromeVisible = true,
            )
        }
        compose.onNodeWithText(FOLD_GUIDE_NONE).performClick()
        assertEquals(false, chrome)
    }

    @Test
    fun `the app button on the empty guide is not a tap on the surface`() {
        var chrome: Boolean? = null
        var opened = false
        compose.setContent {
            FoldGuideContent(
                guide = null,
                loading = false,
                onOpenApp = { opened = true },
                onSetChrome = { chrome = it },
                chromeVisible = false,
            )
        }
        compose.onNodeWithText(FOLD_GUIDE_OPEN_IN_APP).performClick()
        assertTrue(opened)
        assertNull(chrome)
    }

    @Test
    fun `a tap on the empty image surface moves the bar`() {
        var toggles = 0
        renderEmptyImages(onToggleChrome = { toggles++ })
        compose.onNodeWithText(FOLD_IMAGE_NONE).performClick()
        assertEquals(1, toggles)
    }

    @Test
    fun `the app button on the empty image surface is not a tap on the surface`() {
        var toggles = 0
        var opened = false
        renderEmptyImages(onToggleChrome = { toggles++ }, onOpenApp = { opened = true })
        compose.onNodeWithText(FOLD_GUIDE_OPEN_IN_APP).performClick()
        assertTrue(opened)
        assertEquals(0, toggles)
    }

    // The shutter comes and goes with the bar it sits above, on the empty
    // surface as on a picture. Left always-on it had nothing to sit above
    // once the bar was hidden and fell to the bottom edge, into the strip the
    // gesture bar wants and under a handle parked in that corner.
    @Test
    fun `the shutter follows the chrome on the empty image surface`() {
        renderEmptyImages(chromeVisible = true)
        compose.onNodeWithContentDescription(FOLD_IMAGE_SCREENSHOT).assertExists()
    }

    @Test
    fun `no shutter on the empty image surface once the chrome is away`() {
        renderEmptyImages(chromeVisible = false)
        compose.onNodeWithContentDescription(FOLD_IMAGE_SCREENSHOT).assertDoesNotExist()
    }

    private fun renderEmptyImages(
        onToggleChrome: () -> Unit = {},
        onOpenApp: () -> Unit = {},
        chromeVisible: Boolean = true,
    ) {
        compose.setContent {
            FoldImageContent(
                images = emptyList(),
                index = 0,
                vaultId = "v1",
                chromeVisible = chromeVisible,
                capturing = false,
                view = null,
                onPrevious = {},
                onNext = {},
                onToggleChrome = onToggleChrome,
                onOpenApp = onOpenApp,
                onTakeScreenshot = {},
                onViewChanged = {},
            )
        }
    }
}
