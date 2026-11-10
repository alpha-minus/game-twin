package com.twinlore.app.data.retroachievements

/**
 * Levenshtein distance and similarity (0.0..1.0).
 * Used for title matching with threshold ≥ 0.85.
 */
object Levenshtein {

    /**
     * Returns similarity as a value between 0.0 (no match) and 1.0 (identical).
     * Formula: 1 - (distance / max(len(a), len(b))).
     * If both strings are empty, returns 1.0.
     */
    fun similarity(a: String, b: String): Double {
        if (a.isEmpty() && b.isEmpty()) return 1.0
        val maxLen = maxOf(a.length, b.length)
        if (maxLen == 0) return 1.0
        val distance = distance(a, b)
        return 1.0 - (distance.toDouble() / maxLen)
    }

    /**
     * Edit distance between two strings.
     */
    fun distance(a: String, b: String): Int {
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        val costs = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                val newCost = minOf(
                    prev + 1,
                    costs[j] + 1,
                    costs[j - 1] + cost
                )
                costs[j - 1] = prev
                prev = newCost
            }
            costs[b.length] = prev
        }
        return costs[b.length]
    }
}
