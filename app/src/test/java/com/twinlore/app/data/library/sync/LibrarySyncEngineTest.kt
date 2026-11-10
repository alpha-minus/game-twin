package com.twinlore.app.data.library.sync

import android.content.Context
import androidx.room.Room
import com.twinlore.app.data.local.VaultDatabase
import com.twinlore.app.data.local.VaultDatabaseMigration
import android.database.sqlite.SQLiteConstraintException
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LibrarySyncEngineTest {

    private lateinit var context: Context
    private lateinit var local: VaultDatabase
    private lateinit var engine: LibrarySyncEngine

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        local = Room.databaseBuilder(context, VaultDatabase::class.java, "test_local.db")
            .addMigrations(*VaultDatabaseMigration.ALL)
            .allowMainThreadQueries()
            .build()
        engine = LibrarySyncEngine(
            context,
            local,
            VaultFingerprintCalculator(),
            com.twinlore.app.data.library.ArtworkThumbnailCache(context),
            com.twinlore.app.data.library.MapThumbnailCache(context),
        )
    }

    @After
    fun tearDown() {
        // restoreLibraryFromBackup closes the Room handle; close() is
        // idempotent but guard anyway so a restore-test failure can't cascade.
        runCatching { local.close() }
        context.getDatabasePath("test_local.db").delete()
    }

    private fun buildSnapshot(name: String, build: (VaultDatabase) -> Unit): File {
        val db = Room.databaseBuilder(context, VaultDatabase::class.java, name)
            .addMigrations(*VaultDatabaseMigration.ALL)
            .allowMainThreadQueries()
            .build()
        build(db)
        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
        db.close()
        return context.getDatabasePath(name)
    }

    private fun insertVault(db: VaultDatabase, id: String, vaultName: String) {
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO vaults (id, name, createdAt) VALUES ('$id', '$vaultName', 0)"
        )
    }

    private fun setSyncState(db: VaultDatabase, id: String, changedAt: Long) {
        db.openHelper.writableDatabase.execSQL(
            "INSERT OR REPLACE INTO vault_sync_state (vaultId, fingerprint, lastChangedAt) " +
                "VALUES ('$id', 'stale-not-used', $changedAt)"
        )
    }

    private fun insertTombstone(db: VaultDatabase, id: String, deletedAt: Long) {
        db.openHelper.writableDatabase.execSQL(
            "INSERT OR REPLACE INTO sync_tombstones (vaultId, deletedAt, deviceId) " +
                "VALUES ('$id', $deletedAt, 'other-device')"
        )
    }

    private fun insertBrowserTab(db: VaultDatabase, tabId: String, vaultId: String, url: String) {
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO browser_tabs (id, vaultId, url, title, lastAccessed, createdAt) " +
                "VALUES ('$tabId', '$vaultId', '$url', 'Tab', 0, 0)"
        )
    }

    private fun insertChecklistTag(db: VaultDatabase, tagId: String, vaultId: String, tagName: String) {
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO checklist_tags (id, vaultId, name, color, sortOrder, createdAt, updatedAt) " +
                "VALUES ('$tagId', '$vaultId', '$tagName', 0, 0, 0, 0)"
        )
    }

    private fun insertLibraryMeta(db: VaultDatabase, lastWrittenAtMillis: Long, deviceId: String) {
        db.openHelper.writableDatabase.execSQL(
            "INSERT OR REPLACE INTO library_meta " +
                "(id, schemaVersion, lastWrittenAtMillis, lastWrittenDeviceId, lastWrittenDeviceLabel) " +
                "VALUES (1, 35, $lastWrittenAtMillis, '$deviceId', '$deviceId')"
        )
    }

    private fun insertFavoriteApp(db: VaultDatabase, packageName: String) {
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO favorite_apps (packageName, addedAt, position) VALUES ('$packageName', 0, 0)"
        )
    }

    private fun insertSavedTab(db: VaultDatabase, tabId: String, vaultId: String) {
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO saved_tabs (id, vaultId, title, url, scrollPosition, zoomLevel, " +
                "isFavorite, isMap, isCurrentContent, tab_order, createdAt, lastAccessed, " +
                "fileType, lastPdfPage, pdfScrollOffset, scroll_occurrence_index, scroll_ratio) " +
                "VALUES ('$tabId', '$vaultId', 'Guide', 'https://g.example', 0, 1.0, " +
                "0, 0, 0, 0, 0, 0, 'html', 0, 0, 0, 0)"
        )
    }

    private fun insertPageBookmark(db: VaultDatabase, bookmarkId: String, savedTabId: String) {
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO page_bookmarks (id, saved_tab_id, label, scroll_position, created_at) " +
                "VALUES ('$bookmarkId', '$savedTabId', 'mark', 0, 0)"
        )
    }

    private fun insertResourceFile(db: VaultDatabase, fileId: String, vaultId: String) {
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO resource_files (id, vault_id, kind, filename, size_bytes, " +
                "source, last_seen_at, created_at) " +
                "VALUES ('$fileId', '$vaultId', 'map', 'map.png', 1, 'import', 0, 0)"
        )
    }

    private fun insertMapMarker(db: VaultDatabase, markerId: String, resourceFileId: String) {
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO map_markers (id, resource_file_id, label, x_norm, y_norm, color, " +
                "created_at, zoom, offset_x, offset_y, style_id) " +
                "VALUES ('$markerId', '$resourceFileId', 'pin', 0.5, 0.5, 0, 0, 1.0, 0, 0, 1)"
        )
    }

    private fun insertRaGameCache(db: VaultDatabase, vaultId: String, snapshot: String) {
        db.openHelper.writableDatabase.execSQL(
            "INSERT OR REPLACE INTO ra_game_cache (vaultId, raGameId, jsonSnapshot, lastSyncedAt) " +
                "VALUES ('$vaultId', 1, '$snapshot', 0)"
        )
    }

    private fun localVaultNames(): Map<String, String> {
        val out = mutableMapOf<String, String>()
        local.openHelper.writableDatabase.query("SELECT id, name FROM vaults").use { c ->
            while (c.moveToNext()) out[c.getString(0)] = c.getString(1)
        }
        return out
    }

    private fun localBrowserTabIds(vaultId: String): List<String> {
        val out = mutableListOf<String>()
        local.openHelper.writableDatabase
            .query("SELECT id FROM browser_tabs WHERE vaultId = '$vaultId' ORDER BY id").use { c ->
                while (c.moveToNext()) out.add(c.getString(0))
            }
        return out
    }

    private fun localChecklistTagIds(vaultId: String): List<String> {
        val out = mutableListOf<String>()
        local.openHelper.writableDatabase
            .query("SELECT id FROM checklist_tags WHERE vaultId = '$vaultId' ORDER BY id").use { c ->
                while (c.moveToNext()) out.add(c.getString(0))
            }
        return out
    }

    private fun localFavoriteApps(): List<String> {
        val out = mutableListOf<String>()
        local.openHelper.writableDatabase
            .query("SELECT packageName FROM favorite_apps ORDER BY packageName").use { c ->
                while (c.moveToNext()) out.add(c.getString(0))
            }
        return out
    }

    /** First-column strings of an arbitrary query against the live local DB. */
    private fun localStrings(sql: String): List<String> {
        val out = mutableListOf<String>()
        local.openHelper.writableDatabase.query(sql).use { c ->
            while (c.moveToNext()) out.add(c.getString(0))
        }
        return out
    }

    private fun localLong(sql: String): Long =
        local.openHelper.writableDatabase.query(sql).use { c ->
            assertTrue("expected one row for: $sql", c.moveToFirst())
            c.getLong(0)
        }

    // ── 1 ────────────────────────────────────────────────────────────────────

    @Test
    fun `fresh vault on each side produces union`() = runTest {
        insertVault(local, "vA", "Alpha")
        setSyncState(local, "vA", 100)
        val snap = buildSnapshot("remote_union.db") { db ->
            insertVault(db, "vB", "Beta")
            setSyncState(db, "vB", 100)
        }
        val remote = engine.openRemote(snap)
        try {
            val diff = engine.preview(remote, lastMergedAtMillis = 0, nowMillis = 1000)
            assertEquals(listOf("vB"), diff.toCopy.map { it.vaultId })
            assertEquals(listOf("vB"), diff.newVaults.map { it.vaultId })
            assertTrue(diff.toDelete.isEmpty())
            assertTrue(diff.conflicts.isEmpty())
            engine.apply(diff, remote)
            assertEquals(mapOf("vA" to "Alpha", "vB" to "Beta"), localVaultNames())
        } finally {
            remote.close()
        }
    }

    // ── 2 ────────────────────────────────────────────────────────────────────

    @Test
    fun `equal fingerprints short-circuit regardless of timestamps`() = runTest {
        insertVault(local, "v1", "Same Content")
        setSyncState(local, "v1", 100)
        val snap = buildSnapshot("remote_equal.db") { db ->
            insertVault(db, "v1", "Same Content")
            setSyncState(db, "v1", 999) // remote claims much newer — must not matter
        }
        val remote = engine.openRemote(snap)
        try {
            val diff = engine.preview(remote, lastMergedAtMillis = 0, nowMillis = 1000)
            assertTrue(diff.isEmpty)
            assertTrue(diff.conflicts.isEmpty())
        } finally {
            remote.close()
        }
    }

    // ── 3 ────────────────────────────────────────────────────────────────────

    @Test
    fun `both changed - newer remote wins and is flagged as conflict`() = runTest {
        insertVault(local, "v1", "Local Name")
        setSyncState(local, "v1", 200)
        val snap = buildSnapshot("remote_conflict_remote_wins.db") { db ->
            insertVault(db, "v1", "Remote Name")
            setSyncState(db, "v1", 300)
        }
        val remote = engine.openRemote(snap)
        try {
            val diff = engine.preview(remote, lastMergedAtMillis = 150, nowMillis = 1000)
            assertFalse(diff.isEmpty)
            assertEquals(listOf("v1"), diff.conflicts.map { it.vaultId })
            assertEquals(listOf("v1"), diff.toCopy.map { it.vaultId })
            assertTrue(diff.newVaults.isEmpty())
            engine.apply(diff, remote)
            assertEquals("Remote Name", localVaultNames()["v1"])
        } finally {
            remote.close()
        }
    }

    // ── 4 ────────────────────────────────────────────────────────────────────

    @Test
    fun `both changed - newer local wins, no copy`() = runTest {
        insertVault(local, "v1", "Local Name")
        setSyncState(local, "v1", 300)
        val snap = buildSnapshot("remote_conflict_local_wins.db") { db ->
            insertVault(db, "v1", "Remote Name")
            setSyncState(db, "v1", 200)
        }
        val remote = engine.openRemote(snap)
        try {
            val diff = engine.preview(remote, lastMergedAtMillis = 150, nowMillis = 1000)
            assertTrue(diff.toCopy.isEmpty())
            assertEquals(listOf("v1"), diff.conflicts.map { it.vaultId })
            engine.apply(diff, remote)
            assertEquals("Local Name", localVaultNames()["v1"])
        } finally {
            remote.close()
        }
    }

    // ── 5 ────────────────────────────────────────────────────────────────────

    @Test
    fun `tombstone newer than vault deletes it, edit newer than tombstone survives`() = runTest {
        insertVault(local, "vDel", "Doomed")
        setSyncState(local, "vDel", 100)
        insertVault(local, "vKeep", "Survivor")
        setSyncState(local, "vKeep", 500)
        val snap = buildSnapshot("remote_tombstones.db") { db ->
            insertTombstone(db, "vDel", 200)  // newer than vDel's last change
            insertTombstone(db, "vKeep", 300) // older than vKeep's last change
        }
        val remote = engine.openRemote(snap)
        try {
            val diff = engine.preview(remote, lastMergedAtMillis = 0, nowMillis = 1000)
            assertEquals(listOf("vDel"), diff.toDelete.map { it.vaultId })
            assertTrue(diff.toCopy.isEmpty())
            engine.apply(diff, remote)
            val names = localVaultNames()
            assertNull(names["vDel"])
            assertEquals("Survivor", names["vKeep"])
            val tombs = local.syncTombstoneDao().getAll().associate { it.vaultId to it.deletedAt }
            assertEquals(mapOf("vDel" to 200L, "vKeep" to 300L), tombs)
        } finally {
            remote.close()
        }
    }

    // ── 6 ────────────────────────────────────────────────────────────────────

    @Test
    fun `future-dated remote timestamps are clamped to now`() = runTest {
        insertVault(local, "v1", "Local Name")
        setSyncState(local, "v1", 900)
        val snap = buildSnapshot("remote_future_clock.db") { db ->
            insertVault(db, "v1", "Remote Name")
            setSyncState(db, "v1", 999999) // broken clock far in the future
        }
        val remote = engine.openRemote(snap)
        try {
            val diff = engine.preview(remote, lastMergedAtMillis = 0, nowMillis = 500)
            // remote clamped to 500 < local 900 → local wins, nothing to copy
            assertTrue(diff.toCopy.isEmpty())
            assertTrue(diff.toDelete.isEmpty())
            engine.apply(diff, remote)
            assertEquals("Local Name", localVaultNames()["v1"])
        } finally {
            remote.close()
        }
    }

    // ── 7 ────────────────────────────────────────────────────────────────────

    @Test
    fun `apply preserves local browser_tabs for a replaced vault`() = runTest {
        insertVault(local, "v1", "Local Name")
        setSyncState(local, "v1", 100)
        insertBrowserTab(local, "tab-local", "v1", "https://local.example")
        // Stale local child row that must NOT survive the wholesale replace
        // (ATTACH disables FK enforcement on framework connections, so the
        // engine must delete children explicitly rather than rely on cascade).
        insertChecklistTag(local, "tag-local", "v1", "old-tag")
        val snap = buildSnapshot("remote_browser_tabs.db") { db ->
            insertVault(db, "v1", "Remote Name")
            setSyncState(db, "v1", 200)
            insertChecklistTag(db, "tag-remote", "v1", "new-tag")
            // remote device's own tabs must NOT travel
            insertBrowserTab(db, "tab-remote", "v1", "https://remote.example")
        }
        val remote = engine.openRemote(snap)
        try {
            val diff = engine.preview(remote, lastMergedAtMillis = 0, nowMillis = 1000)
            assertEquals(listOf("v1"), diff.toCopy.map { it.vaultId })
            engine.apply(diff, remote)
            assertEquals("Remote Name", localVaultNames()["v1"])
            assertEquals(listOf("tab-local"), localBrowserTabIds("v1"))
            assertEquals(listOf("tag-remote"), localChecklistTagIds("v1"))
        } finally {
            remote.close()
        }
    }

    // ── 8 ────────────────────────────────────────────────────────────────────

    @Test
    fun `idempotent re-merge is a no-op`() = runTest {
        val snap = buildSnapshot("remote_idempotent.db") { db ->
            insertVault(db, "v1", "Imported")
            setSyncState(db, "v1", 100)
        }
        val first = engine.openRemote(snap)
        try {
            val diff = engine.preview(first, lastMergedAtMillis = 0, nowMillis = 1000)
            assertEquals(listOf("v1"), diff.toCopy.map { it.vaultId })
            engine.apply(diff, first)
        } finally {
            first.close()
        }
        val second = engine.openRemote(snap)
        try {
            val diff = engine.preview(second, lastMergedAtMillis = 1000, nowMillis = 2000)
            assertTrue(diff.isEmpty)
            assertTrue(diff.conflicts.isEmpty())
        } finally {
            second.close()
        }
    }

    // ── 10 ───────────────────────────────────────────────────────────────────

    @Test
    fun `favorite_apps adopt remote set when remote snapshot is newer`() = runTest {
        insertLibraryMeta(local, lastWrittenAtMillis = 100, deviceId = "local-dev")
        insertFavoriteApp(local, "com.local.app")
        insertVault(local, "vA", "Alpha")
        setSyncState(local, "vA", 100)
        val snap = buildSnapshot("remote_favorites.db") { db ->
            insertLibraryMeta(db, lastWrittenAtMillis = 200, deviceId = "remote-dev")
            insertFavoriteApp(db, "com.remote.app")
            insertVault(db, "vB", "Beta")
            setSyncState(db, "vB", 100)
        }
        val remote = engine.openRemote(snap)
        try {
            val diff = engine.preview(remote, lastMergedAtMillis = 0, nowMillis = 1000)
            engine.apply(diff, remote)
            assertEquals(listOf("com.remote.app"), localFavoriteApps())
        } finally {
            remote.close()
        }
    }

    // ── 11 ───────────────────────────────────────────────────────────────────

    @Test
    fun `indirect child tables travel with the vault and stale local children die`() = runTest {
        insertVault(local, "v1", "Local Name")
        setSyncState(local, "v1", 100)
        insertSavedTab(local, "tab-l", "v1")
        insertPageBookmark(local, "bm-l", "tab-l")
        insertResourceFile(local, "res-l", "v1")
        insertMapMarker(local, "mk-l", "res-l")
        val snap = buildSnapshot("remote_children.db") { db ->
            insertVault(db, "v1", "Remote Name")
            setSyncState(db, "v1", 200)
            insertSavedTab(db, "tab-r", "v1")
            insertPageBookmark(db, "bm-r", "tab-r")
            insertResourceFile(db, "res-r", "v1")
            insertMapMarker(db, "mk-r", "res-r")
        }
        val remote = engine.openRemote(snap)
        try {
            val diff = engine.preview(remote, lastMergedAtMillis = 0, nowMillis = 1000)
            assertEquals(listOf("v1"), diff.toCopy.map { it.vaultId })
            engine.apply(diff, remote)
            assertEquals(
                listOf("tab-r"),
                localStrings("SELECT id FROM saved_tabs WHERE vaultId = 'v1' ORDER BY id"),
            )
            assertEquals(listOf("bm-r"), localStrings("SELECT id FROM page_bookmarks ORDER BY id"))
            assertEquals(
                listOf("res-r"),
                localStrings("SELECT id FROM resource_files WHERE vault_id = 'v1' ORDER BY id"),
            )
            assertEquals(listOf("mk-r"), localStrings("SELECT id FROM map_markers ORDER BY id"))
        } finally {
            remote.close()
        }
    }

    // ── 12 ───────────────────────────────────────────────────────────────────

    @Test
    fun `apply adopts the remote lastChangedAt for copied vaults`() = runTest {
        insertVault(local, "v1", "Local Name")
        setSyncState(local, "v1", 100)
        val snap = buildSnapshot("remote_sync_state_pin.db") { db ->
            insertVault(db, "v1", "Remote Name")
            setSyncState(db, "v1", 200)
        }
        val remote = engine.openRemote(snap)
        try {
            val diff = engine.preview(remote, lastMergedAtMillis = 0, nowMillis = 1000)
            assertEquals(listOf("v1"), diff.toCopy.map { it.vaultId })
            engine.apply(diff, remote)
            // The REMOTE timestamp, not a fresh local stamp — otherwise the
            // next local save would let stale content win future merges.
            assertEquals(
                200L,
                localLong("SELECT lastChangedAt FROM vault_sync_state WHERE vaultId = 'v1'"),
            )
        } finally {
            remote.close()
        }
    }

    // ── 13 ───────────────────────────────────────────────────────────────────

    @Test
    fun `foreign key enforcement is back on after apply`() = runTest {
        insertVault(local, "vA", "Alpha")
        setSyncState(local, "vA", 100)
        val snap = buildSnapshot("remote_fk_reenable.db") { db ->
            insertVault(db, "vB", "Beta")
            setSyncState(db, "vB", 100)
        }
        val remote = engine.openRemote(snap)
        try {
            val diff = engine.preview(remote, lastMergedAtMillis = 0, nowMillis = 1000)
            engine.apply(diff, remote)
            // ATTACH dropped `PRAGMA foreign_keys = ON`; the engine must have
            // re-applied it, so an orphan child row must be rejected.
            assertThrows(SQLiteConstraintException::class.java) {
                insertChecklistTag(local, "tag-orphan", "no-such-vault", "boom")
            }
        } finally {
            remote.close()
        }
    }

    // ── 14 ───────────────────────────────────────────────────────────────────

    @Test
    fun `tombstone older than the remote change does not block import`() = runTest {
        val snap = buildSnapshot("remote_tomb_loses.db") { db ->
            insertVault(db, "vX", "Phoenix")
            setSyncState(db, "vX", 400)
            insertTombstone(db, "vX", 300) // older than the change: edit wins
        }
        val remote = engine.openRemote(snap)
        try {
            val diff = engine.preview(remote, lastMergedAtMillis = 0, nowMillis = 1000)
            assertEquals(listOf("vX"), diff.toCopy.map { it.vaultId })
            assertEquals(listOf("vX"), diff.newVaults.map { it.vaultId })
            assertTrue(diff.toDelete.isEmpty())
            engine.apply(diff, remote)
            assertEquals("Phoenix", localVaultNames()["vX"])
            // The tombstone still persists in the union for future merges.
            assertEquals(300L, local.syncTombstoneDao().getByVaultId("vX")?.deletedAt)
        } finally {
            remote.close()
        }
    }

    // ── 15 ───────────────────────────────────────────────────────────────────

    @Test
    fun `additive cache row is replaced for the copied vault, other vaults untouched`() = runTest {
        insertVault(local, "v1", "Local Name")
        setSyncState(local, "v1", 100)
        insertRaGameCache(local, "v1", "local-snap")
        insertVault(local, "vOther", "Bystander")
        setSyncState(local, "vOther", 100)
        insertRaGameCache(local, "vOther", "bystander-snap")
        val snap = buildSnapshot("remote_additive_cache.db") { db ->
            insertVault(db, "v1", "Remote Name")
            setSyncState(db, "v1", 200)
            insertRaGameCache(db, "v1", "remote-snap")
        }
        val remote = engine.openRemote(snap)
        try {
            val diff = engine.preview(remote, lastMergedAtMillis = 0, nowMillis = 1000)
            assertEquals(listOf("v1"), diff.toCopy.map { it.vaultId })
            engine.apply(diff, remote)
            assertEquals(
                listOf("remote-snap"),
                localStrings("SELECT jsonSnapshot FROM ra_game_cache WHERE vaultId = 'v1'"),
            )
            assertEquals(
                listOf("bystander-snap"),
                localStrings("SELECT jsonSnapshot FROM ra_game_cache WHERE vaultId = 'vOther'"),
            )
        } finally {
            remote.close()
        }
    }

    // ── 16 ───────────────────────────────────────────────────────────────────

    @Test
    fun `favorite_apps untouched when local library_meta is newer`() = runTest {
        insertLibraryMeta(local, lastWrittenAtMillis = 300, deviceId = "local-dev")
        insertFavoriteApp(local, "com.local.app")
        insertVault(local, "vA", "Alpha")
        setSyncState(local, "vA", 100)
        val snap = buildSnapshot("remote_favorites_stale.db") { db ->
            insertLibraryMeta(db, lastWrittenAtMillis = 200, deviceId = "remote-dev")
            insertFavoriteApp(db, "com.remote.app")
            insertVault(db, "vB", "Beta")
            setSyncState(db, "vB", 100)
        }
        val remote = engine.openRemote(snap)
        try {
            val diff = engine.preview(remote, lastMergedAtMillis = 0, nowMillis = 1000)
            engine.apply(diff, remote)
            assertEquals(listOf("com.local.app"), localFavoriteApps())
        } finally {
            remote.close()
        }
    }

    // ── 17 ───────────────────────────────────────────────────────────────────

    @Test
    fun `timestamp tie keeps local but still flags the conflict`() = runTest {
        insertVault(local, "v1", "Local Name")
        setSyncState(local, "v1", 200)
        val snap = buildSnapshot("remote_tie.db") { db ->
            insertVault(db, "v1", "Remote Name")
            setSyncState(db, "v1", 200) // exact tie, different content
        }
        val remote = engine.openRemote(snap)
        try {
            val diff = engine.preview(remote, lastMergedAtMillis = 150, nowMillis = 1000)
            assertTrue(diff.toCopy.isEmpty())
            assertEquals(listOf("v1"), diff.conflicts.map { it.vaultId })
            engine.apply(diff, remote)
            assertEquals("Local Name", localVaultNames()["v1"])
        } finally {
            remote.close()
        }
    }

    // ── 19 ───────────────────────────────────────────────────────────────────

    @Test
    fun `restoreVaultWholesale restores one vault and clears its sync state`() = runTest {
        insertVault(local, "v1", "Local Name")
        setSyncState(local, "v1", 100)
        insertVault(local, "vOther", "Bystander")
        setSyncState(local, "vOther", 100)
        val backup = engine.backupLiveDb()
        val snap = buildSnapshot("remote_single_restore.db") { db ->
            insertVault(db, "v1", "Remote Name")
            setSyncState(db, "v1", 200)
        }
        val remote = engine.openRemote(snap)
        val diff = engine.preview(remote, lastMergedAtMillis = 0, nowMillis = 1000)
        engine.apply(diff, remote)
        assertEquals("Remote Name", localVaultNames().getValue("v1"))

        engine.restoreVaultWholesale(backup, VaultRef("v1", "Local Name"))

        val names = localVaultNames()
        assertEquals("Local Name", names.getValue("v1"))
        assertEquals("Bystander", names.getValue("vOther")) // untouched
        // Sync state row deliberately deleted: the next save stamps it fresh
        // so the restore propagates as a new local change.
        local.openHelper.writableDatabase.query(
            "SELECT COUNT(*) FROM vault_sync_state WHERE vaultId = 'v1'"
        ).use { c ->
            c.moveToFirst()
            assertEquals(0, c.getInt(0))
        }
        // Bystander's sync state survives.
        local.openHelper.writableDatabase.query(
            "SELECT COUNT(*) FROM vault_sync_state WHERE vaultId = 'vOther'"
        ).use { c ->
            c.moveToFirst()
            assertEquals(1, c.getInt(0))
        }
    }

    // ── 20 ───────────────────────────────────────────────────────────────────

    @Test
    fun `backupLiveDb creates a timestamped backup file`() = runTest {
        insertVault(local, "v1", "Alpha")
        val before = System.currentTimeMillis()
        val backup = engine.backupLiveDb()
        val after = System.currentTimeMillis()
        assertTrue(backup.exists())
        assertTrue(
            "name should match pre_sync_backup_<millis>.gametwin: ${backup.name}",
            Regex("""pre_sync_backup_\d+\.gametwin""").matches(backup.name),
        )
        val embedded = LibrarySyncEngine.backupMillis(backup)!!
        assertTrue(embedded in before..after)
    }

    // ── 21 ───────────────────────────────────────────────────────────────────

    @Test
    fun `listBackups returns newest first and ignores non-matching files`() {
        val dir = File(context.filesDir, "sync").apply { mkdirs() }
        File(dir, "pre_sync_backup_100.gametwin").writeText("a")
        File(dir, "pre_sync_backup_300.gametwin").writeText("b")
        File(dir, "pre_sync_backup_200.gametwin").writeText("c")
        File(dir, "pre_sync_backup.gametwin").writeText("legacy single-file name")
        File(dir, "unrelated.txt").writeText("x")
        assertEquals(
            listOf(
                "pre_sync_backup_300.gametwin",
                "pre_sync_backup_200.gametwin",
                "pre_sync_backup_100.gametwin",
            ),
            engine.listBackups().map { it.name },
        )
    }

    // ── 22 ───────────────────────────────────────────────────────────────────

    @Test
    fun `pruneBackups keeps the newest three and deletes older ones plus the legacy file`() {
        val dir = File(context.filesDir, "sync").apply { mkdirs() }
        val b100 = File(dir, "pre_sync_backup_100.gametwin").apply { writeText("100") }
        val b200 = File(dir, "pre_sync_backup_200.gametwin").apply { writeText("200") }
        val b300 = File(dir, "pre_sync_backup_300.gametwin").apply { writeText("300") }
        val b400 = File(dir, "pre_sync_backup_400.gametwin").apply { writeText("400") }
        val b500 = File(dir, "pre_sync_backup_500.gametwin").apply { writeText("500") }
        // Un-timestamped single-file name written by pre-restore-points builds:
        // never matches BACKUP_NAME_REGEX, so without the explicit cleanup it
        // would linger forever.
        val legacy = File(dir, "pre_sync_backup.gametwin").apply { writeText("legacy") }
        engine.pruneBackups()
        assertFalse(b100.exists())
        assertFalse(b200.exists())
        assertFalse(legacy.exists())
        assertTrue(b300.exists())
        assertTrue(b400.exists())
        assertTrue(b500.exists())
    }

    @Test
    fun `pruneBackups with three or fewer backups deletes nothing`() {
        val dir = File(context.filesDir, "sync").apply { mkdirs() }
        val b100 = File(dir, "pre_sync_backup_100.gametwin").apply { writeText("100") }
        val b200 = File(dir, "pre_sync_backup_200.gametwin").apply { writeText("200") }
        val b300 = File(dir, "pre_sync_backup_300.gametwin").apply { writeText("300") }
        engine.pruneBackups()
        assertTrue(b100.exists())
        assertTrue(b200.exists())
        assertTrue(b300.exists())
    }

    // ── 23 ───────────────────────────────────────────────────────────────────

    @Test
    fun `restoreLibraryFromBackup replaces the db file and clears sync state`() = runTest {
        insertVault(local, "v1", "Before")
        setSyncState(local, "v1", 100)
        val backup = engine.backupLiveDb()
        // Mutate AFTER the backup: this is the state the restore must erase.
        local.openHelper.writableDatabase
            .execSQL("UPDATE vaults SET name = 'After' WHERE id = 'v1'")

        engine.restoreLibraryFromBackup(backup)

        // The Room handle `local` is CLOSED now — verify by opening the
        // replaced file directly with the framework SQLiteDatabase.
        val dbFile = context.getDatabasePath("test_local.db")
        android.database.sqlite.SQLiteDatabase.openDatabase(
            dbFile.absolutePath,
            null,
            android.database.sqlite.SQLiteDatabase.OPEN_READONLY,
        ).use { raw ->
            raw.rawQuery("SELECT name FROM vaults WHERE id = 'v1'", null).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("Before", c.getString(0))
            }
            // The backup carried a vault_sync_state row, but the restore must
            // clear the table so the next save re-stamps lastChangedAt = now
            // and the restored library propagates as fresh local changes.
            raw.rawQuery("SELECT COUNT(*) FROM vault_sync_state", null).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(0, c.getInt(0))
            }
        }
    }

    @Test
    fun `apply drops device-local and traversing paths from adopted resource rows`() = runTest {
        insertVault(local, "v1", "Local")
        setSyncState(local, "v1", 100)
        val snap = buildSnapshot("remote_paths.db") { db ->
            insertVault(db, "v1", "Remote")
            setSyncState(db, "v1", 500)
            db.openHelper.writableDatabase.execSQL(
                "INSERT INTO resource_files (id, vault_id, kind, filename, size_bytes, " +
                    "home_relative_path, absolute_path, source, last_seen_at, created_at) " +
                    "VALUES ('res-bad', 'v1', 'html', 'g.html', 1, '../../escape.html', " +
                    "'/storage/emulated/0', 'import', 999, 0)"
            )
            db.openHelper.writableDatabase.execSQL(
                "INSERT INTO resource_files (id, vault_id, kind, filename, size_bytes, " +
                    "home_relative_path, absolute_path, source, last_seen_at, created_at) " +
                    "VALUES ('res-ok', 'v1', 'html', 'h.html', 1, 'Remote/h.html', " +
                    "'/some/other/device/path', 'import', 999, 0)"
            )
        }
        val remote = engine.openRemote(snap)
        try {
            val diff = engine.preview(remote, lastMergedAtMillis = 0, nowMillis = 1000)
            engine.apply(diff, remote)
            assertEquals(
                listOf("res-bad|null|null|0", "res-ok|Remote/h.html|null|0"),
                localStrings(
                    "SELECT id || '|' || IFNULL(home_relative_path,'null') || '|' || " +
                        "IFNULL(absolute_path,'null') || '|' || last_seen_at " +
                        "FROM resource_files WHERE vault_id = 'v1' ORDER BY id"
                ),
            )
        } finally {
            remote.close()
        }
    }
}
