package com.twinlore.app.data.metrics

import kotlin.math.abs

/**
 * One reading of one metric.
 *
 * [fraction] is null — not zero — when the metric could not be read, so an
 * unreadable meter never looks like a genuine zero. [display] is the value
 * WITHOUT its unit (the unit comes from [PerfMetric]); [rawCelsius] carries the
 * numeric temperature so the UI can pick a thermal band.
 */
data class MetricSample(
    /** 0..1 for the LED fill, or null when unavailable. */
    val fraction: Float?,
    /** Pre-formatted value (no unit), or [MetricMath.UNAVAILABLE]. */
    val display: String,
    /** True when this is a proxy rather than a direct measurement. */
    val estimated: Boolean = false,
    /** The raw °C for TEMP samples (drives the thermal-band colour); null otherwise. */
    val rawCelsius: Float? = null,
) {
    val available: Boolean get() = fraction != null

    companion object {
        val UNAVAILABLE = MetricSample(null, MetricMath.UNAVAILABLE)
    }
}

/** One poll of every metric. */
data class SystemMetricsSnapshot(
    val cpu: MetricSample = MetricSample.UNAVAILABLE,
    val gpu: MetricSample = MetricSample.UNAVAILABLE,
    val ram: MetricSample = MetricSample.UNAVAILABLE,
    val power: MetricSample = MetricSample.UNAVAILABLE,
    val temp: MetricSample = MetricSample.UNAVAILABLE,
) {
    operator fun get(metric: PerfMetric): MetricSample = when (metric) {
        PerfMetric.CPU -> cpu
        PerfMetric.GPU -> gpu
        PerfMetric.RAM -> ram
        PerfMetric.POWER -> power
        PerfMetric.TEMP -> temp
    }
}

/**
 * Turns raw system readings into displayable values. Pure and Android-free so the
 * device-dependent parts — thermal units, current sign — can be unit-tested
 * without a device.
 *
 * The perf meters show real values rather than percentages, so each function
 * returns both a [MetricSample.fraction] (how full the LED bar is) and a
 * [MetricSample.display] (the actual number: GHz, MHz, GB, W, °C).
 */
object MetricMath {

    const val UNAVAILABLE = "n/a"

    /** Full-scale for the power LED. A handheld SoC under load lands well inside this. */
    const val POWER_CEILING_W = 15f

    /** Outside this band a thermal reading is a unit mistake, not a temperature. */
    const val TEMP_MIN_PLAUSIBLE_C = -20f
    const val TEMP_MAX_PLAUSIBLE_C = 150f

    /** LED fill maps this °C span onto 0..1: cool at [TEMP_LED_MIN], full at [TEMP_LED_MAX]. */
    const val TEMP_LED_MIN = 45f
    const val TEMP_LED_MAX = 95f

    // ---- CPU clock -------------------------------------------------------

    /**
     * Average current core clock in GHz, with the fill as current-over-max.
     *
     * Frequencies rather than `/proc/stat` load: the sysfs `cpufreq` nodes stay
     * readable to an app where `/proc/stat` is SELinux-blocked, and a clock is
     * what the meter is labelled with. Offline cores expose the directory but not
     * the values, so empty lists yield "unavailable".
     */
    fun cpuClock(currentKHz: List<Long>, maxKHz: List<Long>): MetricSample {
        val curs = currentKHz.filter { it > 0L }
        val maxes = maxKHz.filter { it > 0L }
        if (curs.isEmpty() || maxes.isEmpty()) return MetricSample.UNAVAILABLE
        val meanCur = curs.average()
        val meanMax = maxes.average()
        if (meanMax <= 0.0) return MetricSample.UNAVAILABLE
        val ghz = meanCur / 1_000_000.0
        val fraction = (meanCur / meanMax).coerceIn(0.0, 1.0).toFloat()
        return MetricSample(fraction, "%.1f".format(java.util.Locale.US, ghz))
    }

    // ---- GPU clock -------------------------------------------------------

