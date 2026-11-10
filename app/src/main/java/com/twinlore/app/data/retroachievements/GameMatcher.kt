package com.twinlore.app.data.retroachievements

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Matches a vault display name to a RetroAchievements game from the user's recently played list.
 * Uses title normalization and Levenshtein similarity ≥ 85%.
 * Fallback: match against all games in the local RA database (closest name across all consoles).
 */
@Singleton
class GameMatcher @Inject constructor(
    private val apiClient: RetroAchievementsApiClient,
    private val credentialsRepository: RetroAchievementsCredentialsRepository,
    private val raLocalDataRepository: RaLocalDataRepository
) {
    companion object {
        const val SIMILARITY_THRESHOLD = 0.85
        const val RECENTLY_PLAYED_COUNT = 15
    }

    /**
     * Try to find an RA Game ID for the given vault display name.
     * Fetches last [RECENTLY_PLAYED_COUNT] played games and returns the first with similarity ≥ [SIMILARITY_THRESHOLD].
     * Returns null if no credentials, no match, or API error.
     */
    suspend fun findRaGameId(vaultDisplayName: String): Int? {
        val creds = credentialsRepository.getCredentials() ?: return null
        val normalizedVault = TitleNormalizer.normalize(vaultDisplayName)
        if (normalizedVault.isEmpty()) return null
        return runCatching {
            val recent = apiClient.api.getRecentlyPlayedGames(
                username = creds.username,
                apiKey = creds.apiKey,
                count = RECENTLY_PLAYED_COUNT
            )
            for (game in recent) {
                val normalizedRa = TitleNormalizer.normalize(game.title ?: "")
                if (normalizedRa.isEmpty()) continue
                val sim = Levenshtein.similarity(normalizedVault, normalizedRa)
                if (sim >= SIMILARITY_THRESHOLD) return game.gameId
            }
            null
        }.getOrNull()
    }

    /**
     * Fallback: find RA Game ID by matching vault name against all games in the local database
     * (all consoles). Uses same normalization and Levenshtein similarity ≥ [SIMILARITY_THRESHOLD].
     * Returns the game ID with highest similarity, or null if none above threshold.
     */
    suspend fun findRaGameIdFromLocalDatabase(vaultDisplayName: String): Int? {
        val normalizedVault = TitleNormalizer.normalize(vaultDisplayName)
        if (normalizedVault.isEmpty()) return null
        val allGames = raLocalDataRepository.getAllGames()
        var bestId: Int? = null
        var bestSim = 0.0
        for (game in allGames) {
            val normalizedRa = TitleNormalizer.normalize(game.title)
            if (normalizedRa.isEmpty()) continue
            val sim = Levenshtein.similarity(normalizedVault, normalizedRa)
            if (sim > bestSim && sim >= SIMILARITY_THRESHOLD) {
                bestSim = sim
                bestId = game.id
            }
        }
        return bestId
    }
}
