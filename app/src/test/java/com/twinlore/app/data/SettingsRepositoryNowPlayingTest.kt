package com.twinlore.app.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * The now-playing record and the persisted launch marker: both nullable,
 * null clears the key, and both survive a repository re-instantiation
 * (process restart) because cold start reads them to decide what to open.
 *
 * Same caveat as [SettingsRepositoryDetectedAccentTest]: the DataStore behind
 * `preferencesDataStore(name = "settings")` is a process-wide singleton, so every
 * assertion here reads back what the same test just wrote.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SettingsRepositoryNowPlayingTest {

    @Test
    fun now_playing_round_trips_and_null_clears() = runTest {
        val repo = SettingsRepository(RuntimeEnvironment.getApplication())
        assertNull(repo.nowPlayingVaultId.first())

        repo.setNowPlayingVaultId("vault-1")
        assertEquals("vault-1", repo.nowPlayingVaultId.first())

        repo.setNowPlayingVaultId(null)
        assertNull(repo.nowPlayingVaultId.first())
    }

    @Test
    fun launch_marker_round_trips_and_survives_new_instance() = runTest {
        val repo = SettingsRepository(RuntimeEnvironment.getApplication())
        repo.setLastHandledLaunchMarker("esde_log:123:456")

        val afterRestart = SettingsRepository(RuntimeEnvironment.getApplication())
        assertEquals("esde_log:123:456", afterRestart.lastHandledLaunchMarker.first())

        afterRestart.setLastHandledLaunchMarker(null)
        assertNull(afterRestart.lastHandledLaunchMarker.first())
    }
}
