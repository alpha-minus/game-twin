package com.twinlore.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.twinlore.app.data.GroupsFolderResolver
import com.twinlore.app.data.local.PageGroupEntryEntity
import com.twinlore.app.data.local.ResourceFileEntity
import com.twinlore.app.data.local.SavedTabEntity
import com.twinlore.app.data.local.VaultDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PageGroupRepositoryTest {
    private lateinit var db: VaultDatabase
    private lateinit var groupRoot: File
    private lateinit var repo: PageGroupRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            VaultDatabase::class.java
        ).allowMainThreadQueries().build()
        groupRoot = File.createTempFile("group-root", "").apply { delete(); mkdirs() }
        // Seed a vault via raw SQL to avoid coupling to VaultEntity's constructor defaults.
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO vaults (id, name, createdAt) VALUES ('v1', 'Vault 1', 0)"
        )
        repo = PageGroupRepository(
            savedTabDao = db.savedTabDao(),
            resourceFileDao = db.resourceFileDao(),
            pageGroupEntryDao = db.pageGroupEntryDao(),
            pageGroupEntryAnchorDao = db.pageGroupEntryAnchorDao(),
            scrollMarkerDao = db.scrollMarkerDao(),
            groupsFolderResolver = object : GroupsFolderResolver {
                override suspend fun resolve(groupTabId: String): File = groupRoot
            },
            database = db
        )
    }

    @After fun tearDown() { db.close(); groupRoot.deleteRecursively() }

    @Test fun createGroup_writes_row_entries_and_manifest() = runTest {
        // Stage two resource files (the saver normally inserts these before commitGroup).
        File(groupRoot, "page-1.mht").writeText("mht1")
        File(groupRoot, "page-2.mht").writeText("mht2")
        val resourceIds = listOf("r1", "r2")
        resourceIds.forEachIndexed { i, rid ->
            db.resourceFileDao().insert(
                ResourceFileEntity(
                    id = rid, vaultId = "v1", kind = "group_page",
                    filename = "page-${i + 1}.mht", sizeBytes = 0L,
                    homeRelativePath = "Vault 1/groups/g/page-${i + 1}.mht",
                    absolutePath = null, mimeType = "multipart/related",
                    source = "browser_bulk",
                    lastSeenAt = 0L, createdAt = 0L
                )
            )
        }
        val entries = listOf(
            PageGroupRepository.NewEntry("Index", "https://a.example/", null, resourceIds[0], "page-1.mht", "page-1.txt"),
            PageGroupRepository.NewEntry("Chapter 1", "https://a.example/c1/", null, resourceIds[1], "page-2.mht", "page-2.txt"),
            PageGroupRepository.NewEntry("Throne Room", "https://a.example/c1/", "#throne-room", resourceIds[1], "page-2.mht", "page-2.txt"),
            PageGroupRepository.NewEntry("Sewers", "https://a.example/c1/", "#sewers", null, null, null)
        )
        val groupId = repo.createGroup("v1", "ALttP", "https://a.example/", "ALttP", entries = entries)

        val parent = db.savedTabDao().getTabById(groupId)!!
        assertEquals("group", parent.fileType)
        assertEquals("ALttP", parent.title)
        val rows = db.pageGroupEntryDao().getForGroup(groupId)
        assertEquals(4, rows.size)
        assertEquals(rows[0].id, parent.groupActiveEntryId)
        assertEquals(listOf(0, 1, 2, 3), rows.map { it.position })
        assertNull(rows[3].mhtResourceId)

        val manifest = File(groupRoot, "manifest.json")
        assertTrue(manifest.exists())
        val parsed = com.twinlore.app.data.library.PageGroupManifest.fromJson(manifest.readText())
        assertNotNull(parsed)
        assertEquals(4, parsed!!.entries.size)
    }

    @Test fun updateEntryScroll_persists_and_rewrites_manifest() = runTest {
        val groupId = seedTwoEntryGroup()
        val entries = db.pageGroupEntryDao().getForGroup(groupId)
        repo.updateEntryScroll(
            groupTabId = groupId, entryId = entries[1].id,
            anchor = "torch", occurrenceIndex = 2, ratio = 0.5
        )
        val reread = db.pageGroupEntryDao().getById(entries[1].id)!!
        assertEquals("torch", reread.scrollAnchor)
        assertEquals(2, reread.scrollOccurrenceIndex)
        assertEquals(0.5, reread.scrollRatio, 1e-9)
        val manifest = com.twinlore.app.data.library.PageGroupManifest.fromJson(File(groupRoot, "manifest.json").readText())!!
        assertEquals("torch", manifest.entries.first { it.id == entries[1].id }.scrollAnchor)
    }

    @Test fun removeEntry_deletes_file_when_last_reference() = runTest {
        val groupId = seedTwoEntryGroup() // both entries share resource r2
        val entries = db.pageGroupEntryDao().getForGroup(groupId)
        val resourceId = entries[1].mhtResourceId!!

        repo.removeEntry(groupId, entries[1].id)
        assertNotNull(db.resourceFileDao().getById(resourceId))

        repo.removeEntry(groupId, entries[0].id)
        assertNull(db.resourceFileDao().getById(resourceId))
    }

    @Test fun removeEntry_clears_active_entry_id_when_active_entry_is_removed() = runTest {
        val groupId = seedTwoEntryGroup()
        // createGroup sets the first entry with a non-null mhtResourceId as active.
        val activeEntryId = db.savedTabDao().getTabById(groupId)!!.groupActiveEntryId
        assertNotNull(activeEntryId)
        // Remove the active entry; the other remaining entry still has mhtResourceId.
        repo.removeEntry(groupId, activeEntryId!!)
        val updated = db.savedTabDao().getTabById(groupId)!!
        // Fallback: the next remaining entry that has a non-null mhtResourceId.
        val remaining = db.pageGroupEntryDao().getForGroup(groupId)
        val fallback = remaining.firstOrNull { it.mhtResourceId != null }?.id
        assertEquals(fallback, updated.groupActiveEntryId)
    }

    @Test fun deleteGroup_removes_row_entries_files_and_folder() = runTest {
        val groupId = seedTwoEntryGroup()
        File(groupRoot, "page-2.mht").writeText("mht")
        repo.deleteGroup(groupId)
        assertNull(db.savedTabDao().getTabById(groupId))
        assertTrue(db.pageGroupEntryDao().getForGroup(groupId).isEmpty())
        assertTrue(!File(groupRoot, "manifest.json").exists())
        assertNull(db.resourceFileDao().getById("r2"))
    }

    private suspend fun seedTwoEntryGroup(): String {
        db.resourceFileDao().insert(
            ResourceFileEntity(
                id = "r2", vaultId = "v1", kind = "group_page", filename = "page-2.mht",
                sizeBytes = 1, homeRelativePath = "Vault 1/groups/g2/page-2.mht",
                absolutePath = null, mimeType = "multipart/related",
                source = "browser_bulk", lastSeenAt = 0L, createdAt = 0L
            )
        )
        return repo.createGroup(
            vaultId = "v1", title = "G", indexUrl = "https://a/", indexPageTitle = "G",
            entries = listOf(
                PageGroupRepository.NewEntry("E0", "https://a/c/", null, "r2", "page-2.mht", "page-2.txt"),
                PageGroupRepository.NewEntry("E1", "https://a/c/", "#x", "r2", "page-2.mht", "page-2.txt")
            )
        )
    }
}
