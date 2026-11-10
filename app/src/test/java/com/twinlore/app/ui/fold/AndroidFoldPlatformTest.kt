package com.twinlore.app.ui.fold

import android.content.Context
import com.twinlore.app.data.AppIcon
import com.twinlore.app.data.SettingsRepository
import com.twinlore.app.data.SplashIconCache
import com.twinlore.app.data.license.LicenseManagerUiStateContract
import com.twinlore.app.data.license.LicenseTier
import com.twinlore.app.data.license.LicenseUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import javax.inject.Provider

/**
 * [AndroidFoldPlatform] mirrors the themed launcher icon into `themedIconRes` so
 * [AndroidFoldPlatform.showTab] can read it without suspending. This covers that
 * mirroring: it must already be right on the very first fold (seeded from
 * [SplashIconCache] synchronously, before the settings flow has ever emitted), and
 * it must track a later emission from that flow.
 *
 * [SettingsRepository.appIcon] is modelled with a no-replay [MutableSharedFlow]
 * rather than a real DataStore-backed [SettingsRepository]. A fresh DataStore's
 * first collection is a genuine suspension - that suspension is exactly the bug
 * this class works around - but its real-world timing isn't something a test can
 * pin down. A replay-0 SharedFlow reproduces the same "delivers nothing until
 * something happens" shape deterministically: nothing reaches the collector until
 * [MutableSharedFlow.emit] is called, so the seed-vs-flow ordering below is exact
 * rather than a hope about scheduling.
 *
 * `showTab`'s own use of the cached icon needs a real overlay window and isn't
 * covered here; see the task report for why that's a device check, not a unit test.
 *
 * Every flow the constructor collects has to be stubbed, including the two these
 * tests do not care about. An unstubbed Mockito getter returns null, `collect` on
 * null throws inside `appScope.launch`, and that scope has no handler - so the NPE
 * reached the JVM's uncaught handler and sat there. `runTest` refuses to start
 * while an uncaught exception is pending, so the next coroutine test to run in the
 * same Gradle fork failed with UncaughtExceptionsBeforeTest, naming itself rather
 * than this class. Which test that was depended only on how the fork happened to
 * be packed, which is why it looked like a flake.
 */
class AndroidFoldPlatformTest {

    private class FakeLicense(tier: LicenseTier = LicenseTier.NONE) : LicenseManagerUiStateContract {
        override val licenseUiState: StateFlow<LicenseUiState> = MutableStateFlow(LicenseUiState.Free)
        override fun isLicensedNow(): Boolean = false
        override val licenseTier: StateFlow<LicenseTier> = MutableStateFlow(tier)
    }

    /**
     * A repository mock with every flow [AndroidFoldPlatform]'s init collects.
     * See the class comment: an unstubbed one is not an inert one.
     */
    private fun settingsMock(appIconFlow: MutableSharedFlow<AppIcon>): SettingsRepository =
        mock<SettingsRepository>().also {
            whenever(it.appIcon).thenReturn(appIconFlow)
            whenever(it.hapticFeedbackIntensity).thenReturn(emptyFlow())
            whenever(it.foldHandleClockBattery).thenReturn(emptyFlow())
            whenever(it.appFont).thenReturn(emptyFlow())
        }

    private fun newPlatform(
        settingsRepository: SettingsRepository,
        splashIconCache: SplashIconCache,
        appScope: CoroutineScope,
    ): AndroidFoldPlatform = AndroidFoldPlatform(
        context = mock<Context>(),
        appScope = appScope,
        settingsRepository = settingsRepository,
        splashIconCache = splashIconCache,
        licenseManager = FakeLicense(),
        guidePanelHost = mock(),
        guidePanelContentFactory = Provider { error("not exercised by these tests") },
        foldController = Provider { error("not exercised by these tests") },
        connectivity = onlineConnectivity(),
        appIconManager = mock(),
    )

    @Test
    fun `seeds the cached icon synchronously, before the settings flow has emitted anything`() {
        val appIconFlow = MutableSharedFlow<AppIcon>() // no replay: delivers nothing until emit()
        val settingsRepository = settingsMock(appIconFlow)
        val splashIconCache = mock<SplashIconCache>()
        whenever(splashIconCache.read()).thenReturn(AppIcon.NOIR)

        val platform = newPlatform(
            settingsRepository,
            splashIconCache,
            CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        )

        // Nothing has been emitted, so this value can only have come from the
        // synchronous seed, never from the flow collection in init.
        assertEquals(AppIcon.NOIR.launcherMipmap, platform.themedIconRes)
    }

    @Test
    fun `the seed is replaced once the settings flow actually emits`() {
        val appIconFlow = MutableSharedFlow<AppIcon>()
        val settingsRepository = settingsMock(appIconFlow)
        val splashIconCache = mock<SplashIconCache>()
        whenever(splashIconCache.read()).thenReturn(AppIcon.NOIR)

        val platform = newPlatform(
            settingsRepository,
            splashIconCache,
            CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        )
        assertEquals(AppIcon.NOIR.launcherMipmap, platform.themedIconRes)

        // Unconfined + no buffering: emit() resumes the waiting collector
        // synchronously on this thread, so by the time emit() returns,
        // themedIconRes already reflects the new value - no polling needed.
        runBlocking { appIconFlow.emit(AppIcon.PIXEL) }

        assertEquals(AppIcon.PIXEL.launcherMipmap, platform.themedIconRes)
    }

}
