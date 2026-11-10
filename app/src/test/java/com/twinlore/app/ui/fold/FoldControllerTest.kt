package com.twinlore.app.ui.fold

import com.twinlore.app.data.fold.FoldHandleLook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The fold state machine. The rule that matters most: the app must never end
 * up backgrounded without a visible tab, because on the second screen of a
 * handheld there is no other way back to it.
 */
class FoldControllerTest {

    private class FakePlatform(
        var overlaysAllowed: Boolean = true,
        /** The tab refuses to appear: no display, or the overlay window bounced. */
        var tabShows: Boolean = true,
        /** Backgrounding refuses to happen: no activity held, or the system refused. */
        var moveToBackSucceeds: Boolean = true,
        /**
         * The app turns out to be in front already when asked to come back:
         * moveTaskToBack(true) reported success but the task stayed visible, so
         * no resume will ever follow the relaunch.
         */
        var appAlreadyInFront: Boolean = false,
        /** What [mainDisplayId] reports; null models a device that cannot say. */
        var mainDisplay: Int? = 0,
        /**
         * Every display this device has.
         *
         * A list rather than a single "other display", because a device's
         * displays are a property of the device and not of where you happen to
         * be standing. The version this replaced was `otherDisplay?.takeIf { it
         * != currentDisplayId }`, which made `otherDisplayId(0)` null on a
         * device whose whole point is two screens, and hid a real ping-pong:
         * on hardware the pair is symmetric, so a rescue from 4 to 0 leaves 4
         * as 0's "other" and a second tap can hand the app straight back.
         */
        var displays: List<Int> = listOf(0, 4),
        /** The panel's window is refused: no display, or the overlay bounced. */
        var panelShows: Boolean = true,
        /** Moving the handle to another display does not take. */
        var moveSucceeds: Boolean = true,
    ) : FoldPlatform {
        val calls = mutableListOf<String>()
        var lastShownDisplayId: Int? = null
        var lastShownX: Float? = null
        var lastShownY: Float? = null
        var lastShownLook: FoldHandleLook? = null
        var lastFrontDisplayId: Int? = null
        var lastPanelDisplayId: Int? = null
        var lastMovedToDisplayId: Int? = null

        override fun canDrawOverlays(): Boolean = overlaysAllowed

        override fun showTab(
            displayId: Int,
            xFraction: Float,
            yFraction: Float,
            look: FoldHandleLook,
        ): Boolean {
            calls += "showTab"
            lastShownDisplayId = displayId
            lastShownX = xFraction
            lastShownY = yFraction
            lastShownLook = look
            return tabShows
        }

        override fun hideTab() {
            calls += "hideTab"
        }

        override fun closeApp() {
            calls += "closeApp"
        }

        override fun moveAppToBack(): Boolean {
            calls += "moveAppToBack"
            return moveToBackSucceeds
        }

        override fun bringAppToFront(displayId: Int): BringToFrontResult {
            calls += "bringAppToFront"
            lastFrontDisplayId = displayId
            return BringToFrontResult(wasAlreadyInFront = appAlreadyInFront)
        }



        override fun showGuidePanel(): Boolean {
            calls += "showGuidePanel"
            // The panel is built on the handle's own window context, so it
            // always lands where the handle is.
            lastPanelDisplayId = lastShownDisplayId
            return panelShows
        }

        override fun hideGuidePanel() {
            calls += "hideGuidePanel"
        }

        override fun parkGuidePanel() {
            calls += "parkGuidePanel"
        }

        override fun moveToDisplay(displayId: Int): Boolean {
            calls += "moveToDisplay"
            lastMovedToDisplayId = displayId
            if (moveSucceeds) lastShownDisplayId = displayId
            return moveSucceeds
        }

        override fun mainDisplayId(): Int? = mainDisplay

        // The same rule AndroidFoldPlatform uses: the lowest display that is not
        // this one. Symmetric, like the hardware: otherDisplayId(0) == 4 and
        // otherDisplayId(4) == 0 on a two-screen handheld.
        override fun otherDisplayId(currentDisplayId: Int): Int? =
            displays.filter { it != currentDisplayId }.minOrNull()
    }


    private fun controller(platform: FakePlatform, memory: FakeOverlayMemory = FakeOverlayMemory()): FoldController =
        FoldController(platform, memory)

    /** A controller already folded on [displayId], with the call log cleared. */
    private fun foldedController(
        platform: FakePlatform,
        displayId: Int = 4,
    ): FoldController = controller(platform).apply {
        fold(displayId, 1f, 0.5f, FoldHandleLook(sizeDp = 44))
        platform.calls.clear()
    }


