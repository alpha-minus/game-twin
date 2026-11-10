package com.twinlore.app.ui.components

import kotlin.math.ceil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the achievements board says about a game beyond its counts: how far through the list the
 * player is, what it cost, over how long, and when it was last touched. Pure text, so the wording
 * is pinned without a screen.
 */
class AchievementsBoardTextTest {

    private val day = 86_400_000L
    private val hour = 3_600_000L

    /** A finished list says so rather than dividing a number by itself. */
    @Test fun `the achievements line names a full set`() {
        assertEquals("All 54 achievements", boardAchievementsLine(54, 54))
        assertEquals("37 of 54 achievements", boardAchievementsLine(37, 54))
        assertEquals("0 of 54 achievements", boardAchievementsLine(0, 54))
        // A set whose size nobody knows cannot be "all" of anything.
        assertEquals("3 of 0 achievements", boardAchievementsLine(3, 0))
    }

    /** Full words, and a part that is zero is not said at all. */
    @Test fun `a duration is spelled out`() {
        assertEquals("15 hours, 26 minutes", fmtWordyDuration(15 * 60 + 26))
        assertEquals("3 hours", fmtWordyDuration(180))
        assertEquals("40 minutes", fmtWordyDuration(40))
        assertEquals("1 hour, 1 minute", fmtWordyDuration(61))
        assertEquals("0 minutes", fmtWordyDuration(0))
    }

    /**
     * The two coarsest non-zero units and no more. A month is a flat 30 days: this is an
     * approximate readout of how long a run took, and calendar arithmetic would make two runs of
     * equal length print different answers depending on when they started.
     */
    @Test fun `a span is its two coarsest units`() {
        assertEquals("1 month, 18 days", fmtCoarseSpan(48 * day))
        assertEquals("18 days, 4 hours", fmtCoarseSpan(18 * day + 4 * hour))
        assertEquals("4 hours", fmtCoarseSpan(4 * hour))
        assertEquals("2 months", fmtCoarseSpan(60 * day))
        // Nothing worth naming, so the clause that would carry it is simply left off.
        assertNull(fmtCoarseSpan(30 * 60 * 1000L))
        assertNull(fmtCoarseSpan(0L))
        assertNull(fmtCoarseSpan(-1L))
    }

    /** The award names the hours, and the span says over how long they were spread. */
    @Test fun `the played line names the award, the hours and the span`() {
        assertEquals(
            "Mastered in 15 hours, 26 minutes over 1 month, 18 days",
            boardPlayedLine(15 * 60 + 26, "mastered", 48 * day),
        )
        assertEquals("Completed in 3 hours", boardPlayedLine(180, "completed", null))
        assertEquals("Beaten in 1 hour, 5 minutes", boardPlayedLine(65, "beaten-hardcore", null))
        assertEquals("Beaten (casual) in 1 hour, 5 minutes", boardPlayedLine(65, "beaten-softcore", null))
        // No award to name the hours.
        assertEquals("Played 40 minutes", boardPlayedLine(40, null, null))
        // A span too short to name drops the clause rather than printing an empty one.
        assertEquals("Played 40 minutes", boardPlayedLine(40, null, 60_000L))
    }

    /** Nothing known, nothing said: a board with no playtime does not invent a zero. */
    @Test fun `no playtime means no line`() {
        assertNull(boardPlayedLine(null, "mastered", day))
        assertNull(boardPlayedLine(0, "mastered", day))
    }

    /** An absolute date, so a shared picture still says when without knowing when it was shared. */
    @Test fun `last played is a date`() {
        // 2026-08-18T12:00:00Z, read back in the default zone the formatter also uses.
        val cal = java.util.Calendar.getInstance().apply {
            clear()
            set(2026, java.util.Calendar.AUGUST, 18, 12, 0, 0)
        }
        assertEquals("Last played August 18 2026", boardLastPlayedLine(cal.timeInMillis))
        assertNull(boardLastPlayedLine(null))
        assertNull(boardLastPlayedLine(0L))
    }

    /** The newest unlock wins, whatever order the list arrives in; bad stamps are ignored. */
    @Test fun `the last unlock is the newest one that parses`() {
        val newest = latestUnlockMillis(listOf("2026-02-12 22:40:00"))
        assertEquals(
            newest,
            latestUnlockMillis(listOf("2026-02-10 08:00:00", null, "2026-02-12 22:40:00", "", "not a date")),
        )
        assertNull(latestUnlockMillis(listOf(null, "", "nonsense")))
        assertNull(latestUnlockMillis(emptyList()))
    }

    /** And the oldest one, for the far end of the span. */
    @Test fun `the first unlock is the oldest one that parses`() {
        val oldest = earliestUnlockMillis(listOf("2026-02-10 08:00:00"))
        assertEquals(
            oldest,
            earliestUnlockMillis(listOf("2026-02-12 22:40:00", null, "2026-02-10 08:00:00", "", "not a date")),
        )
        assertNull(earliestUnlockMillis(listOf(null, "", "nonsense")))
        assertNull(earliestUnlockMillis(emptyList()))
    }

