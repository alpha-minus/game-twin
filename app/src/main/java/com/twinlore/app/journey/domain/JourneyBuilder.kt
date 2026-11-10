package com.twinlore.app.journey.domain

import com.twinlore.app.journey.domain.model.AchievementType
import com.twinlore.app.journey.domain.model.AchSnapshot
import com.twinlore.app.journey.domain.model.Award
import com.twinlore.app.journey.domain.model.AwardKind
import com.twinlore.app.journey.domain.model.CompletionRow
import com.twinlore.app.journey.domain.model.GameRef
import com.twinlore.app.journey.domain.model.GameSnapshot
import com.twinlore.app.journey.domain.model.JourneyMode
import com.twinlore.app.journey.domain.model.Medal
import com.twinlore.app.journey.domain.model.PlayerJourney
import com.twinlore.app.journey.domain.model.Unlock

/**
 * Builds a journey out of fetched snapshots.
 *
 * There is one earning rule and no mode switch: an achievement counts as unlocked if the player
 * earned it at all, and the moment it landed is the hardcore date when there is one, the softcore
 * date otherwise. A hardcore earn is also a softcore earn on RetroAchievements, so this is the
 * complete set either way; what used to be a "hardcore" toggle only ever *hid* the softcore-only
 * unlocks, which is not a view of a journey anyone asked for.
 */
object JourneyBuilder {

    /** Candidate selection for span mode; ordered, uncapped. */
    fun pickSpanGames(
        completion: List<CompletionRow>,
        spanFromMillis: Long,
        spanToMillis: Long,
    ): List<CompletionRow> {
        val candidates = completion.mapNotNull { row ->
            val lastAward = JourneyParsing.parseWhen(row.mostRecentAwardedDate) ?: return@mapNotNull null
            if (lastAward < spanFromMillis) return@mapNotNull null
            // The softcore count is the superset: a game with any award at all is a candidate.
            if (row.numAwarded <= 0) return@mapNotNull null
            row to lastAward
        }
        val (inWindow, afterWindow) = candidates.partition { (_, lastAward) -> lastAward <= spanToMillis }
        val inWindowSorted = inWindow.sortedByDescending { (_, lastAward) -> lastAward }
        val afterWindowSorted = afterWindow.sortedBy { (_, lastAward) -> lastAward }
        return (inWindowSorted + afterWindowSorted).map { (row, _) -> row }
    }

    /** Snapshot -> every earned achievement, window filtered, ascending. GAME mode passes windowFilter=false. */
    /**
     * The in-game screenshot for [GameRef.ingamePath]: the snapshot's own field, or - for cache
     * entries written before that field existed - recovered from [GameSnapshot.artPaths], which
     * has always led with the in-game shot when the game has one. Without this, the Background
     * picker's "In-game shot" was silently empty for every game already in the cache.
     *
     * Recovered by POSITION, not by eliminating the named siblings. The list is built as
     * [ingame, title, box art, icon, the completion row's icon] with blanks and placeholders
     * dropped, and only the first four of those are stored by name - so a set difference had a
     * fifth candidate to fall through to, and a game with no in-game shot answered with the
     * completion row's icon as though it were one. Leading position is the whole signal: if the
     * head of the list is one of the named siblings then the in-game shot is simply absent.
     */
    private fun GameSnapshot.resolvedIngamePath(): String? =
        ingamePath ?: artPaths.firstOrNull()?.takeIf { it != titlePath && it != boxArtPath && it != iconPath }

    fun deriveUnlocks(
        snapshot: GameSnapshot,
        windowFilter: Boolean,
        spanFromMillis: Long,
        spanToMillis: Long,
    ): List<Unlock> {
        return snapshot.achievements.mapNotNull { ach ->
            val earnedAt = earnedMillis(ach) ?: return@mapNotNull null
            if (windowFilter && (earnedAt < spanFromMillis || earnedAt > spanToMillis)) return@mapNotNull null
            Unlock(
                id = ach.id,
                title = ach.title,
                description = ach.description,
                points = ach.points,
                badgeName = ach.badgeName,
                type = JourneyParsing.normType(ach.type),
                earnedAtMillis = earnedAt,
                numAwarded = ach.numAwarded,
                gameId = snapshot.gameId,
                hardcore = ach.dateEarnedHardcore != null,
            )
        }.sortedBy { it.earnedAtMillis }
    }