    @Test
    fun `folding shows the tab before backgrounding the app`() {
        val platform = FakePlatform()
        val controller = controller(platform)

        val result = controller.fold(displayId = 2, xFraction = 0.4f, yFraction = 0.5f, look = FoldHandleLook(sizeDp = 44))

        assertEquals(FoldResult.FOLDED, result)
        assertEquals(listOf("showTab", "moveAppToBack"), platform.calls)
        assertTrue(controller.isFolded.value)
        // The button's press: the handle and nothing else. The panel is a tap
        // on the handle away, not part of getting out of the way.
        assertFalse(controller.isGuidePanelOpen.value)
    }

    @Test
    fun `folding passes the tab its position, dim and size`() {
        val platform = FakePlatform(mainDisplay = 0)
        val controller = controller(platform)

        controller.fold(
            displayId = 7, xFraction = 0.9f, yFraction = 0.2f,
            look = FoldHandleLook.of(alpha = 0f, sizeDp = 72, recessScale = 1f),
        )

        // The display is the one the button was pressed on. Everything else is
        // passed through.
        assertEquals(7, platform.lastShownDisplayId)
        assertEquals(0.9f, platform.lastShownX!!, 1e-4f)
        assertEquals(0.2f, platform.lastShownY!!, 1e-4f)
        assertEquals(0f, platform.lastShownLook!!.alpha, 1e-4f)
        assertEquals(72, platform.lastShownLook!!.sizeDp)
    }

    // The tab used to outlive the unfold by a three second window on any
    // display but the built-in one. It does not any more: a handle still on
    // screen after the app is back reads as a bug rather than an escape hatch,
    // and the case it covered is now one the user has to opt into.
    @Test
    fun `a resume takes the tab down even on a display that is not the top one`() {
        val platform = FakePlatform(mainDisplay = 0)
        val controller = controller(platform)
        controller.fold(
            displayId = 4, xFraction = 0.5f, yFraction = 0.5f,
            look = FoldHandleLook(sizeDp = 44),
        )
        controller.unfold()
        platform.calls.clear()

        controller.onActivityResumed()

        assertEquals(listOf("hideTab"), platform.calls)
        assertFalse(controller.isFolded.value)
    }

    @Test
    fun `folding without the overlay permission does nothing at all`() {
        val platform = FakePlatform(overlaysAllowed = false)
        val controller = controller(platform)

        val result = controller.fold(displayId = 0, xFraction = 0.5f, yFraction = 0.5f, look = FoldHandleLook(sizeDp = 44))

        assertEquals(FoldResult.PERMISSION_MISSING, result)
        assertEquals(emptyList<String>(), platform.calls)
        assertFalse(controller.isFolded.value)
    }

    @Test
    fun `a tab that never appears cancels the fold instead of backgrounding the app`() {
        val platform = FakePlatform(tabShows = false)
        val controller = controller(platform)

        val result = controller.fold(displayId = 2, xFraction = 0.5f, yFraction = 0.5f, look = FoldHandleLook(sizeDp = 44))

        assertEquals(FoldResult.TAB_FAILED, result)
        // The app stays in front. Backgrounding it here is the one thing that
        // could strand it on a second screen with nothing to tap.
        assertEquals(listOf("showTab"), platform.calls)
        assertFalse(platform.calls.contains("moveAppToBack"))
        assertFalse(controller.isFolded.value)
    }

    @Test
    fun `a fold that fails to background the app takes the tab back down instead of getting stuck foregrounded`() {
        val platform = FakePlatform(moveToBackSucceeds = false)
        val controller = controller(platform)

        val result = controller.fold(displayId = 2, xFraction = 0.5f, yFraction = 0.5f, look = FoldHandleLook(sizeDp = 44))

        assertEquals(FoldResult.TAB_FAILED, result)
        // Showing then un-showing, in that order: the tab must not linger on
        // an app that is still in front, because nothing would ever be able
        // to hide it again once isFolded never became true.
        assertEquals(listOf("showTab", "moveAppToBack", "hideTab"), platform.calls)
        assertFalse(controller.isFolded.value)
    }

    @Test
    fun `a failed fold leaves the controller able to fold again`() {
        val platform = FakePlatform(tabShows = false)
        val controller = controller(platform)
        controller.fold(displayId = 2, xFraction = 0.5f, yFraction = 0.5f, look = FoldHandleLook(sizeDp = 44))
        platform.calls.clear()

        platform.tabShows = true
        val result = controller.fold(displayId = 4, xFraction = 0.5f, yFraction = 0.5f, look = FoldHandleLook(sizeDp = 44))

        assertEquals(FoldResult.FOLDED, result)
        assertEquals(listOf("showTab", "moveAppToBack"), platform.calls)
        assertTrue(controller.isFolded.value)
        assertEquals(4, platform.lastShownDisplayId)
    }

