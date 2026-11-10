package com.twinlore.app.ui.fold

import android.view.Display
import com.twinlore.app.data.AppIcon
import com.twinlore.app.data.SettingsRepository
import com.twinlore.app.data.SplashIconCache
import com.twinlore.app.data.fold.FoldHandleLook
import com.twinlore.app.data.fold.FoldHandleStyle
import com.twinlore.app.data.license.LicenseManagerUiStateContract
import com.twinlore.app.data.license.LicenseTier
import com.twinlore.app.data.license.LicenseUiState
import com.twinlore.app.ui.fold.guide.FoldGuidePanelContentFactory
import com.twinlore.app.ui.fold.guide.FoldGuidePanelHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import javax.inject.Provider

/**
 * Changing the handle's size must not close the panel.
 *
 * It used to, and not by accident: a size change was a window rebuild, the
 * rebuild went through [AndroidFoldPlatform.hideTab], and hideTab takes the
 * panel down first and unconditionally, because a full screen reader over a
 * game with no handle left to close it is the state this whole feature exists
 * to prevent. That is right for hideTab and wrong for a resize, and it meant
 * changing the size from the panel's own settings surface closed the settings
 * surface. It is also unusable under a slider, which is what the size is set
 * with now.
 *
 * So the size is applied to the window that is already up. The panel host is a
 * mock here for one reason: it is the thing that would be told to hide, and
 * these tests are about that call not happening.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class FoldHandleResizeTest {

    private class FakeLicense : LicenseManagerUiStateContract {
        override val licenseUiState: StateFlow<LicenseUiState> = MutableStateFlow(LicenseUiState.Free)
        override fun isLicensedNow(): Boolean = false
        override val licenseTier: StateFlow<LicenseTier> = MutableStateFlow(LicenseTier.NONE)
    }

    private val panelHost: FoldGuidePanelHost = mock<FoldGuidePanelHost>().also {
        whenever(it.isShowing).thenReturn(true)
    }
    private val contentFactory: FoldGuidePanelContentFactory = mock()

    private val settings: SettingsRepository = mock<SettingsRepository>().also {
        whenever(it.appIcon).thenReturn(emptyFlow())
        whenever(it.hapticFeedbackIntensity).thenReturn(emptyFlow())
        whenever(it.foldHandleClockBattery).thenReturn(emptyFlow())
        whenever(it.appFont).thenReturn(emptyFlow())
    }

    private fun platform(): AndroidFoldPlatform {
        val cache = mock<SplashIconCache>().also {
            whenever(it.read()).thenReturn(AppIcon.DEFAULT)
        }
        return AndroidFoldPlatform(
            context = RuntimeEnvironment.getApplication(),
            appScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            settingsRepository = settings,
            splashIconCache = cache,
            licenseManager = FakeLicense(),
            guidePanelHost = panelHost,
            guidePanelContentFactory = Provider { contentFactory },
            foldController = Provider { error("not exercised by these tests") },
        connectivity = onlineConnectivity(),
        appIconManager = mock(),
        )
    }

    private fun shownPlatform(sizeDp: Int = FoldHandleStyle.MIN_SIZE_DP): AndroidFoldPlatform {
        val platform = platform()
        assertTrue(
            "the handle's window has to be up for any of this to mean anything",
            platform.showTab(Display.DEFAULT_DISPLAY, 1f, 0.5f, FoldHandleLook(sizeDp = sizeDp)),
        )
        return platform
    }

    @Test
    fun `resizing changes the handle without taking the panel down`() {
        val platform = shownPlatform()
        val before = platform.tabSizePx

        platform.resizeTab(FoldHandleStyle.MAX_SIZE_DP)

        assertNotEquals("the handle has to actually resize", before, platform.tabSizePx)
        verify(panelHost, never()).hide()
    }

    /**
     * The control for the test above. hideTab still closes the panel, and must:
     * a reader with no handle left to close it is unrecoverable.
     */
    @Test
    fun `hiding the handle still takes the panel with it`() {
        val platform = shownPlatform()

        platform.hideTab()

        verify(panelHost).hide()
    }

    /**
     * The clock is drawn on the icon, so turning it on leaves the window
     * exactly the icon's square, and the panel stays up.
     */
    @Test
    fun `the clock leaves the window the icon's size without taking the panel down`() {
        val platform = shownPlatform(sizeDp = 56)
        val mark = platform.tabSizePx

        platform.applyClockAndBattery(true)

        assertEquals(mark, platform.tabSizePx)
        assertTrue(platform.shownTabView!!.clockAndBattery)
        verify(panelHost, never()).hide()
    }

    /**
     * The setting arrives on a store thread, and only the main thread may
     * touch the handle. Applying it there directly crashed the app on device,
     * both when it was picked and on every drag (the drag's position write
     * makes the store re-emit).
     */
    @Test
    fun `a clock change from a background thread is applied on the main thread`() {
        val platform = shownPlatform(sizeDp = 56)

        val worker = Thread { platform.applyClockAndBattery(true) }
        worker.start()
        worker.join()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        assertTrue(platform.shownTabView!!.clockAndBattery)
    }

    @Test
    fun `the size the slider lands on is the size the window gets`() {
        val platform = shownPlatform()
        val density = RuntimeEnvironment.getApplication().resources.displayMetrics.density

        platform.resizeTab(72)

        assertEquals((72 * density).toInt(), platform.tabSizePx)
    }

    /** Out of range on either side is clamped, not obeyed and not refused. */
    @Test
    fun `a size past either end of the range is clamped`() {
        val platform = shownPlatform(sizeDp = 56)
        val density = RuntimeEnvironment.getApplication().resources.displayMetrics.density

        platform.resizeTab(4000)
        assertEquals((FoldHandleStyle.MAX_SIZE_DP * density).toInt(), platform.tabSizePx)

        platform.resizeTab(1)
        assertEquals((FoldHandleStyle.MIN_SIZE_DP * density).toInt(), platform.tabSizePx)
    }

    /**
     * One write per gesture, and it carries the whole look.
     *
     * The sliders apply live and persist on release, so what reaches DataStore
     * has to be everything the handle is drawn from and not just the one value
     * that moved: they share a commit, and a commit that wrote only its own
     * field would drop whichever of the other two was changed first.
     */
    @Test
    fun `committing writes all three of the handle's numbers`() = runBlocking {
        val platform = shownPlatform()

        platform.resizeTab(72)
        platform.applyAlpha(0.5f)
        platform.applyRecess(0.6f)
        platform.persistHandleStyle()

        verify(settings).setFoldHandleSizeDp(72)
        verify(settings).setFoldHandleAlpha(0.5f)
        verify(settings).setFoldHandleRecess(0.6f)
    }

    /** The reading size is applied live, and never takes the panel down either. */
    @Test
    fun `changing the reading size does not take the panel down`() {
        val platform = shownPlatform()

        platform.applyRecess(0.4f)

        verify(panelHost, never()).hide()
    }

    /**
     * A size chosen with nothing folded still has to be the size the next fold
     * comes up at. There is no window to update, so the only thing that can
     * carry it is the field.
     */
    @Test
    fun `a resize with no handle up is not simply dropped`() {
        val platform = platform()

        platform.resizeTab(72)
        platform.showTab(Display.DEFAULT_DISPLAY, 1f, 0.5f, FoldHandleLook(sizeDp = 72))

        val density = RuntimeEnvironment.getApplication().resources.displayMetrics.density
        assertEquals((72 * density).toInt(), platform.tabSizePx)
    }
}
