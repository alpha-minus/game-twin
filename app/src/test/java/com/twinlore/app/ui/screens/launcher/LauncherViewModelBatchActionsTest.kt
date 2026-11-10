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
import com.twinlore.app.data.local.FavoriteAppEntity
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
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class LauncherViewModelBatchActionsTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    // A non-empty favorites list disables seedDefaultFavorites (existing-user
    // gate), so init does not insert default seed apps that would pollute the
    // insert-count assertions below.
    private fun newVm(
        existingFavorites: List<FavoriteAppEntity> = listOf(FavoriteAppEntity("com.seed.gate")),
    ): Pair<LauncherViewModel, FavoriteAppDao> {
        val context: Context = mock()
        val packageManager: PackageManager = mock()
        whenever(context.packageManager).thenReturn(packageManager)
        whenever(context.packageName).thenReturn("com.twinlore.app")
        whenever(packageManager.getLaunchIntentForPackage(any())).thenReturn(Intent())
        whenever(packageManager.queryIntentActivities(any(), eq(0))).thenReturn(emptyList())

        val favoriteAppDao: FavoriteAppDao = mock()
        whenever(favoriteAppDao.getAll()).thenReturn(flowOf(existingFavorites))
        whenever(favoriteAppDao.getAllPackageNames()).thenReturn(flowOf(existingFavorites.map { it.packageName }))

        val vaultRepository: VaultRepository = mock()
        whenever(vaultRepository.getAllVaultsSortedByLastOpened()).thenReturn(emptyFlow())

        val settingsRepository: SettingsRepository = mock()
        whenever(settingsRepository.launcherApplyThemeToIcons).thenReturn(MutableStateFlow(false))
        whenever(settingsRepository.launcherShowAppNames).thenReturn(MutableStateFlow(true))
        whenever(settingsRepository.launcherIconSize).thenReturn(MutableStateFlow(LauncherIconSize.M))
        whenever(settingsRepository.launcherAutoDetectGames).thenReturn(MutableStateFlow(true))
        whenever(settingsRepository.launcherShowClock).thenReturn(MutableStateFlow(true))
        whenever(settingsRepository.launcherClockSize).thenReturn(MutableStateFlow(LauncherClockSize.M))
        whenever(settingsRepository.appsSectionCollapsed).thenReturn(MutableStateFlow(false))
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
        return vm to favoriteAppDao
    }

    @Test
    fun `pinAll pins each package by append`() = runTest(dispatcher) {
        val (vm, dao) = newVm()
        advanceUntilIdle()

        vm.pinAll(setOf("com.a", "com.b"))
        advanceUntilIdle()

        val captor = argumentCaptor<String>()
        verify(dao, times(2)).pin(captor.capture())
        assertEquals(setOf("com.a", "com.b"), captor.allValues.toSet())
    }

    @Test
    fun `unpinAll deletes each package by name`() = runTest(dispatcher) {
        val (vm, dao) = newVm(
            existingFavorites = listOf(FavoriteAppEntity("com.a"), FavoriteAppEntity("com.b")),
        )
        advanceUntilIdle()

        vm.unpinAll(setOf("com.a", "com.b"))
        advanceUntilIdle()

        verify(dao).deleteByPackageName("com.a")
        verify(dao).deleteByPackageName("com.b")
    }

    @Test
    fun `pinAll with empty set inserts nothing`() = runTest(dispatcher) {
        val (vm, dao) = newVm()
        advanceUntilIdle()

        vm.pinAll(emptySet())
        advanceUntilIdle()

        verify(dao, never()).insert(any())
    }
}
