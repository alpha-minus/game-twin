package com.twinlore.app.ui.screens.browser

import com.twinlore.app.data.BrowserTabRepository
import com.twinlore.app.domain.BrowserTab
import com.twinlore.app.domain.HistoryEntry
import com.twinlore.app.domain.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
class BrowserTabsViewModelTest {

    private lateinit var repo: BrowserTabRepository
    private lateinit var savedTabRepo: com.twinlore.app.data.SavedTabRepository
    private lateinit var vm: BrowserTabsViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before fun setup() {
        Dispatchers.setMain(testDispatcher)
        repo = mock()
        savedTabRepo = mock()
        whenever(repo.observeForVault(any())).thenReturn(MutableStateFlow(emptyList()))
        vm = BrowserTabsViewModel(repo, savedTabRepo, mock())
    }

    @After fun tearDown() { Dispatchers.resetMain() }

    private fun tab(id: String, lastAccessed: Long = 1L) = BrowserTab(
        id = id, vaultId = "v1", url = "u", title = "t",
        faviconBytes = null, scrollY = 0, tabOrder = 0,
        lastAccessed = lastAccessed, createdAt = lastAccessed
    )

    @Test fun `bind starts observing tabs for the given vault`() = runTest {
        vm.bind("v1") { "https://landing" }
        advanceUntilIdle()
        verify(repo).observeForVault("v1")
    }

    @Test fun `bind is idempotent for same vaultId`() = runTest {
        vm.bind("v1") { "https://landing" }
        vm.bind("v1") { "https://landing" }
        advanceUntilIdle()
        verify(repo, times(1)).observeForVault("v1")
    }

    @Test fun `bind calls ensureAtLeastOne with vaultId and landing url`() = runTest {
        whenever(repo.ensureAtLeastOne(any(), any())).thenReturn(tab("seeded"))
        vm.bind("v1") { "https://landing" }
        advanceUntilIdle()
        verify(repo).ensureAtLeastOne("v1", "https://landing")
    }

    @Test fun `newTab foreground delegates to repo with background=false`() = runTest {
        vm.bind("v1") { "https://landing" }
        whenever(repo.createTab(eq("v1"), eq("https://landing"), eq(""), eq(false)))
            .thenReturn(Result.success(tab("new")))

        vm.newTab().join()

        verify(repo).createTab("v1", "https://landing", "", false)
    }

    @Test fun `newTab background passes background=true`() = runTest {
        vm.bind("v1") { "https://landing" }
        whenever(repo.createTab(eq("v1"), eq("https://x"), eq(""), eq(true)))
            .thenReturn(Result.success(tab("bg")))

        vm.newTab(url = "https://x", background = true).join()

        verify(repo).createTab("v1", "https://x", "", true)
    }

    @Test fun `newTab emits tabOpenedPulse when pulseOnSuccess is true and result succeeds`() = runTest {
        vm.bind("v1") { "https://landing" }
        whenever(repo.createTab(eq("v1"), eq("https://x"), eq(""), eq(false)))
            .thenReturn(Result.success(tab("new")))

        val received = mutableListOf<Unit>()
        val collectJob = launch { vm.tabOpenedPulse.collect { received += Unit } }
        advanceUntilIdle()

        vm.newTab(url = "https://x", background = false, pulseOnSuccess = true).join()
        advanceUntilIdle()

        assertEquals(1, received.size)
        collectJob.cancel()
    }

    @Test fun `newTab does not pulse when pulseOnSuccess is false`() = runTest {
        vm.bind("v1") { "https://landing" }
        whenever(repo.createTab(eq("v1"), eq("https://x"), eq(""), eq(false)))
            .thenReturn(Result.success(tab("new")))

        val received = mutableListOf<Unit>()
        val collectJob = launch { vm.tabOpenedPulse.collect { received += Unit } }
        advanceUntilIdle()

        vm.newTab(url = "https://x", background = false).join()
        advanceUntilIdle()

        assertEquals(0, received.size)
        collectJob.cancel()
    }

    @Test fun `newTab does not pulse when pulseOnSuccess is true but result fails`() = runTest {
        vm.bind("v1") { "https://landing" }
        whenever(repo.createTab(any(), any(), any(), any()))
            .thenReturn(Result.failure(IllegalStateException("Tab cap of 50 reached")))

        val received = mutableListOf<Unit>()
        val collectJob = launch { vm.tabOpenedPulse.collect { received += Unit } }
        advanceUntilIdle()

        vm.newTab(url = "https://x", background = false, pulseOnSuccess = true).join()
        advanceUntilIdle()

        assertEquals(0, received.size)
        collectJob.cancel()
    }

