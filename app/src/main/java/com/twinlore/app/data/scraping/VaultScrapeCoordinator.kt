package com.twinlore.app.data.scraping

import android.util.Log
import com.twinlore.app.data.VaultRepository
import com.twinlore.app.data.library.BrowserSaveCoordinator
import com.twinlore.app.data.library.SaveResult
import com.twinlore.app.domain.AssetType
import com.twinlore.app.domain.Result
import com.twinlore.app.domain.Vault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Unified scraping coordinator for Vault assets
 * Orchestrates scraping of box art, map, and guide in a single pipeline
 * 
 * Contract:
 * - Identifies game once
 * - Scrapes all supported media types (box art, map, guide)
 * - Assigns results atomically to Vault
 * - Never overwrites user-assigned assets
 */
@Singleton
class VaultScrapeCoordinator @Inject constructor(
    private val vaultRepository: VaultRepository,
    private val browserSaveCoordinator: BrowserSaveCoordinator,
    private val gameNameSanitizer: GameNameSanitizer,
    private val settingsRepository: com.twinlore.app.data.SettingsRepository,
    private val scrapers: @JvmSuppressWildcards List<ArtworkScraper>,
) {
    companion object {
        private const val TAG = "VaultScrapeCoordinator"
        const val DEBOUNCE_WINDOW_MS: Long = 30_000L

        /**
         * Overall wall-clock ceiling for a single scrape attempt (search +
         * grids/heroes/logos, across all providers). The per-call OkHttp
         * timeouts (5s connect / 10-15s read) stack up sequentially — a
         * hanging provider could otherwise keep the user waiting ~110s. This
         * bounds the whole loop; expiry surfaces as [ScrapeError.Unavailable].
         */
        const val SCRAPE_TIMEOUT_MS: Long = 20_000L

        /**
         * Single user-facing label for every scrape stage (search → fetch →
         * download). The staged wording ("Searching…", "Fetching…") was noise
         * for a flow that only ever produces one asset; the dialog just says
         * "Scraping artwork" until it completes.
         */
        const val SCRAPING_MESSAGE: String = "Scraping artwork…"
    }

    private val lastScrapeAtByVault = java.util.concurrent.ConcurrentHashMap<String, Long>()

    /**
     * Picker flow: try every configured scraper in order, return the first
     * one that yields assets for [gameName]. Throws the most specific
     * [ScrapeError] encountered when all scrapers fail.
     *
     * Distinct from [scrapeVault] because the picker dialog needs the full
     * asset lists (grids/heroes/logos) so the user can choose, whereas
     * [scrapeVault] just picks the first candidate automatically.
     *
     * Error precedence (most specific first): [ScrapeError.InvalidKey],
     * [ScrapeError.QuotaExceeded], [ScrapeError.RateLimited],
     * [ScrapeError.Unavailable], [ScrapeError.Unknown]. If every scraper
     * returned null/empty (no hard error), throws [ScrapeError.NotFound].
     */
    @Throws(ScrapeError::class)
    suspend fun collectArtworkOptions(gameName: String): ScrapedArtwork {
        val sanitized = gameNameSanitizer.sanitize(gameName)
        Log.d(TAG, "collectArtworkOptions for: '$sanitized' (original: '$gameName')")
        val errors = mutableListOf<ScrapeError>()
        val result = try {
            kotlinx.coroutines.withTimeout(SCRAPE_TIMEOUT_MS) {
                for (scraper in scrapers) {
                    try {
                        val scraped = scraper.scrape(sanitized)
                        if (scraped != null && scraped.hasAnyAssets()) {
                            Log.d(TAG, "${scraper.providerName} returned assets for picker")
                            return@withTimeout scraped
                        }
                        Log.d(TAG, "${scraper.providerName} returned empty; falling back")
                    } catch (e: ScrapeError) {
                        Log.w(TAG, "${scraper.providerName} failed: ${e.userMessage}")
                        errors += e
                    }
                }
                null
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Log.w(TAG, "⏱Picker scrape timed out after ${SCRAPE_TIMEOUT_MS}ms")
            throw ScrapeError.Unavailable
        }
        if (result != null) return result
        if (errors.isEmpty()) throw ScrapeError.NotFound
        throw errors.minBy { errorPriority(it) }
    }

    private fun errorPriority(e: ScrapeError): Int = when (e) {
        is ScrapeError.InvalidKey -> 0
        is ScrapeError.QuotaExceeded -> 1
        ScrapeError.RateLimited -> 2
        ScrapeError.Unavailable -> 3
        is ScrapeError.Unknown -> 4
        ScrapeError.NotFound -> 5
    }

    /** Vault IDs currently being scraped (e.g. new vault from auto-refresh). UI can show loading overlay for these. */
    private val _scrapingVaultIds = MutableStateFlow<Set<String>>(emptySet())
    val scrapingVaultIds: StateFlow<Set<String>> = _scrapingVaultIds.asStateFlow()

    /**
     * Scraping result for a single asset
     */
    sealed class AssetScrapeResult {
        data class Success(val assetType: AssetType, val filePath: String) : AssetScrapeResult()
        data class Skipped(val assetType: AssetType, val reason: String) : AssetScrapeResult()
        data class NotFound(val assetType: AssetType, val message: String) : AssetScrapeResult()
        data class Error(val assetType: AssetType, val error: Throwable) : AssetScrapeResult()
    }
    
    /**
     * Overall scraping progress
     */
    sealed class ScrapingProgress {
        data class Processing(val vaultId: String, val message: String) : ScrapingProgress()
        data class AssetResult(val vaultId: String, val result: AssetScrapeResult) : ScrapingProgress()
        data class Complete(val vaultId: String, val results: List<AssetScrapeResult>) : ScrapingProgress()
        data class Error(val vaultId: String, val error: Throwable) : ScrapingProgress()
    }
    
    /**
     * Main entry point: Scrape vault assets using SteamGridDB
     * 
     * Pipeline:
     * 1. Get vault
     * 2. Check if scraping is needed
     * 3. Search for game by name using SteamGridDB
     * 4. Get gameId from search results (first result)
     * 5. Fetch assets (heroes, logos, grids)
     * 6. Download and save assets
     * 7. Assign assets to Vault
     * 8. Persist Vault state
     * 
     * @param vaultId Vault ID to scrape
     * @return Flow of scraping progress
     */
    fun scrapeVault(vaultId: String, forceFresh: Boolean = false): Flow<ScrapingProgress> = flow {
        _scrapingVaultIds.value = _scrapingVaultIds.value + vaultId
        Log.d(TAG, "SCRAPE VAULT CALLED for vaultId: $vaultId (forceFresh=$forceFresh)")
        try {
            // Debounce: skip if the same vault was scraped within the window.
            val now = System.currentTimeMillis()
            val last = lastScrapeAtByVault[vaultId]
            if (!forceFresh && last != null && (now - last) < DEBOUNCE_WINDOW_MS) {
                Log.d(TAG, "⏳ Debounced scrape for vault $vaultId (elapsed=${now - last}ms)")
                emit(ScrapingProgress.Complete(vaultId, emptyList()))
                return@flow
            }
            lastScrapeAtByVault[vaultId] = now

            emit(ScrapingProgress.Processing(vaultId, SCRAPING_MESSAGE))

            // Step 1: Get vault
            val vault = vaultRepository.getVaultById(vaultId)
            if (vault == null) {
                emit(ScrapingProgress.Error(vaultId, Exception("Vault not found: $vaultId")))
                return@flow
            }

            // Step 2: Check if scraping is needed (only artwork, maps are no longer scraped)
            val needsBoxArt = !vault.hasArtwork

            Log.d(TAG, "Vault: ${vault.name}")
            Log.d(TAG, "needsBoxArt: $needsBoxArt")

            if (!needsBoxArt) {
                Log.d(TAG, "⏭Artwork already exists, skipping scrape")
                emit(ScrapingProgress.Complete(vaultId, listOf(
                    AssetScrapeResult.Skipped(AssetType.ART, "Already has artwork"),
                    AssetScrapeResult.Skipped(AssetType.GUIDE, "Guides are manual only")
                )))
                return@flow
            }

            // Step 3: Search for game by display name (includes renamed name, ROM filename fallback)
            val gameName = vault.displayName
            if (gameName.isBlank()) {
                emit(ScrapingProgress.Error(vaultId, Exception("No game name available for search")))
                return@flow
            }

            emit(ScrapingProgress.Processing(vaultId, SCRAPING_MESSAGE))
            val sanitizedName = gameNameSanitizer.sanitize(gameName)
            Log.d(TAG, "Searching for game: '$sanitizedName' (original: '$gameName')")

            // Try each scraper in order; first success with assets wins.
            // The whole loop is bounded by SCRAPE_TIMEOUT_MS so a hanging
            // provider can't keep the user waiting past the ceiling — expiry
            // is treated as a transient Unavailable failure.
            var artwork: ScrapedArtwork? = null
            val errors = mutableListOf<ScrapeError>()
            try {
                kotlinx.coroutines.withTimeout(SCRAPE_TIMEOUT_MS) {
                    for (scraper in scrapers) {
                        try {
                            Log.d(TAG, "Trying scraper: ${scraper.providerName}")
                            val result = scraper.scrape(sanitizedName)
                            if (result != null && result.hasAnyAssets()) {
                                artwork = result
                                Log.d(TAG, "Scraper ${scraper.providerName} returned assets")
                                break
                            }
                        } catch (e: ScrapeError) {
                            Log.w(TAG, "Scraper ${scraper.providerName} failed: ${e.userMessage}")
                            errors += e
                            // fall through to next scraper
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                Log.w(TAG, "⏱Scrape timed out after ${SCRAPE_TIMEOUT_MS}ms")
                errors += ScrapeError.Unavailable
            }

            // Stable local: `artwork` was assigned inside the withTimeout
            // closure, so it can no longer be smart-cast below.
            val resolvedArtwork = artwork
            if (resolvedArtwork == null) {
                val error: ScrapeError = when {
                    errors.isEmpty() -> ScrapeError.NotFound
                    errors.all { it is ScrapeError.Unavailable } -> ScrapeError.Unavailable
                    errors.any { it is ScrapeError.RateLimited } -> ScrapeError.RateLimited
                    else -> errors.first()
                }
                emit(ScrapingProgress.Error(vaultId, error))
                return@flow
            }

            // Update vault with gameId for future use
            val updatedVault = vault.copy(gameId = resolvedArtwork.gameId)
            vaultRepository.updateVault(updatedVault)

            // Step 5: Fetch assets
            emit(ScrapingProgress.Processing(vaultId, SCRAPING_MESSAGE))

            val results = mutableListOf<AssetScrapeResult>()

            // Scrape box art using default asset type from settings
            if (needsBoxArt) {
                val defaultAssetType = settingsRepository.defaultAssetType.first()
                Log.d(TAG, "Starting box art scraping (${defaultAssetType.displayName})...")
                emit(ScrapingProgress.Processing(vaultId, SCRAPING_MESSAGE))
                val boxArtResult = scrapeBoxArtFromArtwork(vault, resolvedArtwork, defaultAssetType)
                results.add(boxArtResult)
                emit(ScrapingProgress.AssetResult(vaultId, boxArtResult))
            } else {
                results.add(AssetScrapeResult.Skipped(AssetType.ART, "Already has artwork"))
            }

            // Guides are manual only
            results.add(AssetScrapeResult.Skipped(AssetType.GUIDE, "Guides are manual only"))

            // Step 6: Complete
            Log.d(TAG, "Scraping complete for vault: $vaultId")
            emit(ScrapingProgress.Complete(vaultId, results))
        } catch (e: Exception) {
            if (e::class.qualifiedName?.contains("AbortFlowException") == true) {
                Log.w(TAG, "Flow was cancelled (AbortFlowException) - not emitting error")
                throw e
            }
            Log.e(TAG, "EXCEPTION scraping vault: $vaultId", e)
            emit(ScrapingProgress.Error(vaultId, e))
        }
    }.catch { e ->
        if (e::class.qualifiedName?.contains("AbortFlowException") == true) {
            Log.w(TAG, "Flow was cancelled (AbortFlowException) in catch operator")
        } else {
            Log.e(TAG, "EXCEPTION in Flow catch operator for vault: $vaultId", e)
            emit(ScrapingProgress.Error(vaultId, e))
        }
    }.onCompletion {
        _scrapingVaultIds.value = _scrapingVaultIds.value - vaultId
    }
    
    /**
     * Scrape box art from a normalized ScrapedArtwork (from either SGDB or TGDB),
     * picking the candidate whose aspect ratio is closest to the user's grid
     * display ratio. Within the user's preferred ArtworkType bucket first,
     * falling back to the other buckets if empty. Width/height come from the
     * provider's metadata; candidates without dimensions are deprioritized.
     */
    private suspend fun scrapeBoxArtFromArtwork(
        vault: Vault,
        artwork: ScrapedArtwork,
        assetType: com.twinlore.app.data.DefaultAssetType
    ): AssetScrapeResult = withContext(Dispatchers.IO) {
        try {
            val preferredType = when (assetType) {
                com.twinlore.app.data.DefaultAssetType.GRID -> ArtworkType.GRID
                com.twinlore.app.data.DefaultAssetType.LOGO -> ArtworkType.LOGO
                com.twinlore.app.data.DefaultAssetType.HERO -> ArtworkType.HERO
            }
            val targetRatio = settingsRepository.gridDisplayRatio.first().aspectRatio
            // Globally closest ratio across all types (preferred type breaks
            // ties) — same selection the ratio-sorted manual picker shows on
            // top, so auto-assign and the picker agree.
            val candidate = artwork.pickClosestByRatio(targetRatio, preferredType)

            if (candidate == null) {
                Log.e(TAG, "No usable assets in artwork for ${artwork.gameName}")
                return@withContext AssetScrapeResult.NotFound(AssetType.ART, "No assets found for game")
            }

            val assetUrl = candidate.url
            Log.d(TAG, "Saving asset via BrowserSaveCoordinator: $assetUrl")
            val saveResult = browserSaveCoordinator.saveImage(
                url = assetUrl,
                kind = "artwork",
                vaultId = vault.id,
                referer = null
            )
            when (saveResult) {
                is SaveResult.Success -> {
                    val updateResult = vaultRepository.updateArtworkResourceId(vault.id, saveResult.resourceFileId)
                    if (updateResult.isSuccess()) {
                        AssetScrapeResult.Success(AssetType.ART, saveResult.resourceFileId)
                    } else {
                        val error = (updateResult as? Result.Failure)?.error ?: Exception("Unknown error")
                        AssetScrapeResult.Error(AssetType.ART, Exception("Failed to update vault: ${error.message}"))
                    }
                }
                is SaveResult.Failed -> {
                    Log.e(TAG, "Failed to save box art from: $assetUrl", saveResult.cause)
                    AssetScrapeResult.Error(AssetType.ART, saveResult.cause)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception scraping box art from artwork", e)
            AssetScrapeResult.Error(AssetType.ART, e)
        }
    }

}

