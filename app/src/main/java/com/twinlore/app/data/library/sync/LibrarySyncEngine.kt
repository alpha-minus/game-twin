package com.twinlore.app.data.library.sync

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import com.twinlore.app.data.local.SyncTombstoneEntity
import com.twinlore.app.data.local.VaultDatabase
import com.twinlore.app.data.local.VaultDatabaseMigration
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** A vault touched by a merge, with its display name for preview UI. */
data class VaultRef(val vaultId: String, val name: String)

/**
 * Result of diffing the local library against a remote snapshot. Computed by
 * [LibrarySyncEngine.preview], consumed verbatim by [LibrarySyncEngine.apply].
 */
data class SyncDiff(
    /** Vaults copied wholesale from the remote: brand-new + remote-won updates. */
    val toCopy: List<VaultRef>,
    /** Subset of [toCopy] that does not exist locally at all. */
    val newVaults: List<VaultRef>,
    /** Local vaults killed by a tombstone at least as new as their last change. */
    val toDelete: List<VaultRef>,
    /** Vaults where BOTH sides changed since the last merge (winner may be either side). */
    val conflicts: List<VaultRef>,
    /** Union of both sides' tombstones (newest deletedAt per vault, remote clamped). */
    val tombstoneUnion: List<SyncTombstoneEntity>,
) {
    val updatedVaults: List<VaultRef> get() = toCopy - newVaults.toSet()
    val isEmpty: Boolean get() = toCopy.isEmpty() && toDelete.isEmpty()
}

/** An opened, migrated copy of a foreign snapshot. Always [close] it. */
class RemoteSnapshot internal constructor(
    internal val db: VaultDatabase,
    internal val dbFile: File,
) {
    fun close() {
        db.close()
        dbFile.delete()
        File(dbFile.parentFile, "${dbFile.name}-wal").delete()
        File(dbFile.parentFile, "${dbFile.name}-shm").delete()
    }
}

/**
 * Cross-device library merge: open a foreign snapshot, diff it against the
 * live DB per vault (fingerprint short-circuit, newest-lastChangedAt wins,
 * tombstones win over both), apply the diff wholesale via ATTACH-based SQL,
 * and undo from a pre-merge backup file.
 */
