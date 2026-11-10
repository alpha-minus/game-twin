package com.twinlore.app.data.library

import android.content.Context
import android.net.Uri
import com.twinlore.app.data.local.SCHEMA_VERSION
import com.twinlore.app.data.local.VaultDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thrown by [LibraryImporter.importFrom] when the newest snapshot in the
 * picked folder was written by an app build whose Room schema version is
 * higher than this build can read.
 *
 * Copying such a file over the live DB would trigger Room's
 * `fallbackToDestructiveMigration` on the next open — silently wiping the
 * snapshot. Cold-start auto-restore would then see an empty DB plus a
 * populated library folder and re-trigger the import on every launch,
 * producing the startup loop users have reported.
 *
 * Callers catch this specifically to surface a "this library was saved by a
 * newer app version; update the app" message and to suppress the
 * post-import restart.
 */
class LibrarySnapshotTooNewException(
    val savedVersion: Int,
    val supportedVersion: Int,
) : IOException(
    "Library snapshot was saved by a newer app version " +
        "(schemaVersion=$savedVersion, this build supports up to $supportedVersion). " +
        "Update Game Twin to read this library."
)

/**
 * Restores the live library DB from the newest snapshot inside the given
 * folder. Newest is determined by embedded `library_meta.lastWrittenAtMillis`
 * via [findSnapshotsNewestFirst]; cloud-sync mangles filesystem mtime so the
 * embedded value is the source of truth. Closes Room after restoring; the
 * caller MUST restart the process or otherwise re-open Room.
 */
@Singleton
class LibraryImporter(
    private val context: Context,
    private val targetDbProvider: () -> File,
    private val checkpoint: suspend () -> Unit,
    private val closeDb: () -> Unit,
    private val artworkThumbnailCache: ArtworkThumbnailCache,
    private val mapThumbnailCache: MapThumbnailCache,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        database: VaultDatabase,
        artworkThumbnailCache: ArtworkThumbnailCache,
        mapThumbnailCache: MapThumbnailCache,
    ) : this(
        context = context,
        targetDbProvider = { context.getDatabasePath("vault_database") },
        checkpoint = {
            withContext(Dispatchers.IO) {
                database.openHelper.writableDatabase
                    .query("PRAGMA wal_checkpoint(TRUNCATE)")
                    .use { it.moveToFirst() }
            }
        },
        closeDb = { database.close() },
        artworkThumbnailCache = artworkThumbnailCache,
        mapThumbnailCache = mapThumbnailCache,
    )

    suspend fun importFrom(folderUri: Uri): Unit = withContext(Dispatchers.IO) {
        val target = targetDbProvider()
        if (target.exists()) checkpoint()
        // Empty currentShortDeviceId intentionally: this is the load-from-folder
        // path where "our" snapshot is undefined (fresh install or explicit
        // adoption of the folder's identity), so we disable the our-device
        // tie-breaker and fall through to lexical filename order.
        val newest = findSnapshotsNewestFirst(context, folderUri, currentShortDeviceId = "")
            .firstOrNull()
            ?: error("no library snapshot found at $folderUri")
        // Refuse newer-schema snapshots BEFORE closeDb() + the file copy.
        // Letting the copy proceed would have Room destructive-migrate the
        // too-new file into oblivion on next open, and the cold-start
        // auto-restore would re-import the same too-new file every launch
        // (empty DB + populated folder = restore trigger). Reading the
        // version a second time here (the resolve pass also read it via
        // readMetadata) is one extra IO open per import — fine, importFrom
        // runs at most once per cold-start or user-triggered load.
        val savedVersion = readSnapshotSchemaVersion(context, newest)
        if (savedVersion != null && savedVersion > SCHEMA_VERSION) {
            throw LibrarySnapshotTooNewException(
                savedVersion = savedVersion,
                supportedVersion = SCHEMA_VERSION,
            )
        }
        // Loading a library of any size is unconditional (no vault-count cap).
        val input = openSnapshotStream(newest)
            ?: error("snapshot file not readable at $newest")
        closeDb()
        input.use { stream ->
            target.outputStream().use { out -> stream.copyTo(out) }
        }
        target.parentFile?.let { dir ->
            File(dir, "${target.name}-wal").delete()
            File(dir, "${target.name}-shm").delete()
        }
        // The imported DB may have entirely different vault ids than the live
        // one we just replaced. Drop all cached thumbs so stale entries from
        // the previous library can't pose as valid thumbnails for vault ids
        // that no longer exist.
        artworkThumbnailCache.wipeAll()
        mapThumbnailCache.wipeAll()
    }

    private fun openSnapshotStream(uri: Uri): InputStream? = when (uri.scheme) {
        "file" -> uri.path?.let(::File)?.takeIf { it.isFile }?.inputStream()
        "content" -> context.contentResolver.openInputStream(uri)
        else -> null
    }
}
