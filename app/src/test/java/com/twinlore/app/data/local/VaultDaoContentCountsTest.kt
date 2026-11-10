package com.twinlore.app.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class VaultDaoContentCountsTest {
    private lateinit var db: VaultDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            VaultDatabase::class.java
        ).allowMainThreadQueries().build()
        val w = db.openHelper.writableDatabase
        // Only the counts are under test; skip seeding checklist sections and tags.
        w.execSQL("PRAGMA foreign_keys = OFF")
        for (id in listOf("empty", "artOnly", "guide", "tab", "map", "check")) {
            w.execSQL("INSERT INTO vaults (id, name, createdAt) VALUES ('$id', '$id', 0)")
        }
        fun res(id: String, vault: String, kind: String, size: Long = 1, source: String = "user_add") = w.execSQL(
            "INSERT INTO resource_files (id, vault_id, kind, filename, size_bytes, source, last_seen_at, created_at) " +
                "VALUES ('$id', '$vault', '$kind', 'f', $size, '$source', 0, 0)"
        )
        res("r1", "artOnly", "artwork")
        res("r2", "guide", "artwork")
        res("r3", "guide", "guide")
        res("r4", "guide", "group_page", size = 100)
        res("r5", "guide", "guide", size = 5_000, source = "local_link")
        res("r6", "map", "map", size = 40)
        res("r7", "map", "screenshot", size = 2)
        w.execSQL("INSERT INTO saved_maps (id, vaultId, title, map_order, createdAt, lastAccessed) VALUES ('m1', 'map', 't', 0, 0, 0)")
        w.execSQL(
            "INSERT INTO checklist_items (id, vaultId, tagId, sectionId, title, completed, sortOrder, createdAt, updatedAt) " +
                "VALUES ('c1', 'check', 't', 's', 'x', 0, 0, 0, 0)"
        )
    }

    @After fun tearDown() = db.close()

    @Test
    fun counts_everything_but_artwork() = runTest {
        db.savedTabDao().insertTab(
            SavedTabEntity(id = "t1", vaultId = "tab", title = "t", url = "u", createdAt = Date(0), lastAccessed = Date(0))
        )
        val counts = db.vaultDao().getContentCounts().associate { it.vaultId to it.contentCount }
        assertEquals(
            mapOf("empty" to 0, "artOnly" to 0, "guide" to 3, "tab" to 1, "map" to 3, "check" to 1),
            counts
        )
        val guides = db.vaultDao().getContentCounts().associate { it.vaultId to it.guideCount }
        assertEquals(1, guides["tab"])
        assertEquals(0, guides["map"])
        val sizes = db.vaultDao().getContentCounts().associate { it.vaultId to (it.totalBytes to it.guideImageBytes) }
        // Artwork 1 + guide 1 + group page 100; the linked 5,000-byte file is not ours to free.
        assertEquals(102L to 101L, sizes["guide"])
        assertEquals(0L to 0L, sizes["empty"])
        // Images (maps and screenshots) count towards what the cleanup frees; artwork never does.
        assertEquals(42L to 42L, sizes["map"])
        assertEquals(1L to 0L, sizes["artOnly"])
        val images = db.vaultDao().getContentCounts().associate { it.vaultId to it.imageCount }
        assertEquals(1, images["map"])
        assertEquals(0, images["guide"])
    }
}