    @Test
    fun `folding twice is a no-op`() {
        val platform = FakePlatform()
        val controller = controller(platform)
        controller.fold(displayId = 0, xFraction = 0.5f, yFraction = 0.5f, look = FoldHandleLook(sizeDp = 44))
        platform.calls.clear()

        val result = controller.fold(displayId = 0, xFraction = 0.5f, yFraction = 0.5f, look = FoldHandleLook(sizeDp = 44))

        assertEquals(FoldResult.ALREADY_FOLDED, result)
        assertEquals(emptyList<String>(), platform.calls)
    }

    // Folding used to relocate you: press the button on the second screen and
    // the app came back on the first. Safe, and not the choice the user made.
    @Test
    fun `the tab lands on the display it was folded from`() {
        val platform = FakePlatform(mainDisplay = 0)
        val controller = controller(platform)

        controller.fold(displayId = 4, xFraction = 0.5f, yFraction = 0.5f, look = FoldHandleLook(sizeDp = 44))

        assertEquals(4, platform.lastShownDisplayId)
    }

    // A device that cannot name its built-in display is not a reason to refuse
    // the fold: the caller's display is what the old behaviour used, and it is
    // still better than no tab at all.
    @Test
    fun `a device that cannot name a main display folds where it was asked`() {
        val platform = FakePlatform(mainDisplay = null)
        val controller = controller(platform)

        controller.fold(displayId = 4, xFraction = 0.5f, yFraction = 0.5f, look = FoldHandleLook(sizeDp = 44))

        assertEquals(4, platform.lastShownDisplayId)
    }

    @Test
    fun `unfolding leaves the tab up until the activity actually resumes`() {
        val platform = FakePlatform()
        val controller = controller(platform)
        controller.fold(displayId = 3, xFraction = 0.5f, yFraction = 0.5f, look = FoldHandleLook(sizeDp = 44))
        platform.calls.clear()

        controller.unfold()

        // No hideTab yet. If the relaunch fails there is still a way back.
        assertFalse(platform.calls.contains("hideTab"))
        assertTrue(controller.isFolded.value)
    }

    // A start from outside while overlay mode is on: a frontend launching
    // the app beside a dual-screen game, recents, the launcher. Under the
    // game the app cannot be seen, so it folds straight back, panel up.
    @Test
    fun `a resume nobody asked for folds straight back with the panel up`() {
        val platform = FakePlatform(mainDisplay = 0)
        val controller = controller(platform)
        controller.fold(displayId = 4, xFraction = 0.5f, yFraction = 0.5f, look = FoldHandleLook(sizeDp = 44))
        platform.calls.clear()

        assertTrue(controller.onActivityResumed())

        assertEquals(listOf("showGuidePanel", "moveAppToBack"), platform.calls)
        assertTrue(controller.isFolded.value)
        assertTrue(controller.isGuidePanelOpen.value)
        assertEquals("folding back is not a return", 0, controller.overlayReturns.value)
    }

    // The app would not go back, so it is in front: an ordinary return.
    @Test
    fun `a resume that cannot fold back settles as a return and ends the mode`() {
        val platform = FakePlatform(mainDisplay = 0)
        val memory = FakeOverlayMemory()
        val controller = controller(platform, memory)
        controller.fold(displayId = 4, xFraction = 0.5f, yFraction = 0.5f, look = FoldHandleLook(sizeDp = 44))
        platform.moveToBackSucceeds = false

        assertFalse(controller.onActivityResumed())

        assertFalse(controller.isFolded.value)
        assertFalse(memory.on)
        assertTrue(platform.calls.contains("hideTab"))
    }

    // Asking for the app back and then watching the handle sit there for three
    // more seconds reads as something that failed to go away. On the built-in
    // display there is nothing left to confirm, so it does not.
    @Test
    fun `an unfold onto the built-in display takes the tab down at once`() {
        val platform = FakePlatform(mainDisplay = 0)
        val controller = controller(platform)
        // Folded onto the built-in display itself, so the confirmation window
        // never has anything to prove.
        controller.fold(displayId = 0, xFraction = 0.5f, yFraction = 0.5f, look = FoldHandleLook(sizeDp = 44))
        controller.unfold()
        platform.calls.clear()

        controller.onActivityResumed()

        assertEquals(listOf("hideTab"), platform.calls)
    }

    // The tab's tap is the unfold. Inside the confirmation window a second tap
    // used to be a blind jump to display 0; that guess is gone, replaced by
    // aiming at the other display by name, the same action a held tap reaches
    // explicitly from the handle's menu. These next tests are what that
    // restored behaviour has to hold.

    // Swap: the named replacement for the guess. It moves Game Twin's windows,
    // never the app itself, so a game on the target display is not displaced.

    @Test
    fun `resuming while unfolded touches nothing`() {
        val platform = FakePlatform()
        val controller = controller(platform)

        controller.onActivityResumed()

        assertEquals(emptyList<String>(), platform.calls)
    }

