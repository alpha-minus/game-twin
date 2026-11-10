package com.twinlore.app.ui.fold.guide

import com.twinlore.app.data.SettingsRepository
import com.twinlore.app.ui.fold.FoldHandover
import com.twinlore.app.domain.AppState
import com.twinlore.app.domain.SavedTab
import com.twinlore.app.domain.RaAchievementSortField
import com.twinlore.app.domain.RaAchievementTypeFilter
import com.twinlore.app.domain.SortDirection
import com.twinlore.app.domain.StateManager
import com.twinlore.app.domain.VaultTab
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Date
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * What the panel decides for itself, which is almost nothing and is meant to be.
 *
 * Everything about reading a guide belongs to the app's viewers, which the panel
 * hosts: the position, the chapter, the zoom, the search, the caching. What is
 * left here is which guide is open, which viewer renders it, and pointing the
 * app at that guide before handing over to it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FoldGuidePanelPresenterTest {

    private val dispatcher = StandardTestDispatcher()

    private class FakeLicense(
        state: com.twinlore.app.data.license.LicenseUiState = com.twinlore.app.data.license.LicenseUiState.Free,
    ) : com.twinlore.app.data.license.LicenseManagerUiStateContract {
        override val licenseUiState = kotlinx.coroutines.flow.MutableStateFlow(state)
        override fun isLicensedNow(): Boolean =
            licenseUiState.value is com.twinlore.app.data.license.LicenseUiState.Licensed
        override val licenseTier = kotlinx.coroutines.flow.MutableStateFlow(com.twinlore.app.data.license.LicenseTier.NONE)
    }

    private class FakeData(var guide: SavedTab? = null) : FoldGuideData {
        val asked = mutableListOf<String>()
        val touched = mutableListOf<String>()
        val zoomWrites = mutableListOf<Pair<String, Float>>()
        var images: List<FoldImage> = emptyList()

        override suspend fun currentGuide(vaultId: String): SavedTab? {
            asked += vaultId
            return guide
        }

        override suspend fun images(vaultId: String): List<FoldImage> = images

        /** The image the app's Image tab is open on, as the store has it. */
        var currentImageId: String? = null

        override suspend fun currentImage(vaultId: String): String? = currentImageId

        override suspend fun touchImage(vaultId: String, imageId: String) {
            touched += imageId
            currentImageId = imageId
        }

        override suspend fun saveTextZoom(bookmarkId: String, zoom: Float) {
            zoomWrites += bookmarkId to zoom
        }

        var storedViews = mutableMapOf<String, FoldImageView>()
        val viewWrites = mutableListOf<Pair<String, FoldImageView>>()

        /** What each read answered, so a test can see what the panel was told. */
        val viewReads = mutableListOf<Pair<String, FoldImageView?>>()

        override suspend fun imageView(vaultId: String, imageId: String): FoldImageView? =
            storedViews[imageId].also { viewReads += imageId to it }

        override suspend fun saveImageView(vaultId: String, imageId: String, view: FoldImageView) {
            viewWrites += imageId to view
            storedViews[imageId] = view
        }

        var cachedAwards: FoldAwards = FoldAwards()
        var freshAwards: FoldAwards? = null
        var comments: List<FoldComment> = emptyList()
        val refreshedVaults = mutableListOf<String>()
        val commentedOn = mutableListOf<Int>()

        override suspend fun awards(vaultId: String): FoldAwards = cachedAwards

        val opened = mutableListOf<String>()
        override suspend fun markOpened(vaultId: String) {
            opened += vaultId
        }

        var detected: FoldDetectedGame? = null
        val detectedFor = mutableListOf<String?>()
        override suspend fun detectNewlyPlayedGame(currentVaultId: String?): FoldDetectedGame? {
            detectedFor += currentVaultId
            return detected?.takeIf { it.vaultId != currentVaultId }
        }

        val handledMarkers = mutableListOf<String?>()
        override suspend fun markLaunchHandled(marker: String?) {
            handledMarkers += marker
        }

        /** Held open by a test that wants the refresh to land late. */
        var refreshGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null

        override suspend fun refreshAwards(vaultId: String): FoldAwards? {
            refreshedVaults += vaultId
            refreshGate?.await()
            return freshAwards
        }

        override suspend fun awardComments(achievementId: Int): List<FoldComment> {
            commentedOn += achievementId
            return comments
        }

        var savedScreenshot: FoldImage? = null
        val screenshotsFiled = mutableListOf<String>()

        val screenshotVaults = mutableListOf<String>()

        override suspend fun saveScreenshot(vaultId: String, file: java.io.File): FoldImage? {
            screenshotsFiled += file.name
            screenshotVaults += vaultId
            return savedScreenshot
        }

        val deleted = mutableListOf<String>()

        override suspend fun deleteImage(vaultId: String, imageId: String) {
            deleted += imageId
            images = images.filterNot { it.id == imageId }
        }
    }

    private fun achievement(id: Int, title: String = "Achievement $id") =
        com.twinlore.app.data.retroachievements.RaAchievementDisplay(
            id = id,
            title = title,
            description = "",
            points = 5,
            badgeName = "$id",
            rarityPercent = null,
            dateAwarded = null,
            hardcoreAchieved = false,
        )

    private fun image(id: String) = FoldImage(id, "Image $id", "file:///img/$id.png")

    /**
     * The presenter collects the fold's two settings for its own Settings
     * surface, so every one of them has to answer.
     */
    private fun foldSettings(): SettingsRepository = mock {
        on { foldPanelBarCollapsed } doReturn flowOf(false)
        on { appIcon } doReturn flowOf(com.twinlore.app.data.AppIcon.DEFAULT)
        on { foldPanelIntroSeen } doReturn flowOf(true)
        on { foldHandleSizeDp } doReturn flowOf(44)
        on { foldHandleAlpha } doReturn flowOf(0.35f)
        on { foldHandleRecess } doReturn flowOf(1f)
        on { foldHandleClockBattery } doReturn flowOf(false)
        on { guideThemeInject } doReturn flowOf(true)
        on { guideSmartFormatting } doReturn flowOf(true)
        on { themePalette } doReturn flowOf(null)
        on { appFont } doReturn flowOf(com.twinlore.app.data.AppFont.DEFAULT)
        on { checklistTapGameHighScore } doReturn flowOf(0)
        on { keepTopBarVisible } doReturn flowOf(false)
        on { foldPanelSurface } doReturn flowOf(null)
        on { foldPanelAwardSort } doReturn flowOf(null)
        on { foldPanelAwardSortDirection } doReturn flowOf(null)
        on { foldPanelAwardTypeFilter } doReturn flowOf(null)
        on { foldPanelAwardLockedOnly } doReturn flowOf(false)
        on { foldPanelAwardScroll } doReturn flowOf(0 to 0)
        on { statusBarMode } doReturn flowOf(com.twinlore.app.data.StatusBarMode.STANDARD)
    }

    // The pin is the app's own keepTopBarVisible, not a panel-local flag. Two
    // settings for the same question about the same kind of bar would disagree
    // the first time anyone used both surfaces.
    @Test
    fun `pinning the top bar writes the app's own setting`() = runTest(dispatcher) {
        val settings = foldSettings()
        val presenter = FoldGuidePanelPresenter(
            stateManager, data, settings, systemMetrics(), FoldHandover(), FakeLicense(), mock(),
            TestScope(dispatcher),
        )
        advanceUntilIdle()
        assertFalse(presenter.state.value.pinned)

        presenter.setPinned(true)
        advanceUntilIdle()
        assertTrue(presenter.state.value.pinned)
        verify(settings).setKeepTopBarVisible(true)
    }

    // ----- The app icon and font, from the panel's own settings -----

    @Test
    fun `an icon the user does not have is not written`() = runTest(dispatcher) {
        val settings = foldSettings()
        val presenter = FoldGuidePanelPresenter(
            stateManager, data, settings, systemMetrics(), FoldHandover(), FakeLicense(), mock(),
            TestScope(dispatcher),
        )
        advanceUntilIdle()
        // A free license and no tap score: the default alone is offered.
        assertEquals(listOf(com.twinlore.app.data.AppIcon.DEFAULT), presenter.state.value.selectableIcons)

        presenter.setAppIcon(com.twinlore.app.data.AppIcon.NOIR)
        advanceUntilIdle()
        verify(settings, org.mockito.kotlin.never()).setSelectedAppIcon(com.twinlore.app.data.AppIcon.NOIR)
        assertEquals(com.twinlore.app.data.AppIcon.DEFAULT, presenter.state.value.appIcon)
    }

    @Test
    fun `an earned icon is written, and the font is the app's own setting`() = runTest(dispatcher) {
        val settings = foldSettings()
        whenever(settings.checklistTapGameHighScore).thenReturn(flowOf(com.twinlore.app.data.PIXEL_UNLOCK_TAP_SCORE))
        val presenter = FoldGuidePanelPresenter(
            stateManager, data, settings, systemMetrics(), FoldHandover(), FakeLicense(), mock(),
            TestScope(dispatcher),
        )
        advanceUntilIdle()

        presenter.setAppIcon(com.twinlore.app.data.AppIcon.PIXEL)
        presenter.setAppFont(com.twinlore.app.data.AppFont.PIXEL)
        advanceUntilIdle()
        verify(settings).setSelectedAppIcon(com.twinlore.app.data.AppIcon.PIXEL)
        verify(settings).setAppFont(com.twinlore.app.data.AppFont.PIXEL)
        assertEquals(com.twinlore.app.data.AppFont.PIXEL, presenter.state.value.appFont)
    }

    // ----- A game started behind the panel -----

    // The app follows a launch on its own resume, which never comes while the
    // panel is up. So the panel asks, and asking is the point: somebody
    // reading a boss strategy has not asked to lose their page.
    @Test
    fun `a game started behind the panel is offered, not taken`() = runTest(dispatcher) {
        data.guide = tab()
        data.detected = FoldDetectedGame("vault-2", "Chrono Trigger", "marker-1")
        val presenter = presenter()
        stateManager.setState(AppState.VaultSession(VAULT, VaultTab.Guide))
        presenter.onPanelOpened()
        advanceUntilIdle()

        assertEquals(FoldDetectedGame("vault-2", "Chrono Trigger", "marker-1"), presenter.state.value.detectedGame)
        assertEquals("nothing switches until asked", VAULT, presenter.state.value.let { stateVaultId() })
        assertTrue("and nothing is settled until answered", data.handledMarkers.isEmpty())
    }

    @Test
    fun `switching follows the game and moves the app with it`() = runTest(dispatcher) {
        data.guide = tab()
        data.detected = FoldDetectedGame("vault-2", "Chrono Trigger", "marker-1")
        val presenter = presenter()
        stateManager.setState(AppState.VaultSession(VAULT, VaultTab.Guide))
        presenter.onPanelOpened()
        advanceUntilIdle()

        presenter.switchToDetectedGame()
        advanceUntilIdle()

        assertNull(presenter.state.value.detectedGame)
        assertEquals("vault-2", stateVaultId())
        // So Return lands on the game now being played.
        assertEquals(listOf("marker-1"), data.handledMarkers)
        // And the library's last-opened order moves with it.
        assertEquals(listOf("vault-2"), data.opened)
    }

    // Either answer settles the launch: it was asked, and answered, so the app
    // does not ask again about the same one.
    @Test
    fun `staying keeps the page and still settles the launch`() = runTest(dispatcher) {
        data.guide = tab()
        data.detected = FoldDetectedGame("vault-2", "Chrono Trigger", "marker-1")
        val presenter = presenter()
        stateManager.setState(AppState.VaultSession(VAULT, VaultTab.Guide))
        presenter.onPanelOpened()
        advanceUntilIdle()

        presenter.dismissDetectedGame()
        advanceUntilIdle()

        assertNull(presenter.state.value.detectedGame)
        assertEquals(VAULT, stateVaultId())
        assertEquals(listOf("marker-1"), data.handledMarkers)
    }

    // The ask rides on the offer only while there is something to ask for.
    // Unknown shows nothing: a button that appears and vanishes as the license
    // record loads is worse than one that arrives late.
    @Test
    fun `the support emblem is offered only while unlicensed`() = runTest(dispatcher) {
        val free = FoldGuidePanelPresenter(
            stateManager, data, foldSettings(), systemMetrics(), FoldHandover(), FakeLicense(), mock(),
            TestScope(dispatcher),
        )
        advanceUntilIdle()
        assertEquals(com.twinlore.app.data.AppIcon.DEFAULT, free.state.value.supportEmblem)

        val licensed = FoldGuidePanelPresenter(
            stateManager, data, foldSettings(), systemMetrics(), FoldHandover(),
            FakeLicense(com.twinlore.app.data.license.LicenseUiState.Licensed("x")), mock(),
            TestScope(dispatcher),
        )
        advanceUntilIdle()
        assertNull(licensed.state.value.supportEmblem)

        val unknown = FoldGuidePanelPresenter(
            stateManager, data, foldSettings(), systemMetrics(), FoldHandover(),
            FakeLicense(com.twinlore.app.data.license.LicenseUiState.Unknown), mock(),
            TestScope(dispatcher),
        )
        advanceUntilIdle()
        assertNull(unknown.state.value.supportEmblem)
    }

    // The dialog is the app's to show, so the button leaves a note and the
    // offer stands: coming back to it is a decision about the game.
    @Test
    fun `support leaves the note the app reads on arrival and keeps the offer`() = runTest(dispatcher) {
        data.guide = tab()
        data.detected = FoldDetectedGame("vault-2", "Chrono Trigger", "marker-1")
        val handover = FoldHandover()
        val presenter = FoldGuidePanelPresenter(
            stateManager, data, foldSettings(), systemMetrics(), handover, FakeLicense(), mock(),
            TestScope(dispatcher),
        )
        stateManager.setState(AppState.VaultSession(VAULT, VaultTab.Guide))
        presenter.onPanelOpened()
        advanceUntilIdle()

        presenter.armSupportRequest()

        assertTrue(handover.consumeSupport())
        assertFalse("once", handover.consumeSupport())
        assertNotNull(presenter.state.value.detectedGame)
    }

    // The common case: the game behind the panel is the one the panel is on.
    @Test
    fun `the game already showing is never offered`() = runTest(dispatcher) {
        data.guide = tab()
        data.detected = FoldDetectedGame(VAULT, "Same Game", "marker-1")
        val presenter = presenter()
        stateManager.setState(AppState.VaultSession(VAULT, VaultTab.Guide))
        presenter.onPanelOpened()
        advanceUntilIdle()

        assertNull(presenter.state.value.detectedGame)
    }

    // Detection is asked about the vault the panel is actually on, so it can
    // answer "that is the one you are reading" for itself.
    @Test
    fun `detection is asked after the panel has something to show`() = runTest(dispatcher) {
        data.guide = tab()
        val presenter = presenter()
        stateManager.setState(AppState.VaultSession(VAULT, VaultTab.Guide))
        presenter.onPanelOpened()
        advanceUntilIdle()

        assertEquals(listOf(VAULT), data.detectedFor)
    }

    // ----- The bars -----

    // A layer, never a surface: it flips, and a fresh open puts it away, so a
    // Settings is a surface now, not a layer, and it is the one surface the
    // panel refuses to reopen on: it is somewhere you go and come back from,
    // not somewhere you were reading.
    @Test
    fun `settings is a surface and is never the one a panel reopens on`() = runTest(dispatcher) {
        data.guide = tab()
        val presenter = presenter()
        stateManager.setState(AppState.VaultSession(VAULT, VaultTab.Guide))
        presenter.onPanelOpened()
        advanceUntilIdle()
        assertEquals(FoldPanelSurface.GUIDE, presenter.state.value.surface)

        presenter.selectSurface(FoldPanelSurface.IMAGE)
        advanceUntilIdle()
        presenter.selectSurface(FoldPanelSurface.SETTINGS)
        advanceUntilIdle()
        assertEquals(FoldPanelSurface.SETTINGS, presenter.state.value.surface)

        presenter.onPanelClosed()
        presenter.onPanelOpened()
        advanceUntilIdle()
        assertEquals(
            "the panel comes back to the image, not to the settings over it",
            FoldPanelSurface.IMAGE,
            presenter.state.value.surface,
        )
    }

    // A held handle asks before it closes anything.
    @Test
    fun `the close question is raised and can be waved off`() = runTest(dispatcher) {
        data.guide = tab()
        val presenter = presenter()
        stateManager.setState(AppState.VaultSession(VAULT, VaultTab.Guide))
        presenter.onPanelOpened()
        advanceUntilIdle()
        assertFalse(presenter.state.value.closePrompt)

        presenter.askCloseApp()
        assertTrue(presenter.state.value.closePrompt)

        presenter.dismissCloseApp()
        assertFalse(presenter.state.value.closePrompt)
    }

    // The hold's Swap stays in the mode. A panel that only came up to ask
    // folds again on the other screen; one that was already open stays open.
    @Test
    fun `swap from the hold folds the panel again only if the hold opened it`() = runTest(dispatcher) {
        val presenter = presenter()
        var folds = 0

        presenter.askCloseApp(openedPanel = true)
        presenter.swapFromCloseApp(swap = { true }, fold = { folds++ })
        assertFalse(presenter.state.value.closePrompt)
        assertEquals(1, folds)

        presenter.askCloseApp(openedPanel = false)
        presenter.swapFromCloseApp(swap = { true }, fold = { folds++ })
        assertEquals(1, folds)

        // A swap that did not happen leaves the panel where it is.
        presenter.askCloseApp(openedPanel = true)
        presenter.swapFromCloseApp(swap = { false }, fold = { folds++ })
        assertEquals(1, folds)
    }

    // The app's own rule, written to the app's own setting: Standard and
    // Performance trade places, so the two bars change together.
    @Test
    fun `tapping the status cluster trades standard for performance and writes it`() = runTest(dispatcher) {
        val settings = foldSettings()
        val presenter = FoldGuidePanelPresenter(
            stateManager, data, settings, systemMetrics(), FoldHandover(), FakeLicense(), mock(),
            TestScope(dispatcher),
        )
        advanceUntilIdle()
        assertEquals(com.twinlore.app.data.StatusBarMode.STANDARD, presenter.state.value.statusBarMode)

        presenter.tapToggleStatusBarMode()
        advanceUntilIdle()

        assertEquals(com.twinlore.app.data.StatusBarMode.PERFORMANCE, presenter.state.value.statusBarMode)
        verify(settings).setStatusBarMode(com.twinlore.app.data.StatusBarMode.PERFORMANCE)
    }

    // Seen until the store says otherwise, so a panel that has seen the card
    // never flashes it; unseen shows it, and the button is what writes it.
    @Test
    fun `the intro shows until dismissed, and dismissing writes it`() = runTest(dispatcher) {
        val settings = foldSettings()
        whenever(settings.foldPanelIntroSeen).thenReturn(flowOf(false))
        val presenter = FoldGuidePanelPresenter(
            stateManager, data, settings, systemMetrics(), FoldHandover(), FakeLicense(), mock(),
            TestScope(dispatcher),
        )
        assertTrue("seen until the store answers", presenter.state.value.introSeen)
        advanceUntilIdle()
        assertFalse(presenter.state.value.introSeen)

        presenter.dismissIntro()
        advanceUntilIdle()
        assertTrue(presenter.state.value.introSeen)
        verify(settings).setFoldPanelIntroSeen(true)
    }

    // Null, not an empty snapshot: an empty one draws five dashes, which reads
    // as five broken readouts rather than as a cluster that is switched off.
    @Test
    fun `no meters unless the cluster is in performance mode`() = runTest(dispatcher) {
        val presenter = presenter()
        advanceUntilIdle()
        assertNull(presenter.state.value.metrics)
    }

    // ----- Achievements -----

    // Cached, never fetched. The panel is up while a game is running and the
    // process is already under memory pressure from it; a surface that phoned
    // home on open would be the one thing nobody asked for.
    @Test
    fun `opening loads the cached achievements and refreshes nothing`() = runTest(dispatcher) {
        data.guide = tab()
        data.cachedAwards = FoldAwards(
            achievements = listOf(achievement(1), achievement(2)),
            total = 2,
            unlocked = 1,
            linked = true,
        )
        val presenter = presenter()
        stateManager.setState(AppState.VaultSession(VAULT, VaultTab.Guide))
        presenter.onPanelOpened()
        advanceUntilIdle()

        assertEquals(2, presenter.state.value.awards.achievements.size)
        assertFalse(presenter.state.value.awardsLoading)
        assertTrue("open must not refresh", data.refreshedVaults.isEmpty())
    }

    @Test
    fun `refresh replaces the list and clears the spinner`() = runTest(dispatcher) {
        data.guide = tab()
        data.cachedAwards = FoldAwards(achievements = listOf(achievement(1)), linked = true)
        data.freshAwards = FoldAwards(
            achievements = listOf(achievement(1), achievement(2)),
            linked = true,
        )
        val presenter = presenter()
        stateManager.setState(AppState.VaultSession(VAULT, VaultTab.Guide))
        presenter.onPanelOpened()
        advanceUntilIdle()

        presenter.refreshAwards()
        advanceUntilIdle()
        assertEquals(listOf(VAULT), data.refreshedVaults)
        assertEquals(2, presenter.state.value.awards.achievements.size)
        assertFalse(presenter.state.value.awardsRefreshing)
    }

    // A panel opened onto the achievements is opened to read them, so they are
    // made true the way arriving at them does: once, and after the cached read,
    // which would otherwise land on top of the fresh list.
    @Test
    fun `opening onto the achievements refreshes them once`() = runTest(dispatcher) {
        data.guide = tab()
        data.cachedAwards = FoldAwards(achievements = listOf(achievement(1)), linked = true)
        data.freshAwards = FoldAwards(achievements = listOf(achievement(1), achievement(2)), linked = true)
        val settings = foldSettings()
        whenever(settings.foldPanelSurface).thenReturn(flowOf(FoldPanelSurface.AWARDS.name))
        val presenter = FoldGuidePanelPresenter(
            stateManager, data, settings, systemMetrics(), FoldHandover(), FakeLicense(), mock(),
            TestScope(dispatcher),
        )
        advanceUntilIdle()
        stateManager.setState(AppState.VaultSession(VAULT, VaultTab.Guide))
        presenter.onPanelOpened()
        advanceUntilIdle()

        assertEquals(listOf(VAULT), data.refreshedVaults)
        assertEquals("the cache must not land over the fresh list", 2, presenter.state.value.awards.achievements.size)

        // Leaving and coming back in the same open is not a second fetch.
        presenter.selectSurface(FoldPanelSurface.GUIDE)
        presenter.selectSurface(FoldPanelSurface.AWARDS)
        advanceUntilIdle()
        assertEquals(listOf(VAULT), data.refreshedVaults)
    }

    @Test
    fun `reopening onto the achievements refreshes again`() = runTest(dispatcher) {
        data.guide = tab()
        data.cachedAwards = FoldAwards(achievements = listOf(achievement(1)), linked = true)
        data.freshAwards = data.cachedAwards
        val settings = foldSettings()
        whenever(settings.foldPanelSurface).thenReturn(flowOf(FoldPanelSurface.AWARDS.name))
        val presenter = FoldGuidePanelPresenter(
            stateManager, data, settings, systemMetrics(), FoldHandover(), FakeLicense(), mock(),
            TestScope(dispatcher),
        )
        advanceUntilIdle()
        stateManager.setState(AppState.VaultSession(VAULT, VaultTab.Guide))
        presenter.onPanelOpened()
        advanceUntilIdle()
        presenter.onPanelClosed()
        presenter.onPanelResumed()
        advanceUntilIdle()

        assertEquals(listOf(VAULT, VAULT), data.refreshedVaults)
    }

    // The panel is over a running game and the achievements it already has are
    // still true. Blanking them to report a network problem trades something
    // useful for something that is not.
    @Test
    fun `a failed refresh leaves the list alone`() = runTest(dispatcher) {
        data.guide = tab()
        data.cachedAwards = FoldAwards(achievements = listOf(achievement(1)), linked = true)
        data.freshAwards = null
        val presenter = presenter()
        stateManager.setState(AppState.VaultSession(VAULT, VaultTab.Guide))
        presenter.onPanelOpened()
        advanceUntilIdle()

        presenter.refreshAwards()
        advanceUntilIdle()
        assertEquals(1, presenter.state.value.awards.achievements.size)
        assertFalse(presenter.state.value.awardsRefreshing)
    }

    @Test
    fun `comments load for the achievement that was tapped`() = runTest(dispatcher) {
        data.comments = listOf(FoldComment("someone", "hold left on the title screen", "2026"))
        val presenter = presenter()
        presenter.showComments(achievement(7))
        advanceUntilIdle()

        assertEquals(listOf(7), data.commentedOn)
        assertEquals(7, presenter.state.value.commentsFor?.id)
        assertEquals(1, presenter.state.value.comments.size)
        assertFalse(presenter.state.value.commentsLoading)
    }

    // A fast tap through two achievements must not land the first one's
    // comments under the second one's title.
    @Test
    fun `a superseded comment load does not land`() = runTest(dispatcher) {
        val presenter = presenter()
        presenter.showComments(achievement(7))
        presenter.showComments(achievement(9))
        advanceUntilIdle()

        assertEquals(9, presenter.state.value.commentsFor?.id)
    }

    @Test
    fun `leaving the surface closes an open thread`() = runTest(dispatcher) {
        val presenter = presenter()
        presenter.showComments(achievement(7))
        advanceUntilIdle()
        assertNotNull(presenter.state.value.commentsFor)

        presenter.selectSurface(FoldPanelSurface.GUIDE)
        assertNull(presenter.state.value.commentsFor)
    }

    // Coming back to the surface must not reopen whatever was last read, but
    // selecting the surface it is already on must not close it either.
    @Test
    fun `staying on the surface keeps the thread open`() = runTest(dispatcher) {
        val presenter = presenter()
        presenter.showComments(achievement(7))
        advanceUntilIdle()

        presenter.selectSurface(FoldPanelSurface.AWARDS)
        assertNotNull(presenter.state.value.commentsFor)
    }

    // App from the achievements surface lands on the achievements tab, the way
    // it lands on the map tab from the images.
    @Test
    fun `the app opens on the achievements tab from the achievements surface`() = runTest(dispatcher) {
        data.guide = tab()
        val presenter = presenter()
        stateManager.setState(AppState.VaultSession(VAULT, VaultTab.Guide))
        presenter.onPanelOpened()
        advanceUntilIdle()

        presenter.selectSurface(FoldPanelSurface.AWARDS)
        presenter.openAppOnCurrentGuide {}
        advanceUntilIdle()
        assertEquals(
            VaultTab.Achievements,
            (stateManager.currentState.value as AppState.VaultSession).activeTab,
        )
    }

    @Test
    fun `the sort cycles and each field lands on its own default direction`() = runTest(dispatcher) {
        val presenter = presenter()
        val first = presenter.state.value.awardSortField
        presenter.cycleAwardSort()
        val second = presenter.state.value.awardSortField
        assertTrue("the field must move", first != second)
        assertEquals(second.defaultDirection, presenter.state.value.awardSortDirection)

        presenter.toggleAwardSortDirection()
        assertTrue(presenter.state.value.awardSortDirection != second.defaultDirection)
    }

    // Steam has its own sort fields (no points, no display order, no type), so
    // on a Steam list the pill walks those and leaves the RetroAchievements
    // choice where the user put it.
    @Test
    fun `on a steam list the sort cycles steam's fields and leaves the ra choice alone`() = runTest(dispatcher) {
        data.cachedAwards = FoldAwards(source = FoldAwardSource.STEAM, linked = true)
        val presenter = presenter()
        stateManager.setState(AppState.VaultSession(VAULT, VaultTab.Guide))
        presenter.onPanelOpened()
        advanceUntilIdle()
        val raField = presenter.state.value.awardSortField
        val raDirection = presenter.state.value.awardSortDirection

        val first = presenter.state.value.steamAwardSortField
        presenter.cycleAwardSort()
        val second = presenter.state.value.steamAwardSortField
        assertTrue("the steam field must move", first != second)
        assertEquals(second.defaultDirection, presenter.state.value.steamAwardSortDirection)
        presenter.toggleAwardSortDirection()
        assertTrue(presenter.state.value.steamAwardSortDirection != second.defaultDirection)

        assertEquals(raField, presenter.state.value.awardSortField)
        assertEquals(raDirection, presenter.state.value.awardSortDirection)
    }

    // ----- Screenshots -----

    // The shot goes into the vault through the same two writes the app's own
    // screenshot path makes, and the panel then shows it: somebody who just
    // took one is looking for it, not for where they were.
    @Test
    fun `a landed screenshot is filed and becomes the shown image`() = runTest(dispatcher) {
        data.guide = tab()
        data.images = listOf(image("a"), image("b"))
        data.savedScreenshot = FoldImage("shot", "2026-09-03 Screenshot", "file:///shot.png")
        val presenter = presenter()
        stateManager.setState(AppState.VaultSession(VAULT, VaultTab.Guide))
        presenter.onPanelOpened()
        advanceUntilIdle()

        presenter.onScreenshotTaken(java.io.File("/tmp/shot.png"))
        advanceUntilIdle()

        assertEquals(listOf("shot.png"), data.screenshotsFiled)
        assertEquals(3, presenter.state.value.images.size)
        assertEquals(2, presenter.state.value.imageIndex)
        assertEquals(FoldPanelSurface.IMAGE, presenter.state.value.surface)
        assertFalse(presenter.state.value.capturing)
        // The app's own screenshot path selects the new shot; so does this one.
        assertEquals("shot", data.currentImageId)
    }

    // A failed file leaves the list alone and, more importantly, clears the
    // spinner: a shutter that stays disabled is a surface nobody can use again.
    @Test
    fun `a screenshot that cannot be filed clears the spinner and adds nothing`() =
        runTest(dispatcher) {
            data.guide = tab()
            data.images = listOf(image("a"))
            data.savedScreenshot = null
            val presenter = presenter()
            stateManager.setState(AppState.VaultSession(VAULT, VaultTab.Guide))
            presenter.onPanelOpened()
            advanceUntilIdle()

            presenter.onScreenshotTaken(java.io.File("/tmp/shot.png"))
            advanceUntilIdle()

            assertEquals(1, presenter.state.value.images.size)
            assertFalse(presenter.state.value.capturing)
        }

    // Nothing to capture into, so nothing is asked for. Reaching the consent
    // sheet here would ask for a permission whose result had nowhere to go.
    @Test
    fun `no vault means no capture`() = runTest(dispatcher) {
        val presenter = presenter()
        presenter.takeScreenshot(0)
        advanceUntilIdle()
        assertFalse(presenter.state.value.capturing)
    }

    // ----- Deleting an image -----

    // The bin asks first. Nothing leaves the list, or the store, on the tap.
    @Test
    fun `the bin asks before anything goes`() = runTest(dispatcher) {
        data.guide = tab()
        data.images = listOf(image("a"), image("b"))
        val presenter = presenter()
        inVault()
        presenter.onPanelOpened()
        advanceUntilIdle()

        presenter.askDeleteImage()
        assertTrue(presenter.state.value.deletePrompt)
        assertEquals(2, presenter.state.value.images.size)
        assertTrue(data.deleted.isEmpty())

        presenter.dismissDeleteImage()
        advanceUntilIdle()
        assertFalse(presenter.state.value.deletePrompt)
        assertEquals(2, presenter.state.value.images.size)
        assertTrue(data.deleted.isEmpty())
    }

    // Yes removes the shown image and lands on the one after it, and the app's
    // own selection is moved with it so its Image tab opens on the same picture.
    @Test
    fun `deleting lands on the next image and moves the app's selection`() = runTest(dispatcher) {
        data.guide = tab()
        data.images = listOf(image("a"), image("b"), image("c"))
        data.storedViews["b"] = FoldImageView(2f, 5f, 5f)
        val presenter = presenter()
        inVault()
        presenter.onPanelOpened()
        advanceUntilIdle()

        presenter.askDeleteImage()
        presenter.confirmDeleteImage()
        assertFalse(presenter.state.value.deletePrompt)
        assertEquals(listOf("b", "c"), presenter.state.value.images.map { it.id })
        assertEquals(0, presenter.state.value.imageIndex)
        assertNull("the next image must not open at the deleted one's pan", presenter.state.value.imageView)
        advanceUntilIdle()

        assertEquals(listOf("a"), data.deleted)
        assertEquals("b", data.currentImageId)
        assertEquals(2f, presenter.state.value.imageView?.zoom)
    }

    // The last in the list has nothing after it, so the one before it is shown.
    @Test
    fun `deleting the last image steps back`() = runTest(dispatcher) {
        data.guide = tab()
        data.images = listOf(image("a"), image("b"))
        data.currentImageId = "b"
        val presenter = presenter()
        inVault()
        presenter.onPanelOpened()
        advanceUntilIdle()
        assertEquals(1, presenter.state.value.imageIndex)

        presenter.askDeleteImage()
        presenter.confirmDeleteImage()
        advanceUntilIdle()

        assertEquals(listOf("a"), presenter.state.value.images.map { it.id })
        assertEquals(0, presenter.state.value.imageIndex)
        assertEquals("a", data.currentImageId)
    }

    // The only image going leaves the empty surface, and no selection is
    // written for an image that does not exist.
    @Test
    fun `deleting the only image leaves the surface empty`() = runTest(dispatcher) {
        data.guide = tab()
        data.images = listOf(image("a"))
        val presenter = presenter()
        inVault()
        presenter.onPanelOpened()
        advanceUntilIdle()
        val touchedBefore = data.touched.size

        presenter.askDeleteImage()
        presenter.confirmDeleteImage()
        advanceUntilIdle()

        assertTrue(presenter.state.value.images.isEmpty())
        assertNull(presenter.state.value.currentImage)
        assertEquals(listOf("a"), data.deleted)
        assertEquals(touchedBefore, data.touched.size)
    }

    // No image, no question: the empty surface has no bin, and a stray call
    // must not put a prompt over it.
    @Test
    fun `nothing to delete means no question`() = runTest(dispatcher) {
        data.guide = tab()
        val presenter = presenter()
        inVault()
        presenter.onPanelOpened()
        advanceUntilIdle()

        presenter.askDeleteImage()
        assertFalse(presenter.state.value.deletePrompt)
    }

    // A pan made on the image just deleted must not be written to disk after
    // it is gone: the debounce that carries it is dropped with the image.
    @Test
    fun `a pan waiting for the deleted image is dropped`() = runTest(dispatcher) {
        data.guide = tab()
        data.images = listOf(image("a"), image("b"))
        val presenter = presenter()
        inVault()
        presenter.onPanelOpened()
        advanceUntilIdle()

        presenter.onImageViewChanged(FoldImageView(4f, 1f, 1f))
        presenter.askDeleteImage()
        presenter.confirmDeleteImage()
        advanceUntilIdle()

        assertTrue(data.viewWrites.none { it.first == "a" })
    }

    // A question asked about one vault's image must not be answered on
    // another's: the switch to a detected game takes the question with it.
    @Test
    fun `switching vaults drops an open delete question`() = runTest(dispatcher) {
        data.guide = tab()
        data.images = listOf(image("a"))
        val presenter = presenter()
        inVault()
        presenter.onPanelOpened()
        advanceUntilIdle()
        presenter.askDeleteImage()
        assertTrue(presenter.state.value.deletePrompt)

        // The way a vault changes under a panel: it closes, the app moves on,
        // and the next open finds a different vault.
        presenter.onPanelClosed()
        switchVault("other")
        presenter.onPanelOpened()

        assertFalse("must be gone at once, before anything loads", presenter.state.value.deletePrompt)
        advanceUntilIdle()
        assertFalse(presenter.state.value.deletePrompt)
    }

    // ----- Image position -----

    // The app's Image tab reads and writes the same manager, so a map panned in
    // one opens where it was left in the other, both ways round.
    @Test
    fun `the shown image opens where it was left`() = runTest(dispatcher) {
        data.guide = tab()
        data.images = listOf(image("a"), image("b"))
        data.storedViews["a"] = FoldImageView(zoom = 2.5f, offsetX = 40f, offsetY = -10f)
        val presenter = presenter()
        stateManager.setState(AppState.VaultSession(VAULT, VaultTab.Guide))
        presenter.onPanelOpened()
        advanceUntilIdle()

        assertEquals(2.5f, presenter.state.value.imageView?.zoom)
    }

    // Stepping must not leave the previous image's pan applied to the next one,
    // which would open a fresh map already scrolled somewhere arbitrary.
    @Test
    fun `stepping clears the pan before the next one's is read`() = runTest(dispatcher) {
        data.guide = tab()
        data.images = listOf(image("a"), image("b"))
        data.storedViews["a"] = FoldImageView(3f, 10f, 10f)
        val presenter = presenter()
        stateManager.setState(AppState.VaultSession(VAULT, VaultTab.Guide))
        presenter.onPanelOpened()
        advanceUntilIdle()

        presenter.stepImage(1)
        assertNull("b has no stored view, so it must open centred", presenter.state.value.imageView)
        advanceUntilIdle()
        assertNull(presenter.state.value.imageView)
    }

    @Test
    fun `panning is written back for the image being shown`() = runTest(dispatcher) {
        data.guide = tab()
        data.images = listOf(image("a"))
        val presenter = presenter()
        stateManager.setState(AppState.VaultSession(VAULT, VaultTab.Guide))
        presenter.onPanelOpened()
        advanceUntilIdle()

        presenter.onImageViewChanged(FoldImageView(1.5f, 5f, 6f))
        advanceUntilIdle()
        assertEquals(1, data.viewWrites.size)
        assertEquals("a", data.viewWrites.first().first)
        assertEquals(1.5f, presenter.state.value.imageView?.zoom)
    }

    // ----- Which surface a panel opens on -----

    // Folding to glance at the game and unfolding is the most common thing that
    // happens to this window, and it was landing on the guide every time no
    // matter what you had open.
    @Test
    fun `a new panel opens on the surface the last one was left on`() = runTest(dispatcher) {
        val settings = foldSettings()
        whenever(settings.foldPanelSurface).thenReturn(flowOf(FoldPanelSurface.AWARDS.name))
        val presenter = FoldGuidePanelPresenter(
            stateManager, data, settings, systemMetrics(), FoldHandover(), FakeLicense(), mock(),
            TestScope(dispatcher),
        )
        advanceUntilIdle()
        stateManager.setState(AppState.VaultSession(VAULT, VaultTab.Guide))
        presenter.onPanelOpened()
        advanceUntilIdle()

        assertEquals(FoldPanelSurface.AWARDS, presenter.state.value.surface)
    }

    @Test
    fun `picking a surface is remembered for the next panel`() = runTest(dispatcher) {
        val settings = foldSettings()
        val presenter = FoldGuidePanelPresenter(
            stateManager, data, settings, systemMetrics(), FoldHandover(), FakeLicense(), mock(),
            TestScope(dispatcher),
        )
        advanceUntilIdle()

        presenter.selectSurface(FoldPanelSurface.IMAGE)
        advanceUntilIdle()
        verify(settings).setFoldPanelSurface(FoldPanelSurface.IMAGE.name)
    }

    // A name that no longer matches any case - an enum that lost one, or a
    // store written by an older build - falls back rather than throwing.
    @Test
    fun `an unknown stored surface falls back to the guide`() = runTest(dispatcher) {
        val settings = foldSettings()
        whenever(settings.foldPanelSurface).thenReturn(flowOf("CHECKLIST"))
        val presenter = FoldGuidePanelPresenter(
            stateManager, data, settings, systemMetrics(), FoldHandover(), FakeLicense(), mock(),
            TestScope(dispatcher),
        )
        advanceUntilIdle()
        stateManager.setState(AppState.VaultSession(VAULT, VaultTab.Guide))
        presenter.onPanelOpened()
        advanceUntilIdle()

        assertEquals(FoldPanelSurface.GUIDE, presenter.state.value.surface)
    }

    // ----- The achievement list's own state -----

    // The drawer keeps these in its view model on purpose: a filter is about
    // the question being asked right now. The panel cannot follow that rule,
    // because its "right now" ends at every fold - re-narrowing a
    // 200-achievement list on every unfold is not the same act as re-narrowing
    // it on every visit to a tab.
    @Test
    fun `the list's narrowing and sort survive a fold`() = runTest(dispatcher) {
        val settings = foldSettings()
        whenever(settings.foldPanelAwardSort).thenReturn(flowOf(RaAchievementSortField.POINTS.name))
        whenever(settings.foldPanelAwardSortDirection).thenReturn(flowOf(SortDirection.ASC.name))
        whenever(settings.foldPanelAwardTypeFilter)
            .thenReturn(flowOf(RaAchievementTypeFilter.MISSABLE.name))
        whenever(settings.foldPanelAwardLockedOnly).thenReturn(flowOf(true))
        whenever(settings.foldPanelAwardScroll).thenReturn(flowOf(12 to 340))
        val presenter = FoldGuidePanelPresenter(
            stateManager, data, settings, systemMetrics(), FoldHandover(), FakeLicense(), mock(),
            TestScope(dispatcher),
        )
        advanceUntilIdle()

        val state = presenter.state.value
        assertEquals(RaAchievementSortField.POINTS, state.awardSortField)
        assertEquals(SortDirection.ASC, state.awardSortDirection)
        assertEquals(RaAchievementTypeFilter.MISSABLE, state.awardTypeFilter)
        assertTrue(state.awardsLockedOnly)
        assertEquals(12 to 340, state.awardScroll)
    }

    // Null until the store answers, never a zero standing in for one.
    //
    // The list restores its position once, on the first frame that has rows.
    // A zero that means "not loaded yet" is indistinguishable from a zero that
    // means "the top", so the list would mark itself restored against the
    // placeholder and ignore the real position when it arrived a moment later.
    @Test
    fun `no scroll position is reported before the store has answered`() = runTest(dispatcher) {
        val settings = foldSettings()
        whenever(settings.foldPanelAwardScroll)
            .thenReturn(kotlinx.coroutines.flow.emptyFlow())
        val presenter = FoldGuidePanelPresenter(
            stateManager, data, settings, systemMetrics(), FoldHandover(), FakeLicense(), mock(),
            TestScope(dispatcher),
        )
        advanceUntilIdle()

        assertNull(presenter.state.value.awardScroll)
    }

    @Test
    fun `changing a filter writes it`() = runTest(dispatcher) {
        val settings = foldSettings()
        val presenter = FoldGuidePanelPresenter(
            stateManager, data, settings, systemMetrics(), FoldHandover(), FakeLicense(), mock(),
            TestScope(dispatcher),
        )
        advanceUntilIdle()

        presenter.toggleAwardsLockedOnly()
        presenter.cycleAwardTypeFilter()
        presenter.setAwardScroll(4, 20)
        advanceUntilIdle()

        verify(settings).setFoldPanelAwardLockedOnly(true)
        verify(settings).setFoldPanelAwardTypeFilter(RaAchievementTypeFilter.ALL.next().name)
        verify(settings).setFoldPanelAwardScroll(4, 20)
    }

    // The field and its direction are one write: landing on a new field at its
    // own default means both change together, and two writes would let a reader
    // observe the new field against the old field's direction.
    @Test
    fun `cycling the sort writes the field and its default direction together`() =
        runTest(dispatcher) {
            val settings = foldSettings()
            val presenter = FoldGuidePanelPresenter(
                stateManager, data, settings, systemMetrics(), FoldHandover(), FakeLicense(), mock(),
                TestScope(dispatcher),
            )
            advanceUntilIdle()

            presenter.cycleAwardSort()
            advanceUntilIdle()
            val field = presenter.state.value.awardSortField
            verify(settings).setFoldPanelAwardSort(field.name, field.defaultDirection.name)
        }

    // A name no longer in the enum falls back rather than resolving to whatever
    // now sits at that ordinal.
    @Test
    fun `unknown stored sort and filter names fall back`() = runTest(dispatcher) {
        val settings = foldSettings()
        whenever(settings.foldPanelAwardSort).thenReturn(flowOf("RARITY"))
        whenever(settings.foldPanelAwardTypeFilter).thenReturn(flowOf("SECRET"))
        val presenter = FoldGuidePanelPresenter(
            stateManager, data, settings, systemMetrics(), FoldHandover(), FakeLicense(), mock(),
            TestScope(dispatcher),
        )
        advanceUntilIdle()

        assertEquals(RaAchievementSortField.UNLOCK_TIME, presenter.state.value.awardSortField)
        assertEquals(RaAchievementTypeFilter.ALL, presenter.state.value.awardTypeFilter)
    }

    // ----- What a review found -----

    // A fresh capture must not inherit the pan of whatever was on screen
    // before it, the same rule stepping already follows.
    @Test
    fun `a captured screenshot opens unpanned`() = runTest(dispatcher) {
        data.guide = tab()
        data.images = listOf(image("a"))
        data.storedViews["a"] = FoldImageView(4f, 90f, 90f)
        data.savedScreenshot = FoldImage("shot", "Screenshot", "file:///shot.png")
        val presenter = presenter()
        stateManager.setState(AppState.VaultSession(VAULT, VaultTab.Guide))
        presenter.onPanelOpened()
        advanceUntilIdle()
        assertEquals(4f, presenter.state.value.imageView?.zoom)

        presenter.onScreenshotTaken(java.io.File("/tmp/shot.png"))
        advanceUntilIdle()
        assertNull(presenter.state.value.imageView)
    }

    // Awards load after the guide, so without a reset the achievements surface
    // - which a remembered surface can now open straight onto - showed the
    // previous vault's list for the whole of the guide read.
    @Test
    fun `opening on another vault does not show the last one's achievements`() =
        runTest(dispatcher) {
            data.guide = tab()
            data.cachedAwards = FoldAwards(achievements = listOf(achievement(1)), linked = true)
            val presenter = presenter()
            stateManager.setState(AppState.VaultSession(VAULT, VaultTab.Guide))
            presenter.onPanelOpened()
            advanceUntilIdle()
            assertEquals(1, presenter.state.value.awards.achievements.size)

            presenter.showComments(achievement(1))
            advanceUntilIdle()
            presenter.onPanelClosed()

            data.cachedAwards = FoldAwards(linked = true)
            switchVault("vault-2")
            presenter.onPanelOpened()
            assertTrue("the old list must be gone at once", presenter.state.value.awards.isEmpty)
            assertNull("and so must an open thread", presenter.state.value.commentsFor)
        }

    // MapZoomableImage reports from inside detectTransformGestures, so this
    // arrives once per pointer sample. Undebounced it was a prefs commit per
    // frame of every pan, over a running game.
    @Test
    fun `a pan writes once, not once per sample`() = runTest(dispatcher) {
        data.guide = tab()
        data.images = listOf(image("a"))
        val presenter = presenter()
        stateManager.setState(AppState.VaultSession(VAULT, VaultTab.Guide))
        presenter.onPanelOpened()
        advanceUntilIdle()

        repeat(30) { i ->
            presenter.onImageViewChanged(FoldImageView(1f + i, i.toFloat(), 0f))
        }
        advanceUntilIdle()

        assertEquals(1, data.viewWrites.size)
        assertEquals(30f, data.viewWrites.single().second.zoom)
    }

    // ----- What the second review found -----

    // MapZoomableImage re-applies whatever it was handed as its initial values
    // on every finger lift. The state therefore has to carry the live pan at
    // once; only the disk write waits for the gesture to settle. Debouncing
    // the state too made every pan snap back on release and jump forward half
    // a second later.
    @Test
    fun `a pan is in state at once and on disk only after it settles`() = runTest(dispatcher) {
        data.guide = tab()
        data.images = listOf(image("a"))
        val presenter = presenter()
        stateManager.setState(AppState.VaultSession(VAULT, VaultTab.Guide))
        presenter.onPanelOpened()
        advanceUntilIdle()

        presenter.onImageViewChanged(FoldImageView(1.5f, 5f, 6f))
        assertEquals(1.5f, presenter.state.value.imageView?.zoom)
        assertTrue("the disk write waits for the pan to settle", data.viewWrites.isEmpty())
        advanceUntilIdle()
        assertEquals(1, data.viewWrites.size)
    }

    // A pending write must land before anything reads the same image back,
    // or the panel restores the pan from before the gesture it just made.
    @Test
    fun `stepping back within the settle window reads the pan just made`() = runTest(dispatcher) {
        data.guide = tab()
        data.images = listOf(image("a"), image("b"))
        val presenter = presenter()
        stateManager.setState(AppState.VaultSession(VAULT, VaultTab.Guide))
        presenter.onPanelOpened()
        advanceUntilIdle()

        val panned = FoldImageView(2f, 20f, 20f)
        presenter.onImageViewChanged(panned)
        presenter.stepImage(1)
        presenter.stepImage(-1)
        advanceUntilIdle()

        assertEquals("the disk must answer with the pan", panned, data.viewReads.last { it.first == "a" }.second)
        assertEquals(panned, presenter.state.value.imageView)
    }

    // A fetch started on one vault and finished on another must not replace
    // the new vault's list, and must not keep the new vault's refresh disabled
    // while it is on its way.
    @Test
    fun `a refresh that lands after the vault changed is dropped`() = runTest(dispatcher) {
        data.guide = tab()
        data.cachedAwards = FoldAwards(achievements = listOf(achievement(1)), gameTitle = "A", linked = true)
        data.freshAwards = FoldAwards(achievements = listOf(achievement(1), achievement(2)), gameTitle = "A", linked = true)
        data.refreshGate = kotlinx.coroutines.CompletableDeferred()
        val presenter = presenter()
        stateManager.setState(AppState.VaultSession(VAULT, VaultTab.Guide))
        presenter.onPanelOpened()
        advanceUntilIdle()

        presenter.refreshAwards()
        advanceUntilIdle()
        presenter.onPanelClosed()

        data.cachedAwards = FoldAwards(achievements = listOf(achievement(3)), gameTitle = "B", linked = true)
        switchVault("vault-2")
        presenter.onPanelOpened()
        advanceUntilIdle()
        assertFalse("the old fetch must not block a new one", presenter.state.value.awardsRefreshing)

        data.refreshGate!!.complete(Unit)
        advanceUntilIdle()
        assertEquals("B", presenter.state.value.awards.gameTitle)
    }

    // Fold, glance at the game, unfold: the same vault. Blanking the list and
    // spinning through the guide read on every one of those was the visible
    // cost of a reset written for the other case.
    @Test
    fun `reopening the same vault keeps what it had`() = runTest(dispatcher) {
        data.guide = tab()
        data.images = listOf(image("a"), image("b"))
        data.cachedAwards = FoldAwards(achievements = listOf(achievement(1)), linked = true)
        val presenter = presenter()
        stateManager.setState(AppState.VaultSession(VAULT, VaultTab.Guide))
        presenter.onPanelOpened()
        advanceUntilIdle()
        presenter.stepImage(1)
        advanceUntilIdle()

        presenter.onPanelClosed()
        presenter.onPanelOpened()
        assertEquals(1, presenter.state.value.awards.achievements.size)
        assertFalse(presenter.state.value.awardsLoading)
        assertEquals(1, presenter.state.value.imageIndex)
        advanceUntilIdle()
        assertEquals(1, presenter.state.value.imageIndex)
        assertEquals(1, presenter.state.value.awards.achievements.size)
    }

    // A fold now parks the panel instead of removing it, so its reader is still
    // composed and holding the page. Resuming must not put the guide through
    // null: that disposes the reader and reloads the page, which for a guide
    // the size of the Isaac one on the Thor was the whole cost of reopening.
    @Test
    fun `resuming a parked panel on the same vault leaves the guide alone`() = runTest(dispatcher) {
        data.guide = tab()
        val presenter = presenter()
        stateManager.setState(AppState.VaultSession(VAULT, VaultTab.Guide))
        presenter.onPanelOpened()
        advanceUntilIdle()
        val guide = presenter.state.value.guide
        assertNotNull(guide)

        presenter.onPanelClosed()
        data.asked.clear()
        presenter.onPanelResumed()
        assertTrue("never blanked, not even for a frame", presenter.state.value.guide === guide)
        advanceUntilIdle()
        assertTrue(presenter.state.value.guide === guide)
        assertTrue("nothing re-read", data.asked.isEmpty())
    }

    @Test
    fun `resuming a parked panel on another vault loads that vault`() = runTest(dispatcher) {
        data.guide = tab()
        val presenter = presenter()
        stateManager.setState(AppState.VaultSession(VAULT, VaultTab.Guide))
        presenter.onPanelOpened()
        advanceUntilIdle()
        presenter.onPanelClosed()

        switchVault("vault-2")
        data.asked.clear()
        presenter.onPanelResumed()
        advanceUntilIdle()
        assertEquals(listOf("vault-2"), data.asked)
    }

    // Folded before the guide had loaded: the park cancelled the load, so the
    // resume has to finish it rather than keep an empty panel.
    @Test
    fun `resuming after a fold that cut the load short finishes the load`() = runTest(dispatcher) {
        data.guide = tab()
        val presenter = presenter()
        stateManager.setState(AppState.VaultSession(VAULT, VaultTab.Guide))
        presenter.onPanelOpened()
        presenter.onPanelClosed()

        presenter.onPanelResumed()
        advanceUntilIdle()
        assertNotNull(presenter.state.value.guide)
        assertFalse(presenter.state.value.loading)
    }

    // The receiver is deliberately left up across a fold so a shot in flight
    // can still be filed. Filed where it was taken, not wherever the panel
    // happens to be when it lands.
    @Test
    fun `a screenshot is filed into the vault it was taken for`() = runTest(dispatcher) {
        data.guide = tab()
        data.images = listOf(image("a"))
        data.savedScreenshot = FoldImage("shot", "Screenshot", "file:///shot.png")
        val presenter = presenter()
        stateManager.setState(AppState.VaultSession(VAULT, VaultTab.Guide))
        presenter.onPanelOpened()
        advanceUntilIdle()

        presenter.takeScreenshot(0)
        assertTrue(presenter.state.value.capturing)
        presenter.onPanelClosed()

        switchVault("vault-2")
        presenter.onPanelOpened()
        advanceUntilIdle()
        assertFalse("another vault's shot must not disable this shutter", presenter.state.value.capturing)

        presenter.onScreenshotTaken(java.io.File("/tmp/shot.png"))
        advanceUntilIdle()
        assertEquals(listOf(VAULT), data.screenshotVaults)
        assertEquals("the other vault's list is untouched", 1, presenter.state.value.images.size)
        assertEquals(FoldPanelSurface.GUIDE, presenter.state.value.surface)
    }

    // A shot switches the panel to the image, and the next panel should open
    // there too, the same as if the surface had been picked from the bar.
    @Test
    fun `a screenshot moves the remembered surface to the image`() = runTest(dispatcher) {
        data.guide = tab()
        data.savedScreenshot = FoldImage("shot", "Screenshot", "file:///shot.png")
        val settings = foldSettings()
        val presenter = FoldGuidePanelPresenter(
            stateManager, data, settings, systemMetrics(), FoldHandover(), FakeLicense(), mock(),
            TestScope(dispatcher),
        )
        stateManager.setState(AppState.VaultSession(VAULT, VaultTab.Guide))
        presenter.onPanelOpened()
        advanceUntilIdle()

        presenter.onScreenshotTaken(java.io.File("/tmp/shot.png"))
        advanceUntilIdle()
        verify(settings).setFoldPanelSurface(FoldPanelSurface.IMAGE.name)

        presenter.onPanelClosed()
        presenter.onPanelOpened()
        assertEquals(FoldPanelSurface.IMAGE, presenter.state.value.surface)
    }

    private fun tab(id: String = "tab-1", fileType: String = "mht") = SavedTab(
        id = id,
        vaultId = VAULT,
        title = "A guide",
        url = "",
        createdAt = Date(0),
        lastAccessed = Date(0),
        fileType = fileType,
    )

    private lateinit var data: FakeData
    private lateinit var stateManager: StateManager

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        data = FakeData()
        stateManager = StateManager()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    /**
     * Mocked because the real one needs a Context and this is not a Robolectric
     * test. Only [snapshot] has to answer: the presenter collects it from init,
     * and an unstubbed StateFlow property would NPE that collector rather than
     * fail whatever test is actually running.
     */
    private fun systemMetrics() = org.mockito.kotlin.mock<com.twinlore.app.data.metrics.SystemMetrics> {
        on { snapshot } doReturn
            kotlinx.coroutines.flow.MutableStateFlow(com.twinlore.app.data.metrics.SystemMetricsSnapshot())
    }

    private fun stateVaultId(): String? =
        (stateManager.currentState.value as? AppState.VaultSession)?.vaultId

    private fun presenter(): FoldGuidePanelPresenter {
        val settings = foldSettings()
        return FoldGuidePanelPresenter(
            stateManager, data, settings, systemMetrics(), FoldHandover(), FakeLicense(), mock(),
            TestScope(dispatcher),
        )
    }

    private suspend fun inVault() {
        stateManager.setState(AppState.VaultSession(VAULT, VaultTab.Guide))
    }

    /**
     * Opens another vault the way the app does, through the library.
     *
     * StateManager refuses VaultSession -> VaultSession across two different
     * vaults, so setting one directly is silently a no-op and the panel would
     * never see the vault change at all.
     */
    private suspend fun switchVault(vaultId: String) {
        stateManager.setState(AppState.Library)
        stateManager.setState(AppState.VaultSession(vaultId, VaultTab.Guide))
    }

    // Folded from the library there is no vault, so there is no guide, and the
    // panel says so rather than loading whatever it saw last.
    @Test
    fun `a fold with no vault opens on nothing`() = runTest(dispatcher) {
        val presenter = presenter()

        presenter.onPanelOpened()
        advanceUntilIdle()

        assertNull(presenter.state.value.guide)
        assertFalse(presenter.state.value.loading)
        assertTrue("nothing to ask about", data.asked.isEmpty())
    }

    // The routing, which is the presenter's whole job: four file types to three
    // viewers, and everything else named instead.
    @Test
    fun `each kind of guide goes to the viewer that owns it`() = runTest(dispatcher) {
        val web = listOf("html", "mht", "epub", "txt")
        web.forEach { column ->
            inVault()
            data.guide = tab(fileType = column)
            val presenter = presenter()
            presenter.onPanelOpened()
            advanceUntilIdle()
            assertTrue("$column should read as a single guide", presenter.state.value.guide is FoldGuide.Web)
        }

        inVault()
        data.guide = tab(fileType = "group")
        presenter().apply { onPanelOpened() }.also { advanceUntilIdle() }
            .let { assertTrue(it.state.value.guide is FoldGuide.Group) }

        inVault()
        data.guide = tab(fileType = "pdf")
        presenter().apply { onPanelOpened() }.also { advanceUntilIdle() }
            .let { assertTrue(it.state.value.guide is FoldGuide.Pdf) }
    }

    // Code wheels, HTMLZIP bundles and links are whole screens with their own
    // navigation in the app. Naming the type beats a blank surface.
    @Test
    fun `the types with their own screens are named rather than rendered`() =
        runTest(dispatcher) {
            listOf("wheel" to "WHEEL", "htmlzip" to "HTML", "link" to "LINK")
                .forEach { (column, label) ->
                    inVault()
                    data.guide = tab(fileType = column)
                    val presenter = presenter()

                    presenter.onPanelOpened()
                    advanceUntilIdle()

                    assertEquals(FoldGuide.Unsupported(label), presenter.state.value.guide)
                }
        }

    @Test
    fun `a vault with no guide open lands on the empty surface`() = runTest(dispatcher) {
        inVault()
        data.guide = null
        val presenter = presenter()

        presenter.onPanelOpened()
        advanceUntilIdle()

        assertNull(presenter.state.value.guide)
        assertFalse(presenter.state.value.loading)
        assertEquals(listOf(VAULT), data.asked)
    }

    // A swap tears the window down and builds a new one while somebody is still
    // reading. Without the open guard that rebuild would reload the guide
    // underneath them.
    @Test
    fun `a rebuild is not an opening`() = runTest(dispatcher) {
        inVault()
        data.guide = tab()
        val presenter = presenter()

        presenter.onPanelOpened()
        advanceUntilIdle()
        presenter.onPanelOpened()
        advanceUntilIdle()

        assertEquals("the guide is read once per open", listOf(VAULT), data.asked)
    }

    @Test
    fun `closing and opening again does read the guide afresh`() = runTest(dispatcher) {
        inVault()
        data.guide = tab()
        val presenter = presenter()

        presenter.onPanelOpened()
        advanceUntilIdle()
        presenter.onPanelClosed()
        presenter.onPanelOpened()
        advanceUntilIdle()

        assertEquals(listOf(VAULT, VAULT), data.asked)
    }

    // The App button. A fold from a vault leaves the app on that vault, but the
    // app renders from StateManager and nothing else points it at the guide.
    @Test
    fun `opening the app points it at the guide that was being read`() = runTest(dispatcher) {
        inVault()
        stateManager.setState(AppState.VaultSession(VAULT, VaultTab.Checklist))
        data.guide = tab()
        val presenter = presenter()
        presenter.onPanelOpened()
        advanceUntilIdle()

        var opened = 0
        presenter.openAppOnCurrentGuide { opened++ }
        advanceUntilIdle()

        assertEquals(1, opened)
        assertEquals(
            AppState.VaultSession(VAULT, VaultTab.Guide),
            stateManager.currentState.value,
        )
    }

    /**
     * The Library button, which is the opposite trip to the one above.
     *
     * Return points the app at the guide so it lands where you were reading.
     * This one is for leaving that game behind, so it points the app at the
     * library instead, and the note still has to be left: the resume that
     * follows runs the open-last-played pass, and without the note that pass
     * would reopen the very game somebody just asked to walk away from.
     */
    @Test
    fun `returning to the library leaves the app on the library, not on the game`() = runTest(dispatcher) {
        inVault()
        data.guide = tab()
        val handover = FoldHandover()
        val presenter = FoldGuidePanelPresenter(
            stateManager, data, foldSettings(), systemMetrics(), handover, FakeLicense(), mock(),
            TestScope(dispatcher),
        )
        presenter.onPanelOpened()
        advanceUntilIdle()

        var opened = 0
        presenter.openAppOnLibrary { opened++ }
        advanceUntilIdle()

        assertEquals(1, opened)
        assertEquals(AppState.Library, stateManager.currentState.value)
        assertTrue("the open-last-played pass must stand down", handover.consume())
    }

    /**
     * The Settings surface's way out to the rest of the settings.
     *
     * The panel carries the handful worth changing without putting the game
     * down, and deliberately not the rest. Everything else is in the app, and
     * the app cannot be shown over a game, so this leaves the mode: the same
     * hand-over Library uses, aimed at the settings screen, with the same note
     * left behind so the resume's open-last-played pass does not read it as an
     * ordinary return and bounce to a game.
     */
    @Test
    fun `opening the app's settings lands there rather than on the game`() = runTest(dispatcher) {
        inVault()
        data.guide = tab()
        val handover = FoldHandover()
        val presenter = FoldGuidePanelPresenter(
            stateManager, data, foldSettings(), systemMetrics(), handover, FakeLicense(), mock(),
            TestScope(dispatcher),
        )
        presenter.onPanelOpened()
        advanceUntilIdle()

        var opened = 0
        presenter.openAppOnSettings { opened++ }
        advanceUntilIdle()

        assertEquals(1, opened)
        assertEquals(AppState.Settings, stateManager.currentState.value)
        assertTrue("the open-last-played pass must stand down", handover.consume())
    }

    /**
     * And the order, which is the part that bites. The state has to be set
     * before the activity is asked forward: the resume runs the
     * open-last-played pass, and a pass that arrives before the state is
     * written decides for itself where the app lands.
     */
    @Test
    fun `the settings state is set before the app is asked forward`() = runTest(dispatcher) {
        inVault()
        data.guide = tab()
        val presenter = FoldGuidePanelPresenter(
            stateManager, data, foldSettings(), systemMetrics(), FoldHandover(), FakeLicense(), mock(),
            TestScope(dispatcher),
        )
        presenter.onPanelOpened()
        advanceUntilIdle()

        var stateWhenOpened: AppState? = null
        presenter.openAppOnSettings { stateWhenOpened = stateManager.currentState.value }
        advanceUntilIdle()

        assertEquals(AppState.Settings, stateWhenOpened)
    }

    // The handover takes a window down and destroys a lifecycle, and the scope
    // it runs on is Dispatchers.IO. Pressing App killed the process outright:
    // "Method setCurrentState must be called on the main thread".
    //
    // Real dispatchers rather than test ones, because the thing under test is
    // which thread the callback lands on, and test dispatchers all run on the
    // caller's. The main dispatcher's thread is named so the assertion can say
    // what it means.
    @Test
    fun `the app handover runs on the main thread, not on the app scope`() {
        val mainThreadName = "fold-panel-test-main"
        val mainExecutor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, mainThreadName)
        }
        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            Dispatchers.setMain(mainExecutor.asCoroutineDispatcher())
            stateManager = StateManager()
            val presenter = FoldGuidePanelPresenter(
                stateManager, data, foldSettings(), systemMetrics(), FoldHandover(), FakeLicense(), mock(),
                appScope,
            )
            data.guide = tab()
            runBlocking { stateManager.setState(AppState.VaultSession(VAULT, VaultTab.Guide)) }
            presenter.onPanelOpened()

            val landed = CountDownLatch(1)
            var ranOn = ""
            presenter.openAppOnCurrentGuide {
                ranOn = Thread.currentThread().name
                landed.countDown()
            }

            assertTrue("the handover never ran", landed.await(5, TimeUnit.SECONDS))
            // startsWith, not equals: coroutine debug mode appends
            // " @coroutine#n" to the thread name.
            assertTrue("handover ran on $ranOn", ranOn.startsWith(mainThreadName))
        } finally {
            appScope.cancel()
            mainExecutor.shutdownNow()
        }
    }

    // Folded from the library: there is nothing to point the app at, and the
    // handover still has to happen.
    @Test
    fun `opening the app with no vault still opens the app`() = runTest(dispatcher) {
        val presenter = presenter()
        presenter.onPanelOpened()
        advanceUntilIdle()

        var opened = 0
        presenter.openAppOnCurrentGuide { opened++ }
        advanceUntilIdle()

        assertEquals(1, opened)
    }

    // ---- Links the reader refuses to follow ----

    // The safeguard, and why it exists: this window is over a running game with
    // Game Twin backgrounded, so following a link either drops the player into
    // another app or, for ordinary http, reaches a null mainViewModel and does
    // nothing at all. The panel takes it and names it instead.
    @Test
    fun `a blocked link is named rather than followed`() = runTest(dispatcher) {
        inVault()
        data.guide = tab()
        val presenter = presenter()
        presenter.onPanelOpened()
        advanceUntilIdle()

        presenter.onExternalLink("https://gamefaqs.gamespot.com/ds/1234")
        advanceUntilIdle()

        assertEquals(
            "https://gamefaqs.gamespot.com/ds/1234",
            presenter.state.value.blockedLink?.url,
        )
    }

    @Test
    fun `dismissing a blocked link puts the reader back`() = runTest(dispatcher) {
        inVault()
        data.guide = tab()
        val presenter = presenter()
        presenter.onPanelOpened()
        advanceUntilIdle()
        presenter.onExternalLink("https://example.com")
        advanceUntilIdle()

        presenter.dismissBlockedLink()

        assertNull(presenter.state.value.blockedLink)
    }

    // Text size is per guide, so it goes to the bookmark the app reads back.
    @Test
    fun `text size is applied here and written for the app`() = runTest(dispatcher) {
        inVault()
        data.guide = tab(id = "tab-9")
        val presenter = presenter()
        presenter.onPanelOpened()
        advanceUntilIdle()

        presenter.setTextZoom(1.5f)
        advanceUntilIdle()

        assertEquals(1.5f, presenter.state.value.textZoom, 1e-4f)
        assertEquals(listOf("tab-9" to 1.5f), data.zoomWrites)
    }

    // ---- Images ----

    @Test
    fun `the vault's images arrive with the guide`() = runTest(dispatcher) {
        inVault()
        data.guide = tab()
        data.images = listOf(image("a"), image("b"), image("c"))
        val presenter = presenter()

        presenter.onPanelOpened()
        advanceUntilIdle()

        assertEquals(3, presenter.state.value.images.size)
        assertEquals("a", presenter.state.value.currentImage?.id)
        assertTrue(presenter.state.value.hasNextImage)
        assertFalse(presenter.state.value.hasPreviousImage)
    }

    // Folding from the Image tab lands on the picture that was on screen. The
    // app records which image it is open on, and the panel reads that record
    // rather than starting at the top of its list.
    @Test
    fun `the panel opens on the image the app is open on`() = runTest(dispatcher) {
        inVault()
        data.guide = tab()
        data.images = listOf(image("a"), image("b"), image("c"))
        data.currentImageId = "b"
        data.storedViews["b"] = FoldImageView(2f, 30f, 40f)
        val presenter = presenter()
        presenter.onPanelOpened()
        advanceUntilIdle()

        assertEquals("b", presenter.state.value.currentImage?.id)
        assertEquals(1, presenter.state.value.imageIndex)
        assertEquals("and at the app's pan of it", FoldImageView(2f, 30f, 40f), presenter.state.value.imageView)
        assertEquals("opening is not a step; nothing is written back", emptyList<String>(), data.touched)
    }

    // A record pointing at an image the vault no longer has is not an error,
    // it is just no help: the list starts at the top as before.
    @Test
    fun `a recorded image the vault no longer has falls back to the first`() = runTest(dispatcher) {
        inVault()
        data.guide = tab()
        data.images = listOf(image("a"), image("b"))
        data.currentImageId = "deleted"
        val presenter = presenter()
        presenter.onPanelOpened()
        advanceUntilIdle()

        assertEquals(0, presenter.state.value.imageIndex)
    }

    // Fold, glance, unfold on the same vault keeps the image the panel was on
    // even if the app's record says otherwise: the record is where the app
    // was, and the panel is where the reader is.
    @Test
    fun `reopening on the same vault keeps the panel's own image over the record`() = runTest(dispatcher) {
        inVault()
        data.guide = tab()
        data.images = listOf(image("a"), image("b"), image("c"))
        val presenter = presenter()
        presenter.onPanelOpened()
        advanceUntilIdle()
        presenter.stepImage(1)
        presenter.stepImage(1)
        advanceUntilIdle()
        assertEquals("c", data.currentImageId)

        presenter.onPanelClosed()
        data.currentImageId = "a"
        presenter.onPanelOpened()
        advanceUntilIdle()

        assertEquals("c", presenter.state.value.currentImage?.id)
    }

    // Paging here is where the app opens next: touchImage writes the same
    // selection the app's Image tab restores to.
    @Test
    fun `stepping an image moves and tells the app where it got to`() = runTest(dispatcher) {
        inVault()
        data.guide = tab()
        data.images = listOf(image("a"), image("b"), image("c"))
        val presenter = presenter()
        presenter.onPanelOpened()
        advanceUntilIdle()

        presenter.stepImage(1)
        advanceUntilIdle()

        assertEquals("b", presenter.state.value.currentImage?.id)
        assertEquals(listOf("b"), data.touched)
    }

    @Test
    fun `stepping past either end does nothing`() = runTest(dispatcher) {
        inVault()
        data.guide = tab()
        data.images = listOf(image("a"), image("b"))
        val presenter = presenter()
        presenter.onPanelOpened()
        advanceUntilIdle()

        presenter.stepImage(-1)
        advanceUntilIdle()
        assertEquals(0, presenter.state.value.imageIndex)

        presenter.stepImage(1)
        presenter.stepImage(1)
        advanceUntilIdle()
        assertEquals(1, presenter.state.value.imageIndex)
        assertEquals(listOf("b"), data.touched)
    }

    @Test
    fun `the panel opens on the guide, and the image surface is a choice`() =
        runTest(dispatcher) {
            inVault()
            data.guide = tab()
            val presenter = presenter()
            presenter.onPanelOpened()
            advanceUntilIdle()
            assertEquals(FoldPanelSurface.GUIDE, presenter.state.value.surface)

            presenter.selectSurface(FoldPanelSurface.IMAGE)

            assertEquals(FoldPanelSurface.IMAGE, presenter.state.value.surface)
        }

    // App follows what is on screen: the Image surface is the app's Map tab.
    @Test
    fun `pressing App from the image surface opens the app's image tab`() =
        runTest(dispatcher) {
            inVault()
            data.guide = tab()
            val presenter = presenter()
            presenter.onPanelOpened()
            advanceUntilIdle()
            presenter.selectSurface(FoldPanelSurface.IMAGE)

            presenter.openAppOnCurrentGuide { }
            advanceUntilIdle()

            assertEquals(
                AppState.VaultSession(VAULT, VaultTab.Map),
                stateManager.currentState.value,
            )
        }

    @Test
    fun `whether swap has anywhere to go is the platform's answer, not ours`() =
        runTest(dispatcher) {
            val presenter = presenter()

            presenter.setSwapAvailable(true)

            assertTrue(presenter.state.value.swapAvailable)
        }

    private companion object {
        const val VAULT = "vault-1"
    }
}
