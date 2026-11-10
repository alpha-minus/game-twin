package com.twinlore.app.data.library

import android.content.Context
import androidx.room.Room
import com.twinlore.app.data.scraping.GameNameSanitizer
import com.twinlore.app.data.local.MediaSearchPathEntity
import com.twinlore.app.data.local.VaultDatabase
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
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class MediaIndexerFullRebuildTest {

    @get:Rule val tempFolder = TemporaryFolder()

    private lateinit var ctx: Context
    private lateinit var db: VaultDatabase
    private lateinit var indexer: MediaIndexer
    private lateinit var appScope: CoroutineScope
    private val sanitizer = GameNameSanitizer()

    @Before
    fun setup() {
        ctx = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(ctx, VaultDatabase::class.java).allowMainThreadQueries().build()
        appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        indexer = MediaIndexer(ctx, db.mediaSearchPathDao(), db.mediaSearchIndexDao(), db.mediaSearchDirDao(), sanitizer, appScope, clock = { 1000L })
    }

    @After
    fun tearDown() {
        appScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        db.close()
    }

    private suspend fun seedSearchPath(root: java.io.File): String {
        val id = "sp-${root.name}"
        db.mediaSearchPathDao().upsert(MediaSearchPathEntity(
            id = id, uri = "file://${root.absolutePath}", label = "test",
            origin = MediaSearchPath.Origin.MANUAL.raw, lastScannedAt = null, createdAt = 0,
        ))
        return id
    }

    @Test
    fun rebuild_walks_files_and_indexes_normalized_basename() = runTest {
        val root = tempFolder.newFolder("media")
        val covers = java.io.File(root, "snes/covers").apply { mkdirs() }
        java.io.File(covers, "Super Mario World (Europe).png").writeText("png-bytes")
        val spId = seedSearchPath(root)

        indexer.rebuildAll()

        val rows = db.mediaSearchIndexDao().getForPath(spId)
        assertEquals(1, rows.size)
        val r = rows.single()
        assertEquals("Super Mario World (Europe).png", r.filename)
        assertEquals("super mario world", r.normalizedBasename)
        assertEquals("snes covers", r.normalizedPathTokens)
        assertEquals("png", r.extLower)
        assertEquals(spId, r.searchPathId)
        assertTrue(r.sizeBytes > 0)
    }

    @Test
    fun rebuild_skips_unsupported_extensions() = runTest {
        val root = tempFolder.newFolder("media2")
        java.io.File(root, "art.png").writeText("x")
        java.io.File(root, "movie.mp4").writeText("x")
        java.io.File(root, "weird.bin").writeText("x")
        val spId = seedSearchPath(root)

        indexer.rebuildAll()

        val rows = db.mediaSearchIndexDao().getForPath(spId)
        assertEquals(1, rows.size)
        assertEquals("art.png", rows.single().filename)
    }

    @Test
    fun rebuild_records_directory_mtimes() = runTest {
        val root = tempFolder.newFolder("media3")
        val sub = java.io.File(root, "snes").apply { mkdirs() }
        java.io.File(sub, "x.png").writeText("x")
        val spId = seedSearchPath(root)

        indexer.rebuildAll()

        val rootM = db.mediaSearchDirDao().getMtime(spId, "")
        val subM = db.mediaSearchDirDao().getMtime(spId, "snes")
        assertTrue("root mtime should be recorded", rootM != null)
        assertTrue("snes/ mtime should be recorded", subM != null)
    }

    @Test
    fun rebuild_replaces_prior_entries_for_same_search_path() = runTest {
        val root = tempFolder.newFolder("media4")
        val f1 = java.io.File(root, "a.png").apply { writeText("x") }
        val spId = seedSearchPath(root)

        indexer.rebuildAll()
        assertEquals(1, db.mediaSearchIndexDao().getForPath(spId).size)

        f1.delete()
        java.io.File(root, "b.pdf").writeText("x")

        indexer.rebuildAll()

        val rows = db.mediaSearchIndexDao().getForPath(spId)
        assertEquals(1, rows.size)
        assertEquals("b.pdf", rows.single().filename)
    }

    @Test
    fun rebuild_supports_all_extensions_in_SUPPORTED_EXTS() = runTest {
        val root = tempFolder.newFolder("media5")
        for (ext in listOf("png", "jpg", "jpeg", "webp", "pdf", "txt", "mht", "mhtml", "html")) {
            java.io.File(root, "Tetris.$ext").writeText("x")
        }
        val spId = seedSearchPath(root)

        indexer.rebuildAll()

        val rows = db.mediaSearchIndexDao().getForPath(spId)
        assertEquals(9, rows.size)
        val exts = rows.map { it.extLower }.toSet()
        assertEquals(setOf("png", "jpg", "jpeg", "webp", "pdf", "txt", "mht", "mhtml", "html"), exts)
    }

    @Test
    fun rebuild_skips_dot_prefixed_directories_and_files() = runTest {
        val root = tempFolder.newFolder("media6")
        java.io.File(root, "keep.png").writeText("x")
        val st = java.io.File(root, ".stversions/super metroid (2)").apply { mkdirs() }
        java.io.File(st, "old-guide.mht").writeText("x")
        java.io.File(root, ".trashed-123-cover.png").writeText("x")
        val spId = seedSearchPath(root)

        indexer.rebuildAll()

        val rows = db.mediaSearchIndexDao().getForPath(spId)
        assertEquals(listOf("keep.png"), rows.map { it.filename })
        // No dir-mtime row recorded for the excluded subtree either.
        assertEquals(null, db.mediaSearchDirDao().getMtime(spId, ".stversions"))
    }

    @Test
    fun rebuildAll_skips_disabled_paths() = runTest {
        val enabledRoot = tempFolder.newFolder("enabled")
        java.io.File(enabledRoot, "a.png").writeText("a")
        val disabledRoot = tempFolder.newFolder("disabled")
        java.io.File(disabledRoot, "b.png").writeText("b")

        val enabledId = "sp-${enabledRoot.name}"
        val disabledId = "sp-${disabledRoot.name}"
        db.mediaSearchPathDao().upsert(MediaSearchPathEntity(
            id = enabledId, uri = "file://${enabledRoot.absolutePath}", label = "enabled",
            origin = MediaSearchPath.Origin.MANUAL.raw, lastScannedAt = null, createdAt = 0,
            disabled = false,
        ))
        db.mediaSearchPathDao().upsert(MediaSearchPathEntity(
            id = disabledId, uri = "file://${disabledRoot.absolutePath}", label = "disabled",
            origin = MediaSearchPath.Origin.MANUAL.raw, lastScannedAt = null, createdAt = 0,
            disabled = true,
        ))

        indexer.rebuildAll()

        val enabledRows = db.mediaSearchIndexDao().getForPath(enabledId)
        val disabledRows = db.mediaSearchIndexDao().getForPath(disabledId)
        assertEquals(1, enabledRows.size)
        assertEquals(0, disabledRows.size)
    }
}
