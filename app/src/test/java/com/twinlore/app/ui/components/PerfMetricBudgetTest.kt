package com.twinlore.app.ui.components

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.twinlore.app.data.metrics.PerfMetric
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Which meters a bar can afford.
 *
 * The status cluster is read on two screens of very different widths: the
 * handheld's own 520dp bar and the 336dp second screen, both at 3.69x. Five
 * meters, a clock and a battery do not fit the narrow one, and what happened
 * before this rule existed was not a decision but an accident -- the row
 * overran, the clock was measured last and came out 0dp wide, and the reading
 * that vanished was picked by declaration order.
 *
 * So the cluster gives things up on purpose, in a fixed order. GPU first: it
 * is the one reading a player can lose and still know what the device is
 * doing, and the clock it makes room for is worth more.
 */
class PerfMetricBudgetTest {

    /** Every meter the same width, to keep the arithmetic in the test obvious. */
    private val ten: (PerfMetric) -> Dp = { 10.dp }

    @Test
    fun `a wide bar keeps every meter`() {
        assertEquals(
            PerfMetric.entries.toList(),
            metricsThatFit(available = 500.dp, widthOf = ten, gap = 2.dp),
        )
    }

    /** Five at 10dp plus four 2dp gaps is 58dp. One dp short of it drops one. */
    @Test
    fun `GPU is the first to go`() {
        assertEquals(
            listOf(PerfMetric.CPU, PerfMetric.RAM, PerfMetric.POWER, PerfMetric.TEMP),
            metricsThatFit(available = 57.dp, widthOf = ten, gap = 2.dp),
        )
    }

    @Test
    fun `power goes after GPU`() {
        assertEquals(
            listOf(PerfMetric.CPU, PerfMetric.RAM, PerfMetric.TEMP),
            metricsThatFit(available = 45.dp, widthOf = ten, gap = 2.dp),
        )
    }

    /**
     * CPU is not on the list of things to give up. A cluster with no readings
     * at all is not a cluster, and whatever is left has to say something.
     */
    @Test
    fun `CPU survives a bar too narrow for anything`() {
        assertEquals(
            listOf(PerfMetric.CPU),
            metricsThatFit(available = 1.dp, widthOf = ten, gap = 2.dp),
        )
    }

    /** The order on screen never changes, only which of them are on it. */
    @Test
    fun `what is left keeps the reading order`() {
        val shown = metricsThatFit(available = 34.dp, widthOf = ten, gap = 2.dp)
        assertEquals(shown.sortedBy { it.ordinal }, shown)
    }

    /** An unbounded bar is not a narrow one: nothing is given up. */
    @Test
    fun `an unbounded bar keeps every meter`() {
        assertEquals(
            PerfMetric.entries.toList(),
            metricsThatFit(available = Dp.Infinity, widthOf = ten, gap = 2.dp),
        )
    }
}
