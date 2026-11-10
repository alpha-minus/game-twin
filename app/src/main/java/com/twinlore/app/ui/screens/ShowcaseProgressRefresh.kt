package com.twinlore.app.ui.screens

import android.util.Log
import com.twinlore.app.data.retroachievements.RaGameCacheRepository
import com.twinlore.app.data.retroachievements.RaGameProgress
import com.twinlore.app.data.steam.SteamGameCacheRepository
import com.twinlore.app.data.steam.SteamGameProgress
import kotlinx.coroutines.CancellationException

internal sealed interface ShowcaseProgress {
    data class Ra(val progress: RaGameProgress) : ShowcaseProgress
    data class Steam(val progress: SteamGameProgress) : ShowcaseProgress
}

/**
 * Force-refreshes the detection showcase's achievement progress; null when there
 * is nothing fresh to show. RA wins when the vault is linked to both.
 *
 * Never throws (except cancellation): SteamGameCacheRepository reports a private
 * profile, a game with no stats and an unowned game as exceptions, and one escaping
 * the showcase's LaunchedEffect crashes the app. A private profile is also exactly
 * when detection finds nothing and the showcase opens on the already-focused vault.
 */
internal suspend fun refreshShowcaseProgress(
    vaultId: String,
    raGameId: Int?,
    steamAppId: Int?,
    raGameCacheRepository: RaGameCacheRepository,
    steamGameCacheRepository: SteamGameCacheRepository,
): ShowcaseProgress? = try {
    when {
        raGameId != null -> raGameCacheRepository.getGameProgress(vaultId, raGameId, forceRefresh = true)
            ?.let { ShowcaseProgress.Ra(it) }
        steamAppId != null -> steamGameCacheRepository.getGameProgress(vaultId, steamAppId, forceRefresh = true)
            ?.let { ShowcaseProgress.Steam(it) }
        else -> null
    }
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Log.w("ShowcaseProgress", "Showcase achievement refresh failed for vaultId=$vaultId", e)
    null
}
