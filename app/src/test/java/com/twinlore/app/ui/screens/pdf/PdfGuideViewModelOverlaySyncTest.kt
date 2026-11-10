package com.twinlore.app.ui.screens.pdf

import android.content.Context
import androidx.lifecycle.ViewModelStore
import com.twinlore.app.data.SavedTabRepository
import com.twinlore.app.domain.SavedTab
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import org.mockito.kotlin.any
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.mock
import java.util.Date

/**
 * The PDF viewer against the page store it shares with its own copy in the
 * Always on Top panel. Same two obligations as the group viewer: the panel's
 * last write lands despite the store clear that follows its dispose, and the
 * app's viewer can read the page back fresh.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PdfGuideViewModelOverlaySyncTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `a page write started before the store is cleared still lands`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val written = mutableListOf<Int>()
        val repo = mock<SavedTabRepository> {
            onBlocking { updatePdfState(any(), any(), any()) } doSuspendableAnswer { invocation ->
                gate.await()
                written += invocation.getArgument<Int>(1)
                com.twinlore.app.domain.Result.Success(Unit)
            }
        }
        val vm = PdfGuideViewModel(repo, mock(), mock<Context>())
        val store = ViewModelStore().apply { put("vm", vm) }
        vm.setGuide(guide(lastPage = 3))
        vm.onPageChanged(page = 12, total = 40)

        vm.savePdfState("pdf-1")
        advanceUntilIdle()
        store.clear()
        gate.complete(Unit)
        advanceUntilIdle()

        // The page change also queued a debounced write of the same page;
        // both were parked at the gate, and both land.
        assertEquals(setOf(12), written.toSet())
    }

    // Set from the guide it was handed, which is a snapshot; read back from the
    // store, which the panel's own viewer has since written.
    @Test
    fun `the stored page is read fresh, not from the guide it was set with`() = runTest(dispatcher) {
        val repo = mock<SavedTabRepository> {
            onBlocking { getTabById("pdf-1") } doSuspendableAnswer { guide(lastPage = 27) }
        }
        val vm = PdfGuideViewModel(repo, mock(), mock<Context>())
        vm.setGuide(guide(lastPage = 3))

        assertEquals(3, vm.currentPage.value)
        assertEquals(27, vm.storedPage())
    }

    @Test
    fun `no guide set means no stored page`() = runTest(dispatcher) {
        val vm = PdfGuideViewModel(mock(), mock(), mock<Context>())
        assertNull(vm.storedPage())
    }

    private fun guide(lastPage: Int) = SavedTab(
        id = "pdf-1",
        vaultId = "v1",
        title = "A manual",
        url = "",
        createdAt = Date(0),
        lastAccessed = Date(0),
        fileType = "pdf",
        lastPdfPage = lastPage,
    )
}
