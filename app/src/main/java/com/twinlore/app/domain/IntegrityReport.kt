package com.twinlore.app.domain

import com.twinlore.app.data.local.ResourceFileEntity

/**
 * Result of one library integrity audit. `perVault` is sorted alphabetically
 * by vault name. Vaults whose entire resource_files set is missing have
 * [VaultIssueGroup.willBeDeleted] = true and will be cascade-deleted on
 * Remove. `orphans` collects misses with vault_id == null (e.g. files saved
 * to `_unfiled/` by the browser).
 *
 * `unreadable` is deliberately NOT part of `perVault`/`orphans`/`totalMissing`:
 * those rows didn't resolve because the app currently holds no SAF grant covering
 * them (the state every reinstall starts in), not because the file went away. They
 * are reported so the user understands why their library looks thin, and are never
 * offered for removal -- re-granting the folder brings them back on its own.
 */
data class IntegrityReport(
    val perVault: List<VaultIssueGroup>,
    val orphans: List<ResourceFileEntity>,
    val totalMissing: Int,
    val unreadable: List<ResourceFileEntity> = emptyList()
)

data class VaultIssueGroup(
    val vaultId: String,
    val vaultName: String,
    val totalResourceCount: Int,
    val missing: List<ResourceFileEntity>
) {
    val willBeDeleted: Boolean
        get() = totalResourceCount > 0 && missing.size == totalResourceCount
}

sealed class LibraryIntegrityState {
    object Idle : LibraryIntegrityState()
    object Checking : LibraryIntegrityState()
    object Healthy : LibraryIntegrityState()
    data class Issues(val report: IntegrityReport) : LibraryIntegrityState()
}
