package com.twinlore.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Records a vault deletion so it propagates across devices instead of being
 * resurrected by the next merge. Travels inside the snapshot; merge unions
 * both sides keeping the newest [deletedAt]. Pruned after 180 days at save
 * time. Written ONLY by the explicit per-vault delete path — never by
 * deleteAllVaults() (library reset must not nuke other devices).
 */
@Entity(tableName = "sync_tombstones")
data class SyncTombstoneEntity(
    @PrimaryKey val vaultId: String,
    val deletedAt: Long,
    val deviceId: String,
)
