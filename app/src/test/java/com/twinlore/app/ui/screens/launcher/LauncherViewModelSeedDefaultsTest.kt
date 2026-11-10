package com.twinlore.app.ui.screens.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.twinlore.app.data.DualDisplayLauncher
import com.twinlore.app.data.GameLauncher
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Verifies that seedDefaultFavorites only inserts packages whose launch
 * intent resolves, and that it respects the existing-user gate.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LauncherViewModelSeedDefaultsTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun newVm(
        installedPackages: Set<String>,
        existingFavorites: List<FavoriteAppEntity> = emptyList(),
    ): Pair<LauncherViewModel, FavoriteAppDao> {
        val context: Context = mock()
        val packageManager: PackageManager = mock()
        whenever(context.packageManager).thenReturn(packageManager)
        whenever(context.packageName).thenReturn("com.twinlore.app")

        // getLaunchIntentForPackage returns non-null only for installed packages
        whenever(packageManager.getLaunchIntentForPackage(any())).thenAnswer { inv ->
            val pkg = inv.getArgument<String>(0)
            if (pkg in installedPackages) Intent() else null
        }
        // queryIntentActivities used by refreshApps — return empty so init's
        // refresh path is a no-op for these tests.
        whenever(packageManager.queryIntentActivities(any(), eq(0))).thenReturn(emptyList())

        val favoriteAppDao: FavoriteAppDao = mock()
        whenever(favoriteAppDao.getAll()).thenReturn(flowOf(existingFavorites))
        whenever(favoriteAppDao.getAllPackageNames()).thenReturn(flowOf(existingFavorites.map { it.packageName }))

        val vaultRepository: VaultRepository = mock()
        whenever(vaultRepository.getAllVaultsSortedByLastOpened()).thenReturn(emptyFlow())

        val settingsRepository: SettingsRepository = mock()
        whenever(settingsRepository.launcherApplyThemeToIcons).thenReturn(MutableStateFlow(false))
        whenever(settingsRepository.launcherShowAppNames).thenReturn(MutableStateFlow(true))
        whenever(settingsRepository.launcherIconSize).thenReturn(MutableStateFlow(com.twinlore.app.data.LauncherIconSize.M))
        whenever(settingsRepository.launcherAutoDetectGames).thenReturn(MutableStateFlow(true))
        whenever(settingsRepository.launcherShowClock).thenReturn(MutableStateFlow(true))
        whenever(settingsRepository.launcherClockSize).thenReturn(MutableStateFlow(com.twinlore.app.data.LauncherClockSize.M))
        whenever(settingsRepository.appsSectionCollapsed).thenReturn(MutableStateFlow(false))
        whenever(settingsRepository.catalogDeviceType).thenReturn(emptyFlow())

        val gameLauncher: GameLauncher = mock()
        val recommendedAppsCatalog: RecommendedAppsCatalog = mock()

        val vm = LauncherViewModel(
            context = context,
            favoriteAppDao = favoriteAppDao,
            vaultRepository = vaultRepository,
            settingsRepository = settingsRepository,
            gameLauncher = gameLauncher,
            recommendedAppsCatalog = recommendedAppsCatalog,
            dualDisplayLauncher = mock(),
        )
        return vm to favoriteAppDao
    }

    @Test
    fun `seeds Settings, Files, YouTube, Spotify, Discord when all installed`() = runTest(dispatcher) {
        val installed = setOf(
            "com.android.settings",
            "com.google.android.apps.nbu.files",
            "com.google.android.youtube",
            "com.spotify.music",
            "com.discord",
        )
        val (_, dao) = newVm(installedPackages = installed)
        advanceUntilIdle()

        val captor = argumentCaptor<String>()
        verify(dao, org.mockito.kotlin.atLeastOnce()).pin(captor.capture())
        val inserted = captor.allValues
        assertTrue("Settings should be seeded", "com.android.settings" in inserted)
        assertTrue("Google Files should be seeded", "com.google.android.apps.nbu.files" in inserted)
        assertTrue("YouTube should be seeded", "com.google.android.youtube" in inserted)
        assertTrue("Spotify should be seeded", "com.spotify.music" in inserted)
        assertTrue("Discord should be seeded", "com.discord" in inserted)
    }

    @Test
    fun `skips packages whose launch intent is null`() = runTest(dispatcher) {
        // Only Settings installed — Files variants, YouTube, Spotify, Discord all missing
        val (_, dao) = newVm(installedPackages = setOf("com.android.settings"))
        advanceUntilIdle()

        val captor = argumentCaptor<String>()
        verify(dao, org.mockito.kotlin.atLeastOnce()).pin(captor.capture())
        assertEquals(listOf("com.android.settings"), captor.allValues)
    }

    @Test
    fun `does not seed when favorites already exist`() = runTest(dispatcher) {
        val (_, dao) = newVm(
            installedPackages = setOf("com.android.settings", "com.discord"),
            existingFavorites = listOf(FavoriteAppEntity("com.example.foo")),
        )
        advanceUntilIdle()

        verify(dao, org.mockito.kotlin.never()).pin(any())
    }
}
