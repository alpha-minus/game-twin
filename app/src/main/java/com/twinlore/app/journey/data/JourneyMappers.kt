package com.twinlore.app.journey.data

import com.twinlore.app.data.retroachievements.RaGameInfoAndUserProgressResponse
import com.twinlore.app.data.retroachievements.RaUserCompletionProgressResponse
import com.twinlore.app.journey.domain.JourneyParsing
import com.twinlore.app.journey.domain.model.AchSnapshot
import com.twinlore.app.journey.domain.model.CompletionRow
import com.twinlore.app.journey.domain.model.GameSnapshot

/** Maps RA API DTOs (Task-1's [RaDtos][com.twinlore.app.data.retroachievements]) onto the journey's trimmed cache snapshots. */
object JourneyMappers {

    fun toGameSnapshot(dto: RaGameInfoAndUserProgressResponse, fallbackIconPath: String?): GameSnapshot {
        val achievements = (dto.achievements ?: emptyMap()).values
            .mapNotNull { ach ->
                val id = ach.id ?: return@mapNotNull null
                AchSnapshot(
                    id = id,
                    title = ach.title ?: "",
                    description = ach.description ?: "",
                    points = ach.points ?: 0,
                    badgeName = ach.badgeName ?: "",
                    type = ach.type ?: ach.typePascal,
                    dateEarned = ach.dateEarned,
                    dateEarnedHardcore = ach.dateEarnedHardcore,
                    numAwarded = ach.numAwarded ?: 0,
                )
            }
            .sortedBy { it.id }

        return GameSnapshot(
            gameId = dto.id ?: 0,
            title = dto.title ?: "Unknown game",
            iconPath = JourneyParsing.artPath(dto.imageIcon) ?: JourneyParsing.artPath(fallbackIconPath),
            artPaths = JourneyParsing.artList(
                dto.imageIngame, dto.imageTitle, dto.imageBoxArt, dto.imageIcon, fallbackIconPath
            ),
            players = dto.numDistinctPlayers ?: dto.numDistinctPlayersCasual ?: 0,
            achievements = achievements,
            // Kept alongside artPaths rather than picked back out of it: the list is ordered for
            // the backdrop, and the fact cards want the title screen by name.
            titlePath = JourneyParsing.artPath(dto.imageTitle),
            boxArtPath = JourneyParsing.artPath(dto.imageBoxArt),
            ingamePath = JourneyParsing.artPath(dto.imageIngame),
            // Seconds, as the API sends it; the snapshot keeps the API's own unit and the header
            // formats it. See [GameSnapshot.userTotalPlaytimeSeconds].
            userTotalPlaytimeSeconds = dto.userTotalPlaytime,
        )
    }

    fun toCompletionRows(dto: RaUserCompletionProgressResponse): List<CompletionRow> =
        (dto.results ?: emptyList()).mapNotNull { entry ->
            val gameId = entry.gameId ?: return@mapNotNull null
            CompletionRow(
                gameId = gameId,
                title = entry.title ?: "",
                iconPath = entry.imageIcon,
                numAwarded = entry.numAwarded ?: 0,
                numAwardedHardcore = entry.numAwardedHardcore ?: 0,
                mostRecentAwardedDate = entry.mostRecentAwardedDate,
                highestAwardKind = entry.highestAwardKind,
                highestAwardDate = entry.highestAwardDate,
            )
        }
}
