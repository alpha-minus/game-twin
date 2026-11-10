package com.twinlore.app.data

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides game name suggestions based on CSV file
 */
@Singleton
class GameNameSuggestionProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "GameNameSuggestionProvider"
        private const val CSV_ASSET_PATH = "retro_and_indie_games_unique.csv"
        private const val MAX_SUGGESTIONS = 10
    }
    
    private var cachedGameNames: List<String>? = null
    
    /**
     * Load game names from CSV asset file.
     *
     * Dedup strategy: each row's title is reduced to a canonical form by
     * lowercasing and collapsing non-alphanumeric runs to a single space.
     * The full title (including subtitles) is preserved — e.g. "Hollow Knight"
     * and "Hollow Knight: Silksong" are distinct entries. Only true casing/
     * punctuation variants collapse: "ELDEN RING" and "Elden Ring" → same key.
     * For each canonical group we keep the "nicest" display form (prefer mixed
     * case over ALL-CAPS).
     */
    private suspend fun loadGameNames(): List<String> = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.assets.open(CSV_ASSET_PATH)
            val reader = BufferedReader(InputStreamReader(inputStream))

            val canonicalToDisplay = linkedMapOf<String, String>()

            // Skip header line
            reader.readLine()

            reader.useLines { lines ->
                lines.forEach { line ->
                    try {
                        val gameName = parseCsvTitle(line)
                        if (gameName.isNotBlank()) {
                            val key = canonicalTitle(gameName)
                            if (key.isNotBlank()) {
                                val current = canonicalToDisplay[key]
                                if (current == null || isNicerDisplay(gameName, current)) {
                                    canonicalToDisplay[key] = gameName
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Error parsing CSV line: $line", e)
                    }
                }
            }

            Log.d(TAG, "Loaded ${canonicalToDisplay.size} unique game names from CSV (deduped by canonical title)")
            canonicalToDisplay.values.toList()
        } catch (e: Exception) {
            Log.e(TAG, "Error loading CSV file", e)
            emptyList()
        }
    }

    /**
     * Parse the Title field (column 2) from a CSV line, handling quoted fields
     * that may contain commas (e.g. `Steam,"Warhammer 40,000: Boltgun",Various`).
     */
    private fun parseCsvTitle(line: String): String {
        // Find start of second field
        val firstComma = line.indexOf(',')
        if (firstComma < 0) return ""
        val afterFirst = firstComma + 1
        if (afterFirst >= line.length) return ""

        return if (line[afterFirst] == '"') {
            // Quoted field — find closing quote (handle escaped "" as literal ")
            val start = afterFirst + 1
            val sb = StringBuilder()
            var i = start
            while (i < line.length) {
                if (line[i] == '"') {
                    if (i + 1 < line.length && line[i + 1] == '"') {
                        sb.append('"')
                        i += 2
                    } else {
                        break
                    }
                } else {
                    sb.append(line[i])
                    i++
                }
            }
            sb.toString().trim()
        } else {
            // Unquoted field — take until next comma
            val nextComma = line.indexOf(',', afterFirst)
            if (nextComma < 0) line.substring(afterFirst).trim()
            else line.substring(afterFirst, nextComma).trim()
        }
    }

    /**
     * Reduce a game title to a dedup key. Lowercases and normalizes every
     * non-alphanumeric run to a single space. The full title including
     * subtitles is preserved so distinct games sharing a franchise name
     * (e.g. "Hollow Knight" vs "Hollow Knight: Silksong") remain separate.
     *
     * Examples:
     *  - "Elden Ring", "ELDEN RING", "Elden_Ring" → "elden ring"
     *  - "Hollow Knight: Silksong" → "hollow knight silksong"
     *  - "Hollow Knight" → "hollow knight"
     */
    private fun canonicalTitle(name: String): String {
        return name
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")
    }

    /** True if [candidate] should replace [current] as the display form for a
     *  canonical group. Shorter wins; on ties prefer the less-shouty casing. */
    private fun isNicerDisplay(candidate: String, current: String): Boolean {
        if (candidate.length < current.length) return true
        if (candidate.length > current.length) return false
        val candidateLowerCount = candidate.count { it.isLowerCase() }
        val currentLowerCount = current.count { it.isLowerCase() }
        return candidateLowerCount > currentLowerCount
    }
    
    /**
     * Get cached game names, loading if necessary
     */
    private suspend fun getGameNames(): List<String> {
        if (cachedGameNames == null) {
            cachedGameNames = loadGameNames()
        }
        return cachedGameNames ?: emptyList()
    }
    
    /**
     * Normalize a string for better matching
     * - Convert to lowercase
     * - Remove common punctuation and special characters
     * - Remove extra whitespace
     * - Remove common stop words (the, a, an, and, or, etc.)
     */
    private fun normalizeString(str: String): String {
        return str.lowercase()
            .replace(Regex("[.:;,!?'\"()-]"), " ") // Replace punctuation with space
            .replace(Regex("\\s+"), " ") // Normalize whitespace
            .trim()
            .split(" ")
            .filter { word ->
                // Remove common stop words for better matching
                word.isNotBlank() && word !in setOf("the", "a", "an", "and", "or", "of", "in", "on", "at", "to", "for", "with", "from")
            }
            .joinToString(" ")
    }
    
    /**
     * Calculate token-based similarity (word order independent)
     * Returns ratio of matching tokens (Jaccard similarity coefficient)
     */
    private fun tokenSimilarity(queryTokens: List<String>, gameTokens: List<String>): Double {
        if (queryTokens.isEmpty() || gameTokens.isEmpty()) return 0.0
        
        val querySet = queryTokens.toSet()
        val gameSet = gameTokens.toSet()
        
        val intersection = querySet.intersect(gameSet).size
        val union = querySet.union(gameSet).size
        
        // Jaccard similarity coefficient
        return if (union == 0) 0.0 else intersection.toDouble() / union.toDouble()
    }
    
    /**
     * Calculate Levenshtein distance between two strings
     */
    private fun levenshteinDistance(s1: String, s2: String): Int {
        val s1Lower = s1.lowercase()
        val s2Lower = s2.lowercase()
        
        if (s1Lower == s2Lower) return 0
        if (s1Lower.isEmpty()) return s2Lower.length
        if (s2Lower.isEmpty()) return s1Lower.length
        
        val costs = IntArray(s2Lower.length + 1)
        for (i in 0..s1Lower.length) {
            var lastValue = i
            for (j in 0..s2Lower.length) {
                if (i == 0) {
                    costs[j] = j
                } else {
                    if (j > 0) {
                        var newValue = costs[j - 1]
                        if (s1Lower[i - 1] != s2Lower[j - 1]) {
                            newValue = minOf(newValue, lastValue, costs[j]) + 1
                        }
                        costs[j - 1] = lastValue
                        lastValue = newValue
                    }
                }
            }
            if (i > 0) {
                costs[s2Lower.length] = lastValue
            }
        }
        return costs[s2Lower.length]
    }
    
    /**
     * Get suggestions for a query string
     * Returns top matches based on similarity with improved algorithm:
     * - Normalization (removes punctuation, stop words)
     * - Token-based matching (word order independent)
     * - Levenshtein distance for fuzzy matching
     * - Multiple scoring methods combined
     */
    suspend fun getSuggestions(query: String, limit: Int = MAX_SUGGESTIONS): List<String> = withContext(Dispatchers.IO) {
        if (query.isBlank()) {
            return@withContext emptyList()
        }
        
        val queryLower = query.trim().lowercase()
        val queryNormalized = normalizeString(query)
        val queryTokens = queryNormalized.split(" ").filter { it.isNotBlank() }
        val gameNames = getGameNames()
        
        // Calculate similarity scores with similarity ratio
        data class SuggestionScore(val gameName: String, val score: Int, val similarityRatio: Double)
        
        val suggestionsWithScores = gameNames.map { gameName ->
            val gameNameLower = gameName.lowercase()
            val gameNameNormalized = normalizeString(gameName)
            val gameTokens = gameNameNormalized.split(" ").filter { it.isNotBlank() }
            
            val (score, similarityRatio) = when {
                // Exact match (original case-insensitive)
                gameNameLower == queryLower -> Pair(1000, 1.0)
                
                // Exact match (normalized) - handles punctuation differences
                gameNameNormalized == queryNormalized -> Pair(950, 1.0)
                
                // Contains match (original) gets high score (with position bonus)
                gameNameLower.contains(queryLower) -> {
                    val position = gameNameLower.indexOf(queryLower)
                    Pair(500 - position, 1.0) // Earlier matches get higher scores
                }
                
                // Contains match (normalized) - better matching with punctuation removed
                gameNameNormalized.contains(queryNormalized) -> {
                    val position = gameNameNormalized.indexOf(queryNormalized)
                    Pair(480 - position, 0.95)
                }
                
                // Starts with match (original)
                gameNameLower.startsWith(queryLower) -> Pair(400, 1.0)
                
                // Starts with match (normalized)
                gameNameNormalized.startsWith(queryNormalized) -> Pair(390, 0.95)
                
                // Token-based matching (word order independent) - very useful for game names
                queryTokens.isNotEmpty() && gameTokens.isNotEmpty() -> {
                    val tokenSim = tokenSimilarity(queryTokens, gameTokens)
                    if (tokenSim >= 0.5) { // At least 50% token match
                        // Also check normalized Levenshtein for fine-tuning
                        val normalizedDistance = levenshteinDistance(queryNormalized, gameNameNormalized)
                        val maxLength = maxOf(queryNormalized.length, gameNameNormalized.length)
                        val normalizedSimilarity = if (maxLength == 0) 0.0 else 1.0 - (normalizedDistance.toDouble() / maxLength.toDouble())
                        
                        // Combine token similarity (60%) and normalized similarity (40%)
                        val combinedSimilarity = (tokenSim * 0.6) + (normalizedSimilarity * 0.4)
                        Pair((300.0 + (combinedSimilarity * 100.0)).toInt(), combinedSimilarity)
                    } else {
                        // Fall through to Levenshtein
                        val normalizedDistance = levenshteinDistance(queryNormalized, gameNameNormalized)
                        val maxLength = maxOf(queryNormalized.length, gameNameNormalized.length)
                        val similarity = if (maxLength == 0) 0.0 else 1.0 - (normalizedDistance.toDouble() / maxLength.toDouble())
                        Pair(300 - (normalizedDistance * 100 / maxOf(maxLength, 1)), similarity)
                    }
                }
                
                // Levenshtein distance on normalized strings for fuzzy matching
                else -> {
                    val normalizedDistance = levenshteinDistance(queryNormalized, gameNameNormalized)
                    val maxLength = maxOf(queryNormalized.length, gameNameNormalized.length)
                    val similarity = if (maxLength == 0) 0.0 else 1.0 - (normalizedDistance.toDouble() / maxLength.toDouble())
                    Pair(300 - (normalizedDistance * 100 / maxOf(maxLength, 1)), similarity)
                }
            }
            SuggestionScore(gameName, score, similarityRatio)
        }
        
        // Sort by score (descending) and filter Levenshtein distance matches below 0.75 threshold
        // Keep contains/exact matches first (they have similarityRatio >= 0.95)
        suggestionsWithScores
            .filter { it.score > 0 && (it.similarityRatio >= 0.95 || it.similarityRatio >= 0.75) } // Keep exact/contains matches (>=0.95) and fuzzy matches >= 0.75
            .sortedByDescending { it.score } // Sort by score
            .take(limit)
            .map { it.gameName }
    }

    /**
     * Filter and rank a list of candidate strings by the same logic as getSuggestions
     * (normalize, exact/contains/starts-with/token/Levenshtein, threshold 0.75).
     * Used for console/game dropdowns in RetroAchievements drawer.
     */
    fun filterAndRankCandidates(query: String, candidates: List<String>, limit: Int = 100): List<String> {
        if (query.isBlank()) return candidates
        val queryLower = query.trim().lowercase()
        // For very short queries (1–2 chars), use simple substring match so typing always narrows the list
        if (queryLower.length <= 2) {
            return candidates
                .filter { it.lowercase().contains(queryLower) }
                .take(limit)
        }
        val queryNormalized = normalizeString(query)
        val queryTokens = queryNormalized.split(" ").filter { it.isNotBlank() }
        data class Score(val name: String, val score: Int, val similarityRatio: Double)
        val scored = candidates.map { name ->
            val nameLower = name.lowercase()
            val nameNormalized = normalizeString(name)
            val nameTokens = nameNormalized.split(" ").filter { it.isNotBlank() }
            val (score, ratio) = when {
                nameLower == queryLower -> Pair(1000, 1.0)
                nameNormalized == queryNormalized -> Pair(950, 1.0)
                nameLower.contains(queryLower) -> Pair(500 - nameLower.indexOf(queryLower), 1.0)
                nameNormalized.contains(queryNormalized) -> Pair(480 - nameNormalized.indexOf(queryNormalized), 0.95)
                nameLower.startsWith(queryLower) -> Pair(400, 1.0)
                nameNormalized.startsWith(queryNormalized) -> Pair(390, 0.95)
                queryTokens.isNotEmpty() && nameTokens.isNotEmpty() -> {
                    val tokenSim = tokenSimilarity(queryTokens, nameTokens)
                    if (tokenSim >= 0.5) {
                        val dist = levenshteinDistance(queryNormalized, nameNormalized)
                        val maxLen = maxOf(queryNormalized.length, nameNormalized.length)
                        val normSim = if (maxLen == 0) 0.0 else 1.0 - (dist.toDouble() / maxLen)
                        val combined = (tokenSim * 0.6) + (normSim * 0.4)
                        Pair((300.0 + combined * 100.0).toInt(), combined)
                    } else {
                        val dist = levenshteinDistance(queryNormalized, nameNormalized)
                        val maxLen = maxOf(queryNormalized.length, nameNormalized.length)
                        val sim = if (maxLen == 0) 0.0 else 1.0 - (dist.toDouble() / maxLen)
                        Pair(300 - (dist * 100 / maxOf(maxLen, 1)), sim)
                    }
                }
                else -> {
                    val dist = levenshteinDistance(queryNormalized, nameNormalized)
                    val maxLen = maxOf(queryNormalized.length, nameNormalized.length)
                    val sim = if (maxLen == 0) 0.0 else 1.0 - (dist.toDouble() / maxLen)
                    Pair(300 - (dist * 100 / maxOf(maxLen, 1)), sim)
                }
            }
            Score(name, score, ratio)
        }
        return scored
            .filter { it.score > 0 && (it.similarityRatio >= 0.95 || it.similarityRatio >= 0.75) }
            .sortedByDescending { it.score }
            .take(limit)
            .map { it.name }
    }
}
