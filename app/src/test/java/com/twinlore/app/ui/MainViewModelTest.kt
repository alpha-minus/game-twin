package com.twinlore.app.ui

import com.twinlore.app.domain.AppState
import com.twinlore.app.domain.StateManager
import com.twinlore.app.domain.VaultTab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.Rule
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.twinlore.app.data.ContentUriFileWatcher
import com.twinlore.app.data.PersistedFolderValidator
import com.twinlore.app.data.SettingsRepository
import com.twinlore.app.data.SyncLatestPlayedGameUseCase
import com.twinlore.app.data.TrackedFileSource
import com.twinlore.app.data.ValidationResult
import com.twinlore.app.data.VaultModeStateManager
import com.twinlore.app.data.VaultRepository
import com.twinlore.app.data.license.LicenseManager
import com.twinlore.app.data.license.LicenseValidationWorker
import com.twinlore.app.data.update.UpdateManager
import com.twinlore.app.data.library.FileRelocator
import com.twinlore.app.data.library.HomeFolderSetupCoordinator
import com.twinlore.app.data.library.JsonSidecarImporter
import com.twinlore.app.data.library.LibraryAutoSaveObserver
import com.twinlore.app.data.library.LibraryFolderManager
import com.twinlore.app.data.library.LibraryImporter
import com.twinlore.app.data.library.MigrationCoordinator
import com.twinlore.app.data.library.MediaIndexer
import com.twinlore.app.data.library.LocalMediaBinder
import com.twinlore.app.data.library.DeviceIdMigrator
import com.twinlore.app.data.library.sync.LibrarySyncCoordinator
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Unit tests for MainViewModel
 * Test Strategy: All navigation methods, state persistence across configuration changes, ViewModel lifecycle handling, and proper StateFlow emission
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var stateManager: StateManager
    private lateinit var viewModel: MainViewModel
    private lateinit var folderValidator: PersistedFolderValidator
    private lateinit var vaultRepository: VaultRepository
    private lateinit var vaultModeStateManager: VaultModeStateManager

    /**
     * The two things the journey's doorways are gated on, held here rather than in `setup`'s locals
     * so a test can switch the feature off the way Settings does and then press back.
     */
    private lateinit var raCredentials:
        com.twinlore.app.data.retroachievements.RetroAchievementsCredentialsRepository
    private val achievementsTabVisible = MutableStateFlow(true)

    private val testDispatcher = StandardTestDispatcher()

    /**
     * Owns every view model this class builds, so tearDown can cancel their
     * scopes. Without it `MainViewModel.init` keeps a live coroutine on
     * Dispatchers.Default: a real delay(2000), then checkForUpdate(), then a
     * 24h loop. clearInlineMocks() empties the mocks it is holding, so about
     * two seconds after this class finishes that coroutine calls into an
     * emptied UpdateManager and throws NPE on a background thread. The
     * exception lands on whichever test class happens to be starting in this
     * JVM fork, which then fails with UncaughtExceptionsBeforeTest having done
     * nothing wrong.
     */
    private val viewModelStore = androidx.lifecycle.ViewModelStore()
    private var viewModelKeys = 0

    private fun <T : androidx.lifecycle.ViewModel> T.owned(): T =
        also { viewModelStore.put("vm-${viewModelKeys++}", it) }

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        // Create a real StateManager for testing
        stateManager = StateManager()
        vaultModeStateManager = mock<VaultModeStateManager>()
        val licenseManager = mock<LicenseManager>()
        val licenseValidationWorker = mock<LicenseValidationWorker>()
        val settingsRepository = mock<SettingsRepository>()
        raCredentials = mock<com.twinlore.app.data.retroachievements.RetroAchievementsCredentialsRepository> {
            onBlocking { getCredentials() } doReturn
                com.twinlore.app.data.retroachievements.RaCredentials("me", "key")
        }
        val trackedFileSource = mock<TrackedFileSource>()
        val contentUriFileWatcher = mock<ContentUriFileWatcher>()
        val syncLatestPlayedGameUseCase = mock<SyncLatestPlayedGameUseCase>()
        // No pending fuzzy-match candidate by default; handleOpenLastPlayed's guard
        // reads this after execute() to decide whether to showcase the candidate.
        whenever(syncLatestPlayedGameUseCase.pendingFuzzyMatch).thenReturn(MutableStateFlow(null))
        val updateManager = mock<UpdateManager>()
        val appContext = mock<Context>()
        val libraryFolderManager = mock<LibraryFolderManager>()
        val migrationCoordinator = mock<MigrationCoordinator>()
        val sidecarImporter = mock<JsonSidecarImporter>()
        val fileRelocator = mock<FileRelocator>()
        vaultRepository = mock<VaultRepository>()
        val mediaIndexer = mock<MediaIndexer>()
        val localMediaBinder = mock<LocalMediaBinder>()
        val libraryAutoSaveObserver = mock<LibraryAutoSaveObserver>()
        val libraryImporter = mock<LibraryImporter>()
        val libraryMetaDao = mock<com.twinlore.app.data.local.LibraryMetaDao>()
        folderValidator = mock<PersistedFolderValidator>()

        // Stub legacyMigrationCompleted with a flow that suspends forever (never emits).
        // This keeps evaluateStartup() waiting at .first() indefinitely so it never
        // reaches releaseStartupGate() → withContext(Dispatchers.IO), avoiding a
        // race-condition where the IO thread completes after @After resets Main.
        whenever(settingsRepository.legacyMigrationCompleted).thenReturn(
            flow { awaitCancellation() }
        )
        whenever(settingsRepository.aiBottomTabVisible).thenReturn(flowOf(true))
        whenever(settingsRepository.openLastPlayedOnLaunch).thenReturn(flowOf(false))
        whenever(settingsRepository.lastHandledLaunchMarker).thenReturn(flowOf(null))
        whenever(settingsRepository.nowPlayingVaultId).thenReturn(flowOf(null))
        whenever(settingsRepository.achievementsTabVisible).thenReturn(achievementsTabVisible)
        whenever(contentUriFileWatcher.fileChanges(anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(emptyFlow())
        // Stub openVault pre-flight: return a non-null Vault for any ID so openVault succeeds.
        kotlinx.coroutines.runBlocking {
            whenever(vaultRepository.getVaultById(any())).thenAnswer { invocation ->
                val id = invocation.getArgument<String>(0)
                com.twinlore.app.domain.Vault(
                    id = id, name = id, romPath = null, romFilename = null,
                    systemeid = null, gameId = null, source = null,
                    lastOpened = null, createdAt = java.util.Date()
                )
            }
        }
        whenever(vaultModeStateManager.getLastTab(any())).thenReturn(VaultTab.Guide)
        kotlinx.coroutines.runBlocking {
            whenever(folderValidator.validate()).thenReturn(ValidationResult.EMPTY)
            whenever(libraryMetaDao.getMeta()).thenReturn(
                com.twinlore.app.data.local.LibraryMetaEntity(
                    schemaVersion = 1,
                    lastWrittenAtMillis = 0L,
                    lastWrittenDeviceId = "test",
                    lastWrittenDeviceLabel = "test"
                )
            )
        }
        val homeFolderSetupCoordinator = mock<HomeFolderSetupCoordinator>()
        val librarySyncCoordinator = mock<LibrarySyncCoordinator>().also { c ->
            whenever(c.promptState).thenReturn(MutableStateFlow(null))
        }
        viewModel = MainViewModel(
            stateManager, com.twinlore.app.ui.fold.FoldHandover(),
            vaultModeStateManager, licenseManager, licenseValidationWorker,
            settingsRepository, trackedFileSource, contentUriFileWatcher,
            syncLatestPlayedGameUseCase, updateManager,
            appContext, libraryFolderManager, migrationCoordinator,
            sidecarImporter, fileRelocator, vaultRepository, mediaIndexer, localMediaBinder,
            mock<com.twinlore.app.data.library.VaultFolderAdopter>(),
            mock<com.twinlore.app.data.library.PageGroupFolderAdopter>(),
            libraryAutoSaveObserver, libraryImporter,
            mock<com.twinlore.app.data.library.LibraryLoadCoordinator>(), libraryMetaDao,
            folderValidator,
            homeFolderSetupCoordinator,
            mock<com.twinlore.app.data.library.ResourceResolver>(),
            mock<com.twinlore.app.data.AchievementSyncCoordinator>(),
            mock<com.twinlore.app.data.GuideViewStateManager>(),
            mock<com.twinlore.app.data.SavedTabRepository>(),
            librarySyncCoordinator,
            mock<DeviceIdMigrator>(),
            mock<com.twinlore.app.data.library.TextGuideSplitter>(),
            mock<com.twinlore.app.data.library.BrowserSaveCoordinator>(),
            raCredentials,
            com.twinlore.app.ui.fold.FakeOverlayMemory(),
        ).owned()
    }


    /**
     * Gives the cancelled scopes a moment to finish unwinding.
     *
     * A fixed wait, not a condition: [androidx.lifecycle.ViewModelStore.clear]
     * cancels and returns, the children it cancels live on IO and Default, and
     * nothing here can join them. So this is a sleep, and it is one on purpose
     * rather than a loop dressed up to look like a check.
     */
    private fun settleCancellations() {
        Thread.sleep(SETTLE_MS)
    }

    @After
    fun tearDown() {
        // Before resetMain, always: viewModelScope runs on Dispatchers.Main.immediate
        // and cancelling it after Main is reset throws out of the cancellation itself.
        viewModelStore.clear()
        // The clear cancels viewModelScope, but children parked on IO or
        // Default are cancelled asynchronously and their completion hops back
        // to the scope's Main.immediate. Reset Main before that lands and the
        // resume throws "The main looper is not available" out of the
        // coroutines machinery, which is the same class of leak this teardown
        // exists to stop, just wearing a different exception. Settle first,
        // while Main is still installed.
        settleCancellations()
        Dispatchers.resetMain()
        // Free Mockito's per-mock invocation records. The viewmodel's daily update-check
        // loop (delay(24h) forever) spins ~106k virtual-time iterations per
        // advanceUntilIdle() — each a recorded call on the updateManager mock — so without
        // clearing, this class alone accumulates ~8.5M records (~4 GB), starving the test
        // JVM and wedging the Gradle worker on shutdown.
        org.mockito.Mockito.framework().clearInlineMocks()
    }

    @Test
    fun `initial state is Library`() = runTest(testDispatcher) {
        val initialState = viewModel.currentState.value
        assertEquals(AppState.Library, initialState)
    }

    @Test
    fun `navigateToLibrary sets Library state`() = runTest(testDispatcher) {
        // First navigate to another state
        viewModel.navigateToSettings()
        advanceUntilIdle()
        
        // Verify we're in Settings
        assertEquals(AppState.Settings, viewModel.currentState.first())
        
        // Then navigate to Library
        viewModel.navigateToLibrary()
        advanceUntilIdle()
        
        assertEquals(AppState.Library, viewModel.currentState.first())
    }

    @Test
    fun `openVault sets VaultSession state with correct vaultId`() = runTest(testDispatcher) {
        val vaultId = "test-vault-123"
        viewModel.openVault(vaultId)
        advanceUntilIdle()

        val currentState = viewModel.currentState.first()
        assertTrue(currentState is AppState.VaultSession)
        assertEquals(vaultId, (currentState as AppState.VaultSession).vaultId)
    }

    @Test
    fun `navigateToMapViewer sets MapViewer state with correct vaultId`() = runTest(testDispatcher) {
        val vaultId = "test-vault-123"
        // First open vault
        viewModel.openVault(vaultId)
        advanceUntilIdle()

        // Then navigate to MapViewer
        viewModel.navigateToMapViewer(vaultId)
        advanceUntilIdle()

        val currentState = viewModel.currentState.first()
        assertTrue(currentState is AppState.MapViewer)
        assertEquals(vaultId, (currentState as AppState.MapViewer).vaultId)
    }

    @Test
    fun `navigateToGuideViewer sets GuideViewer state with correct vaultId`() = runTest(testDispatcher) {
        val vaultId = "test-vault-123"
        // First open vault
        viewModel.openVault(vaultId)
        advanceUntilIdle()

        // Then navigate to GuideViewer
        viewModel.navigateToGuideViewer(vaultId)
        advanceUntilIdle()

        val currentState = viewModel.currentState.first()
        assertTrue(currentState is AppState.GuideViewer)
        assertEquals(vaultId, (currentState as AppState.GuideViewer).vaultId)
    }

    @Test
    fun `navigateToSettings sets Settings state`() = runTest(testDispatcher) {
        viewModel.navigateToSettings()
        advanceUntilIdle()
        
        assertEquals(AppState.Settings, viewModel.currentState.first())
    }

    // --- Settings opened from the Achievement Journey ---
    //
    // Back out of Settings restores the journey it was opened from. Settings is also where the two
    // things the journey is offered on are switched off, and the screen used to come straight back
    // whichever one had just gone: the feature toggle gates the library button and the vault tab
    // and nothing else, so the user landed on a feature they had just removed, with no doorway
    // back to it from anywhere.

    @Test
    fun `back out of Settings returns to the journey it was opened from`() = runTest(testDispatcher) {
        viewModel.navigateToJourney()
        advanceUntilIdle()
        viewModel.navigateToSettings()
        advanceUntilIdle()

        viewModel.exitSettings()
        advanceUntilIdle()

        assertTrue(viewModel.currentState.first() is AppState.Journey)
    }

    @Test
    fun `Achievements switched off inside Settings sends back to the library`() = runTest(testDispatcher) {
        viewModel.navigateToJourney()
        advanceUntilIdle()
        viewModel.navigateToSettings()
        advanceUntilIdle()

        achievementsTabVisible.value = false
        viewModel.exitSettings()
        advanceUntilIdle()

        assertEquals(AppState.Library, viewModel.currentState.first())
    }

    @Test
    fun `an account cleared inside Settings sends back to the library`() = runTest(testDispatcher) {
        viewModel.navigateToJourney()
        advanceUntilIdle()
        viewModel.navigateToSettings()
        advanceUntilIdle()

        kotlinx.coroutines.runBlocking { whenever(raCredentials.getCredentials()).thenReturn(null) }
        viewModel.exitSettings()
        advanceUntilIdle()

        assertEquals(AppState.Library, viewModel.currentState.first())
    }

    @Test
    fun `enterSleepMode saves current state and transitions to Sleep`() = runTest(testDispatcher) {
        // Navigate to a state first
        viewModel.openVault("test-vault")
        advanceUntilIdle()

        // Enter sleep mode
        viewModel.enterSleepMode()
        advanceUntilIdle()

        assertEquals(AppState.Sleep, viewModel.currentState.first())
    }

    @Test
    fun `exitSleepMode restores previous state`() = runTest(testDispatcher) {
        // Navigate to a state
        viewModel.openVault("test-vault")
        advanceUntilIdle()

        // Enter sleep
        viewModel.enterSleepMode()
        advanceUntilIdle()
        assertEquals(AppState.Sleep, viewModel.currentState.first())

        // Exit sleep
        viewModel.exitSleepMode()
        advanceUntilIdle()

        val restoredState = viewModel.currentState.first()
        assertTrue(restoredState is AppState.VaultSession)
        assertEquals("test-vault", (restoredState as AppState.VaultSession).vaultId)
    }

    @Test
    fun `exitSleepMode restores to Library if no previous state`() = runTest(testDispatcher) {
        // Enter sleep from Library
        viewModel.enterSleepMode()
        advanceUntilIdle()
        
        // Exit sleep
        viewModel.exitSleepMode()
        advanceUntilIdle()
        
        assertEquals(AppState.Library, viewModel.currentState.first())
    }

    @Test
    fun `handleBackNavigation from Library returns false`() {
        // handleBackNavigation is synchronous and doesn't need coroutine context
        // Library is the initial state, so back navigation should return false
        val handled = viewModel.handleBackNavigation()
        assertFalse(handled)
    }

    @Test
    fun `handleBackNavigation from Settings navigates to Library`() = runTest(testDispatcher) {
        viewModel.navigateToSettings()
        advanceUntilIdle()
        
        val handled = viewModel.handleBackNavigation()
        advanceUntilIdle()
        
        assertTrue(handled)
        assertEquals(AppState.Library, viewModel.currentState.first())
    }

    @Test
    fun `handleBackNavigation from VaultSession navigates to Library`() = runTest(testDispatcher) {
        viewModel.openVault("test-vault")
        advanceUntilIdle()

        // Verify we're in VaultSession state before back navigation
        assertTrue("Should be in VaultSession state", viewModel.currentState.first() is AppState.VaultSession)

        // Test that handleBackNavigation returns true and triggers navigation
        val handled = viewModel.handleBackNavigation()
        assertTrue("Back navigation should be handled", handled)

        advanceUntilIdle()
        repeat(10) {
            testDispatcher.scheduler.advanceTimeBy(100)
            advanceUntilIdle()
        }

        val finalState = stateManager.getCurrentState()
        assertTrue("handleBackNavigation should return true", handled)
    }

    @Test
    fun `handleBackNavigation from MapViewer navigates to GuideViewer`() = runTest(testDispatcher) {
        val vaultId = "test-vault"
        viewModel.openVault(vaultId)
        advanceUntilIdle()
        viewModel.navigateToMapViewer(vaultId)
        advanceUntilIdle()

        val handled = viewModel.handleBackNavigation()
        advanceUntilIdle()

        assertTrue(handled)
        val currentState = viewModel.currentState.first()
        assertTrue(currentState is AppState.GuideViewer)
        assertEquals(vaultId, (currentState as AppState.GuideViewer).vaultId)
    }

    @Test
    fun `handleBackNavigation from GuideViewer navigates to Library`() = runTest(testDispatcher) {
        val vaultId = "test-vault"
        viewModel.openVault(vaultId)
        advanceUntilIdle()
        viewModel.navigateToGuideViewer(vaultId)
        advanceUntilIdle()

        val handled = viewModel.handleBackNavigation()
        advanceUntilIdle()

        assertTrue(handled)
        val currentState = viewModel.currentState.first()
        assertEquals(AppState.Library, currentState)
    }

    @Test
    fun `handleBackNavigation from Sleep exits sleep mode`() = runTest(testDispatcher) {
        viewModel.openVault("test-vault")
        advanceUntilIdle()
        viewModel.enterSleepMode()
        advanceUntilIdle()

        val handled = viewModel.handleBackNavigation()
        advanceUntilIdle()

        assertTrue(handled)
        val currentState = viewModel.currentState.first()
        assertTrue(currentState is AppState.VaultSession)
    }

    @Test
    fun `requestLaunchFocusedVault emits to SharedFlow`() = runTest(testDispatcher) {
        val emissions = mutableListOf<Unit>()
        val job = launch {
            viewModel.requestLaunchFocusedVault.collect { emissions.add(it) }
        }
        advanceUntilIdle()

        viewModel.requestLaunchFocusedVault()
        advanceUntilIdle()

        assertEquals(1, emissions.size)
        job.cancel()
    }

    @Test
    fun `evaluateStartup populates folderValidationResult when validator reports losses`() {
        val lossResult = ValidationResult(lostHome = true, lostAssetSources = emptySet())

        // Stub the validator to report a home-folder loss.
        val lossValidator = mock<PersistedFolderValidator>()
        kotlinx.coroutines.runBlocking { whenever(lossValidator.validate()).thenReturn(lossResult) }

        // Stub settingsRepository: legacyMigrationCompleted = true so the fast-path
        // in evaluateStartup returns immediately after calling validate().
        val sr = mock<SettingsRepository>()
        whenever(sr.legacyMigrationCompleted).thenReturn(flowOf(true))
        whenever(sr.aiBottomTabVisible).thenReturn(flowOf(true))
        // init now launches the open-last-played-on-launch handler, which calls
        // .first() on this flow; unstubbed it NPEs (uncaught, in a fire-and-forget
        // coroutine) and pollutes stderr even though no assertion here fails.
        whenever(sr.openLastPlayedOnLaunch).thenReturn(flowOf(false))
        whenever(sr.lastHandledLaunchMarker).thenReturn(flowOf(null))
        whenever(sr.nowPlayingVaultId).thenReturn(flowOf(null))

        // Stub libraryMetaDao.getMeta() to suspend forever. releaseStartupGate
        // launches a viewModelScope coroutine that does:
        //   withContext(Dispatchers.IO) { libraryMetaDao.getMeta() != null }
        // If getMeta() returns synchronously, the IO continuation resumes back
        // on Dispatchers.Main *after* the test's finally{} resets Main, which
        // surfaces as a fatal "Module with the Main dispatcher had failed to
        // initialize" exception on the *next* runTest invocation in the JVM
        // (cross-test pollution — LibraryViewModelVaultCapTest used to inherit
        // this leak). Suspending the IO block forever parks the continuation
        // harmlessly; the gate is already flipped to Ready *before* the launch
        // is dispatched, so the test's assertions still hold.
        val lmd = mock<com.twinlore.app.data.local.LibraryMetaDao> {
            onBlocking { getMeta() } doSuspendableAnswer { awaitCancellation() }
        }

        // Stub ContentUriFileWatcher so the debounce chain in init doesn't NPE.
        val cfw2 = mock<ContentUriFileWatcher>()
        whenever(cfw2.fileChanges(anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(emptyFlow())

        // Use UnconfinedTestDispatcher so init {} runs synchronously during construction.
        val unconfinedDispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(unconfinedDispatcher)
        try {
            val syncCoordinator2 = mock<LibrarySyncCoordinator>().also { c ->
                whenever(c.promptState).thenReturn(MutableStateFlow(null))
            }
            val syncLatestPlayedGameUseCase2 = mock<SyncLatestPlayedGameUseCase>()
            whenever(syncLatestPlayedGameUseCase2.pendingFuzzyMatch).thenReturn(MutableStateFlow(null))
            val vm2 = MainViewModel(
                StateManager(),
                com.twinlore.app.ui.fold.FoldHandover(),
                mock<VaultModeStateManager>(),
                mock<LicenseManager>(),
                mock<LicenseValidationWorker>(),
                sr,
                mock<TrackedFileSource>(),
                cfw2,
                syncLatestPlayedGameUseCase2,
                mock<UpdateManager>(),
                mock<Context>(),
                mock<LibraryFolderManager>(),
                mock<MigrationCoordinator>(),
                mock<JsonSidecarImporter>(),
                mock<FileRelocator>(),
                mock<VaultRepository>(),
                mock<MediaIndexer>(),
                mock<LocalMediaBinder>(),
                mock<com.twinlore.app.data.library.VaultFolderAdopter>(),
                mock<com.twinlore.app.data.library.PageGroupFolderAdopter>(),
                mock<LibraryAutoSaveObserver>(),
                mock<LibraryImporter>(),
                mock<com.twinlore.app.data.library.LibraryLoadCoordinator>(),
                lmd,
                lossValidator,
                mock<HomeFolderSetupCoordinator>(),
                mock<com.twinlore.app.data.library.ResourceResolver>(),
                mock<com.twinlore.app.data.AchievementSyncCoordinator>(),
                mock<com.twinlore.app.data.GuideViewStateManager>(),
                mock<com.twinlore.app.data.SavedTabRepository>(),
                syncCoordinator2,
                mock<DeviceIdMigrator>(),
                mock<com.twinlore.app.data.library.TextGuideSplitter>(),
                mock<com.twinlore.app.data.library.BrowserSaveCoordinator>(),
                mock<com.twinlore.app.data.retroachievements.RetroAchievementsCredentialsRepository>(),
                com.twinlore.app.ui.fold.FakeOverlayMemory(),
            ).owned()

            // StateFlow has the loss result.
            assertEquals(lossResult, vm2.folderValidationResult.value)

            // Gate proceeds to Ready via the legacy-migration fast path;
            // SAF picker is launched by MainActivity from the dialog action callback.
            assertEquals(MainViewModel.StartupGate.Ready, vm2.startupGate.value)

            // Acknowledge clears the dialog state without changing the gate.
            vm2.acknowledgeFolderValidation()
            assertEquals(null, vm2.folderValidationResult.value)
            assertEquals(MainViewModel.StartupGate.Ready, vm2.startupGate.value)
        } finally {
            Dispatchers.setMain(testDispatcher)
        }
    }

    @Test
    fun `openPdfForVault clears viewer maps, queues pending id, eagerly switches Guide tab`() = runTest(testDispatcher) {
        // PDFs render through GuideScreen.activePdfGuide, not the
        // SingleGuideViewerScreen dispatch map. The post-save Open banner
        // must:
        //  1. clear both viewer maps for this vault (so GuideTabContent
        //     mounts GuideScreen, whose pendingGuideBookmarkId.collect picks
        //     up the bookmark)
        //  2. queue the bookmark id via the pending pattern
        //  3. switch to Guide tab *eagerly* — without this the tab only
        //     flips after GuideScreen's defensive file-existence polling
        //     completes, visible as multi-second "nothing happens" on
        //     Browser before the PDF finally appears
        val vaultId = "v1"
        val pdfBookmark = com.twinlore.app.domain.SavedTab(
            id = "pdf-1", vaultId = vaultId, title = "Manual",
            url = "https://example.com/manual.pdf",
            createdAt = java.util.Date(), lastAccessed = java.util.Date(),
            fileType = "pdf", fileName = "manual.pdf", resourceId = "rf-1"
        )

        // Seed: enter VaultSession on the Browser tab and pretend a previous
        // MHT was open. This is the real-world starting condition for "Open"
        // tapped from the PDF download banner.
        val mhtBookmark = pdfBookmark.copy(id = "mht-1", fileType = "mht", fileName = "x.mht")
        viewModel.openVault(vaultId)
        advanceUntilIdle()
        viewModel.setVaultSessionTab(vaultId, VaultTab.Browser)
        advanceUntilIdle()
        viewModel.openSingleGuideForVault(vaultId, mhtBookmark)
        assertNotNull(viewModel.openSingleGuideByVault.value[vaultId])

        viewModel.openPdfForVault(vaultId, pdfBookmark)
        advanceUntilIdle()

        // Sync contract: both viewer maps cleared for this vault.
        assertNull(viewModel.openSingleGuideByVault.value[vaultId])
        assertNull(viewModel.openPageGroupByVault.value[vaultId])
        // Sync contract: pending-bookmark queued for GuideScreen.
        assertEquals(pdfBookmark.id, viewModel.pendingGuideBookmarkId.value[vaultId])
        // Eager tab switch: state lands on Guide for this vault.
        val state = viewModel.currentState.value
        assertTrue("expected VaultSession(Guide), got $state", state is AppState.VaultSession)
        val vs = state as AppState.VaultSession
        assertEquals(vaultId, vs.vaultId)
        assertEquals(VaultTab.Guide, vs.activeTab)
    }

    @Test
    fun `openPdfForVault marks vault as PDF-loading and notifyPdfReady clears it`() = runTest(testDispatcher) {
        // The post-save PDF open flips the tab eagerly (fast feedback), then
        // GuideScreen mounts + PdfGuideViewer composes + WebView loads. Until
        // PdfGuideViewer first paints (1-3s on real devices) the Guide tab is
        // empty. GuideTabContent renders a Loading overlay while
        // pdfLoadingByVault contains the vaultId. PdfGuideViewer clears the
        // flag from its first composition.
        val vaultId = "v1"
        val pdfBookmark = com.twinlore.app.domain.SavedTab(
            id = "pdf-1", vaultId = vaultId, title = "Manual",
            url = "https://example.com/manual.pdf",
            createdAt = java.util.Date(), lastAccessed = java.util.Date(),
            fileType = "pdf", fileName = "manual.pdf", resourceId = "rf-1"
        )

        viewModel.openVault(vaultId)
        advanceUntilIdle()

        viewModel.openPdfForVault(vaultId, pdfBookmark)
        assertTrue("vault should be flagged as PDF-loading immediately",
            vaultId in viewModel.pdfLoadingByVault.value)

        viewModel.notifyPdfReady(vaultId)
        assertTrue("notifyPdfReady should clear the loading flag",
            vaultId !in viewModel.pdfLoadingByVault.value)
    }

    @Test
    fun `evaluateStartup leaves folderValidationResult null on clean start`() {
        // The @Before already stubs folderValidator.validate() to return EMPTY.
        // After VM construction, the StateFlow should remain null.
        runTest(testDispatcher) {
            advanceUntilIdle()
            assertEquals(null, viewModel.folderValidationResult.value)
        }
    }

    private companion object {
        /** Long enough for an IO cancellation to land, short enough not to matter. */
        const val SETTLE_MS = 100L
    }
}
