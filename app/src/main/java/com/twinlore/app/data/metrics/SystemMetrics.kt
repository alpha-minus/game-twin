package com.twinlore.app.data.metrics

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Polls the device for the numbers the Performance status-bar meters draw: CPU clock,
 * GPU clock, RAM in use, power draw and temperature.
 *
 * Every read is a documented platform API or a sysfs file; the sysfs reads are
 * cheap but are file I/O, so the whole poll runs on [Dispatchers.IO], never on
 * the frame thread. All arithmetic lives in [MetricMath]; this class only
 * fetches.
 *
 * **Started only while a status cluster is showing meters** (see
 * [StatusBarMetricsController]) — a 1 Hz poll behind hidden chrome is exactly
 * the battery cost the meters exist to reveal.
 *
 * Two things ask for meters and neither knows about the other: the app's status
 * cluster and the fold panel's top bar, which is up precisely when the activity
 * is not. So [start] and [stop] are reference-counted per owner, and the poll
 * runs on this object's own scope rather than a caller's. Without the count the
 * panel closing would stop the activity's meters; without the private scope the
 * activity dying would stop the panel's.
 */
@Singleton
class SystemMetrics @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val _snapshot = MutableStateFlow(SystemMetricsSnapshot())
    val snapshot: StateFlow<SystemMetricsSnapshot> = _snapshot.asStateFlow()

    private var job: Job? = null

    /**
     * Who currently wants meters. The poll runs while this is non-empty.
     * Identity-based, so an owner asking twice still counts once.
     */
    private val owners = mutableSetOf<Any>()

    /**
     * The poll's own scope. It outlives every caller by design: the activity and
     * the panel take turns being alive, and a job parented to whichever asked
     * first would die when that one did.
     */
    private val pollScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var thermalZone: File? = null
    private var thermalZoneResolved = false

    private var gpuCurFile: File? = null
    private var gpuMaxFile: File? = null
    private var gpuResolved = false

    @Synchronized
    fun start(owner: Any, intervalMs: Long = 1_000L) {
        owners += owner
        if (job?.isActive == true) return
        job = pollScope.launch {
            while (isActive) {
                _snapshot.value = sample()
                delay(intervalMs)
            }
        }
    }

    /** Whether the poll is running. The reference count's only observable. */
    @get:androidx.annotation.VisibleForTesting
    val isPolling: Boolean get() = job?.isActive == true

    /** A no-op for an owner that never started, so unbalanced calls are safe. */
    @Synchronized
    fun stop(owner: Any) {
        if (!owners.remove(owner)) return
        if (owners.isNotEmpty()) return
        job?.cancel()
        job = null
    }

    private fun sample() = SystemMetricsSnapshot(
        cpu = readCpuClock(),
        gpu = readGpuClock(),
        ram = readRam(),
        power = readPower(),
        temp = readTemperature(),
    )

    // ---- CPU clock -------------------------------------------------------

    private fun readCpuClock(): MetricSample {
        val root = File("/sys/devices/system/cpu")
        val cores = root.listFiles { f -> f.isDirectory && f.name.matches(CORE_DIR) }
            ?.sortedBy { it.name }
            ?: return MetricSample.UNAVAILABLE

        val current = mutableListOf<Long>()
        val maximum = mutableListOf<Long>()
        cores.forEach { core ->
            val cur = readLineOrNull("${core.path}/cpufreq/scaling_cur_freq")?.toLongOrNull()
            val max = readLineOrNull("${core.path}/cpufreq/cpuinfo_max_freq")?.toLongOrNull()
            if (cur != null && max != null) {
                current += cur
                maximum += max
            }
        }
        return MetricMath.cpuClock(current, maximum)
    }

    // ---- GPU clock -------------------------------------------------------

    /**
     * Adreno exposes its clock through kgsl. The current node is in Hz on the
     * devfreq path; the older `gpuclk` node is also Hz. Resolved once (including
     * the failure) because the node set never changes at runtime.
     */
    private fun readGpuClock(): MetricSample {
        resolveGpu()
        val cur = gpuCurFile?.let { readLineOrNull(it.path)?.trim()?.toLongOrNull() }
        val max = gpuMaxFile?.let { readLineOrNull(it.path)?.trim()?.toLongOrNull() }
        return MetricMath.gpuClock(cur, max)
    }

    private fun resolveGpu() {
        if (gpuResolved) return
        gpuResolved = true
        val candidates = listOf(
            File("/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq") to File("/sys/class/kgsl/kgsl-3d0/devfreq/max_freq"),
            File("/sys/class/kgsl/kgsl-3d0/gpuclk") to File("/sys/class/kgsl/kgsl-3d0/max_gpuclk"),
            File("/sys/kernel/gpu/gpu_clock") to File("/sys/kernel/gpu/gpu_max_clock"),
        )
        val chosen = candidates.firstOrNull { (cur, _) -> cur.canRead() }
        gpuCurFile = chosen?.first
        gpuMaxFile = chosen?.second?.takeIf { it.canRead() }
    }

    // ---- RAM -------------------------------------------------------------

    private fun readRam(): MetricSample {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return MetricSample.UNAVAILABLE
        val info = ActivityManager.MemoryInfo()
        return runCatching {
            manager.getMemoryInfo(info)
            MetricMath.ramUsed(info.totalMem, info.availMem)
        }.getOrDefault(MetricSample.UNAVAILABLE)
    }

    // ---- power -----------------------------------------------------------

    private fun readPower(): MetricSample {
        val manager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            ?: return MetricSample.UNAVAILABLE
        val microAmps = runCatching {
            manager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        }.getOrNull() ?: return MetricSample.UNAVAILABLE
        // Long.MIN_VALUE is what the platform returns for "not supported".
        if (microAmps == Long.MIN_VALUE || microAmps == 0L) return MetricSample.UNAVAILABLE

        val voltage = stickyBatteryIntent()
            ?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
            ?: -1
        return MetricMath.powerDraw(microAmps, voltage)
    }

    // ---- temperature -----------------------------------------------------

    private fun readTemperature(): MetricSample {
        resolveThermalZone()
        thermalZone?.let { zone ->
            val sample = MetricMath.temperatureFromThermalZone(
                readLineOrNull(zone.path)?.trim()?.toLongOrNull()
            )
            if (sample.available) return sample
        }
        val tenths = stickyBatteryIntent()?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        if (tenths <= 0) return MetricSample.UNAVAILABLE
        return MetricMath.temperatureFromBattery(tenths)
    }

    private fun resolveThermalZone() {
        if (thermalZoneResolved) return
        thermalZoneResolved = true
        val zones = File("/sys/class/thermal").listFiles { f -> f.name.startsWith("thermal_zone") }
            ?.sortedBy { it.name }
            ?: return
        thermalZone = zones.firstNotNullOfOrNull { zone ->
            val type = readLineOrNull("${zone.path}/type")
            val temp = File(zone, "temp")
            if (MetricMath.isCpuThermalZone(type) && temp.canRead()) temp else null
        }
    }

    // ---- plumbing --------------------------------------------------------

    private fun stickyBatteryIntent(): Intent? = runCatching {
        context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }.getOrNull()

    private fun readLineOrNull(path: String): String? = runCatching {
        File(path).bufferedReader().use { it.readLine() }
    }.getOrNull()

    private companion object {
        val CORE_DIR = Regex("cpu[0-9]+")
    }
}