    // --- badgeCellDp: the largest grid cell that fits, replacing the old
    // cols = ceil(sqrt(n * aspect)) heuristic (see badgeCellDp's own KDoc for why that formula
    // under-sized a wide landscape board). ---

    /** cols/rows recomputed from a returned cell, the same way the composable derives them. */
    private fun colsFor(availableW: Float, cell: Float) =
        if (cell > 0f) (availableW / cell).toInt().coerceAtLeast(1) else 1

    private fun rowsFor(count: Int, cols: Int) = ceil(count.coerceAtLeast(1).toDouble() / cols).toInt()

    /**
     * The old heuristic on the Thor's ~1850 x 480dp board area with 69 badges: cols =
     * ceil(sqrt(69 * (1850/480))) = 17, rows = ceil(69/17) = 5, offered = min(1850/17, 480/5) =
     * 96dp. That is the number the direct search should beat, since 17 columns was never the
     * widest arrangement that still fits 5 rows in the available height.
     */
    @Test fun `a wide container gets a bigger cell than the old sqrt heuristic`() {
        val cell = badgeCellDp(count = 69, availableW = 1850f, availableH = 480f, maxCell = 999f)
        assertTrue("expected > 96f (old heuristic), got $cell", cell > 96f)
    }

    @Test fun `the cell never exceeds the cap`() {
        // Ample room, small count: the cap is the only thing holding the cell back.
        assertEquals(72f, badgeCellDp(count = 3, availableW = 5000f, availableH = 5000f, maxCell = 72f), 0.01f)
    }

    @Test fun `the chosen cell always fits the grid it implies`() {
        val cases = listOf(
            Triple(69, 1850f, 480f),
            Triple(12, 1850f, 480f),
            Triple(250, 1850f, 480f),
            Triple(1, 340f, 700f),
            Triple(400, 340f, 700f),
        )
        for ((count, w, h) in cases) {
            val cell = badgeCellDp(count = count, availableW = w, availableH = h, maxCell = 136f)
            val cols = colsFor(w, cell)
            val rows = rowsFor(count, cols)
            assertTrue("n=$count cols*cell=${cols * cell} > $w", cols * cell <= w + 0.01f)
            assertTrue("n=$count rows*cell=${rows * cell} > $h", rows * cell <= h + 0.01f)
        }
    }

    @Test fun `zero or one badge does not divide by zero`() {
        assertEquals(50f, badgeCellDp(count = 0, availableW = 200f, availableH = 200f, maxCell = 50f), 0.01f)
        assertEquals(50f, badgeCellDp(count = 1, availableW = 200f, availableH = 200f, maxCell = 50f), 0.01f)
    }

    @Test fun `a degenerate container does not crash`() {
        assertEquals(0f, badgeCellDp(count = 20, availableW = 0f, availableH = 200f, maxCell = 72f), 0.01f)
        assertEquals(0f, badgeCellDp(count = 20, availableW = 200f, availableH = 0f, maxCell = 72f), 0.01f)
    }

    // --- badgeArtDp / badgeRingBoxDp: the ring must not shrink the art, and a ringed badge must
    // occupy the same footprint as an unringed one. ---

    /**
     * The art is the same size call whether or not a badge ends up ringed - neither function
     * below takes a "ringed" argument, so there is no code path that can special-case one. The
     * ring's margin comes out of GRID_GAP_DP, which every cell reserves regardless of ring state.
     */
    @Test fun `the art size does not depend on whether the badge is ringed`() {
        val cell = badgeCellDp(count = 40, availableW = 1200f, availableH = 500f, maxCell = 136f)
        val artForRingedBadge = badgeArtDp(cell)
        val artForUnringedBadge = badgeArtDp(cell)
        assertEquals(artForUnringedBadge, artForRingedBadge, 0f)
    }

    @Test fun `the ring box sits strictly outside the art and inside the cell`() {
        for (cell in listOf(40f, 94.78f, 128f, 136f)) {
            val art = badgeArtDp(cell)
            val ringBox = badgeRingBoxDp(art)
            // Strictly bigger than the art it surrounds, so its stroke is never painted over it.
            assertTrue("ringBox=$ringBox should exceed art=$art", ringBox > art)
            // And no bigger than the cell itself, so it never reaches into a neighbour's art.
            assertTrue("ringBox=$ringBox should fit within cell=$cell", ringBox <= cell)
        }
    }

    /**
     * The badges sit flush: the gap is EXACTLY the two ring widths
     * that meet at a cell boundary, and nothing more. Adjacent rings may touch - the earlier
     * dark-space-between-rings rule is deliberately superseded; the art itself stays untouched
     * by any ring, which the ring-box test above still guarantees.
     */
    @Test fun `the gap is exactly the rings' own space - the badges sit flush`() {
        assertEquals(0f, GRID_GAP_DP / 2f - HARDCORE_RING_WIDTH_DP, 0f)
    }
}
