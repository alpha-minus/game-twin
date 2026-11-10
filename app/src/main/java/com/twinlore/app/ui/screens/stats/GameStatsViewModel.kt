package com.twinlore.app.ui.screens.stats

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twinlore.app.data.SettingsRepository
import com.twinlore.app.data.esde.ESDEArtworkResolver
import com.twinlore.app.data.esde.ESDEGamelistStore
import com.twinlore.app.data.esde.EsdeFsAccess
import com.twinlore.app.data.esde.GameMedia
import com.twinlore.app.data.esde.GamelistEntry
import com.twinlore.app.data.esde.MediaSlot
import com.twinlore.app.data.esde.esdeSystemDisplayName
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/** Everything the stats screen renders — all of it from ES-DE's own files. */
data class GameStatsUiState(
    val loading: Boolean = true,
    /** False when gamelists are unreachable (no folder / no All-Files access). */
    val hasGamelists: Boolean = false,
    val totals: StatsTotals = StatsTotals(0, 0, 0, 0),
    /** Play time per system, largest first. */
    val buckets: List<ChartBucket> = emptyList(),
    val topGames: List<GameStat> = emptyList(),
    val genres: List<GenreShare> = emptyList(),
    /** Golden year + average game age from scraped release dates. */
    val era: EraStats = EraStats(),
    /** Most played game of the top genre — its art dims behind the genre card. */
    val genreBackdropGame: GameStat? = null,
    /** ES-DE short name of the most played system. */
    val topSystemShort: String? = null,
    /** Most played game of that system — its physical media watermarks the card. */
    val topSystemGame: GameStat? = null,
)

/** The per-game "activity log" mode: one game's ES-DE numbers with its art. */
data class GameDetail(
    val stat: GameStat,
    val systemDisplayName: String,
    val media: GameMedia = GameMedia(),
    val favorite: Boolean = false,
    val genre: String? = null,
    val releaseYear: Int? = null,
    /** "30 Jul 2026" from ES-DE's lastplayed stamp, if any. */
    val lastPlayedLabel: String? = null,
)

/**
 * Screenshot-first artwork fallback chain (from the removed live-mode appearance system).
 */
private val SCREENSHOT_ART_CHAIN = listOf(
    MediaSlot.SCREENSHOT, MediaSlot.TITLE, MediaSlot.MIX,
    MediaSlot.HERO, MediaSlot.BOX_FRONT, MediaSlot.BOX_3D, MediaSlot.PHYSICAL_MEDIA,
)

/**
 * Aggregates ES-DE gamelists into the play-stats screen state. All heavy work runs
 * off the main thread; gamelists are re-read every time the screen opens (the store
 * caches by file mtime, so unchanged files cost nothing). The app records no play
 * metadata of its own — ES-DE's files are the single source of truth.
 */
