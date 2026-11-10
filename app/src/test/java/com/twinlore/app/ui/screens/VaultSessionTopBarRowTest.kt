package com.twinlore.app.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.twinlore.app.data.StatusBarMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The vault session bar's row, at the width of the handheld it is read on.
 *
 * A Row measures its unweighted children first, in order, and hands each of
 * them whatever the one before left. The status cluster sits between the pin
 * and the way into Always on Top, so in Performance — where the cluster is
 * five meters wide and wants more than the bar has — it took the whole row and
 * the fold button after it was measured at zero. The button was not clipped or
 * hidden: it was there, 0dp wide, at the far right edge. The cluster's own
 * last child, the connection mark, was crushed the same way by the clip.
 *
 * 640dp because that is the AYN Thor in landscape: 1920px at 3x.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w640dp-h360dp-land")
class VaultSessionTopBarRowTest {

    @get:Rule
    val compose = createComposeRule()

    /** Wider than the whole bar, which is what five meters and a clock are. */
    private val OVER_WIDE = 900.dp

    @Test
    fun `the fold button keeps its size in Performance however wide the cluster is`() {
        compose.setContent { Bar(StatusBarMode.PERFORMANCE, clusterWidth = OVER_WIDE) }

        assertEquals(32f, widthOf("fold"), 0.5f)
    }

    @Test
    fun `the fold button stays at the right edge in Performance`() {
        compose.setContent { Bar(StatusBarMode.PERFORMANCE, clusterWidth = OVER_WIDE) }

        val fold = compose.onNodeWithTag("fold").getUnclippedBoundsInRoot()
        assertEquals("the way out belongs in the corner", 640f, fold.right.value, 0.5f)
    }

    /** The pin is measured before the cluster and was never at risk, but it is
     *  the other control in that bar and the test is cheap. */
    @Test
    fun `the pin keeps its size in Performance`() {
        compose.setContent { Bar(StatusBarMode.PERFORMANCE, clusterWidth = OVER_WIDE) }

        assertEquals(36f, widthOf("pin"), 0.5f)
    }

    /**
     * The bar is wider than the readings on the handheld's own screen, and the
     * leftover has to land somewhere. Not after the fold button: the way into
     * Always on Top keeps the corner whether or not there is room to spare.
     */
    @Test
    fun `the fold button keeps the corner in Performance when the cluster is narrow`() {
        compose.setContent { Bar(StatusBarMode.PERFORMANCE, clusterWidth = 80.dp) }

        val fold = compose.onNodeWithTag("fold").getUnclippedBoundsInRoot()
        assertEquals("the way out belongs in the corner", 640f, fold.right.value, 0.5f)
        assertEquals(32f, widthOf("fold"), 0.5f)
    }

    /** And not before the pin, which is the dead air the panel's bar showed. */
    @Test
    fun `the pin keeps the left edge in Performance`() {
        compose.setContent { Bar(StatusBarMode.PERFORMANCE, clusterWidth = 80.dp) }

        val pin = compose.onNodeWithTag("pin").getUnclippedBoundsInRoot()
        assertEquals("the pin starts where the bar starts", 0f, pin.left.value, 0.5f)
    }

    /**
     * The slot is still offered — the doc switcher's chevron lives in it — but
     * it is unweighted, so it takes only what it draws and none of the row.
     */
    @Test
    fun `Performance gives the title no share of the row`() {
        compose.setContent { Bar(StatusBarMode.PERFORMANCE, clusterWidth = OVER_WIDE) }

        assertEquals(0f, widthOf("title"), 0.5f)
    }

    /**
     * Standard is untouched: the name keeps the left edge and the fold button
     * keeps the right one, with the cluster between them at its natural width.
     * Making the cluster flexible in both modes would cost this — two weighted
     * children split the row, and the name ends up starting 166dp in.
     */
    @Test
    fun `Standard keeps the name at the left edge and the fold button at the right`() {
        compose.setContent { Bar(StatusBarMode.STANDARD, clusterWidth = 120.dp) }

        val title = compose.onNodeWithTag("title").getUnclippedBoundsInRoot()
        val fold = compose.onNodeWithTag("fold").getUnclippedBoundsInRoot()
        assertEquals("the name starts where the bar starts", 0f, title.left.value, 0.5f)
        assertEquals("the way out belongs in the corner", 640f, fold.right.value, 0.5f)
    }

    @Test
    fun `Standard leaves the cluster at its own width`() {
        compose.setContent { Bar(StatusBarMode.STANDARD, clusterWidth = 120.dp) }

        assertEquals(120f, widthOf("cluster"), 0.5f)
    }

    private fun widthOf(tag: String): Float {
        val b = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot()
        return (b.right - b.left).value
    }

    /** The real row, with boxes standing in for the controls it hosts. */
    @Composable
    private fun Bar(mode: StatusBarMode, clusterWidth: androidx.compose.ui.unit.Dp) {
        VaultSessionTopBarRow(
            statusBarMode = mode,
            isLandscape = true,
            modifier = Modifier.fillMaxWidth(),
            title = { m -> Box(m.height(24.dp).testTag("title")) },
            pin = { Box(Modifier.size(36.dp).testTag("pin")) },
            divider = { Box(Modifier.size(0.dp).testTag("divider")) },
            cluster = { m -> Box(m.width(clusterWidth).height(24.dp).testTag("cluster")) },
            fold = { Box(Modifier.size(32.dp).testTag("fold")) },
        )
    }
}
