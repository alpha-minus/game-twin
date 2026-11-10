package com.twinlore.app.data

import com.twinlore.app.data.scraping.GameNameSanitizer
import com.twinlore.app.domain.Vault
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fuzzy name matching for vault conflict resolution.
 * Uses Levenshtein distance on normalized names to find similar vaults.
 */
@Singleton
class FuzzyNameMatcher @Inject constructor(
    private val gameNameSanitizer: GameNameSanitizer
) {
    data class MatchResult(
        val vault: Vault,
        val similarity: Double
    )

    /**
     * Find the best matching vault by normalized name similarity.
     *
     * Two bounds, both of which must hold. [minSimilarity] (default 0.80) is what governs short
     * names, where a single edit is already a large fraction of the name. [MAX_EDITS] governs
     * long ones, where 20% of a 37-character title is seven characters — a whole subtitle. That
     * is not hypothetical: "Oracle of Seasons" and "Oracle of Ages" score 0.865, and "Aria of
     * Sorrow" and "Dawn of Sorrow" score 0.846, so the ratio alone merged two pairs of
     * genuinely different games. Neither carries a numeral, so the sequel guard cannot help.
     *
     * The cap is equivalent to a threshold that rises with length (1 - 3/len): 0.82 at 17
     * characters, 0.88 at 26, 0.92 at 37. Expressing it as "at most three characters may
     * differ" keeps it tunable and easy to reason about.
     */
    fun findBestMatch(
        candidateName: String,
        existingVaults: List<Vault>,
        minSimilarity: Double = 0.80
    ): MatchResult? {
        val normalizedCandidate = gameNameSanitizer.normalize(candidateName)
        if (normalizedCandidate.isBlank()) return null
        // Sequel guard, shared with the StrategyWiki / GameFAQs auto-detect
        // matchers: the set of numbers in the name must match exactly, so a base
        // game never looks like its numbered sequel and "2" never looks like "3".
        // Roman and Arabic numerals are unified, so "God of War II" and "God of
        // War 2" still count as the same game. See [canonicalMatchTokens].
        val candidateNumbers = numberSignature(canonicalMatchTokens(candidateName))

        return existingVaults
            .mapNotNull { vault ->
                val existingNumbers = numberSignature(canonicalMatchTokens(vault.displayName))
                if (existingNumbers != candidateNumbers) return@mapNotNull null
                val normalizedExisting = gameNameSanitizer.normalize(vault.displayName)
                if (levenshteinDistance(normalizedCandidate, normalizedExisting) > MAX_EDITS) {
                    return@mapNotNull null
                }
                MatchResult(vault, levenshteinSimilarity(normalizedCandidate, normalizedExisting))
            }
            .filter { it.similarity >= minSimilarity }
            .maxByOrNull { it.similarity }
    }

    private fun levenshteinSimilarity(a: String, b: String): Double {
        if (a == b) return 1.0
        val maxLen = maxOf(a.length, b.length)
        if (maxLen == 0) return 1.0
        return 1.0 - (levenshteinDistance(a, b).toDouble() / maxLen)
    }

    private fun levenshteinDistance(a: String, b: String): Int {
        val m = a.length
        val n = b.length
        val dp = Array(m + 1) { IntArray(n + 1) }
        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j
        for (i in 1..m) {
            for (j in 1..n) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[m][n]
    }

    companion object {
        /**
         * The most characters two normalized names may differ by and still count as the same
         * game. Three covers punctuation, a region or edition tag, and roman-vs-arabic numerals
         * ("Final Fantasy VII" to "Final Fantasy 7" is exactly three), while stopping the
         * subtitle swaps that the ratio alone let through.
         */
        const val MAX_EDITS = 3
    }
}