@Singleton
class LibrarySyncEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: VaultDatabase,
    private val calculator: VaultFingerprintCalculator,
    private val artworkThumbnailCache: com.twinlore.app.data.library.ArtworkThumbnailCache,
    private val mapThumbnailCache: com.twinlore.app.data.library.MapThumbnailCache,
) {

    /** Copy snapshot into the databases dir and open via Room so ALL migrations upgrade it. */
    suspend fun openRemote(snapshotFile: File): RemoteSnapshot = withContext(Dispatchers.IO) {
        val name = "sync_remote_tmp"
        val target = context.getDatabasePath(name)
        target.parentFile?.mkdirs()
        File(target.parentFile, "$name-wal").delete()
        File(target.parentFile, "$name-shm").delete()
        snapshotFile.copyTo(target, overwrite = true)
        val db = Room.databaseBuilder(context, VaultDatabase::class.java, name)
            .addMigrations(*VaultDatabaseMigration.ALL)
            .build()
        // Force open (and therefore migration) now, so failures surface here.
        db.openHelper.writableDatabase.query("PRAGMA user_version").use { it.moveToFirst() }
        RemoteSnapshot(db, target)
    }

    suspend fun preview(
        remote: RemoteSnapshot,
        lastMergedAtMillis: Long,
        nowMillis: Long,
    ): SyncDiff = withContext(Dispatchers.IO) {
        val localFp = calculator.computeAll(database)
        val remoteFp = calculator.computeAll(remote.db)
        val localChanged = database.vaultSyncStateDao().getAll()
            .associate { it.vaultId to it.lastChangedAt }
        // Remote timestamps are clamped to now so broken-clock devices can't
        // systematically win merges. Local timestamps are NOT clamped.
        val remoteChanged = remote.db.vaultSyncStateDao().getAll()
            .associate {
                if (it.lastChangedAt > nowMillis) {
                    android.util.Log.w(
                        "LibrarySync",
                        "clamping future-dated remote lastChangedAt for vault ${it.vaultId} " +
                            "(${it.lastChangedAt} -> $nowMillis)"
                    )
                }
                it.vaultId to minOf(it.lastChangedAt, nowMillis)
            }
        val localNames = vaultNames(database.openHelper.writableDatabase)
        val remoteNames = vaultNames(remote.db.openHelper.writableDatabase)

        val tombs = mutableMapOf<String, SyncTombstoneEntity>()
        (database.syncTombstoneDao().getAll() +
            remote.db.syncTombstoneDao().getAll()
                .map { it.copy(deletedAt = minOf(it.deletedAt, nowMillis)) }
            ).forEach { t ->
                val prev = tombs[t.vaultId]
                if (prev == null || t.deletedAt > prev.deletedAt) tombs[t.vaultId] = t
            }

        val toCopy = mutableListOf<VaultRef>()
        val newVaults = mutableListOf<VaultRef>()
        val toDelete = mutableListOf<VaultRef>()
        val conflicts = mutableListOf<VaultRef>()

        for (id in localFp.keys + remoteFp.keys) {
            val inLocal = id in localFp
            val inRemote = id in remoteFp
            val lc = localChanged[id] ?: 0L
            val rc = remoteChanged[id] ?: 0L
            val tomb = tombs[id]
            val newestChange = maxOf(if (inLocal) lc else -1L, if (inRemote) rc else -1L)
            if (tomb != null && tomb.deletedAt >= newestChange) {
                // Deletion is at least as new as any change: vault dies (or is
                // never imported if it only exists remotely).
                if (inLocal) toDelete.add(VaultRef(id, localNames[id] ?: id))
                continue
            }
            when {
                inLocal && inRemote -> {
                    if (localFp[id] == remoteFp[id]) continue // identical content
                    val isConflict = lc > lastMergedAtMillis && rc > lastMergedAtMillis
                    val ref = VaultRef(id, remoteNames[id] ?: localNames[id] ?: id)
                    if (isConflict) conflicts.add(ref)
                    // Strictly-newer remote wins; ties keep local.
                    if (rc > lc) toCopy.add(ref)
                }
                inRemote -> {
                    val ref = VaultRef(id, remoteNames[id] ?: id)
                    toCopy.add(ref)
                    newVaults.add(ref)
                }
                // inLocal only, no tombstone: kept as-is.
            }
        }
        SyncDiff(toCopy, newVaults, toDelete, conflicts, tombs.values.toList())
    }

    /**
     * Applies [diff] to the live DB. Closes [remote]'s Room handle (the
     * snapshot file is then ATTACHed directly); the caller still owns
     * [RemoteSnapshot.close] for file cleanup.
     *
     * Atomicity: vault deletes, vault copies, the tombstone-union upsert and
     * the vault_sync_state adoption/cleanup all commit in ONE transaction. A
     * crash mid-merge therefore can't leave copied vaults without sync-state
     * rows (which would let stale content win future merges) or deletions
     * without tombstones (resurrection). Only the favorite_apps adoption is a
     * separate, idempotent transaction — and it is skipped entirely when the
     * remote snapshot isn't newer.
     */
    suspend fun apply(diff: SyncDiff, remote: RemoteSnapshot): Unit = withContext(Dispatchers.IO) {
        // Read the remote library_meta timestamp BEFORE closing the Room
        // handle so the favorite_apps no-op case can skip its ATTACH cycle.
        val remoteMetaTs = queryLong(
            remote.db.openHelper.writableDatabase,
            "SELECT lastWrittenAtMillis FROM library_meta WHERE id = 1",
        )
        remote.db.openHelper.writableDatabase
            .query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
        remote.db.close()
        try {
            applyMergeTransaction(
                sourceDbPath = remote.dbFile.absolutePath,
                copy = diff.toCopy.map { it.vaultId },
                delete = diff.toDelete.map { it.vaultId },
                tombstoneUnion = diff.tombstoneUnion,
            )
            adoptFavoriteAppsIfRemoteNewer(remote.dbFile.absolutePath, remoteMetaTs)
        } finally {
            database.invalidationTracker.refreshVersionsAsync()
        }
    }

    /**
     * Restores ONE vault wholesale from the pre-merge backup file, without
     * touching any other vault and WITHOUT the verbatim sync-table restore
     * [undoFromBackup] performs (that would clobber ALL sync state with
     * week-old backup state). The vault's vault_sync_state row is deliberately
     * left deleted ([deleteVaultTree] inside the copy removes it): the next
     * save's [SyncStateRefresher] recreates it with lastChangedAt = now, so
     * the restored content propagates to other devices instead of losing the
     * next merge to the content it just replaced.
     */
    suspend fun restoreVaultWholesale(backupFile: File, ref: VaultRef): Unit =
        withContext(Dispatchers.IO) {
            try {
                withAttached(backupFile.absolutePath) { db ->
                    val columns = MainColumnLists(db)
                    db.beginTransaction()
                    try {
                        db.execSQL("PRAGMA defer_foreign_keys = ON")
                        copyVaultsWholesale(db, columns, copy = listOf(ref.vaultId), delete = emptyList())
                        // Belt-and-braces: make sure no stale sync-state row
                        // survives (deleteVaultTree already removed it).
                        db.execSQL(
                            "DELETE FROM main.vault_sync_state WHERE vaultId = ${sqlString(ref.vaultId)}"
                        )
                        db.setTransactionSuccessful()
                    } finally {
                        db.endTransaction()
                    }
                }
            } finally {
                database.invalidationTracker.refreshVersionsAsync()
            }
        }

    /**
     * Checkpoints and copies the live DB file to a fresh, timestamped
     * pre-merge backup file (`pre_sync_backup_<millis>.gametwin`). Each sync
     * gets its own backup so older ones survive as whole-library restore
     * points until [pruneBackups] rotates them out (newest [MAX_RESTORE_POINTS]
     * are kept).
     *
     * The file copy runs inside a write transaction so concurrent
     * auto-checkpoints can't rewrite the main DB file mid-copy (torn backup).
     * The backup is consistent as-of the checkpoint; commits landing in the
     * WAL after the checkpoint are not included.
     */
    suspend fun backupLiveDb(): File = withContext(Dispatchers.IO) {
        val db = database.openHelper.writableDatabase
        db.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
        val src = File(db.path!!)
        val dir = File(context.filesDir, "sync").apply { mkdirs() }
        val backup = File(dir, "$BACKUP_FILE_PREFIX${System.currentTimeMillis()}$BACKUP_FILE_SUFFIX")
        db.beginTransaction() // read-only hold: no setTransactionSuccessful needed
        try {
            src.copyTo(backup, overwrite = true)
        } finally {
            db.endTransaction()
        }
        backup
    }

    /**
     * All pre-sync backup files in `filesDir/sync/`, newest first (by the
     * millis embedded in the filename). Non-matching files are ignored.
     */
    fun listBackups(): List<File> {
        val dir = File(context.filesDir, "sync")
        return (dir.listFiles() ?: emptyArray())
            .mapNotNull { f -> backupMillis(f)?.let { f to it } }
            .sortedByDescending { it.second }
            .map { it.first }
    }

    /**
     * Keeps the newest [MAX_RESTORE_POINTS] backups (by the millis embedded in
     * the filename) and deletes the rest. Also deletes the legacy single-file
     * `pre_sync_backup.gametwin` written by pre-restore-points builds — it has
     * no embedded timestamp, so [listBackups] never surfaces it and without
     * this one-time cleanup it would linger forever.
     */
    fun pruneBackups() {
        listBackups().drop(MAX_RESTORE_POINTS).forEach { it.delete() }
        File(File(context.filesDir, "sync"), LEGACY_BACKUP_FILE_NAME).delete()
    }

    /**
     * Restores the WHOLE library from [backupFile], mirroring
     * [com.twinlore.app.data.library.LibraryImporter.importFrom]'s file-replace
     * mechanics on the live DB: checkpoint, close Room, copy the backup's
     * bytes over the live DB file, delete its `-wal`/`-shm` siblings, then
     * clear `vault_sync_state` in the replaced file and wipe both thumbnail
     * caches.
     *
     * WHY the `DELETE FROM vault_sync_state`: the backup's sync-state carries
     * old lastChangedAt values; left in place, the other device's
     * (newer-stamped) snapshot would re-win on its next save and silently
     * undo the restore. Clearing it makes the next save's
     * [SyncStateRefresher] stamp every vault `lastChangedAt = now`, so the
     * restored library propagates as fresh local changes.
     *
     * The replaced file is touched via framework [android.database.sqlite.SQLiteDatabase]
     * because the Room handle is closed by then. The caller MUST restart the
     * process afterwards — every cached DAO still points at the closed handle.
     */
    suspend fun restoreLibraryFromBackup(backupFile: File): Unit = withContext(Dispatchers.IO) {
        // Pre-flight BEFORE closing Room: failing after close() would leave the
        // process with a dead Room handle and no restart (the caller's
        // runCatching would swallow it). A pruned/missing backup must fail here.
        require(backupFile.isFile) { "restore point no longer exists: ${backupFile.name}" }
        val db = database.openHelper.writableDatabase
        db.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
        val target = File(db.path!!)
        database.close()
        backupFile.copyTo(target, overwrite = true)
        target.parentFile?.let { dir ->
            File(dir, "${target.name}-wal").delete()
            File(dir, "${target.name}-shm").delete()
        }
        android.database.sqlite.SQLiteDatabase.openDatabase(
            target.absolutePath,
            null,
            android.database.sqlite.SQLiteDatabase.OPEN_READWRITE,
        ).use { raw ->
            raw.execSQL("DELETE FROM vault_sync_state")
        }
        // Same as LibraryImporter.importFrom: the restored DB's vault ids may
        // not match the live caches; drop all thumbs.
        artworkThumbnailCache.wipeAll()
        mapThumbnailCache.wipeAll()
    }

    // ── internals ─────────────────────────────────────────────────────────────

    /**
     * Map of vaultId -> display name, using the same fallback as [Vault.displayName]
     * (name -> romFilename -> romPath basename). The raw `name` column can be blank for
     * ROM-imported vaults, which both reads poorly in the conflict/delete UI and — because
     * the media-index bindings are keyed on displayName — would make the post-merge
     * binding cleanup a no-op for those vaults. Resolving it here keeps both consistent.
     */
    private fun vaultNames(db: SupportSQLiteDatabase): Map<String, String> {
        val out = mutableMapOf<String, String>()
        db.query("SELECT id, name, romFilename, romPath FROM vaults").use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0)
                val name = c.getString(1)
                val romFilename = if (c.isNull(2)) null else c.getString(2)
                val romPath = if (c.isNull(3)) null else c.getString(3)
                out[id] = when {
                    !name.isNullOrBlank() -> name
                    romFilename != null -> romFilename
                    romPath != null -> romPath.substringAfterLast('/')
                    else -> id
                }
            }
        }
        return out
    }

    /**
     * Runs [block] on the live DB with the file at [sourceDbPath] ATTACHed as
     * `src`. ATTACH/DETACH must stay OUTSIDE any transaction (SQLite forbids
     * ATTACH inside one); DETACH runs in a finally.
     *
     * Mechanism (verified empirically, Robolectric AND framework behavior):
     * AOSP SQLiteDatabase special-cases ATTACH — it sets a sticky
     * attached-dbs flag on the connection pool and calls
     * disableWriteAheadLogging(). That pool reconfiguration:
     *  1. Resets per-connection pragmas, dropping the raw
     *     `PRAGMA foreign_keys = ON` Room applied at open — FK enforcement
     *     AND ON DELETE CASCADE are silently OFF inside [block], so callers
     *     must delete child rows explicitly (see [deleteVaultTree]). We
     *     re-apply the pragma in the outer finally so post-merge app writes
     *     get FK enforcement back even if ATTACH itself throws (the framework
     *     reconfigures the connection even on a failed ATTACH) or DETACH
     *     throws.
     *  2. Leaves WAL OFF for the remainder of the process. Acceptable: merges
     *     happen at app-open; WAL returns on the next process start.
     *
     * Single-flight by contract: the attach alias `src` and the snapshot copy
     * name `sync_remote_tmp` are fixed, so callers must serialize merges —
     * the sync coordinator holds the library save mutex for the whole merge.
     */
    private inline fun <T> withAttached(sourceDbPath: String, block: (SupportSQLiteDatabase) -> T): T {
        val db = database.openHelper.writableDatabase
        try {
            db.execSQL("ATTACH DATABASE ${sqlString(sourceDbPath)} AS src")
            try {
                return block(db)
            } finally {
                runCatching { db.execSQL("DETACH DATABASE src") }
            }
        } finally {
            db.execSQL("PRAGMA foreign_keys = ON")
        }
    }

    /**
     * Lazily-built cache of explicit column lists, in the MAIN side's
     * declared order, one per attach cycle (column order can differ between a
     * fresh install and a long-migrated device — `SELECT *` maps by POSITION
     * across ATTACHed DBs and would silently scramble columns; selecting by
     * NAME from `src` is order-proof).
     */
    private class MainColumnLists(private val db: SupportSQLiteDatabase) {
        private val cache = mutableMapOf<String, String>()

        /** Comma-joined, backtick-quoted column list for `main`.[table]. */
        fun of(table: String): String = cache.getOrPut(table) {
            val cols = mutableListOf<String>()
            db.query("PRAGMA main.table_info(`$table`)").use { c ->
                val nameIdx = c.getColumnIndexOrThrow("name")
                while (c.moveToNext()) cols.add("`${c.getString(nameIdx)}`")
            }
            check(cols.isNotEmpty()) { "No columns found for table $table" }
            cols.joinToString(",")
        }
    }

    /**
     * Explicitly deletes a vault and every vault-scoped child row, leaf-to-root.
     * Mirrors what ON DELETE CASCADE would do — except cascade is unavailable
     * inside ATTACH transactions (see [withAttached]) — plus browser_tabs and
     * vault_sync_state. Additive caches are deliberately NOT deleted (no FK,
     * never deleted by design).
     *
     * [vaultLiteral] is an already-escaped SQL string literal.
     */
    private fun deleteVaultTree(db: SupportSQLiteDatabase, vaultLiteral: String) {
        for (t in SyncSchema.VAULT_SCOPED.asReversed()) {
            val where = SyncSchema.whereFor(t, prefix = "main.").replaceFirst("?", vaultLiteral)
            db.execSQL("DELETE FROM main.${t.name} WHERE $where")
        }
        db.execSQL("DELETE FROM main.browser_tabs WHERE vaultId = $vaultLiteral")
        db.execSQL("DELETE FROM main.vault_sync_state WHERE vaultId = $vaultLiteral")
        db.execSQL("DELETE FROM main.vaults WHERE id = $vaultLiteral")
    }

    /**
     * The whole merge in ONE attached transaction: vault deletes, wholesale
     * vault copies, the tombstone-union upsert, the vault_sync_state adoption
     * for copied vaults and the sync-state cleanup for deleted vaults. Either
     * all of it commits or none of it does.
     */
    private fun applyMergeTransaction(
        sourceDbPath: String,
        copy: List<String>,
        delete: List<String>,
        tombstoneUnion: List<SyncTombstoneEntity>,
    ) {
        if (copy.isEmpty() && delete.isEmpty() && tombstoneUnion.isEmpty()) return
        withAttached(sourceDbPath) { db ->
            val columns = MainColumnLists(db)
            db.beginTransaction()
            try {
                // Belt-and-braces: if FK enforcement is ever active in this
                // transaction, defer the checks to commit so the
                // delete-parent-then-reinsert-children window passes.
                db.execSQL("PRAGMA defer_foreign_keys = ON")
                copyVaultsWholesale(db, columns, copy, delete)
                // Tombstone union via raw SQL (NOT the DAO — Room's own
                // transaction machinery must not run mid-raw-transaction),
                // keep-newest guard matching SyncTombstoneDao.upsert.
                for (t in tombstoneUnion) {
                    db.execSQL(
                        "INSERT INTO main.sync_tombstones (vaultId, deletedAt, deviceId) " +
                            "VALUES (${sqlString(t.vaultId)}, ${t.deletedAt}, ${sqlString(t.deviceId)}) " +
                            "ON CONFLICT(vaultId) DO UPDATE SET " +
                            "deletedAt = excluded.deletedAt, deviceId = excluded.deviceId " +
                            "WHERE excluded.deletedAt > sync_tombstones.deletedAt"
                    )
                }
                // deleteVaultTree cleared the replaced vaults' old
                // vault_sync_state rows; adopt the remote's rows so the
                // copied content keeps the REMOTE lastChangedAt (a fresh
                // local stamp would let stale content win future merges).
                if (copy.isNotEmpty()) {
                    val inList = copy.joinToString(",") { sqlString(it) }
                    val cols = columns.of("vault_sync_state")
                    db.execSQL(
                        "INSERT OR REPLACE INTO main.vault_sync_state ($cols) " +
                            "SELECT $cols FROM src.vault_sync_state WHERE vaultId IN ($inList)"
                    )
                }
                // deleteVaultTree already removed these; belt-and-braces.
                if (delete.isNotEmpty()) {
                    val inList = delete.joinToString(",") { sqlString(it) }
                    db.execSQL("DELETE FROM main.vault_sync_state WHERE vaultId IN ($inList)")
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }

    /**
     * Deletes [delete] vaults and replaces [copy] vaults wholesale from the
     * ATTACHed `src` DB. Local browser_tabs rows for replaced vaults are
     * preserved via a temp table (device-local by design). Must run inside an
     * already-open transaction on [db] (with deferred FK checks).
     */
    private fun copyVaultsWholesale(
        db: SupportSQLiteDatabase,
        columns: MainColumnLists,
        copy: List<String>,
        delete: List<String>,
    ) {
        for (id in delete) {
            deleteVaultTree(db, sqlString(id))
        }
        for (id in copy) {
            val v = sqlString(id)
            val browserCols = columns.of("browser_tabs")
            db.execSQL("DROP TABLE IF EXISTS keep_browser")
            db.execSQL(
                "CREATE TEMP TABLE keep_browser AS " +
                    "SELECT * FROM main.browser_tabs WHERE vaultId = $v"
            )
            deleteVaultTree(db, v)
            val vaultCols = columns.of("vaults")
            db.execSQL(
                "INSERT INTO main.vaults ($vaultCols) " +
                    "SELECT $vaultCols FROM src.vaults WHERE id = $v"
            )
            for (t in SyncSchema.VAULT_SCOPED) {
                val where = SyncSchema.whereFor(t, prefix = "src.").replaceFirst("?", v)
                val cols = columns.of(t.name)
                db.execSQL(
                    "INSERT INTO main.${t.name} ($cols) " +
                        "SELECT $cols FROM src.${t.name} WHERE $where"
                )
            }
            // resource_files paths are the only path-bearing columns sanitized
            // HERE: absolute_path is device-local by definition
            // (SyncSchema.VOLATILE_COLUMNS) and is re-derived on the next scan;
            // home_relative_path is shared, but only as a plain relative path.
            //
            // They are NOT the only path-bearing columns a snapshot carries.
            // saved_tabs.group_folder_name and vaults.folder_name are copied
            // wholesale above and are not sanitized here or on read --
            // LibraryFolderManager.resolveFolderName deliberately prefers the
            // persisted column over the sanitizer, so a dot-dot guard on the
            // sanitizer never sees them. Those two are guarded at the point of
            // use instead, by the containment check in
            // PageGroupSaveTargets.resolveDirUnderHome.
            db.execSQL(
                "UPDATE main.resource_files SET absolute_path = NULL, last_seen_at = 0 " +
                    "WHERE vault_id = $v"
            )
            db.execSQL(
                "UPDATE main.resource_files SET home_relative_path = NULL " +
                    "WHERE vault_id = $v AND (" +
                    "home_relative_path LIKE '/%' OR " +
                    "home_relative_path = '..' OR " +
                    "home_relative_path LIKE '../%' OR " +
                    "home_relative_path LIKE '%/..' OR " +
                    "home_relative_path LIKE '%/../%')"
            )
            for (t in SyncSchema.ADDITIVE_CACHES) {
                val where = t.where.replaceFirst("?", v)
                val cols = columns.of(t.name)
                db.execSQL(
                    "INSERT OR REPLACE INTO main.${t.name} ($cols) " +
                        "SELECT $cols FROM src.${t.name} WHERE $where"
                )
            }
            // keep_browser's column order is main's (CREATE..AS SELECT from
            // main), but stay order-proof here too.
            db.execSQL(
                "INSERT INTO main.browser_tabs ($browserCols) " +
                    "SELECT $browserCols FROM keep_browser"
            )
            db.execSQL("DROP TABLE IF EXISTS keep_browser")
        }
    }

    /**
     * Whole-table newest-snapshot-wins for the global favorite_apps table.
     * No-ops WITHOUT attaching when the remote snapshot isn't newer.
     * [remoteMetaTs] is read by the caller before the remote Room handle is
     * closed.
     */
    private fun adoptFavoriteAppsIfRemoteNewer(sourceDbPath: String, remoteMetaTs: Long) {
        val localTs = queryLong(
            database.openHelper.writableDatabase,
            "SELECT lastWrittenAtMillis FROM library_meta WHERE id = 1",
        )
        if (remoteMetaTs <= localTs) return
        withAttached(sourceDbPath) { db ->
            val cols = MainColumnLists(db).of("favorite_apps")
            db.beginTransaction()
            try {
                db.execSQL("DELETE FROM main.favorite_apps")
                db.execSQL(
                    "INSERT INTO main.favorite_apps ($cols) SELECT $cols FROM src.favorite_apps"
                )
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }

    private fun queryLong(db: SupportSQLiteDatabase, sql: String): Long =
        db.query(sql).use { c -> if (c.moveToFirst()) c.getLong(0) else 0L }

    /** Single-quoted SQL string literal with embedded quotes escaped. */
    private fun sqlString(value: String): String = "'${value.replace("'", "''")}'"

    companion object {
        const val BACKUP_FILE_PREFIX = "pre_sync_backup_"
        const val BACKUP_FILE_SUFFIX = ".gametwin"

        /** Count-based retention: the newest N restore points are kept (). */
        const val MAX_RESTORE_POINTS = 3

        /** Un-timestamped backup name written by pre-restore-points builds; cleaned up by [pruneBackups]. */
        const val LEGACY_BACKUP_FILE_NAME = "pre_sync_backup.gametwin"

        private val BACKUP_NAME_REGEX = Regex("""pre_sync_backup_(\d+)\.gametwin""")

        /** Millis embedded in a backup filename, or null for non-backup files. */
        fun backupMillis(file: File): Long? =
            BACKUP_NAME_REGEX.matchEntire(file.name)?.groupValues?.get(1)?.toLongOrNull()
    }
}
