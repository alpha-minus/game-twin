package com.twinlore.app.journey.domain.model

enum class AchievementType { MISSABLE, PROGRESSION, WIN_CONDITION, STANDARD }
/**
 * The four awards RetroAchievements grades a game with, in the site's own terms.
 *
 * [BEATEN_CASUAL] is `beaten-softcore`: beaten without hardcore mode. The journey used to fold it
 * into [BEATEN] and draw the pair as one filled silver disc, which lost the distinction the rest
 * of this app has always drawn (see `AchievementVisuals.HighestAwardIcon`) and the site's own
 * progression row makes - a hollow silver circle.
 */
enum class AwardKind { BEATEN_CASUAL, BEATEN, COMPLETED, MASTERED }
enum class JourneyMode { GAME, SPAN }

data class Award(val kind: AwardKind, val whenMillis: Long)

/**
 * How many awards of each kind a readout has counted, so a strip can say which awards a journey
 * came to rather than only how many.
 *
 * One value rather than a `Map<AwardKind, Int>` because every reader wants all three (and wants
 * them in a fixed order), and because a missing key and a zero are the same fact here: an award
 * kind nobody earned is a kind the breakdown leaves out.
 */
data class AwardTally(
    val mastered: Int = 0,
    val completed: Int = 0,
    val beaten: Int = 0,
    val beatenCasual: Int = 0,
) {

    operator fun get(kind: AwardKind): Int = when (kind) {
        AwardKind.MASTERED -> mastered
        AwardKind.COMPLETED -> completed
        AwardKind.BEATEN -> beaten
        AwardKind.BEATEN_CASUAL -> beatenCasual
    }

    /** Every award counted, which is what the single "Awards N" counter used to say on its own. */
    val total: Int get() = mastered + completed + beaten + beatenCasual

    companion object {
        /** The tally of [kinds], however they arrive. */
        fun of(kinds: Iterable<AwardKind>): AwardTally {
            var mastered = 0
            var completed = 0
            var beaten = 0
            var beatenCasual = 0
            kinds.forEach {
                when (it) {
                    AwardKind.MASTERED -> mastered++
                    AwardKind.COMPLETED -> completed++
                    AwardKind.BEATEN -> beaten++
                    AwardKind.BEATEN_CASUAL -> beatenCasual++
                }
            }
            return AwardTally(mastered, completed, beaten, beatenCasual)
        }
    }
}

data class GameRef(
    val id: Int,
    val title: String,
    val iconPath: String?,          // e.g. "/Images/012345.png" (media-base relative)
    val artPaths: List<String>,     // ordered backdrop candidates, media-base relative
    val colorIndex: Int,            // index into the series palette
    val players: Int,
    val award: Award?,
    // The two candidates worth telling apart by name. [artPaths] is an ordered list built for the
    // backdrop, which wants the in-game screenshot first; a fact card wants the title screen, and
    // recovering "which of these five is the title screen" by parsing the list back apart is not
    // something a caller should have to do. Null when the game has neither.
    val titlePath: String? = null,
    val boxArtPath: String? = null,
    /** The in-game screenshot by name; the export-background picker offers it explicitly. */
    val ingamePath: String? = null,
    /**
     * How many achievements the game has in total, earned or not - the denominator the single-game
     * strip's "x of N" counter divides by.
     *
     * Defaults to 0, which means "nobody knows" rather than "none": a snapshot cached before this
     * field existed deserializes without it, and the counter then shows the earned count on its own
     * instead of dividing by a zero it made up.
     */
    val totalAchievements: Int = 0,
    /**
     * How many points the game is worth in total, earned or not - the denominator the single-game
     * header's points line divides by.
     *
     * Defaults to 0, meaning "nobody knows" rather than "worth nothing": a snapshot cached before
     * this field existed deserializes without it, and the header then falls back to the running
     * total it divided by before this field existed (see `JourneyStripText.gameHeader`) rather than
     * printing "of 0". Computed the same way [totalAchievements] is, off the snapshot's full
     * achievement list rather than a second fetch - `AchSnapshot.points` is already carried per
     * achievement for [com.twinlore.app.journey.domain.BoardAchievements]' own board, so summing it
     * here costs nothing that was not already in hand.
     */
    val totalPoints: Int = 0,
    /**
     * How long this player has had the game open, in seconds, or null when nothing knows.
     *
     * See [GameSnapshot.userTotalPlaytimeSeconds]: null means "unknown", never "none played", and
     * the one surface that says it (the single-game header at the end of a journey) drops that half
     * of its line rather than printing a zero.
     */
    val playtimeSeconds: Int? = null,
)

data class Unlock(
    val id: Int,
    val title: String,
    val description: String,
    val points: Int,
    val badgeName: String,
    val type: AchievementType,
    val earnedAtMillis: Long,
    val numAwarded: Int,
    val gameId: Int,
    /**
     * Whether this achievement carries a hardcore earn date, in which case [earnedAtMillis] is that
     * date. Per achievement rather than per chart: the journey always includes everything the player
     * earned in either mode, so "was this one hardcore" is the only honest question left, and it is
     * what picks the crowd median a tooltip quotes.
     */
    val hardcore: Boolean = false,
)

/**
 * One badge as the achievements grid draws it: which art, and whether this player has it.
 *
 * The journey's own [Unlock] is not this, and cannot be: a journey is what the player earned, so
 * its list has no locked entries at all. The grid is about the game rather than about the run, and
 * a grid of only the badges somebody already has says nothing about how far there is left to go.
 */
data class BoardAchievement(
    val badgeName: String,
    val earned: Boolean,
    /**
     * What this achievement is worth, earned or not - the denominator of the board's points line
     * ("305 of 570 points"), which the RA drawer's own board gets from `p.totalPoints`.
     *
     * A journey has no such field to read: `GameRef` counts achievements and not their points, and
     * the player's unlocks only add up to what they have already taken. The full list is the only
     * thing that knows, and it is already being walked to build the grid.
     */
    val points: Int = 0,
    /** Whether this player took it in hardcore; see [com.twinlore.app.ui.components.AchievementsBoardBadge]. */
    val hardcore: Boolean = false,
)

data class Medal(
    val kind: AwardKind,
    val whenMillis: Long,
    val gameId: Int?,
    val ownerName: String?,         // null = main player
    val title: String,              // "Game Title" or "rival · Game Title"
)

data class PlayerJourney(
    val name: String,
    val unlocks: List<Unlock>,      // ascending by earnedAtMillis
    val games: Map<Int, GameRef>,
    val medals: List<Medal>,
    val failedFetches: Int = 0,
    val error: Boolean = false,
)

data class JourneyFrame(
    val mode: JourneyMode,
    val main: PlayerJourney,
    val rivals: List<PlayerJourney>,
    val spanFromMillis: Long,       // 0 = all time
    val spanToMillis: Long,
)
