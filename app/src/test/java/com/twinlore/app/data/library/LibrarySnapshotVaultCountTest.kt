package com.twinlore.app.data.library

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Pins the vault-count read path used by [LibraryImporter] and
 * [com.twinlore.app.data.library.sync.LibrarySyncCoordinator] to gate
 * over-cap libraries on unlicensed installs.
 *
 * The resolver is internal so the test lives in the same package.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LibrarySnapshotVaultCountTest {

    private lateinit var context: Context
    private lateinit var folder: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        folder = File(context.cacheDir, "snap-count-${System.nanoTime()}").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        folder.deleteRecursively()
    }

    @Test
    fun `readSnapshotVaultCount returns the number of vault rows`() = runTest {
        val snapshot = File(folder, "library.abc12345.gametwin")
        SQLiteDatabase.openOrCreateDatabase(snapshot, null).use { db ->
            db.execSQL("CREATE TABLE vaults (id TEXT PRIMARY KEY, name TEXT, createdAt INTEGER)")
            for (i in 1..3) {
                db.execSQL("INSERT INTO vaults VALUES ('v$i', 'Vault $i', 0)")
            }
        }

        val count = readSnapshotVaultCount(context, Uri.fromFile(snapshot))

        assertEquals(3, count)
    }

    @Test
    fun `readSnapshotVaultCount returns zero for an empty vaults table`() = runTest {
        val snapshot = File(folder, "library.cafebabe.gametwin")
        SQLiteDatabase.openOrCreateDatabase(snapshot, null).use { db ->
            db.execSQL("CREATE TABLE vaults (id TEXT PRIMARY KEY, name TEXT, createdAt INTEGER)")
        }

        val count = readSnapshotVaultCount(context, Uri.fromFile(snapshot))

        assertEquals(0, count)
    }

    @Test
    fun `readSnapshotVaultCount returns null for a corrupt file`() = runTest {
        val snapshot = File(folder, "library.deadbeef.gametwin")
        snapshot.writeBytes(ByteArray(64) { 0x42 }) // not a SQLite file

        val count = readSnapshotVaultCount(context, Uri.fromFile(snapshot))

        assertNull(count)
    }

    @Test
    fun `readSnapshotVaultCount returns null for a missing file`() = runTest {
        val ghost = File(folder, "library.00000000.gametwin")

        val count = readSnapshotVaultCount(context, Uri.fromFile(ghost))

        assertNull(count)
    }

    @Test
    fun `readSnapshotVaultCount returns null when the vaults table is missing`() = runTest {
        val snapshot = File(folder, "library.feedface.gametwin")
        SQLiteDatabase.openOrCreateDatabase(snapshot, null).use { db ->
            db.execSQL("CREATE TABLE library_meta (id INTEGER PRIMARY KEY)")
        }

        val count = readSnapshotVaultCount(context, Uri.fromFile(snapshot))

        assertNull(count)
    }
}
