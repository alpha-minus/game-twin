package com.twinlore.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * Per-vault change tracking for cross-device sync. [fingerprint] is a SHA-256
 * over the vault row + its vault-scoped child rows, computed at snapshot-save
 * time by VaultFingerprintCalculator. [lastChangedAt] is bumped only when the
 * fingerprint changes between saves ("last session in which this vault
 * actually changed"). Travels inside the snapshot. NOT a source of truth for
 * cross-version comparison — merge recomputes fingerprints on both sides.
 */
@Entity(
    tableName = "vault_sync_state",
    foreignKeys = [
        ForeignKey(
            entity = VaultEntity::class,
            parentColumns = ["id"],
            childColumns = ["vaultId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class VaultSyncStateEntity(
    @PrimaryKey val vaultId: String,
    val fingerprint: String,
    val lastChangedAt: Long,
)
