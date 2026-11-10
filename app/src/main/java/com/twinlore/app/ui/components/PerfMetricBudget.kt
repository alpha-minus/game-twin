package com.twinlore.app.ui.components

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.twinlore.app.data.metrics.PerfMetric

/**
 * The order the cluster gives meters up in, when the bar cannot hold them all.
 *
 * GPU first, because it is the reading a player can lose and still know what
 * the device is doing, and because the clock it makes room for is worth more
 * than a second frequency. Power next: on this handheld it often reads
 * unavailable anyway, so its slot is frequently a dash in a space wide enough
 * for a number. Then heat, then memory.
 *
 * CPU is not on the list. A cluster with nothing in it is not a cluster.
 */
private val SACRIFICE_ORDER = listOf(
    PerfMetric.GPU,
    PerfMetric.POWER,
    PerfMetric.TEMP,
    PerfMetric.RAM,
)

/**
 * The meters that fit in [available], in reading order.
 *
 * A decision rather than an accident. Before this existed the row simply
 * overran: the meters are measured before the clock in the bars that host
 * them, so the clock came out 0dp wide and disappeared, and which reading was
 * lost was decided by declaration order rather than by what a player needs.
 * Now the cluster gives things up on purpose, in [SACRIFICE_ORDER], and stops
 * as soon as what is left fits.
 *
 * [widthOf] is measured rather than written down, because the same meter is
 * drawn at different sizes by different bars, and in
 * whichever font the app is set to. [gap] is the space between two meters,
 * which is only spent between the ones that survive.
 */
fun metricsThatFit(
    available: Dp,
    widthOf: (PerfMetric) -> Dp,
    gap: Dp,
): List<PerfMetric> {
    val shown = PerfMetric.entries.toMutableList()
    for (victim in SACRIFICE_ORDER) {
        if (widthOfAll(shown, widthOf, gap) <= available) break
        shown.remove(victim)
    }
    return shown
}

/** What [metrics] need side by side, gaps included. */
private fun widthOfAll(
    metrics: List<PerfMetric>,
    widthOf: (PerfMetric) -> Dp,
    gap: Dp,
): Dp {
    if (metrics.isEmpty()) return 0.dp
    val meters = metrics.fold(0.dp) { total, metric -> total + widthOf(metric) }
    return meters + gap * (metrics.size - 1)
}
