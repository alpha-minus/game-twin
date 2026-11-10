package com.twinlore.app.data.scraping

enum class ArtworkType(val wire: String) {
    GRID("grid"),
    HERO("hero"),
    LOGO("logo"),
}

data class AssetCandidate(
    val url: String,
    val width: Int?,
    val height: Int?,
    val thumbnailUrl: String? = null,
)

data class ScrapedArtwork(
    val provider: String,
    val gameId: String,
    val gameName: String,
    val grids: List<AssetCandidate>,
    val heroes: List<AssetCandidate>,
    val logos: List<AssetCandidate>,
) {
    fun hasAnyAssets(): Boolean =
        grids.isNotEmpty() || heroes.isNotEmpty() || logos.isNotEmpty()

    fun candidatesFor(type: ArtworkType): List<AssetCandidate> = when (type) {
        ArtworkType.GRID -> grids
        ArtworkType.HERO -> heroes
        ArtworkType.LOGO -> logos
    }
}

/**
 * Auto-assign selection: the [preferredType] is prioritized — pick the
 * candidate WITHIN that type whose aspect ratio (width/height) is closest to
 * [targetRatio], and only fall back to the other types (GRID, LOGO, HERO
 * order) when the preferred type has no candidates at all. Within the chosen
 * type, dimensioned candidates are ranked by ratio distance; if none carry
 * provider dimensions, the first is kept (provider's natural order).
 */
internal fun ScrapedArtwork.pickClosestByRatio(
    targetRatio: Float,
    preferredType: ArtworkType,
): AssetCandidate? {
    val order = listOf(preferredType) +
        (listOf(ArtworkType.GRID, ArtworkType.LOGO, ArtworkType.HERO) - preferredType)
    for (type in order) {
        val candidates = candidatesFor(type)
        if (candidates.isEmpty()) continue
        return candidates.pickClosestByRatio(targetRatio)
    }
    return null
}

/**
 * Pick the candidate in this list whose ratio is closest to [targetRatio].
 * Candidates without dimensions can't be ranked, so when none have
 * dimensions the first (provider's natural order) is returned.
 */
internal fun List<AssetCandidate>.pickClosestByRatio(targetRatio: Float): AssetCandidate? {
    if (isEmpty()) return null
    val withRatio = mapNotNull { c ->
        val w = c.width
        val h = c.height
        if (w != null && h != null && w > 0 && h > 0) c to (w.toFloat() / h) else null
    }
    return if (withRatio.isNotEmpty()) {
        withRatio.minBy { kotlin.math.abs(it.second - targetRatio) }.first
    } else {
        first()
    }
}

sealed class ScrapeError(
    val userMessage: String,
    cause: Throwable? = null,
) : Throwable(userMessage, cause) {
    object Unavailable : ScrapeError(
        "Network error or slow server response. Try again later with Assign " +
            "Artwork, or add an image file manually."
    )
    object NotFound : ScrapeError("No artwork found for this game.")
    object RateLimited : ScrapeError(
        "Too many scrape requests. Please wait a moment and try again."
    )

    /**
     * The provider rejected the API key (HTTP 401). The service is probably
     * up; the bundled or overridden key is the likely culprit. Points the
     * user at Settings → Library Display where they can paste their own.
     */
    class InvalidKey(val providerDisplayName: String) : ScrapeError(
        "$providerDisplayName rejected the API key. If the service is up, " +
            "your key is likely the problem — open Settings → Library Display and " +
            "paste your own $providerDisplayName key, or clear any override to " +
            "fall back to the bundled key."
    )

    /**
     * The provider says the monthly/daily quota for this key is exhausted
     * (HTTP 403 for TheGamesDB, similar for SteamGridDB). Only user override
     * can unblock it.
     */
    class QuotaExceeded(val providerDisplayName: String) : ScrapeError(
        "$providerDisplayName quota exceeded for the current API key. " +
            "Open Settings → Library Display and paste your own " +
            "$providerDisplayName key to continue scraping."
    )

    class Unknown(cause: Throwable) : ScrapeError(
        "Artwork scraping failed: ${cause.message ?: cause.javaClass.simpleName}",
        cause,
    )
}

interface ArtworkScraper {
    /**
     * Provider name for logging and debugging (e.g. "steamgriddb", "thegamesdb").
     */
    val providerName: String

    /**
     * Search and fetch artwork for a game. Returns null when the provider
     * found no match (distinct from a hard error). Throws [ScrapeError] for
     * transient / rate-limit / auth / unknown failures.
     */
    @Throws(ScrapeError::class)
    suspend fun scrape(
        name: String,
        types: Set<ArtworkType> = setOf(ArtworkType.GRID, ArtworkType.HERO, ArtworkType.LOGO),
    ): ScrapedArtwork?
}