    @Test fun `newTab on cap exceeded emits toast`() = runTest {
        vm.bind("v1") { "https://landing" }
        whenever(repo.createTab(any(), any(), any(), any()))
            .thenReturn(Result.failure(IllegalStateException("Tab cap of 50 reached")))

        val toasts = mutableListOf<String>()
        val toastJob = TestScope(testDispatcher).launch {
            vm.toastEvent.collect { toasts.add(it) }
        }
        advanceUntilIdle()

        vm.newTab().join()
        advanceUntilIdle()
        toastJob.cancel()

        assertTrue("expected a toast about cap/close, got: $toasts",
            toasts.any { it.contains("cap", ignoreCase = true) || it.contains("close", ignoreCase = true) })
    }

    @Test fun `newTab refuses a file url handed in from a page`() = runTest {
        vm.bind("v1") { "https://landing" }
        vm.newTab(url = "file:///data/data/com.twinlore.app/cache/x.html").join()
        verify(repo, never()).createTab(any(), any(), any(), any())
    }

    @Test fun `newTab refuses content and javascript urls`() = runTest {
        vm.bind("v1") { "https://landing" }
        vm.newTab(url = "content://com.android.externalstorage.documents/document/x").join()
        vm.newTab(url = "javascript:alert(1)").join()
        verify(repo, never()).createTab(any(), any(), any(), any())
    }

    @Test fun `isOpenableInTab accepts http and https only`() {
        assertTrue(BrowserTabsViewModel.isOpenableInTab("https://gamefaqs.gamespot.com/x"))
        assertTrue(BrowserTabsViewModel.isOpenableInTab("HTTP://old.fansite/x"))
        assertFalse(BrowserTabsViewModel.isOpenableInTab("file:///x"))
        assertFalse(BrowserTabsViewModel.isOpenableInTab("intent://x#Intent;end"))
    }

    @Test fun `switchTo touches the tab`() = runTest {
        vm.bind("v1") { "https://landing" }

        vm.switchTo("abc").join()

        verify(repo).touch("abc")
    }

    @Test fun `closeTab on multi-tab list calls repo close`() = runTest {
        val seedTabs = MutableStateFlow(listOf(tab("a"), tab("only")))
        whenever(repo.observeForVault("v1")).thenReturn(seedTabs)
        whenever(repo.ensureAtLeastOne(eq("v1"), eq("https://landing"))).thenReturn(tab("fresh"))
        vm.bind("v1") { "https://landing" }
        // Let bind's own ensureAtLeastOne coroutine complete before we call closeTab.
        advanceUntilIdle()
        whenever(repo.closeTab("only")).thenReturn(Result.success(Unit))

        vm.closeTab("only").join()

        verify(repo).closeTab("only")
    }

    @Test fun `closeTab buffers the closed tab for undo`() = runTest {
        val tabsFlow = MutableStateFlow(listOf(tab("keeper"), tab("victim")))
        whenever(repo.observeForVault("v1")).thenReturn(tabsFlow)
        whenever(repo.closeTab("victim")).thenReturn(Result.success(Unit))
        whenever(repo.ensureAtLeastOne(any(), any())).thenReturn(tab("fresh"))
        vm.bind("v1") { "https://landing" }
        advanceUntilIdle()

        vm.closeTab("victim").join()
        // Check before advancing past the 5s auto-dismiss delay.
        assertEquals("victim", vm.lastClosedTab.value?.id)
    }

    @Test fun `undoClose restores the buffered tab and clears the slot`() = runTest {
        val tabsFlow = MutableStateFlow(listOf(tab("keeper"), tab("victim")))
        whenever(repo.observeForVault("v1")).thenReturn(tabsFlow)
        whenever(repo.closeTab(any())).thenReturn(Result.success(Unit))
        whenever(repo.ensureAtLeastOne(any(), any())).thenReturn(tab("fresh"))
        whenever(repo.restoreTab(any())).thenReturn(Result.success(Unit))
        vm.bind("v1") { "https://landing" }
        advanceUntilIdle()
        vm.closeTab("victim").join()

        vm.undoClose().join()

        verify(repo).restoreTab(argThat { id == "victim" })
        assertEquals(null, vm.lastClosedTab.value)
    }

