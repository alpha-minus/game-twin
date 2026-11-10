package com.twinlore.app.ui.screens

import com.twinlore.app.data.StatusBarMode
import com.twinlore.app.domain.VaultTab
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * VaultSessionScreen's top-status-bar visibility rule, extracted so it has one
 * source of truth: VaultSessionScreen itself calls these two functions (see
 * VaultSessionChrome.kt's doc) instead of hand-rolling the expression, and so
 * does the fold button's own suppression logic in GuideScreen/BrowserScreen --
 * both read the exact same gate this test pins down.
 */
class VaultSessionChromeTest {

    @Test
    fun `visible by default when nothing suppresses it`() {
        assertEquals(
            true,
            vaultSessionTopBarChromeVisible(
                isKeyboardVisible = false,
                browserUrlFieldFocused = false,
                activeTab = VaultTab.Guide,
                anyWallpaperView = false,
                activeTabChromeVisible = true,
            ),
        )
    }

    @Test
    fun `keyboard open hides the bar unless the browser url field is what is focused`() {
        assertEquals(
            false,
            vaultSessionTopBarChromeVisible(
                isKeyboardVisible = true,
                browserUrlFieldFocused = false,
                activeTab = VaultTab.Browser,
                anyWallpaperView = false,
                activeTabChromeVisible = true,
            ),
        )
        assertEquals(
            true,
            vaultSessionTopBarChromeVisible(
                isKeyboardVisible = true,
                browserUrlFieldFocused = true,
                activeTab = VaultTab.Browser,
                anyWallpaperView = false,
                activeTabChromeVisible = true,
            ),
        )
    }

    @Test
    fun `keyboard open hides the guide tab's bar too, since only the browser url field is exempt`() {
        // GuideScreen never sets browserUrlFieldFocused -- this is the exact
        // condition that left the Guide tab with zero fold buttons before
        // shouldShowFoldButtonInHostTopBar learned to read the full gate.
        assertEquals(
            false,
            vaultSessionTopBarChromeVisible(
                isKeyboardVisible = true,
                browserUrlFieldFocused = false,
                activeTab = VaultTab.Guide,
                anyWallpaperView = false,
                activeTabChromeVisible = true,
            ),
        )
    }

    @Test
    fun `achievements wallpaper suppresses the bar only while achievements is the active tab`() {
        assertEquals(
            false,
            vaultSessionTopBarChromeVisible(
                isKeyboardVisible = false,
                browserUrlFieldFocused = false,
                activeTab = VaultTab.Achievements,
                anyWallpaperView = true,
                activeTabChromeVisible = true,
            ),
        )
        // A wallpaper flag left over from a previous Achievements visit must not
        // leak into a different tab's visibility.
        assertEquals(
            true,
            vaultSessionTopBarChromeVisible(
                isKeyboardVisible = false,
                browserUrlFieldFocused = false,
                activeTab = VaultTab.Guide,
                anyWallpaperView = true,
                activeTabChromeVisible = true,
            ),
        )
    }

    @Test
    fun `the active tab's own auto-hide flag is respected`() {
        assertEquals(
            false,
            vaultSessionTopBarChromeVisible(
                isKeyboardVisible = false,
                browserUrlFieldFocused = false,
                activeTab = VaultTab.Map,
                anyWallpaperView = false,
                activeTabChromeVisible = false,
            ),
        )
    }

    @Test
    fun `keepTopBarVisible beats the per-tab auto-hide`() {
        assertEquals(
            true,
            vaultSessionTopBarChromeVisible(
                isKeyboardVisible = false,
                browserUrlFieldFocused = false,
                activeTab = VaultTab.Map,
                anyWallpaperView = false,
                activeTabChromeVisible = false,
                keepTopBarVisible = true,
            ),
        )
    }

    @Test
    fun `keepTopBarVisible does not beat the keyboard`() {
        assertEquals(
            false,
            vaultSessionTopBarChromeVisible(
                isKeyboardVisible = true,
                browserUrlFieldFocused = false,
                activeTab = VaultTab.Guide,
                anyWallpaperView = false,
                activeTabChromeVisible = true,
                keepTopBarVisible = true,
            ),
        )
    }

    @Test
    fun `keepTopBarVisible does not beat the achievements wallpaper`() {
        assertEquals(
            false,
            vaultSessionTopBarChromeVisible(
                isKeyboardVisible = false,
                browserUrlFieldFocused = false,
                activeTab = VaultTab.Achievements,
                anyWallpaperView = true,
                activeTabChromeVisible = true,
                keepTopBarVisible = true,
            ),
        )
    }

    // Hidden is gone: the bar is the chrome gate and nothing else. The mode
    // used to be able to remove it outright, which also removed the only way
    // into Always on Top.
    @Test
    fun `the bar defers entirely to the chrome-visible gate`() {
        assertEquals(true, vaultSessionTopBarRenders(chromeVisible = true))
        assertEquals(false, vaultSessionTopBarRenders(chromeVisible = false))
    }

    @Test
    fun `no status bar mode can remove the bar`() {
        assertEquals(
            "Hidden must not come back without a way into Always on Top",
            3,
            StatusBarMode.entries.size,
        )
        assertEquals(
            listOf(StatusBarMode.STANDARD, StatusBarMode.PERFORMANCE, StatusBarMode.OFF),
            StatusBarMode.entries,
        )
    }

    /**
     * The name is what the meters are given room out of.
     *
     * In Performance the bar carries five meters, a clock and the connection
     * mark on top of the pin and the way into Always on Top, and the name is a
     * weighted child: it takes whatever is left, which on a 640dp handheld is
     * a few characters and an ellipsis. The panel's own bar reached the same
     * place and answered it the same way (see `panelTitleVisible`).
     */
    @Test
    fun `the game name steps aside in Performance and nowhere else`() {
        assertEquals(false, vaultSessionTitleVisible(StatusBarMode.PERFORMANCE, isLandscape = true))
        assertEquals(true, vaultSessionTitleVisible(StatusBarMode.STANDARD, isLandscape = true))
        assertEquals(true, vaultSessionTitleVisible(StatusBarMode.OFF, isLandscape = true))
    }

    /**
     * Portrait has no cluster at all -- LibraryStatusIndicators returns before
     * it draws anything, and the Android system bar carries the clock instead.
     * There is nothing to make room for, so stepping aside there leaves a bar
     * holding nothing whatsoever.
     */
    @Test
    fun `the game name keeps its place in portrait, Performance or not`() {
        assertEquals(true, vaultSessionTitleVisible(StatusBarMode.PERFORMANCE, isLandscape = false))
        assertEquals(true, vaultSessionTitleVisible(StatusBarMode.STANDARD, isLandscape = false))
    }

    /**
     * Off is not Performance. The cluster draws nothing at all there, so there
     * is nothing to make room for and the name is the only thing in the bar
     * worth reading.
     */
    @Test
    fun `every mode but Performance keeps the name`() {
        assertEquals(
            StatusBarMode.entries.filter { it != StatusBarMode.PERFORMANCE },
            StatusBarMode.entries.filter { vaultSessionTitleVisible(it, isLandscape = true) },
        )
    }
}
