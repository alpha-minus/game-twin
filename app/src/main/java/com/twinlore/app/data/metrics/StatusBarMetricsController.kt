package com.twinlore.app.data.metrics

import com.twinlore.app.data.SettingsRepository
import com.twinlore.app.data.StatusBarMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sole owner of the [SystemMetrics] poll lifecycle: polls at 1 Hz while the global status
 * cluster is showing meters (Performance mode), stops otherwise.
 *
 * Wired from MainActivity with its lifecycleScope, so polling dies with the
 * activity and resumes on the next launch.
 */
@Singleton
class StatusBarMetricsController @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val systemMetrics: SystemMetrics,
) {
    private var job: Job? = null

    /** Idempotent while the previous scope is alive. */
    fun observe(scope: CoroutineScope) {
        if (job?.isActive == true) return
        lateinit var own: Job
        own = scope.launch {
            try {
                settingsRepository.statusBarMode
                    .map { it == StatusBarMode.PERFORMANCE }
                    .distinctUntilChanged()
                    .collect { wanted ->
                        if (wanted) systemMetrics.start(this@StatusBarMetricsController)
                        else systemMetrics.stop(this@StatusBarMetricsController)
                    }
            } finally {
                // Only if nobody has taken over. The activity is deliberately moved between
                // displays, which recreates it — so a newer observe() can be running by the time
                // this cancellation unwinds, and stopping then would leave the new owner polling
                // nothing with no way to notice.
                if (job === own) systemMetrics.stop(this@StatusBarMetricsController)
            }
        }
        job = own
    }
}
