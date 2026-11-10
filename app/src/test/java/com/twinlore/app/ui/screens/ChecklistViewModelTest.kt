package com.twinlore.app.ui.screens

import android.os.Looper
import androidx.room.Room
import com.twinlore.app.data.ChecklistRepository
import com.twinlore.app.data.ChecklistViewStateManager
import com.twinlore.app.data.SavedTabRepository
import com.twinlore.app.data.SettingsRepository
import com.twinlore.app.data.VaultRepository
import com.twinlore.app.data.local.VaultDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class ChecklistViewModelTest {

    private lateinit var db: VaultDatabase
    private lateinit var repo: ChecklistRepository
    private lateinit var vm: ChecklistViewModel
    private val vaultId = "v1"

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val ctx = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(ctx, VaultDatabase::class.java)
            .allowMainThreadQueries().build()
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO vaults (id, name, createdAt) VALUES ('$vaultId', 'T', 0)"
        )
        repo = ChecklistRepository(
            database = db,
            tagDao = db.checklistTagDao(),
            itemDao = db.checklistItemDao(),
            tabDao = db.checklistTabDao(),
            sectionDao = db.checklistSectionDao(),
        )
        val vaultRepo = mock<VaultRepository> {
            on { getVaultByIdFlow(any()) } doReturn flowOf(null)
        }
        val settingsRepo = mock<SettingsRepository> {
            on { checklistPromptTemplatesJson } doReturn flowOf(null)
            on { checklistSelectedPromptId } doReturn flowOf(null)
            on { checklistTapGameHighScore } doReturn flowOf(0)
        }
        // ChecklistViewModel.availableGuides observes
        // savedTabRepository.getTabsForVault(...) inside a flatMapLatest, so
        // the mock must return a real (possibly empty) Flow — Mockito's null
        // default would NPE the flatMapLatest collector.
        val savedTabRepository = mock<SavedTabRepository> {
            on { getTabsForVault(any()) } doReturn flowOf(emptyList())
        }
        val checklistViewStateManager = mock<ChecklistViewStateManager>()
        vm = ChecklistViewModel(
            repo,
            vaultRepository = vaultRepo,
            settingsRepository = settingsRepo,
            savedTabRepository = savedTabRepository,
            checklistViewStateManager = checklistViewStateManager,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    /** Flush Room's InvalidationTracker notifications through the Robolectric main looper. */
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Test
    fun load_creates_default_tab_and_section_on_empty_vault() = runTest {
        vm.load(vaultId)
        idle() // let Room push the tab invalidation through the main looper

        val tabs = vm.tabs.first { it.isNotEmpty() }
        assertEquals(1, tabs.size)
        // Bootstrap tab name was renamed "Checklist" → "Tab 1" in commit 0b57af98.
        assertEquals("Tab 1", tabs[0].name)

        val active = vm.activeTabId.first { it != null }
        assertEquals(tabs[0].id, active)
    }

    @Test
    fun createTab_adds_tab_and_selects_it() = runTest {
        vm.load(vaultId)
        idle()
        vm.tabs.first { it.isNotEmpty() }

        vm.createTab("Bosses", colorIndex = 0)
        idle()

        val tabs = vm.tabs.first { it.size >= 2 }
        assertEquals(2, tabs.size)
        assertEquals("Bosses", tabs[1].name)

        val active = vm.activeTabId.first { it == tabs[1].id }
        assertEquals(tabs[1].id, active)
    }

    @Test
    fun toggleAllSectionsExpanded_flips_expanded_state_in_active_tab() = runTest {
        vm.load(vaultId)
        idle()
        // load() only seeds a default tab now (commit 0b57af98) — sections are
        // user-created. Use the VM's API so the section defaults match
        // production: vm.createSection passes expanded=true.
        vm.activeTabId.first { it != null }
        vm.createSection("First")
        idle()
        val initialSections = vm.sectionsWithItems.first { it.isNotEmpty() }
        assertTrue(initialSections.all { it.section.expanded })

        // Add a second section to the active tab via the VM API too, so both
        // start expanded — toggleAllSectionsExpanded should then collapse them.
        vm.createSection("Second")
        idle()

        vm.sectionsWithItems.first { list -> list.size >= 2 }

        vm.toggleAllSectionsExpanded()
        idle()

        val collapsed = vm.sectionsWithItems.first { list -> list.isNotEmpty() && list.all { !it.section.expanded } }
        assertTrue(collapsed.all { !it.section.expanded })
    }
}
