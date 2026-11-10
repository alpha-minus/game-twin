package com.twinlore.app.data.library

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Returns all library snapshot candidates inside [folderUri] (file:// dir or
 * SAF tree), sorted newest-first by the embedded
 * `library_meta.lastWrittenAtMillis`. Cloud sync mangles filesystem mtime so
 * the embedded timestamp is the only reliable order key.
 *
 * Tie-breaker for identical timestamps: the snapshot whose
 * `lastWrittenDeviceId` matches [currentShortDeviceId] sorts first, then
 * lexical filename order.
 *
 * Unreadable / non-SQLite files are skipped silently.
 */
internal suspend fun findSnapshotsNewestFirst(
    context: Context,
    folderUri: Uri,
    currentShortDeviceId: String
): List<Uri> = withContext(Dispatchers.IO) {
    val candidates = listCandidateUris(context, folderUri)
    candidates
        .mapNotNull { uri ->
            val md = readMetadata(context, uri) ?: return@mapNotNull null
            val short = LibraryFileStore.shortDeviceId(md.deviceId)
            Triple(uri, md.lastWrittenAt, short)
        }
        .sortedWith(
            compareByDescending<Triple<Uri, Long, String>> { it.second }
                .thenByDescending { it.third == currentShortDeviceId }
                .thenBy { uriFileName(it.first) }
        )
        .map { it.first }
}

/**
 * Reads just the short deviceId from the snapshot at [snapshotUri]. Returns
 * null when the file is missing, corrupted, or lacks a `library_meta` row.
 * Used by [LibraryFileStore] to decide whether the legacy bare
 * `library.gametwin` belongs to this install (and is therefore safe to
 * delete on the first per-device save).
 */
internal suspend fun readSnapshotShortDeviceId(
    context: Context,
    snapshotUri: Uri
): String? = withContext(Dispatchers.IO) {
    val md = readMetadata(context, snapshotUri) ?: return@withContext null
    LibraryFileStore.shortDeviceId(md.deviceId).ifEmpty { null }
}

/**
 * Reads just the [LibraryMetaEntity.schemaVersion] field from the snapshot at
 * [snapshotUri]. Returns null when the file is missing, corrupted, lacks a
 * `library_meta` row, or the column doesn't exist (pre-schemaVersion bare
 * snapshots written by very old builds — see [LibraryAutoSaveObserver]).
 *
 * Used by [LibraryImporter] to refuse files written by newer app versions
 * BEFORE copying them over the live DB, so Room's destructive-migration
 * fallback can't silently wipe a newer-schema snapshot and trigger the
 * cold-start auto-restore loop (auto-restore sees an empty DB + a populated
 * folder and tries to import the same too-new file again).
 */
internal suspend fun readSnapshotSchemaVersion(
    context: Context,
    snapshotUri: Uri
): Int? = withContext(Dispatchers.IO) {
    readMetadata(context, snapshotUri)?.schemaVersion
}

/**
 * Reads the number of `vaults` rows in the snapshot at [snapshotUri]. Returns
 * null when the file is missing, corrupted, or lacks a `vaults` table.
 *
 * Used by [LibraryImporter] and
 * [com.twinlore.app.data.library.sync.LibrarySyncCoordinator] to gate
 * over-cap libraries on unlicensed installs
 * (the former free vault cap of 2) before any
 * file copy / merge happens.
 */
internal suspend fun readSnapshotVaultCount(
    context: Context,
    snapshotUri: Uri
): Int? = withContext(Dispatchers.IO) {
    var localFile: File? = null
    var db: SQLiteDatabase? = null
    try {
        localFile = materialize(context, snapshotUri) ?: return@withContext null
        db = SQLiteDatabase.openDatabase(
            localFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY
        )
        db.rawQuery("SELECT COUNT(*) FROM vaults", null).use { cursor ->
            if (!cursor.moveToFirst()) return@withContext null
            cursor.getInt(0)
        }
    } catch (_: Exception) {
        null
    } finally {
        db?.close()
        if (snapshotUri.scheme == "content") localFile?.delete()
    }
}

data class LibrarySnapshotMeta(
    val uri: Uri,
    val fileName: String,
    val deviceId: String,
    val deviceLabel: String,
    val lastWrittenAtMillis: Long,
    val schemaVersion: Int,
)

