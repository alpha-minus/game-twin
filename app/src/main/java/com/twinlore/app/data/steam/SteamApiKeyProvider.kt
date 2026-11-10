package com.twinlore.app.data.steam

import com.twinlore.app.BuildConfig
import com.twinlore.app.data.SettingsRepository
import com.twinlore.app.data.security.ObfuscatedKey
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves the effective Steam Web API key for each outgoing request.
 *
 * Resolution order:
 *   1. User override from Settings (if non-blank).
 *   2. Bundled default, decoded from BuildConfig at first use.
 *
 * The decoded bundled default is cached for the lifetime of the process.
 */
@Singleton
class SteamApiKeyProvider @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {
    @Volatile
    private var bundledCache: String? = null

    private fun bundled(): String {
        bundledCache?.let { return it }
        val decoded = ObfuscatedKey.decode(
            BuildConfig.STEAM_API_KEY_ENCODED,
            BuildConfig.KEY_OBFUSCATION_MASK,
        )
        bundledCache = decoded
        return decoded
    }

    suspend fun effectiveKey(): String {
        val override = settingsRepository.steamApiKeyOverride.first()
        return override.takeIf { it.isNotBlank() } ?: bundled()
    }
}
