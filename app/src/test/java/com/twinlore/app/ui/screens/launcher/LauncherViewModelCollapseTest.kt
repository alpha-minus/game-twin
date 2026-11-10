package com.twinlore.app.ui.screens.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.twinlore.app.data.DualDisplayLauncher
import com.twinlore.app.data.GameLauncher
import com.twinlore.app.data.LauncherClockSize
import com.twinlore.app.data.LauncherIconSize
import com.twinlore.app.data.SettingsRepository
import com.twinlore.app.data.VaultRepository
import com.twinlore.app.data.catalog.RecommendedAppsCatalog
import com.twinlore.app.data.local.FavoriteAppDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class LauncherViewModelCollapseTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun newVm(collapsed: Boolean): Pair<LauncherViewModel, SettingsRepository> {
        val context: Context = mock()
        val packageManager: PackageManager = mock()
        whenever(context.packageManager).thenReturn(packageManager)
        whenever(context.packageName).thenReturn("com.twinlore.app")
        whenever(packageManager.getLaunchIntentForPackage(any())).thenReturn(Intent())
        whenever(packageManager.queryIntentActivities(any(), eq(0))).thenReturn(emptyList())

        val favoriteAppDao: FavoriteAppDao = mock()
        whenever(favoriteAppDao.getAll()).thenReturn(flowOf(emptyList()))
        whenever(favoriteAppDao.getAllPackageNames()).thenReturn(flowOf(emptyList()))

        val vaultRepository: VaultRepository = mock()
        whenever(vaultRepository.getAllVaultsSortedByLastOpened()).thenReturn(emptyFlow())

        val settingsRepository: SettingsRepository = mock()
        whenever(settingsRepository.launcherApplyThemeToIcons).thenReturn(MutableStateFlow(false))
        whenever(settingsRepository.launcherShowAppNames).thenReturn(MutableStateFlow(true))
        whenever(settingsRepository.launcherIconSize).thenReturn(MutableStateFlow(LauncherIconSize.M))
        whenever(settingsRepository.launcherAutoDetectGames).thenReturn(MutableStateFlow(true))
        whenever(settingsRepository.launcherShowClock).thenReturn(MutableStateFlow(true))
        whenever(settingsRepository.launcherClockSize).thenReturn(MutableStateFlow(LauncherClockSize.M))
        whenever(settingsRepository.appsSectionCollapsed).thenReturn(MutableStateFlow(collapsed))
        whenever(settingsRepository.catalogDeviceType).thenReturn(emptyFlow())

        val vm = LauncherViewModel(
            context = context,
            favoriteAppDao = favoriteAppDao,
            vaultRepository = vaultRepository,
            settingsRepository = settingsRepository,
            gameLauncher = mock<GameLauncher>(),
            recommendedAppsCatalog = mock<RecommendedAppsCatalog>(),
            dualDisplayLauncher = mock(),
        )
        return vm to settingsRepository
    }

    @Test
    fun `appsSectionCollapsed reflects the persisted value`() = runTest(dispatcher) {
        val (vm, _) = newVm(collapsed = true)
        advanceUntilIdle()
        assertTrue(vm.appsSectionCollapsed.value)
    }

    @Test
    fun `setAppsSectionCollapsed writes through to the repository`() = runTest(dispatcher) {
        val (vm, repo) = newVm(collapsed = false)
        advanceUntilIdle()

        vm.setAppsSectionCollapsed(true)
        advanceUntilIdle()

        verify(repo).setAppsSectionCollapsed(true)
    }
}