internal suspend fun findSnapshotMetasNewestFirst(
    context: Context,
    folderUri: Uri,
): List<LibrarySnapshotMeta> = withContext(Dispatchers.IO) {
    listCandidateUris(context, folderUri)
        .mapNotNull { uri ->
            val md = readMetadata(context, uri) ?: return@mapNotNull null
            LibrarySnapshotMeta(
                uri = uri,
                fileName = uriFileName(uri),
                deviceId = md.deviceId,
                deviceLabel = md.deviceLabel,
                lastWrittenAtMillis = md.lastWrittenAt,
                schemaVersion = md.schemaVersion,
            )
        }
        .sortedWith(
            compareByDescending<LibrarySnapshotMeta> { it.lastWrittenAtMillis }
                .thenBy { it.fileName } // deterministic order for equal timestamps
        )
}

// ---- internals ----

private data class SnapshotMeta(
    val lastWrittenAt: Long,
    val deviceId: String,
    val schemaVersion: Int,
    val deviceLabel: String,
)

private fun listCandidateUris(context: Context, folderUri: Uri): List<Uri> =
    when (folderUri.scheme) {
        "file" -> folderUri.path?.let(::File)
            ?.takeIf { it.isDirectory }
            ?.listFiles()
            ?.filter { it.isFile && isSnapshotName(it.name) }
            ?.map { Uri.fromFile(it) }
            ?: emptyList()
        "content" -> DocumentFile.fromTreeUri(context, folderUri)
            ?.listFiles()
            ?.filter { it.isFile && it.name != null && isSnapshotName(it.name!!) }
            ?.map { it.uri }
            ?: emptyList()
        else -> emptyList()
    }

private fun isSnapshotName(name: String): Boolean =
    name == LibraryFileStore.BARE_FILE_NAME || LibraryFileStore.isPerDeviceSnapshotName(name)

private fun uriFileName(uri: Uri): String =
    uri.lastPathSegment?.substringAfterLast('/') ?: ""

private suspend fun readMetadata(context: Context, snapshotUri: Uri): SnapshotMeta? =
    withContext(Dispatchers.IO) {
        var localFile: File? = null
        var db: SQLiteDatabase? = null
        try {
            localFile = materialize(context, snapshotUri) ?: return@withContext null
            db = SQLiteDatabase.openDatabase(
                localFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY
            )
            // schemaVersion was always part of library_meta (added with the
            // table itself at SCHEMA_VERSION = 1), so the column is present
            // for every snapshot the app has ever written. Read as Int with
            // a null-safe fallback so a corrupted row reads as version 0
            // rather than crashing the resolve pass.
            db.rawQuery(
                "SELECT lastWrittenAtMillis, lastWrittenDeviceId, schemaVersion, lastWrittenDeviceLabel FROM library_meta LIMIT 1",
                null
            ).use { cursor ->
                if (!cursor.moveToFirst()) return@withContext null
                val metaSchema = if (cursor.isNull(2)) 0 else cursor.getInt(2)
                // PRAGMA user_version is Room's authoritative on-disk schema
                // version: it's the value Room checks when it opens the DB and
                // what triggers destructive migration on a mismatch. The
                // app-written library_meta.schemaVersion can lag behind it (a
                // Room migration bumps user_version, but the row is only
                // rewritten on the next save), so a genuinely too-new DB could
                // read as <= this build here, slip past the import guard, get
                // copied over the live DB, then destructive-migrate to an empty
                // DB on open — the infinite cold-start re-import loop. Take the
                // max so the higher (true) version always wins the too-new check.
                val userVersion = readUserVersion(db)
                SnapshotMeta(
                    lastWrittenAt = cursor.getLong(0),
                    deviceId = cursor.getString(1) ?: "",
                    schemaVersion = maxOf(metaSchema, userVersion),
                    deviceLabel = if (cursor.isNull(3)) "" else cursor.getString(3),
                )
            }
        } catch (_: Exception) {
            null
        } finally {
            db?.close()
            if (snapshotUri.scheme == "content") localFile?.delete()
        }
    }

/** Reads Room's authoritative on-disk schema version (`PRAGMA user_version`). */
private fun readUserVersion(db: SQLiteDatabase): Int =
    db.rawQuery("PRAGMA user_version", null).use { c ->
        if (c.moveToFirst()) c.getInt(0) else 0
    }

private fun materialize(context: Context, uri: Uri): File? = when (uri.scheme) {
    "file" -> uri.path?.let(::File)?.takeIf { it.isFile }
    "content" -> {
        val tmp = File.createTempFile("snap", ".gametwin", context.cacheDir)
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                tmp.outputStream().use { out -> input.copyTo(out) }
            } ?: run { tmp.delete(); return@materialize null }
            tmp
        } catch (_: Exception) {
            tmp.delete()
            null
        }
    }
    else -> null
}
