package com.twinlore.app.data.scraping

import com.twinlore.app.data.DefaultAssetType
import com.twinlore.app.data.SettingsRepository
import com.twinlore.app.data.VaultRepository
import com.twinlore.app.data.library.BrowserSaveCoordinator
import com.twinlore.app.domain.Vault
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
class VaultScrapeCoordinatorFallbackTest {

    private lateinit var vaultRepository: VaultRepository
    private lateinit var browserSaveCoordinator: BrowserSaveCoordinator
    private lateinit var gameNameSanitizer: GameNameSanitizer
    private lateinit var settings: SettingsRepository
    private lateinit var sgdb: ArtworkScraper
    private lateinit var tgdb: ArtworkScraper
    private lateinit var coordinator: VaultScrapeCoordinator

    private val vaultId = "v1"
    private val testVault = Vault(
        id = vaultId,
        name = "Test Game",
        romPath = null,
        romFilename = null,
        // artworkResourceId left null so needsBoxArt = true
        systemeid = null,
        gameId = null,
        source = null,
        lastOpened = null,
        createdAt = Date(),
    )

    @Before
    fun setUp() = runTest {
        vaultRepository = mock()
        browserSaveCoordinator = mock()
        gameNameSanitizer = mock()
        settings = mock()
        sgdb = mock()
        tgdb = mock()

        whenever(vaultRepository.getVaultById(eq(vaultId))).thenReturn(testVault)
        whenever(gameNameSanitizer.sanitize(any())).thenAnswer { it.arguments[0] as String }
        whenever(settings.defaultAssetType).thenReturn(flowOf(DefaultAssetType.GRID))
        whenever(sgdb.providerName).thenReturn("steamgriddb")
        whenever(tgdb.providerName).thenReturn("thegamesdb")

        coordinator = VaultScrapeCoordinator(
            vaultRepository = vaultRepository,
            browserSaveCoordinator = browserSaveCoordinator,
            gameNameSanitizer = gameNameSanitizer,
            settingsRepository = settings,
            scrapers = listOf(sgdb, tgdb),
        )
    }

    @Test
    fun `sgdb null falls back to tgdb`() = runTest {
        whenever(sgdb.scrape(any(), any())).thenReturn(null)
        // tgdb returns null too so the pipeline emits Error(NotFound) and we don't need
        // to mock the download path
        whenever(tgdb.scrape(any(), any())).thenReturn(null)

        val events = coordinator.scrapeVault(vaultId).toList()

        verify(sgdb, times(1)).scrape(any(), any())
        verify(tgdb, times(1)).scrape(any(), any())
        assertTrue(events.any {
            it is VaultScrapeCoordinator.ScrapingProgress.Error &&
                it.error is ScrapeError.NotFound
        })
    }

    @Test
    fun `sgdb transient error falls back to tgdb`() = runTest {
        whenever(sgdb.scrape(any(), any())).thenThrow(ScrapeError.Unavailable)
        whenever(tgdb.scrape(any(), any())).thenReturn(null)

        coordinator.scrapeVault(vaultId).toList()

        verify(sgdb, times(1)).scrape(any(), any())
        verify(tgdb, times(1)).scrape(any(), any())
    }

    @Test
    fun `both providers empty emits NotFound`() = runTest {
        whenever(sgdb.scrape(any(), any())).thenReturn(null)
        whenever(tgdb.scrape(any(), any())).thenReturn(null)

        val events = coordinator.scrapeVault(vaultId).toList()

        val error = events.filterIsInstance<VaultScrapeCoordinator.ScrapingProgress.Error>().first()
        assertTrue(error.error is ScrapeError.NotFound)
    }

    @Test
    fun `both providers unavailable emits Unavailable`() = runTest {
        whenever(sgdb.scrape(any(), any())).thenThrow(ScrapeError.Unavailable)
        whenever(tgdb.scrape(any(), any())).thenThrow(ScrapeError.Unavailable)

        val events = coordinator.scrapeVault(vaultId).toList()

        val error = events.filterIsInstance<VaultScrapeCoordinator.ScrapingProgress.Error>().first()
        assertTrue(error.error is ScrapeError.Unavailable)
    }

    @Test
    fun `debounce short-circuits repeat scrape`() = runTest {
        whenever(sgdb.scrape(any(), any())).thenReturn(null)
        whenever(tgdb.scrape(any(), any())).thenReturn(null)

        coordinator.scrapeVault(vaultId).toList()
        coordinator.scrapeVault(vaultId).toList()

        // Each scraper should only be called once despite two scrape invocations
        verify(sgdb, times(1)).scrape(any(), any())
        verify(tgdb, times(1)).scrape(any(), any())
    }

    @Test
    fun `forceFresh bypasses debounce`() = runTest {
        whenever(sgdb.scrape(any(), any())).thenReturn(null)
        whenever(tgdb.scrape(any(), any())).thenReturn(null)

        coordinator.scrapeVault(vaultId).toList()
        coordinator.scrapeVault(vaultId, forceFresh = true).toList()

        verify(sgdb, times(2)).scrape(any(), any())
        verify(tgdb, times(2)).scrape(any(), any())
    }
}
