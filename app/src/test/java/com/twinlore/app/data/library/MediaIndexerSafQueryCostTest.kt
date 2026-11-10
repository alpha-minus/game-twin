package com.twinlore.app.data.library

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.DocumentsContract
import androidx.room.Room
import com.twinlore.app.data.local.MediaSearchIndexDao
import com.twinlore.app.data.local.MediaSearchIndexEntity
import com.twinlore.app.data.local.MediaSearchPathEntity
import com.twinlore.app.data.local.VaultDatabase
import com.twinlore.app.data.scraping.GameNameSanitizer
import com.twinlore.app.domain.MediaSearchPath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * Regression for the slow first local-artwork lookup after a folder pick: the first
 * scanDelta over a never-scanned SAF tree (a fresh AUTO_ESDE / AUTO_HOME row) used to read
 * every property of every file through DocumentFile, one ContentResolver query each, and
 * wrote every file in its own Room transaction. These tests serve a downloaded_media-shaped
 * tree through a counting fake documents provider and pin the cost to one children query
 * per directory and one index write per directory.
 */
@RunWith(RobolectricTestRunner::class)
class MediaIndexerSafQueryCostTest {

    @get:Rule val tempFolder = TemporaryFolder()

    private lateinit var db: VaultDatabase
    private lateinit var appScope: CoroutineScope
    private lateinit var indexDao: CountingIndexDao
    private lateinit var indexer: MediaIndexer
    private lateinit var base: File

    @Before
    fun setup() {
        val ctx = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(ctx, VaultDatabase::class.java).allowMainThreadQueries().build()
        appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        indexDao = CountingIndexDao(db.mediaSearchIndexDao())
        indexer = MediaIndexer(ctx, db.mediaSearchPathDao(), indexDao, db.mediaSearchDirDao(), GameNameSanitizer(), appScope)
        base = tempFolder.newFolder("base")
        FakeDocsProvider.base = base
        FakeDocsProvider.queries.set(0)
        Robolectric.buildContentProvider(FakeDocsProvider::class.java).create(AUTHORITY)
    }

    @After
    fun tearDown() {
        appScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        db.close()
    }

    /** 5 systems x 4 media folders x 20 images, plus one video each: 26 dirs, 400 indexable files. */
    private fun seedTree() {
        val root = File(base, "root").apply { mkdirs() }
        for (s in 1..SYSTEMS) {
            for (folder in FOLDERS) {
                val dir = File(root, "sys$s/$folder").apply { mkdirs() }
                for (g in 1..GAMES) File(dir, "Game $g.png").writeText("png$g")
            }
            File(root, "sys$s/videos").mkdirs()
            File(root, "sys$s/videos/Game 1.mp4").writeText("mp4")
        }
    }

    private suspend fun seedSp(): String {
        val spId = "sp-fake-saf"
        db.mediaSearchPathDao().upsert(MediaSearchPathEntity(
            id = spId, uri = treeUri.toString(),
            label = "ES-DE downloaded_media", origin = MediaSearchPath.Origin.AUTO_ESDE.raw,
            lastScannedAt = null, createdAt = 0,
        ))
        return spId
    }

    @Test
    fun first_scanDelta_lists_each_directory_once_and_reads_no_per_file_properties() = runTest {
        seedTree()
        val spId = seedSp()

        val touched = indexer.scanDelta(spId)

        val rows = db.mediaSearchIndexDao().getForPath(spId)
        assertEquals(SYSTEMS * FOLDERS.size * GAMES, rows.size)
        assertEquals(rows.size, touched)
        // Directory count: root + systems + (media folders + videos) per system.
        val dirs = 1 + SYSTEMS + SYSTEMS * (FOLDERS.size + 1)
        val queries = FakeDocsProvider.queries.get()
        // One children query per directory plus one for the root's own mtime. The
        // DocumentFile walk this replaced made roughly five queries per file (2,197 here).
        assertTrue("expected <= ${dirs + 1} provider queries, got $queries", queries <= dirs + 1)
        // One batched write per directory that holds files, not one per file.
        assertTrue("expected <= $dirs index writes, got ${indexDao.insertCalls}", indexDao.insertCalls <= dirs)
    }

