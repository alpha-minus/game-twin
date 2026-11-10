package com.twinlore.app.ui.screens

import com.twinlore.app.data.library.LibraryIntegrityChecker
import com.twinlore.app.data.local.VaultContentCount
import com.twinlore.app.data.local.VaultDao
import com.twinlore.app.domain.Vault
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
class MultiDeleteViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val vaultDao: VaultDao = mock()
    private val checker: LibraryIntegrityChecker = mock()
    private val savedTabs: com.twinlore.app.data.SavedTabRepository = mock()
    private val autoSave: com.twinlore.app.data.library.LibraryAutoSaveObserver = mock()
    private val images: com.twinlore.app.data.library.VaultImageCleaner = mock()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun vault(id: String) = Vault(
        id = id, name = id, romPath = null, romFilename = null, systemeid = null,
        gameId = null, source = null, lastOpened = Date(0), createdAt = Date(0),
    )

    @Test
    fun list_shows_before_the_file_check_and_fills_missing_counts_after() = runTest(dispatcher) {
        val gate = CompletableDeferred<Map<String, Int>>()
        whenever(vaultDao.getContentCounts()).thenReturn(listOf(VaultContentCount("a", 2, guideCount = 4, imageCount = 7)))
        whenever(checker.missingCountsByVault()).doSuspendableAnswer { gate.await() }
        val vm = MultiDeleteViewModel(vaultDao, checker, savedTabs, images, autoSave) { 0L }

        vm.load(listOf(vault("a"), vault("b")))
        dispatcher.scheduler.runCurrent()

        val checking = vm.state.value as MultiDeleteViewModel.State.Checking
        assertEquals(listOf("a", "b"), checking.rows.map { it.vault.id })
        assertFalse(checking.rows[0].isEmpty)
        assertTrue(checking.rows[1].isEmpty)
        assertEquals(listOf(4, 0), checking.rows.map { it.guideCount })
        assertEquals(listOf(7, 0), checking.rows.map { it.imageCount })

        gate.complete(mapOf("b" to 3))
        dispatcher.scheduler.advanceUntilIdle()

        val ready = vm.state.value as MultiDeleteViewModel.State.Ready
        assertEquals(listOf(0, 3), ready.rows.map { it.missingFileCount })
    }

    @Test
    fun a_failed_file_check_still_ends_ready_with_no_missing_files() = runTest(dispatcher) {
        whenever(vaultDao.getContentCounts()).thenReturn(emptyList())
        whenever(checker.missingCountsByVault()).thenThrow(RuntimeException("saf"))
        val vm = MultiDeleteViewModel(vaultDao, checker, savedTabs, images, autoSave) { 0L }

        vm.load(listOf(vault("a")))
        dispatcher.scheduler.advanceUntilIdle()

        val ready = vm.state.value as MultiDeleteViewModel.State.Ready
        assertFalse(ready.rows.single().hasMissingFiles)
    }

    @Test
    fun guides_and_images_cleans_each_vault_keeps_them_and_saves_the_library_once() = runTest(dispatcher) {
        whenever(images.deleteAllImagesForVault("a")).thenReturn(2)
        whenever(images.deleteAllImagesForVault("b")).thenReturn(5)
        whenever(images.deleteAllImagesForVault("c")).thenReturn(0)
        whenever(savedTabs.deleteAllGuidesForVault("a")).thenReturn(3)
        whenever(savedTabs.deleteAllGuidesForVault("b")).thenThrow(RuntimeException("io"))
        whenever(savedTabs.deleteAllGuidesForVault("c")).thenReturn(1)
        val vm = MultiDeleteViewModel(vaultDao, checker, savedTabs, images, autoSave) { 0L }
        var result: MultiDeleteViewModel.CleanupResult? = null

        vm.deleteGuides(listOf("a", "b", "c")) { result = it }
        dispatcher.scheduler.advanceUntilIdle()

        // b's guide cleanup failed, but its images were still cleaned.
        assertEquals(MultiDeleteViewModel.CleanupResult(guides = 4, images = 7), result)
        assertEquals(null, vm.guideCleanupProgress.value)
        org.mockito.kotlin.verify(autoSave, org.mockito.kotlin.times(1)).saveAsync()
        org.mockito.kotlin.verify(vaultDao, org.mockito.kotlin.never()).deleteVaultById(org.mockito.kotlin.any())
    }
}
