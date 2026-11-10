package com.twinlore.app.ui.screens.stats

import com.twinlore.app.data.esde.GamelistEntry
import java.time.LocalDate

/** Human play time from seconds: "47m", "3h 12m", "2d 4h 32m". Pure, for the metadata line. */
fun formatPlayTime(seconds: Long): String {
    val d = seconds / 86_400
    val h = (seconds % 86_400) / 3600
    val m = (seconds % 3600) / 60
    return when {
        d > 0 -> "${d}d ${h}h ${m}m"
        h > 0 -> "${h}h ${m}m"
        m > 0 -> "${m}m"
        // A real but sub-minute time must not read as "0m" (looks broken next to "1 play").
        seconds > 0 -> "<1m"
        else -> "0m"
    }
}

/** One ranked game on the stats screen. */
data class GameStat(
    val system: String,
    val romPath: String,
    val name: String,
    val playTimeSeconds: Long,
    val timesPlayed: Int,
    /** Scraped genre (possibly combined, e.g. "Action / Platformer"), if present. */
    val genre: String? = null,
    /** Release year from the scraped `<releasedate>` stamp, if plausible. */
    val releaseYear: Int? = null,
)

/** Headline numbers for the tiles row. */
data class StatsTotals(
    val totalPlaySeconds: Long,
    val timesPlayed: Int,
    val gamesPlayed: Int,
    val avgSessionSeconds: Long,
)

/** One bar of a chart. */
data class ChartBucket(val label: String, val seconds: Long)

/** One genre slice, ordered by weight. */
data class GenreShare(val genre: String, val weightSeconds: Long, val fraction: Float)

/**
 * The player's relationship with gaming eras, from scraped `<releasedate>` stamps:
 * the release year their play time gravitates to, and how old their games are on
 * average — both weighted by play time so a 20-hour love affair counts for more
 * than a 2-minute detour.
 */
data class EraStats(
    val goldenYear: Int? = null,
    val avgGameAgeYears: Int? = null,
    /** Games that carried a release year and a play signal — the basis of the numbers. */
    val gamesCounted: Int = 0,
    /** Which set of games [gamesCounted] refers to, so the screen can say so out loud. */
    val basis: EraBasis = EraBasis.ALL_PLAYED,
)

/**
 * Which games the era numbers were computed from.
 *
 * A library is full of games opened once and abandoned, and each of those is a release year
 * voting on the answer. Preferring the ones actually played for a while makes the claim mean
 * something — but only when there are enough of them to have a median at all.
 */
enum class EraBasis {
    /** Only games played for more than [GameStatsCalc.SUBSTANTIAL_PLAY_SECONDS]. */
    SUBSTANTIAL,

    /** Every game with any play signal, because too few passed the hour. */
    ALL_PLAYED,
}

/**
 * Pure aggregation for the play-stats screen, fed EXCLUSIVELY by ES-DE's own
 * gamelist files (`playcount`, `lastplayed`, `gametime` when the setup writes it,
 * `genre`, `releasedate`). The app deliberately records nothing of its own — no
 * sessions, no history — so every number here is one the user's ES-DE data can
 * honestly back.
 */
object GameStatsCalc {

    /** Assumed session length when weighing games that only have a play count. */
    private const val ASSUMED_SESSION_SECONDS = 1800L

    private fun baseName(path: String): String =
        path.substringAfterLast('/').lowercase()

    /** Identity used to match gamelist entries and artwork for one game. */
    fun gameKey(system: String, romPath: String): String =
        "${system.lowercase()}|${baseName(romPath)}"

    /**
     * How much one game "counts": real play time when the gamelist carries it,
     * else play count at an assumed half hour per launch. Zero for never-played games.
     */
    private fun playWeight(entry: GamelistEntry): Long =
        maxOf(entry.playTimeSeconds ?: 0L, entry.playCount * ASSUMED_SESSION_SECONDS)

    /**
     * Every game with a play signal, in gamelist order. The screen needs this set three times
     * over (ranking, totals, per-system bars); deriving it once and passing it around keeps a
     * ten-thousand-entry library to a single pass instead of three, and only the ranking pays
     * for a sort.
     */
    fun playedGames(entries: List<GamelistEntry>): List<GameStat> =
        entries.mapNotNull { entry ->
            val time = entry.playTimeSeconds ?: 0L
            if (time <= 0 && entry.playCount <= 0) return@mapNotNull null
            GameStat(
                system = entry.system,
                romPath = entry.romPath,
                name = entry.name ?: baseName(entry.romPath),
                playTimeSeconds = time,
                timesPlayed = entry.playCount,
                genre = entry.genre,
                releaseYear = entry.releaseYear,
            )
        }

    fun topGames(entries: List<GamelistEntry>, limit: Int = 10): List<GameStat> =
        rank(playedGames(entries), limit)

    /** [topGames] over an already-derived [playedGames] list. */
    fun rank(played: List<GameStat>, limit: Int = 10): List<GameStat> =
        played
            .sortedWith(compareByDescending<GameStat> { it.playTimeSeconds }.thenByDescending { it.timesPlayed })
            .take(limit)

    fun totals(entries: List<GamelistEntry>): StatsTotals = totalsOf(playedGames(entries))

