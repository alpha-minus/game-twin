package com.twinlore.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.twinlore.app.data.local.VaultDatabase
import kotlinx.coroutines.flow.first
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PageGroupRepositoryMarkerTest {
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

    private suspend fun seedGroup(): Triple<String, String, String> {
        val groupId = repo.createGroup(
            vaultId = "v1", title = "G", indexUrl = "https://a/", indexPageTitle = "G",
            entries = listOf(
                PageGroupRepository.NewEntry("E0", "u1", null, null, null, null),
                PageGroupRepository.NewEntry("E1", "u2", null, null, null, null)
            )
        )
        val entries = db.pageGroupEntryDao().getForGroup(groupId)
        return Triple(groupId, entries[0].id, entries[1].id)
    }

    @Test fun addMarker_writes_correct_shape() = runTest {
        val (groupId, e0, _) = seedGroup()
        val marker = repo.addMarker(groupId, e0, "first selection", 800, 1).getOrThrow()
        val row = db.scrollMarkerDao().getForTab(groupId).first().single()
        assertEquals(marker.id, row.id)
        assertEquals(groupId, row.savedTabId)
        assertEquals(e0, row.pageGroupEntryId)
        assertEquals(800, row.scrollPosition)
        assertEquals(0, row.markerOrder)
        assertEquals("first selection", row.label)
        assertEquals(1, row.occurrenceIndex)
    }

    @Test fun observeForTab_returns_in_marker_order() = runTest {
        val (groupId, e0, e1) = seedGroup()
        repo.addMarker(groupId, e0, "A", 100, 0)
        repo.addMarker(groupId, e1, "B", 200, 0)
        repo.addMarker(groupId, e0, "C", 300, 0)
        val ordered = db.scrollMarkerDao().getForTab(groupId).first().map { it.label }
        assertEquals(listOf("A", "B", "C"), ordered)
    }

    @Test fun removeEntry_deletes_only_that_entrys_markers() = runTest {
        val (groupId, e0, e1) = seedGroup()
        repo.addMarker(groupId, e0, "first", 100, 0)
        repo.addMarker(groupId, e1, "second", 200, 0)
        repo.removeEntry(groupId, e0)
        val remaining = db.scrollMarkerDao().getForTab(groupId).first()
        assertEquals(1, remaining.size)
        assertEquals("second", remaining.single().label)
    }

    @Test fun deleteGroup_cascades_all_markers() = runTest {
        val (groupId, e0, _) = seedGroup()
        repo.addMarker(groupId, e0, "first", 100, 0)
        repo.deleteGroup(groupId)
        assertTrue(db.scrollMarkerDao().getForTab(groupId).first().isEmpty())
    }

    @Test fun deleteMarker_removes_by_id() = runTest {
        val (groupId, e0, _) = seedGroup()
        val m = repo.addMarker(groupId, e0, "x", 0, 0).getOrThrow()
        repo.deleteMarker(m.id)
        assertTrue(db.scrollMarkerDao().getForTab(groupId).first().isEmpty())
    }
}
