package com.twinlore.app.data.library

import android.net.Uri
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * The load-from-folder sequence must run the import (and therefore its
 * too-new guard) BEFORE adopting the folder as the home folder. Otherwise a
 * refused load strands the app on an unreadable folder, which the next
 * auto-save then overwrites — the library-corruption bug this coordinator
 * exists to prevent.
 */
class LibraryLoadCoordinatorTest {

    private val uri: Uri = mock()

    @Test
    fun refusedTooNewLoad_doesNotSwitchHomeFolder() = runTest {
        val importer = mock<LibraryImporter> {
            onBlocking { importFrom(any()) } doAnswer {
                throw LibrarySnapshotTooNewException(savedVersion = 99, supportedVersion = 35)
            }
        }
        val storageFolders = mock<StorageFolderMigrationCoordinator>()
        val coordinator = LibraryLoadCoordinator(importer, storageFolders)

        var thrown: LibrarySnapshotTooNewException? = null
        try {
            coordinator.loadFromFolder(uri)
        } catch (e: LibrarySnapshotTooNewException) {
            thrown = e
        }

        assert(thrown != null) { "too-new import should propagate LibrarySnapshotTooNewException" }
        // The folder was refused, so the home folder must be left untouched.
        verify(storageFolders, never()).switchWithoutMigration(any())
    }

    @Test
    fun successfulLoad_importsBeforeSwitchingHomeFolder() = runTest {
        val importer = mock<LibraryImporter>()
        val storageFolders = mock<StorageFolderMigrationCoordinator>()
        whenever(importer.importFrom(any())).thenReturn(Unit)
        val coordinator = LibraryLoadCoordinator(importer, storageFolders)

        coordinator.loadFromFolder(uri)

        inOrder(importer, storageFolders) {
            verify(importer).importFrom(uri)
            verify(storageFolders).switchWithoutMigration(uri)
        }
    }
}