    @Test fun `dismissUndo clears the slot without calling repo restore`() = runTest {
        val tabsFlow = MutableStateFlow(listOf(tab("keeper"), tab("victim")))
        whenever(repo.observeForVault("v1")).thenReturn(tabsFlow)
        whenever(repo.closeTab(any())).thenReturn(Result.success(Unit))
        whenever(repo.ensureAtLeastOne(any(), any())).thenReturn(tab("fresh"))
        vm.bind("v1") { "https://landing" }
        advanceUntilIdle()
        vm.closeTab("victim").join()

        vm.dismissUndo()

        assertEquals(null, vm.lastClosedTab.value)
        verify(repo, never()).restoreTab(any())
    }

    @Test fun `onPageFinished records navigation for normal in-tab clicks`() = runTest {
        val tabsFlow = MutableStateFlow(listOf(
            tabWithHistory("active", listOf(HistoryEntry("https://current/", "Current")), idx = 0)
        ))
        whenever(repo.observeForVault("v1")).thenReturn(tabsFlow)
        vm.bind("v1") { "https://landing" }
        advanceUntilIdle()

        // loadingForTabId starts null (no in-flight switch) AND no history-replay flag → genuine nav.
        vm.onPageFinished("https://example.com", "Example").join()

        verify(repo).recordNavigation("active", "https://example.com", "Example")
    }

    @Test fun `onPageFinished is dropped when loadingForTabId points to a different tab`() = runTest {
        val tabsFlow = MutableStateFlow(listOf(tab("active")))
        whenever(repo.observeForVault("v1")).thenReturn(tabsFlow)
        vm.bind("v1") { "https://landing" }
        advanceUntilIdle()

        // Simulate a switch to a different tab id, leaving loadingForTabId pointing at it.
        // Then the previous tab's onPageFinished arrives late — must be dropped.
        vm.switchTo("other-id").join()
        // activeTab is still "active" (the flow hasn't emitted a new head); loadingForTabId is "other-id".
        vm.onPageFinished("https://stale.com", "Stale").join()

        verify(repo, never()).updateUrl(eq("active"), any(), any())
        verify(repo, never()).recordNavigation(eq("active"), any(), any())
    }

    @Test fun `onPageFinished is a no-op when no active tab`() = runTest {
        vm.bind("v1") { "https://landing" }
        // Flow is emptyList by default → activeTab = null
        vm.onPageFinished("https://example.com", "Example").join()
        verify(repo, never()).updateUrl(any(), any(), any())
        verify(repo, never()).recordNavigation(any(), any(), any())
    }

    @Test fun `onTitleReceived updates title preserving the active URL`() = runTest {
        val tabsFlow = MutableStateFlow(listOf(tab("active").copy(url = "https://gamefaqs.com")))
        whenever(repo.observeForVault("v1")).thenReturn(tabsFlow)
        vm.bind("v1") { "https://landing" }
        advanceUntilIdle()

        vm.onTitleReceived("GameFAQs Walkthrough").join()

        verify(repo).updateUrl("active", "https://gamefaqs.com", "GameFAQs Walkthrough")
    }

    @Test fun `consumeScrollRestoreFor clears flag after first call`() = runTest {
        vm.bind("v1") { "https://landing" }
        // switchTo sets the flag
        vm.switchTo("abc").join()
        assertTrue(vm.consumeScrollRestoreFor("abc"))
        assertFalse(vm.consumeScrollRestoreFor("abc"))  // already consumed
    }

    /**
     * A NEW tab never asks for a scroll restore, however it was opened.
     *
     * It has no position of its own to return to, and its row's `scrollY` is not necessarily 0:
     * BrowserScreen flushes the live scroll onto whichever tab is active when its debounce fires,
     * which lands on the tab that has just been created. Opening a guide from the vault's browse
     * menu while halfway down a long page therefore used to hand the fresh page a target of a few
     * thousand pixels, and the restore obliged - the reported "the guide opens already scrolled
     * down". The flag that suppresses the history push is a different flag now; the test below
     * pins that half.
     */
    @Test fun `a new tab does not ask for a scroll restore`() = runTest {
        vm.bind("v1") { "https://landing" }
        whenever(repo.createTab(eq("v1"), eq("https://guide"), eq(""), eq(false)))
            .thenReturn(Result.success(tab("fresh")))

        vm.newTab(url = "https://guide", background = false).join()

        assertFalse(vm.consumeScrollRestoreFor("fresh"))
    }

