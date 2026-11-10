package com.twinlore.app.data

import com.twinlore.app.data.local.BrowserTabDao
import com.twinlore.app.data.local.BrowserTabEntity
import com.twinlore.app.data.local.BrowserTabEntityMapper
import com.twinlore.app.data.local.BrowserTabHistorySerializer
import com.twinlore.app.domain.BrowserTab
import com.twinlore.app.domain.HistoryEntry
import com.twinlore.app.domain.Result
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

class BrowserTabRepositoryTest {

    private lateinit var dao: BrowserTabDao
    private lateinit var repo: BrowserTabRepository

    @Before fun setup() {
        dao = mock()
        repo = BrowserTabRepository(dao)
    }

    private fun entity(id: String, lastAccessed: Long = 1L) = BrowserTabEntity(
        id = id, vaultId = "v1", url = "u", title = "t",
        faviconBytes = null, scrollY = 0, tabOrder = 0,
        lastAccessed = lastAccessed, createdAt = lastAccessed
    )

    @Test fun `createTab respects per-vault cap`() = runTest {
        whenever(dao.getCountForVault("v1")).thenReturn(BrowserTabRepository.MAX_TABS_PER_VAULT)

        val result = repo.createTab("v1", "https://example.com")

        assertTrue(result is Result.Failure)
        verify(dao, never()).insert(any())
    }

    @Test fun `createTab foreground writes lastAccessed = now and inserts`() = runTest {
        whenever(dao.getCountForVault("v1")).thenReturn(3)
        whenever(dao.getMaxOrder("v1")).thenReturn(2)

        val before = System.currentTimeMillis()
        val result = repo.createTab("v1", "https://example.com", title = "Example")
        val after = System.currentTimeMillis()

        assertTrue(result is Result.Success<*>)
        val tab = (result as Result.Success<*>).data as com.twinlore.app.domain.BrowserTab
        assertTrue(tab.lastAccessed in before..after)
        assertEquals(3, tab.tabOrder)
        verify(dao).insert(any())
    }

    @Test fun `createTab background clamps lastAccessed below current head`() = runTest {
        whenever(dao.getCountForVault("v1")).thenReturn(1)
        whenever(dao.getMaxOrder("v1")).thenReturn(0)
        val headTs = 10_000_000_000_000L  // far future, larger than now()
        whenever(dao.getActive("v1")).thenReturn(entity("h", lastAccessed = headTs))

        val result = repo.createTab("v1", "https://other.com", background = true)

        assertTrue(result is Result.Success<*>)
        val tab = (result as Result.Success<*>).data as com.twinlore.app.domain.BrowserTab
        assertTrue("background ts ${tab.lastAccessed} must be < head $headTs", tab.lastAccessed < headTs)
    }

    @Test fun `ensureAtLeastOne is no-op when tabs exist`() = runTest {
        whenever(dao.getForVaultSync("v1")).thenReturn(listOf(entity("x")))

        val result = repo.ensureAtLeastOne("v1", "https://landing")

        assertEquals("x", result.id)
        verify(dao, never()).insert(any())
    }

    @Test fun `ensureAtLeastOne creates a tab when vault is empty`() = runTest {
        whenever(dao.getForVaultSync("v1")).thenReturn(emptyList())
        whenever(dao.getCountForVault("v1")).thenReturn(0)
        whenever(dao.getMaxOrder("v1")).thenReturn(null)

        val result = repo.ensureAtLeastOne("v1", "https://landing")

        assertEquals("https://landing", result.url)
        verify(dao).insert(any())
    }

    @Test fun `observeForVault delegates to dao`() = runTest {
        whenever(dao.observeForVault("v1")).thenReturn(flowOf(listOf(entity("x"))))
        val flow = repo.observeForVault("v1")
        assertNotNull(flow)
    }

    @Test fun `createTab seeds initial history with the requested url`() = runTest {
        whenever(dao.getCountForVault("v1")).thenReturn(0)
        whenever(dao.getMaxOrder("v1")).thenReturn(null)
        val captor = argumentCaptor<BrowserTabEntity>()

        val result = repo.createTab("v1", "https://landing/", title = "")

        assertTrue(result is Result.Success<*>)
        verify(dao).insert(captor.capture())
        val inserted = captor.firstValue
        assertEquals(0, inserted.historyIndex)
        // history_json must contain one entry pointing at the requested url.
        assertTrue(
            "history_json missing url: ${inserted.historyJson}",
            inserted.historyJson.contains("\"url\":\"https://landing/\"")
        )
    }

    private fun tabWithHistory(
        id: String,
        history: List<HistoryEntry>,
        idx: Int
    ): BrowserTab = BrowserTab(
        id = id, vaultId = "v1",
        url = history[idx].url, title = history[idx].title,
        faviconBytes = null, scrollY = 0, tabOrder = 0,
        lastAccessed = 1L, createdAt = 1L,
        history = history, historyIndex = idx
    )

