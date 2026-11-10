package com.twinlore.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.twinlore.app.data.metrics.MetricSample
import com.twinlore.app.data.metrics.PerfMetric
import com.twinlore.app.data.metrics.SystemMetricsSnapshot
import com.twinlore.app.ui.LocalThemePalette
import com.twinlore.app.ui.theme.toFontFamily
import kotlin.math.roundToInt

/** Fixed per-metric colours. Temperature is decided by thermal band, not here. */
private val CPU_COLOR = Color(0xFFFF4FA3)   // pink
private val GPU_COLOR = Color(0xFF3B82F6)   // blue
private val RAM_COLOR = Color(0xFF22C55E)   // green
private val PWR_COLOR = Color(0xFFF59E0B)   // orange

private const val LED_STEPS = 5

/** Press Start 2P renders large for its point size; this is the app bar's size for it. */
val PIXEL_FONT_SIZE = 6.sp

/** Below this the battery meter turns red, the threshold the battery pill uses. */
private const val BATTERY_LOW_PERCENT = 15
private val BATTERY_LOW_COLOR = Color(0xFFE53935)

/** The LED column plus the space before its reading: what every meter adds to its text slot. */
private val METER_LEDS_WIDTH = 6.dp + 4.dp
private val BOLT_SIZE = 10.dp

/**
 * Charge left, for [PerfMeters] to draw as the cluster's last meter.
 * [percent] is 0..100.
 */
data class BatteryReading(val percent: Int, val charging: Boolean)

/** The space between two meters. Tighter without labels than it was with. */
private val METER_GAP = 10.dp
private val METER_GAP_PIXEL = 8.dp

/**
 * The device metrics for the status cluster, each a **5-step vertical LED
 * meter** with the reading beside it (e.g. `▮ 1.84GHz`). Colours are fixed per
 * metric — CPU pink, GPU blue, RAM green, PWR orange — and temperature is
 * coloured by thermal band (green → red) so a hot device reads at a glance.
 *
 * The colour is the only thing naming the metric on screen: the CPU/GPU/RAM
 * labels are gone, which is four characters each back in a bar that is 336dp
 * wide on the second screen. The unit tells the two frequencies apart, and the
 * name is still spoken to a screen reader, which cannot see a colour anyway.
 *
 * What is drawn depends on the width this is given: see [metricsThatFit].
 */
@Composable
fun PerfMeters(
    snapshot: SystemMetricsSnapshot,
    modifier: Modifier = Modifier,
    textColor: Color? = null,
    pixelFont: Boolean = false,
    /**
     * The meters' size in the Pixel font. Small by default for the app's bar,
     * which is packed edge to edge; the overlay's bar has the room to draw it
     * larger and is read at a glance over a game.
     */
    pixelFontSize: TextUnit = PIXEL_FONT_SIZE,
) {
    val style = rememberMeterStyle(pixelFont, pixelFontSize)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val slots = remember(style, density, measurer) {
        PerfMetric.entries.associateWith { metric ->
            val measured = measurer.measure(metric.widestReading(), style = style)
            with(density) { measured.size.width.toDp() }
        }
    }
    val slotOf: (PerfMetric) -> Dp = { slots.getValue(it) }
    val gap = if (pixelFont) METER_GAP_PIXEL else METER_GAP
    // What the bar can afford, decided against the width it actually has.
    // Unbounded means nobody is asking it to fit, so nothing is given up.
    BoxWithConstraints(modifier) {
        val shown = metricsThatFit(
            available = maxWidth,
            widthOf = { METER_LEDS_WIDTH + slotOf(it) },
            gap = gap,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            shown.forEach { metric ->
                val sample = snapshot[metric]
                Meter(metric, sample, colourFor(metric, sample), textColor, style, slotOf(metric))
            }
        }
    }
}

/**
 * The meters' text style: one style, used to measure and to draw.
 *
 * Press Start 2P renders large for its point size, so it is shrunk hard in the
 * tight status bar.
 */
@Composable
private fun rememberMeterStyle(pixelFont: Boolean, pixelFontSize: TextUnit): TextStyle {
    val context = androidx.compose.ui.platform.LocalContext.current
    val fontFamily = if (pixelFont) com.twinlore.app.data.AppFont.PIXEL.toFontFamily(context) else null
    val fontSize = if (pixelFont) pixelFontSize else 10.sp
    // One style, used to measure and to draw.
    //
    // These were two before, and the difference was a bug you could read on
    // the device: the slot was measured with no font family while the Text
    // inherited the app's, which on a monospace setting is wider, so "1.4GHz"
    // was drawn into a slot cut for a narrower font and lost its "z". The line
    // height matters just as much. A 10sp value inheriting a 24sp line height
    // is a 24dp tall thing in a bar that is otherwise 16dp, which is where the
    // Performance bar's extra 36dp of height came from.
    val base = LocalTextStyle.current
    val style = remember(base, fontSize, fontFamily) {
        base.merge(
            TextStyle(
                fontSize = fontSize,
                fontFamily = fontFamily,
                lineHeight = fontSize * 1.25f,
            )
        )
    }
    return style
}

/**
 * The battery as a meter: the same five LEDs and reading beside them as the
 * rest of the cluster, so it reads as one of them rather than a pill beside
 * them. Drawn in the text colour, not a colour of its own: the metric colours
 * are already pink, blue, green, orange and teal, and a sixth hue would only
 * compete with them. It goes red when low, as the pill does, and carries a bolt
 * while charging.
 */
