package com.twinlore.app.ui.screens

import androidx.test.core.app.ApplicationProvider
import com.twinlore.app.data.MapViewStateManager
import com.twinlore.app.data.SavedMapRepository
import com.twinlore.app.domain.SavedMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

/**
 * Which image the Image tab is open on, against the record it shares with
 * the Always on Top panel. The tab records every selection it makes, opens on
 * the record when it has nothing better, and follows the record on request
 * without writing it back.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MapViewerScreenViewModelOverlaySyncTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var store: MapViewStateManager
    private val maps = MutableStateFlow(listOf(map("a"), map("b"), map("c")))

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        store = MapViewStateManager(ApplicationProvider.getApplicationContext())
    }

    @After fun tearDown() { Dispatchers.resetMain() }

    private fun viewModel(): MapViewerScreenViewModel {
        val repository = mock<SavedMapRepository> {
            on { getMapsByVaultId("v1") } doReturn maps
        }
        return MapViewerScreenViewModel(mock(), store, mock(), repository, mock(), mock(), mock())
    }

    @Test
    fun `with nothing recorded the first image is selected and recorded`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.observeSavedMaps("v1")
        advanceUntilIdle()

        assertEquals("a", vm.selectedMapId.value)
        assertEquals("a", store.getSelectedMapId("v1"))
    }

    // The panel paged to an image and recorded it before the tab was ever
    // composed for this vault: the tab opens there, not on the newest.
    @Test
    fun `a recorded image is where the tab opens`() = runTest(dispatcher) {
        store.saveSelectedMapId("v1", "c")
        val vm = viewModel()
        vm.observeSavedMaps("v1")
        advanceUntilIdle()

        assertEquals("c", vm.selectedMapId.value)
    }

    @Test
    fun `a recorded image the vault no longer has gives way to the first`() = runTest(dispatcher) {
        store.saveSelectedMapId("v1", "gone")
        val vm = viewModel()
        vm.observeSavedMaps("v1")
        advanceUntilIdle()

        assertEquals("a", vm.selectedMapId.value)
        assertEquals("the record is corrected", "a", store.getSelectedMapId("v1"))
    }

    @Test
    fun `selecting records the selection`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.observeSavedMaps("v1")
        advanceUntilIdle()

        vm.selectMap("b")
        advanceUntilIdle()

        assertEquals("b", vm.selectedMapId.value)
        assertEquals("b", store.getSelectedMapId("v1"))
    }

    // Back from Always on Top: the panel recorded "c" while this view model
    // sat on "a". Following is a read, not a write, and an unknown or absent
    // record leaves the selection alone.
    @Test
    fun `adopting the record follows it and nothing else`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.observeSavedMaps("v1")
        advanceUntilIdle()
        assertEquals("a", vm.selectedMapId.value)

        store.saveSelectedMapId("v1", "c")
        vm.adoptStoredSelection("v1")
        assertEquals("c", vm.selectedMapId.value)

        store.saveSelectedMapId("v1", "gone")
        vm.adoptStoredSelection("v1")
        assertEquals("c", vm.selectedMapId.value)

        store.saveSelectedMapId("v1", null)
        vm.adoptStoredSelection("v1")
        assertEquals("c", vm.selectedMapId.value)
        assertNull("adopting never writes", store.getSelectedMapId("v1"))
    }

    private fun map(id: String) = SavedMap(
        id = id,
        vaultId = "v1",
        title = "Image $id",
        createdAt = Date(0),
        lastAccessed = Date(0),
    )
}
