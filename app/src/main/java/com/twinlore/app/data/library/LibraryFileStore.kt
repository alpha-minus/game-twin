package com.twinlore.app.data.library

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class LibraryFileSaveOutcome(val fileUri: Uri, val sizeBytes: Long)

/**
 * Single-file persistence for the library snapshot. Writes to
 * `library.<shortDeviceId>.gametwin` so two devices sharing a synced folder
 * never overwrite each other's snapshot. Atomic `.partial` -> rename. The
 * caller (auto-save observer or explicit Save button) is expected to:
 *   1. Update the library_meta row in Room.
 *   2. PRAGMA wal_checkpoint(TRUNCATE) so the DB file is self-contained.
 *   3. Pass the DB file to [save] with the device UUID.
 *
 * Migration: on the first per-device save, if a legacy bare
 * `library.gametwin` exists and its embedded `lastWrittenDeviceId` matches
 * ours, atomically rename it to the per-device name before writing. If a
 * per-device file is already present, the bare file is redundant and is
 * deleted instead. Keeping the bare file would re-import after a Reset
 * Library, since the load path would still find it.
 *
 * Testing note: unit tests in `LibraryFileStoreTest` cover only the
 * `file://` branch. The SAF `content://` branches (save, deleteIfPresent,
 * and `cleanupBareIfOurs`) are exercised by the on-device validation matrix
 * in the per-device-library-snapshots plan, not by Robolectric. Changes
 * touching the SAF branches should be re-validated on hardware.
 */
@Singleton
class LibraryFileStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val folder: LibraryFolderManager
) {

    suspend fun save(sourceDb: File, deviceUuid: String): LibraryFileSaveOutcome? =
        withContext(Dispatchers.IO) {
            val homeUri = folder.homeFolderUri() ?: return@withContext null
            val targetName = fileName(deviceUuid)
            cleanupBareIfOurs(homeUri, deviceUuid)
            when (homeUri.scheme) {
                "file" -> saveToFileHome(homeUri, sourceDb, targetName)
                "content" -> saveToSafHome(homeUri, sourceDb, targetName)
                else -> null
            }
        }

    suspend fun deleteIfPresent(deviceUuid: String): Boolean = withContext(Dispatchers.IO) {
        val homeUri = folder.homeFolderUri() ?: return@withContext false
        val targetName = fileName(deviceUuid)
        when (homeUri.scheme) {
            "file" -> {
                val home = homeUri.path?.let(::File) ?: return@withContext false
                val file = File(home, targetName)
                if (file.isFile) file.delete() else false
            }
            "content" -> {
                val root = DocumentFile.fromTreeUri(context, homeUri) ?: return@withContext false
                root.findFile(targetName)?.takeIf { it.isFile }?.delete() ?: false
            }
            else -> false
        }
    }

    private suspend fun cleanupBareIfOurs(homeUri: Uri, deviceUuid: String) {
        val ourShort = shortDeviceId(deviceUuid)
        if (ourShort.isEmpty()) return
        val targetName = fileName(deviceUuid)
        when (homeUri.scheme) {
            "file" -> {
                val home = homeUri.path?.let(::File) ?: return
                val bare = File(home, BARE_FILE_NAME).takeIf { it.isFile } ?: return
                val theirShort = readSnapshotShortDeviceId(context, Uri.fromFile(bare)) ?: return
                if (theirShort != ourShort) return
                val target = File(home, targetName)
                if (target.exists()) {
                    bare.delete()
                } else {
                    // Atomic move so there is no window where the bare file is
                    // gone but the per-device file isn't there yet. If the move
                    // fails (exotic SAF / FS errors), leave the bare alone: the
                    // save() call about to run will write the per-device file
                    // beside it, and the next successful save will promote bare
                    // correctly. Deleting bare on move failure could leave the
                    // user with zero snapshots if that save also fails.
                    runCatching {
                        java.nio.file.Files.move(
                            bare.toPath(), target.toPath(),
                            java.nio.file.StandardCopyOption.ATOMIC_MOVE
                        )
                    }.onFailure {
                        Log.w(
                            "LibraryFileStore",
                            "atomic move of bare snapshot failed; leaving in place",
                            it
                        )
                    }
                }
            }
            "content" -> {
                val tree = DocumentFile.fromTreeUri(context, homeUri) ?: return
                val bare = tree.findFile(BARE_FILE_NAME)?.takeIf { it.isFile } ?: return
                val theirShort = readSnapshotShortDeviceId(context, bare.uri) ?: return
                if (theirShort != ourShort) return
                val existingTarget = tree.findFile(targetName)
                if (existingTarget != null) {
                    bare.delete()
                } else {
                    // See file:// branch above: leave bare alone on rename
                    // failure so the user always has at least one valid
                    // snapshot on disk.
                    runCatching {
                        DocumentsContract.renameDocument(context.contentResolver, bare.uri, targetName)
                    }.onFailure {
                        Log.w(
                            "LibraryFileStore",
                            "SAF rename of bare snapshot failed; leaving in place",
                            it
                        )
                    }
                }
            }
        }
    }

    private fun saveToFileHome(homeUri: Uri, sourceDb: File, targetName: String): LibraryFileSaveOutcome? {
        val home = File(homeUri.path ?: return null).apply { mkdirs() }
        val partial = File(home, "$targetName$PARTIAL_SUFFIX")
        val target = File(home, targetName)
        if (partial.exists()) partial.delete()
        return runCatching {
            sourceDb.copyTo(partial, overwrite = true)
            try {
                java.nio.file.Files.move(
                    partial.toPath(), target.toPath(),
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE
                )
            } catch (_: Exception) {
                partial.delete()
                return@runCatching null
            }
            LibraryFileSaveOutcome(Uri.fromFile(target), target.length())
        }.getOrNull()
    }

    private suspend fun saveToSafHome(
        homeUri: Uri, sourceDb: File, targetName: String
    ): LibraryFileSaveOutcome? {
        val root = DocumentFile.fromTreeUri(context, homeUri) ?: return null
        val partialName = "$targetName$PARTIAL_SUFFIX"
        root.findFile(partialName)?.delete()
        root.findFile(targetName)?.delete()
        val partial = root.createFile("application/octet-stream", partialName) ?: return null
        val ok = context.contentResolver.openOutputStream(partial.uri)?.use { out ->
            sourceDb.inputStream().use { it.copyTo(out) }; true
        } ?: false
        if (!ok) { partial.delete(); return null }
        val renamed = runCatching {
            DocumentsContract.renameDocument(context.contentResolver, partial.uri, targetName)
        }.getOrNull() ?: run { partial.delete(); return null }
        val finalDoc = DocumentFile.fromSingleUri(context, renamed) ?: return null
        return LibraryFileSaveOutcome(renamed, finalDoc.length())
    }

    companion object {
        const val BARE_FILE_NAME = "library.gametwin"
        private const val PARTIAL_SUFFIX = ".partial"
        private val PER_DEVICE_REGEX = Regex("^library\\.[0-9a-f]{8}\\.gametwin$")

        fun shortDeviceId(deviceUuid: String): String {
            val trimmed = deviceUuid.trim()
            if (trimmed.isEmpty()) return ""
            return trimmed.replace("-", "").lowercase().take(8)
        }

        fun fileName(deviceUuid: String): String {
            val short = shortDeviceId(deviceUuid)
            return if (short.isEmpty()) BARE_FILE_NAME else "library.$short.gametwin"
        }

        fun isPerDeviceSnapshotName(name: String): Boolean = PER_DEVICE_REGEX.matches(name)
    }
}
