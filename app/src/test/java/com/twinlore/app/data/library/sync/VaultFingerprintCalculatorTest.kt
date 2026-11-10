package com.twinlore.app.data.library.sync

import androidx.room.Room
import com.twinlore.app.data.local.VaultDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class VaultFingerprintCalculatorTest {

    private lateinit var db: VaultDatabase
    private val calc = VaultFingerprintCalculator()

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

    private fun insertVault(id: String, name: String) {
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO vaults (id, name, createdAt) VALUES ('$id', '$name', 0)"
        )
    }

    @Test
    fun `identical data produces identical hash`() = runTest {
        insertVault("v1", "Hollow Knight")
        val a = calc.computeAll(db)
        val b = calc.computeAll(db)
        assertEquals(a.getValue("v1"), b.getValue("v1"))
    }

    @Test
    fun `vault row change changes the hash`() = runTest {
        insertVault("v1", "Hollow Knight")
        val before = calc.computeAll(db).getValue("v1")
        db.openHelper.writableDatabase.execSQL("UPDATE vaults SET name = 'HK' WHERE id = 'v1'")
        val after = calc.computeAll(db).getValue("v1")
        assertNotEquals(before, after)
    }

    @Test
    fun `child row change changes the hash, other vaults unaffected`() = runTest {
        insertVault("v1", "A"); insertVault("v2", "B")
        db.openHelper.writableDatabase.execSQL(
            // ChecklistTagEntity columns: id, vaultId, name, color (Int), sortOrder (Int),
            // createdAt (Long), updatedAt (Long) — all NOT NULL
            "INSERT INTO checklist_tags (id, vaultId, name, color, sortOrder, createdAt, updatedAt) " +
                "VALUES ('t1', 'v1', 'boss', 0, 0, 0, 0)"
        )
        val before = calc.computeAll(db)
        db.openHelper.writableDatabase.execSQL("UPDATE checklist_tags SET name = 'boss2' WHERE id = 't1'")
        val after = calc.computeAll(db)
        assertNotEquals(before.getValue("v1"), after.getValue("v1"))
        assertEquals(before.getValue("v2"), after.getValue("v2"))
    }

    @Test
    fun `volatile column lastOpened does not change the hash`() = runTest {
        insertVault("v1", "A")
        val before = calc.computeAll(db).getValue("v1")
        db.openHelper.writableDatabase.execSQL("UPDATE vaults SET lastOpened = 12345 WHERE id = 'v1'")
        val after = calc.computeAll(db).getValue("v1")
        assertEquals(before, after)
    }

    @Test
    fun `NULL and literal space-NULL string produce different hashes`() = runTest {
        // romPath is a nullable TEXT column on vaults — starts NULL
        insertVault("v1", "Hollow Knight")
        val nullHash = calc.computeAll(db).getValue("v1")
        // Set romPath to the old sentinel string that would have collided before type-tagging
        db.openHelper.writableDatabase.execSQL("UPDATE vaults SET romPath = ' NULL' WHERE id = 'v1'")
        val sentinelHash = calc.computeAll(db).getValue("v1")
        assertNotEquals(
            "NULL and literal ' NULL' string must hash differently (type-tag fix)",
            nullHash,
            sentinelHash
        )
    }

    @Test
    fun `volatile column resource_files last_seen_at does not change the hash`() = runTest {
        insertVault("v1", "A")
        db.openHelper.writableDatabase.execSQL(
            // NOT NULL columns: id, kind, filename, size_bytes, source, last_seen_at, created_at
            // vault_id is nullable FK; include it so the row is scoped to v1
            "INSERT INTO resource_files (id, vault_id, kind, filename, size_bytes, source, last_seen_at, created_at) " +
                "VALUES ('r1', 'v1', 'artwork', 'cover.png', 12345, 'manual', 1000, 500)"
        )
        val before = calc.computeAll(db).getValue("v1")
        db.openHelper.writableDatabase.execSQL("UPDATE resource_files SET last_seen_at = 99999 WHERE id = 'r1'")
        val after = calc.computeAll(db).getValue("v1")
        assertEquals(
            "volatile last_seen_at must not affect fingerprint",
            before,
            after
        )
    }

    @Test
    fun `device-local resource_files columns do not change the hash`() = runTest {
        insertVault("v1", "A")
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO resource_files (id, vault_id, kind, filename, size_bytes, source, " +
                "absolute_path, last_seen_at, created_at) " +
                "VALUES ('r1', 'v1', 'guide', 'walkthrough.mht', 12345, 'manual', " +
                "'content://deviceA/tree/abc', 1000, 500)"
        )
        val before = calc.computeAll(db).getValue("v1")
        // Simulate the same logical file as scanned on a second device: different SAF
        // absolute_path and a different scan time. Neither is user content.
        db.openHelper.writableDatabase.execSQL(
            "UPDATE resource_files SET absolute_path = 'content://deviceB/tree/xyz', " +
                "created_at = 7777 WHERE id = 'r1'"
        )
        val after = calc.computeAll(db).getValue("v1")
        assertEquals(
            "device-local absolute_path / created_at must not affect fingerprint",
            before,
            after
        )
    }

    @Test
    fun `blob column favicon_bytes participates in the hash`() = runTest {
        // saved_tabs.favicon_bytes is a nullable BLOB column — perfect for testing raw-bytes hashing.
        // All NOT NULL columns (Room schema has no DEFAULT for any of them):
        // id, vaultId, title, url, scrollPosition, zoomLevel, isFavorite, isMap,
        // isCurrentContent, tab_order, createdAt, lastAccessed, fileType,
        // lastPdfPage, pdfScrollOffset, scroll_occurrence_index, scroll_ratio
        insertVault("v1", "A")
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO saved_tabs " +
                "(id, vaultId, title, url, scrollPosition, zoomLevel, isFavorite, isMap, " +
                "isCurrentContent, tab_order, createdAt, lastAccessed, fileType, " +
                "lastPdfPage, pdfScrollOffset, scroll_occurrence_index, scroll_ratio) " +
                "VALUES ('tab1', 'v1', 'My Tab', 'https://example.com', 0, 1.0, 0, 0, 0, 0, 0, 0, 'html', 0, 0.0, 0, 0.0)"
        )
        val noBlob = calc.computeAll(db).getValue("v1")
        // Write a non-null blob — SQLite hex literal X'DEADBEEF'
        db.openHelper.writableDatabase.execSQL(
            "UPDATE saved_tabs SET favicon_bytes = X'DEADBEEF' WHERE id = 'tab1'"
        )
        val withBlob = calc.computeAll(db).getValue("v1")
        assertNotEquals(
            "blob favicon_bytes content must participate in the fingerprint",
            noBlob,
            withBlob
        )
    }
}