    @Test
    fun `unfolding while unfolded touches nothing`() {
        val platform = FakePlatform()
        val controller = controller(platform)

        controller.unfold()

        assertEquals(emptyList<String>(), platform.calls)
    }

    // forceUnfold's folded branch funnels through returnAppToFront, exactly as
    // unfold() does, so every assertion about it was a second copy of an
    // unfold test one screen above (`unfolding returns the app to the display
    // it folded from`, `unfolding leaves the tab up until the activity
    // actually resumes`, `the activity resuming clears the fold whichever
    // route it came back by`, `unfolding an app that never actually left takes
    // the tab down immediately`). Those copies are gone. This is the one
    // behaviour only forceUnfold has: unfold() returns early when not folded,
    // so this is the only path that sweeps up a tab still on screen with
    // isFolded already false.
    @Test
    fun `forcing an unfold while unfolded still clears any stray tab`() {
        val platform = FakePlatform()
        val controller = controller(platform)

        controller.forceUnfold()

        assertEquals(listOf("hideTab"), platform.calls)
        assertFalse(controller.isFolded.value)
    }

    // moveTaskToBack(true) can report success while the task stays visible, which
    // a secondary display is documented to do. The fold then leaves isFolded true,
    // the tab up, and the app in front. Waiting for a resume that will never come
    // (a foregrounded singleTask activity relaunched "to front" produces no
    // lifecycle transition) pinned the tab on screen with no in-app way off it.
    // Folded onto the built-in display itself, so "in front" really does mean
    // visible and the immediate clear is correct: see the non-main-display
    // version of this scenario below, where it is not.
    @Test
    fun `unfolding an app that never actually left the built-in display takes the tab down immediately`() {
        val platform = FakePlatform(mainDisplay = 0)
        val controller = controller(platform)
        controller.fold(displayId = 0, xFraction = 0.5f, yFraction = 0.5f, look = FoldHandleLook(sizeDp = 44))
        platform.calls.clear()
        platform.appAlreadyInFront = true

        controller.unfold()

        assertEquals(listOf("bringAppToFront", "hideTab"), platform.calls)
        assertFalse(controller.isFolded.value)
    }

    // The immediate-clear path must not swallow the safety case: an app that
    // really is away still waits for the resume before the tab comes down.
    @Test
    fun `an app that really did leave still waits for the resume before the tab comes down`() {
        val platform = FakePlatform(appAlreadyInFront = false)
        val controller = controller(platform)
        controller.fold(displayId = 4, xFraction = 0.5f, yFraction = 0.5f, look = FoldHandleLook(sizeDp = 44))
        platform.calls.clear()

        controller.unfold()

        assertEquals(listOf("bringAppToFront"), platform.calls)
        assertTrue(controller.isFolded.value)
    }

    // Having cleared the fold early, a later stray resume must stay a no-op.
    // Note there was no first hideTab to double: this fold was on display 4,
    // so the early clear opened a confirmation window instead of taking the
    // handle down, and the handle is still up when this resume arrives. What
    // the guard protects is that window: a resume that re-entered the state
    // machine here could re-arm or close it out from under the tap the window
    // exists to catch.
    @Test
    fun `a resume after an already-in-front unfold changes nothing further`() {
        val platform = FakePlatform(appAlreadyInFront = true)
        val controller = controller(platform)
        controller.fold(displayId = 4, xFraction = 0.5f, yFraction = 0.5f, look = FoldHandleLook(sizeDp = 44))
        controller.unfold()
        platform.calls.clear()

        controller.onActivityResumed()

        assertEquals(emptyList<String>(), platform.calls)
        assertFalse(controller.isFolded.value)
    }

    // ---- Telling the app's readers the store moved ----

    // The app's readers stay composed behind the panel with their positions in
    // memory, and the panel's readers write to the same store. The return is
    // the one moment the app has to re-read, and it is told exactly once per
    // return, however the app came back.
    @Test
    fun `every return from a fold is counted, once`() {
        val platform = FakePlatform(mainDisplay = 0)
        val controller = controller(platform)
        assertEquals(0, controller.overlayReturns.value)

        controller.fold(displayId = 0, xFraction = 0.5f, yFraction = 0.5f, look = FoldHandleLook(sizeDp = 44))
        assertEquals("folding is not a return", 0, controller.overlayReturns.value)
        controller.unfold()
        assertEquals("the relaunch has not landed yet", 0, controller.overlayReturns.value)
        controller.onActivityResumed()
        assertEquals(1, controller.overlayReturns.value)

        controller.onActivityResumed()
        assertEquals("a resume while unfolded is not a return", 1, controller.overlayReturns.value)

        // Started from outside while the mode is on: folds back, no return.
        controller.fold(displayId = 0, xFraction = 0.5f, yFraction = 0.5f, look = FoldHandleLook(sizeDp = 44))
        controller.onActivityResumed()
        assertEquals(1, controller.overlayReturns.value)
    }

