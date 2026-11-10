package com.twinlore.app.data

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.twinlore.app.data.SettingsRepository.Companion.dataStore
import com.twinlore.app.data.fold.FoldHandleStyle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Fold-to-edge settings: tab position, dim, tab size, and keep-top-bar-visible.
 * The tab has to come back where it was left after a process restart, or the
 * user has to re-aim it every session.
 *
 * Same caveat as [SettingsRepositoryOpenLastPlayedTest]: the DataStore behind
 * `preferencesDataStore(name = "settings")` is a process-wide singleton, so
 * every assertion here reads back what the same test just wrote. Every test
 * in this class writes at least one fold key to a non-default value, so
 * [resetFoldSettings] restores all five (positionX, positionY, dim, tabSize,
 * keepTopBarVisible) to their defaults after each test - without it, JUnit4's
 * deterministic-but-unspecified method order could run any test after another
 * one that left a key non-default, and a default-value assertion would read
 * stale state instead of the real default.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SettingsRepositoryFoldTest {

    @After
    fun resetFoldSettings() = runTest {
        val repo = SettingsRepository(RuntimeEnvironment.getApplication())
        repo.setFoldPositionX(1f)
        repo.setFoldPositionY(0.5f)
        repo.setFoldHandleAlpha(FoldHandleStyle.DEFAULT_ALPHA)
        repo.setFoldHandleSizeDp(FoldHandleStyle.DEFAULT_SIZE_DP)
        repo.setFoldHandleRecess(FoldHandleStyle.DEFAULT_RECESS)
        // The enum keys two releases of the handle wrote. Cleared as well as
        // the numeric ones, because they are the fallback the numeric ones
        // read through and a leftover would follow the next test in.
        RuntimeEnvironment.getApplication().dataStore.edit {
            it.remove(stringPreferencesKey("fold_dim"))
            it.remove(stringPreferencesKey("fold_tab_size"))
        }
        repo.setKeepTopBarVisible(false)
        repo.setOverlayMode(false)
        repo.setFoldPanelIntroSeen(false)
        repo.setOverlayEnteredBefore(false)
    }

    // The swap hint waits for the second entry, so the repository has to
    // remember that a first one happened across a process restart; otherwise
    // every cold start would be a first press again and the hint never fires.
    @Test
    fun `overlay has not been entered until it has, and that is remembered`() = runTest {
        val repo = SettingsRepository(RuntimeEnvironment.getApplication())
        assertFalse(repo.overlayEnteredBefore.first())

        repo.setOverlayEnteredBefore(true)
        assertTrue(repo.overlayEnteredBefore.first())
        assertTrue(SettingsRepository(RuntimeEnvironment.getApplication()).overlayEnteredBefore.first())
    }

    // Right edge, mid-height: where the old edge-based default put the tab. A
    // centred default would drop the very first fold on top of the game.
    @Test
    fun `position defaults to the right edge at mid height and round trips on both axes`() = runTest {
        val repo = SettingsRepository(RuntimeEnvironment.getApplication())
        assertEquals(1f, repo.foldPositionX.first(), 1e-4f)
        assertEquals(0.5f, repo.foldPositionY.first(), 1e-4f)

        repo.setFoldPositionX(0.2f)
        repo.setFoldPositionY(0.8f)
        assertEquals(0.2f, repo.foldPositionX.first(), 1e-4f)
        assertEquals(0.8f, repo.foldPositionY.first(), 1e-4f)
    }

    @Test
    fun `position is clamped on write so a bad drag cannot strand the tab offscreen`() = runTest {
        val repo = SettingsRepository(RuntimeEnvironment.getApplication())
        repo.setFoldPositionX(-3f)
        repo.setFoldPositionY(7f)
        assertEquals(0f, repo.foldPositionX.first(), 1e-4f)
        assertEquals(1f, repo.foldPositionY.first(), 1e-4f)
    }

    @Test
    fun `handle opacity round trips and snaps to the slider's grain`() = runTest {
        val repo = SettingsRepository(RuntimeEnvironment.getApplication())
        assertEquals(FoldHandleStyle.DEFAULT_ALPHA, repo.foldHandleAlpha.first(), 1e-4f)

        repo.setFoldHandleAlpha(0f)
        assertEquals(0f, repo.foldHandleAlpha.first(), 1e-4f)

        repo.setFoldHandleAlpha(1f)
        assertEquals(1f, repo.foldHandleAlpha.first(), 1e-4f)

        repo.setFoldHandleAlpha(0.63f)
        assertEquals(0.65f, repo.foldHandleAlpha.first(), 1e-4f)
    }

    @Test
    fun `handle size round trips and is clamped to a hittable range`() = runTest {
        val repo = SettingsRepository(RuntimeEnvironment.getApplication())
        assertEquals(FoldHandleStyle.DEFAULT_SIZE_DP, repo.foldHandleSizeDp.first())

        repo.setFoldHandleSizeDp(72)
        assertEquals(72, repo.foldHandleSizeDp.first())

        repo.setFoldHandleSizeDp(4)
        assertEquals(FoldHandleStyle.MIN_SIZE_DP, repo.foldHandleSizeDp.first())

        repo.setFoldHandleSizeDp(4000)
        assertEquals(FoldHandleStyle.MAX_SIZE_DP, repo.foldHandleSizeDp.first())
    }

    /**
     * How small the handle gets while the panel is over it.
     *
     * The default is a full 1f and that is the point: the handle shrank to a
     * hardcoded 0.62 for everybody before this setting existed, and now it
     * keeps the size it was chosen at unless somebody asks for less.
     */
    @Test
    fun `handle reading size defaults to no shrink and round trips`() = runTest {
        val repo = SettingsRepository(RuntimeEnvironment.getApplication())
        assertEquals(1f, repo.foldHandleRecess.first(), 1e-4f)

        repo.setFoldHandleRecess(0.6f)
        assertEquals(0.6f, repo.foldHandleRecess.first(), 1e-4f)

        repo.setFoldHandleRecess(0f)
        assertEquals(FoldHandleStyle.MIN_RECESS, repo.foldHandleRecess.first(), 1e-4f)
    }

    @Test
    fun `handle clock and battery default to off and round trip`() = runTest {
        val repo = SettingsRepository(RuntimeEnvironment.getApplication())
        assertEquals(false, repo.foldHandleClockBattery.first())

        repo.setFoldHandleClockBattery(true)
        assertEquals(true, repo.foldHandleClockBattery.first())
        assertEquals(
            true,
            SettingsRepository(RuntimeEnvironment.getApplication()).foldHandleClockBattery.first(),
        )
    }

    @Test
    fun `all three survive a new repository instance`() = runTest {
        SettingsRepository(RuntimeEnvironment.getApplication()).apply {
            setFoldHandleAlpha(1f)
            setFoldHandleSizeDp(64)
            setFoldHandleRecess(0.5f)
        }

        val afterRestart = SettingsRepository(RuntimeEnvironment.getApplication())
        assertEquals(1f, afterRestart.foldHandleAlpha.first(), 1e-4f)
        assertEquals(64, afterRestart.foldHandleSizeDp.first())
        assertEquals(0.5f, afterRestart.foldHandleRecess.first(), 1e-4f)
    }

    /**
     * The migration, at the level it actually happens.
     *
     * An installed copy has `fold_dim` and `fold_tab_size` holding enum names
     * and no numeric keys at all, because the enums are what two releases of
     * this feature stored. Reading straight past them to the defaults is that
     * user's handle silently resetting to something they did not choose, which
     * for anyone who had it on Transparent means a handle that reappears.
     */
    @Test
    fun `a handle stored as an enum name comes back as the number it meant`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        storeAsEnumsOnly(dim = "TRANSPARENT", size = "BIG")

        val repo = SettingsRepository(context)
        assertEquals(0f, repo.foldHandleAlpha.first(), 1e-4f)
        assertEquals(72, repo.foldHandleSizeDp.first())
    }

    @Test
    fun `a number written over a stored enum name wins`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        storeAsEnumsOnly(dim = "TRANSPARENT", size = "BIG")

        val repo = SettingsRepository(context)
        repo.setFoldHandleAlpha(0.5f)

        assertEquals(0.5f, repo.foldHandleAlpha.first(), 1e-4f)
    }

    /** A name that was never one of the three is not evidence about anything. */
    @Test
    fun `a corrupt enum name falls through to the default rather than to a guess`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        storeAsEnumsOnly(dim = "SPARKLY", size = "ENORMOUS")

        val repo = SettingsRepository(context)
        assertEquals(FoldHandleStyle.DEFAULT_ALPHA, repo.foldHandleAlpha.first(), 1e-4f)
        assertEquals(FoldHandleStyle.DEFAULT_SIZE_DP, repo.foldHandleSizeDp.first())
    }

    /**
     * The store an installed copy actually has: the enum names, and no numeric
     * keys at all. Removing them is the point, not tidiness. The numeric keys
     * are read first and would otherwise answer instead, which would make
     * every migration test below pass whether or not a migration existed.
     */
    private suspend fun storeAsEnumsOnly(dim: String, size: String) {
        RuntimeEnvironment.getApplication().dataStore.edit {
            it.remove(floatPreferencesKey("fold_handle_alpha"))
            it.remove(intPreferencesKey("fold_handle_size_dp"))
            it[stringPreferencesKey("fold_dim")] = dim
            it[stringPreferencesKey("fold_tab_size")] = size
        }
    }

    @Test
    fun `keep top bar visible is off by default and round trips`() = runTest {
        val repo = SettingsRepository(RuntimeEnvironment.getApplication())
        assertFalse(repo.keepTopBarVisible.first())

        repo.setKeepTopBarVisible(true)
        assertTrue(repo.keepTopBarVisible.first())
    }

    // Off by default and off again once overlay is left; both stored as the
    // key's absence so a fresh install and a deliberate exit read the same.
    @Test
    fun `overlay mode is off until entered, survives a restart, and turns off`() = runTest {
        val repo = SettingsRepository(RuntimeEnvironment.getApplication())
        assertEquals(false, repo.overlayMode.first())

        repo.setOverlayMode(true)
        assertEquals(true, repo.overlayMode.first())

        val afterRestart = SettingsRepository(RuntimeEnvironment.getApplication())
        assertEquals(true, afterRestart.overlayMode.first())

        repo.setOverlayMode(false)
        assertEquals(false, repo.overlayMode.first())
    }

    @Test
    fun `the panel intro is unseen until dismissed, then stays dismissed`() = runTest {
        val repo = SettingsRepository(RuntimeEnvironment.getApplication())
        assertFalse(repo.foldPanelIntroSeen.first())

        repo.setFoldPanelIntroSeen(true)
        assertTrue(repo.foldPanelIntroSeen.first())
        assertTrue(SettingsRepository(RuntimeEnvironment.getApplication()).foldPanelIntroSeen.first())
    }

    @Test
    fun `settings survive a new repository instance`() = runTest {
        SettingsRepository(RuntimeEnvironment.getApplication()).apply {
            setFoldPositionX(0.25f)
        }

        val afterRestart = SettingsRepository(RuntimeEnvironment.getApplication())
        assertEquals(0.25f, afterRestart.foldPositionX.first(), 1e-4f)
    }

    // Both axes, because the handle is dragged on both and the bottom edge is
    // the one a user notices losing: a handle left at the bottom that comes
    // back at the default mid-height reads as the app forgetting where it was.
    // The button reads these at press time, so what the store answers after a
    // restart is what the next fold actually puts on screen.
    @Test
    fun `a handle dragged to the bottom is still at the bottom after a restart`() = runTest {
        SettingsRepository(RuntimeEnvironment.getApplication()).apply {
            setFoldPositionX(0f)
            setFoldPositionY(1f)
        }

        val afterRestart = SettingsRepository(RuntimeEnvironment.getApplication())
        assertEquals(0f, afterRestart.foldPositionX.first(), 1e-4f)
        assertEquals("the bottom must survive, not fall back to mid-height",
            1f, afterRestart.foldPositionY.first(), 1e-4f)
    }
}
