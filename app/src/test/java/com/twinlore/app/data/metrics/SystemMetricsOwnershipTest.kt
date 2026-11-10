package com.twinlore.app.data.metrics

import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Who gets to stop the poll.
 *
 * Two things ask for meters and neither knows about the other: the app's status
 * cluster, owned by MainActivity's lifecycle, and the fold panel's top bar,
 * which is up precisely when that activity is not in front. Before the count,
 * either one closing stopped the other's meters - and the panel case is the
 * silent one, because nobody is looking at the app's bar to notice.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SystemMetricsOwnershipTest {

    private val metrics = SystemMetrics(RuntimeEnvironment.getApplication())
    private val activity = Any()
    private val panel = Any()

    @After
    fun stopPolling() {
        metrics.stop(activity)
        metrics.stop(panel)
    }

    @Test
    fun `the poll survives one of two owners letting go`() {
        metrics.start(activity)
        metrics.start(panel)
        assertTrue(metrics.isPolling)

        metrics.stop(panel)
        assertTrue("the activity still wants meters", metrics.isPolling)

        metrics.stop(activity)
        assertFalse("nobody wants meters now", metrics.isPolling)
    }

    // The same owner asking twice must not need two releases, or an owner that
    // re-asserts itself on every settings emission can never be stopped.
    @Test
    fun `an owner asking twice still only counts once`() {
        metrics.start(activity)
        metrics.start(activity)
        metrics.stop(activity)
        assertFalse(metrics.isPolling)
    }

    // The panel calls stop on close whether or not it ever started, and the
    // controller stops on any non-Performance mode including the first one.
    @Test
    fun `stopping an owner that never started leaves the poll alone`() {
        metrics.start(activity)
        metrics.stop(panel)
        assertTrue(metrics.isPolling)
    }

    @Test
    fun `a stopped poll can be started again`() {
        metrics.start(activity)
        metrics.stop(activity)
        metrics.start(activity)
        assertTrue(metrics.isPolling)
    }
}
