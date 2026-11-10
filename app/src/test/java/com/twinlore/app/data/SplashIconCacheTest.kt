package com.twinlore.app.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * The splash-icon cache is read synchronously on the first frame of cold start
 * to seed the splash with the user's selected icon, so the splash never renders
 * the default (purple + default logo) before the async DataStore selection
 * arrives. It must default to [AppIcon.DEFAULT] and survive across instances
 * (i.e. across process restarts).
 */
@RunWith(RobolectricTestRunner::class)
class SplashIconCacheTest {

    @Test
    fun read_returns_DEFAULT_when_never_written() = runTest {
        val cache = SplashIconCache(RuntimeEnvironment.getApplication())
        assertEquals(AppIcon.DEFAULT, cache.read())
    }

    @Test
    fun written_icon_survives_a_new_instance() = runTest {
        SplashIconCache(RuntimeEnvironment.getApplication()).write(AppIcon.CYBER)
        // A fresh instance models the next cold start reading the persisted value.
        val afterRestart = SplashIconCache(RuntimeEnvironment.getApplication())
        assertEquals(AppIcon.CYBER, afterRestart.read())
    }
}
