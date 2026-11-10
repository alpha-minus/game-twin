package com.twinlore.app.data.library.sync

import androidx.room.Room
import com.twinlore.app.data.local.VaultDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SyncStateRefreshTest {

    private lateinit var db: VaultDatabase
    private lateinit var refresher: SyncStateRefresher

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(), VaultDatabase::class.java
        ).allowMainThreadQueries().build()
        refresher = SyncStateRefresher(VaultFingerprintCalculator())
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO vaults (id, name, createdAt) VALUES ('v1', 'A', 0)"
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `first refresh stamps now, unchanged second refresh keeps timestamp`() = runTest {
        refresher.refresh(db, nowMillis = 1000L)
        assertEquals(1000L, db.vaultSyncStateDao().getAll().single().lastChangedAt)
        refresher.refresh(db, nowMillis = 2000L)
        assertEquals(1000L, db.vaultSyncStateDao().getAll().single().lastChangedAt)
    }

    @Test
    fun `content change bumps lastChangedAt`() = runTest {
        refresher.refresh(db, nowMillis = 1000L)
        db.openHelper.writableDatabase.execSQL("UPDATE vaults SET name = 'B' WHERE id = 'v1'")
        refresher.refresh(db, nowMillis = 2000L)
        assertEquals(2000L, db.vaultSyncStateDao().getAll().single().lastChangedAt)
    }

    @Test
    fun `vanished vault rows are dropped and old tombstones pruned`() = runTest {
        refresher.refresh(db, nowMillis = 1000L)
        db.syncTombstoneDao().upsert(
            com.twinlore.app.data.local.SyncTombstoneEntity("old", deletedAt = 1000L, deviceId = "d")
        )
        db.openHelper.writableDatabase.execSQL("DELETE FROM vaults WHERE id = 'v1'")
        val justUnderCutoff = 1000L + SyncStateRefresher.TOMBSTONE_RETENTION_MILLIS
        refresher.refresh(db, nowMillis = justUnderCutoff)
        assertEquals(1, db.syncTombstoneDao().getAll().size)   // exactly at cutoff: kept
        refresher.refresh(db, nowMillis = justUnderCutoff + 1)
        assertEquals(0, db.syncTombstoneDao().getAll().size)   // past cutoff: pruned
        assertEquals(0, db.vaultSyncStateDao().getAll().size)  // state row gone (cascade or explicit)
    }
}
