package com.twinlore.app.data.library

import android.content.Context
import androidx.room.Room
import com.twinlore.app.data.local.MediaSearchIndexEntity
import com.twinlore.app.data.local.MediaSearchPathEntity
import com.twinlore.app.data.local.VaultDatabase
import com.twinlore.app.data.scraping.GameNameSanitizer
import com.twinlore.app.domain.MediaSearchPath
import com.twinlore.app.domain.Vault
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.util.Date
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class LocalMediaMatcherTest {

    private lateinit var ctx: Context
    private lateinit var db: VaultDatabase
    private lateinit var matcher: LocalMediaMatcher
    private val sanitizer = GameNameSanitizer()

    @Before
    fun setup() {
        ctx = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(ctx, VaultDatabase::class.java).allowMainThreadQueries().build()
        matcher = LocalMediaMatcher(
            indexDao = db.mediaSearchIndexDao(),
            dirDao = db.mediaSearchDirDao(),
            sanitizer = sanitizer,
        )
    }

    @After
    fun tearDown() { db.close() }

    private fun vault(name: String) = Vault(
        id = UUID.randomUUID().toString(),
        name = name,
        romPath = null, romFilename = null, systemeid = null, gameId = null,
        source = null, lastOpened = null, createdAt = Date(),
    )

    private suspend fun seedSp(): String {
        val spId = "sp"
        db.mediaSearchPathDao().upsert(MediaSearchPathEntity(
            id = spId, uri = "file:///tmp/x", label = "t",
            origin = MediaSearchPath.Origin.MANUAL.raw, lastScannedAt = null, createdAt = 0,
            disabled = false,
        ))
        return spId
    }

    private suspend fun insertEntry(
        spId: String, filename: String, parents: List<String>, ext: String, size: Long = 100L,
    ): MediaSearchIndexEntity {
        val parts = parents.map { sanitizer.normalize(it) }
        val e = MediaSearchIndexEntity(
            id = UUID.randomUUID().toString(),
            searchPathId = spId,
            absolutePath = "/tmp/x/${(parents + filename).joinToString("/")}",
            relativePath = (parents + filename).joinToString("/"),
            filename = filename,
            normalizedBasename = sanitizer.normalize(filename.substringBeforeLast('.')),
            normalizedPathTokens = parts.joinToString(" "),
            extLower = ext,
            sizeBytes = size,
            mtime = 0,
        )
        db.mediaSearchIndexDao().insertAll(listOf(e))
        return e
    }

    private suspend fun seedDir(spId: String, relativeDir: String) {
        db.mediaSearchDirDao().upsert(
            com.twinlore.app.data.local.MediaSearchDirEntity(
                id = java.util.UUID.randomUUID().toString(),
                searchPathId = spId,
                relativeDir = relativeDir,
                mtime = 0L,
                normalizedName = sanitizer.normalize(relativeDir.substringAfterLast('/')),
            )
        )
    }

    @Test
    fun basenameEqualityMatches() = runTest {
        val spId = seedSp()
        insertEntry(spId, "Resident Evil 4.png", listOf("snes"), "png")
        seedDir(spId, "snes")
        val res = matcher.findForVault(vault("Resident Evil 4"))
        assertEquals(1, res.size)
    }

    @Test
    fun partialBasenameDoesNotMatch() = runTest {
        val spId = seedSp()
        insertEntry(spId, "Resident Evil 4 Box.png", listOf("snes"), "png")
        seedDir(spId, "snes")
        val res = matcher.findForVault(vault("Resident Evil 4"))
        assertEquals(0, res.size)
    }

    @Test
    fun numericSuffixDoesNotMatch() = runTest {
        val spId = seedSp()
        insertEntry(spId, "Shinobi 2.png", listOf("snes"), "png")
        seedDir(spId, "snes")
        val res = matcher.findForVault(vault("Shinobi"))
        assertEquals(0, res.size)
    }

    @Test
    fun folderEqualityMatchesAllFilesUnderFolder() = runTest {
        val spId = seedSp()
        insertEntry(spId, "cover.jpg", listOf("Resident Evil 4"), "jpg")
        insertEntry(spId, "manual.pdf", listOf("Resident Evil 4"), "pdf")
        seedDir(spId, "Resident Evil 4")
        val res = matcher.findForVault(vault("Resident Evil 4"))
        assertEquals(2, res.size)
    }

    @Test
    fun folderEqualityMatchesRecursivelyIntoSubfolders() = runTest {
        val spId = seedSp()
        insertEntry(spId, "title.png", listOf("Resident Evil 4", "screenshots"), "png")
        seedDir(spId, "Resident Evil 4")
        seedDir(spId, "Resident Evil 4/screenshots")
        val res = matcher.findForVault(vault("Resident Evil 4"))
        assertEquals(1, res.size)
    }

    @Test
    fun multiWordVaultMatchesMultiWordFolder() = runTest {
        val spId = seedSp()
        insertEntry(spId, "art.jpg", listOf("Resident Evil 4 Remake"), "jpg")
        seedDir(spId, "Resident Evil 4 Remake")
        val res = matcher.findForVault(vault("Resident Evil 4"))
        assertEquals(0, res.size)
    }

    @Test
    fun blankVaultNameReturnsEmpty() = runTest {
        val spId = seedSp()
        // Seed a perfect-equality match for the basename "()".
        // If the matcher's blank-needle guard misfires, this would match.
        insertEntry(spId, "().jpg", listOf("snes"), "jpg")
        seedDir(spId, "snes")
        // Vault name "()" normalizes to "" after sanitizer strips non-alphanumerics,
        // which exercises the early-return guard rather than relying on no entries
        // happening to match a non-blank fallback name.
        val res = matcher.findForVault(vault("()"))
        assertEquals(0, res.size)
    }

    @Test
    fun basenameAndFolderMatchesAreDeduped() = runTest {
        val spId = seedSp()
        insertEntry(spId, "Resident Evil 4.png", listOf("Resident Evil 4"), "png")
        seedDir(spId, "Resident Evil 4")
        val res = matcher.findForVault(vault("Resident Evil 4"))
        assertEquals(1, res.size)
    }

    @Test
    fun removeBindingsForVaultName_unwinds_all_matches_across_paths() = runTest {
        val sp1 = seedSp() // MANUAL path
        // A second search path (AUTO_HOME) — the old cleanup only touched this one.
        db.mediaSearchPathDao().upsert(
            MediaSearchPathEntity(
                id = "sp2", uri = "file:///tmp/y", label = "t2",
                origin = MediaSearchPath.Origin.AUTO_HOME.raw, lastScannedAt = null, createdAt = 0,
                disabled = false,
            )
        )
        // Basename match on the MANUAL path; folder match (2 files) on the AUTO_HOME path.
        insertEntry(sp1, "Resident Evil 4.png", listOf("snes"), "png")
        seedDir(sp1, "snes")
        insertEntry("sp2", "cover.jpg", listOf("Resident Evil 4"), "jpg")
        insertEntry("sp2", "manual.pdf", listOf("Resident Evil 4"), "pdf")
        seedDir("sp2", "Resident Evil 4")
        // An unrelated vault that must survive the cleanup.
        insertEntry("sp2", "Sonic.png", listOf("genesis"), "png")
        seedDir("sp2", "genesis")

        assertEquals(3, matcher.findForVault(vault("Resident Evil 4")).size)

        val removed = matcher.removeBindingsForVaultName("Resident Evil 4")
        assertEquals(3, removed)
        // A same-name recreate now finds nothing to resurrect…
        assertEquals(0, matcher.findForVault(vault("Resident Evil 4")).size)
        // …and the unrelated vault is untouched.
        assertEquals(1, matcher.findForVault(vault("Sonic")).size)
    }

    @Test
    fun removeBindingsForVaultName_blankName_isNoop() = runTest {
        val spId = seedSp()
        insertEntry(spId, "Sonic.png", listOf("genesis"), "png")
        seedDir(spId, "genesis")
        assertEquals(0, matcher.removeBindingsForVaultName("()"))
        assertEquals(1, matcher.findForVault(vault("Sonic")).size)
    }
}
