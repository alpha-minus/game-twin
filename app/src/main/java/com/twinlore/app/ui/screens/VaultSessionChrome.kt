package com.twinlore.app.ui.screens

import com.twinlore.app.data.StatusBarMode
import com.twinlore.app.domain.VaultTab

/**
 * The vault session's shared top-status-bar visibility rule (the game-name /
 * clock-battery-wifi strip rendered by `VaultSessionTopBar`).
 *
 * Extracted as a pure function — no Compose, no Android types — so
 * `VaultSessionScreen` (the actual renderer, at its `topBarVisible` and
 * `statusBarMode != HIDDEN` checks) and any other screen that needs to know
 * whether that bar is on screen (the fold button's own suppression logic in
 * `GuideScreen`/`BrowserScreen`) read the exact same rule, instead of two
 * hand-copies that can silently drift apart.
 *
 * Two gates:
 *  - [vaultSessionTopBarChromeVisible]: the outer condition — keyboard open
 *    (unless the browser URL field is what's focused), the Achievements
 *    wallpaper screensaver, and the active tab's own auto-hide-on-idle flag.
 *  - (removed) the inner Hidden-mode override; see
 *    `statusBarMode == HIDDEN` override, which removes the bar outright
 *    regardless of the outer gate.
 *  - [vaultSessionTopBarRenders]: "is that bar really on screen", which is
 *    the signal other screens need. Since Hidden was removed it is the chrome
 *    gate and nothing more.
 *
 * The `keepTopBarVisible` setting (`SettingsRepository.keepTopBarVisible`)
 * overrides only the active tab's own auto-hide term ([activeTabChromeVisible]
 * below) — never the keyboard or Achievements-wallpaper terms.
 */
fun vaultSessionTopBarChromeVisible(
    isKeyboardVisible: Boolean,
    browserUrlFieldFocused: Boolean,
    activeTab: VaultTab,
    anyWallpaperView: Boolean,
    activeTabChromeVisible: Boolean,
    keepTopBarVisible: Boolean = false,
): Boolean =
    (!isKeyboardVisible || browserUrlFieldFocused) &&
        !(activeTab == VaultTab.Achievements && anyWallpaperView) &&
        (activeTabChromeVisible || keepTopBarVisible)

/**
 * Whether `VaultSessionTopBar` actually renders, which is now exactly the
 * chrome gate: [vaultSessionTopBarChromeVisible].
 *
 * There was a second half, a `statusBarMode != HIDDEN` override applied inside
 * the bar's own AnimatedVisibility. Hidden is gone, because that bar carries
 * the way into Always on Top and a setting that removed it removed the
 * feature. Nothing gates the bar on the mode any more, so the two halves are
 * one again.
 *
 * Kept as a named function rather than inlined: it is what GuideScreen,
 * BrowserScreen and the fold button all read to answer "is that bar really on
 * screen", and a session must never be left with zero fold buttons.
 */
fun vaultSessionTopBarRenders(chromeVisible: Boolean): Boolean = chromeVisible

/**
 * Whether the bar names the game it belongs to.
 *
 * Not in Performance. The name is the bar's one weighted child, so it is
 * handed whatever the fixed content leaves: in Performance that content is
 * five meters, a clock and the connection mark on top of the pin, the divider
 * and the way into Always on Top, and on a 1080p handheld what is left of the
 * name is a few characters and an ellipsis. A name cut that short names
 * nothing, and the width is better spent on the readings the mode exists for.
 *
 * The panel's bar reached this exact place first and answered it the same way
 * — see `panelTitleVisible` in `FoldGuideTopBar` — after the same bar had
 * already given up its battery pill and its connection mark for the same
 * reason. This is that rule, for the app's own bar.
 *
 * Off is not Performance: the cluster draws nothing at all there, so there is
 * nothing to make room for and the name is the only thing in the bar worth
 * reading. [isLandscape] is the same test for the same reason —
 * `LibraryStatusIndicators` returns before it draws anything in portrait, where
 * the Android system bar carries the clock instead, so a name that stepped
 * aside there would leave a bar holding nothing at all.
 *
 * The doc switcher does not go with the name. It is the Guide tab's only way
 * to change document, so in Performance its chevron stays behind on its own as
 * the tap target — a control that is gone is a feature that is gone, which is
 * not what dropping a title is supposed to cost.
 */
fun vaultSessionTitleVisible(
    statusBarMode: StatusBarMode,
    isLandscape: Boolean,
): Boolean = !(isLandscape && statusBarMode == StatusBarMode.PERFORMANCE)