    // An app that never left has no resume coming, so the count moves when the
    // controller settles the fold itself.
    @Test
    fun `an already-in-front unfold counts as the return`() {
        val platform = FakePlatform(appAlreadyInFront = true)
        val controller = controller(platform)
        controller.fold(displayId = 4, xFraction = 0.5f, yFraction = 0.5f, look = FoldHandleLook(sizeDp = 44))

        controller.unfold()
        assertEquals(1, controller.overlayReturns.value)

        controller.onActivityResumed()
        assertEquals(1, controller.overlayReturns.value)
    }

    // ---- The swap ----

    // ---- The rescue, and not bouncing back off it ----

    // ---- Reporting a swap that did not happen ----

    // ---- The lite guide panel ----

    // A tap opens the panel. The one rule beyond that is the fallback below.
    @Test
    fun `a tap opens the panel on the fold display`() {
        val platform = FakePlatform()
        val controller = foldedController(platform, displayId = 4)

        controller.onTabTapped()

        assertEquals(listOf("showGuidePanel"), platform.calls)
        assertEquals(4, platform.lastPanelDisplayId)
        assertTrue(controller.isGuidePanelOpen.value)
        assertTrue("a panel is not an unfold", controller.isFolded.value)
    }

    // Parked, not removed: the reader keeps its page, so reopening a big guide
    // does not load it again. Every way out of overlay still removes it.
    @Test
    fun `a second tap closes the panel again`() {
        val platform = FakePlatform()
        val controller = foldedController(platform)
        controller.onTabTapped()
        platform.calls.clear()

        controller.onTabTapped()

        assertEquals(listOf("parkGuidePanel"), platform.calls)
        assertFalse(controller.isGuidePanelOpen.value)
        // And the handle is still the way back: closing a panel is not leaving.
        assertTrue(controller.isFolded.value)
    }

    // The fallback that keeps the handle honest. A tap on the one thing on
    // screen may do something other than what was asked, but it may never do
    // nothing, and a refused overlay window is exactly how it would.
    @Test
    fun `a panel that cannot be shown falls back to bringing the app back`() {
        val platform = FakePlatform(panelShows = false)
        val controller = foldedController(platform)

        controller.onTabTapped()

        assertEquals(listOf("showGuidePanel", "bringAppToFront"), platform.calls)
        assertFalse(controller.isGuidePanelOpen.value)
    }

    @Test
    fun `tapping while unfolded does nothing`() {
        val platform = FakePlatform()
        val controller = controller(platform)

        controller.onTabTapped()

        assertEquals(emptyList<String>(), platform.calls)
    }

    // ---- Entering overlay ----

    // The order is forced (the panel is built on the tab's window context) and
    // it is the safe one: nothing backgrounds until both windows are up.
    @Test
    fun `entering overlay shows the tab, then the panel, then backgrounds the app`() {
        val platform = FakePlatform()
        val controller = controller(platform)

        val result = controller.enterOverlay(displayId = 4, xFraction = 1f, yFraction = 0.5f, look = FoldHandleLook(sizeDp = 56))

        assertEquals(FoldResult.FOLDED, result)
        assertEquals(listOf("showTab", "showGuidePanel", "moveAppToBack"), platform.calls)
        assertEquals(4, platform.lastShownDisplayId)
        assertEquals(4, platform.lastPanelDisplayId)
        assertTrue(controller.isFolded.value)
        assertTrue(controller.isGuidePanelOpen.value)
    }

    // A handle is recoverable and a half torn-down fold is not, so a refused
    // panel degrades to the handle rather than aborting.
    @Test
    fun `a panel that cannot be shown on entry leaves the handle up and the fold in place`() {
        val platform = FakePlatform(panelShows = false)
        val controller = controller(platform)

        val result = controller.enterOverlay(displayId = 4, xFraction = 1f, yFraction = 0.5f, look = FoldHandleLook(sizeDp = 56))

        assertEquals(FoldResult.FOLDED, result)
        assertEquals(listOf("showTab", "showGuidePanel", "moveAppToBack"), platform.calls)
        assertTrue(controller.isFolded.value)
        assertFalse(controller.isGuidePanelOpen.value)
    }

    @Test
    fun `a tab that cannot be shown on entry aborts before the panel`() {
        val platform = FakePlatform(tabShows = false)
        val controller = controller(platform)

        val result = controller.enterOverlay(displayId = 4, xFraction = 1f, yFraction = 0.5f, look = FoldHandleLook(sizeDp = 56))

        assertEquals(FoldResult.TAB_FAILED, result)
        assertEquals(listOf("showTab"), platform.calls)
        assertFalse(controller.isFolded.value)
    }

