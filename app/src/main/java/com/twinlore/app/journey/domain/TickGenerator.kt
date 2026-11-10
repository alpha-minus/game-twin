package com.twinlore.app.journey.domain

import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

data class Tick(val tMillis: Long, val label: String)

object TickGenerator {
    private const val TICK_TARGET = 11.0
    private const val MIN = 60_000L
    private const val HR = 3_600_000L
    private const val D = 86_400_000L
    private val MO = (30.44 * D).toLong()
    private val Y = (365.25 * D).toLong()

    private val dayFmt = DateTimeFormatter.ofPattern("MMM d", Locale.US)
    // "Sep '25", never "Sep 25": the bare two-digit year read exactly like a day of the month,
    // so a year-long axis looked like it was counting "Sep 25, Oct 25, Nov 25" while the corner
    // readout (correctly) named the first of each month. The apostrophe is the disambiguator.
    private val monthYearFmt = DateTimeFormatter.ofPattern("MMM ''yy", Locale.US)
    private val monthFmt = DateTimeFormatter.ofPattern("MMM", Locale.US)
    private val dateFmt = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm", Locale.US)

    private fun utc(millis: Long): ZonedDateTime =
        Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC)

    private data class Cand(val kind: String, val n: Int)
    private data class ScoredCand(val cand: Cand, val score: Double)

    fun timeTicks(t0: Long, t1: Long): List<Tick> {
        val span = (t1 - t0).coerceAtLeast(1)
        val cands = buildList {
            // Sub-hour steps exist so that a short window (an hour of play, a single session)
            // still gets a populated axis instead of the one-or-zero ticks an hourly grid
            // anchored to midnight would produce.
            intArrayOf(5, 10, 15, 30).forEach { add(Cand("minute", it)) }
            intArrayOf(1, 2, 3, 4, 6, 12).forEach { add(Cand("hour", it)) }
            intArrayOf(1, 2, 3, 4, 5, 7, 10, 14).forEach { add(Cand("day", it)) }
            intArrayOf(1, 2, 3, 4, 6).forEach { add(Cand("month", it)) }
            intArrayOf(1, 2, 5, 10, 25).forEach { add(Cand("year", it)) }
        }

        // Score candidates using the scoring formula (threshold 9.5)
        val scored = cands.map { c ->
            val approx = when (c.kind) {
                "minute" -> c.n * MIN
                "hour" -> c.n * HR
                "day" -> c.n * D
                "month" -> c.n * MO
                else -> c.n * Y
            }
            val count = span.toDouble() / approx
            val score = (if (count >= 9.5) 0.0 else 100.0) + abs(count - TICK_TARGET)
            ScoredCand(c, score)
        }.sortedBy { it.score }

        val showYear = span > 330 * D
        // Whether the axis is allowed to talk about times of day at all: see [clockTicks].
        val withClock = showsTimeOfDay(span)

        // Try candidates in score order until one generates >= 10 ticks. When none can (a window
        // shorter than ten of even the finest step), fall back to whichever candidate produced the
        // MOST ticks - `scored` is sorted ascending by score, so walking it in order and only
        // replacing on a strictly larger count breaks ties toward the better-scoring candidate.
        var best: List<Tick> = emptyList()
        for (sc in scored) {
            val out = generateTimeTicksFor(sc.cand, t0, t1, showYear, withClock)
            if (out.size >= 10) {
                return out
            }
            if (out.size > best.size) best = out
        }
        return best
    }

    private fun generateTimeTicksFor(
        b: Cand,
        t0: Long,
        t1: Long,
        showYear: Boolean,
        withClock: Boolean,
    ): List<Tick> {
        val out = mutableListOf<Tick>()
        when (b.kind) {
            "minute" -> {
                val step = b.n * MIN
                var t = utc(t0).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                while (t < t0) t += step
                val stamps = mutableListOf<Long>()
                while (t <= t1) {
                    stamps += t
                    t += step
                }
                out += clockTicks(stamps, withClock)
            }
            "hour" -> {
                val step = b.n * HR
                val d0 = utc(t0)
                var t = d0.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                while (t < t0) t += step
                val stamps = mutableListOf<Long>()
                while (t <= t1) {
                    stamps += t
                    t += step
                }
                out += clockTicks(stamps, withClock)
            }
            "day" -> {
                val step = b.n * D
                val d0 = utc(t0)
                var t = d0.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                if (t < t0) t += D
                while (t <= t1) {
                    out += Tick(t, (if (showYear) monthYearFmt else dayFmt).format(utc(t)))
                    t += step
                }
            }
            "month" -> {
                val d0 = utc(t0)
                var y = d0.year
                var m = (d0.monthValue - 1) / b.n * b.n   // 0-based month floor
                for (guard in 0 until 400) {
                    val t = ZonedDateTime.of(y, m + 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)
                        .toInstant().toEpochMilli()
                    if (t > t1) break
                    // The year rides January alone. A multi-year axis used to stamp it on every
                    // month ("Mar '23 Apr '23 ..."), which at monthly density is wider than the
                    // slots the labels sit in - they collided into one unreadable run. The
                    // endpoint dates under the axis carry the full anchor either way.
                    if (t >= t0) {
                        val fmt = if (showYear && m == 0) monthYearFmt else monthFmt
                        out += Tick(t, fmt.format(utc(t)))
                    }
                    m += b.n
                    while (m > 11) { m -= 12; y++ }
                }
            }
            else -> {
                val d0 = utc(t0)
                var y = Math.ceil(d0.year.toDouble() / b.n).toInt() * b.n
                for (guard in 0 until 400) {
                    val t = ZonedDateTime.of(y, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)
                        .toInstant().toEpochMilli()
                    if (t > t1) break
                    if (t >= t0) out += Tick(t, y.toString())
                    y += b.n
                }
            }
        }
        return out
    }

    /**
     * Labels a sub-day grid: midnight names the day, and every tick between them names its hour.
     *
     * The intermediates used to be blank, which on a weekend-long journey left an axis of eleven
     * gridlines carrying two labels, both of them dates, and no way to tell what part of a day the
     * chart was over. A clock on each of them says it.
     *
     * The catch is that hours repeat: "06:00" on the Saturday and "06:00" on the Sunday are two
     * different places on the same axis. The rule that keeps them apart without crowding the axis:
     * exactly one dated clock, the first tick ("Jun 14 06:00"), which anchors a window that opens
     * mid-day; every later clock stays bare, because the midnight ticks - which a midnight-anchored
     * hour grid always lands on - announce each new day before its hours can repeat, and a reader
     * resolves a clock against the last date to its left. The old rule dated every repeat instead,
     * which past the first midnight put the wide form on nearly every tick and the labels overlapped
     * each other into noise (the very thing the rule existed to prevent).
     *
     * [withClock] is the whole of that rule's scope, and it is the same rule the date chip and the
     * exported strip follow ([showsTimeOfDay]). An hourly grid keeps winning the scoring loop well
     * past two days - up to about nine and a half - so a five-day journey drew a 12-hourly clock
     * along its axis while the readout above it sat on one date for 24 hours at a time. Two
     * answers to "what time is this chart about", from the same frame. Past the threshold the
     * intermediates go back to being blank and the axis says dates, which is what the readout says.
     */
    private fun clockTicks(stamps: List<Long>, withClock: Boolean): List<Tick> {
        if (!withClock) {
            return stamps.map { at ->
                val z = utc(at)
                Tick(at, if (z.hour == 0 && z.minute == 0) dayFmt.format(z) else "")
            }
        }
        return stamps.mapIndexed { i, at ->
            val z = utc(at)
            val label = when {
                z.hour == 0 && z.minute == 0 -> dayFmt.format(z)
                i == 0 -> "${dayFmt.format(z)} ${timeFmt.format(z)}"
                else -> timeFmt.format(z)
            }
            Tick(at, label)
        }
    }

    fun elapsedTicks(eMaxMillis: Long): List<Tick> {
        val steps = longArrayOf(
            MIN, 2 * MIN, 5 * MIN, 10 * MIN, 15 * MIN, 30 * MIN,
            HR, 2 * HR, 3 * HR, 4 * HR, 6 * HR, 12 * HR,
            D, 2 * D, 3 * D, 5 * D, 7 * D, 10 * D, 14 * D, 20 * D,
            MO, 2 * MO, 3 * MO, 4 * MO, 6 * MO, Y, 2 * Y, 5 * Y, 10 * Y,
        )

        // Score steps using the scoring formula (threshold 9.5)
        val scored = steps.map { s ->
            val count = eMaxMillis.toDouble() / s
            val score = (if (count >= 9.5) 0.0 else 100.0) + abs(count - TICK_TARGET)
            Pair(s, score)
        }.sortedBy { it.second }

        // Try steps in score order until one generates >= 10 ticks; otherwise keep the step that
        // produced the most ticks (ties resolve to the better score, since `scored` is ascending).
        // An elapsed axis near the 15-minute floor has no step that can reach ten.
        var best: List<Tick> = emptyList()
        for ((step, _) in scored) {
            val out = generateElapsedTicksFor(step, eMaxMillis)
            if (out.size >= 10) {
                return out
            }
            if (out.size > best.size) best = out
        }
        return best
    }

    private fun generateElapsedTicksFor(step: Long, eMaxMillis: Long): List<Tick> {
        val out = mutableListOf<Tick>()
        var v = step
        while (v <= (eMaxMillis * 1.001).toLong()) {
            out += Tick(v, fmtElapsed(v, step))
            v += step
        }
        return out
    }

    // Label unit derives from the STEP so adjacent ticks can never render the same text.
    private fun fmtElapsed(ms: Long, step: Long): String = when {
        step < HR -> "${(ms.toDouble() / MIN).roundToLong()}m"
        step < D -> "${trim1(ms.toDouble() / HR)}h"
        step < (MO * 0.9).toLong() ->
            if (step < (D * 1.5).toLong()) "${trim1(ms.toDouble() / D)}d"
            else "${(ms.toDouble() / D).roundToLong()}d"
        step < (Y * 0.9).toLong() -> "${(ms.toDouble() / MO).roundToLong()}mo"
        else -> if (step < (Y * 1.5).toLong()) "${trim1(ms.toDouble() / Y)}y"
                else "${(ms.toDouble() / Y).roundToLong()}y"
    }

    // "2.0" -> "2", "2.5" -> "2.5" (matches JS +x.toFixed(1) behavior)
    private fun trim1(v: Double): String {
        val r = (v * 10).roundToLong() / 10.0
        return if (r == Math.floor(r)) r.toLong().toString() else r.toString()
    }

    /**
     * Elapsed time for the playback overlay, where there is no tick step to derive a unit from:
     * the unit comes from the magnitude alone, so the readout stays short at every scale.
     */
    fun fmtElapsedApprox(ms: Long): String {
        val v = ms.coerceAtLeast(0L)
        return when {
            v < HR -> "${(v.toDouble() / MIN).roundToLong()}m"
            v < D -> "${trim1(v.toDouble() / HR)}h"
            v < MO -> "${(v.toDouble() / D).roundToLong()}d"
            v < Y -> "${(v.toDouble() / MO).roundToLong()}mo"
            else -> "${trim1(v.toDouble() / Y)}y"
        }
    }

    fun fmtDate(millis: Long): String = dateFmt.format(utc(millis))

    /**
     * The same date with the clock time after it, for a journey short enough that the date alone
     * would sit unchanged for the whole replay. UTC, exactly like every other label on the chart,
     * so the readout and the axis under it can never disagree by a timezone.
     */
    fun fmtDateTime(millis: Long): String = "${fmtDate(millis)} ${timeFmt.format(utc(millis))}"

    /**
     * Below this much axis, a journey is one or two sittings rather than a stretch of calendar:
     * every unlock lands on the same day or the one after, so a date-only readout would be frozen
     * for the whole replay while the chart moved under it.
     */
    const val TIME_OF_DAY_SPAN_MILLIS = 48L * 3_600_000L

    /** Whether an axis of [spanMillis] is short enough to want the clock time (see [fmtDateTime]). */
    fun showsTimeOfDay(spanMillis: Long): Boolean = spanMillis <= TIME_OF_DAY_SPAN_MILLIS

}
