package com.twinlore.app.data.scraping

import com.twinlore.app.data.DefaultAssetType
import com.twinlore.app.data.SettingsRepository
import com.twinlore.app.data.VaultRepository
import com.twinlore.app.data.library.BrowserSaveCoordinator
import com.twinlore.app.domain.Vault
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.Date

/**
 * A scraper that hangs (suspends) for [delayMs] before returning — models a
 * provider that accepts the connection then stalls, the pathological case the
 * per-call OkHttp timeouts don't fully bound. runTest's virtual clock makes the
 * delay deterministic and instant.
 */
private class HangingScraper(
    override val providerName: String,
    private val delayMs: Long,
) : ArtworkScraper {
    override suspend fun scrape(name: String, types: Set<ArtworkType>): ScrapedArtwork? {
        delay(delayMs)
        return null
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class VaultScrapeCoordinatorTimeoutTest {

    private val vaultId = "v1"
    private val testVault = Vault(
        id = vaultId,
        name = "Test Game",
        romPath = null,
        romFilename = null,
        systemeid = null,
        gameId = null,
        source = null,
        lastOpened = null,
        createdAt = Date(),
    )

    private suspend fun coordinatorWith(vararg scrapers: ArtworkScraper): VaultScrapeCoordinator {
        val vaultRepository: VaultRepository = mock()
        val browserSaveCoordinator: BrowserSaveCoordinator = mock()
        val gameNameSanitizer: GameNameSanitizer = mock()
        val settings: SettingsRepository = mock()
        whenever(vaultRepository.getVaultById(eq(vaultId))).thenReturn(testVault)
        whenever(gameNameSanitizer.sanitize(any())).thenAnswer { it.arguments[0] as String }
        whenever(settings.defaultAssetType).thenReturn(flowOf(DefaultAssetType.GRID))
        return VaultScrapeCoordinator(
            vaultRepository = vaultRepository,
            browserSaveCoordinator = browserSaveCoordinator,
            gameNameSanitizer = gameNameSanitizer,
            settingsRepository = settings,
            scrapers = scrapers.toList(),
        )
    }

    @Test
    fun `scrapeVault that outlasts the timeout emits Unavailable`() = runTest {
        val coordinator = coordinatorWith(
            HangingScraper("slow", VaultScrapeCoordinator.SCRAPE_TIMEOUT_MS + 5_000)
        )

        val events = coordinator.scrapeVault(vaultId).toList()

        val error = events.filterIsInstance<VaultScrapeCoordinator.ScrapingProgress.Error>().first()
        assertTrue(error.error is ScrapeError.Unavailable)
    }

    @Test
    fun `collectArtworkOptions that outlasts the timeout throws Unavailable`() = runTest {
        val coordinator = coordinatorWith(
            HangingScraper("slow", VaultScrapeCoordinator.SCRAPE_TIMEOUT_MS + 5_000)
        )

        val thrown = try {
            coordinator.collectArtworkOptions("Test Game")
            null
        } catch (e: ScrapeError) {
            e
        }

        assertTrue(thrown is ScrapeError.Unavailable)
    }
}