    /** [totals] over an already-derived [playedGames] list. */
    fun totalsOf(played: List<GameStat>): StatsTotals {
        val totalPlay = played.sumOf { it.playTimeSeconds }
        val timesPlayed = played.sumOf { it.timesPlayed }
        return StatsTotals(
            totalPlaySeconds = totalPlay,
            timesPlayed = timesPlayed,
            gamesPlayed = played.size,
            avgSessionSeconds = if (timesPlayed > 0) totalPlay / timesPlayed else 0L,
        )
    }

    /** ES-DE `lastplayed` stamp ("20260730T105702") → date, or null if malformed. */
    fun lastPlayedDay(stamp: String?): LocalDate? {
        val digits = stamp?.takeIf { it.length >= 8 }?.substring(0, 8) ?: return null
        return runCatching {
            LocalDate.of(digits.substring(0, 4).toInt(), digits.substring(4, 6).toInt(), digits.substring(6, 8).toInt())
        }.getOrNull()
    }

    /** All-time play time per system, largest first. */
    fun systemBuckets(entries: List<GamelistEntry>, limit: Int = 6): List<ChartBucket> =
        systemBucketsOf(playedGames(entries), limit)

    /** [systemBuckets] over an already-derived [playedGames] list. */
    fun systemBucketsOf(played: List<GameStat>, limit: Int = 6): List<ChartBucket> =
        played
            .groupBy { it.system.lowercase() }
            .map { (system, list) -> ChartBucket(system, list.sumOf { it.playTimeSeconds }) }
            .filter { it.seconds > 0 }
            .sortedByDescending { it.seconds }
            .take(limit)

    /**
     * Genre popularity, weighted per game by real play time when known, else by
     * play count at an assumed half hour per launch. Combined genres split their
     * weight between the parts so totals stay honest.
     */
    fun genreShares(entries: List<GamelistEntry>, maxGenres: Int = 5): List<GenreShare> {
        val weights = LinkedHashMap<String, Long>() // lowercase genre -> weight
        val display = HashMap<String, String>()     // lowercase genre -> first-seen casing

        for (entry in entries) {
            val genres = entry.genre.orEmpty()
                .split('/', ',', ';')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            if (genres.isEmpty()) continue
            val weight = playWeight(entry)
            if (weight <= 0) continue
            val perGenre = weight / genres.size
            for (g in genres) {
                val k = g.lowercase()
                display.putIfAbsent(k, g)
                weights[k] = (weights[k] ?: 0L) + perGenre
            }
        }
        if (weights.isEmpty()) return emptyList()

        val total = weights.values.sum().coerceAtLeast(1L)
        val ranked = weights.entries.sortedByDescending { it.value }
        val top = ranked.take(maxGenres).map {
            GenreShare(display.getValue(it.key), it.value, it.value.toFloat() / total)
        }
        val rest = ranked.drop(maxGenres).sumOf { it.value }
        return if (rest > 0) top + GenreShare("Other", rest, rest.toFloat() / total) else top
    }

    /** Play time above which a game counts as one the player actually played, not sampled. */
    const val SUBSTANTIAL_PLAY_SECONDS = 3_600L

    /** Fewer qualifying games than this and a median of them would be noise, not a finding. */
    private const val MIN_SUBSTANTIAL_GAMES = 3

    /**
     * Golden year and average game age from scraped release dates, weighted by [playWeight].
     *
     * The golden year is the play-time-weighted MEDIAN release year — half the play time sits at
     * or before it — so one obsessively played outlier cannot drag the era claim to its own year.
     * The average age is the weighted mean of (current year - release year).
     *
     * Games played for more than an hour are preferred over every game with a play signal: a
     * library is full of titles opened once and abandoned, and each of those is a release year
     * voting on the answer. When too few clear the hour there is no median worth having, so the
     * whole played set is used instead and [EraStats.basis] records which happened — the screen
     * states it rather than presenting one as the other.
     */
    fun eraStats(entries: List<GamelistEntry>, currentYear: Int): EraStats {
        val dated = ArrayList<Pair<Int, Long>>() // (release year, weight)
        for (entry in entries) {
            val year = entry.releaseYear ?: continue
            val weight = playWeight(entry)
            if (weight <= 0) continue
            dated += year to weight
        }
        if (dated.isEmpty()) return EraStats()

        val substantial = dated.filter { it.second > SUBSTANTIAL_PLAY_SECONDS }
        val useSubstantial = substantial.size >= MIN_SUBSTANTIAL_GAMES
        val perGame = if (useSubstantial) substantial else dated

        var totalWeight = 0L
        var weightedAge = 0.0
        for ((year, weight) in perGame) {
            totalWeight += weight
            weightedAge += weight.toDouble() * (currentYear - year)
        }
        if (totalWeight <= 0) return EraStats()

        var cumulative = 0L
        var golden = perGame.first().first
        for ((year, weight) in perGame.sortedBy { it.first }) {
            cumulative += weight
            if (cumulative * 2 >= totalWeight) {
                golden = year
                break
            }
        }
        return EraStats(
            goldenYear = golden,
            avgGameAgeYears = Math.round(weightedAge / totalWeight).toInt(),
            gamesCounted = perGame.size,
            basis = if (useSubstantial) EraBasis.SUBSTANTIAL else EraBasis.ALL_PLAYED,
        )
    }
}