    /** But it still counts as a reload for history: its row was created carrying that URL. */
    @Test fun `a new tab's first page does not push a second history entry`() = runTest {
        val flow = MutableStateFlow(listOf(
            tabWithHistory("fresh", listOf(HistoryEntry("https://guide", "")), idx = 0)
        ))
        whenever(repo.observeForVault("v1")).thenReturn(flow)
        whenever(repo.createTab(eq("v1"), eq("https://guide"), eq(""), eq(false)))
            .thenReturn(Result.success(tab("fresh")))
        vm.bind("v1") { "https://landing" }
        advanceUntilIdle()

        vm.newTab(url = "https://guide", background = false).join()
        advanceUntilIdle()
        vm.onPageFinished("https://guide", "Guide").join()

        verify(repo, never()).recordNavigation(any(), any(), any())
        verify(repo).updateUrl("fresh", "https://guide", "Guide")
    }

    /**
     * A pending restore is not allowed to be overwritten by the loading page's own scroll.
     *
     * Between the switch and its onPageFinished the WebView reports the new document's offsets -
     * 0 first of all - and BrowserScreen's debounced flush would write them onto the row the
     * restore is about to read, so the tab would come back to the top of the page rather than to
     * the place it was left. Found on a device, not in a test: the first fix for the fresh-guide
     * bug reset the screen's scroll flow to 0 on load, and this is where that landed.
     */
    @Test fun `a flush is ignored while the tab is waiting for its restore`() = runTest {
        val flow = MutableStateFlow(listOf(tab("t1").copy(scrollY = 4200)))
        whenever(repo.observeForVault("v1")).thenReturn(flow)
        vm.bind("v1") { "https://landing" }
        advanceUntilIdle()

        vm.switchTo("t1").join()
        vm.flushActiveScrollY(0).join()
        verify(repo, never()).updateScrollY(any(), any())

        // Once the restore has been handed out, the tab's own scrolling is news again.
        assertTrue(vm.consumeScrollRestoreFor("t1"))
        vm.flushActiveScrollY(1234).join()
        verify(repo).updateScrollY("t1", 1234)
    }

    /** And the reload flag is spent by that load, so the tab's next navigation is a real one. */
    @Test fun `the load after a switch pushes history again`() = runTest {
        val flow = MutableStateFlow(listOf(
            tabWithHistory("t1", listOf(HistoryEntry("https://a/", "A")), idx = 0)
        ))
        whenever(repo.observeForVault("v1")).thenReturn(flow)
        vm.bind("v1") { "https://landing" }
        advanceUntilIdle()

        vm.switchTo("t1").join()
        advanceUntilIdle()
        vm.onPageFinished("https://a/", "A").join()
        vm.markLoadingComplete("t1")

        // The user now taps a link on the restored page: a genuine navigation.
        vm.onPageFinished("https://b/", "B").join()

        verify(repo).recordNavigation("t1", "https://b/", "B")
    }

    @Test fun `bind on a new vault replaces the observer and clears prior state`() = runTest {
        val flowA = MutableStateFlow(listOf(tab("a-only").copy(vaultId = "vA")))
        val flowB = MutableStateFlow(listOf(tab("b-only").copy(vaultId = "vB")))
        whenever(repo.observeForVault("vA")).thenReturn(flowA)
        whenever(repo.observeForVault("vB")).thenReturn(flowB)

        vm.bind("vA") { "https://landing-a" }
        advanceUntilIdle()
        assertEquals(listOf("a-only"), vm.tabs.value.map { it.id })

        vm.bind("vB") { "https://landing-b" }
        advanceUntilIdle()
        assertEquals(listOf("b-only"), vm.tabs.value.map { it.id })

        // Mutate the OLD flow: must NOT leak into the new vault's _tabs.
        flowA.value = listOf(tab("a-changed").copy(vaultId = "vA"))
        advanceUntilIdle()
        assertEquals(
            "old-vault flow leaked into new vault's _tabs",
            listOf("b-only"), vm.tabs.value.map { it.id }
        )
    }

