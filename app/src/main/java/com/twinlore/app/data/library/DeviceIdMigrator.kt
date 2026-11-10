package com.twinlore.app.data.library

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.twinlore.app.data.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One-time switch back from ANDROID_ID device ids to per-install random UUIDs.
 * Reading ANDROID_ID for the snapshot id is access to terminal equipment under
 * § 25 TDDG without a consent exemption (privacy policy §5), so no code path
 * reads it for this purpose anymore; installs that already stored one get a
 * fresh UUID and their snapshot renamed, which also removes the derived value
 * from file names in cloud-synced folders.
 *
 * Detection is by shape alone: ANDROID_ID is 16 lowercase hex chars, a UUID is
 * 36 with dashes. Comparing against the live ANDROID_ID would itself be the
 * access this class exists to remove.
 */
@Singleton
class DeviceIdMigrator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val folder: LibraryFolderManager,
) {
    /** Run once at startup, before the first save or sync scan. */
    suspend fun migrateIfNeeded() {
        val stored = settingsRepository.getOrCreateDeviceId()
        if (!ANDROID_ID_SHAPE.matches(stored)) return
        val newId = java.util.UUID.randomUUID().toString()
        folder.homeFolderUri()?.let { home ->
            migrateSnapshotFileName(context, home, oldDeviceId = stored, newDeviceId = newId)
        }
        settingsRepository.overwriteDeviceId(newId)
    }

    companion object {
        internal val ANDROID_ID_SHAPE = Regex("^[0-9a-f]{16}$")
    }
}

/**
 * Renames library.<old8>.gametwin to library.<new8>.gametwin when its
 * embedded lastWrittenDeviceId matches [oldDeviceId]; deletes it instead if
 * the new name already exists; leaves foreign files alone. Mirrors
 * LibraryFileStore.cleanupBareIfOurs (file:// + SAF branches).
 */
internal suspend fun migrateSnapshotFileName(
    context: Context,
    homeUri: Uri,
    oldDeviceId: String,
    newDeviceId: String,
): Unit = withContext(Dispatchers.IO) {
    val oldName = LibraryFileStore.fileName(oldDeviceId)
    val newName = LibraryFileStore.fileName(newDeviceId)
    if (oldName == newName || oldName == LibraryFileStore.BARE_FILE_NAME) return@withContext
    when (homeUri.scheme) {
        "file" -> {
            val home = homeUri.path?.let(::File) ?: return@withContext
            val old = File(home, oldName).takeIf { it.isFile } ?: return@withContext
            val writer = readSnapshotShortDeviceId(context, Uri.fromFile(old)) ?: return@withContext
            if (writer != LibraryFileStore.shortDeviceId(oldDeviceId)) return@withContext
            val target = File(home, newName)
            if (target.exists()) old.delete()
            else runCatching {
                java.nio.file.Files.move(
                    old.toPath(), target.toPath(),
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE
                )
            }
        }
        "content" -> {
            val tree = DocumentFile.fromTreeUri(context, homeUri) ?: return@withContext
            val old = tree.findFile(oldName)?.takeIf { it.isFile } ?: return@withContext
            val writer = readSnapshotShortDeviceId(context, old.uri) ?: return@withContext
            if (writer != LibraryFileStore.shortDeviceId(oldDeviceId)) return@withContext
            if (tree.findFile(newName) != null) old.delete()
            else runCatching {
                android.provider.DocumentsContract.renameDocument(
                    context.contentResolver, old.uri, newName
                )
            }
        }
    }
}