    /** Current GPU clock in MHz, fill as current-over-max. Both in Hz. */
    fun gpuClock(currentHz: Long?, maxHz: Long?): MetricSample {
        if (currentHz == null || currentHz <= 0L) return MetricSample.UNAVAILABLE
        val mhz = currentHz / 1_000_000.0
        val fraction = if (maxHz != null && maxHz > 0L) {
            (currentHz.toDouble() / maxHz).coerceIn(0.0, 1.0).toFloat()
        } else {
            // No max exposed: still show the value, fill against a generous ceiling.
            (mhz / 1_200.0).coerceIn(0.0, 1.0).toFloat()
        }
        return MetricSample(fraction, "%.0f".format(java.util.Locale.US, mhz))
    }

    // ---- RAM -------------------------------------------------------------

    /** Memory in use, in GB, with the fill as used-over-total. */
    fun ramUsed(totalBytes: Long, availableBytes: Long): MetricSample {
        if (totalBytes <= 0L || availableBytes < 0L) return MetricSample.UNAVAILABLE
        val used = (totalBytes - availableBytes).coerceIn(0L, totalBytes)
        val gb = used.toDouble() / 1_000_000_000.0
        val fraction = (used.toFloat() / totalBytes).coerceIn(0f, 1f)
        return MetricSample(fraction, "%.1f".format(java.util.Locale.US, gb))
    }

    // ---- power -----------------------------------------------------------

    /**
     * Battery power in watts, **signed** — negative while discharging, positive
     * while charging, matching how the value reads on the device. The LED fill is
     * the magnitude over [POWER_CEILING_W]. An implausible magnitude (the classic
     * milliamp-reporting OEM) is rejected rather than shown.
     */
    fun powerDraw(currentMicroAmps: Long, voltageMilliVolts: Int): MetricSample {
        if (voltageMilliVolts <= 0) return MetricSample.UNAVAILABLE
        val amps = currentMicroAmps / 1_000_000f
        val volts = voltageMilliVolts / 1_000f
        val watts = amps * volts
        if (watts.isNaN() || abs(watts) > 100f) return MetricSample.UNAVAILABLE
        val display = if (abs(watts) >= 10f) "%.0f".format(java.util.Locale.US, watts) else "%.1f".format(java.util.Locale.US, watts)
        return MetricSample(
            fraction = (abs(watts) / POWER_CEILING_W).coerceIn(0f, 1f),
            display = display,
        )
    }

    // ---- temperature -----------------------------------------------------

    /**
     * Normalises a thermal-zone reading to °C. Zones report millidegrees on most
     * devices and decidegrees on some, with no way to ask which; magnitude is the
     * only signal. Anything still outside a plausible band is treated as
     * unreadable rather than shown.
     */
    fun temperatureFromThermalZone(raw: Long?): MetricSample {
        if (raw == null) return MetricSample.UNAVAILABLE
        val celsius = when {
            abs(raw) >= 1_000L -> raw / 1_000f
            abs(raw) >= 100L -> raw / 10f
            else -> raw.toFloat()
        }
        return temperature(celsius, estimated = false)
    }

    /** Battery temperature, reported in tenths of a degree by the platform. */
    fun temperatureFromBattery(tenthsOfCelsius: Int): MetricSample =
        temperature(tenthsOfCelsius / 10f, estimated = true)

    private fun temperature(celsius: Float, estimated: Boolean): MetricSample {
        if (celsius.isNaN() ||
            celsius < TEMP_MIN_PLAUSIBLE_C ||
            celsius > TEMP_MAX_PLAUSIBLE_C
        ) {
            return MetricSample.UNAVAILABLE
        }
        val fraction = ((celsius - TEMP_LED_MIN) / (TEMP_LED_MAX - TEMP_LED_MIN)).coerceIn(0f, 1f)
        return MetricSample(
            fraction = fraction,
            display = "%.0f".format(java.util.Locale.US, celsius),
            estimated = estimated,
            rawCelsius = celsius,
        )
    }

    /**
     * Does a thermal zone's `type` name a CPU or SoC sensor? A device exposes
     * dozens of zones — battery, charger, modem, skin — and matching by name is
     * the only way to find the die. Qualcomm parts (the AYN Thor included) use
     * `tsens`.
     */
    fun isCpuThermalZone(type: String?): Boolean {
        val name = type?.trim()?.lowercase() ?: return false
        if (name.contains("battery") || name.contains("charger")) return false
        return SOC_ZONE_HINTS.any { name.contains(it) }
    }

    private val SOC_ZONE_HINTS = listOf("cpu", "soc", "tsens", "apc", "gold", "silver", "bigcore")
}