    private fun linkBookmark(id: String, url: String = "https://example.com"): com.twinlore.app.domain.SavedTab =
        com.twinlore.app.domain.SavedTab(
            id = id,
            vaultId = "v1",
            title = "Saved",
            url = url,
            scrollPosition = 0,
            zoomLevel = 1f,
            isFavorite = false,
            isMap = false,
            isCurrentContent = false,
            order = 0,
            createdAt = java.util.Date(),
            lastAccessed = java.util.Date(),
            fileType = "link"
        )

    @Test fun `armLinkUndo populates lastDeletedLink`() = runTest {
        whenever(repo.observeForVault("v1")).thenReturn(MutableStateFlow(emptyList()))
        vm.bind("v1") { "https://landing" }
        advanceUntilIdle()

        val link = linkBookmark("L1")
        vm.armLinkUndo(link)
        assertEquals("L1", vm.lastDeletedLink.value?.id)
    }

    @Test fun `armLinkUndo cancels pending lastClosedTab and vice versa`() = runTest {
        whenever(repo.observeForVault("v1")).thenReturn(MutableStateFlow(emptyList()))
        vm.bind("v1") { "https://landing" }
        advanceUntilIdle()

        val tabToClose = tab("T1")
        val linkToDelete = linkBookmark("L1")

        vm.armUndo(tabToClose)
        assertEquals("T1", vm.lastClosedTab.value?.id)
        assertNull(vm.lastDeletedLink.value)

        vm.armLinkUndo(linkToDelete)
        assertNull("lastClosedTab must be cleared when armLinkUndo fires", vm.lastClosedTab.value)
        assertEquals("L1", vm.lastDeletedLink.value?.id)

        vm.armUndo(tabToClose)
        assertNull("lastDeletedLink must be cleared when armUndo fires", vm.lastDeletedLink.value)
    }

    @Test fun `undoLinkDelete restores via savedTabRepository and clears slot`() = runTest {
        whenever(repo.observeForVault("v1")).thenReturn(MutableStateFlow(emptyList()))
        whenever(savedTabRepo.restoreLinkBookmark(any())).thenReturn(com.twinlore.app.domain.Result.success(Unit))
        vm.bind("v1") { "https://landing" }
        advanceUntilIdle()

        val link = linkBookmark("L1")
        vm.armLinkUndo(link)

        vm.undoLinkDelete().join()

        verify(savedTabRepo).restoreLinkBookmark(argThat { id == "L1" })
        assertNull(vm.lastDeletedLink.value)
    }

    @Test fun `dismissLinkUndo clears slot without calling repo`() = runTest {
        whenever(repo.observeForVault("v1")).thenReturn(MutableStateFlow(emptyList()))
        vm.bind("v1") { "https://landing" }
        advanceUntilIdle()

        val link = linkBookmark("L1")
        vm.armLinkUndo(link)
        vm.dismissLinkUndo()

        assertNull(vm.lastDeletedLink.value)
        verify(savedTabRepo, never()).restoreLinkBookmark(any())
    }

    private fun tabWithHistory(
        id: String,
        history: List<HistoryEntry>,
        idx: Int,
        lastAccessed: Long = 1L
    ): BrowserTab = BrowserTab(
        id = id, vaultId = "v1",
        url = history[idx].url, title = history[idx].title,
        faviconBytes = null, scrollY = 0, tabOrder = 0,
        lastAccessed = lastAccessed, createdAt = lastAccessed,
        history = history, historyIndex = idx
    )

    @Test fun `canGoBack reflects active tab's history pointer`() = runTest {
        val h = listOf(HistoryEntry("https://a/", "A"), HistoryEntry("https://b/", "B"))
        val flow = MutableStateFlow(listOf(tabWithHistory("t1", h, idx = 1)))
        whenever(repo.observeForVault("v1")).thenReturn(flow)

        vm.bind("v1") { "https://landing" }
        advanceUntilIdle()

        assertTrue(vm.canGoBack.value)
        assertFalse(vm.canGoForward.value)
    }

    @Test fun `navigateBack delegates to repo with index - 1`() = runTest {
        val h = listOf(HistoryEntry("https://a/", "A"), HistoryEntry("https://b/", "B"))
        val flow = MutableStateFlow(listOf(tabWithHistory("t1", h, idx = 1)))
        whenever(repo.observeForVault("v1")).thenReturn(flow)
        vm.bind("v1") { "https://landing" }
        advanceUntilIdle()

        val returned = vm.navigateBack()
        advanceUntilIdle()

        assertTrue("navigateBack should return true when at idx > 0", returned)
        verify(repo).setHistoryIndex("t1", 0)
    }

