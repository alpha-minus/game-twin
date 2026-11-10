package com.twinlore.app.ui.fold

import android.view.Display
import com.twinlore.app.data.AppFont
import com.twinlore.app.data.AppIcon
import com.twinlore.app.data.AppIconManager
import com.twinlore.app.data.SettingsRepository
import com.twinlore.app.data.SplashIconCache
import com.twinlore.app.data.fold.FoldHandleLook
import com.twinlore.app.data.license.LicenseManagerUiStateContract
import com.twinlore.app.data.license.LicenseTier
import com.twinlore.app.data.license.LicenseUiState
import com.twinlore.app.ui.fold.guide.FoldGuidePanelContentFactory
import com.twinlore.app.ui.fold.guide.FoldGuidePanelHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
import org.robolectric.shadows.ShadowLooper
import javax.inject.Provider

/**
 * An icon or a font picked from the panel's own settings shows on the handle
 * that is already up, and the icon does not touch the launcher until the app
 * closes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class FoldHandleIconFontTest {

    private class FakeLicense(tier: LicenseTier) : LicenseManagerUiStateContract {
        override val licenseUiState: StateFlow<LicenseUiState> = MutableStateFlow(LicenseUiState.Free)
        override fun isLicensedNow(): Boolean = false
        override val licenseTier: StateFlow<LicenseTier> = MutableStateFlow(tier)
    }

    private val appIcon = MutableStateFlow(AppIcon.DEFAULT)
    private val appFont = MutableStateFlow(AppFont.DEFAULT)
    private val iconManager = mock<AppIconManager>()
    private val cache = mock<SplashIconCache>().also { whenever(it.read()).thenReturn(AppIcon.DEFAULT) }

    private fun shownPlatform(tier: LicenseTier = LicenseTier.TIER3): AndroidFoldPlatform {
        val settings = mock<SettingsRepository>().also {
            whenever(it.appIcon).thenReturn(appIcon)
            whenever(it.appFont).thenReturn(appFont)
            whenever(it.hapticFeedbackIntensity).thenReturn(emptyFlow())
            whenever(it.foldHandleClockBattery).thenReturn(MutableStateFlow(true))
        }
        val platform = AndroidFoldPlatform(
            context = RuntimeEnvironment.getApplication(),
            appScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            settingsRepository = settings,
            splashIconCache = cache,
            licenseManager = FakeLicense(tier),
            guidePanelHost = mock<FoldGuidePanelHost>(),
            guidePanelContentFactory = Provider { mock<FoldGuidePanelContentFactory>() },
            foldController = Provider { error("not exercised by these tests") },
            connectivity = onlineConnectivity(),
            appIconManager = iconManager,
        )
        assertTrue(platform.showTab(Display.DEFAULT_DISPLAY, 1f, 0.5f, FoldHandleLook()))
        ShadowLooper.idleMainLooper()
        return platform
    }

    @Test
    fun `a picked icon changes the handle that is up, and its accent with it`() {
        val platform = shownPlatform()
        appIcon.value = AppIcon.CYBER
        ShadowLooper.idleMainLooper()

        val view = platform.shownTabView!!
        assertEquals(AppIcon.CYBER.launcherMipmap, view.iconResForTest)
        assertEquals(AppIcon.CYBER.handleAccent.toInt(), view.chip.levelColorForTest)
        verify(cache).write(AppIcon.CYBER)
    }

    @Test
    fun `a picked icon leaves the launcher alone while folded`() {
        shownPlatform()
        appIcon.value = AppIcon.GOLD
        ShadowLooper.idleMainLooper()

        verify(iconManager, never()).applyLauncherIcon(org.mockito.kotlin.any())
    }

    @Test
    fun `an icon the license does not unlock is not what the handle shows`() {
        val platform = shownPlatform(tier = LicenseTier.NONE)
        appIcon.value = AppIcon.NOIR
        ShadowLooper.idleMainLooper()

        assertEquals(AppIcon.DEFAULT.launcherMipmap, platform.shownTabView!!.iconResForTest)
    }

    @Test
    fun `the pixel font reaches the handle's clock live, and back`() {
        val platform = shownPlatform()
        val view = platform.shownTabView!!
        assertFalse(view.chip.pixelFont)

        appFont.value = AppFont.PIXEL
        ShadowLooper.idleMainLooper()
        assertTrue(view.chip.pixelFont)

        appFont.value = AppFont.SPACE
        ShadowLooper.idleMainLooper()
        assertFalse(view.chip.pixelFont)
    }

    @Test
    fun `a handle built while the font is pixel starts in it`() {
        appFont.value = AppFont.PIXEL
        val platform = shownPlatform()
        assertTrue(platform.shownTabView!!.pixelFont)
    }
}
