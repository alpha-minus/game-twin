package com.twinlore.app.data.metrics

/**
 * The device metrics the Performance status bar surfaces as vertical LED meters, each
 * showing a real value (not a percentage):
 *
 *  - CPU — average core clock, GHz
 *  - GPU — graphics clock, MHz
 *  - RAM — memory in use, GB
 *  - PWR — battery power draw, W (signed; negative = discharging)
 *  - TMP — temperature, °C
 *
 * [label] is drawn next to the meter, [unit] follows the value, [displayName] is
 * the long form for accessibility. Order here is the left-to-right meter order.
 */
enum class PerfMetric(val label: String, val unit: String, val displayName: String) {
    CPU("CPU", "GHz", "Processor clock"),
    GPU("GPU", "MHz", "Graphics clock"),
    RAM("RAM", "GB", "Memory used"),
    POWER("PWR", "W", "Power draw"),
    TEMP("TMP", "°", "Temperature"),
}
