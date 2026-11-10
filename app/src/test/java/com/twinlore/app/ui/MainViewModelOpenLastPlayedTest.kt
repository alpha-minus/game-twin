package com.twinlore.app.ui

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.twinlore.app.data.ContentUriFileWatcher
import com.twinlore.app.data.LastPlayedSource
import com.twinlore.app.data.SettingsRepository
import com.twinlore.app.data.SyncLatestPlayedGameUseCase
import com.twinlore.app.data.TrackedFileSource
import com.twinlore.app.data.ValidationResult
import com.twinlore.app.data.VaultModeStateManager
import com.twinlore.app.data.VaultRepository
import com.twinlore.app.data.PersistedFolderValidator
import com.twinlore.app.data.library.BrowserSaveCoordinator
import com.twinlore.app.data.library.DeviceIdMigrator
import com.twinlore.app.data.library.FileRelocator
import com.twinlore.app.data.library.HomeFolderSetupCoordinator
import com.twinlore.app.data.library.JsonSidecarImporter
import com.twinlore.app.data.library.LibraryAutoSaveObserver
import com.twinlore.app.data.library.LibraryFolderManager
import com.twinlore.app.data.library.LibraryImporter
import com.twinlore.app.data.library.MediaIndexer
import com.twinlore.app.data.library.LocalMediaBinder
import com.twinlore.app.data.library.MigrationCoordinator
import com.twinlore.app.data.library.sync.LibrarySyncCoordinator
import com.twinlore.app.data.license.LicenseManager
import com.twinlore.app.data.license.LicenseValidationWorker
import com.twinlore.app.data.retroachievements.RaCredentials
import com.twinlore.app.data.update.UpdateManager
import com.twinlore.app.domain.AppState
import com.twinlore.app.domain.StateManager
import com.twinlore.app.domain.Vault
import com.twinlore.app.domain.VaultTab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * The open-last-played-on-launch handler. Cold start is the handler call in
 * MainViewModel.init; foreground is onAppForegrounded(). File-source foregrounds
 * are gated on the launch marker changing; the already-open vault is never
 * re-showcased; a watcher-set pending library refresh is cleared by the Library
 * showcase path instead.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelOpenLastPlayedTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var stateManager: StateManager
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var contentUriFileWatcher: ContentUriFileWatcher
    private lateinit var syncLatestPlayedGameUseCase: SyncLatestPlayedGameUseCase
    private lateinit var vaultRepository: VaultRepository
    private lateinit var vaultModeStateManager: VaultModeStateManager
    private var overlayMemory = com.twinlore.app.ui.fold.FakeOverlayMemory()
    private val foldHandover = com.twinlore.app.ui.fold.FoldHandover()
    private lateinit var pendingFuzzyMatch: MutableStateFlow<SyncLatestPlayedGameUseCase.PendingFuzzyMatch?>

    /** See MainViewModelTest: the view model's init outlives the class without this. */
    private val viewModelStore = androidx.lifecycle.ViewModelStore()
    private var viewModelKeys = 0

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        stateManager = StateManager()
        settingsRepository = mock<SettingsRepository>()
        contentUriFileWatcher = mock<ContentUriFileWatcher>()
        syncLatestPlayedGameUseCase = mock<SyncLatestPlayedGameUseCase>()
        // No pending fuzzy-match candidate by default; handleOpenLastPlayed's guard
        // reads this after execute() to decide whether to showcase the candidate.
        // Tests that exercise the guard flip this field mid-test.
        pendingFuzzyMatch = MutableStateFlow(null)
        whenever(syncLatestPlayedGameUseCase.pendingFuzzyMatch).thenReturn(pendingFuzzyMatch)
        vaultRepository = mock<VaultRepository>()
        vaultModeStateManager = mock<VaultModeStateManager>()

        // Same init-survival stubs as MainViewModelTest.setup.
        whenever(settingsRepository.legacyMigrationCompleted).thenReturn(flow { awaitCancellation() })
        whenever(settingsRepository.aiBottomTabVisible).thenReturn(flowOf(true))
        whenever(settingsRepository.achievementsTabVisible).thenReturn(flowOf(true))
        whenever(contentUriFileWatcher.fileChanges(anyOrNull(), anyOrNull(), anyOrNull()))
            .thenReturn(emptyFlow())
        whenever(vaultModeStateManager.getLastTab(any())).thenReturn(VaultTab.Guide)
        kotlinx.coroutines.runBlocking {
            whenever(vaultRepository.getVaultById(any())).thenAnswer { invocation ->
                val id = invocation.getArgument<String>(0)
                Vault(
                    id = id, name = id, romPath = null, romFilename = null,
                    systemeid = null, gameId = null, source = null,
                    lastOpened = null, createdAt = java.util.Date()
                )
            }
        }

        // Feature stubs; individual tests override before calling buildViewModel().
        whenever(settingsRepository.openLastPlayedOnLaunch).thenReturn(flowOf(true))
        whenever(settingsRepository.lastPlayedSource).thenReturn(flowOf(LastPlayedSource.ES_DE))
        whenever(settingsRepository.esdeFolderUri).thenReturn(flowOf("content://esde"))
        whenever(settingsRepository.lastHandledLaunchMarker).thenReturn(flowOf(null))
        whenever(settingsRepository.nowPlayingVaultId).thenReturn(flowOf(null))
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
        // Before resetMain: viewModelScope is on Dispatchers.Main.immediate.
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
        // See MainViewModelTest.tearDown: the daily update-check loop makes Mockito
        // accumulate millions of invocation records without this.
        org.mockito.Mockito.framework().clearInlineMocks()
    }

    private fun buildViewModel(): MainViewModel {
        val folderValidator = mock<PersistedFolderValidator>()
        val libraryMetaDao = mock<com.twinlore.app.data.local.LibraryMetaDao>()
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
        val librarySyncCoordinator = mock<LibrarySyncCoordinator>().also { c ->
            whenever(c.promptState).thenReturn(MutableStateFlow(null))
        }
        val raCredentials = mock<com.twinlore.app.data.retroachievements.RetroAchievementsCredentialsRepository> {
            onBlocking { getCredentials() } doReturn RaCredentials("me", "key")
        }
        return MainViewModel(
            stateManager, foldHandover, vaultModeStateManager, mock<LicenseManager>(), mock<LicenseValidationWorker>(),
            settingsRepository, mock<TrackedFileSource>(), contentUriFileWatcher,
            syncLatestPlayedGameUseCase, mock<UpdateManager>(),
            mock<Context>(), mock<LibraryFolderManager>(), mock<MigrationCoordinator>(),
            mock<JsonSidecarImporter>(), mock<FileRelocator>(), vaultRepository,
            mock<MediaIndexer>(), mock<LocalMediaBinder>(),
            mock<com.twinlore.app.data.library.VaultFolderAdopter>(),
            mock<com.twinlore.app.data.library.PageGroupFolderAdopter>(),
            mock<LibraryAutoSaveObserver>(), mock<LibraryImporter>(),
            mock<com.twinlore.app.data.library.LibraryLoadCoordinator>(), libraryMetaDao,
            folderValidator,
            mock<HomeFolderSetupCoordinator>(),
            mock<com.twinlore.app.data.library.ResourceResolver>(),
            mock<com.twinlore.app.data.AchievementSyncCoordinator>(),
            mock<com.twinlore.app.data.GuideViewStateManager>(),
            mock<com.twinlore.app.data.SavedTabRepository>(),
            librarySyncCoordinator,
            mock<DeviceIdMigrator>(),
            mock<com.twinlore.app.data.library.TextGuideSplitter>(),
            mock<BrowserSaveCoordinator>(),
            raCredentials,
            overlayMemory,
        ).also { viewModelStore.put("vm-${viewModelKeys++}", it) }
    }

    private fun stubMarkers(vararg markers: String?) {
        kotlinx.coroutines.runBlocking {
            var stubbing = whenever(contentUriFileWatcher.readLaunchMarker(anyOrNull(), any()))
                .thenReturn(markers.first())
            for (marker in markers.drop(1)) {
                stubbing = stubbing.thenReturn(marker)
            }
        }
    }

    private fun stubDetection(vararg vaultIds: String?) {
        kotlinx.coroutines.runBlocking {
            var stubbing = whenever(syncLatestPlayedGameUseCase.execute(anyOrNull()))
                .thenReturn(vaultIds.first())
            for (id in vaultIds.drop(1)) {
                stubbing = stubbing.thenReturn(id)
            }
        }
    }

    @Test
    fun `cold start with a new marker triggers the full refresh`() = runTest(testDispatcher) {
        stubMarkers("m1") // handled marker stubbed null => newLaunch
        val viewModel = buildViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.triggerRefreshWhenOnLibrary.value)
        assertNull(viewModel.vaultToShowcase.value)
        verify(syncLatestPlayedGameUseCase, never()).execute(anyOrNull())
    }

    @Test
    fun `cold start with unchanged marker and a record shows the recorded vault`() = runTest(testDispatcher) {
        stubMarkers("m1")
        whenever(settingsRepository.lastHandledLaunchMarker).thenReturn(flowOf("m1"))
        whenever(settingsRepository.nowPlayingVaultId).thenReturn(flowOf("v-rec"))
        val viewModel = buildViewModel()
        advanceUntilIdle()

        assertEquals("v-rec", viewModel.vaultToShowcase.value)
        assertFalse(viewModel.triggerRefreshWhenOnLibrary.value)
        verify(syncLatestPlayedGameUseCase, never()).execute(anyOrNull())
    }

    @Test
    fun `cold start with unchanged marker and no record does nothing`() = runTest(testDispatcher) {
        stubMarkers("m1")
        whenever(settingsRepository.lastHandledLaunchMarker).thenReturn(flowOf("m1"))
        val viewModel = buildViewModel()
        advanceUntilIdle()

        assertNull(viewModel.vaultToShowcase.value)
        assertFalse(viewModel.triggerRefreshWhenOnLibrary.value)
    }

    @Test
    fun `unreadable log erases the handled marker and arms nothing`() = runTest(testDispatcher) {
        // readLaunchMarker returning null (log missing/unparseable) must not be
        // treated as "nothing new" against a stale handled marker: it is stored as
        // the new handled value so a later successful read counts as a fresh launch
        // and runs the visible detection refresh, rather than being silently eaten.
        stubMarkers(null)
        whenever(settingsRepository.lastHandledLaunchMarker).thenReturn(flowOf("m1"))
        val viewModel = buildViewModel()
        advanceUntilIdle()

        assertFalse(viewModel.triggerRefreshWhenOnLibrary.value)
        assertNull(viewModel.vaultToShowcase.value)
        verify(settingsRepository).setLastHandledLaunchMarker(null)
    }

    @Test
    fun `toggle off means nothing is armed`() = runTest(testDispatcher) {
        whenever(settingsRepository.openLastPlayedOnLaunch).thenReturn(flowOf(false))
        val viewModel = buildViewModel()
        advanceUntilIdle()

        assertFalse(viewModel.triggerRefreshWhenOnLibrary.value)
    }

    @Test
    fun `unconfigured source means nothing is armed`() = runTest(testDispatcher) {
        whenever(settingsRepository.lastPlayedSource).thenReturn(flowOf(LastPlayedSource.NOT_CONFIGURED))
        val viewModel = buildViewModel()
        advanceUntilIdle()

        assertFalse(viewModel.triggerRefreshWhenOnLibrary.value)
        assertNull(viewModel.vaultToShowcase.value)
        verify(syncLatestPlayedGameUseCase, never()).execute(anyOrNull())
    }

    @Test
    fun `library foreground with nothing new still shows the recorded vault`() = runTest(testDispatcher) {
        stubMarkers("m1", "m1")
        whenever(settingsRepository.lastHandledLaunchMarker).thenReturn(flowOf("m1"))
        whenever(settingsRepository.nowPlayingVaultId).thenReturn(flowOf("v-rec"))
        val viewModel = buildViewModel()
        advanceUntilIdle()
        viewModel.clearVaultToShowcase()

        viewModel.onAppForegrounded() // state is Library
        advanceUntilIdle()

        assertEquals("v-rec", viewModel.vaultToShowcase.value)
    }

    @Test
    fun `vault foreground with nothing new and same recorded vault stays put`() = runTest(testDispatcher) {
        stubMarkers("m1", "m1")
        whenever(settingsRepository.lastHandledLaunchMarker).thenReturn(flowOf("m1"))
        whenever(settingsRepository.nowPlayingVaultId).thenReturn(flowOf("v1"))
        val viewModel = buildViewModel()
        advanceUntilIdle()
        viewModel.clearVaultToShowcase()
        viewModel.openVault("v1")
        advanceUntilIdle()
        assertTrue(viewModel.currentState.value is AppState.VaultSession)

        viewModel.onAppForegrounded()
        advanceUntilIdle()

        assertTrue(viewModel.currentState.value is AppState.VaultSession)
        assertNull(viewModel.vaultToShowcase.value)
    }

    @Test
    fun `vault foreground with a new launch bounces to library and arms the new vault`() = runTest(testDispatcher) {
        stubMarkers("m1", "m2")
        whenever(settingsRepository.lastHandledLaunchMarker).thenReturn(flowOf(null), flowOf("m1"))
        stubDetection("v2")
        whenever(settingsRepository.nowPlayingVaultId).thenReturn(flowOf("v1"))
        val viewModel = buildViewModel()
        advanceUntilIdle()
        viewModel.clearVaultToShowcase()
        viewModel.clearTriggerRefreshWhenOnLibrary()
        viewModel.openVault("v1")
        advanceUntilIdle()

        viewModel.onAppForegrounded()
        advanceUntilIdle()

        assertTrue(viewModel.currentState.value is AppState.Library)
        assertEquals("v2", viewModel.vaultToShowcase.value)
        verify(syncLatestPlayedGameUseCase, times(1)).execute(anyOrNull())
    }

    @Test
    fun `vault foreground resolving the already open vault stays put`() = runTest(testDispatcher) {
        stubMarkers("m1", "m2")
        whenever(settingsRepository.lastHandledLaunchMarker).thenReturn(flowOf(null), flowOf("m1"))
        stubDetection("v1")
        val viewModel = buildViewModel()
        advanceUntilIdle()
        viewModel.clearVaultToShowcase()
        viewModel.clearTriggerRefreshWhenOnLibrary()
        viewModel.openVault("v1")
        advanceUntilIdle()

        viewModel.onAppForegrounded()
        advanceUntilIdle()

        assertTrue(viewModel.currentState.value is AppState.VaultSession)
        assertNull(viewModel.vaultToShowcase.value)
    }

    @Test
    fun `foreground with a new launch while in Settings arms nothing and stays put`() = runTest(testDispatcher) {
        // Settings and AppCatalog are configuration surfaces the user is actively
        // using; an activity-result round trip from the app's own SAF folder picker
        // or a permission dialog fires onResume just like a real app switch, and
        // must not yank the user out into a vault mid-configuration.
        stubMarkers("m1", "m2")
        whenever(settingsRepository.lastHandledLaunchMarker).thenReturn(flowOf(null), flowOf("m1"))
        stubDetection("v2")
        val viewModel = buildViewModel()
        advanceUntilIdle()
        viewModel.clearVaultToShowcase()
        viewModel.clearTriggerRefreshWhenOnLibrary()
        viewModel.navigateToSettings()
        advanceUntilIdle()
        assertTrue(viewModel.currentState.value is AppState.Settings)

        viewModel.onAppForegrounded()
        advanceUntilIdle()

        assertTrue(viewModel.currentState.value is AppState.Settings)
        assertNull(viewModel.vaultToShowcase.value)
        verify(syncLatestPlayedGameUseCase, never()).execute(anyOrNull())
    }

    @Test
    fun `pending fuzzy match suppresses the arm`() = runTest(testDispatcher) {
        stubMarkers("m1", "m2")
        whenever(settingsRepository.lastHandledLaunchMarker).thenReturn(flowOf(null), flowOf("m1"))
        stubDetection("v2")
        val viewModel = buildViewModel()
        advanceUntilIdle()
        viewModel.clearVaultToShowcase()
        viewModel.clearTriggerRefreshWhenOnLibrary()
        viewModel.openVault("v1")
        advanceUntilIdle()
        // Flip the pending flow the handler reads after execute() resolves the candidate.
        val existingVault = Vault(
            id = "v2", name = "Existing Game", romPath = null, romFilename = null,
            systemeid = null, gameId = null, source = null,
            lastOpened = null, createdAt = java.util.Date()
        )
        pendingFuzzyMatch.value = SyncLatestPlayedGameUseCase.PendingFuzzyMatch(
            existingVault = existingVault,
            newName = "New Game",
            similarity = 0.8,
            createIfNotFound = true,
            source = "ES-DE"
        )

        viewModel.onAppForegrounded()
        advanceUntilIdle()

        assertNull(viewModel.vaultToShowcase.value)
        assertTrue(viewModel.currentState.value is AppState.VaultSession)
    }

    @Test
    fun `achievements source on library triggers the full refresh`() = runTest(testDispatcher) {
        whenever(settingsRepository.lastPlayedSource).thenReturn(flowOf(LastPlayedSource.ACHIEVEMENTS))
        stubMarkers(null)
        val viewModel = buildViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.triggerRefreshWhenOnLibrary.value)
        verify(syncLatestPlayedGameUseCase, never()).execute(anyOrNull())
    }

    @Test
    fun `achievements cold start survives settings that resolve without suspending`() = runTest(testDispatcher) {
        // On device viewModelScope is Main.immediate, so init's launch runs inside
        // the constructor until its first real suspension. The achievements source
        // has no launch-marker IO to suspend on, so the handler reached the refresh
        // trigger before that field was initialized: an NPE on every cold start
        // (1.1.1 crash loop with "open last played on launch"). An eager Main
        // reproduces the device ordering.
        Dispatchers.setMain(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler))
        whenever(settingsRepository.lastPlayedSource).thenReturn(flowOf(LastPlayedSource.ACHIEVEMENTS))
        val viewModel = buildViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.triggerRefreshWhenOnLibrary.value)
    }

    @Test
    fun `marker is persisted on every attempt`() = runTest(testDispatcher) {
        stubMarkers("m1")
        val viewModel = buildViewModel()
        advanceUntilIdle()

        verify(settingsRepository).setLastHandledLaunchMarker("m1")
    }

    // Revision 3: the showcase no longer enters the vault by itself. An automatic
    // trigger (cold start, new-launch foreground, or the lightweight "nothing new"
    // reopen) only arms the Library's showcase presentation; confirming or
    // dismissing it is entirely up to the user from there on, exactly like a
    // manual refresh. MainViewModel holds no auto-open signal at all any more, so
    // there is nothing left in this class that could flip state to VaultSession
    // on its own -- these assertions pin that absence for both trigger shapes.

    @Test
    fun `full detection trigger arms the showcase and never opens the vault by itself`() = runTest(testDispatcher) {
        stubMarkers("m1") // handled marker stubbed null => newLaunch
        val viewModel = buildViewModel()
        advanceUntilIdle()

        // Full-detection trigger shape armed: LibraryScreen will fade, detect, and
        // raise the showcase.
        assertTrue(viewModel.triggerRefreshWhenOnLibrary.value)
        assertTrue(viewModel.currentState.value is AppState.Library)
        // Advance well past the old ~1s auto-open dwell (contentReadySignaled wait
        // + delay). If any code path still auto-entered the vault, state would have
        // flipped to VaultSession by now.
        advanceTimeBy(10_000)
        advanceUntilIdle()
        assertTrue(viewModel.currentState.value is AppState.Library)
    }

    @Test
    fun `lightweight showcase trigger arms the recorded vault and never opens it by itself`() = runTest(testDispatcher) {
        stubMarkers("m1")
        whenever(settingsRepository.lastHandledLaunchMarker).thenReturn(flowOf("m1"))
        whenever(settingsRepository.nowPlayingVaultId).thenReturn(flowOf("v-rec"))
        val viewModel = buildViewModel()
        advanceUntilIdle()

        // Lightweight trigger shape armed: LibraryScreen will focus v-rec and raise
        // the showcase, with no detection pass.
        assertEquals("v-rec", viewModel.vaultToShowcase.value)
        assertTrue(viewModel.currentState.value is AppState.Library)
        advanceTimeBy(10_000)
        advanceUntilIdle()
        assertTrue(viewModel.currentState.value is AppState.Library)
        assertFalse(viewModel.currentState.value is AppState.VaultSession)
    }

    // ----- Cold start with overlay mode on -----

    private fun assertResumedInOverlay(viewModel: MainViewModel, vaultId: String) {
        assertTrue(foldHandover.consumeColdStartFold(vaultId))
        assertTrue(overlayMemory.on)
        assertEquals(vaultId, (stateManager.currentState.value as? AppState.VaultSession)?.vaultId)
        assertFalse(viewModel.triggerRefreshWhenOnLibrary.value)
        assertNull(viewModel.vaultToShowcase.value)
    }

    private fun assertOrdinaryStart(vaultTried: String = "v-any") {
        assertFalse(foldHandover.consumeColdStartFold(vaultTried))
        // The app is in front, so overlay mode is over.
        assertFalse(overlayMemory.on)
    }

    @Test
    fun `overlay mode, a new launch of a game in the library resumes it in overlay`() = runTest(testDispatcher) {
        overlayMemory.on = true
        stubMarkers("m1")
        whenever(syncLatestPlayedGameUseCase.resolveExisting(anyOrNull())).thenReturn("v-game")
        val viewModel = buildViewModel()
        advanceUntilIdle()

        assertResumedInOverlay(viewModel, "v-game")
        verify(syncLatestPlayedGameUseCase, never()).execute(anyOrNull())
    }

    @Test
    fun `overlay mode, nothing new launched resumes the recorded game`() = runTest(testDispatcher) {
        overlayMemory.on = true
        stubMarkers("m1")
        whenever(settingsRepository.lastHandledLaunchMarker).thenReturn(flowOf("m1"))
        whenever(settingsRepository.nowPlayingVaultId).thenReturn(flowOf("v-rec"))
        val viewModel = buildViewModel()
        advanceUntilIdle()

        assertResumedInOverlay(viewModel, "v-rec")
        verify(syncLatestPlayedGameUseCase, never()).resolveExisting(anyOrNull())
    }

    @Test
    fun `overlay mode, a game not in the library yet starts the app normally`() = runTest(testDispatcher) {
        overlayMemory.on = true
        stubMarkers("m1")
        whenever(syncLatestPlayedGameUseCase.resolveExisting(anyOrNull())).thenReturn(null)
        val viewModel = buildViewModel()
        advanceUntilIdle()

        assertOrdinaryStart()
        // The Library's own detection adds it, with the app in front.
        assertTrue(viewModel.triggerRefreshWhenOnLibrary.value)
    }

    @Test
    fun `overlay mode, an unconfirmed guess starts the app normally to ask`() = runTest(testDispatcher) {
        overlayMemory.on = true
        stubMarkers("m1")
        whenever(syncLatestPlayedGameUseCase.resolveExisting(anyOrNull())).thenAnswer {
            pendingFuzzyMatch.value = mock()
            "v-guess"
        }
        whenever(syncLatestPlayedGameUseCase.dismissFuzzyMatch()).thenAnswer {
            pendingFuzzyMatch.value = null
            Unit
        }
        val viewModel = buildViewModel()
        advanceUntilIdle()

        assertOrdinaryStart("v-guess")
        verify(syncLatestPlayedGameUseCase).dismissFuzzyMatch()
        assertTrue(viewModel.triggerRefreshWhenOnLibrary.value)
    }

    @Test
    fun `overlay mode with detection off resumes the recorded game`() = runTest(testDispatcher) {
        overlayMemory.on = true
        whenever(settingsRepository.nowPlayingVaultId).thenReturn(flowOf("v-rec"))
        whenever(settingsRepository.openLastPlayedOnLaunch).thenReturn(flowOf(false))
        val viewModel = buildViewModel()
        advanceUntilIdle()

        assertResumedInOverlay(viewModel, "v-rec")
    }

    @Test
    fun `overlay mode with detection off and no vault starts the app normally`() = runTest(testDispatcher) {
        overlayMemory.on = true
        whenever(settingsRepository.openLastPlayedOnLaunch).thenReturn(flowOf(false))
        buildViewModel()
        advanceUntilIdle()

        assertOrdinaryStart()
    }

    @Test
    fun `overlay mode on a vault that was deleted starts the app normally`() = runTest(testDispatcher) {
        overlayMemory.on = true
        stubMarkers("m1")
        whenever(settingsRepository.lastHandledLaunchMarker).thenReturn(flowOf("m1"))
        whenever(settingsRepository.nowPlayingVaultId).thenReturn(flowOf("v-gone"))
        whenever(vaultRepository.getVaultById("v-gone")).thenReturn(null)
        buildViewModel()
        advanceUntilIdle()

        assertOrdinaryStart("v-gone")
    }

    @Test
    fun `overlay mode off, a cold start never resumes it`() = runTest(testDispatcher) {
        stubMarkers("m1")
        whenever(settingsRepository.lastHandledLaunchMarker).thenReturn(flowOf("m1"))
        whenever(settingsRepository.nowPlayingVaultId).thenReturn(flowOf("v-rec"))
        val viewModel = buildViewModel()
        advanceUntilIdle()

        assertFalse(foldHandover.consumeColdStartFold("v-rec"))
        assertEquals("v-rec", viewModel.vaultToShowcase.value)
    }

    @Test
    fun `a foreground never resumes overlay, only a cold start does`() = runTest(testDispatcher) {
        stubMarkers("m1", "m1")
        whenever(settingsRepository.lastHandledLaunchMarker).thenReturn(flowOf("m1"))
        whenever(settingsRepository.nowPlayingVaultId).thenReturn(flowOf("v-rec"))
        val viewModel = buildViewModel()
        advanceUntilIdle()
        overlayMemory.on = true

        viewModel.onAppForegrounded()
        advanceUntilIdle()

        assertFalse(foldHandover.consumeColdStartFold("v-rec"))
    }

    private companion object {
        /** Long enough for an IO cancellation to land, short enough not to matter. */
        const val SETTLE_MS = 100L
    }
}
