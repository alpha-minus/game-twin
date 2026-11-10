package com.twinlore.app.journey.ui.chart

import android.content.Context
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.twinlore.app.data.retroachievements.RaUrls
import com.twinlore.app.journey.domain.ChartLayout
import com.twinlore.app.journey.domain.model.JourneyFrame
import com.twinlore.app.journey.ui.BackdropLogic
import com.twinlore.app.journey.ui.ExportBackground
import com.twinlore.app.journey.ui.JourneyUrls
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * Decodes every image a chart frame draws (badges, medal icons, player avatars) once, up front,
 * so playback never blocks on a decode mid-animation.
 *
 * Keys are the full URLs the renderer looks up. A URL that fails to load is simply absent from the
 * map, and the renderer falls back to the game's icon or to a plain typed disc rather than drawing
 * an empty one.
 *
 * Failures are not remembered anywhere: Coil does not negative-cache, and this object holds no
 * state, so a url that failed is retried whenever it is asked for again. What used to make a
 * failure permanent was the caller, which decoded once per loaded journey and kept that map for the
 * life of the screen. [missingUrls] and [retryMissing] give it a cheap second attempt (the screen
 * takes it when the player is opened) without re-decoding the images that already landed.
 */
object JourneyBitmaps {

    private const val PARALLELISM = 6

    // ARGB_8888 at 96x96 is 36,864 bytes (~36 KB) per decoded icon. IconBudget's widened-stage
    // ceiling (WIDE_ICONS_CAP, 600 icons - the preload reads the WIDE layout) is the worst case
    // for this: 600 * 36 KB ~= 21.6 MB, plus a handful of avatar bitmaps at the same tile size -
    // an acceptable allocation on the target hardware, and the reason that ceiling is absolute.
    private const val BADGE_PX = 96
    private const val AVATAR_PX = 96

    // Half the export's 1920x1080: the art is drawn at 16% alpha behind the chart, so a full
    // resolution decode per game would cost memory nothing on screen would ever repay.
    private const val ART_W = 960
    private const val ART_H = 540

    /** Every URL [preload] would fetch for this frame, in a stable order. */
    fun urlsFor(
        frame: JourneyFrame,
        layout: ChartLayout,
        userName: String,
    ): List<String> {
        val urls = LinkedHashSet<String>()
        layout.nodes.forEach { urls += RaUrls.badge(it.unlock.badgeName) }
        // Game icons, for the medallions that draw them and for badges whose own art fails to
        // decode (the renderer falls back to the game's icon before it falls back to a plain disc).
        // A chart's badges come from a handful of distinct games, the set dedupes them, and an icon
        // is one 96px tile, so covering the fallback costs a few requests at most.
        val gameIds = LinkedHashSet<Int>()
        layout.nodes.forEach { gameIds += it.unlock.gameId }
        layout.medals.forEach { medal -> medal.medal.gameId?.let { gameIds += it } }
        gameIds.forEach { gameId ->
            val game = frame.main.games[gameId] ?: frame.rivals.firstNotNullOfOrNull { it.games[gameId] }
            game?.iconPath?.let { urls += JourneyUrls.media(it) }
        }
        if (userName.isNotBlank()) urls += JourneyUrls.userPic(userName)
        frame.rivals.forEach { rival ->
            if (rival.unlocks.isNotEmpty() && rival.name.isNotBlank()) {
                urls += JourneyUrls.userPic(rival.name)
            }
        }
        return urls.toList()
    }

    suspend fun preload(
        context: Context,
        imageLoader: ImageLoader,
        frame: JourneyFrame,
        layout: ChartLayout,
        userName: String,
    ): Map<String, ImageBitmap> {
        val urls = urlsFor(frame, layout, userName)
        if (urls.isEmpty()) return emptyMap()
        val gate = Semaphore(PARALLELISM)
        return withContext(Dispatchers.IO) {
            coroutineScope {
                urls.map { url ->
                    async { url to gate.withPermit { load(context, imageLoader, url) } }
                }.awaitAll()
            }
        }.mapNotNull { (url, bitmap) -> bitmap?.let { url to it } }.toMap()
    }