    @Test
    fun first_scanDelta_stores_the_same_document_uris_documentfile_handed_out() = runTest {
        seedTree()
        val spId = seedSp()

        indexer.scanDelta(spId)

        val row = db.mediaSearchIndexDao().getForPath(spId).first { it.relativePath == "sys1/covers/Game 1.png" }
        // Rows written by older builds came from TreeDocumentFile.getUri(); the stored string
        // must not change, or resource_files fingerprints stop matching and duplicate.
        val expected = DocumentsContract.buildDocumentUriUsingTree(
            DocumentsContract.buildDocumentUriUsingTree(treeUri, "root"),
            "root/sys1/covers/Game 1.png",
        ).toString()
        assertEquals(expected, row.absolutePath)
        assertEquals("Game 1.png", row.filename)
        assertEquals("sys1 covers", row.normalizedPathTokens)
        assertEquals(4L, row.sizeBytes)
    }

    @Test
    fun unchanged_rescan_skips_leaf_listings_and_writes_nothing() = runTest {
        seedTree()
        val spId = seedSp()
        indexer.scanDelta(spId)
        FakeDocsProvider.queries.set(0)
        indexDao.insertCalls = 0

        val touched = indexer.scanDelta(spId)

        assertEquals(0, touched)
        assertEquals(0, indexDao.insertCalls)
        // Root mtime + the root listing + one listing per system dir; the media folders
        // themselves (the big ones) are never listed while their mtime is unchanged.
        val queries = FakeDocsProvider.queries.get()
        assertTrue("expected <= ${2 + SYSTEMS} provider queries, got $queries", queries <= 2 + SYSTEMS)
    }

    @Test
    fun first_rebuild_over_saf_lists_each_directory_once() = runTest {
        seedTree()
        val spId = seedSp()

        indexer.rebuildOne(spId)

        assertEquals(SYSTEMS * FOLDERS.size * GAMES, db.mediaSearchIndexDao().getForPath(spId).size)
        val dirs = 1 + SYSTEMS + SYSTEMS * (FOLDERS.size + 1)
        val queries = FakeDocsProvider.queries.get()
        assertTrue("expected <= ${dirs + 1} provider queries, got $queries", queries <= dirs + 1)
    }

    private class CountingIndexDao(private val d: MediaSearchIndexDao) : MediaSearchIndexDao by d {
        var insertCalls = 0
        override suspend fun insertAll(entities: List<MediaSearchIndexEntity>) {
            insertCalls++
            d.insertAll(entities)
        }
    }

    /**
     * Serves `base/<docId>` as a documents tree: `.../document/<docId>` answers one row,
     * `.../document/<docId>/children` lists the directory. Counts every query.
     */
    class FakeDocsProvider : ContentProvider() {
        companion object {
            lateinit var base: File
            val queries = AtomicInteger(0)
        }

        override fun onCreate() = true

        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
            queries.incrementAndGet()
            val segs = uri.pathSegments
            val docId = segs[3]
            val cols = projection ?: arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val cursor = MatrixCursor(cols)
            val file = File(base, docId)
            if (segs.size == 5 && segs[4] == "children") {
                file.listFiles().orEmpty().forEach { cursor.addRow(row(cols, "$docId/${it.name}", it)) }
            } else if (file.exists()) {
                cursor.addRow(row(cols, docId, file))
            }
            return cursor
        }

        private fun row(cols: Array<out String>, docId: String, f: File): Array<Any?> = cols.map { c ->
            when (c) {
                DocumentsContract.Document.COLUMN_DOCUMENT_ID -> docId
                DocumentsContract.Document.COLUMN_DISPLAY_NAME -> f.name
                DocumentsContract.Document.COLUMN_MIME_TYPE ->
                    if (f.isDirectory) DocumentsContract.Document.MIME_TYPE_DIR else "application/octet-stream"
                DocumentsContract.Document.COLUMN_SIZE -> f.length()
                DocumentsContract.Document.COLUMN_LAST_MODIFIED -> f.lastModified()
                DocumentsContract.Document.COLUMN_FLAGS -> 0
                else -> null
            }
        }.toTypedArray()

        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
    }

    private companion object {
        const val AUTHORITY = "com.twinlore.test.fakedocs"
        const val SYSTEMS = 5
        const val GAMES = 20
        val FOLDERS = listOf("covers", "fanart", "marquees", "screenshots")
        val treeUri: Uri = DocumentsContract.buildTreeDocumentUri(AUTHORITY, "root")
    }
}