@Composable
fun BatteryMeterReadout(
    battery: BatteryReading,
    textColor: Color? = null,
    pixelFont: Boolean = false,
    pixelFontSize: TextUnit = PIXEL_FONT_SIZE,
) {
    val style = rememberMeterStyle(pixelFont, pixelFontSize)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    // Wide enough for "100%" whatever the level, so a draining battery does
    // not shift what is beside it. The bolt is reserved only while charging:
    // plugging in is rare enough that one shift then is fine.
    val text = remember(style, density, measurer) {
        with(density) { measurer.measure("100%", style = style).size.width.toDp() }
    }
    BatteryMeter(battery, textColor, style, text + if (battery.charging) BOLT_SIZE else 0.dp)
}

@Composable
private fun BatteryMeter(
    battery: BatteryReading,
    textColor: Color?,
    style: TextStyle,
    slot: Dp,
) {
    val valueColor = textColor ?: LocalThemePalette.current.foreground
    val percent = battery.percent.coerceIn(0, 100)
    val low = percent < BATTERY_LOW_PERCENT && !battery.charging
    val colour = if (low) BATTERY_LOW_COLOR else valueColor
    val description = "Battery $percent percent" + if (battery.charging) ", charging" else ""
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.semantics { contentDescription = description },
    ) {
        VerticalLeds(fraction = percent / 100f, colour = colour, available = true)
        Spacer(Modifier.width(4.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.width(slot),
        ) {
            if (battery.charging) {
                Icon(
                    Icons.Filled.Bolt,
                    contentDescription = null,
                    tint = valueColor,
                    modifier = Modifier.width(BOLT_SIZE).height(BOLT_SIZE),
                )
            }
            Text(
                text = "$percent%",
                color = if (low) BATTERY_LOW_COLOR else valueColor,
                style = style,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@Composable
private fun Meter(
    metric: PerfMetric,
    sample: MetricSample,
    colour: Color,
    textColor: Color?,
    style: TextStyle,
    slot: Dp,
) {
    val palette = LocalThemePalette.current
    val valueColor = textColor ?: palette.foreground
    // The name is still said, just not drawn: the LED's colour is what names
    // the metric on screen now, and a screen reader cannot see a colour.
    val description = if (sample.available) {
        "${metric.displayName} ${sample.display}${metric.unit}"
    } else {
        "${metric.displayName} unavailable"
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.semantics { contentDescription = description },
    ) {
        VerticalLeds(fraction = sample.fraction, colour = colour, available = sample.available)
        Spacer(Modifier.width(4.dp))
        Text(
            text = buildAnnotatedString {
                if (sample.available) {
                    withStyle(SpanStyle(color = valueColor)) {
                        if (sample.estimated) append('~')
                        append(sample.display)
                        append(metric.unit)
                    }
                } else {
                    withStyle(SpanStyle(color = valueColor.copy(alpha = 0.5f))) { append("—") }
                }
            },
            style = style,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.width(slot),
        )
    }
}

/**
 * The widest reading a meter can ever show, label and unit included.
 *
 * The label is not in it any more: the LED's colour names the metric and the
 * unit tells two frequencies apart, which buys back the four characters a
 * 336dp bar could not spare.
 *
 * A tilde on every one because [MetricSample.estimated] prefixes it and any
 * metric may be a proxy rather than a direct measurement. Erring wide costs a
 * few dp of blank; erring narrow puts the jitter straight back.
 */
private fun PerfMetric.widestReading(): String = when (this) {
    PerfMetric.CPU -> "~9.9"
    PerfMetric.GPU -> "~9999"
    PerfMetric.RAM -> "~99.9"
    PerfMetric.POWER -> "~99.9"
    // Two digits: a handheld shuts itself down long before 100 degrees, and
    // the third was a character of blank after every reading.
    PerfMetric.TEMP -> "~99"
} + unit

/** Five stacked segments, lit from the bottom in proportion to [fraction]. */
@Composable
private fun VerticalLeds(fraction: Float?, colour: Color, available: Boolean) {
    Canvas(
        Modifier
            .width(6.dp)
            .height(16.dp)
    ) {
        val lit = if (fraction == null) 0 else (fraction * LED_STEPS).roundToInt().coerceIn(0, LED_STEPS)
        val gap = size.height * 0.10f / (LED_STEPS - 1)
        val segH = (size.height - gap * (LED_STEPS - 1)) / LED_STEPS
        for (i in 0 until LED_STEPS) {
            // Bottom segment is index 0.
            val top = size.height - (i + 1) * segH - i * gap
            val on = i < lit
            val segColour = when {
                on -> colour
                available -> colour.copy(alpha = 0.20f)
                else -> colour.copy(alpha = 0.10f)
            }
            drawRoundRect(
                color = segColour,
                topLeft = Offset(0f, top),
                size = Size(size.width, segH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.5f, 1.5f),
            )
        }
    }
}

private fun colourFor(metric: PerfMetric, sample: MetricSample): Color = when (metric) {
    PerfMetric.CPU -> CPU_COLOR
    PerfMetric.GPU -> GPU_COLOR
    PerfMetric.RAM -> RAM_COLOR
    PerfMetric.POWER -> PWR_COLOR
    PerfMetric.TEMP -> thermalColour(sample.rawCelsius)
}

/**
 * Thermal-band colour:
 *  - < 65 °C optimal — green
 *  - 65–75 normal — yellow
 *  - 75–85 high — orange
 *  - 85–95 damaging over time — red
 *  - ≥ 95 critical — deep red
 */
private fun thermalColour(celsius: Float?): Color = when {
    celsius == null -> Color(0xFF9CA3AF)
    // Cool band is teal (not green) so TMP never clashes with the RAM meter.
    celsius < 65f -> Color(0xFF2DD4BF)
    celsius < 75f -> Color(0xFFEAB308)
    celsius < 85f -> Color(0xFFF97316)
    celsius < 95f -> Color(0xFFEF4444)
    else -> Color(0xFFB91C1C)
}
