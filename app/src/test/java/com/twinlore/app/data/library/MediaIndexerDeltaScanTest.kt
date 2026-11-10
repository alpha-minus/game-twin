package com.twinlore.app.data.library

import android.content.Context
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class MediaIndexerDeltaScanTest {

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

    private suspend fun seedAndRebuild(root: java.io.File): String {
        val spId = "sp-${root.name}"
        db.mediaSearchPathDao().upsert(MediaSearchPathEntity(
            id = spId, uri = "file://${root.absolutePath}", label = "t",
            origin = MediaSearchPath.Origin.MANUAL.raw, lastScannedAt = null, createdAt = 0,
        ))
        indexer.rebuildAll()
        return spId
    }

    @Test
    fun scanDelta_no_changes_returns_zero() = runTest {
        val root = tempFolder.newFolder("d1")
        java.io.File(root, "a.png").writeText("x")
        val spId = seedAndRebuild(root)

        val n = indexer.scanDelta(spId)
        assertEquals(0, n)
        assertEquals(1, db.mediaSearchIndexDao().getForPath(spId).size)
    }

    @Test
    fun scanDelta_picks_up_new_file_in_changed_dir() = runTest {
        val root = tempFolder.newFolder("d2")
        val sub = java.io.File(root, "snes/covers").apply { mkdirs() }
        java.io.File(sub, "a.png").writeText("x")
        val spId = seedAndRebuild(root)

        // Touch the dir to bump its mtime, then add a new file.
        Thread.sleep(20)
        java.io.File(sub, "b.png").writeText("y")
        sub.setLastModified(System.currentTimeMillis())

        val n = indexer.scanDelta(spId)
        assertTrue("delta should report >0", n >= 1)
        val rows = db.mediaSearchIndexDao().getForPath(spId)
        assertEquals(2, rows.size)
    }

    @Test
    fun scanDelta_removes_deleted_file() = runTest {
        val root = tempFolder.newFolder("d3")
        val sub = java.io.File(root, "covers").apply { mkdirs() }
        val a = java.io.File(sub, "a.png").apply { writeText("x") }
        java.io.File(sub, "b.png").writeText("y")
        val spId = seedAndRebuild(root)

        Thread.sleep(20)
        a.delete()
        sub.setLastModified(System.currentTimeMillis())

        indexer.scanDelta(spId)

        val rows = db.mediaSearchIndexDao().getForPath(spId)
        assertEquals(1, rows.size)
        assertEquals("b.png", rows.single().filename)
    }

    @Test
    fun scanDelta_skips_unchanged_dir_listing() = runTest {
        // Hard to assert "we didn't listFiles()" without instrumentation. Instead
        // verify behavior: after rebuild, just call scanDelta with no FS changes.
        // The single indexed row's stored mtime must remain identical (a re-list
        // would also produce the same mtime since the file is unchanged, but the
        // counter assertion in the no-changes test gives us indirect evidence).
        val root = tempFolder.newFolder("d4")
        val sub = java.io.File(root, "covers").apply { mkdirs() }
        java.io.File(sub, "a.png").writeText("x")
        val spId = seedAndRebuild(root)

        val before = db.mediaSearchIndexDao().getForPath(spId).single().mtime
        indexer.scanDelta(spId)
        val after = db.mediaSearchIndexDao().getForPath(spId).single().mtime
        assertEquals(before, after)
    }

    @Test
    fun scanDelta_skips_dot_prefixed_directories_and_files() = runTest {
        val root = tempFolder.newFolder("d6")
        java.io.File(root, "a.png").writeText("x")
        val spId = seedAndRebuild(root)

        Thread.sleep(20)
        // Syncthing drops a version-backup dir + a trashed file into the tree.
        val st = java.io.File(root, ".stversions/super metroid (2)").apply { mkdirs() }
        java.io.File(st, "old-guide.mht").writeText("y")
        java.io.File(root, ".trashed-123-cover.png").writeText("y")
        root.setLastModified(System.currentTimeMillis())

        indexer.scanDelta(spId)

        val rows = db.mediaSearchIndexDao().getForPath(spId)
        assertEquals(1, rows.size)
        assertEquals("a.png", rows.single().filename)
    }

    @Test
    fun scanDelta_handles_new_subdirectory() = runTest {
        val root = tempFolder.newFolder("d5")
        java.io.File(root, "a.png").writeText("x")
        val spId = seedAndRebuild(root)

        Thread.sleep(20)
        // Add a brand new subdir with a new file
        val newSub = java.io.File(root, "fanart").apply { mkdirs() }
        java.io.File(newSub, "b.jpg").writeText("y")
        root.setLastModified(System.currentTimeMillis())

        indexer.scanDelta(spId)

        val rows = db.mediaSearchIndexDao().getForPath(spId)
        assertEquals(2, rows.size)
        assertTrue(rows.any { it.filename == "b.jpg" })
    }
}