    private fun entityFromTab(tab: BrowserTab): BrowserTabEntity =
        BrowserTabEntityMapper.toEntity(tab)

    @Test fun `recordNavigation pushes new entry and advances index`() = runTest {
        val base = tabWithHistory("t1", listOf(HistoryEntry("https://a/", "A")), idx = 0)
        whenever(dao.getById("t1")).thenReturn(entityFromTab(base))

        repo.recordNavigation("t1", "https://b/", "B")

        verify(dao).updateHistory(
            id = eq("t1"),
            json = argThat { contains("\"url\":\"https://a/\"") && contains("\"url\":\"https://b/\"") },
            idx = eq(1),
            url = eq("https://b/"),
            title = eq("B")
        )
    }

    @Test fun `recordNavigation dedupes consecutive duplicate URL`() = runTest {
        val base = tabWithHistory("t1", listOf(HistoryEntry("https://a/", "A")), idx = 0)
        whenever(dao.getById("t1")).thenReturn(entityFromTab(base))

        repo.recordNavigation("t1", "https://a/", "A again")

        verify(dao, never()).updateHistory(any(), any(), any(), any(), any())
    }

    @Test fun `recordNavigation truncates forward stack before appending`() = runTest {
        val base = tabWithHistory(
            "t1",
            history = listOf(
                HistoryEntry("https://a/", "A"),
                HistoryEntry("https://b/", "B"),
                HistoryEntry("https://c/", "C")
            ),
            idx = 0  // pointing at A; B and C are "forward"
        )
        whenever(dao.getById("t1")).thenReturn(entityFromTab(base))

        repo.recordNavigation("t1", "https://d/", "D")

        verify(dao).updateHistory(
            id = eq("t1"),
            // Forward (B, C) must be gone; only A and D remain.
            json = argThat {
                contains("\"url\":\"https://a/\"") &&
                contains("\"url\":\"https://d/\"") &&
                !contains("\"url\":\"https://b/\"") &&
                !contains("\"url\":\"https://c/\"")
            },
            idx = eq(1),
            url = eq("https://d/"),
            title = eq("D")
        )
    }

    @Test fun `recordNavigation enforces 50-entry cap`() = runTest {
        val fifty = (0 until 50).map { HistoryEntry("https://p$it/", "P$it") }
        val base = tabWithHistory("t1", fifty, idx = 49)
        whenever(dao.getById("t1")).thenReturn(entityFromTab(base))

        repo.recordNavigation("t1", "https://p50/", "P50")

        verify(dao).updateHistory(
            id = eq("t1"),
            // Oldest (p0) dropped; newest at end; size still 50.
            json = argThat {
                !contains("\"url\":\"https://p0/\"") &&
                contains("\"url\":\"https://p50/\"")
            },
            idx = eq(49),
            url = eq("https://p50/"),
            title = eq("P50")
        )
    }

    @Test fun `setHistoryIndex writes new index and denormalized url+title`() = runTest {
        val base = tabWithHistory(
            "t1",
            history = listOf(
                HistoryEntry("https://a/", "A"),
                HistoryEntry("https://b/", "B")
            ),
            idx = 1
        )
        whenever(dao.getById("t1")).thenReturn(entityFromTab(base))

        repo.setHistoryIndex("t1", newIndex = 0)

        verify(dao).updateHistory(
            id = eq("t1"),
            json = any(),       // unchanged but we don't pin exact JSON
            idx = eq(0),
            url = eq("https://a/"),
            title = eq("A")
        )
    }

    @Test fun `setHistoryIndex with out-of-range index does nothing`() = runTest {
        val base = tabWithHistory("t1", listOf(HistoryEntry("https://a/", "A")), idx = 0)
        whenever(dao.getById("t1")).thenReturn(entityFromTab(base))

        repo.setHistoryIndex("t1", newIndex = 5)

        verify(dao, never()).updateHistory(any(), any(), any(), any(), any())
    }

    @Test fun `updateUrl rewrites the current history entry's title`() = runTest {
        // updateUrl is called by onPageFinished/onTitleReceived to update the
        // title at the current entry. URL is unchanged (by contract). Verify
        // both the denormalized cache AND the history JSON title get updated
        // so back/forward replay doesn't reset to a stale title.
        val base = tabWithHistory(
            "t1",
            history = listOf(
                HistoryEntry("https://a/", "old"),
                HistoryEntry("https://b/", "B")
            ),
            idx = 0
        )
        whenever(dao.getById("t1")).thenReturn(entityFromTab(base))

        repo.updateUrl("t1", "https://a/", "fresh")

        verify(dao).updateHistory(
            id = eq("t1"),
            json = argThat {
                // The current entry's title changed; the other entry's title preserved.
                contains("\"title\":\"fresh\"") &&
                contains("\"title\":\"B\"") &&
                !contains("\"title\":\"old\"")
            },
            idx = eq(0),
            url = eq("https://a/"),
            title = eq("fresh")
        )
    }
}
