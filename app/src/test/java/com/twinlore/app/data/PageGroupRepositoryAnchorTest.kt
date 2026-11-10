package com.twinlore.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.twinlore.app.data.local.VaultDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PageGroupRepositoryAnchorTest {
    private lateinit var db: VaultDatabase
    private lateinit var groupRoot: File
    private lateinit var repo: PageGroupRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            VaultDatabase::class.java
        ).allowMainThreadQueries().build()
        // Enable FK enforcement so CASCADE deletes are exercised.
        db.openHelper.writableDatabase.execSQL("PRAGMA foreign_keys = ON")
        groupRoot = File.createTempFile("group-root", "").apply { delete(); mkdirs() }
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

    @Test fun createGroup_persists_anchors_in_position_order() = runTest {
        val groupId = repo.createGroup(
            vaultId = "v1", title = "G", indexUrl = "https://a/", indexPageTitle = "G",
            entries = listOf(
                PageGroupRepository.NewEntry(
                    label = "E0", savedHref = "u1", fragment = null,
                    mhtResourceId = null, pageFile = null, textFile = null,
                    anchors = emptyList()
                ),
                PageGroupRepository.NewEntry(
                    label = "E1", savedHref = "u2", fragment = null,
                    mhtResourceId = null, pageFile = null, textFile = null,
                    anchors = listOf(
                        PageGroupRepository.NewAnchor("Top", 0),
                        PageGroupRepository.NewAnchor("Middle", 500),
                        PageGroupRepository.NewAnchor("Bottom", 1200)
                    )
                )
            )
        )
        val entries = db.pageGroupEntryDao().getForGroup(groupId)
        val e1 = entries.first { it.label == "E1" }
        val anchors = db.pageGroupEntryAnchorDao().getForEntry(e1.id)
        assertEquals(3, anchors.size)
        assertEquals(listOf(0, 1, 2), anchors.map { it.position })
        assertEquals(listOf("Top", "Middle", "Bottom"), anchors.map { it.label })
        assertEquals(listOf(0, 500, 1200), anchors.map { it.scrollPosition })
    }

    @Test fun anchors_cascade_on_remove_entry() = runTest {
        val groupId = repo.createGroup(
            vaultId = "v1", title = "G", indexUrl = "https://a/", indexPageTitle = "G",
            entries = listOf(
                PageGroupRepository.NewEntry(
                    label = "E0", savedHref = "u1", fragment = null,
                    mhtResourceId = null, pageFile = null, textFile = null,
                    anchors = listOf(PageGroupRepository.NewAnchor("Top", 0))
                ),
                PageGroupRepository.NewEntry(
                    label = "E1", savedHref = "u2", fragment = null,
                    mhtResourceId = null, pageFile = null, textFile = null,
                    anchors = listOf(PageGroupRepository.NewAnchor("Heading", 200))
                )
            )
        )
        val entries = db.pageGroupEntryDao().getForGroup(groupId)
        repo.removeEntry(groupId, entries.first { it.label == "E0" }.id)
        val remaining = db.pageGroupEntryDao().getForGroup(groupId)
        assertEquals(1, remaining.size)
        assertEquals(1, db.pageGroupEntryAnchorDao().getForEntry(remaining[0].id).size)
        // E0's anchor row cascaded away.
        assertEquals(0, db.pageGroupEntryAnchorDao().getForEntry(entries.first { it.label == "E0" }.id).size)
    }

    @Test fun anchors_cascade_on_delete_group() = runTest {
        val groupId = repo.createGroup(
            vaultId = "v1", title = "G", indexUrl = "https://a/", indexPageTitle = "G",
            entries = listOf(
                PageGroupRepository.NewEntry(
                    label = "E0", savedHref = "u1", fragment = null,
                    mhtResourceId = null, pageFile = null, textFile = null,
                    anchors = listOf(PageGroupRepository.NewAnchor("Top", 0))
                )
            )
        )
        val entries = db.pageGroupEntryDao().getForGroup(groupId)
        assertTrue(db.pageGroupEntryAnchorDao().getForEntry(entries[0].id).isNotEmpty())
        repo.deleteGroup(groupId)
        assertTrue(db.pageGroupEntryAnchorDao().getForEntry(entries[0].id).isEmpty())
    }
}
