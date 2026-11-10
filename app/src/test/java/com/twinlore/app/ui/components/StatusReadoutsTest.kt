package com.twinlore.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.twinlore.app.data.NetworkStatus
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
 * The group every status bar ends with: connection, time, battery. Drawn the
 * same in every mode, and never the thing that gives way for the meters.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w600dp-h400dp-land")
class StatusReadoutsTest {

    @get:Rule
    val compose = createComposeRule()

    private val wifi = NetworkStatus(hasInternet = true, isWifi = true, signalStrength = -50)

    /** A bar shaped like the real ones: meters weighted on the left, the readouts packed to the end. */
    @Composable
    private fun Bar(width: Dp?, meters: Boolean, network: NetworkStatus = wifi, battery: BatteryReading? = BatteryReading(49, charging = true)) {
        CompositionLocalProvider(LocalThemePalette provides ThemePalette.OBSIDIAN) {
            MaterialTheme {
                Row(
                    modifier = if (width != null) Modifier.width(width) else Modifier,
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    if (meters) PerfMeters(busy(), modifier = Modifier.weight(1f, fill = false))
                    StatusReadouts(network, "12:34", battery, Color.White, pixelFont = false)
                }
            }
        }
    }

    @Test
    fun `the battery is a meter, and charging is said`() {
        compose.setContent { Bar(width = null, meters = false) }
        compose.onNodeWithContentDescription("Battery 49 percent, charging", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `a lost connection is said, and a weak one is not mistaken for it`() {
        var network by mutableStateOf(NetworkStatus.OFFLINE)
        compose.setContent { Bar(width = null, meters = false, network = network) }
        compose.onNodeWithContentDescription("No connection", useUnmergedTree = true).assertExists()

        network = wifi
        compose.waitForIdle()
        compose.onNodeWithContentDescription("WiFi signal", substring = true, useUnmergedTree = true).assertExists()
        assertEquals(
            0,
            compose.onAllNodesWithContentDescription("No connection", useUnmergedTree = true).fetchSemanticsNodes().size,
        )
    }

    /**
     * The alignment that was seen: switching between Standard and
     * Performance slid the connection mark along the bar, because the battery
     * changed places between the two. Now the meters only arrive on the left.
     */
    @Test
    fun `the readouts stay put when the meters come and go`() {
        var meters by mutableStateOf(false)
        compose.setContent { Bar(width = 420.dp, meters = meters) }
        fun at(description: String) = compose
            .onNodeWithContentDescription(description, substring = true, useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val wifiBefore = at("WiFi signal")
        val batteryBefore = at("Battery 49")

        meters = true
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Processor clock", substring = true, useUnmergedTree = true).assertExists()
        assertEquals(wifiBefore, at("WiFi signal"))
        assertEquals(batteryBefore, at("Battery 49"))
    }

    /** However narrow the bar, the meters give up readings and the readouts keep their size. */
    @Test
    fun `a narrow bar takes from the meters, never the readouts`() {
        var width by mutableStateOf<Dp?>(null)
        compose.setContent { Bar(width = width, meters = true) }
        fun size(description: String) = compose
            .onNodeWithContentDescription(description, substring = true, useUnmergedTree = true)
            .fetchSemanticsNode().size
        val battery = size("Battery 49")
        val mark = size("WiFi signal")

        for (dp in 120..420 step 20) {
            width = dp.dp
            compose.waitForIdle()
            assertEquals("battery at ${dp}dp", battery, size("Battery 49"))
            assertEquals("connection at ${dp}dp", mark, size("WiFi signal"))
        }
    }

    private fun busy() = SystemMetricsSnapshot(
        cpu = MetricSample(fraction = 0.9f, display = "3.2"),
        gpu = MetricSample(fraction = 0.8f, display = "1200"),
        ram = MetricSample(fraction = 0.7f, display = "15.9"),
        power = MetricSample(fraction = 0.6f, display = "12.3", estimated = true),
        temp = MetricSample(fraction = 0.5f, display = "100", rawCelsius = 100f),
    )
}