    @Test fun `navigateBack returns false at start of stack`() = runTest {
        val h = listOf(HistoryEntry("https://a/", "A"))
        val flow = MutableStateFlow(listOf(tabWithHistory("t1", h, idx = 0)))
        whenever(repo.observeForVault("v1")).thenReturn(flow)
        vm.bind("v1") { "https://landing" }
        advanceUntilIdle()

        val returned = vm.navigateBack()
        advanceUntilIdle()

        assertFalse(returned)
        verify(repo, never()).setHistoryIndex(any(), any())
    }

    @Test fun `navigateForward delegates to repo with index + 1`() = runTest {
        val h = listOf(HistoryEntry("https://a/", "A"), HistoryEntry("https://b/", "B"))
        val flow = MutableStateFlow(listOf(tabWithHistory("t1", h, idx = 0)))
        whenever(repo.observeForVault("v1")).thenReturn(flow)
        vm.bind("v1") { "https://landing" }
        advanceUntilIdle()

        val returned = vm.navigateForward()
        advanceUntilIdle()

        assertTrue(returned)
        verify(repo).setHistoryIndex("t1", 1)
    }

    @Test fun `navigateForward returns false at end of stack`() = runTest {
        val h = listOf(HistoryEntry("https://a/", "A"))
        val flow = MutableStateFlow(listOf(tabWithHistory("t1", h, idx = 0)))
        whenever(repo.observeForVault("v1")).thenReturn(flow)
        vm.bind("v1") { "https://landing" }
        advanceUntilIdle()

        val returned = vm.navigateForward()
        advanceUntilIdle()

        assertFalse(returned)
        verify(repo, never()).setHistoryIndex(any(), any())
    }

    @Test fun `onPageFinished records navigation when no flags are set`() = runTest {
        val flow = MutableStateFlow(listOf(
            tabWithHistory("t1", listOf(HistoryEntry("https://a/", "A")), idx = 0)
        ))
        whenever(repo.observeForVault("v1")).thenReturn(flow)
        vm.bind("v1") { "https://landing" }
        advanceUntilIdle()

        // No switch in flight (loadingForTabId null) AND no history-replay flag → genuine nav.
        vm.onPageFinished("https://b/", "B").join()

        verify(repo).recordNavigation("t1", "https://b/", "B")
        verify(repo, never()).updateUrl(any(), any(), any())
    }

    @Test fun `onPageFinished does NOT push history during switch-induced reload`() = runTest {
        val flow = MutableStateFlow(listOf(
            tabWithHistory("t1", listOf(HistoryEntry("https://a/", "A")), idx = 0)
        ))
        whenever(repo.observeForVault("v1")).thenReturn(flow)
        vm.bind("v1") { "https://landing" }
        advanceUntilIdle()

        // switchTo arms the scroll-restore + loadingForTabId flags.
        vm.switchTo("t1").join()
        advanceUntilIdle()

        vm.onPageFinished("https://a/", "A").join()

        verify(repo, never()).recordNavigation(any(), any(), any())
        verify(repo).updateUrl("t1", "https://a/", "A")
    }

    @Test fun `onPageFinished does NOT push history during back-forward replay`() = runTest {
        val h = listOf(HistoryEntry("https://a/", "A"), HistoryEntry("https://b/", "B"))
        val flow = MutableStateFlow(listOf(tabWithHistory("t1", h, idx = 1)))
        whenever(repo.observeForVault("v1")).thenReturn(flow)
        vm.bind("v1") { "https://landing" }
        advanceUntilIdle()

        vm.navigateBack()
        advanceUntilIdle()

        // The page that loads after navigateBack should NOT push history.
        vm.onPageFinished("https://a/", "A").join()

        verify(repo, never()).recordNavigation(any(), any(), any())
        verify(repo).updateUrl("t1", "https://a/", "A")
    }

