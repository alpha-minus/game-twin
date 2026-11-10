package com.twinlore.app.data

import com.twinlore.app.data.steam.SteamLastPlayed
import android.content.Context
import android.net.Uri
import android.util.Log
import com.twinlore.app.data.license.LicenseManager
import com.twinlore.app.data.retroachievements.RaProfileRepository
import com.twinlore.app.data.retroachievements.RetroAchievementsApiClient
import com.twinlore.app.data.retroachievements.RetroAchievementsCredentialsRepository
import com.twinlore.app.data.library.BindReport
import com.twinlore.app.data.library.LocalMediaBinder
import com.twinlore.app.data.scraping.GameNameSanitizer
import com.twinlore.app.data.scraping.VaultScrapeCoordinator
import com.twinlore.app.data.steam.SteamApiClient
import com.twinlore.app.data.steam.SteamCredentialsRepository
import com.twinlore.app.domain.Result
import com.twinlore.app.domain.Vault
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Syncs the latest played game from RetroArch history file, ES-DE gamelists, or achievement APIs:
 * resolves or creates vault, optionally triggers box-art scrape for new vaults. Used by auto-refresh.
 *
 * @return Vault ID to focus, or null if no vault (not found and create disabled, or license/error).
 * @param uriOverride If set, use this URI instead of reading from settings. For RetroArch this is the folder URI (from watcher); for ES-DE the folder URI.
 */
