package com.twinlore.app.data.local

import androidx.room.Room
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SyncDaoTest {

    private lateinit var db: VaultDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(), VaultDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `vault sync state upsert and read back`() = runTest {
        // vault_sync_state now has a FK on vaultId — seed a vaults row first.
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO vaults (id, name, createdAt) VALUES ('v1', 'Test', 0)"
        )
        val dao = db.vaultSyncStateDao()
        dao.upsertAll(listOf(VaultSyncStateEntity("v1", "fp1", 100L)))
        dao.upsertAll(listOf(VaultSyncStateEntity("v1", "fp2", 200L)))
        val all = dao.getAll()
        assertEquals(1, all.size)
        assertEquals("fp2", all[0].fingerprint)
        assertEquals(200L, all[0].lastChangedAt)
        dao.deleteByVaultIds(listOf("v1"))
        assertEquals(0, dao.getAll().size)
    }

    @Test
    fun `tombstone upsert keeps newest deletedAt and prunes old`() = runTest {
        val dao = db.syncTombstoneDao()
        dao.upsert(SyncTombstoneEntity("v1", deletedAt = 100L, deviceId = "a"))
        dao.upsert(SyncTombstoneEntity("v1", deletedAt = 200L, deviceId = "b"))
        assertEquals(200L, dao.getAll().single().deletedAt)

        // An older-timestamp upsert must be a no-op — deletedAt and deviceId stay at the winner's values.
        dao.upsert(SyncTombstoneEntity("v1", deletedAt = 50L, deviceId = "c"))
        val afterStale = dao.getAll().single()
        assertEquals("Stale upsert must not overwrite deletedAt", 200L, afterStale.deletedAt)
        assertEquals("Stale upsert must not overwrite deviceId", "b", afterStale.deviceId)

        // prune boundary: cutoff == 200 keeps the row (condition is strictly less-than).
        dao.pruneOlderThan(200L)
        assertEquals("prune at cutoff=200 must keep the row (deletedAt=200 is not < 200)", 1, dao.getAll().size)

        // prune boundary: cutoff > 200 removes the row.
        dao.pruneOlderThan(201L)
        assertEquals("prune at cutoff=201 must remove the row (deletedAt=200 < 201)", 0, dao.getAll().size)
        assertNull(dao.getByVaultId("v1"))
    }
}