    @Test
    fun `an entry that fails to background the app takes both windows back down`() {
        val platform = FakePlatform(moveToBackSucceeds = false)
        val controller = controller(platform)

        val result = controller.enterOverlay(displayId = 4, xFraction = 1f, yFraction = 0.5f, look = FoldHandleLook(sizeDp = 56))

        assertEquals(FoldResult.TAB_FAILED, result)
        assertEquals(
            listOf("showTab", "showGuidePanel", "moveAppToBack", "hideGuidePanel", "hideTab"),
            platform.calls,
        )
        assertFalse(controller.isFolded.value)
        assertFalse(controller.isGuidePanelOpen.value)
    }

    @Test
    fun `entering overlay twice is a no-op`() {
        val platform = FakePlatform()
        val controller = controller(platform)
        controller.enterOverlay(displayId = 4, xFraction = 1f, yFraction = 0.5f, look = FoldHandleLook(sizeDp = 56))
        platform.calls.clear()

        val result = controller.enterOverlay(displayId = 4, xFraction = 1f, yFraction = 0.5f, look = FoldHandleLook(sizeDp = 56))

        assertEquals(FoldResult.ALREADY_FOLDED, result)
        assertTrue(platform.calls.isEmpty())
    }

    // ---- Overlay mode ----

    @Test
    fun `entering overlay turns the mode on`() {
        val memory = FakeOverlayMemory()
        val controller = controller(FakePlatform(), memory)

        controller.enterOverlay(displayId = 4, xFraction = 1f, yFraction = 0.5f, look = FoldHandleLook(sizeDp = 56))

        assertTrue(memory.on)
    }

    @Test
    fun `a refused entry leaves the mode off`() {
        val memory = FakeOverlayMemory()
        val controller = controller(FakePlatform(tabShows = false), memory)

        controller.enterOverlay(displayId = 4, xFraction = 1f, yFraction = 0.5f, look = FoldHandleLook(sizeDp = 56))

        assertFalse(memory.on)
    }

    // Every one of overlay's own ways into the app ends the mode before the
    // app is asked forward, so the resume that follows is a return.
    @Test
    fun `leaving through the overlay ends the mode and the resume is a return`() {
        val routes: List<Pair<String, (FoldController) -> Unit>> = listOf(
            "unfold" to { c -> c.unfold() },
            "other screen" to { c -> c.unfoldToOtherScreen() },
            "forced" to { c -> c.forceUnfold() },
        )
        for ((name, leave) in routes) {
            val platform = FakePlatform(mainDisplay = 0)
            val memory = FakeOverlayMemory()
            val controller = controller(platform, memory)
            controller.enterOverlay(displayId = 4, xFraction = 1f, yFraction = 0.5f, look = FoldHandleLook(sizeDp = 56))

            leave(controller)
            assertFalse(name, memory.on)
            assertFalse(name, controller.onActivityResumed())
            assertFalse(name, controller.isFolded.value)
        }
    }

    // Folding the panel away is staying in overlay, not leaving it.
    @Test
    fun `folding the panel away keeps the mode on`() {
        val memory = FakeOverlayMemory()
        val controller = controller(FakePlatform(), memory)
        controller.enterOverlay(displayId = 4, xFraction = 1f, yFraction = 0.5f, look = FoldHandleLook(sizeDp = 56))

        controller.foldGuidePanel()
        controller.closeGuidePanel()

        assertTrue(memory.on)
    }

        // The invariant the whole feature now rests on: the app comes back on the
    // built-in display, whatever screen the handle is on. It is the one
    // display no other app can cover with a presentation window.
    @Test
    fun `unfolding returns the app to the built-in display whatever screen the handle is on`() {
        val platform = FakePlatform(mainDisplay = 0)
        val controller = controller(platform)
        controller.fold(displayId = 3, xFraction = 0.5f, yFraction = 0.5f, look = FoldHandleLook(sizeDp = 44))
        platform.calls.clear()

        controller.unfold()

        assertEquals(listOf("bringAppToFront"), platform.calls)
        assertEquals(0, platform.lastFrontDisplayId)
        // And the fold now points there, so a resume that never comes cannot
        // leave the handle aimed at the screen the app was taken off.
        controller.onActivityResumed()
        assertFalse(controller.isFolded.value)
    }

    @Test
    fun `a device that cannot name its built-in display returns the app where the handle is`() {
        val platform = FakePlatform(mainDisplay = null)
        val controller = foldedController(platform, displayId = 4)

        controller.unfold()

        assertEquals(4, platform.lastFrontDisplayId)
    }

