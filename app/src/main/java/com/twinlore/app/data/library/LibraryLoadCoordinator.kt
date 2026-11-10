package com.twinlore.app.data.library

import android.net.Uri
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads a library from a user-picked folder and then adopts that folder as the
 * home folder.
 *
 * Order is load-bearing: [LibraryImporter.importFrom] runs FIRST so its
 * too-new guard ([LibrarySnapshotTooNewException]) — and any other read
 * failure — throws BEFORE [StorageFolderMigrationCoordinator.switchWithoutMigration]
 * persists the new home-folder URI. A refused load therefore leaves the
 * previous home folder untouched instead of stranding the app on an
 * unreadable folder, where the next auto-save would overwrite the existing
 * library. See [com.twinlore.app.ui.startup.LibrarySnapshotTooNewDialog].
 */
@Singleton
class LibraryLoadCoordinator @Inject constructor(
    private val importer: LibraryImporter,
    private val storageFolders: StorageFolderMigrationCoordinator,
) {
    /**
     * Restore the live DB from the newest snapshot in [folderUri], then switch
     * the home folder to [folderUri]. Propagates any exception from the import
     * step without having touched the home folder. The caller MUST restart the
     * process afterwards (import closes Room).
     */
    suspend fun loadFromFolder(folderUri: Uri) {
        importer.importFrom(folderUri)
        storageFolders.switchWithoutMigration(folderUri)
    }
}
