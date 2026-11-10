package com.twinlore.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cache entry for RetroAchievements game progress (achievements, unlocks, rarity).
 * TTL 15 minutes for the heavy `jsonSnapshot`; keyed by vaultId.
 * Bulk sync may insert rows with jsonSnapshot=null (counts only) for vaults whose
 * drawer has never been opened — see AchievementSyncCoordinator.
 */
@Entity(tableName = "ra_game_cache")
data class RaGameCacheEntity(
    @PrimaryKey
    val vaultId: String,
    val raGameId: Int,
    /** JSON snapshot of game + user progress. Null when only count columns are populated (bulk-only row). */
    val jsonSnapshot: String?,
    val lastSyncedAt: Long,
    /** Highest award kind from API_GetUserCompletionProgress (mastered, completed, beaten-hardcore, beaten-softcore). */
    val highestAwardKind: String? = null,
    /** User's rank by points for this game (from API_GetUserGameRankAndScore). Persisted so cached progress shows correct rank. */
    val userRank: Int? = null,
    /** Softcore unlocks (bulk sync + drawer write). */
    @ColumnInfo(defaultValue = "0") val numAwardedSoft: Int = 0,
    /** Hardcore unlocks (bulk sync + drawer write). */
    @ColumnInfo(defaultValue = "0") val numAwardedHardcore: Int = 0,
    /** Total achievements in the game (bulk sync + drawer write). */
    @ColumnInfo(defaultValue = "0") val numAchievements: Int = 0,
)
