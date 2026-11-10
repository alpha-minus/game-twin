package com.twinlore.app.data.retroachievements

import com.twinlore.app.data.local.RaGameCacheDao
import com.twinlore.app.data.local.RaGameCacheEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class RaGameCacheRepositoryNullSnapshotTest {

    @Test
    fun getCachedProgress_returns_null_when_row_has_no_snapshot() = runTest {
        val dao = mock<RaGameCacheDao>()
        whenever(dao.getByVaultId("v1")).thenReturn(
            RaGameCacheEntity(
                vaultId = "v1", raGameId = 1, jsonSnapshot = null,
                lastSyncedAt = 0L, highestAwardKind = "mastered", userRank = null,
                numAwardedSoft = 5, numAwardedHardcore = 2, numAchievements = 10
            )
        )
        val repo = RaGameCacheRepository(dao, mock(), mock())

        assertNull(repo.getCachedProgress("v1"))
    }

    @Test
    fun getCachedProgress_returns_null_when_row_has_empty_snapshot() = runTest {
        val dao = mock<RaGameCacheDao>()
        whenever(dao.getByVaultId("v_empty")).thenReturn(
            RaGameCacheEntity(
                vaultId = "v_empty", raGameId = 1, jsonSnapshot = "",
                lastSyncedAt = 0L,
                numAwardedSoft = 0, numAwardedHardcore = 0, numAchievements = 5,
            )
        )
        val repo = RaGameCacheRepository(dao, mock(), mock())

        assertNull(repo.getCachedProgress("v_empty"))
    }
}
