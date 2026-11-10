package com.twinlore.app.ui.screens.catalog

import android.content.Context
import com.twinlore.app.data.DualDisplayLauncher
import com.twinlore.app.data.SettingsRepository
import com.twinlore.app.data.catalog.ApkDownloadManager
import com.twinlore.app.data.catalog.RecommendedAppsCatalog
import com.twinlore.app.domain.Category
import com.twinlore.app.domain.DeviceType
import com.twinlore.app.domain.RecommendedApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlinx.coroutines.runBlocking

@OptIn(ExperimentalCoroutinesApi::class)
class AppCatalogViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val context = mock<Context>()
    private val dualDisplayLauncher = mock<DualDisplayLauncher>()
    private val settingsRepository = mock<SettingsRepository>()
    private val catalog = mock<RecommendedAppsCatalog>()
    private val apkDownloadManager = mock<ApkDownloadManager>()
    private val tabFlow = MutableStateFlow(Category.Emulator)
    private val deviceTypeFlow = MutableStateFlow(DeviceType.SingleScreen)

    private val sampleApps = listOf(
        app("a", "aPS3e", Category.Emulator, "PlayStation 3"),
        app("b", "Dolphin Emulator", Category.Emulator, "GameCube + Wii"),
        app("c", "Cemu", Category.Emulator, "Wii U"),
        app("d", "RetroArch", Category.Emulator, "Many systems"),
        app("e", "Pegasus", Category.Frontend, "Game launcher"),
        app("f", "Moonlight", Category.Streaming, "Game streaming")
    )

    private fun app(id: String, name: String, category: Category, about: String): RecommendedApp {
        // A minimal valid upstream entry shape — buildObtainiumImportJson re-parses it.
        val entryJson = """
            {
              "id": "$id",
              "url": "https://example.test/$id",
              "name": "$name",
              "author": "test",
              "categories": ["${category.name}"],
              "additionalSettings": "{\"about\":\"$about\"}",
              "apkUrls": null,
              "otherAssetUrls": null
            }
        """.trimIndent()
        return RecommendedApp(
            id = id,
            name = name,
            author = "test",
            category = category,
            sourceUrl = "https://example.test/$id",
            about = about,
            entryJson = entryJson
        )
    }

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        whenever(settingsRepository.catalogSelectedTab).thenReturn(tabFlow)
        whenever(settingsRepository.catalogDeviceType).thenReturn(deviceTypeFlow)
        whenever(apkDownloadManager.states).thenReturn(MutableStateFlow(emptyMap()))
        // toggleSelectAllFiltered → isAppInstalled → resolveRealPackageName
        // reads apkDownloadManager.downloadDir.listFiles(). Mock returns null
        // for the File by default, NPE'ing the listFiles() call. Point it at a
        // path that doesn't exist so listFiles() returns null and the safe-call
        // chain in resolveRealPackageName degrades cleanly.
        whenever(apkDownloadManager.downloadDir).thenReturn(java.io.File("/dev/null/nonexistent"))
        runBlocking {
            whenever(catalog.load(any())).thenReturn(sampleApps)
            // VM init() auto-calls refresh() on construction (commit 3bce268).
            // Stub a Failure so the production path falls through cleanly without
            // hitting GitHub, and so the non-null onResult lambda parameter isn't
            // handed a null result.
            whenever(catalog.refresh()).thenReturn(
                RecommendedAppsCatalog.RefreshResult.Failure("offline test")
            )
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newViewModel() = AppCatalogViewModel(
        context = context,
        settingsRepository = settingsRepository,
        dualDisplayLauncher = dualDisplayLauncher,
        catalog = catalog,
        apkDownloadManager = apkDownloadManager
    )

    @Test
    fun `Emulator tab returns all Emulator entries sorted by name`() = runTest {
        val vm = newViewModel()
        advanceUntilIdle()
        val names = vm.filteredApps.first().map { it.name }
        assertEquals(listOf("aPS3e", "Cemu", "Dolphin Emulator", "RetroArch"), names)
    }

    @Test
    fun `tab switch shows only that category`() = runTest {
        val vm = newViewModel()
        tabFlow.value = Category.Streaming
        advanceUntilIdle()
        val names = vm.filteredApps.first().map { it.name }
        assertEquals(listOf("Moonlight"), names)
    }

    @Test
    fun `search matches name case-insensitively`() = runTest {
        val vm = newViewModel()
        vm.onSearchQueryChange("dolphin")
        advanceUntilIdle()
        val names = vm.filteredApps.first().map { it.name }
        assertEquals(listOf("Dolphin Emulator"), names)
    }

    @Test
    fun `search matches about text`() = runTest {
        val vm = newViewModel()
        vm.onSearchQueryChange("wii u")
        advanceUntilIdle()
        val names = vm.filteredApps.first().map { it.name }
        assertEquals(listOf("Cemu"), names)
    }

    @Test
    fun `toggleSelection adds and removes keys preserving insertion order`() = runTest {
        val vm = newViewModel()
        advanceUntilIdle()
        vm.toggleSelection("a")
        vm.toggleSelection("d")
        vm.toggleSelection("a") // remove
        vm.toggleSelection("b")
        val selected = vm.selectedKeys.first()
        assertEquals(listOf("d", "b"), selected.toList())
    }

    @Test
    fun `toggleSelectAllFiltered selects every visible row without touching others`() = runTest {
        val vm = newViewModel()
        advanceUntilIdle()
        tabFlow.value = Category.Streaming
        advanceUntilIdle()
        vm.toggleSelection("f") // Moonlight
        tabFlow.value = Category.Emulator
        advanceUntilIdle()
        vm.toggleSelectAllFiltered()
        val selected = vm.selectedKeys.first()
        assertEquals(setOf("f", "a", "b", "c", "d"), selected)
    }

    @Test
    fun `toggleSelectAllFiltered deselects visible rows when all are already selected`() = runTest {
        val vm = newViewModel()
        advanceUntilIdle()
        // Pre-select a row in a different tab so we can prove it stays.
        tabFlow.value = Category.Streaming
        advanceUntilIdle()
        vm.toggleSelection("f")
        tabFlow.value = Category.Emulator
        advanceUntilIdle()
        // First tap: select all four visible Emulator rows.
        vm.toggleSelectAllFiltered()
        assertEquals(setOf("f", "a", "b", "c", "d"), vm.selectedKeys.first())
        // Second tap: deselect them all. The non-visible "f" survives.
        vm.toggleSelectAllFiltered()
        assertEquals(setOf("f"), vm.selectedKeys.first())
    }

    @Test
    fun `clearSelection empties the set`() = runTest {
        val vm = newViewModel()
        advanceUntilIdle()
        vm.toggleSelection("a")
        vm.toggleSelection("b")
        vm.clearSelection()
        assertTrue(vm.selectedKeys.first().isEmpty())
    }

    @Test
    fun `buildObtainiumImportJson emits Obtainium export shape with selected apps`() = runTest {
        val vm = newViewModel()
        advanceUntilIdle()
        vm.toggleSelection("b")
        vm.toggleSelection("a")
        val json = org.json.JSONObject(vm.buildObtainiumImportJson())
        val apps = json.getJSONArray("apps")
        assertEquals(2, apps.length())
        // Selection order preserved.
        assertEquals("b", apps.getJSONObject(0).getString("id"))
        assertEquals("a", apps.getJSONObject(1).getString("id"))
        for (i in 0 until apps.length()) {
            val app = apps.getJSONObject(i)
            assertTrue(app.isNull("installedVersion"))
            assertTrue(app.isNull("latestVersion"))
            assertEquals(false, app.getBoolean("pinned"))
            assertEquals("[]", app.getString("apkUrls"))
            assertEquals("[]", app.getString("otherAssetUrls"))
        }
    }

    @Test
    fun `googleSearchUrl encodes name with spaces and parentheses`() {
        // udm=14 surfaces web results only (commit 5554595), suppressing
        // Google's AI Overview / Discover panel.
        assertEquals(
            "https://www.google.com/search?udm=14&q=RetroArch+%28AArch64%29+android+setup+guide",
            AppCatalogViewModel.googleSearchUrl("RetroArch (AArch64)")
        )
    }

    @Test
    fun `youtubeSearchUrl encodes name with spaces and parentheses`() {
        assertEquals(
            "https://www.youtube.com/results?search_query=RetroArch+%28AArch64%29+android+setup+guide",
            AppCatalogViewModel.youtubeSearchUrl("RetroArch (AArch64)")
        )
    }
}
