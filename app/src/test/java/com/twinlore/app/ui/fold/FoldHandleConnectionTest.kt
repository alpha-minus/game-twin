package com.twinlore.app.ui.fold

import android.view.Display
import com.twinlore.app.data.AppIcon
import com.twinlore.app.data.NetworkStatus
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeastOnce
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
 * The handle's lost-connection mark: shown once a loss has lasted, cleared at
 * once when the connection is back, and re-asked on a timer while it can be
 * seen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class FoldHandleConnectionTest {

    private class FakeLicense : LicenseManagerUiStateContract {
        override val licenseUiState: StateFlow<LicenseUiState> = MutableStateFlow(LicenseUiState.Free)
        override fun isLicensedNow(): Boolean = false
        override val licenseTier: StateFlow<LicenseTier> = MutableStateFlow(LicenseTier.NONE)
    }

    private val online = NetworkStatus(hasInternet = true, isWifi = true, signalStrength = -50)
    private val status = MutableStateFlow(online)
    private val connectivity = onlineConnectivity(status)
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

    private fun idle(ms: Long) = ShadowLooper.idleMainLooper(ms, java.util.concurrent.TimeUnit.MILLISECONDS)

    @Test
    fun `a loss is shown once it has lasted, not before`() {
        val platform = shownPlatform()
        status.value = NetworkStatus.OFFLINE

        idle(AndroidFoldPlatform.OFFLINE_GRACE_MS - 500)
        assertFalse(platform.shownTabView!!.offline)

        idle(1_000)
        assertTrue(platform.shownTabView!!.offline)
    }

    /** What a re-ask behind a game looks like: offline for a moment, then the network again. */
    @Test
    fun `a moment offline is never shown`() {
        val platform = shownPlatform()
        status.value = NetworkStatus.OFFLINE
        idle(500)
        status.value = online

        idle(AndroidFoldPlatform.OFFLINE_GRACE_MS * 2)
        assertFalse(platform.shownTabView!!.offline)
    }

    @Test
    fun `the connection coming back clears the mark at once`() {
        val platform = shownPlatform()
        status.value = NetworkStatus.OFFLINE
        idle(AndroidFoldPlatform.OFFLINE_GRACE_MS + 100)
        assertTrue(platform.shownTabView!!.offline)

        status.value = online
        ShadowLooper.idleMainLooper()
        assertFalse(platform.shownTabView!!.offline)
    }

    @Test
    fun `the connection is re-asked on a timer while the mark can be seen`() {
        shownPlatform()
        idle(AndroidFoldPlatform.CONNECTION_RECHECK_MS + 100)
        verify(connectivity, atLeastOnce()).recheck(any())
    }

    @Test
    fun `nothing is re-asked with the clock off`() {
        shownPlatform(clockOn = false)
        idle(AndroidFoldPlatform.CONNECTION_RECHECK_MS * 2)
        verify(connectivity, never()).recheck(any())
    }

    @Test
    fun `nothing is re-asked while the panel is up`() {
        panelShowing.value = true
        shownPlatform()
        idle(AndroidFoldPlatform.CONNECTION_RECHECK_MS * 2)
        verify(connectivity, never()).recheck(any())
    }

    @Test
    fun `taking the handle down stops the timer`() {
        val platform = shownPlatform()
        platform.hideTab()
        idle(AndroidFoldPlatform.CONNECTION_RECHECK_MS * 2)
        verify(connectivity, never()).recheck(any())
    }
}