    // Swap moves the overlay; it never moves where the app comes back.
    @Test
    fun `a swapped fold still returns the app to the built-in display`() {
        val platform = FakePlatform(mainDisplay = 0, displays = listOf(0, 4))
        val controller = foldedController(platform, displayId = 0)
        controller.swapGuidePanelDisplay()
        platform.calls.clear()

        controller.unfold()

        assertEquals(0, platform.lastFrontDisplayId)
    }

    // The panel is a full screen window over the display the app is being
    // asked back to, so every path that ends the fold takes it down first.
    @Test
    fun `every way out of the fold takes the panel down first`() {
        listOf<Pair<String, (FoldController) -> Unit>>(
            "unfold" to { it.unfold() },
            "forceUnfold" to { it.forceUnfold() },
        ).forEach { (name, exit) ->
            val platform = FakePlatform(mainDisplay = 0, displays = listOf(0, 4))
            val controller = foldedController(platform, displayId = 4)
            controller.openGuidePanel()
            platform.calls.clear()

            exit(controller)

            assertEquals(
                "$name must close the panel before asking for the app",
                listOf("hideGuidePanel", "bringAppToFront"),
                platform.calls,
            )
            assertFalse(name, controller.isGuidePanelOpen.value)
        }
    }

    // A panel already up stays up when a start from outside folds back.
    @Test
    fun `folding back keeps a panel that was already up`() {
        val platform = FakePlatform(mainDisplay = 0)
        val controller = foldedController(platform, displayId = 0)
        controller.openGuidePanel()
        platform.calls.clear()

        controller.onActivityResumed()

        assertEquals(listOf("moveAppToBack"), platform.calls)
        assertTrue(controller.isGuidePanelOpen.value)
    }

    // The panel's Swap, and the thing that makes it different from the handle
    // menu's: reading beside a game on the other screen is what is being asked
    // for, so the app stays where it is and the fold moves.
    @Test
    fun `the panel's swap moves the fold to the other screen and leaves the app alone`() {
        val platform = FakePlatform(mainDisplay = 0, displays = listOf(0, 4))
        val controller = foldedController(platform, displayId = 4)
        controller.openGuidePanel()
        platform.calls.clear()

        assertTrue(controller.swapGuidePanelDisplay())

        assertEquals(listOf("moveToDisplay"), platform.calls)
        assertEquals(0, platform.lastMovedToDisplayId)
        assertFalse("a swap must not bring the app back", "bringAppToFront" in platform.calls)
        assertTrue(controller.isFolded.value)
    }

    @Test
    fun `a swap that does not take leaves the fold display alone`() {
        val platform = FakePlatform(mainDisplay = 0, displays = listOf(0, 4), moveSucceeds = false)
        val controller = foldedController(platform, displayId = 4)

        assertFalse(controller.swapGuidePanelDisplay())

        // Still on 4: a second try aims at 4's other screen, not at 0's.
        platform.moveSucceeds = true
        assertTrue(controller.swapGuidePanelDisplay())
        assertEquals(0, platform.lastMovedToDisplayId)
    }

    @Test
    fun `a swap with nowhere to go does nothing at all`() {
        val platform = FakePlatform(mainDisplay = 0, displays = listOf(0))
        val controller = foldedController(platform, displayId = 0)

        assertFalse(controller.swapGuidePanelDisplay())

        assertTrue(platform.calls.isEmpty())
    }

    @Test
    fun `there is no panel to open while unfolded`() {
        val platform = FakePlatform()
        val controller = controller(platform)

        assertFalse(controller.openGuidePanel())
        controller.unfold()
        assertEquals(emptyList<String>(), platform.calls)
    }

    // Back with nothing left to close inside the panel is a fold, the same as a
    // tap on the handle, so it parks rather than tears down.
    @Test
    fun `back folds the panel the way a tap does`() {
        val platform = FakePlatform()
        val controller = foldedController(platform)
        controller.onTabTapped()
        platform.calls.clear()

        controller.foldGuidePanel()

        assertEquals(listOf("parkGuidePanel"), platform.calls)
        assertFalse(controller.isGuidePanelOpen.value)
        assertTrue(controller.isFolded.value)
    }

    @Test
    fun `folding a panel that is not open touches nothing`() {
        val platform = FakePlatform()
        val controller = foldedController(platform)

        controller.foldGuidePanel()

        assertEquals(emptyList<String>(), platform.calls)
    }

    // Closing a panel that is not open must not reach the window layer: the
    // teardown paths all call it, and none of them knows whether one is up.
    @Test
    fun `closing a panel that is not open touches nothing`() {
        val platform = FakePlatform()
        val controller = foldedController(platform)

        controller.closeGuidePanel()

        assertEquals(emptyList<String>(), platform.calls)
    }

