package com.twinlore.app.ui.screens

import com.twinlore.app.data.vgmaps.VgmapsCredit
import com.twinlore.app.data.vgmaps.VgmapsEntry
import com.twinlore.app.data.vgmaps.VgmapsGameDetails
import com.twinlore.app.data.vgmaps.VgmapsMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VgmapsPickerViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun entry(s: String) = VgmapsEntry("Castlevania: SOTN", s, "CSotN")
    private fun map(name: String) = VgmapsMap(name, "https://www.vgmaps.com/Atlas/PSX/$name.png")

    private fun details(vararg maps: VgmapsMap, credits: List<VgmapsCredit> = emptyList()) =
        VgmapsGameDetails(maps = maps.toList(), credits = credits)

    @Test fun `init - no match → NoMatch state`() = runTest {
        val vm = VgmapsPickerViewModel(
            gameName = "Random Indie Game 2024",
            matcher = { emptyList() },
            scraper = { _ -> Result.success(details()) },
        )
        advanceUntilIdle()
        assertEquals(VgmapsPickerState.NoMatch, vm.state.first())
    }

    @Test fun `init - single match → Ready with that entry selected`() = runTest {
        val maps = listOf(map("A"), map("B"))
        val vm = VgmapsPickerViewModel(
            gameName = "Castlevania",
            matcher = { listOf(entry("PSX")) },
            scraper = { _ -> Result.success(VgmapsGameDetails(maps, emptyList())) },
        )
        advanceUntilIdle()
        val s = vm.state.first()
        assertTrue("State was $s", s is VgmapsPickerState.Ready)
        s as VgmapsPickerState.Ready
        assertEquals(listOf(entry("PSX")), s.entries)
        assertEquals(entry("PSX"), s.selectedEntry)
        assertEquals(maps, s.maps)
        assertTrue(s.selectedUrls.isEmpty())
    }

    @Test fun `init - multi match → Ready with first entry selected, others in entries list`() = runTest {
        val vm = VgmapsPickerViewModel(
            gameName = "Castlevania: SOTN",
            matcher = { listOf(entry("PSX"), entry("Saturn")) },
            scraper = { _ -> Result.success(VgmapsGameDetails(listOf(map("X")), emptyList())) },
        )
        advanceUntilIdle()
        val s = vm.state.first() as VgmapsPickerState.Ready
        assertEquals(2, s.entries.size)
        assertEquals("PSX", s.selectedEntry.system)
    }

    @Test fun `onSelectEntry - switches entry, refetches, clears selection`() = runTest {
        val perSystem = mapOf(
            "PSX" to VgmapsGameDetails(listOf(map("PA")), emptyList()),
            "Saturn" to VgmapsGameDetails(listOf(map("SA"), map("SB")), emptyList()),
        )
        val vm = VgmapsPickerViewModel(
            gameName = "Castlevania: SOTN",
            matcher = { listOf(entry("PSX"), entry("Saturn")) },
            scraper = { e -> Result.success(perSystem.getValue(e.system)) },
        )
        advanceUntilIdle()
        vm.onSelectEntry(entry("Saturn"))
        advanceUntilIdle()
        val s = vm.state.first() as VgmapsPickerState.Ready
        assertEquals("Saturn", s.selectedEntry.system)
        assertEquals(2, s.maps.size)
        assertTrue(s.selectedUrls.isEmpty())
    }

    @Test fun `onToggle - flips membership`() = runTest {
        val maps = listOf(map("A"), map("B"))
        val vm = VgmapsPickerViewModel(
            gameName = "x",
            matcher = { listOf(entry("PSX")) },
            scraper = { Result.success(VgmapsGameDetails(maps, emptyList())) },
        )
        advanceUntilIdle()
        vm.onToggle(maps[0].imageUrl)
        vm.onToggle(maps[1].imageUrl)
        vm.onToggle(maps[0].imageUrl)
        val s = vm.state.first() as VgmapsPickerState.Ready
        assertEquals(setOf(maps[1].imageUrl), s.selectedUrls)
    }

    @Test fun `onSelectAll - includes every map`() = runTest {
        val maps = listOf(map("A"), map("B"), map("C"))
        val vm = VgmapsPickerViewModel(
            gameName = "x",
            matcher = { listOf(entry("PSX")) },
            scraper = { Result.success(VgmapsGameDetails(maps, emptyList())) },
        )
        advanceUntilIdle()
        vm.onSelectAll()
        val s = vm.state.first() as VgmapsPickerState.Ready
        assertEquals(3, s.selectedUrls.size)
    }

    @Test fun `scraper failure → FetchError state`() = runTest {
        val vm = VgmapsPickerViewModel(
            gameName = "x",
            matcher = { listOf(entry("PSX")) },
            scraper = { Result.failure(RuntimeException("boom")) },
        )
        advanceUntilIdle()
        val s = vm.state.first()
        assertTrue("State was $s", s is VgmapsPickerState.FetchError)
    }

    @Test fun `credits propagate into Ready state`() = runTest {
        val credits = listOf(
            VgmapsCredit(name = "Cartographer", email = "cartographer@example.com"),
            VgmapsCredit(name = "tilewalker", email = null),
        )
        val vm = VgmapsPickerViewModel(
            gameName = "Castlevania: SOTN",
            matcher = { listOf(entry("PSX")) },
            scraper = { _ -> Result.success(VgmapsGameDetails(listOf(map("A")), credits)) },
        )
        advanceUntilIdle()
        val s = vm.state.first() as VgmapsPickerState.Ready
        assertEquals(credits, s.credits)
    }
}
