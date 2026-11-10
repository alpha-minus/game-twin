package com.twinlore.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Wifi1Bar
import androidx.compose.material.icons.filled.Wifi2Bar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.twinlore.app.R
import com.twinlore.app.data.NetworkStatus

/** The connection mark's size in every bar. */
val STATUS_ICON_SIZE = 18.dp

/** The clock's size in every bar. */
val STATUS_CLOCK_SIZE = 13.sp

/**
 * The end of every status bar: connection, time, battery, in that order, drawn
 * the same way in the library, the vault session and the overlay panel, in
 * Standard and in Performance alike.
 *
 * One group so that nothing in it moves when the mode changes. Performance
 * only adds meters to the left of it. Before, the battery was a pill at the
 * end in Standard and an LED meter among the meters in Performance, so
 * switching modes slid the connection mark and the clock along the bar, and
 * the two bars did not even agree on the order.
 *
 * The battery is the meters' own LED column in every mode: one look for it
 * everywhere, and in Performance it sits beside the meters as one of them.
 */
@Composable
fun StatusReadouts(
    network: NetworkStatus,
    time: String,
    battery: BatteryReading?,
    color: Color,
    pixelFont: Boolean,
    modifier: Modifier = Modifier,
    /** The battery reading's size in the Pixel font; the overlay draws its bar larger. */
    pixelFontSize: TextUnit = PIXEL_FONT_SIZE,
) {
    // In the Pixel font the clock takes the meters' size: at 13sp it towered
    // over every other reading in the bar.
    val clockSize = if (pixelFont) pixelFontSize else STATUS_CLOCK_SIZE
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ConnectionMark(network, color, pixelFont)
        Text(
            text = time,
            color = color,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = clockSize,
                lineHeight = if (clockSize == STATUS_CLOCK_SIZE) TextUnit.Unspecified else clockSize,
            ),
            maxLines = 1,
            softWrap = false,
        )
        battery?.let { BatteryMeterReadout(it, color, pixelFont, pixelFontSize) }
    }
}

/**
 * Wi-Fi strength drawn solid over a faint full-strength track, so the missing
 * bars read as greyed out. With no connection the track is crossed out: a
 * loss has to be told apart from a weak signal at a glance.
 */
@Composable
fun ConnectionMark(
    network: NetworkStatus,
    color: Color,
    pixelFont: Boolean,
    size: Dp = STATUS_ICON_SIZE,
) {
    val level = wifiSignalLevel(network)
    val track = color.copy(alpha = 0.22f)
    val solid = color.copy(alpha = 0.75f)
    val description = when {
        !network.hasInternet -> "No connection"
        level < 0 -> "Connected"
        else -> "WiFi signal $level/4"
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .semantics { contentDescription = description },
    ) {
        if (pixelFont) {
            Image(
                painter = painterResource(R.drawable.ic_pixel_wifi),
                contentDescription = null,
                colorFilter = ColorFilter.tint(track),
                modifier = Modifier.size(size),
            )
        } else {
            Icon(Icons.Default.Wifi, contentDescription = null, tint = track, modifier = Modifier.size(size))
        }
        if (!network.hasInternet) {
            Canvas(Modifier.size(size)) {
                val inset = this.size.width * 0.14f
                drawLine(
                    color = solid,
                    start = Offset(inset, inset),
                    end = Offset(this.size.width - inset, this.size.height - inset),
                    strokeWidth = this.size.width * 0.11f,
                    cap = if (pixelFont) StrokeCap.Butt else StrokeCap.Round,
                )
            }
        } else {
            // Online but not on Wi-Fi (Ethernet, a phone's data) has no
            // strength to show, so it gets the full mark.
            val shown = if (level < 0) 4 else level
            if (shown > 0) {
                if (pixelFont) {
                    val res = when (shown) {
                        1 -> R.drawable.ic_pixel_wifi_1
                        2 -> R.drawable.ic_pixel_wifi_2
                        else -> R.drawable.ic_pixel_wifi
                    }
                    Image(
                        painter = painterResource(res),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(solid),
                        modifier = Modifier.size(size),
                    )
                } else {
                    val icon: ImageVector = when (shown) {
                        1 -> Icons.Default.Wifi1Bar
                        2 -> Icons.Default.Wifi2Bar
                        else -> Icons.Default.Wifi
                    }
                    Icon(icon, contentDescription = null, tint = solid, modifier = Modifier.size(size))
                }
            }
        }
    }
}

/**
 * Bars for the Wi-Fi mark, 0 to 4, or -1 when the default network is not
 * Wi-Fi with internet. An unreported signal reads as three bars.
 */
@Suppress("DEPRECATION")
internal fun wifiSignalLevel(status: NetworkStatus): Int = when {
    !status.isWifi || !status.hasInternet -> -1
    status.signalStrength == NetworkStatus.SIGNAL_UNKNOWN -> 3
    else -> android.net.wifi.WifiManager.calculateSignalLevel(status.signalStrength, 5)
}
