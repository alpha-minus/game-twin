package com.twinlore.app.journey.domain

import com.twinlore.app.journey.domain.model.BoardAchievement
import com.twinlore.app.journey.domain.model.GameSnapshot
import com.twinlore.app.journey.domain.model.JourneyMode

/**
 * The full badge list of a single-game journey's game, for the achievements grid the player can
 * open over the chart.
 *
 * The data is already in hand and was being thrown away: the rebuild reads snapshots whose
 * `achievements` carry EVERY achievement with a nullable earn date, and [JourneyBuilder] keeps only
 * the ones with a date on them - which is the right frame for a chart of what somebody did, and the
 * wrong list for a grid of what a game has.
 *
 * Empty for a span, which is about many games and has no single achievement list to be a grid of,
 * and empty for a game nothing was fetched for. Never a guess: an unknown id gives nothing rather
 * than another game's badges.
 */
object BoardAchievements {

    fun forGame(
        mode: JourneyMode,
        gameId: Int?,
        snapshots: List<GameSnapshot>,
    ): List<BoardAchievement> {
        if (mode != JourneyMode.GAME || gameId == null) return emptyList()
        val snapshot = snapshots.firstOrNull { it.gameId == gameId } ?: return emptyList()
        return snapshot.achievements.map {
            // Either date is an earn. Hardcore is the stricter mode of the same unlock, not a third
            // state, and a badge earned only in hardcore is emphatically not locked.
            BoardAchievement(
                badgeName = it.badgeName,
                earned = it.dateEarned != null || it.dateEarnedHardcore != null,
                // Carried whether or not it was earned: what the board needs it for is the total
                // the game is worth, which is the whole list. See [BoardAchievement.points].
                points = it.points,
                hardcore = it.dateEarnedHardcore != null,
            )
        }
    }
}