@HiltViewModel
class GameStatsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val gamelistStore: ESDEGamelistStore,
    private val artworkResolver: ESDEArtworkResolver,
) : ViewModel() {

    /** null = still loading, empty = no gamelists reachable. */
    private val entries = MutableStateFlow<List<GamelistEntry>?>(null)

    @Volatile private var latestEntries: List<GamelistEntry> = emptyList()

    /** Box-art (or screenshot) per ranked row, keyed by "system|rom basename". */
    private val _artwork = MutableStateFlow<Map<String, Uri?>>(emptyMap())
    val artwork: StateFlow<Map<String, Uri?>> = _artwork.asStateFlow()

    /** Full media (marquee logo + hero/fanart) for the ranked games, by art key. */
    private val _gameMedia = MutableStateFlow<Map<String, GameMedia>>(emptyMap())
    val gameMedia: StateFlow<Map<String, GameMedia>> = _gameMedia.asStateFlow()

    /** The open per-game activity log, or null when the ranking list is shown. */
    private val _detail = MutableStateFlow<GameDetail?>(null)
    val detail: StateFlow<GameDetail?> = _detail.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val folder = settingsRepository.esdeFolderUri.firstOrNull()
            val fsRoot = folder
                ?.takeIf { EsdeFsAccess.isGranted() }
                ?.let { EsdeFsAccess.rootPath(Uri.parse(it)) }
            entries.value = fsRoot?.let { gamelistStore.allEntries(it) } ?: emptyList()
        }
    }

    val uiState: StateFlow<GameStatsUiState> = entries.map { entryList ->
        val loaded = entryList != null
        val gamelist = entryList.orEmpty()
        latestEntries = gamelist

        // One pass over the library feeds the ranking, the totals and the per-system bars —
        // each of those used to re-derive (and re-sort) the whole played set on its own.
        val played = GameStatsCalc.playedGames(gamelist)
        val buckets = GameStatsCalc.systemBucketsOf(played)
        val topGames = GameStatsCalc.rank(played)
        val genres = GameStatsCalc.genreShares(gamelist)

        // Genre card backdrop: the most played game of the leading genre. The ranked games
        // already carry their gamelist genre, so no lookup table over the whole library.
        val topGenre = genres.firstOrNull()?.genre?.lowercase()
        val genreBackdrop = topGenre?.let { g ->
            topGames.firstOrNull { game ->
                game.genre.orEmpty().split('/', ',', ';').any { it.trim().lowercase() == g }
            }
        }

        GameStatsUiState(
            loading = !loaded,
            hasGamelists = gamelist.isNotEmpty(),
            totals = GameStatsCalc.totalsOf(played),
            buckets = buckets,
            topGames = topGames,
            genres = genres,
            era = GameStatsCalc.eraStats(gamelist, currentYear = LocalDate.now(ZoneId.systemDefault()).year),
            genreBackdropGame = genreBackdrop,
            topSystemShort = buckets.firstOrNull()?.label,
            // topGames is already ordered by play time, so the first match IS the most played.
            topSystemGame = buckets.firstOrNull()?.label?.let { system ->
                topGames.firstOrNull { it.system.equals(system, ignoreCase = true) }
            },
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GameStatsUiState())

    /** Key for [artwork] lookups. */
    fun artKey(game: GameStat): String = GameStatsCalc.gameKey(game.system, game.romPath)

    /** Resolve missing box art for the visible ranked rows (cheap direct-file probes). */
    fun ensureArtwork(games: List<GameStat>) {
        viewModelScope.launch(Dispatchers.IO) {
            val folder = settingsRepository.esdeFolderUri.firstOrNull() ?: return@launch
            val fsRoot = (if (EsdeFsAccess.isGranted()) EsdeFsAccess.rootPath(Uri.parse(folder)) else null)
                ?: return@launch
            val missing = games.filter { artKey(it) !in _artwork.value }
            if (missing.isEmpty()) return@launch
            val resolved = missing.associate { game ->
                artKey(game) to runCatching {
                    artworkResolver.libraryImageFor(
                        fsRoot, game.system, game.romPath,
                        // The gameplay-first chain this used before it was shared with the
                        // live view's collage. Same order, now with a 3D-box last resort on the
                        // end, so a game scraped only for that finally gets a thumbnail.
                        SCREENSHOT_ART_CHAIN,
                    )
                }.getOrNull()
            }
            _artwork.update { it + resolved }
        }
    }

    /**
     * Locally-provided logo per system short name, from the user's own logo folder or an ES-DE
     * theme they installed. Null means nothing local was found and the caller may fall back to
     * the downloaded default.
     */
    private val _systemLogos = MutableStateFlow<Map<String, Uri?>>(emptyMap())
    val systemLogos: StateFlow<Map<String, Uri?>> = _systemLogos.asStateFlow()

    /**
     * Resolves the local logo for a system, so the stats rows honour the user's logo folder
     * exactly as the live view's platform screens do. Without it, a user who had replaced every
     * logo would still see downloaded ones here.
     */
    fun ensureSystemLogo(systemShort: String) {
        if (systemShort in _systemLogos.value) return
        viewModelScope.launch(Dispatchers.IO) {
            val folder = settingsRepository.esdeFolderUri.firstOrNull() ?: return@launch
            val art = runCatching {
                artworkResolver.resolveSystemArt(
                    folderUri = Uri.parse(folder),
                    systemShort = systemShort,
                    logoFolder = settingsRepository.esdeCustomLogoFolder.firstOrNull()
                        ?.let(Uri::parse),
                )
            }.getOrNull()
            _systemLogos.update { it + (systemShort to art?.logo) }
        }
    }

    /** The cartridge or disc of one game, for the system chart's backdrop. Null when unscraped. */
    private val _physicalMedia = MutableStateFlow<Map<String, Uri?>>(emptyMap())
    val physicalMedia: StateFlow<Map<String, Uri?>> = _physicalMedia.asStateFlow()

    fun ensurePhysicalMedia(game: GameStat) {
        val key = artKey(game)
        if (key in _physicalMedia.value) return
        viewModelScope.launch(Dispatchers.IO) {
            val folder = settingsRepository.esdeFolderUri.firstOrNull() ?: return@launch
            val fsRoot = (if (EsdeFsAccess.isGranted()) EsdeFsAccess.rootPath(Uri.parse(folder)) else null)
                ?: return@launch
            val uri = runCatching {
                artworkResolver.physicalMediaFor(fsRoot, game.system, game.romPath)
            }.getOrNull()
            _physicalMedia.update { it + (key to uri) }
        }
    }

    /** Resolve marquee + hero art for the ranked games (podium cards and rows). */
    fun ensureGameMedia(games: List<GameStat>) {
        viewModelScope.launch(Dispatchers.IO) {
            val folder = settingsRepository.esdeFolderUri.firstOrNull() ?: return@launch
            val missing = games.filter { artKey(it) !in _gameMedia.value }
            if (missing.isEmpty()) return@launch
            val resolved = missing.mapNotNull { game ->
                runCatching { artworkResolver.resolve(Uri.parse(folder), game.system, game.romPath) }
                    .getOrNull()?.let { artKey(game) to it }
            }.toMap()
            if (resolved.isNotEmpty()) _gameMedia.value = _gameMedia.value + resolved
        }
    }

    /** Open the per-game activity log for one ranked game. Media streams in after the numbers. */
    fun openDetail(game: GameStat) {
        val key = GameStatsCalc.gameKey(game.system, game.romPath)
        val entry = latestEntries.firstOrNull { GameStatsCalc.gameKey(it.system, it.romPath) == key }
        val fmt = DateTimeFormatter.ofPattern("d MMM yyyy")

        _detail.value = GameDetail(
            stat = game,
            systemDisplayName = esdeSystemDisplayName(game.system, game.system),
            favorite = entry?.favorite == true,
            genre = entry?.genre,
            releaseYear = entry?.releaseYear,
            lastPlayedLabel = GameStatsCalc.lastPlayedDay(entry?.lastPlayed)?.format(fmt),
        )
        // Full media (hero, logo, box art) after the numbers are already on screen.
        viewModelScope.launch(Dispatchers.IO) {
            val folder = settingsRepository.esdeFolderUri.firstOrNull() ?: return@launch
            val media = runCatching {
                artworkResolver.resolve(Uri.parse(folder), game.system, game.romPath)
            }.getOrNull() ?: return@launch
            _detail.value = _detail.value
                ?.takeIf { it.stat == game }
                ?.copy(media = media)
        }
    }

    fun closeDetail() { _detail.value = null }
}
