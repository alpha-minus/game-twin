package com.twinlore.app.ui.fold

import android.view.Display
import android.view.View
import com.twinlore.app.data.AppIcon
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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import javax.inject.Provider

/**
 * The handle steps aside while the keyboard is up over the panel, and only
 * then: over a parked or closed panel it is the way back and always shows.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class FoldHandleKeyboardTest {

    private class FakeLicense : LicenseManagerUiStateContract {
        override val licenseUiState: StateFlow<LicenseUiState> = MutableStateFlow(LicenseUiState.Free)
        override fun isLicensedNow(): Boolean = false
        override val licenseTier: StateFlow<LicenseTier> = MutableStateFlow(LicenseTier.NONE)
    }

    private val connectivity = onlineConnectivity()
    private val panelShowing = MutableStateFlow(false)
    private val panelHost: FoldGuidePanelHost = mock<FoldGuidePanelHost>().also {
        whenever(it.isShowing).thenAnswer { panelShowing.value }
    }

    private fun shownPlatform(clockOn: Boolean = true): AndroidFoldPlatform {
        val settings = mock<SettingsRepository>().also {
            whenever(it.appIcon).thenReturn(emptyFlow())
            whenever(it.hapticFeedbackIntensity).thenReturn(emptyFlow())
            whenever(it.foldHandleClockBattery).thenReturn(MutableStateFlow(clockOn))
            whenever(it.appFont).thenReturn(emptyFlow())
        }
        val cache = mock<SplashIconCache>().also { whenever(it.read()).thenReturn(AppIcon.DEFAULT) }
        val platform = AndroidFoldPlatform(
            context = RuntimeEnvironment.getApplication(),
            appScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            settingsRepository = settings,
            splashIconCache = cache,
            licenseManager = FakeLicense(),
            guidePanelHost = panelHost,
            guidePanelContentFactory = Provider { mock<FoldGuidePanelContentFactory>() },
            foldController = Provider { error("not exercised by these tests") },
            connectivity = connectivity,
            appIconManager = mock(),
        )
        assertTrue(platform.showTab(Display.DEFAULT_DISPLAY, 1f, 0.5f, FoldHandleLook()))
        ShadowLooper.idleMainLooper()
        return platform
    }

    @Test
    fun `the keyboard over an open panel hides the handle, and its going brings it back`() {
        panelShowing.value = true
        val platform = shownPlatform()

        platform.setHandleHiddenForKeyboard(true)
        assertEquals(View.INVISIBLE, platform.shownTabView!!.visibility)

        platform.setHandleHiddenForKeyboard(false)
        assertEquals(View.VISIBLE, platform.shownTabView!!.visibility)
    }

    @Test
    fun `with no panel up the handle stays, keyboard or not`() {
        val platform = shownPlatform()
        platform.setHandleHiddenForKeyboard(true)
        assertEquals(View.VISIBLE, platform.shownTabView!!.visibility)
    }

    @Test
    fun `parking the panel with the keyboard up brings the handle back`() {
        panelShowing.value = true
        whenever(panelHost.park()).thenAnswer { panelShowing.value = false; true }
        val platform = shownPlatform()
        platform.setHandleHiddenForKeyboard(true)

        platform.parkGuidePanel()
        assertEquals(View.VISIBLE, platform.shownTabView!!.visibility)
    }
}
