package com.twinlore.app.ui.screens.library

import com.twinlore.app.ui.screens.library.LibraryFocusRules.AppSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryFocusRulesTest {

    // ---- acceptsFocusReport ---------------------------------------------------------

    @Test fun `focus report is believed when it agrees with intent`() {
        assertTrue(LibraryFocusRules.acceptsFocusReport(intendedIndex = 4, reportingIndex = 4))
    }

    @Test fun `focus report is dropped when it disagrees with intent`() {
        // A neighbour picked up by Compose's fallback search while item 4 was being
        // disposed mid-scroll. Believing it is what dragged selection back one item.
        assertFalse(LibraryFocusRules.acceptsFocusReport(intendedIndex = 4, reportingIndex = 3))
    }

    @Test fun `any focus report is believed when there is no intent yet`() {
        // First landing, touch, restoration from another screen: nothing to contradict.
        assertTrue(LibraryFocusRules.acceptsFocusReport(intendedIndex = null, reportingIndex = 7))
    }

    // ---- shouldRefocusOnSettle ------------------------------------------------------

    @Test fun `settle refocuses when the pager landed on the intended page`() {
        assertTrue(
            LibraryFocusRules.shouldRefocusOnSettle(
                settledPage = 2, intendedPage = 2, surfaceHasFocus = true
            )
        )
    }

    @Test fun `settle does not refocus an intermediate page`() {
        // Two quick d-pad presses: the second animation cancels the first, the pager can
        // settle on page 1 while intent is already 2. Refocusing here is the snap-back.
        assertFalse(
            LibraryFocusRules.shouldRefocusOnSettle(
                settledPage = 1, intendedPage = 2, surfaceHasFocus = true
            )
        )
    }

    @Test fun `settle never steals focus into a surface that does not have it`() {
        assertFalse(
            LibraryFocusRules.shouldRefocusOnSettle(
                settledPage = 2, intendedPage = 2, surfaceHasFocus = false
            )
        )
    }

    // ---- restoreTargetAfterMenu -----------------------------------------------------

    private val pinned = listOf("com.pin.a", "com.pin.b")
    private val emulators = listOf("com.emu.a", "com.emu.b", "com.emu.c")
    private val allApps = listOf("com.app.a", "com.app.b", "com.app.c")

    @Test fun `restores the same package when it is still installed`() {
        assertEquals(
            "com.app.b",
            LibraryFocusRules.restoreTargetAfterMenu(
                closedPackage = "com.app.b",
                sectionAtOpen = AppSection.ALL_APPS, indexAtOpen = 1,
                pinned = pinned, emulators = emulators, allApps = allApps
            )
        )
    }

    @Test fun `restores the same package after Unpin moved it to another section`() {
        // Unpin rebuilds both requester maps, so a captured requester is dead. Looking the
        // package up at restore time finds it in its new home.
        assertEquals(
            "com.pin.a",
            LibraryFocusRules.restoreTargetAfterMenu(
                closedPackage = "com.pin.a",
                sectionAtOpen = AppSection.PINNED, indexAtOpen = 0,
                pinned = emptyList(), emulators = emulators,
                allApps = listOf("com.pin.a") + allApps
            )
        )
    }

    @Test fun `falls back to the same index in the section when the app was uninstalled`() {
        assertEquals(
            "com.app.c",
            LibraryFocusRules.restoreTargetAfterMenu(
                closedPackage = "com.app.b",
                sectionAtOpen = AppSection.ALL_APPS, indexAtOpen = 1,
                pinned = pinned, emulators = emulators,
                allApps = listOf("com.app.a", "com.app.c")   // b removed, c slid into slot 1
            )
        )
    }

    @Test fun `falls back to the last cell of the section when the index is now past the end`() {
        assertEquals(
            "com.emu.a",
            LibraryFocusRules.restoreTargetAfterMenu(
                closedPackage = "com.emu.b",
                sectionAtOpen = AppSection.EMULATORS, indexAtOpen = 1,
                pinned = pinned, emulators = listOf("com.emu.a"), allApps = allApps
            )
        )
    }

    @Test fun `falls back to the first All apps cell when the section emptied out`() {
        assertEquals(
            "com.app.a",
            LibraryFocusRules.restoreTargetAfterMenu(
                closedPackage = "com.pin.a",
                sectionAtOpen = AppSection.PINNED, indexAtOpen = 0,
                pinned = emptyList(), emulators = emulators, allApps = allApps
            )
        )
    }

    @Test fun `returns null when there is nothing left to focus`() {
        assertNull(
            LibraryFocusRules.restoreTargetAfterMenu(
                closedPackage = "com.pin.a",
                sectionAtOpen = AppSection.PINNED, indexAtOpen = 0,
                pinned = emptyList(), emulators = emptyList(), allApps = emptyList()
            )
        )
    }

    // ---- item indices (top bar is item 0) -------------------------------------------

    @Test fun `the vault carousel sits directly under the top bar`() {
        // Ties the constant to the grid's real order: bar at 0, carousel at 1. If a section is
        // ever inserted above the carousel, firstAllAppsItemIndex's own tests move with it and
        // this one pins the other end.
        assertEquals(1, LibraryFocusRules.VAULT_ITEM_INDEX)
        assertTrue(
            LibraryFocusRules.firstAllAppsItemIndex(true, 0, 0) > LibraryFocusRules.VAULT_ITEM_INDEX
        )
    }

    @Test fun `first All apps cell counts every section above it`() {
        // bar(1) + carousel(1) + divider(1) + pinned(2) + divider(1) + "Emulators"(1)
        // + emulators(3) + browse tile(1) + divider(1) + "All apps"(1) = 13
        assertEquals(
            13,
            LibraryFocusRules.firstAllAppsItemIndex(
                hasVaults = true, pinnedCount = 2, emulatorCount = 3
            )
        )
    }

    @Test fun `an empty library replaces carousel and divider with a single hint`() {
        // bar(1) + hint(1) + pinned(0) + divider(1) + "Emulators"(1) + emulators(0)
        // + browse tile(1) + divider(1) + "All apps"(1) = 7
        assertEquals(
            7,
            LibraryFocusRules.firstAllAppsItemIndex(
                hasVaults = false, pinnedCount = 0, emulatorCount = 0
            )
        )
    }

    @Test fun `pinned apps push the All apps header down one slot each`() {
        val none = LibraryFocusRules.firstAllAppsItemIndex(true, pinnedCount = 0, emulatorCount = 0)
        val four = LibraryFocusRules.firstAllAppsItemIndex(true, pinnedCount = 4, emulatorCount = 0)
        assertEquals(none + 4, four)
    }

    @Test fun `pinned cells start straight after the carousel and its divider`() {
        // bar(1) + carousel(1) + divider(1) = 3
        assertEquals(3, LibraryFocusRules.firstPinnedItemIndex(hasVaults = true))
        // bar(1) + hint(1) = 2
        assertEquals(2, LibraryFocusRules.firstPinnedItemIndex(hasVaults = false))
    }

    @Test fun `emulator cells start after the pinned cells and two chrome items`() {
        // firstPinned(3) + pinned(2) + divider(1) + "Emulators"(1) = 7
        assertEquals(7, LibraryFocusRules.firstEmulatorItemIndex(hasVaults = true, pinnedCount = 2))
    }

    @Test fun `the three section starts are one chain, so a new section is counted once`() {
        // Pins the arithmetic the three helpers share: each start is the previous one plus
        // that section's cells plus the fixed chrome between them.
        val pinned = LibraryFocusRules.firstPinnedItemIndex(hasVaults = true)
        val emulators = LibraryFocusRules.firstEmulatorItemIndex(hasVaults = true, pinnedCount = 2)
        val allApps = LibraryFocusRules.firstAllAppsItemIndex(
            hasVaults = true, pinnedCount = 2, emulatorCount = 3
        )
        assertEquals(pinned + 2 + 2, emulators)
        assertEquals(emulators + 3 + 3, allApps)
    }

    // ---- appItemIndex ---------------------------------------------------------------

    @Test fun `an app cell sits at its section start plus its position in the section`() {
        val args = Triple(true, 2, 3)   // hasVaults, pinnedCount, emulatorCount
        assertEquals(
            LibraryFocusRules.firstPinnedItemIndex(args.first) + 1,
            LibraryFocusRules.appItemIndex(
                AppSection.PINNED, indexInSection = 1,
                hasVaults = args.first, pinnedCount = args.second, emulatorCount = args.third
            )
        )
        assertEquals(
            LibraryFocusRules.firstEmulatorItemIndex(args.first, args.second) + 2,
            LibraryFocusRules.appItemIndex(
                AppSection.EMULATORS, indexInSection = 2,
                hasVaults = args.first, pinnedCount = args.second, emulatorCount = args.third
            )
        )
        assertEquals(
            LibraryFocusRules.firstAllAppsItemIndex(args.first, args.second, args.third) + 4,
            LibraryFocusRules.appItemIndex(
                AppSection.ALL_APPS, indexInSection = 4,
                hasVaults = args.first, pinnedCount = args.second, emulatorCount = args.third
            )
        )
    }

    @Test fun `Pin moves a cell from deep in All apps to near the top of the page`() {
        // The case the context-menu restore exists for: before Pin the cell is item 18, after
        // Pin it is item 3 — hundreds of pixels apart, so the restore has to scroll, not just
        // retry requestFocus on a cell that is no longer composed.
        val beforePin = LibraryFocusRules.appItemIndex(
            AppSection.ALL_APPS, indexInSection = 7,
            hasVaults = true, pinnedCount = 0, emulatorCount = 3
        )
        val afterPin = LibraryFocusRules.appItemIndex(
            AppSection.PINNED, indexInSection = 0,
            hasVaults = true, pinnedCount = 1, emulatorCount = 3
        )
        assertEquals(18, beforePin)
        assertEquals(3, afterPin)
        assertTrue(afterPin < beforePin)
    }

    // ---- launcherLanding ------------------------------------------------------------

    @Test fun `cycling into Apps lands on the vault carousel`() {
        assertEquals(
            LibraryFocusRules.LauncherLanding.VAULT_CAROUSEL,
            LibraryFocusRules.launcherLanding(hasVaults = true)
        )
    }

    @Test fun `with no vaults there is no carousel to land on`() {
        assertEquals(
            LibraryFocusRules.LauncherLanding.APP_GRID,
            LibraryFocusRules.launcherLanding(hasVaults = false)
        )
    }
}
