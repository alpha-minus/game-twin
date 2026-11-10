package com.twinlore.app.data.library

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LibrarySnapshotMetaListTest {

    private lateinit var context: Context
    private lateinit var folder: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        folder = File(context.cacheDir, "metas-${System.nanoTime()}").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        folder.deleteRecursively()
    }

    private fun createSnapshot(fileName: String, deviceId: String, label: String, at: Long): File {
        val file = File(folder, fileName)
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL(
                "CREATE TABLE library_meta (id INTEGER PRIMARY KEY, schemaVersion INTEGER, " +
                    "lastWrittenAtMillis INTEGER, lastWrittenDeviceId TEXT, lastWrittenDeviceLabel TEXT)"
            )
            db.execSQL(
                "INSERT INTO library_meta VALUES (1, 35, ?, ?, ?)",
                arrayOf(at, deviceId, label)
            )
        }
        return file
    }

    @Test
    fun `lists all snapshots with device id, label, timestamp, newest first`() = runTest {
        createSnapshot("library.aaaaaaaa.gametwin", "aaaaaaaa-1111-2222-3333-444444444444", "Pixel 8", 100L)
        createSnapshot("library.bbbbbbbb.gametwin", "bbbbbbbb-1111-2222-3333-444444444444", "Deck", 200L)
        val metas = findSnapshotMetasNewestFirst(context, Uri.fromFile(folder))
        assertEquals(2, metas.size)
        assertEquals("Deck", metas[0].deviceLabel)
        assertEquals("bbbbbbbb-1111-2222-3333-444444444444", metas[0].deviceId)
        assertEquals(200L, metas[0].lastWrittenAtMillis)
        assertEquals(35, metas[0].schemaVersion)
        assertEquals("library.bbbbbbbb.gametwin", metas[0].fileName)
    }

    @Test
    fun `skips unreadable files silently`() = runTest {
        createSnapshot("library.aaaaaaaa.gametwin", "aaaaaaaa-1111-2222-3333-444444444444", "Pixel 8", 100L)
        File(folder, "library.deadbeef.gametwin").writeText("not a sqlite file")
        val metas = findSnapshotMetasNewestFirst(context, Uri.fromFile(folder))
        assertEquals(1, metas.size)
    }
}
