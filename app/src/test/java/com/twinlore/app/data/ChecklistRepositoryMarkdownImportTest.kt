package com.twinlore.app.data

import androidx.room.Room
import com.twinlore.app.data.local.VaultDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class ChecklistRepositoryMarkdownImportTest {

    private lateinit var db: VaultDatabase
    private lateinit var repo: ChecklistRepository
    private val vaultId = "vault-test"

    @Before
    fun setUp() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, VaultDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        // Seed a vault row so FK references resolve.
        // createdAt stored as Long via DateConverters.
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO vaults (id, name, createdAt) VALUES ('$vaultId', 'Test', 0)"
        )

        repo = ChecklistRepository(
            database = db,
            tagDao = db.checklistTagDao(),
            itemDao = db.checklistItemDao(),
            tabDao = db.checklistTabDao(),
            sectionDao = db.checklistSectionDao(),
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun full_hierarchy_paste_creates_tabs_sections_items() = runBlocking {
        val md = """
            # FF7
            ## Bosses
            - [ ] Sephiroth
            - [x] Weapon
        """.trimIndent()

        val result = repo.importFromMarkdown(vaultId, activeTabId = null, markdown = md)

        assertEquals(1, result.tabsCreated)
        assertEquals(1, result.sectionsCreated)
        assertEquals(2, result.itemsAdded)

        val tabs = repo.getTabsForVaultOnce(vaultId)
        assertEquals("FF7", tabs.single().name)
    }

    @Test
    fun active_tab_is_used_when_paste_has_no_tab_heading() = runBlocking {
        val tab = repo.createTab(vaultId, "Existing", colorIndex = 3)

        val md = """
            ## NewSection
            - item
        """.trimIndent()
        val result = repo.importFromMarkdown(vaultId, activeTabId = tab.id, markdown = md)

        assertEquals(0, result.tabsCreated)
        assertEquals(1, result.sectionsCreated)
        assertEquals(1, result.itemsAdded)
    }

    @Test
    fun dedup_skips_existing_item_in_same_section() = runBlocking {
        val md = """
            # T
            ## S
            - alpha
        """.trimIndent()
        repo.importFromMarkdown(vaultId, null, md)

        val md2 = """
            # T
            ## S
            - alpha
            - beta
        """.trimIndent()
        val result = repo.importFromMarkdown(vaultId, null, md2)

        assertEquals(1, result.itemsAdded)
        assertEquals(1, result.itemsSkipped)
    }

    @Test
    fun achievement_heuristic_creates_section_per_line() = runBlocking {
        val tab = repo.createTab(vaultId, "Achievements", colorIndex = 1)
        val md = """
            Speedrunner - Complete campaign
            Collector - Find all 50 items
        """.trimIndent()

        val result = repo.importFromMarkdown(vaultId, activeTabId = tab.id, markdown = md)

        assertEquals(0, result.tabsCreated)
        assertEquals(2, result.sectionsCreated)
        assertEquals(2, result.itemsAdded)
    }
}
