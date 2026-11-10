package com.twinlore.app.journey.domain

import com.twinlore.app.journey.domain.model.Unlock

/**
 * The facts a journey has about itself that are not positions on a chart: which day an unlock
 * landed on, which badge speaks for a day, how long the player kept coming back, and which game
 * held them the longest.
 *
 * Pure and free of Compose, Android and time zones: a day is a whole number of days since the
 * epoch, which is what every caller here compares against another day rather than prints.
 *
 * Every function is deterministic to the tie-break, because two of them decide what is drawn: a
 * chart that picked a different badge for the same day on a second run would be a different picture
 * of the same journey.
 */
object JourneyStats {

    const val DAY_MILLIS = 86_400_000L

    /** How many consecutive days of unlocks it takes before a stretch counts as a streak. */
    const val MIN_STREAK_DAYS = 3

    /** Which day an unlock landed on: whole days since the epoch, negative dates included. */
    fun dayOf(millis: Long): Long = Math.floorDiv(millis, DAY_MILLIS)

    /**
     * The one badge per day worth drawing, as indices into [unlocks], ascending.
     *
     * The compare chart draws two or three players at once, and a player's whole badge list on that
     * stage is the wall of thumbnails compare mode had its badges taken away for. One a day is the
     * shape of the answer: it keeps the stage readable however busy the journey, and it keeps
     * drawing the badge anyone would point at, because the day speaks through its highest scorer.
     *
     * Ties go to the earlier unlock and then to the lower [Unlock.id], so the same day of the same
     * journey always elects the same badge. Medals are not in this list and are never affected by
     * it: an award is drawn wherever it was won.
     */
    fun dailyBadgeIndices(unlocks: List<Unlock>): List<Int> {
        if (unlocks.isEmpty()) return emptyList()
        val best = LinkedHashMap<Long, Int>()
        unlocks.forEachIndexed { index, unlock ->
            val day = dayOf(unlock.earnedAtMillis)
            val current = best[day]
            if (current == null || beats(unlock, unlocks[current])) best[day] = index
        }
        return best.values.sorted()
    }

    private fun beats(candidate: Unlock, held: Unlock): Boolean = when {
        candidate.points != held.points -> candidate.points > held.points
        candidate.earnedAtMillis != held.earnedAtMillis -> candidate.earnedAtMillis < held.earnedAtMillis
        else -> candidate.id < held.id
    }

    /** A stretch of consecutive days the player unlocked something on. */
    data class Streak(val startMillis: Long, val endMillis: Long, val days: Int)

    /**
     * Every run of [minDays] or more consecutive days with an unlock on each of them, in order.
     *
     * The bounds are the first and last unlock of the run rather than the day boundaries around it,
     * so the chart can place a mark at the moment the streak covers instead of at midnight beside
     * it. A day with several unlocks counts once, which is what "consecutive days" means.
     */
    fun streaks(unlocks: List<Unlock>, minDays: Int = MIN_STREAK_DAYS): List<Streak> {
        if (unlocks.isEmpty()) return emptyList()
        val byDay = sortedMapOf<Long, LongArray>()
        for (unlock in unlocks) {
            val day = dayOf(unlock.earnedAtMillis)
            val bounds = byDay[day]
            if (bounds == null) {
                byDay[day] = longArrayOf(unlock.earnedAtMillis, unlock.earnedAtMillis)
            } else {
                if (unlock.earnedAtMillis < bounds[0]) bounds[0] = unlock.earnedAtMillis
                if (unlock.earnedAtMillis > bounds[1]) bounds[1] = unlock.earnedAtMillis
            }
        }

        val out = mutableListOf<Streak>()
        var runStartDay: Long? = null
        var previousDay: Long? = null
        fun close() {
            val start = runStartDay ?: return
            val end = previousDay ?: return
            val days = (end - start + 1).toInt()
            if (days >= minDays) {
                out += Streak(
                    startMillis = byDay.getValue(start)[0],
                    endMillis = byDay.getValue(end)[1],
                    days = days,
                )
            }
        }
        for (day in byDay.keys) {
            val last = previousDay
            if (last == null || day != last + 1) {
                close()
                runStartDay = day
            }
            previousDay = day
        }
        close()
        return out
    }

    /**
     * The longest run of consecutive days with an unlock on each, in days; 0 for a journey with no
     * unlocks at all. Unlike [streaks] this counts a single day as a run of one, because the end
     * state reports it as a number rather than drawing it.
     */
    fun longestStreakDays(unlocks: List<Unlock>): Int {
        if (unlocks.isEmpty()) return 0
        val days = unlocks.map { dayOf(it.earnedAtMillis) }.distinct().sorted()
        var best = 1
        var run = 1
        for (i in 1 until days.size) {
            run = if (days[i] == days[i - 1] + 1) run + 1 else 1
            if (run > best) best = run
        }
        return best
    }

    /**
     * The game the journey spent the longest on: the one whose first and last unlock are furthest
     * apart. Null when there is nothing to compare.
     *
     * Elapsed time rather than unlock count on purpose - the question the end state asks is which
     * game the player kept coming back to, and a game finished in one sitting can easily out-count
     * one played across a season. Ties go to the game with more unlocks and then to the lower id,
     * so the answer never depends on map ordering.
     */
    fun longestPlayedGameId(unlocks: List<Unlock>): Int? {
        if (unlocks.isEmpty()) return null
        val first = HashMap<Int, Long>()
        val last = HashMap<Int, Long>()
        val count = HashMap<Int, Int>()
        for (unlock in unlocks) {
            val id = unlock.gameId
            val at = unlock.earnedAtMillis
            first[id] = minOf(first[id] ?: at, at)
            last[id] = maxOf(last[id] ?: at, at)
            count[id] = (count[id] ?: 0) + 1
        }
        return first.keys.maxWithOrNull(
            compareBy<Int> { last.getValue(it) - first.getValue(it) }
                .thenBy { count.getValue(it) }
                .thenBy { -it },
        )
    }

    /** How long a player's own journey ran, first unlock to last; null when they have none. */
    fun playerSpanMillis(unlocks: List<Unlock>): Long? {
        if (unlocks.isEmpty()) return null
        val first = unlocks.minOf { it.earnedAtMillis }
        val last = unlocks.maxOf { it.earnedAtMillis }
        return last - first
    }
}
