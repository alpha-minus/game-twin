package com.twinlore.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "steam_game_cache")
data class SteamGameCacheEntity(
    @PrimaryKey
    val vaultId: String,
    val steamAppId: Int,
    val jsonSnapshot: String,
    val lastSyncedAt: Long,
    /** Unlocked achievements count (drawer write). */
    @ColumnInfo(defaultValue = "0") val numUnlocked: Int = 0,
    /** Total achievements (drawer write). */
    @ColumnInfo(defaultValue = "0") val numAchievements: Int = 0,
)