    @Test
    fun `canSwapFrom answers from the platform`() {
        val twoScreens = controller(FakePlatform(mainDisplay = 0, displays = listOf(0, 4)))

        // Symmetric, like the hardware: from either screen there is another one.
        assertTrue(twoScreens.canSwapFrom(4))
        assertTrue(twoScreens.canSwapFrom(0))

        val oneScreen = controller(FakePlatform(mainDisplay = 0, displays = listOf(0)))
        assertFalse(oneScreen.canSwapFrom(0))
    }

    // --- Hold to switch screen -------------------------------------------
    //
    // Two routes, one gesture. Unfolded, the fold button's hold moves the app
    // itself across; in overlay, the panel's Return hold brings it back on the
    // screen it does not normally return to. Both reuse bringAppToFront, which
    // is the launch-on-a-display recipe every return already takes.

    @Test
    fun `a swap moves the app to the other display`() {
        val platform = FakePlatform(displays = listOf(0, 4))
        val controller = controller(platform)

        assertTrue(controller.swapAppDisplay(fromDisplayId = 0))

        assertEquals(4, platform.lastFrontDisplayId)
        assertTrue(platform.calls.contains("bringAppToFront"))
    }

    @Test
    fun `a swap is symmetric`() {
        val platform = FakePlatform(displays = listOf(0, 4))
        val controller = controller(platform)

        assertTrue(controller.swapAppDisplay(fromDisplayId = 4))

        assertEquals(0, platform.lastFrontDisplayId)
    }

    /** The caller says so out loud; it must not relaunch the app to nowhere. */
    @Test
    fun `a swap says no on a device with one screen`() {
        val platform = FakePlatform(displays = listOf(0))
        val controller = controller(platform)

        assertEquals(false, controller.swapAppDisplay(fromDisplayId = 0))

        assertEquals(false, platform.calls.contains("bringAppToFront"))
    }

    /**
     * The unfolded route only. Folded, the app is behind an overlay whose
     * handle and panel live on a display of their own, and moving the app out
     * from under them is the panel's Return, not this.
     */
    @Test
    fun `a swap does nothing while folded`() {
        val platform = FakePlatform(displays = listOf(0, 4))
        val controller = controller(platform)
        controller.enterOverlay(displayId = 0, xFraction = 0f, yFraction = 0f, look = FoldHandleLook(sizeDp = 56))
        platform.calls.clear()

        assertEquals(false, controller.swapAppDisplay(fromDisplayId = 0))

        assertEquals(false, platform.calls.contains("bringAppToFront"))
    }

    @Test
    fun `a swap leaves the fold state alone`() {
        val platform = FakePlatform(displays = listOf(0, 4))
        val controller = controller(platform)

        controller.swapAppDisplay(fromDisplayId = 0)

        assertEquals(false, controller.isFolded.value)
    }

    /**
     * Return normally aims at the built-in display, the one screen no other
     * app can cover with a presentation window. The hold is the deliberate
     * exception: it asks for the other one by name.
     */
    @Test
    fun `returning to the other screen aims away from the built-in display`() {
        val platform = FakePlatform(mainDisplay = 0, displays = listOf(0, 4))
        val controller = controller(platform)
        controller.enterOverlay(displayId = 0, xFraction = 0f, yFraction = 0f, look = FoldHandleLook(sizeDp = 56))

        assertTrue(controller.unfoldToOtherScreen())

        assertEquals(4, platform.lastFrontDisplayId)
    }

    @Test
    fun `returning to the other screen takes the panel down first`() {
        val platform = FakePlatform(mainDisplay = 0, displays = listOf(0, 4))
        val controller = controller(platform)
        controller.enterOverlay(displayId = 0, xFraction = 0f, yFraction = 0f, look = FoldHandleLook(sizeDp = 56))
        platform.calls.clear()

        controller.unfoldToOtherScreen()

        val panelDown = platform.calls.indexOf("hideGuidePanel")
        val appBack = platform.calls.indexOf("bringAppToFront")
        assertTrue("the panel covers the screen the app is being asked back to", panelDown in 0 until appBack)
    }

    @Test
    fun `returning to the other screen says no on a device with one screen`() {
        val platform = FakePlatform(mainDisplay = 0, displays = listOf(0))
        val controller = controller(platform)
        controller.enterOverlay(displayId = 0, xFraction = 0f, yFraction = 0f, look = FoldHandleLook(sizeDp = 56))
        platform.calls.clear()

        assertEquals(false, controller.unfoldToOtherScreen())

        assertEquals(false, platform.calls.contains("bringAppToFront"))
    }

    /** Nothing to return when nothing is folded. */
    @Test
    fun `returning to the other screen does nothing when not folded`() {
        val platform = FakePlatform(mainDisplay = 0, displays = listOf(0, 4))
        val controller = controller(platform)

        assertEquals(false, controller.unfoldToOtherScreen())

        assertEquals(false, platform.calls.contains("bringAppToFront"))
    }
}
