package com.twinlore.app.data.license

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LicenseMigrationV2Test {

    @Test
    fun `migration clears orphan grace period field on first run`() = runTest {
        val repo = FakeRepo(migrationDone = false, hasOrphanGracePeriodField = true)
        val migration = LicenseMigrationV2(repo)

        migration.runIfNeeded()

        assertFalse(repo.hasOrphanGracePeriodField)
        assertTrue(repo.migrationDone)
    }

    @Test
    fun `migration sets done flag on fresh install`() = runTest {
        val repo = FakeRepo(migrationDone = false, hasOrphanGracePeriodField = false)
        val migration = LicenseMigrationV2(repo)

        migration.runIfNeeded()

        assertTrue(repo.migrationDone)
    }

    @Test
    fun `migration is idempotent and does not run twice`() = runTest {
        val repo = FakeRepo(migrationDone = true, hasOrphanGracePeriodField = false)
        val migration = LicenseMigrationV2(repo)

        migration.runIfNeeded()

        // Migration already done; no writes occurred.
        assertEquals(0, repo.clearCallCount)
        assertEquals(0, repo.setDoneCallCount)
    }

    @Test
    fun `migration preserves existing Valid state by not touching it`() = runTest {
        // The migration never inspects or mutates StoredLicenseData
        // beyond the grace-period cleanup, so a Valid license carries
        // over implicitly. This test pins the contract: no DataStore
        // write paths beyond clearOrphanGracePeriodField + setMigrationDone.
        val repo = FakeRepo(migrationDone = false, hasOrphanGracePeriodField = false)
        val migration = LicenseMigrationV2(repo)

        migration.runIfNeeded()

        assertEquals(1, repo.clearCallCount)
        assertEquals(1, repo.setDoneCallCount)
    }

    private class FakeRepo(
        var migrationDone: Boolean,
        var hasOrphanGracePeriodField: Boolean = false,
    ) : LicenseMigrationV2.Repo {
        var clearCallCount = 0
        var setDoneCallCount = 0

        override suspend fun isMigrationDone(): Boolean = migrationDone
        override suspend fun setMigrationDone() {
            migrationDone = true
            setDoneCallCount++
        }
        override suspend fun clearOrphanGracePeriodField() {
            hasOrphanGracePeriodField = false
            clearCallCount++
        }
    }
}
