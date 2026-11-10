package com.twinlore.app.data.retroachievements

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName
import java.lang.reflect.Type

/**
 * RA's API_GetUserSummary returns "RecentAchievements" as a JSON object keyed by
 * game id — but as an empty ARRAY (`[]`) when there are none. Gson can't map `[]`
 * onto a Map and throws, which would fail the whole summary parse. Tolerate it:
 * non-objects (arrays, null) deserialize to an empty map.
 */
class RaRecentAchievementsAdapter : JsonDeserializer<Map<String, List<RaRecentAchievementEntry>>> {
    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext
    ): Map<String, List<RaRecentAchievementEntry>> {
        if (json == null || json.isJsonNull || !json.isJsonObject) return emptyMap()
        val out = LinkedHashMap<String, List<RaRecentAchievementEntry>>()
        for ((key, value) in json.asJsonObject.entrySet()) {
            if (!value.isJsonArray) continue
            out[key] = value.asJsonArray.mapNotNull {
                context.deserialize<RaRecentAchievementEntry>(it, RaRecentAchievementEntry::class.java)
            }
        }
        return out
    }
}

// --------------- User Summary (validation + profile) ---------------

/** Response of API_GetUserSummary.php. Uses PascalCase from API. */
data class RaUserSummaryResponse(
    @SerializedName("User") val user: String? = null,
    @SerializedName("MemberSince") val memberSince: String? = null,
    @SerializedName("TotalPoints") val totalPoints: Int? = null,
    @SerializedName("TotalSoftcorePoints") val totalSoftcorePoints: Int? = null,
    @SerializedName("TotalTruePoints") val totalTruePoints: Int? = null,
    @SerializedName("Rank") val rank: Int? = null,
    @SerializedName("RecentlyPlayed") val recentlyPlayed: List<RaRecentlyPlayedGame>? = null,
    @JsonAdapter(RaRecentAchievementsAdapter::class)
    @SerializedName("RecentAchievements") val recentAchievements: Map<String, List<RaRecentAchievementEntry>>? = null,
    @SerializedName("LastActivity") val lastActivity: RaLastActivity? = null,
    /** Most-recent game the user was active in, and its rich-presence ("now playing") string. */
    @SerializedName("LastGameID") val lastGameId: Int? = null,
    @SerializedName("RichPresenceMsg") val richPresenceMsg: String? = null
)

data class RaLastActivity(
    @SerializedName("ID") val id: Long? = null,
    @SerializedName("timestamp") val timestamp: String? = null,
    @SerializedName("lastupdate") val lastUpdate: String? = null,
    @SerializedName("activitytype") val activityType: Int? = null,
    @SerializedName("User") val user: String? = null,
    @SerializedName("data") val data: String? = null,
    @SerializedName("data2") val data2: String? = null
)

/** Entry in RecentAchievements map (key is game ID string). */
data class RaRecentAchievementEntry(
    @SerializedName("ID") val id: Int? = null,
    @SerializedName("Title") val title: String? = null,
    @SerializedName("Description") val description: String? = null,
    @SerializedName("Points") val points: Int? = null,
    @SerializedName("BadgeName") val badgeName: String? = null,
    @SerializedName("Date") val date: String? = null,
    @SerializedName("HardcoreAchieved") val hardcoreAchieved: Int? = null
)

// --------------- Recently played games ---------------

data class RaRecentlyPlayedGame(
    @SerializedName("GameID") val gameId: Int,
    @SerializedName("Title") val title: String? = null,
    @SerializedName("ConsoleID") val consoleId: Int? = null,
    @SerializedName("ConsoleName") val consoleName: String? = null,
    @SerializedName("ImageIcon") val imageIcon: String? = null,
    @SerializedName("LastPlayed") val lastPlayed: String? = null,
    @SerializedName("MyVote") val myVote: Double? = null,
    @SerializedName("NumPossibleAchievements") val numPossibleAchievements: Int? = null,
    @SerializedName("PossibleScore") val possibleScore: Int? = null,
    @SerializedName("NumAchieved") val numAchieved: Int? = null,
    @SerializedName("ScoreAchieved") val scoreAchieved: Int? = null,
    @SerializedName("PctWon") val pctWon: Double? = null
)

// --------------- Game summary (validate Game ID) ---------------

data class RaGameSummaryResponse(
    @SerializedName("ID") val id: Int? = null,
    @SerializedName("Title") val title: String? = null,
    @SerializedName("ConsoleID") val consoleId: Int? = null,
    @SerializedName("ConsoleName") val consoleName: String? = null,
    @SerializedName("ImageIcon") val imageIcon: String? = null,
    @SerializedName("ImageTitle") val imageTitle: String? = null,
    @SerializedName("ImageIngame") val imageIngame: String? = null,
    @SerializedName("ImageBoxArt") val imageBoxArt: String? = null,
    @SerializedName("NumAchievements") val numAchievements: Int? = null,
    @SerializedName("Points") val points: Int? = null
)

