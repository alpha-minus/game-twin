package com.twinlore.app.ui.screens.group

import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.twinlore.app.data.GroupsFolderResolver
import com.twinlore.app.data.PageGroupRepository
import com.twinlore.app.data.local.ResourceFileEntity
import com.twinlore.app.data.local.VaultDatabase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * The group viewer against the store it shares with its own copy in the
 * Always on Top panel.
 *
 * Two things have to hold. The panel's last write must land even though the
 * panel's window clears its view model store on the same call stack as the
 * dispose that writes it. And the app's viewer, which stayed composed behind
 * the panel, must be able to read back what the panel wrote and follow it
 * without writing anything itself.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PageGroupViewerViewModelOverlaySyncTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var db: VaultDatabase
    private lateinit var groupRoot: File
    private lateinit var repo: PageGroupRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            VaultDatabase::class.java
        ).allowMainThreadQueries().build()
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

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
        groupRoot.deleteRecursively()
    }

    // The panel's dispose writes the chapter's position, and the very next
    // thing on that call stack is the window clearing its view model store.
    // The write is parked on its way to the database at that moment, and a
    // cancellable write was cancelled there every time.
    @Test
    fun `a scroll write started before the store is cleared still lands`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val written = mutableListOf<Pair<String, Double>>()
        val slowRepo = mock<PageGroupRepository> {
            on { observeGroupMarkers(any()) } doReturn flowOf(emptyList())
            onBlocking {
                updateEntryScroll(any(), any(), anyOrNull(), any(), any())
            } doSuspendableAnswer { invocation ->
                gate.await()
                written += invocation.getArgument<String>(1) to invocation.getArgument<Double>(4)
                Unit
            }
        }
        val vm = PageGroupViewerViewModel(db.savedTabDao(), db.pageGroupEntryDao(), slowRepo)
        val store = ViewModelStore().apply { put("vm", vm) }
        vm.bind(seedTwoEntryGroup())
        advanceUntilIdle()

        vm.persistScrollRatio("e-out", 0.6)
        advanceUntilIdle()
        // The window comes down: the scope is cancelled with the write parked.
        store.clear()
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf("e-out" to 0.6), written)
    }

    // The app's viewer was bound before the panel opened. The panel moved the
    // group to another chapter and scrolled it; the app's viewer reads that
    // back from the store, not from what it seeded at bind.
    @Test
    fun `the stored position is read fresh and adopted without a write`() = runTest(dispatcher) {
        val vm = PageGroupViewerViewModel(db.savedTabDao(), db.pageGroupEntryDao(), repo)
        val groupId = seedTwoEntryGroup()
        vm.bind(groupId)
        // Room answers bind from its own thread, so wait on the state rather
        // than on the scheduler.
        val bound = vm.state.first { it.activeEntryId != null }
        val entries = db.pageGroupEntryDao().getForGroup(groupId)
        assertEquals("bound on the first chapter", entries[0].id, bound.activeEntryId)

        // The panel, through its own view model over the same rows.
        repo.updateActiveEntry(groupId, entries[1].id)
        repo.updateEntryScroll(groupId, entries[1].id, anchor = null, occurrenceIndex = 0, ratio = 0.7)

        val stored = vm.storedPosition()
        assertEquals(PageGroupViewerViewModel.StoredPosition(entries[1].id, 0.7), stored)

        val tapsBefore = vm.state.value.lastTapSerial
        vm.adoptStoredEntry(entries[1].id)
        assertEquals(entries[1].id, vm.state.value.activeEntryId)
        assertEquals("following the store is not a tap", tapsBefore, vm.state.value.lastTapSerial)
        assertEquals("and the store is left as it was", entries[1].id, db.savedTabDao().getTabById(groupId)?.groupActiveEntryId)
    }

    @Test
    fun `no active chapter recorded means nothing to follow`() = runTest(dispatcher) {
        val vm = PageGroupViewerViewModel(db.savedTabDao(), db.pageGroupEntryDao(), repo)
        val groupId = seedTwoEntryGroup()
        vm.bind(groupId)
        vm.state.first { it.activeEntryId != null }
        repo.updateActiveEntry(groupId, null)

        assertNull(vm.storedPosition())
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