    /**
     * The URLs this frame draws that [loaded] has no bitmap for, i.e. the ones whose request
     * failed. Pure, so the "what is worth retrying" question is unit tested without an ImageLoader.
     */
    fun missingUrls(
        frame: JourneyFrame,
        layout: ChartLayout,
        userName: String,
        loaded: Set<String>,
    ): List<String> = urlsFor(frame, layout, userName).filterNot { it in loaded }

    /**
     * One more attempt at [urls], merged into [loaded]. Whatever fails again is simply still
     * absent, and the caller is expected to try this once rather than on a timer: a url that is
     * genuinely gone (a deleted badge, a 404) must not turn into a request loop.
     */
    suspend fun retryMissing(
        context: Context,
        imageLoader: ImageLoader,
        urls: List<String>,
        loaded: Map<String, ImageBitmap>,
    ): Map<String, ImageBitmap> {
        if (urls.isEmpty()) return loaded
        val gate = Semaphore(PARALLELISM)
        val retried = withContext(Dispatchers.IO) {
            coroutineScope {
                urls.map { url ->
                    async { url to gate.withPermit { load(context, imageLoader, url) } }
                }.awaitAll()
            }
        }.mapNotNull { (url, bitmap) -> bitmap?.let { url to it } }
        return if (retried.isEmpty()) loaded else loaded + retried
    }

    /**
     * Backdrop art per game id, for every game the sweep passes over (main player and rivals), so
     * the video export can draw the same dimmed art the screen shows without fetching anything of
     * its own. Candidates are [BackdropLogic.bigArtCandidatesFor] - the same list the screen's own
     * backdrop walks, icon excluded for the same reason it is excluded there - and the first one
     * that decodes wins; a game whose art all fails is simply absent, and that stretch of the video
     * keeps the plain background.
     */
    suspend fun preloadArt(
        context: Context,
        imageLoader: ImageLoader,
        frame: JourneyFrame,
        layout: ChartLayout,
        /** Which art to decode; [ExportBackground.NONE] preloads nothing and the export stays plain. */
        source: ExportBackground = ExportBackground.TITLE,
    ): Map<Int, ImageBitmap> {
        if (source == ExportBackground.NONE) return emptyMap()
        val players = listOf(frame.main) + frame.rivals
        val tracks = listOf(layout.main) + layout.rivals
        val candidates = LinkedHashMap<Int, List<String>>()
        tracks.forEachIndexed { index, curve ->
            val games = players.getOrNull(index)?.games ?: return@forEachIndexed
            curve.bgTrack.forEach { (_, gameId) ->
                if (candidates.containsKey(gameId)) return@forEach
                val paths = games[gameId]?.let { BackdropLogic.bigArtCandidatesFor(it, source) }.orEmpty()
                if (paths.isNotEmpty()) candidates[gameId] = paths.map(JourneyUrls::media)
            }
        }
        if (candidates.isEmpty()) return emptyMap()
        val gate = Semaphore(PARALLELISM)
        return withContext(Dispatchers.IO) {
            coroutineScope {
                candidates.map { (gameId, urls) ->
                    async {
                        gameId to gate.withPermit {
                            urls.firstNotNullOfOrNull { url -> loadArt(context, imageLoader, url) }
                        }
                    }
                }.awaitAll()
            }
        }.mapNotNull { (gameId, bitmap) -> bitmap?.let { gameId to it } }.toMap()
    }

    private suspend fun loadArt(context: Context, imageLoader: ImageLoader, url: String): ImageBitmap? =
        runCatching {
            val request = ImageRequest.Builder(context)
                .data(url)
                .size(ART_W, ART_H)
                .allowHardware(false)
                .build()
            val result = imageLoader.execute(request)
            if (result !is SuccessResult) null
            else result.drawable.toBitmap().asImageBitmap()
        }.getOrNull()

    private suspend fun load(context: Context, imageLoader: ImageLoader, url: String): ImageBitmap? =
        runCatching {
            val px = if (url.startsWith(RaUrls.BADGE_BASE)) BADGE_PX else AVATAR_PX
            val request = ImageRequest.Builder(context)
                .data(url)
                .size(px, px)
                .allowHardware(false)
                .build()
            val result = imageLoader.execute(request)
            if (result !is SuccessResult) null
            else result.drawable.toBitmap().asImageBitmap()
        }.getOrNull()
}