    @Test fun `markLoadingComplete also clears navigatingViaHistoryFor`() = runTest {
        val h = listOf(HistoryEntry("https://a/", "A"), HistoryEntry("https://b/", "B"))
        val flow = MutableStateFlow(listOf(tabWithHistory("t1", h, idx = 1)))
        whenever(repo.observeForVault("v1")).thenReturn(flow)
        vm.bind("v1") { "https://landing" }
        advanceUntilIdle()

        vm.navigateBack()
        advanceUntilIdle()
        vm.markLoadingComplete("t1")

        // After markLoadingComplete, a subsequent onPageFinished (e.g. user
        // clicking a link on the back-target page) should record as genuine nav.
        vm.onPageFinished("https://c/", "C").join()

        verify(repo).recordNavigation("t1", "https://c/", "C")
    }

    // === Guard against closing the last tab ===

    @Test fun `closeTab on single-tab list emits toast and does not call repo`() = runTest {
        val seedTabs = MutableStateFlow(listOf(tab("only")))
        whenever(repo.observeForVault("v1")).thenReturn(seedTabs)
        whenever(repo.ensureAtLeastOne(any(), any())).thenReturn(tab("only"))
        vm.bind("v1") { "https://landing" }
        advanceUntilIdle()

        val emitted = mutableListOf<String>()
        val collectJob = launch { vm.toastEvent.collect { emitted += it } }
        advanceUntilIdle()

        vm.closeTab("only").join()
        advanceUntilIdle()

        verify(repo, never()).closeTab(any())
        assertEquals(listOf("At least one tab must stay open"), emitted)
        collectJob.cancel()
    }

    @Test fun `closeTab on multi-tab list still calls repo and arms singular undo`() = runTest {
        val seedTabs = MutableStateFlow(listOf(tab("a"), tab("b")))
        whenever(repo.observeForVault("v1")).thenReturn(seedTabs)
        whenever(repo.ensureAtLeastOne(any(), any())).thenReturn(tab("a"))
        whenever(repo.closeTab("b")).thenReturn(Result.success(Unit))
        vm.bind("v1") { "https://landing" }
        advanceUntilIdle()

        vm.closeTab("b").join()
        // Do NOT advanceUntilIdle here: that would advance past the 5s auto-dismiss timer.

        verify(repo).closeTab("b")
        assertEquals("b", vm.lastClosedTab.value?.id)
    }

    // === closeAllExceptActive ===

    @Test fun `closeAllExceptActive deletes every non-active tab and populates lastClosedTabs`() = runTest {
        // _tabs.first() is the active tab (lastAccessed-DESC order).
        val seedTabs = MutableStateFlow(
            listOf(tab("active"), tab("b"), tab("c"), tab("d"))
        )
        whenever(repo.observeForVault("v1")).thenReturn(seedTabs)
        whenever(repo.ensureAtLeastOne(any(), any())).thenReturn(tab("active"))
        whenever(repo.closeTab(any())).thenReturn(Result.success(Unit))
        vm.bind("v1") { "https://landing" }
        advanceUntilIdle()

        vm.closeAllExceptActive().join()
        // Do NOT advanceUntilIdle here: that would advance past the 5s auto-dismiss timer.

        verify(repo).closeTab("b")
        verify(repo).closeTab("c")
        verify(repo).closeTab("d")
        verify(repo, never()).closeTab("active")
        val closed = vm.lastClosedTabs.value
        assertNotNull(closed)
        assertEquals(setOf("b", "c", "d"), closed!!.map { it.id }.toSet())
    }

    @Test fun `closeAllExceptActive on single-tab list is a no-op`() = runTest {
        val seedTabs = MutableStateFlow(listOf(tab("only")))
        whenever(repo.observeForVault("v1")).thenReturn(seedTabs)
        whenever(repo.ensureAtLeastOne(any(), any())).thenReturn(tab("only"))
        vm.bind("v1") { "https://landing" }
        advanceUntilIdle()

        vm.closeAllExceptActive().join()
        advanceUntilIdle()

        verify(repo, never()).closeTab(any())
        assertNull(vm.lastClosedTabs.value)
    }

    @Test fun `closeAllExceptActive with no tabs is a no-op`() = runTest {
        val seedTabs = MutableStateFlow<List<BrowserTab>>(emptyList())
        whenever(repo.observeForVault("v1")).thenReturn(seedTabs)
        whenever(repo.ensureAtLeastOne(any(), any())).thenReturn(tab("seeded"))
        vm.bind("v1") { "https://landing" }
        advanceUntilIdle()

        vm.closeAllExceptActive().join()
        advanceUntilIdle()

        verify(repo, never()).closeTab(any())
        assertNull(vm.lastClosedTabs.value)
    }

