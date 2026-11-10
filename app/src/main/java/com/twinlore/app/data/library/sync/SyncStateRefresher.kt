package com.twinlore.app.data.library.sync

import com.twinlore.app.data.local.VaultDatabase
import com.twinlore.app.data.local.VaultSyncStateEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs at snapshot-save time (inside LibraryAutoSaveObserver.saveNow, before
 * the WAL checkpoint): recomputes every vault's fingerprint, bumps
 * lastChangedAt for vaults whose fingerprint changed (or are new), drops sync
 * state for vanished vaults (belt-and-braces — the FK cascade usually already
 * removed them), prunes tombstones older than 180 days.
 */
@Singleton
class SyncStateRefresher @Inject constructor(
    private val calculator: VaultFingerprintCalculator,
) {
    suspend fun refresh(database: VaultDatabase, nowMillis: Long) {
        val fresh = calculator.computeAll(database)
        val stateDao = database.vaultSyncStateDao()
        val previous = stateDao.getAll().associateBy { it.vaultId }
        val upserts = fresh.map { (vaultId, fp) ->
            val prev = previous[vaultId]
            if (prev != null && prev.fingerprint == fp) prev
            else VaultSyncStateEntity(vaultId, fp, nowMillis)
        }
        stateDao.upsertAll(upserts)
        val vanished = previous.keys - fresh.keys
        if (vanished.isNotEmpty()) stateDao.deleteByVaultIds(vanished.toList())
        database.syncTombstoneDao().pruneOlderThan(nowMillis - TOMBSTONE_RETENTION_MILLIS)
    }

    companion object {
        const val TOMBSTONE_RETENTION_MILLIS: Long = 180L * 24 * 60 * 60 * 1000
    }
}
