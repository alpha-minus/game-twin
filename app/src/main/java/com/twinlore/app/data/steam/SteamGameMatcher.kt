package com.twinlore.app.data.steam

import com.twinlore.app.data.util.Levenshtein
import com.twinlore.app.data.util.TitleNormalizer
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SteamGameMatcher @Inject constructor(
    private val apiClient: SteamApiClient,
    private val credentialsRepository: SteamCredentialsRepository
) {
    companion object {
        const val SIMILARITY_THRESHOLD = 0.75
    }

    /**
     * Find a Steam App ID by matching the vault display name against the user's owned Steam games
     * using Levenshtein similarity. Only searches games in the user's library.
     */
    suspend fun findSteamAppId(vaultDisplayName: String): Int? {
        val normalizedVault = TitleNormalizer.normalize(vaultDisplayName)
        if (normalizedVault.isEmpty()) return null
        val creds = credentialsRepository.getCredentials() ?: return null
        return runCatching {
            val response = apiClient.api.getOwnedGames(
                steamId = creds.steamId, includeAppInfo = 1
            )
            val games = response.response.games ?: return@runCatching null
            var bestId: Int? = null
            var bestSim = 0.0
            for (game in games) {
                val normalizedSteam = TitleNormalizer.normalize(game.name ?: "")
                if (normalizedSteam.isEmpty()) continue
                val sim = Levenshtein.similarity(normalizedVault, normalizedSteam)
                if (sim > bestSim && sim >= SIMILARITY_THRESHOLD) {
                    bestSim = sim
                    bestId = game.appId
                }
            }
            bestId
        }.getOrNull()
    }
}
