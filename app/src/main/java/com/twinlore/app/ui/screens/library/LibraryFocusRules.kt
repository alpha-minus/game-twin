package com.twinlore.app.ui.screens.library

/**
 * Every focus and item-index decision the library's scrolling views make, as pure functions.
 *
 * These used to be scattered across racing `LaunchedEffect`s in LibraryScreen, where several
 * writers could each set focus and the last one to run won. Pulling the decisions out here
 * means there is one answer per question, and it can be tested without a device.
 */
object LibraryFocusRules {

    /** Which of the launcher's three app sections a cell belongs to. */
    enum class AppSection { PINNED, EMULATORS, ALL_APPS }

    /**
     * Whether a focus callback from a composable should be written through to shared state.
     *
     * A deliberate move (d-pad press, touch settle) records its target as `intendedIndex`.
     * While a scroll animation runs, the item that currently holds focus can be scrolled out
     * of the viewport and disposed; Compose then releases focus and its fallback search hands
     * it to whichever neighbour is still composed. That neighbour reports itself, and believing
     * it is what made the selection slide back to the previous item.
     *
     * A null intent means nothing has claimed the surface yet (first landing, restoration from
     * another screen), so any report is the best information available.
     */
    fun acceptsFocusReport(intendedIndex: Int?, reportingIndex: Int): Boolean =
        intendedIndex == null || intendedIndex == reportingIndex

    /**
     * Whether a pager settling should pull Compose focus to the settled page.
     *
     * Each d-pad press starts its own `animateScrollToPage`. Two presses in quick succession
     * means the second cancels the first, `isScrollInProgress` can drop, and the pager reports
     * a settle on an intermediate page. Only a settle that agrees with intent is real.
     */
    fun shouldRefocusOnSettle(settledPage: Int, intendedPage: Int, surfaceHasFocus: Boolean): Boolean =
        surfaceHasFocus && settledPage == intendedPage

    /**
     * The package to focus after the launcher's app context menu closes.
     *
     * Resolved by package rather than by held FocusRequester, because Pin/Unpin rebuilds the
     * requester maps (they are remembered off the app lists) and moves the app between
     * sections. App info and Uninstall leave the app entirely, so on return the package may be
     * gone; the fallback then walks the section it was in, then All apps.
     *
     * Returns null only when the device has no launchable apps left to focus at all.
     */
    fun restoreTargetAfterMenu(
        closedPackage: String,
        sectionAtOpen: AppSection,
        indexAtOpen: Int,
        pinned: List<String>,
        emulators: List<String>,
        allApps: List<String>,
    ): String? {
        // Still installed somewhere: focus it, wherever Pin/Unpin has moved it to.
        if (closedPackage in pinned || closedPackage in emulators || closedPackage in allApps) {
            return closedPackage
        }
        val section = when (sectionAtOpen) {
            AppSection.PINNED -> pinned
            AppSection.EMULATORS -> emulators
            AppSection.ALL_APPS -> allApps
        }
        // Same slot (the next app slid into it), else the section's last cell.
        section.getOrNull(indexAtOpen)?.let { return it }
        section.lastOrNull()?.let { return it }
        return allApps.firstOrNull()
    }

    /**
     * Item index of the launcher grid's vault carousel: directly under the top bar. When the
     * library is empty this slot holds the empty-library hint instead, so the index is the same
     * either way and callers need not distinguish.
     */
    const val VAULT_ITEM_INDEX = 1

    /**
     * Item index of the first pinned-app cell in the launcher's LazyVerticalGrid.
     *
     * MIRRORS THE GRID'S ITEM ORDER, and so do [firstEmulatorItemIndex] and
     * [firstAllAppsItemIndex] below: each is the previous one plus that section's cells plus
     * the fixed chrome between them, so a section added anywhere above only has to be counted
     * once. A cell that is not on screen has no attached FocusRequester, so these are what let
     * the launcher scroll a cell in before trying to focus it.
     */
    fun firstPinnedItemIndex(hasVaults: Boolean): Int {
        var n = 1                                   // top bar
        n += if (hasVaults) 2 else 1                // carousel + divider, or the empty hint
        return n
    }

    /** Item index of the first emulator cell. See [firstPinnedItemIndex]. */
    fun firstEmulatorItemIndex(hasVaults: Boolean, pinnedCount: Int): Int {
        var n = firstPinnedItemIndex(hasVaults)
        n += pinnedCount                            // pinned app cells
        n += 2                                      // divider, "Emulators" header
        return n
    }

    /**
     * Item index of the first "All apps" cell in the launcher's LazyVerticalGrid.
     *
     * See [firstPinnedItemIndex]. The loading spinner is deliberately absent: it only renders
     * while the app list is empty, and this is only used once there is a first app to scroll to.
     */
    fun firstAllAppsItemIndex(hasVaults: Boolean, pinnedCount: Int, emulatorCount: Int): Int {
        var n = firstEmulatorItemIndex(hasVaults, pinnedCount)
        n += emulatorCount                          // emulator cells
        n += 3                                      // browse tile, divider, "All apps" header
        return n
    }

    /**
     * Grid item index of one app cell, from the section it is in and its position within it.
     *
     * The context-menu restore needs this: Pin moves a cell to the top of the page and Unpin
     * drops it into All apps, so the cell to restore focus to is usually scrolled out of the
     * lazy grid and therefore disposed, with no requester to focus until it is scrolled back.
     */
    fun appItemIndex(
        section: AppSection,
        indexInSection: Int,
        hasVaults: Boolean,
        pinnedCount: Int,
        emulatorCount: Int,
    ): Int = when (section) {
        AppSection.PINNED -> firstPinnedItemIndex(hasVaults) + indexInSection
        AppSection.EMULATORS -> firstEmulatorItemIndex(hasVaults, pinnedCount) + indexInSection
        AppSection.ALL_APPS ->
            firstAllAppsItemIndex(hasVaults, pinnedCount, emulatorCount) + indexInSection
    }

    /** Where the launcher parks scroll and focus when it opens. */
    enum class LauncherLanding { APP_GRID, VAULT_CAROUSEL }

    /**
     * Every way in (L1/R1 cycling, the view-mode menu, returning from a vault) opens on the
     * vault carousel like every other view mode.
     *
     * An empty library has no carousel to land on — its slot holds the empty-library hint —
     * so it falls back to the app grid rather than leaving focus nowhere.
     */
    fun launcherLanding(hasVaults: Boolean): LauncherLanding =
        if (!hasVaults) LauncherLanding.APP_GRID else LauncherLanding.VAULT_CAROUSEL
}
