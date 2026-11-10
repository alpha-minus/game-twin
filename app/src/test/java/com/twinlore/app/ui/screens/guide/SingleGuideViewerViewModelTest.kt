package com.twinlore.app.ui.screens.guide

import com.twinlore.app.domain.ScrollMarker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SingleGuideViewerViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun pendingScrollToMarker_singleShot() {
        val vm = SingleGuideViewerViewModel(savedTabRepository = NullRepo)
        val pm = SingleGuideViewerViewModel.PendingScrollToMarker(
            label = "Boss",
            fallbackScrollY = 1000,
            occurrenceIndex = 1,
            flashHex = "#ff0000"
        )
        vm.enqueuePendingScrollToMarker(pm)
        assertEquals(pm, vm.consumePendingScrollToMarker())
        assertNull("queue should be empty after consume", vm.consumePendingScrollToMarker())
    }

    @Test
    fun clearPendingScrollToMarker_dropsQueued() {
        val vm = SingleGuideViewerViewModel(savedTabRepository = NullRepo)
        vm.enqueuePendingScrollToMarker(
            SingleGuideViewerViewModel.PendingScrollToMarker(
                label = "x",
                fallbackScrollY = 0,
                occurrenceIndex = 0,
                flashHex = null
            )
        )
        vm.clearPendingScrollToMarker()
        assertNull(vm.consumePendingScrollToMarker())
    }

    private object NullRepo : SingleGuideViewerViewModel.Repo {
        override fun markersFor(bookmarkId: String) =
            kotlinx.coroutines.flow.flowOf(emptyList<ScrollMarker>())
    }
}