    /** GAME-mode milestones computed from a full snapshot (beaten/completed/mastered). */
    fun computeGameMilestones(snapshot: GameSnapshot): List<Pair<AwardKind, Long>> {
        val achievements = snapshot.achievements
        if (achievements.isEmpty()) return emptyList()

        val milestones = mutableListOf<Pair<AwardKind, Long>>()

        val typed = achievements.map { it to JourneyParsing.normType(it.type) }
        val prog = typed.filter { (_, type) -> type == AchievementType.PROGRESSION }.map { (a, _) -> a }
        val wins = typed.filter { (_, type) -> type == AchievementType.WIN_CONDITION }.map { (a, _) -> a }

        val progDone = prog.isEmpty() || prog.all { earnedMillis(it) != null }
        val winDone = if (wins.isEmpty()) prog.isNotEmpty() && progDone else wins.any { earnedMillis(it) != null }

        if (progDone && winDone && (prog.isNotEmpty() || wins.isNotEmpty())) {
            val progTimes = prog.mapNotNull { earnedMillis(it) }
            val winTimes = wins.mapNotNull { earnedMillis(it) }
            val latestProg = progTimes.maxOrNull()
            val earliestWin = winTimes.minOrNull()
            val beatenTime = maxOf(latestProg ?: Long.MIN_VALUE, earliestWin ?: Long.MIN_VALUE)
            if (beatenTime != Long.MIN_VALUE) {
                // Which beaten award it is, decided the same way the mastery below is: every
                // achievement that beat the game earned in hardcore is the site's `beaten-hardcore`,
                // anything short of that is `beaten-softcore` - the hollow silver circle.
                val beatenHardcore = (prog + wins).all { it.dateEarnedHardcore != null }
                milestones += (if (beatenHardcore) AwardKind.BEATEN else AwardKind.BEATEN_CASUAL) to beatenTime
            }
        }

        val allSoftcoreEarned = achievements.all { it.dateEarned != null || it.dateEarnedHardcore != null }
        val allHardcoreEarned = achievements.all { it.dateEarnedHardcore != null }

        if (allHardcoreEarned) {
            val time = achievements.mapNotNull { JourneyParsing.parseWhen(it.dateEarnedHardcore) }.maxOrNull()
            if (time != null) milestones += AwardKind.MASTERED to time
        } else if (allSoftcoreEarned) {
            val time = achievements.mapNotNull {
                JourneyParsing.parseWhen(it.dateEarned) ?: JourneyParsing.parseWhen(it.dateEarnedHardcore)
            }.maxOrNull()
            if (time != null) milestones += AwardKind.COMPLETED to time
        }

        return milestones.sortedBy { it.second }
    }

    /** Assemble one player's journey from snapshots (SPAN) or one snapshot (GAME). */
    fun buildPlayer(
        name: String,
        mode: JourneyMode,
        spanFromMillis: Long,
        spanToMillis: Long,
        snapshots: List<GameSnapshot>,
        completionByGame: Map<Int, CompletionRow>,
        failedFetches: Int,
    ): PlayerJourney {
        return when (mode) {
            JourneyMode.SPAN -> buildSpanPlayer(name, spanFromMillis, spanToMillis, snapshots, completionByGame, failedFetches)
            JourneyMode.GAME -> buildGamePlayer(name, snapshots, failedFetches)
        }
    }