// --------------- Game info + user progress ---------------

data class RaGameInfoAndUserProgressResponse(
    @SerializedName("ID") val id: Int? = null,
    @SerializedName("Title") val title: String? = null,
    @SerializedName("ConsoleID") val consoleId: Int? = null,
    @SerializedName("ConsoleName") val consoleName: String? = null,
    @SerializedName("ImageIcon") val imageIcon: String? = null,
    @SerializedName("ImageTitle") val imageTitle: String? = null,
    @SerializedName("ImageIngame") val imageIngame: String? = null,
    @SerializedName("ImageBoxArt") val imageBoxArt: String? = null,
    @SerializedName("NumAchievements") val numAchievements: Int? = null,
    @SerializedName("Points") val points: Int? = null,
    @SerializedName("Achievements") val achievements: Map<String, RaAchievementDto>? = null,
    @SerializedName("UserProgress") val userProgress: RaUserProgressDto? = null,
    @SerializedName("NumAwardedToUser") val numAwardedToUser: Int? = null,
    @SerializedName("NumAwardedToUserHardcore") val numAwardedToUserHardcore: Int? = null,
    @SerializedName("UserTotalPlaytime") val userTotalPlaytime: Int? = null,
    @SerializedName("NumDistinctPlayers") val numDistinctPlayers: Int? = null,
    @SerializedName("NumDistinctPlayersCasual") val numDistinctPlayersCasual: Int? = null,
    @SerializedName("NumDistinctPlayersHardcore") val numDistinctPlayersHardcore: Int? = null,
    @SerializedName("UserCompletion") val userCompletion: String? = null,
    @SerializedName("UserCompletionHardcore") val userCompletionHardcore: String? = null
)

/** Single entry from API_GetUserGameRankAndScore.php (user's rank for a game). */
data class RaUserGameRankAndScoreEntry(
    @SerializedName("User") val user: String? = null,
    @SerializedName("UserRank") val userRank: Int? = null,
    @SerializedName("TotalScore") val totalScore: Int? = null,
    @SerializedName("LastAward") val lastAward: String? = null
)

/** Response of API_GetUserCompletionProgress.php (paginated). */
data class RaUserCompletionProgressResponse(
    @SerializedName("Count") val count: Int? = null,
    @SerializedName("Total") val total: Int? = null,
    @SerializedName("Results") val results: List<RaUserCompletionProgressEntry>? = null
)

/** One game in GetUserCompletionProgress results. */
data class RaUserCompletionProgressEntry(
    @SerializedName("GameID") val gameId: Int? = null,
    @SerializedName("Title") val title: String? = null,
    @SerializedName("ImageIcon") val imageIcon: String? = null,
    @SerializedName("ConsoleID") val consoleId: Int? = null,
    @SerializedName("ConsoleName") val consoleName: String? = null,
    @SerializedName("MaxPossible") val maxPossible: Int? = null,
    @SerializedName("NumAwarded") val numAwarded: Int? = null,
    @SerializedName("NumAwardedHardcore") val numAwardedHardcore: Int? = null,
    @SerializedName("MostRecentAwardedDate") val mostRecentAwardedDate: String? = null,
    @SerializedName("HighestAwardKind") val highestAwardKind: String? = null,
    @SerializedName("HighestAwardDate") val highestAwardDate: String? = null
)

data class RaUserProgressDto(
    @SerializedName("NumAchieved") val numAchieved: Int? = null,
    @SerializedName("NumAwarded") val numAwarded: Int? = null,
    @SerializedName("SoftcorePoints") val softcorePoints: Int? = null,
    @SerializedName("HardcorePoints") val hardcorePoints: Int? = null,
    @SerializedName("Achievements") val achievements: Map<String, RaUserAchievementDto>? = null
)

data class RaUserAchievementDto(
    @SerializedName("NumAwarded") val numAwarded: Int? = null,
    @SerializedName("DateAwarded") val dateAwarded: String? = null,
    @SerializedName("HardcoreAchieved") val hardcoreAchieved: Int? = null
)

