package com.twinlore.app.data

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.twinlore.app.data.SettingsRepository.Companion.dataStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * The one-time Cocoon icon pack hint flag: unset until the hint is shown once,
 * and never unset again afterward.
 *
 * Same caveat as the fold settings tests: the DataStore behind
 * `preferencesDataStore(name = "settings")` is a process-wide singleton, so
 * this class resets the flag after each test rather than relying on ordering.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SettingsRepositoryCocoonHintTest {

    @After
    fun resetCocoonHintFlag() = runTest {
        // There is no unsetter by design (the flag only ever moves one way in
        // the product), so the test resets storage directly.
        RuntimeEnvironment.getApplication().dataStore.edit {
            it.remove(booleanPreferencesKey("cocoon_icon_pack_hint_shown"))
        }
    }

    @Test
    fun `defaults to not shown`() = runTest {
        val repo = SettingsRepository(RuntimeEnvironment.getApplication())
        assertFalse(repo.cocoonIconPackHintShown.first())
    }

    @Test
    fun `recording the hint as shown sticks, including across a new repository instance`() = runTest {
        val repo = SettingsRepository(RuntimeEnvironment.getApplication())
        repo.setCocoonIconPackHintShown()
        assertTrue(repo.cocoonIconPackHintShown.first())

        val afterRestart = SettingsRepository(RuntimeEnvironment.getApplication())
        assertTrue(afterRestart.cocoonIconPackHintShown.first())
    }
}
