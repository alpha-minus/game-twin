package com.twinlore.app.ui.fold

import com.twinlore.app.data.fold.FoldHandleLook

/**
 * What asking the app back actually produced.
 *
 * [wasAlreadyInFront] is about the app: it was resumed at the moment of the
 * call, so no lifecycle transition and no onResume will follow. See
 * [FoldPlatform.bringAppToFront].
 *
 * There was a second fact here, whether the system accepted the relaunch at
 * all, read by the in-app swap so it could avoid toasting "moved" over a
 * relaunch that threw. The swap is gone, and every remaining caller keeps the
 * tab up until a resume proves the app is back, which is the stronger check.
 */
data class BringToFrontResult(
    val wasAlreadyInFront: Boolean,
)

/**
 * Every side effect folding needs, behind one seam.
 *
 * [FoldController] holds the rules; this holds the Android. Keeping them apart
 * is what lets the ordering guarantees (tab up before the app goes down) be
 * unit tests instead of hopes.
 */
interface FoldPlatform {
    /**
     * The built-in display's id: the only display no other app can cover with
     * a TYPE_PRESENTATION window, and so the only one where a resumed activity
     * is proof of a visible one. Null when it cannot be resolved.
     *
     * Purely a predicate now. Every fold lands on the display the button was
     * pressed on, whatever this returns; what it decides is whether the
     * confirmation window is needed once the app comes back, and a device that
     * cannot name its built-in display simply always needs it.
     */
    fun mainDisplayId(): Int?

    fun canDrawOverlays(): Boolean

    /**
     * Puts the tab up on [displayId]. Returns true only when it is genuinely on
     * screen; a false return means the caller must not background the app,
     * because there would be nothing left to bring it back with.
     */
    fun showTab(
        displayId: Int,
        xFraction: Float,
        yFraction: Float,
        look: FoldHandleLook,
    ): Boolean
    fun hideTab()

    /**
     * Closes Game Twin: the windows go, the task goes, the process goes.
     *
     * The same finish-and-exit the app's own close uses. Answered from the
     * panel, since a held handle is what asks for it.
     */
    fun closeApp()

    /**
     * Sends the current activity to the back. Returns true only when it
     * genuinely happened; a false return (no activity held, or the system
     * refused) means the caller must not treat the fold as having taken
     * effect, because the app would be left foregrounded with the tab up and
     * no way for anything downstream to ever take it down again.
     */
    fun moveAppToBack(): Boolean

    /**
     * Asks the app back to [displayId].
     *
     * [BringToFrontResult.wasAlreadyInFront] is true when the app turned out to
     * be in front already (an activity was resumed at the moment of the call).
     * That is not a success signal, it is a warning: a foregrounded singleTask
     * activity relaunched "to front" produces no lifecycle transition, so no
     * onResume will follow and nothing downstream will ever tell the caller the
     * app is back. False means the app really was away and a resume can be
     * waited for.
     */
    fun bringAppToFront(displayId: Int): BringToFrontResult

    /**
     * The other display Game Twin could live on, or null when there is only
     * one. Null is what hides every swap affordance, so nothing above this has
     * to know what a DisplayManager is.
     */
    fun otherDisplayId(currentDisplayId: Int): Int?

    /**
     * Puts the guide panel up, full screen, on the display the handle is on.
     *
     * Returns false when the window could not be added, which the controller
     * treats as "do what a tap has always done" rather than as a failure to
     * report: a tap that shows nothing is the one outcome this feature may not
     * produce.
     */
    fun showGuidePanel(): Boolean

    /**
     * Takes the panel down. Safe to call when there is none, because every
     * teardown path calls it and none of them knows whether one is up.
     */
    fun hideGuidePanel()

    /**
     * Folds the panel away without removing it: hidden, untouchable and paused,
     * but still composed, so the next [showGuidePanel] on the same display is
     * instant and the reader keeps its page. A large guide reloading on every
     * open was the cost of removing it. The platform releases a parked panel on
     * its own (memory pressure, a long fold), and [hideGuidePanel] releases it
     * for good.
     */
    fun parkGuidePanel()

    /**
     * Moves Game Twin's presence, the handle and any open panel, to
     * [displayId]. False means nothing moved and the fold display is unchanged.
     */
    fun moveToDisplay(displayId: Int): Boolean
}
