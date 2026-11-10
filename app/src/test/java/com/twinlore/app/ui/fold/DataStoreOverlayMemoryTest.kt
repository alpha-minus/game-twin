package com.twinlore.app.ui.fold

import com.twinlore.app.data.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Overlay mode as it survives a process death.
 *
 * Same DataStore singleton caveat as [com.twinlore.app.data.SettingsRepositoryFoldTest]:
 * every test resets the key after itself.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DataStoreOverlayMemoryTest {

    private fun repo() = SettingsRepository(RuntimeEnvironment.getApplication())

    /**
     * Waits for the writes the memory launched on this scope. Advancing the
     * scheduler is not enough: a DataStore edit suspends in real IO, and a
     * read issued while it is pending sees the old value.
     */
    private suspend fun TestScope.flushWrites() = coroutineContext.job.children.toList().joinAll()

    @Before
    fun main() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun reset() {
        runTest { repo().setOverlayMode(false) }
        Dispatchers.resetMain()
    }

    @Test
    fun `turning overlay mode on and off reaches the store`() = runTest {
        val repo = repo()
        val memory = DataStoreOverlayMemory(repo, this)

        memory.setOverlayMode(true)
        flushWrites()
        assertEquals(true, repo.overlayMode.first())
        assertEquals(true, memory.wasOverlayMode())

        memory.setOverlayMode(false)
        flushWrites()
        assertEquals(false, repo.overlayMode.first())
    }

    // An enter and a leave in quick succession must not land in reverse.
    @Test
    fun `the last of several quick writes is what the store keeps`() = runTest {
        val repo = repo()
        val memory = DataStoreOverlayMemory(repo, this)

        memory.setOverlayMode(true)
        memory.setOverlayMode(false)
        memory.setOverlayMode(true)
        flushWrites()

        assertEquals(true, repo.overlayMode.first())
    }

    // A process restart mid-overlay has to find the mode again.
    @Test
    fun `a fresh memory reads what the last run left`() = runTest {
        val repo = repo()
        repo.setOverlayMode(true)

        assertEquals(true, DataStoreOverlayMemory(repo, this).wasOverlayMode())
    }
}
