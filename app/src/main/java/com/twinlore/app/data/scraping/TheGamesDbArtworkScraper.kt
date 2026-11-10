package com.twinlore.app.data.scraping

import android.util.Log
import com.twinlore.app.BuildConfig
import com.twinlore.app.data.SettingsRepository
import com.twinlore.app.data.security.ObfuscatedKey
import kotlinx.coroutines.flow.first
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TheGamesDbArtworkScraper @Inject constructor(
    private val api: TheGamesDbApi,
    private val settingsRepository: SettingsRepository,
) : ArtworkScraper {

    override val providerName: String = "thegamesdb"

    private val bundledKey: String by lazy {
        ObfuscatedKey.decode(
            BuildConfig.THEGAMESDB_API_KEY_ENCODED,
            BuildConfig.KEY_OBFUSCATION_MASK,
        )
    }

    private suspend fun effectiveKey(): String {
        val override = settingsRepository.theGamesDbApiKeyOverride.first()
        return override.takeIf { it.isNotBlank() } ?: bundledKey
    }

    override suspend fun scrape(name: String, types: Set<ArtworkType>): ScrapedArtwork? {
        val override = settingsRepository.theGamesDbApiKeyOverride.first()
        val key = override.takeIf { it.isNotBlank() } ?: bundledKey
        val keySource = if (override.isNotBlank()) "USER_OVERRIDE" else "BUNDLED_DEFAULT"
        Log.d(TAG, "scrape() keySource=$keySource")
        if (key.isBlank()) return null

        val search = try {
            api.searchByName(apiKey = key, name = name)
        } catch (e: HttpException) {
            mapHttpError(e)?.let { throw it }
            return null
        } catch (e: IOException) {
            throw ScrapeError.Unavailable
        } catch (e: Throwable) {
            throw ScrapeError.Unknown(e)
        }

        if (search.data.count == 0 || search.data.games.isEmpty()) return null
        val top = search.data.games.first()

        val images = try {
            api.getImages(apiKey = key, gameId = top.id)
        } catch (e: HttpException) {
            mapHttpError(e)?.let { throw it }
            return null
        } catch (e: IOException) {
            throw ScrapeError.Unavailable
        } catch (e: Throwable) {
            throw ScrapeError.Unknown(e)
        }

        val list = images.data.images[top.id.toString()] ?: emptyList()
        val baseUrl = images.data.baseUrl.original

        val grids = mutableListOf<AssetCandidate>()
        val heroes = mutableListOf<AssetCandidate>()
        val logos = mutableListOf<AssetCandidate>()

        for (img in list) {
            val dims = parseResolution(img.resolution)
            val candidate = AssetCandidate(
                url = baseUrl + img.filename,
                width = dims.first,
                height = dims.second,
            )
            when {
                img.type == "boxart" && img.side == "front" && ArtworkType.GRID in types -> grids += candidate
                img.type == "fanart" && ArtworkType.HERO in types -> heroes += candidate
                img.type == "clearlogo" && ArtworkType.LOGO in types -> logos += candidate
                else -> { /* screenshot, banner, boxart back: dropped */ }
            }
        }

        // TGDB returns grid candidates in arbitrary order. For grid mode
        // (the most common case) the caller picks the first one, so
        // front-load the candidate whose aspect ratio is closest to the
        // canonical 2:3 box-art shape. Candidates without dimensions fall
        // to the back and are only used when nothing else is available.
        val sortedGrids = grids.sortedBy { c ->
            val w = c.width
            val h = c.height
            if (w != null && h != null && h > 0) {
                kotlin.math.abs(w.toDouble() / h.toDouble() - TARGET_GRID_RATIO)
            } else {
                Double.MAX_VALUE
            }
        }

        return ScrapedArtwork(
            provider = providerName,
            gameId = top.id.toString(),
            gameName = top.gameTitle,
            grids = sortedGrids,
            heroes = heroes,
            logos = logos,
        )
    }

    private fun mapHttpError(e: HttpException): ScrapeError? {
        Log.w(TAG, "HTTP ${e.code()} from TheGamesDB")
        return when (e.code()) {
        404 -> null // treated as "no match" by the caller via returning null
        429 -> ScrapeError.RateLimited
        401 -> ScrapeError.InvalidKey("TheGamesDB")
        // TheGamesDB returns 403 when the monthly allowance is exhausted.
        403 -> ScrapeError.QuotaExceeded("TheGamesDB")
        502, 503, 504 -> ScrapeError.Unavailable
        else -> ScrapeError.Unknown(e)
        }
    }

    companion object {
        private const val TAG = "TheGamesDbScraper"
        /** Canonical box-art aspect ratio used to pick the best grid candidate. */
        private const val TARGET_GRID_RATIO: Double = 2.0 / 3.0
    }

    private fun parseResolution(res: String?): Pair<Int?, Int?> {
        if (res.isNullOrEmpty()) return null to null
        val m = Regex("""^(\d+)x(\d+)$""").matchEntire(res) ?: return null to null
        return m.groupValues[1].toInt() to m.groupValues[2].toInt()
    }
}