@Singleton
class SyncLatestPlayedGameUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val retroArchHistoryParser: RetroArchHistoryParser,
    private val esdeLogParser: ESDELogParser,
    private val cocoonLogParser: CocoonLogParser,
    private val vaultRepository: VaultRepository,
    private val gameNameSanitizer: GameNameSanitizer,
    private val licenseManager: LicenseManager,
    private val vaultScrapeCoordinator: VaultScrapeCoordinator,
    private val localMediaBinder: LocalMediaBinder,
    private val raCredentialsRepository: RetroAchievementsCredentialsRepository,
    private val raApiClient: RetroAchievementsApiClient,
    private val raProfileRepository: RaProfileRepository,
    private val steamCredentialsRepository: SteamCredentialsRepository,
    private val steamApiClient: SteamApiClient,
    private val fuzzyNameMatcher: FuzzyNameMatcher
) {
    companion object {
        private const val TAG = "SyncLatestPlayedGame"
    }

    /** Pending fuzzy match for auto-detection: the UI should show a confirmation dialog. */
    data class PendingFuzzyMatch(
        val existingVault: Vault,
        val newName: String,
        val similarity: Double,
        val createIfNotFound: Boolean,
        val source: String,
        val romPath: String? = null,
        val romFilename: String? = null,
        val raGameId: String? = null,
        val steamAppId: String? = null
    )

    private val _pendingFuzzyMatch = MutableStateFlow<PendingFuzzyMatch?>(null)
    val pendingFuzzyMatch: StateFlow<PendingFuzzyMatch?> = _pendingFuzzyMatch.asStateFlow()

    /**
     * Names of games detected during the most recent execute() that were
     * skipped because the user is at the free-tier vault cap. UI collects
     * this to render the "New games detected" summary dialog.
     * Cleared at the start of every execute() call.
     */
    private val _lastRunSkippedByCap = MutableStateFlow<List<String>>(emptyList())
    val lastRunSkippedByCap: StateFlow<List<String>> = _lastRunSkippedByCap.asStateFlow()

    fun clearLastRunSkippedByCap() {
        _lastRunSkippedByCap.value = emptyList()
    }

    /**
     * Bind reports accumulated during the most recent execute() call. Each entry corresponds
     * to a vault for which local media was found or bound. Cleared at the start of every
     * execute() call. UI collects this to render a "Local media bound" summary notification.
     */
    private val _lastRunLocalBindReports = MutableStateFlow<List<BindReport>>(emptyList())
    val lastRunLocalBindReports: StateFlow<List<BindReport>> = _lastRunLocalBindReports.asStateFlow()

    fun clearLastRunLocalBindReports() { _lastRunLocalBindReports.value = emptyList() }

    /** User confirmed using the existing vault — rename it. */
    suspend fun confirmFuzzyMatch() {
        val pending = _pendingFuzzyMatch.value ?: return
        _pendingFuzzyMatch.value = null
        vaultRepository.renameVault(pending.existingVault.id, pending.newName)
        // The vault id is settled now that the user confirmed it; execute() held off
        // writing the now-playing record while this was still a pending candidate.
        settingsRepository.setNowPlayingVaultId(pending.existingVault.id)
    }

    /** User declined — create a new vault. */
    suspend fun declineFuzzyMatch(): String? {
        val pending = _pendingFuzzyMatch.value ?: return null
        _pendingFuzzyMatch.value = null
        val createResult = vaultRepository.createVault(
            name = pending.newName,
            romPath = pending.romPath,
            romFilename = pending.romFilename,
            source = pending.source,
            raGameId = pending.raGameId,
            steamAppId = pending.steamAppId
        )
        return when (createResult) {
            is Result.Success -> {
                val vault = createResult.data
                vaultScrapeCoordinator.scrapeVault(vault.id).collect {}
                // Bind without accumulating into _lastRunLocalBindReports — the user
                // just created this vault, so the local-media result is implicit.
                // bindForVaultWithRescanIfEmpty refreshes the media index when no
                // local match exists (on-demand replacement for the removed
                // startup scanDelta loop).
                localMediaBinder.bindForVaultWithRescanIfEmpty(vault)
                // The vault id is settled now that the user declined in favor of a new
                // vault; execute() held off writing the now-playing record while this
                // was still a pending candidate.
                settingsRepository.setNowPlayingVaultId(vault.id)
                vault.id
            }
            else -> null
        }
    }

    fun dismissFuzzyMatch() {
        _pendingFuzzyMatch.value = null
    }

    suspend fun execute(uriOverride: String? = null): String? =
        detect(uriOverride, allowCreate = true)

    /**
     * [execute], but only ever names a vault that already exists: a game not
     * in the library yet comes back null instead of being created. For a cold
     * start going back into overlay, which only does so when it knows what to
     * show; a new game is the library's to add, with the app in front.
     */
    suspend fun resolveExisting(uriOverride: String? = null): String? =
        detect(uriOverride, allowCreate = false)

    private suspend fun detect(uriOverride: String?, allowCreate: Boolean): String? {
        // Reset the per-run accumulators before every run.
        _lastRunSkippedByCap.value = emptyList()
        _lastRunLocalBindReports.value = emptyList()
        val source = settingsRepository.lastPlayedSource.first()
        val createIfNotFound = allowCreate && settingsRepository.createVaultIfNotFound.first()

        val resolvedVaultId = when (source) {
            LastPlayedSource.RETROARCH -> {
                val folderUri = uriOverride?.takeIf { it.isNotBlank() }
                    ?: settingsRepository.retroArchHistoryFolder.first()
                executeRetroArch(folderUri, createIfNotFound)
            }
            LastPlayedSource.ES_DE -> executeESDE(
                uriOverride?.takeIf { it.isNotBlank() } ?: settingsRepository.esdeFolderUri.first(),
                createIfNotFound
            )
            LastPlayedSource.COCOON -> executeCocoon(
                uriOverride?.takeIf { it.isNotBlank() } ?: settingsRepository.cocoonFolderUri.first(),
                createIfNotFound
            )
            LastPlayedSource.ACHIEVEMENTS -> executeAchievements(createIfNotFound)
            LastPlayedSource.NOT_CONFIGURED -> {
                Log.w(TAG, "Last played source not configured")
                null
            }
        }
        // Skip the write when a fuzzy match is pending confirmation: resolvedVaultId is
        // then only the CANDIDATE vault being focused while the user decides, not the
        // vault they will end up with. Persisting it here would let a later foreground
        // showcase and auto-open the wrong game with no dialog if the user declines.
        // The record is written instead from confirmFuzzyMatch()/declineFuzzyMatch()
        // once the id is settled.
        if (resolvedVaultId != null && _pendingFuzzyMatch.value == null) {
            settingsRepository.setNowPlayingVaultId(resolvedVaultId)
        }
        return resolvedVaultId
    }

    /**
     * Unified achievements detection: queries both RA and Steam APIs (whichever have credentials),
     * compares timestamps, and returns vault ID for the most recently played game.
     */
    private suspend fun executeAchievements(createIfNotFound: Boolean): String? {
        val raCreds = raCredentialsRepository.getCredentials()
        val steamCreds = steamCredentialsRepository.getCredentials()

        if (raCreds == null && steamCreds == null) {
            Log.w(TAG, "No achievement credentials configured")
            return null
        }

        return try {
            coroutineScope {
                val raDeferred = if (raCreds != null) {
                    async {
                        try {
                            // Prefer one getUserSummary (via the profile repo): it serves
                            // BOTH the most-recent game for detection AND the rich-presence
                            // message the fullscreen view reads from the cached profile.
                            val summaryGame = runCatching { raProfileRepository.refresh() }
                                .getOrNull()
                                ?.recentlyPlayed
                                ?.firstOrNull()
                                ?.takeIf { !it.title.isNullOrBlank() }
                            if (summaryGame != null) {
                                Triple(
                                    summaryGame.title!!.trim(),
                                    summaryGame.gameId.toString(),
                                    parseRaLastPlayed(summaryGame.lastPlayed)
                                )
                            } else {
                                // Fallback so detection never breaks if the summary call
                                // fails/empties: the lightweight recently-played endpoint.
                                val recent = raApiClient.api.getRecentlyPlayedGames(
                                    username = raCreds.username,
                                    apiKey = raCreds.apiKey,
                                    count = 1
                                )
                                val game = recent.firstOrNull() ?: return@async null
                                val title = game.title?.trim() ?: return@async null
                                Triple(title, game.gameId.toString(), parseRaLastPlayed(game.lastPlayed))
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "RA API call failed", e)
                            null
                        }
                    }
                } else null

                val steamDeferred = if (steamCreds != null) {
                    async {
                        try {
                            val resp = steamApiClient.api.getOwnedGames(
                                steamId = steamCreds.steamId,
                                includeAppInfo = 1
                            )
                            val games = resp.response.games ?: emptyList()
                            // Owned games are only dated for the key's own account; any
                            // other profile needs the recently played list instead.
                            val recent = if (SteamLastPlayed.needsRecent(games)) {
                                runCatching {
                                    steamApiClient.api.getRecentlyPlayedGames(steamCreds.steamId).response.games
                                }.onFailure { Log.w(TAG, "Steam recently played fallback failed", it) }
                                    .getOrNull().orEmpty()
                            } else {
                                emptyList()
                            }
                            val mostRecent = SteamLastPlayed.pick(games, recent, System.currentTimeMillis()) ?: return@async null
                            Triple(mostRecent.name, mostRecent.appId, mostRecent.epochMillis)
                        } catch (e: Exception) {
                            Log.e(TAG, "Steam API call failed", e)
                            null
                        }
                    }
                } else null

                val raResult = raDeferred?.await()
                val steamResult = steamDeferred?.await()

                when {
                    raResult != null && steamResult != null -> {
                        if (raResult.third >= steamResult.third) {
                            Log.d(TAG, "RA game more recent: ${raResult.first}")
                            resolveOrCreateVaultFromRa(raResult.first, raResult.second, createIfNotFound)
                        } else {
                            Log.d(TAG, "Steam game more recent: ${steamResult.first}")
                            resolveOrCreateVaultFromSteam(steamResult.first, steamResult.second, createIfNotFound)
                        }
                    }
                    raResult != null -> resolveOrCreateVaultFromRa(raResult.first, raResult.second, createIfNotFound)
                    steamResult != null -> resolveOrCreateVaultFromSteam(steamResult.first, steamResult.second, createIfNotFound)
                    else -> {
                        Log.w(TAG, "No recently played games from any achievement source")
                        null
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Achievements sync failed", e)
            null
        }
    }

    /**
     * Parse RA lastPlayed string (format "yyyy-MM-dd HH:mm:ss", assumed UTC) to epoch millis.
     * Returns 0 if null or unparseable.
     */
    private fun parseRaLastPlayed(lastPlayed: String?): Long {
        if (lastPlayed.isNullOrBlank()) return 0L
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            sdf.parse(lastPlayed)?.time ?: 0L
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse RA lastPlayed: $lastPlayed", e)
            0L
        }
    }

    /**
     * Resolve or create vault from RetroAchievements last-played: resolve by raGameId first, then by name; create with raGameId if not found.
     */
    private suspend fun resolveOrCreateVaultFromRa(
        rawGameName: String,
        raGameId: String,
        createIfNotFound: Boolean
    ): String? {
        val sanitizedGameName = gameNameSanitizer.sanitize(rawGameName)
        var existingVault = vaultRepository.resolveVaultByRaGameId(raGameId)
        if (existingVault == null) {
            val normalized = gameNameSanitizer.normalize(rawGameName)
            existingVault = vaultRepository.getAllVaults().firstOrNull { v ->
                gameNameSanitizer.normalize(v.displayName) == normalized
            }
        }
        // Fuzzy match fallback: emit pending confirmation for user
        if (existingVault == null) {
            val allVaults = vaultRepository.getAllVaults()
            val fuzzyMatch = fuzzyNameMatcher.findBestMatch(sanitizedGameName, allVaults)
            if (fuzzyMatch != null && fuzzyMatch.similarity < 1.0) {
                Log.d(TAG, "Fuzzy match pending confirmation: '${fuzzyMatch.vault.displayName}' (${(fuzzyMatch.similarity * 100).toInt()}%) for '$sanitizedGameName'")
                _pendingFuzzyMatch.value = PendingFuzzyMatch(
                    existingVault = fuzzyMatch.vault,
                    newName = sanitizedGameName,
                    similarity = fuzzyMatch.similarity,
                    createIfNotFound = createIfNotFound,
                    source = "RetroAchievements",
                    raGameId = raGameId
                )
                return fuzzyMatch.vault.id // Focus on the existing vault while user decides
            } else if (fuzzyMatch != null) {
                // 100% normalized match — use silently
                existingVault = fuzzyMatch.vault
            }
        }
        if (existingVault != null) {
            val report = localMediaBinder.bindForVault(existingVault)
            if (report.hasAny()) _lastRunLocalBindReports.value = _lastRunLocalBindReports.value + report
            return existingVault.id
        }
        if (!createIfNotFound) return null
        val createResult = vaultRepository.createVault(
            name = sanitizedGameName,
            romPath = null,
            romFilename = null,
            source = "RetroAchievements",
            raGameId = raGameId
        )
        return when (createResult) {
            is Result.Success -> {
                val vault: Vault = createResult.data
                vaultScrapeCoordinator.scrapeVault(vault.id).collect { }
                // Bind without surfacing into the refresh summary — the user just
                // created this vault, so the local-media result is implicit.
                // bindForVaultWithRescanIfEmpty refreshes the media index when
                // no local match exists (on-demand replacement for the removed
                // startup scanDelta loop).
                localMediaBinder.bindForVaultWithRescanIfEmpty(vault)
                vault.id
            }
            is Result.Failure -> {
                Log.e(TAG, "Create vault from RA failed: ${createResult.error.message}")
                null
            }
            else -> null
        }
    }

    /**
     * Resolve or create vault from Steam last-played: resolve by steamAppId first, then by name; create with steamAppId if not found.
     */
    private suspend fun resolveOrCreateVaultFromSteam(
        rawGameName: String,
        steamAppId: String,
        createIfNotFound: Boolean
    ): String? {
        val sanitizedGameName = gameNameSanitizer.sanitize(rawGameName)
        var existingVault = vaultRepository.resolveVaultBySteamAppId(steamAppId)
        if (existingVault == null) {
            val normalized = gameNameSanitizer.normalize(rawGameName)
            existingVault = vaultRepository.getAllVaults().firstOrNull { v ->
                gameNameSanitizer.normalize(v.displayName) == normalized
            }
        }
        // Fuzzy match fallback: emit pending confirmation for user
        if (existingVault == null) {
            val allVaults = vaultRepository.getAllVaults()
            val fuzzyMatch = fuzzyNameMatcher.findBestMatch(sanitizedGameName, allVaults)
            if (fuzzyMatch != null && fuzzyMatch.similarity < 1.0) {
                Log.d(TAG, "Fuzzy match pending confirmation: '${fuzzyMatch.vault.displayName}' (${(fuzzyMatch.similarity * 100).toInt()}%) for '$sanitizedGameName'")
                _pendingFuzzyMatch.value = PendingFuzzyMatch(
                    existingVault = fuzzyMatch.vault,
                    newName = sanitizedGameName,
                    similarity = fuzzyMatch.similarity,
                    createIfNotFound = createIfNotFound,
                    source = "Steam",
                    steamAppId = steamAppId
                )
                return fuzzyMatch.vault.id // Focus on the existing vault while user decides
            } else if (fuzzyMatch != null) {
                // 100% normalized match — use silently
                existingVault = fuzzyMatch.vault
            }
        }
        if (existingVault != null) {
            if (existingVault.steamAppId == null) {
                vaultRepository.updateSteamAppId(existingVault.id, steamAppId)
            }
            val report = localMediaBinder.bindForVault(existingVault)
            if (report.hasAny()) _lastRunLocalBindReports.value = _lastRunLocalBindReports.value + report
            return existingVault.id
        }
        if (!createIfNotFound) return null
        val createResult = vaultRepository.createVault(
            name = sanitizedGameName,
            romPath = null,
            romFilename = null,
            source = "Steam",
            steamAppId = steamAppId
        )
        return when (createResult) {
            is Result.Success -> {
                val vault: Vault = createResult.data
                vaultScrapeCoordinator.scrapeVault(vault.id).collect { }
                // Bind without surfacing into the refresh summary — the user just
                // created this vault, so the local-media result is implicit.
                // bindForVaultWithRescanIfEmpty refreshes the media index when
                // no local match exists (on-demand replacement for the removed
                // startup scanDelta loop).
                localMediaBinder.bindForVaultWithRescanIfEmpty(vault)
                vault.id
            }
            is Result.Failure -> {
                Log.e(TAG, "Create vault from Steam failed: ${createResult.error.message}")
                null
            }
            else -> null
        }
    }

    private suspend fun executeRetroArch(
        folderUriString: String?,
        createIfNotFound: Boolean
    ): String? {
        if (folderUriString.isNullOrBlank()) {
            Log.w(TAG, "Sync: no RetroArch folder URI configured")
            return null
        }
        return try {
            val folderUri = Uri.parse(folderUriString)
            val entry = retroArchHistoryParser.findLastPlayedGame(folderUri)
            if (entry == null) {
                Log.w(TAG, "RetroArch content_history.lpl not found or empty")
                return null
            }
            val vaultId = resolveOrCreateVault(
                rawGameName = entry.gameName,
                romPath = entry.romPath,
                romFilename = entry.romFilename,
                createIfNotFound = createIfNotFound,
                source = "RetroArch",
            )
            if (vaultId != null && entry.launchIntent != null && entry.romPath != null) {
                vaultRepository.updateLaunchIntent(
                    vaultId, entry.launchIntent.toJson(), entry.romPath, entry.romFilename,
                )
            }
            vaultId
        } catch (e: Exception) {
            Log.e(TAG, "RetroArch sync failed", e)
            null
        }
    }

    private suspend fun executeESDE(folderUriString: String?, createIfNotFound: Boolean): String? {
        if (folderUriString.isNullOrBlank()) {
            Log.w(TAG, "Sync: no ES-DE folder URI in settings")
            return null
        }
        return try {
            val folderUri = Uri.parse(folderUriString)

            // Primary: read es_log.txt for the last launched game
            val logEntry = esdeLogParser.findLastLaunchedGame(folderUri)
            if (logEntry != null) {
                Log.d(TAG, "ES-DE log: last launched '${logEntry.gameName}' from '${logEntry.system}'")
                val vaultId = resolveOrCreateVault(
                    rawGameName = logEntry.gameName,
                    romPath = logEntry.romPath,
                    romFilename = esdeLogParser.extractRomFilename(logEntry.romPath),
                    createIfNotFound = createIfNotFound,
                    source = "ES-DE"
                )
                if (vaultId != null && logEntry.launchIntent != null) {
                    val romFilename = esdeLogParser.extractRomFilename(logEntry.romPath)
                    vaultRepository.updateLaunchIntent(
                        vaultId, logEntry.launchIntent.toJson(), logEntry.romPath, romFilename
                    )
                }
                return vaultId
            }

            // Log file not available — no game detected
            Log.w(TAG, "ES-DE log file (logs/es_log.txt) not found. Launch a game from ES-DE first.")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed", e)
            null
        }
    }

    private suspend fun executeCocoon(folderUriString: String?, createIfNotFound: Boolean): String? {
        if (folderUriString.isNullOrBlank()) {
            Log.w(TAG, "Sync: no Cocoon folder URI in settings")
            return null
        }
        return try {
            val folderUri = Uri.parse(folderUriString)
            val logEntry = cocoonLogParser.findLastLaunchedGame(folderUri)
            if (logEntry != null) {
                Log.d(TAG, "Cocoon log: last launched '${logEntry.gameName}'")
                val romFilename = cocoonLogParser.extractRomFilename(logEntry.romPath)
                val vaultId = resolveOrCreateVault(
                    rawGameName = logEntry.gameName,
                    romPath = logEntry.romPath,
                    romFilename = romFilename,
                    createIfNotFound = createIfNotFound,
                    source = "Cocoon"
                )
                if (vaultId != null && logEntry.launchIntent != null) {
                    vaultRepository.updateLaunchIntent(
                        vaultId, logEntry.launchIntent.toJson(), logEntry.romPath, romFilename
                    )
                }
                return vaultId
            }

            Log.w(TAG, "Cocoon launch_debug.log not found or no successful launches.")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Cocoon sync failed", e)
            null
        }
    }

    private suspend fun resolveOrCreateVault(
        rawGameName: String,
        romPath: String?,
        romFilename: String?,
        createIfNotFound: Boolean,
        source: String
    ): String? {
        val sanitizedGameName = gameNameSanitizer.sanitize(rawGameName)

        var existingVault = vaultRepository.resolveVaultIdentity(
            romPath = romPath,
            romFilename = romFilename,
            name = sanitizedGameName
        )
        if (existingVault == null) {
            val normalized = gameNameSanitizer.normalize(sanitizedGameName)
            existingVault = vaultRepository.getAllVaults().firstOrNull { v ->
                gameNameSanitizer.normalize(v.displayName) == normalized
            }
        }
        // Fuzzy match fallback: emit pending confirmation for user
        if (existingVault == null) {
            val allVaults = vaultRepository.getAllVaults()
            val fuzzyMatch = fuzzyNameMatcher.findBestMatch(sanitizedGameName, allVaults)
            if (fuzzyMatch != null && fuzzyMatch.similarity < 1.0) {
                Log.d(TAG, "Fuzzy match pending confirmation: '${fuzzyMatch.vault.displayName}' (${(fuzzyMatch.similarity * 100).toInt()}%) for '$sanitizedGameName'")
                _pendingFuzzyMatch.value = PendingFuzzyMatch(
                    existingVault = fuzzyMatch.vault,
                    newName = sanitizedGameName,
                    similarity = fuzzyMatch.similarity,
                    createIfNotFound = createIfNotFound,
                    source = source,
                    romPath = romPath,
                    romFilename = romFilename
                )
                return fuzzyMatch.vault.id // Focus on the existing vault while user decides
            } else if (fuzzyMatch != null) {
                // 100% normalized match — use silently
                existingVault = fuzzyMatch.vault
            }
        }

        if (existingVault != null) {
            val report = localMediaBinder.bindForVault(existingVault)
            if (report.hasAny()) _lastRunLocalBindReports.value = _lastRunLocalBindReports.value + report
            return existingVault.id
        }
        if (!createIfNotFound) return null

        val createResult = vaultRepository.createVault(
            name = sanitizedGameName,
            romPath = romPath,
            romFilename = romFilename,
            source = source
        )
        return when (createResult) {
            is Result.Success -> {
                val vault: Vault = createResult.data
                // Wait for scrape to complete so UI shows artwork when focusing this vault (auto-refresh / startup).
                vaultScrapeCoordinator.scrapeVault(vault.id).collect { }
                // Bind without surfacing into the refresh summary — the user just
                // created this vault, so the local-media result is implicit.
                // bindForVaultWithRescanIfEmpty refreshes the media index when
                // no local match exists (on-demand replacement for the removed
                // startup scanDelta loop).
                localMediaBinder.bindForVaultWithRescanIfEmpty(vault)
                vault.id
            }
            is Result.Failure -> {
                Log.e(TAG, "Create vault failed: ${createResult.error.message}")
                null
            }
            else -> null
        }
    }
}
