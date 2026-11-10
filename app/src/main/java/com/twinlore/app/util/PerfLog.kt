package com.twinlore.app.util

import android.os.Process
import android.os.SystemClock
import android.util.Log

/**
 * Lightweight startup-tracing helper. Logs `+<ms-since-process-start>ms <phase>`
 * under the `STARTUP_PERF` tag so a single `adb logcat -s STARTUP_PERF` after a
 * cold launch reconstructs the timeline.
 *
 * Anchored to [Process.getStartElapsedRealtime] (API 24+) so timestamps capture
 * everything from process fork — including the work that runs before any of our
 * Kotlin code, like Hilt's generated module init in `Application.attachBaseContext`.
 */
object PerfLog {
    private const val TAG = "STARTUP_PERF"
    private val processStart = Process.getStartElapsedRealtime()
    private val pid = Process.myPid()

    fun mark(phase: String) {
        val now = SystemClock.elapsedRealtime() - processStart
        val ticks = readProcTicks()
        Log.d(TAG, "+${now}ms [ticks=$ticks] $phase")
    }

    /** utime + stime from /proc/<pid>/stat (fields 14+15, 1-indexed). Non-blocking. */
    private fun readProcTicks(): Long {
        return try {
            val line = java.io.File("/proc/$pid/stat").readText()
            val fields = line.substringAfter(") ").split(' ')
            fields[11].toLong() + fields[12].toLong()
        } catch (_: Exception) { -1L }
    }
}