    // === undoBulkClose ===

    @Test fun `undoBulkClose restores every closed tab and clears state`() = runTest {
        val seedTabs = MutableStateFlow(listOf(tab("active"), tab("b"), tab("c")))
        whenever(repo.observeForVault("v1")).thenReturn(seedTabs)
        whenever(repo.ensureAtLeastOne(any(), any())).thenReturn(tab("active"))
        whenever(repo.closeTab(any())).thenReturn(Result.success(Unit))
        whenever(repo.restoreTab(any())).thenReturn(Result.success(Unit))
        vm.bind("v1") { "https://landing" }
        advanceUntilIdle()

        vm.closeAllExceptActive().join()
        // Do NOT advanceUntilIdle here: that would advance past the 5s auto-dismiss timer.
        assertNotNull(vm.lastClosedTabs.value)

        vm.undoBulkClose().join()
        // advanceUntilIdle is safe here: undoBulkClose cancels the timer and clears state.
        advanceUntilIdle()

        verify(repo).restoreTab(check { assertEquals("b", it.id) })
        verify(repo).restoreTab(check { assertEquals("c", it.id) })
        assertNull(vm.lastClosedTabs.value)
    }

    // === mutex between bulk and singular undo ===

    @Test fun `armBulkUndo cancels existing singular undo`() = runTest {
        val seedTabs = MutableStateFlow(listOf(tab("a"), tab("b"), tab("c")))
        whenever(repo.observeForVault("v1")).thenReturn(seedTabs)
        whenever(repo.ensureAtLeastOne(any(), any())).thenReturn(tab("a"))
        whenever(repo.closeTab(any())).thenReturn(Result.success(Unit))
        vm.bind("v1") { "https://landing" }
        advanceUntilIdle()

        // Arm singular undo first.
        vm.closeTab("b").join()
        // Do NOT advanceUntilIdle here: that would advance past the 5s auto-dismiss timer.
        assertNotNull(vm.lastClosedTab.value)

        // Now fire bulk close; the singular state should be cleared.
        // Re-seed so closeAllExceptActive has 2+ tabs to work with.
        seedTabs.value = listOf(tab("a"), tab("c"))
        advanceUntilIdle()
        vm.closeAllExceptActive().join()
        // Do NOT advanceUntilIdle here: that would advance past the 5s bulk auto-dismiss timer.

        assertNull(vm.lastClosedTab.value)
        assertNotNull(vm.lastClosedTabs.value)
    }

    @Test fun `armUndo cancels existing bulk undo`() = runTest {
        val seedTabs = MutableStateFlow(listOf(tab("a"), tab("b"), tab("c")))
        whenever(repo.observeForVault("v1")).thenReturn(seedTabs)
        whenever(repo.ensureAtLeastOne(any(), any())).thenReturn(tab("a"))
        whenever(repo.closeTab(any())).thenReturn(Result.success(Unit))
        vm.bind("v1") { "https://landing" }
        advanceUntilIdle()

        // Arm bulk undo first.
        vm.closeAllExceptActive().join()
        // Do NOT advanceUntilIdle here: that would advance past the 5s auto-dismiss timer.
        assertNotNull(vm.lastClosedTabs.value)

        // Now fire singular close; bulk state should be cleared.
        seedTabs.value = listOf(tab("a"), tab("d"))
        advanceUntilIdle()
        vm.closeTab("d").join()
        // Do NOT advanceUntilIdle here: that would advance past the 5s singular auto-dismiss timer.

        assertNull(vm.lastClosedTabs.value)
        assertNotNull(vm.lastClosedTab.value)
    }

    @Test fun `dismissBulkUndo clears state and cancels job`() = runTest {
        val seedTabs = MutableStateFlow(listOf(tab("a"), tab("b")))
        whenever(repo.observeForVault("v1")).thenReturn(seedTabs)
        whenever(repo.ensureAtLeastOne(any(), any())).thenReturn(tab("a"))
        whenever(repo.closeTab(any())).thenReturn(Result.success(Unit))
        vm.bind("v1") { "https://landing" }
        advanceUntilIdle()

        vm.closeAllExceptActive().join()
        // Do NOT advanceUntilIdle here: that would advance past the 5s auto-dismiss timer.
        assertNotNull(vm.lastClosedTabs.value)

        vm.dismissBulkUndo()
        assertNull(vm.lastClosedTabs.value)
    }
}
