package com.twinlore.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.unit.dp
import com.twinlore.app.data.ThemePalette
import com.twinlore.app.data.metrics.MetricSample
import com.twinlore.app.data.metrics.SystemMetricsSnapshot
import com.twinlore.app.ui.LocalThemePalette
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The cluster keeps its width whatever the numbers say.
 *
 * Each meter's text carries its own reading, so "PWR -" becoming "PWR 9.7W"
 * and "TMP 36" becoming "TMP 100" changes how wide the row wants to be. The
 * row is measured before the clock and the way out in the bars that host it,
 * so every poll shifted everything to its right. A status readout that moves
 * the controls beside it is worse than one that reads slightly short.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w600dp-h400dp-land")
class PerfMetersWidthTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `the cluster is the same width whatever the readings are`() {
        compose.setContent {
            CompositionLocalProvider(LocalThemePalette provides ThemePalette.OBSIDIAN) {
                MaterialTheme {
                    Column {
                        PerfMeters(quiet(), modifier = Modifier.testTag("meters"))
                        PerfMeters(busy(), modifier = Modifier.testTag("meters"))
                    }
                }
            }
        }

        val nodes = compose.onAllNodesWithTag("meters", useUnmergedTree = true)
        val quiet = nodes[0].getUnclippedBoundsInRoot()
        val busy = nodes[1].getUnclippedBoundsInRoot()

        assertEquals(
            "the cluster must not resize with its readings",
            (quiet.right - quiet.left).value,
            (busy.right - busy.left).value,
            0.5f,
        )
    }

    /** Nothing readable yet, which is what a cold poll looks like. */
    private fun quiet() = SystemMetricsSnapshot()

    /** Every reading, at its widest: long clocks, a decimal watt, three-digit heat. */
    private fun busy() = SystemMetricsSnapshot(
        cpu = MetricSample(fraction = 0.9f, display = "3.2"),
        gpu = MetricSample(fraction = 0.8f, display = "1200"),
        ram = MetricSample(fraction = 0.7f, display = "15.9"),
        power = MetricSample(fraction = 0.6f, display = "12.3", estimated = true),
        temp = MetricSample(fraction = 0.5f, display = "100", rawCelsius = 100f),
    )
}
