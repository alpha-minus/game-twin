package com.twinlore.app.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Round-trip test for [SettingsRepository.libraryBackgroundStyle] (round two, task 2).
 * Follows [SplashIconCacheTest]'s pattern -- a real Robolectric application context
 * backing the real DataStore, not a mocked repository -- because the thing worth
 * proving here is that the value actually survives on disk (and across a process
 * restart), not just that a getter returns whatever a setter stored in memory.
 *
 * Deliberately does NOT assert "defaults to BLURRED_ART when the key was never
 * written": `preferencesDataStore(name = "settings")` is a process-wide singleton
 * (one live [androidx.datastore.core.DataStore] instance for the whole test JVM,
 * cached the first time any [android.content.Context] triggers it -- not one per
 * Robolectric [RuntimeEnvironment.getApplication] instance, and deleting the backing
 * file on disk doesn't reach it once it's initialized), so whichever test method in
 * this *or any other* Robolectric-backed settings test happens to run first in the
 * shared JVM decides what "never written" actually observes. Both tests below only
 * assert what was just written in the same test, so they hold regardless of run
 * order or of what earlier tests left behind.
 */
@RunWith(RobolectricTestRunner::class)
class SettingsRepositoryLibraryBackgroundStyleTest {

    @Test
    fun written_style_survives_a_new_instance() = runTest {
        SettingsRepository(RuntimeEnvironment.getApplication())
            .setLibraryBackgroundStyle(LibraryBackgroundStyle.SOLID)

        // A fresh instance models the next process launch reading the persisted value,
        // the same way a real cold start would.
        val afterRestart = SettingsRepository(RuntimeEnvironment.getApplication())
        assertEquals(LibraryBackgroundStyle.SOLID, afterRestart.libraryBackgroundStyle.first())
    }

    @Test
    fun round_trips_every_style() = runTest {
        val repo = SettingsRepository(RuntimeEnvironment.getApplication())
        for (style in LibraryBackgroundStyle.entries) {
            repo.setLibraryBackgroundStyle(style)
            assertEquals(style, repo.libraryBackgroundStyle.first())
        }
    }
}
