package com.twinlore.app.data.library

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import androidx.room.Room
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class MediaIndexerSafTest {

    @get:Rule val tempFolder = TemporaryFolder()

    private lateinit var ctx: Context
    private lateinit var db: VaultDatabase
    private lateinit var indexer: MediaIndexer
    private lateinit var appScope: CoroutineScope

    @Before
    fun setup() {
        ctx = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(ctx, VaultDatabase::class.java).allowMainThreadQueries().build()
        appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        indexer = MediaIndexer(ctx, db.mediaSearchPathDao(), db.mediaSearchIndexDao(), db.mediaSearchDirDao(), GameNameSanitizer(), appScope)
    }

    @After
    fun tearDown() {
        appScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        db.close()
    }

    private suspend fun seedSp(): String {
        val spId = "sp-saf"
        db.mediaSearchPathDao().upsert(MediaSearchPathEntity(
            id = spId, uri = "content://example/x",
            label = "saf", origin = MediaSearchPath.Origin.MANUAL.raw,
            lastScannedAt = null, createdAt = 0,
        ))
        return spId
    }

    @Test
    fun saf_walk_indexes_recursive_files() = runTest {
        val root = tempFolder.newFolder("safroot")
        val covers = java.io.File(root, "snes/covers").apply { mkdirs() }
        java.io.File(covers, "Tetris.png").writeText("xxxx")

        val spId = seedSp()
        val tree = DocumentFile.fromFile(root)
        indexer.rebuildOneFromDocumentFile(spId, tree)

        val rows = db.mediaSearchIndexDao().getForPath(spId)
        assertEquals(1, rows.size)
        val r = rows.single()
        assertEquals("Tetris.png", r.filename)
        assertEquals("snes covers", r.normalizedPathTokens)
        assertEquals("snes/covers/Tetris.png", r.relativePath)
        // SAF walk stores the DocumentFile URI directly so no reconstruction is needed.
        assertEquals(false, r.absolutePath.isNullOrEmpty())
    }

    @Test
    fun saf_walk_filters_unsupported_extensions() = runTest {
        val root = tempFolder.newFolder("safroot2")
        java.io.File(root, "ok.png").writeText("x")
        java.io.File(root, "movie.mp4").writeText("x")
        java.io.File(root, "weird.bin").writeText("x")

        val spId = seedSp()
        indexer.rebuildOneFromDocumentFile(spId, DocumentFile.fromFile(root))

        val rows = db.mediaSearchIndexDao().getForPath(spId)
        assertEquals(1, rows.size)
        assertEquals("ok.png", rows.single().filename)
    }

    @Test
    fun saf_walk_skips_dot_prefixed_directories_and_files() = runTest {
        val root = tempFolder.newFolder("safroot4")
        java.io.File(root, "keep.png").writeText("x")
        val st = java.io.File(root, ".stversions/super metroid (2)").apply { mkdirs() }
        java.io.File(st, "old-guide.mht").writeText("x")
        java.io.File(root, ".trashed-123-cover.png").writeText("x")

        val spId = seedSp()
        indexer.rebuildOneFromDocumentFile(spId, DocumentFile.fromFile(root))

        val rows = db.mediaSearchIndexDao().getForPath(spId)
        assertEquals(listOf("keep.png"), rows.map { it.filename })
    }

    @Test
    fun saf_walk_records_dir_mtimes() = runTest {
        val root = tempFolder.newFolder("safroot3")
        val sub = java.io.File(root, "snes").apply { mkdirs() }
        java.io.File(sub, "x.png").writeText("x")

        val spId = seedSp()
        indexer.rebuildOneFromDocumentFile(spId, DocumentFile.fromFile(root))

        val rootM = db.mediaSearchDirDao().getMtime(spId, "")
        val subM = db.mediaSearchDirDao().getMtime(spId, "snes")
        assertEquals(true, rootM != null)
        assertEquals(true, subM != null)
    }
}