    private fun buildSpanPlayer(
        name: String,
        spanFromMillis: Long,
        spanToMillis: Long,
        snapshots: List<GameSnapshot>,
        completionByGame: Map<Int, CompletionRow>,
        failedFetches: Int,
    ): PlayerJourney {
        val games = mutableMapOf<Int, GameRef>()
        val allUnlocks = mutableListOf<Unlock>()
        val medals = mutableListOf<Medal>()
        var colorIndex = 0

        for (snapshot in snapshots) {
            val unlocks = deriveUnlocks(snapshot, windowFilter = true, spanFromMillis, spanToMillis)
            if (unlocks.isEmpty()) continue

            val row = completionByGame[snapshot.gameId]
            val awardKind = row?.let { JourneyParsing.parseAwardKind(it.highestAwardKind) }
            val awardWhen = row?.let { JourneyParsing.parseWhen(it.highestAwardDate) }
            val award = if (awardKind != null && awardWhen != null) Award(awardKind, awardWhen) else null

            games[snapshot.gameId] = GameRef(
                id = snapshot.gameId,
                title = snapshot.title,
                iconPath = snapshot.iconPath,
                artPaths = snapshot.artPaths,
                colorIndex = colorIndex % 12,
                players = snapshot.players,
                award = award,
                titlePath = snapshot.titlePath,
                boxArtPath = snapshot.boxArtPath,
                ingamePath = snapshot.resolvedIngamePath(),
                totalAchievements = snapshot.achievements.size,
                totalPoints = snapshot.achievements.sumOf { it.points },
                playtimeSeconds = snapshot.userTotalPlaytimeSeconds,
            )
            colorIndex++

            allUnlocks += unlocks

            if (award != null && award.whenMillis in spanFromMillis..spanToMillis) {
                medals += Medal(
                    kind = award.kind,
                    whenMillis = award.whenMillis,
                    gameId = snapshot.gameId,
                    ownerName = null,
                    title = snapshot.title,
                )
            }
        }

        allUnlocks.sortBy { it.earnedAtMillis }

        return PlayerJourney(
            name = name,
            unlocks = allUnlocks,
            games = games,
            medals = medals,
            failedFetches = failedFetches,
        )
    }

    private fun buildGamePlayer(
        name: String,
        snapshots: List<GameSnapshot>,
        failedFetches: Int,
    ): PlayerJourney {
        val snapshot = snapshots.firstOrNull()
            ?: return PlayerJourney(name = name, unlocks = emptyList(), games = emptyMap(), medals = emptyList(), failedFetches = failedFetches)

        val unlocks = deriveUnlocks(snapshot, windowFilter = false, 0, 0)

        val games = mapOf(
            snapshot.gameId to GameRef(
                id = snapshot.gameId,
                title = snapshot.title,
                iconPath = snapshot.iconPath,
                artPaths = snapshot.artPaths,
                colorIndex = 0,
                players = snapshot.players,
                award = null,
                titlePath = snapshot.titlePath,
                boxArtPath = snapshot.boxArtPath,
                ingamePath = snapshot.resolvedIngamePath(),
                totalAchievements = snapshot.achievements.size,
                totalPoints = snapshot.achievements.sumOf { it.points },
                playtimeSeconds = snapshot.userTotalPlaytimeSeconds,
            )
        )

        val medals = computeGameMilestones(snapshot).map { (kind, whenMillis) ->
            Medal(kind = kind, whenMillis = whenMillis, gameId = snapshot.gameId, ownerName = null, title = snapshot.title)
        }

        return PlayerJourney(
            name = name,
            unlocks = unlocks,
            games = games,
            medals = medals,
            failedFetches = failedFetches,
        )
    }

    /**
     * When an achievement landed: its hardcore date if it has one, its softcore date otherwise.
     * "If hardcore exists, that version" - a hardcore earn is the same achievement won the harder
     * way, and it is the timestamp worth putting on the chart.
     *
     * Both dates are parsed rather than picking the string first: a hardcore field the API sent in
     * a shape [JourneyParsing.parseWhen] does not know would otherwise drop an unlock the player
     * has plainly earned, on the strength of the very field that says they earned it twice.
     */
    private fun earnedMillis(ach: AchSnapshot): Long? =
        JourneyParsing.parseWhen(ach.dateEarnedHardcore) ?: JourneyParsing.parseWhen(ach.dateEarned)
}