/** Achievement in GameInfo response; key is achievement ID string. User progress is inline (DateEarned/DateEarnedHardcore). */
data class RaAchievementDto(
    @SerializedName("ID") val id: Int? = null,
    @SerializedName("Title") val title: String? = null,
    @SerializedName("Description") val description: String? = null,
    @SerializedName("Points") val points: Int? = null,
    @SerializedName("TrueRatio") val trueRatio: Int? = null,
    @SerializedName("BadgeName") val badgeName: String? = null,
    @SerializedName("NumAwarded") val numAwarded: Int? = null,
    @SerializedName("NumAwardedHardcore") val numAwardedHardcore: Int? = null,
    @SerializedName("DateEarned") val dateEarned: String? = null,
    @SerializedName("DateEarnedHardcore") val dateEarnedHardcore: String? = null,
    @SerializedName("DisplayOrder") val displayOrder: Int? = null,
    /** "progression", "win_condition", "missable". API may use "type" or "Type". */
    @SerializedName("type") val type: String? = null,
    @SerializedName("Type") val typePascal: String? = null,
    @SerializedName("Author") val author: String? = null
)

// --------------- Achievement of the Week ---------------

data class RaAchievementOfTheWeekResponse(
    @SerializedName("Achievement") val achievement: RaAotWAchievementDto? = null,
    @SerializedName("Console") val console: RaAotWConsoleDto? = null,
    @SerializedName("Game") val game: RaAotWGameDto? = null,
    @SerializedName("StartAt") val startAt: String? = null,
    @SerializedName("TotalPlayers") val totalPlayers: Int? = null,
    @SerializedName("UnlocksCount") val unlocksCount: Int? = null,
    @SerializedName("UnlocksHardcoreCount") val unlocksHardcoreCount: Int? = null
)

data class RaAotWAchievementDto(
    @SerializedName("ID") val id: Int? = null,
    @SerializedName("Title") val title: String? = null,
    @SerializedName("Description") val description: String? = null,
    @SerializedName("Points") val points: Int? = null,
    @SerializedName("TrueRatio") val trueRatio: Int? = null,
    @SerializedName("Author") val author: String? = null
)

data class RaAotWGameDto(
    @SerializedName("ID") val id: Int? = null,
    @SerializedName("Title") val title: String? = null
)

data class RaAotWConsoleDto(
    @SerializedName("ID") val id: Int? = null,
    @SerializedName("Title") val title: String? = null
)

// --------------- Game Extended (includes metadata + achievements) ---------------

data class RaGameExtendedResponse(
    @SerializedName("ID") val id: Int? = null,
    @SerializedName("Title") val title: String? = null,
    @SerializedName("ConsoleID") val consoleId: Int? = null,
    @SerializedName("ConsoleName") val consoleName: String? = null,
    @SerializedName("ImageIcon") val imageIcon: String? = null,
    @SerializedName("ImageTitle") val imageTitle: String? = null,
    @SerializedName("ImageIngame") val imageIngame: String? = null,
    @SerializedName("ImageBoxArt") val imageBoxArt: String? = null,
    @SerializedName("NumAchievements") val numAchievements: Int? = null,
    @SerializedName("Points") val points: Int? = null,
    @SerializedName("Genre") val genre: String? = null,
    @SerializedName("Developer") val developer: String? = null,
    @SerializedName("Publisher") val publisher: String? = null,
    @SerializedName("Released") val released: String? = null,
    @SerializedName("GuideURL") val guideUrl: String? = null,
    @SerializedName("Achievements") val achievements: Map<String, RaAchievementDto>? = null
)

// --------------- Achievement Comments ---------------

/** Response wrapper from API_GetComments.php. */
data class RaCommentsResponse(
    @SerializedName("Count") val count: Int? = null,
    @SerializedName("Total") val total: Int? = null,
    @SerializedName("Results") val results: List<RaCommentDto>? = null
)

/** Single comment entry. */
data class RaCommentDto(
    @SerializedName("User") val user: String? = null,
    @SerializedName("Submitted") val submitted: String? = null,
    @SerializedName("CommentText") val commentText: String? = null
)

// --------------- Game Progression ---------------

data class RaGameProgressionResponse(
    @SerializedName("ID") val id: Int? = null,
    @SerializedName("Title") val title: String? = null,
    @SerializedName("NumDistinctPlayers") val numDistinctPlayers: Int? = null,
    @SerializedName("MedianTimeToBeat") val medianTimeToBeat: Int? = null,
    @SerializedName("MedianTimeToBeatHardcore") val medianTimeToBeatHardcore: Int? = null,
    @SerializedName("MedianTimeToComplete") val medianTimeToComplete: Int? = null,
    @SerializedName("MedianTimeToMaster") val medianTimeToMaster: Int? = null,
    @SerializedName("Achievements") val achievements: List<RaProgressionAchievementDto>? = null
)

/** Per-achievement median unlock time, from API_GetGameProgression.php's "Achievements" array. */
data class RaProgressionAchievementDto(
    @SerializedName("ID") val id: Int? = null,
    @SerializedName("MedianTimeToUnlock") val medianTimeToUnlock: Int? = null,
    @SerializedName("MedianTimeToUnlockHardcore") val medianTimeToUnlockHardcore: Int? = null
)
