package com.twinlore.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cached per-game Achievement Journey snapshot, keyed by (userName, gameId).
 *
 * [validatorJson] is a Gson snapshot of the CompletionRow for this game at
 * fetch time; callers compare it against the live CompletionRow to decide
 * whether the cached [payloadJson] (a GameSnapshot) is still valid before
 * serving it, avoiding a network refetch when nothing changed.
 */
@Entity(tableName = "journey_game_cache", primaryKeys = ["userName", "gameId"])
data class JourneyGameCacheEntity(
    val userName: String,
    val gameId: Int,
    val fetchedAt: Long,
    val validatorJson: String,
    val payloadJson: String,
)

/**
 * Cached Achievement Journey completion list for a user (Gson of
 * List<CompletionRow>), one row per userName.
 */
@Entity(tableName = "journey_completion_cache")
data class JourneyCompletionCacheEntity(
    @PrimaryKey val userName: String,
    val fetchedAt: Long,
    val payloadJson: String,
)
