package com.twinlore.app.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Round-trip tests for the library wallpaper's own dim and blur.
 *
 * Same shape and the same caveat as [SettingsRepositoryLibraryBackgroundStyleTest]: the
 * DataStore behind `preferencesDataStore(name = "settings")` is a process-wide singleton, so a
 * "never written" default cannot be asserted reliably from a shared test JVM. Everything here
 * only asserts what the same test just wrote, which holds whatever ran before it.
 */
@RunWith(RobolectricTestRunner::class)
class SettingsRepositoryWallpaperTreatmentTest {

    @Test
    fun dim_survives_a_new_instance() = runTest {
        SettingsRepository(RuntimeEnvironment.getApplication()).setLibraryWallpaperDim(0.25f)

        val afterRestart = SettingsRepository(RuntimeEnvironment.getApplication())
        assertEquals(0.25f, afterRestart.libraryWallpaperDim.first(), 0.0001f)
    }

    @Test
    fun blur_survives_a_new_instance() = runTest {
        SettingsRepository(RuntimeEnvironment.getApplication()).setLibraryWallpaperBlur(0.75f)

        val afterRestart = SettingsRepository(RuntimeEnvironment.getApplication())
        assertEquals(0.75f, afterRestart.libraryWallpaperBlur.first(), 0.0001f)
    }

    /** Both are fractions; a slider cannot produce out-of-range values but a bad caller can. */
    @Test
    fun values_are_clamped_to_the_0_to_1_range() = runTest {
        val repo = SettingsRepository(RuntimeEnvironment.getApplication())

        repo.setLibraryWallpaperDim(4f)
        assertEquals(1f, repo.libraryWallpaperDim.first(), 0.0001f)
        repo.setLibraryWallpaperDim(-2f)
        assertEquals(0f, repo.libraryWallpaperDim.first(), 0.0001f)

        repo.setLibraryWallpaperBlur(9f)
        assertEquals(1f, repo.libraryWallpaperBlur.first(), 0.0001f)
        repo.setLibraryWallpaperBlur(-1f)
        assertEquals(0f, repo.libraryWallpaperBlur.first(), 0.0001f)
    }

    @Test
    fun dim_and_blur_are_independent_of_each_other() = runTest {
        val repo = SettingsRepository(RuntimeEnvironment.getApplication())
        repo.setLibraryWallpaperDim(0.3f)
        repo.setLibraryWallpaperBlur(0.9f)

        assertEquals(0.3f, repo.libraryWallpaperDim.first(), 0.0001f)
        assertEquals(0.9f, repo.libraryWallpaperBlur.first(), 0.0001f)
    }
}
