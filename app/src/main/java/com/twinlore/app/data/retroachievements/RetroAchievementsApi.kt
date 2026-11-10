package com.twinlore.app.data.retroachievements

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Retrofit interface for RetroAchievements Web API.
 * Base URL: https://retroachievements.org/API/
 * Auth: pass u=username and y=apiKey on each request. Do not log credentials.
 */
interface RetroAchievementsApi {

    /**
     * Validate credentials and get user summary. Use for Settings validation.
     * API_GetUserSummary.php?u=username&y=apiKey
     */
    @GET("API_GetUserSummary.php")
    suspend fun getUserSummary(
        @Query("u") username: String,
        @Query("y") apiKey: String,
        @Query("g") recentGamesCount: Int = 0,
        @Query("a") recentAchievementsCount: Int = 0
    ): RaUserSummaryResponse

    /**
     * Get last played games (for title matching). Returns up to 15.
     * API_GetUserRecentlyPlayedGames.php?u=username&y=apiKey&c=count
     */
    @GET("API_GetUserRecentlyPlayedGames.php")
    suspend fun getRecentlyPlayedGames(
        @Query("u") username: String,
        @Query("y") apiKey: String,
        @Query("c") count: Int = 15
    ): List<RaRecentlyPlayedGame>

    /**
     * Get game info and user progress for a specific game (achievements, unlocks, rarity).
     * API_GetGameInfoAndUserProgress.php?u=username&y=apiKey&g=gameId&a=1 (a=1 includes user award dates).
     */
    @GET("API_GetGameInfoAndUserProgress.php")
    suspend fun getGameInfoAndUserProgress(
        @Query("u") username: String,
        @Query("y") apiKey: String,
        @Query("g") gameId: Int,
        @Query("a") includeUserAwards: Int = 1
    ): RaGameInfoAndUserProgressResponse

    /**
     * Get game summary only (to validate a manual Game ID).
     * API_GetGame.php?y=apiKey&i=gameId (official docs use 'i' for game ID).
     */
    @GET("API_GetGame.php")
    suspend fun getGame(
        @Query("y") apiKey: String,
        @Query("i") gameId: Int
    ): RaGameSummaryResponse

    /**
     * Get user's rank and score for a game (points leaderboard).
     * API_GetUserGameRankAndScore.php?u=username&y=apiKey&g=gameId
     * Returns array of one entry with UserRank, TotalScore, LastAward (empty if no progress).
     */
    @GET("API_GetUserGameRankAndScore.php")
    suspend fun getUserGameRankAndScore(
        @Query("u") username: String,
        @Query("y") apiKey: String,
        @Query("g") gameId: Int
    ): List<RaUserGameRankAndScoreEntry>

    /**
     * Get user completion progress (highest award per game).
     * API_GetUserCompletionProgress.php?u=username&y=apiKey&c=count&o=offset
     * Returns paginated list; use to resolve HighestAwardKind for a game.
     */
    @GET("API_GetUserCompletionProgress.php")
    suspend fun getUserCompletionProgress(
        @Query("u") username: String,
        @Query("y") apiKey: String,
        @Query("c") count: Int = 500,
        @Query("o") offset: Int = 0
    ): RaUserCompletionProgressResponse

    /**
     * Get extended game info including metadata (genre, developer, publisher, released)
     * and full achievements list with badge names.
     * API_GetGameExtended.php?y=apiKey&i=gameId
     */
    @GET("API_GetGameExtended.php")
    suspend fun getGameExtended(
        @Query("y") apiKey: String,
        @Query("i") gameId: Int
    ): RaGameExtendedResponse

    /**
     * Get the current Achievement of the Week.
     * API_GetAchievementOfTheWeek.php?y=apiKey
     * No username required — only the API key.
     */
    @GET("API_GetAchievementOfTheWeek.php")
    suspend fun getAchievementOfTheWeek(
        @Query("y") apiKey: String
    ): RaAchievementOfTheWeekResponse

    /**
     * Get game progression stats including median time to beat.
     * API_GetGameProgression.php?y=apiKey&i=gameId
     */
    @GET("API_GetGameProgression.php")
    suspend fun getGameProgression(
        @Query("y") apiKey: String,
        @Query("i") gameId: Int
    ): RaGameProgressionResponse

    /**
     * Get comments for an achievement.
     * API_GetComments.php?y=apiKey&t=2&i=achievementId&sort=-submitted
     * t: 1=game, 2=achievement, 3=user. sort: -submitted = newest first.
     */
    @GET("API_GetComments.php")
    suspend fun getAchievementComments(
        @Query("y") apiKey: String,
        @Query("t") type: Int = 2,
        @Query("i") achievementId: Int,
        @Query("sort") sort: String = "-submitted"
    ): RaCommentsResponse
}
