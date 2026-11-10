package com.twinlore.app.data.metrics

import com.twinlore.app.data.SettingsRepository
import com.twinlore.app.data.StatusBarMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

@OptIn(ExperimentalCoroutinesApi::class)
class StatusBarMetricsControllerTest {

    @Test
    fun `performance mode starts the poller, other modes stop it`() = runTest {
        val mode = MutableStateFlow(StatusBarMode.STANDARD)
        val settingsRepository = mock<SettingsRepository> {
            on { statusBarMode } doReturn mode
        }
        val systemMetrics = mock<SystemMetrics>()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val controller = StatusBarMetricsController(settingsRepository, systemMetrics)

        controller.observe(scope)
        advanceUntilIdle()
        verify(systemMetrics, never()).start(any(), any())

        mode.value = StatusBarMode.PERFORMANCE
        advanceUntilIdle()
        verify(systemMetrics, times(1)).start(any(), any())

        mode.value = StatusBarMode.OFF
        advanceUntilIdle()
        // Stopped once on the initial STANDARD emission and again on OFF.
        verify(systemMetrics, times(2)).stop(any())

        scope.cancel()
    }

    @Test
    fun `observe is idempotent`() = runTest {
        val mode = MutableStateFlow(StatusBarMode.PERFORMANCE)
        val settingsRepository = mock<SettingsRepository> {
            on { statusBarMode } doReturn mode
        }
        val systemMetrics = mock<SystemMetrics>()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val controller = StatusBarMetricsController(settingsRepository, systemMetrics)

        controller.observe(scope)
        controller.observe(scope)
        advanceUntilIdle()
        verify(systemMetrics, times(1)).start(any(), any())

        scope.cancel()
    }
}
