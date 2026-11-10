package com.twinlore.app.journey.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class TickGeneratorTest {
    private val D = 86_400_000L
    // 2026-06-15 00:00:00 UTC
    private val anchor = 1781481600000L

    private fun gridCount(spanDays: Double): Int =
        TickGenerator.timeTicks(anchor - (spanDays * D).toLong(), anchor).size

    @Test fun `gridline counts match reference table`() {
        assertEquals(13, gridCount(1.0))
        assertEquals(15, gridCount(7.0))
        assertEquals(11, gridCount(30.0))
        assertEquals(13, gridCount(90.0))
        assertEquals(13, gridCount(180.0))
        assertEquals(12, gridCount(365.0))
        assertEquals(12, gridCount(730.0))
        assertEquals(10, gridCount(365.25 * 5))
        assertEquals(10, gridCount(365.25 * 10))
        assertEquals(10, gridCount(365.25 * 20))
    }

    /**
     * No two labels may be ambiguous. Fully-qualified labels are unique outright; a bare clock
     * may repeat only when a date tick sits between the repeats, because the reader resolves a
     * clock against the last date to its left - which is exactly how the axis is built (midnights
     * always land on an hour grid, so a new day always announces itself before its hours repeat).
     * Bare MONTHS follow the same rule against the year since only January carries it now: "Jul"
     * may repeat across years because a "Jan 'yy" (or a plain year) always stands between the
     * repeats and is what the reader resolves the month against.
     */
    private fun assertLabelsDistinct(ticks: List<Tick>, ctx: String) {
        val labeled = ticks.filter { it.label.isNotBlank() }
        val unique = labeled.map { it.label }
            .filter { !hourLabel.matches(it) && !bareMonthLabel.matches(it) }
        assertEquals("$ctx: duplicate labels in $unique", unique.size, unique.distinct().size)
        labeled.forEachIndexed { i, tick ->
            val isClock = hourLabel.matches(tick.label)
            val isBareMonth = bareMonthLabel.matches(tick.label)
            if (!isClock && !isBareMonth) return@forEachIndexed
            val offset = labeled.drop(i + 1).indexOfFirst { it.label == tick.label }
            if (offset < 0) return@forEachIndexed
            val between = labeled.subList(i + 1, i + 1 + offset)
            val resolves: (Tick) -> Boolean = if (isClock) {
                { dateLabel.matches(it.label) || datedHourLabel.matches(it.label) }
            } else {
                { yearedLabel.matches(it.label) }
            }
            assertTrue(
                "$ctx: ${tick.label} repeats with nothing to resolve it between: ${labeled.map { it.label }}",
                between.any(resolves),
            )
        }
    }

    /** A month with no year on it, and the labels that carry a year for it to resolve against. */
    private val bareMonthLabel = Regex("""^[A-Z][a-z]{2}$""")
    private val yearedLabel = Regex("""^([A-Z][a-z]{2} '\d{2}|\d{4})$""")

    private fun assertInvariants(ticks: List<Tick>, ctx: String) {
        assertTrue("$ctx: expected >=10 ticks, got ${ticks.size}", ticks.size >= 10)
        assertLabelsDistinct(ticks, ctx)
    }

    /** Short windows can't reach ten ticks, but they must still render a usable axis. */
    private fun assertPopulated(ticks: List<Tick>, ctx: String) {
        assertTrue("$ctx: expected a non-empty axis", ticks.isNotEmpty())
        assertTrue("$ctx: expected >=2 ticks, got ${ticks.size}", ticks.size >= 2)
        assertLabelsDistinct(ticks, ctx)
    }

    @Test fun `invariants hold on table spans`() {
        listOf(1.0, 7.0, 30.0, 90.0, 180.0, 365.0, 730.0, 365.25 * 5, 365.25 * 10, 365.25 * 20).forEach {
            assertInvariants(TickGenerator.timeTicks(anchor - (it * D).toLong(), anchor), "span ${it}d")
        }
    }

    // Regression: the no-candidate-reaches-ten fallback used to pick the WORST-scoring candidate
    // (a 25-year step), which generates zero ticks for a session-length window.
    @Test fun `short calendar spans still render an axis`() {
        val HOUR = 3_600_000L
        listOf(1L, 3L, 12L).forEach { hours ->
            val ticks = TickGenerator.timeTicks(anchor - hours * HOUR, anchor)
            assertPopulated(ticks, "span ${hours}h")
        }
    }

    // --- sub-day axes name the hour as well as the day ---

    private val dateLabel = Regex("""^[A-Z][a-z]{2} \d{1,2}$""")
    private val hourLabel = Regex("""^\d{2}:\d{2}$""")
    private val datedHourLabel = Regex("""^[A-Z][a-z]{2} \d{1,2} \d{2}:\d{2}$""")

    /**
     * A two-day journey used to draw eleven gridlines carrying two labels, both dates, so nothing
     * on the axis said which part of a day the chart was over. Midnight still names the day; every
     * tick between them names its hour.
     */
    @Test fun `a short span labels its intermediate ticks with the clock`() {
        val ticks = TickGenerator.timeTicks(anchor - 2 * D, anchor)

        assertPopulated(ticks, "span 2d")
        assertTrue("no tick may be left blank on a sub-day grid", ticks.none { it.label.isBlank() })
        assertTrue(
            "midnight must still name the day, got ${ticks.map { it.label }}",
            ticks.any { dateLabel.matches(it.label) },
        )
        assertTrue(
            "the ticks between them must carry a clock, got ${ticks.map { it.label }}",
            ticks.any { hourLabel.matches(it.label) || datedHourLabel.matches(it.label) },
        )
    }

    /**
     * UI feedback: the old rule dated every repeated clock, so past the first midnight nearly
     * every tick carried a wide "Jun 15 06:00"-style label and the axis crowded itself illegible.
     * The rule now: exactly one dated clock - the first tick, which anchors the axis when the
     * window opens mid-day - and after that the midnight date ticks do the disambiguating, so
     * every later hour stays four characters wide.
     */
    @Test fun `a two day axis dates its first clock and lets midnights carry the days`() {
        val ticks = TickGenerator.timeTicks(anchor - 2 * D, anchor)
        val labels = ticks.map { it.label }

        assertTrue("fixture must have clock ticks", labels.any { hourLabel.matches(it) })
        // The first tick anchors the axis: a date, or a clock carrying its date.
        assertTrue(
            "the first tick must say its day: $labels",
            dateLabel.matches(labels.first()) || datedHourLabel.matches(labels.first()),
        )
        // Every other clock stays bare; the wide dated form appears at most once, up front.
        assertTrue(
            "only the first tick may carry the wide dated-clock form: $labels",
            labels.drop(1).none { datedHourLabel.matches(it) },
        )
        // The days between the clocks are announced by their midnights.
        assertTrue(
            "midnights must name their days: $labels",
            labels.count { dateLabel.matches(it) } >= 2,
        )
        assertLabelsDistinct(ticks, "span 2d")
    }

    /** Every sub-day span, not just the convenient one: the labels must never collide. */
    @Test fun `sub-day grids keep their labels distinct at every span`() {
        val HOUR = 3_600_000L
        listOf(2L, 6L, 12L, 20L, 30L, 40L, 48L, 60L, 72L).forEach { hours ->
            val ticks = TickGenerator.timeTicks(anchor - hours * HOUR, anchor)
            assertPopulated(ticks, "span ${hours}h")
            assertLabelsDistinct(ticks, "span ${hours}h")
        }
    }

    /**
     * And the clock appears exactly where the readout has one.
     *
     * An hourly grid keeps winning the scoring loop well past two days, so the axis used to be
     * labelled in hours for every journey up to about nine and a half days while the date chip and
     * the exported strip - which follow [TickGenerator.showsTimeOfDay]'s 48h rule - sat on one date
     * for 24 hours at a time. The two are one question, and this is the one answer.
     */
    @Test fun `an axis carries clock labels exactly when the readout does`() {
        val HOUR = 3_600_000L
        listOf(2L, 6L, 12L, 20L, 30L, 40L, 48L, 60L, 72L).forEach { hours ->
            val span = hours * HOUR
            val ticks = TickGenerator.timeTicks(anchor - span, anchor)
            val clocks = ticks.map { it.label }.filter { hourLabel.matches(it) || datedHourLabel.matches(it) }

            if (TickGenerator.showsTimeOfDay(span)) {
                assertTrue("span ${hours}h: the readout has a clock, so the axis must too", clocks.isNotEmpty())
            } else {
                assertTrue(
                    "span ${hours}h: the readout is date-only, so the axis must be too, got $clocks",
                    clocks.isEmpty(),
                )
            }
        }
    }

    /**
     * The case the disagreement was widest on. Five days is still won by an hour step (`day 1`
     * scores 106 against `hour 12`'s 1.0), so this is the axis that drew "12:00" under a chip
     * reading "Sep 1, 2026" for two thirds of the replay. Its intermediates go back to blank,
     * which is what they were before the clock landed and what the chip agrees with.
     */
    @Test fun `a five day span keeps its intermediates blank and its readout date-only`() {
        val span = 5 * D
        val ticks = TickGenerator.timeTicks(anchor - span, anchor)

        assertPopulated(ticks, "span 5d")
        assertFalse("the readout is date-only here", TickGenerator.showsTimeOfDay(span))
        assertTrue("a five-day axis is midnight-anchored, so it has intermediates", ticks.any { it.label.isBlank() })
        assertTrue(
            "every label it does carry names a day, got ${ticks.map { it.label }}",
            ticks.filter { it.label.isNotBlank() }.all { dateLabel.matches(it.label) },
        )
    }

    /**
     * A multi-year axis puts the year on January alone, in the unambiguous apostrophe form
     * ("Jan '25", never "Jan 25" - the bare two-digit year read exactly like a day of the month).
     * Every month used to carry it, and at monthly density "Mar '23 Apr '23 ..." is wider than
     * the slots the labels sit in - the run collided into unreadable text. The
     * endpoint dates under the axis carry the full anchor either way.
     */
    @Test fun `a year axis puts its year on January alone`() {
        val ticks = TickGenerator.timeTicks(anchor - 365 * D, anchor)
        val labeled = ticks.map { it.label }.filter { it.isNotBlank() }

        assertTrue("fixture must produce month labels: $labeled", labeled.isNotEmpty())
        val yeared = labeled.filter { Regex("""^[A-Z][a-z]{2} '\d{2}$""").matches(it) }
        val plain = labeled.filter { Regex("""^[A-Z][a-z]{2}$""").matches(it) }
        assertEquals(
            "every label is a month, with or without its year: $labeled",
            labeled.size, yeared.size + plain.size,
        )
        assertTrue("a year-long axis crosses a January and says so: $labeled", yeared.isNotEmpty())
        assertTrue(
            "the year rides January alone: $labeled",
            yeared.all { it.startsWith("Jan") },
        )
    }

    @Test fun `elapsed axis at the fifteen minute floor still renders`() {
        assertPopulated(TickGenerator.elapsedTicks(15 * 60 * 1000L), "elapsed 15m")
    }

    @Test fun `fuzz 500 random spans`() {
        val rnd = Random(42)
        val minSpan = 3_600_000.0
        val maxSpan = 25 * 365.25 * 86_400_000.0
        repeat(500) {
            // Log-uniform: linear sampling over 25 years never lands on the short spans at all.
            val spanMs = Math.exp(
                Math.log(minSpan) + rnd.nextDouble() * (Math.log(maxSpan) - Math.log(minSpan))
            ).toLong()
            val t0 = anchor - spanMs
            val ticks = TickGenerator.timeTicks(t0, anchor)
            // Below roughly 19h no candidate step can fit ten ticks in the window; a day or more
            // always can, so the full invariant still applies there.
            if (spanMs >= 86_400_000L) {
                assertInvariants(ticks, "fuzz span ${spanMs}ms")
            } else {
                assertPopulated(ticks, "fuzz span ${spanMs}ms")
            }
        }
    }

    @Test fun `elapsed one year is monthly`() {
        val labels = TickGenerator.elapsedTicks((365.25 * D).toLong()).map { it.label }
        assertEquals((1..12).map { "${it}mo" }, labels)
    }

    @Test fun `elapsed twelve years is yearly`() {
        val labels = TickGenerator.elapsedTicks((12 * 365.25 * D).toLong()).map { it.label }
        assertEquals((1..12).map { "${it}y" }, labels)
    }

    @Test fun `elapsed one day is bi-hourly`() {
        val labels = TickGenerator.elapsedTicks(D).map { it.label }
        assertEquals(listOf("2h", "4h", "6h", "8h", "10h", "12h", "14h", "16h", "18h", "20h", "22h", "24h"), labels)
    }

    @Test fun `elapsed hundred days is ten-daily`() {
        val labels = TickGenerator.elapsedTicks(100 * D).map { it.label }
        assertEquals((1..10).map { "${it * 10}d" }, labels)
    }

    // fmtElapsedApprox picks its unit from the magnitude alone (no step context), for the
    // playback date overlay in race mode.
    @Test fun `elapsed approx picks minutes below an hour`() {
        assertEquals("0m", TickGenerator.fmtElapsedApprox(0))
        assertEquals("25m", TickGenerator.fmtElapsedApprox(25 * 60_000L))
    }

    @Test fun `elapsed approx picks hours below a day`() {
        assertEquals("1.5h", TickGenerator.fmtElapsedApprox(90 * 60_000L))
        assertEquals("6h", TickGenerator.fmtElapsedApprox(6 * 3_600_000L))
    }

    @Test fun `elapsed approx picks days below a month`() {
        assertEquals("3d", TickGenerator.fmtElapsedApprox(3 * D))
        assertEquals("29d", TickGenerator.fmtElapsedApprox(29 * D))
    }

    @Test fun `elapsed approx picks months and years above that`() {
        assertEquals("4mo", TickGenerator.fmtElapsedApprox((4 * 30.44 * D).toLong()))
        assertEquals("2.5y", TickGenerator.fmtElapsedApprox((2.5 * 365.25 * D).toLong()))
        assertEquals("3y", TickGenerator.fmtElapsedApprox((3 * 365.25 * D).toLong()))
    }

    // The date readout the player's overview strip carries, and when it grows a clock.

    @Test fun `a date with a time is the date plus a UTC clock`() {
        // 2026-08-14 14:30:00 UTC
        assertEquals("Aug 14, 2026 14:30", TickGenerator.fmtDateTime(1_786_717_800_000L))
        assertEquals("Aug 14, 2026", TickGenerator.fmtDate(1_786_717_800_000L))
    }

    /** Midnight keeps its clock rather than dropping to a bare date halfway through a replay. */
    @Test fun `midnight still carries its clock`() {
        assertEquals("Aug 14, 2026 00:00", TickGenerator.fmtDateTime(1_786_665_600_000L))
    }

    /**
     * Two days exactly is still a short journey; a millisecond past it is not. The threshold is
     * inclusive because a journey whose axis is exactly 48 hours is one weekend, and a date-only
     * readout for it would show two values across the whole replay.
     */
    @Test fun `the time of day threshold is two days, inclusive`() {
        assertEquals(48 * 3_600_000L, TickGenerator.TIME_OF_DAY_SPAN_MILLIS)
        assertTrue(TickGenerator.showsTimeOfDay(48 * 3_600_000L))
        assertTrue(TickGenerator.showsTimeOfDay(48 * 3_600_000L - 1))
        assertFalse(TickGenerator.showsTimeOfDay(48 * 3_600_000L + 1))
    }

    @Test fun `a one session journey and a multi year one land either side of it`() {
        assertTrue(TickGenerator.showsTimeOfDay(0L))
        assertTrue(TickGenerator.showsTimeOfDay(90 * 60_000L))
        assertFalse(TickGenerator.showsTimeOfDay(30 * D))
        assertFalse(TickGenerator.showsTimeOfDay(1200 * D))
    }
}
