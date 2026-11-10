package com.twinlore.app.data

import android.content.Context
import com.twinlore.app.data.library.BindReport
import com.twinlore.app.data.library.LocalMediaBinder
import com.twinlore.app.data.license.LicenseManager
import com.twinlore.app.data.retroachievements.RaProfileRepository
import com.twinlore.app.data.retroachievements.RetroAchievementsApiClient
import com.twinlore.app.data.retroachievements.RetroAchievementsCredentialsRepository
import com.twinlore.app.data.scraping.GameNameSanitizer
import com.twinlore.app.data.scraping.VaultScrapeCoordinator
import com.twinlore.app.data.steam.SteamApiClient
import com.twinlore.app.data.steam.SteamCredentialsRepository
import com.twinlore.app.domain.Result
import com.twinlore.app.domain.Vault
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import java.util.Date

/**
 * Pins the now-playing record write to a SETTLED vault id. The fuzzy-match path
 * returns a CANDIDATE vault id while [SyncLatestPlayedGameUseCase.pendingFuzzyMatch]
 * is set ("focus while the user decides"); execute() must not persist that as the
 * now-playing record, because the user may decline it in favor of a different
 * vault. The record is written instead once the user resolves the pending match,
 * from confirmFuzzyMatch()/declineFuzzyMatch(), with whichever vault id they settle
 * on.
 *
 * Robolectric only for [android.net.Uri] parsing inside execute(); same reason as
 * [RetroArchHistoryParserTest].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SyncLatestPlayedGameNowPlayingTest {

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var retroArchHistoryParser: RetroArchHistoryParser
    private lateinit var vaultRepository: VaultRepository
    private lateinit var gameNameSanitizer: GameNameSanitizer
    private lateinit var fuzzyNameMatcher: FuzzyNameMatcher
    private lateinit var vaultScrapeCoordinator: VaultScrapeCoordinator
    private lateinit var localMediaBinder: LocalMediaBinder
    private lateinit var useCase: SyncLatestPlayedGameUseCase

    private val existingVault = Vault(
        id = "vault-existing", name = "Old Game", romPath = null, romFilename = null,
        systemeid = null, gameId = null, source = null,
        lastOpened = null, createdAt = Date()
    )

    @Before
    fun setup() {
        settingsRepository = mock()
        retroArchHistoryParser = mock()
        vaultRepository = mock()
        gameNameSanitizer = mock()
        fuzzyNameMatcher = mock()
        vaultScrapeCoordinator = mock()
        localMediaBinder = mock()

        useCase = SyncLatestPlayedGameUseCase(
            context = mock<Context>(),
            settingsRepository = settingsRepository,
            retroArchHistoryParser = retroArchHistoryParser,
            esdeLogParser = mock(),
            cocoonLogParser = mock(),
            vaultRepository = vaultRepository,
            gameNameSanitizer = gameNameSanitizer,
            licenseManager = mock<LicenseManager>(),
            vaultScrapeCoordinator = vaultScrapeCoordinator,
            localMediaBinder = localMediaBinder,
            raCredentialsRepository = mock<RetroAchievementsCredentialsRepository>(),
            raApiClient = mock<RetroAchievementsApiClient>(),
            raProfileRepository = mock<RaProfileRepository>(),
            steamCredentialsRepository = mock<SteamCredentialsRepository>(),
            steamApiClient = mock<SteamApiClient>(),
            fuzzyNameMatcher = fuzzyNameMatcher,
        )

        whenever(settingsRepository.lastPlayedSource).thenReturn(flowOf(LastPlayedSource.RETROARCH))
        whenever(settingsRepository.createVaultIfNotFound).thenReturn(flowOf(true))
        whenever(settingsRepository.retroArchHistoryFolder).thenReturn(flowOf("content://retroarch"))

        // sanitize/normalize as identity so the "same displayName" check below is
        // predictable: "New Game" (candidate) vs "Old Game" (existingVault) never match,
        // so resolution always falls through to the fuzzy matcher.
        whenever(gameNameSanitizer.sanitize(any())).thenReturn("New Game")
        whenever(gameNameSanitizer.normalize(any())).thenAnswer { it.getArgument<String>(0) }

        kotlinx.coroutines.runBlocking {
            whenever(retroArchHistoryParser.findLastPlayedGame(any())).thenReturn(
                RetroArchHistoryParser.LaunchEntry(
                    gameName = "New Game",
                    romFilename = "game.zip",
                    romPath = "/roms/game.zip",
                    coreName = null,
                    packageName = null,
                    activityClass = null,
                    launchIntent = null,
                )
            )
            // No exact identity match; the normalized-name pass over existing vaults finds
            // nothing either ("New Game" != "Old Game"), so execution reaches the fuzzy
            // matcher.
            whenever(vaultRepository.resolveVaultIdentity(any(), any(), any())).thenReturn(null)
            whenever(vaultRepository.getAllVaults()).thenReturn(listOf(existingVault))
        }
        whenever(fuzzyNameMatcher.findBestMatch(any(), any(), any())).thenReturn(
            FuzzyNameMatcher.MatchResult(vault = existingVault, similarity = 0.85)
        )
    }

    @Test
    fun `a pending fuzzy match is not persisted as the now-playing record`() = runTest {
        val resolved = useCase.execute()

        assertEquals(existingVault.id, resolved)
        assertEquals(existingVault.id, useCase.pendingFuzzyMatch.value?.existingVault?.id)
        verify(settingsRepository, never()).setNowPlayingVaultId(anyOrNull())
    }

    @Test
    fun `confirming the fuzzy match persists the existing vault id`() = runTest {
        useCase.execute()
        whenever(vaultRepository.renameVault(any(), any())).thenReturn(Result.success(Unit))

        useCase.confirmFuzzyMatch()

        verify(settingsRepository).setNowPlayingVaultId(existingVault.id)
    }

    @Test
    fun `declining the fuzzy match persists the newly created vault id`() = runTest {
        useCase.execute()
        val newVault = existingVault.copy(id = "vault-new", name = "New Game")
        whenever(vaultRepository.createVault(any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), any(), anyOrNull(), anyOrNull(), anyOrNull()))
            .thenReturn(Result.success(newVault))
        whenever(vaultScrapeCoordinator.scrapeVault(any(), any())).thenReturn(emptyFlow())
        whenever(localMediaBinder.bindForVaultWithRescanIfEmpty(any())).thenReturn(BindReport(vaultId = newVault.id))

        val result = useCase.declineFuzzyMatch()

        assertEquals(newVault.id, result)
        verify(settingsRepository).setNowPlayingVaultId(newVault.id)
    }
}
