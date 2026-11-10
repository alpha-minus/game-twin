package com.twinlore.app.data.license

/**
 * One-time migration from the pre-0.7.x license model to the premium
 * features model. Removes orphan grace-period DataStore fields,
 * preserves Valid licenses as-is, and marks itself done so it never
 * re-runs.
 *
 * Runs once during [LicenseManager] init, before any UI observes
 * [LicenseManager.licenseUiState].
 *
 * This migration does NOT inspect, count, or modify vaults, guides,
 * maps, or any other user content. Grandfathering of existing
 * over-cap content is an implicit consequence of the gate only firing
 * on new inserts.
 */
class LicenseMigrationV2(private val repo: Repo) {

    interface Repo {
        suspend fun isMigrationDone(): Boolean
        suspend fun setMigrationDone()
        suspend fun clearOrphanGracePeriodField()
    }

    suspend fun runIfNeeded() {
        if (repo.isMigrationDone()) return

        // Clear any DataStore leftovers from removed grace-period fields.
        // Idempotent: no-op if nothing to clear.
        repo.clearOrphanGracePeriodField()

        // Existing Valid / NotActivated state is preserved as-is by virtue
        // of leaving the rest of the StoredLicenseData untouched.

        repo.setMigrationDone()
    }
}
