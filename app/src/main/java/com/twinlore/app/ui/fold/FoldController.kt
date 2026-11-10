package com.twinlore.app.ui.fold

import android.view.Display
import com.twinlore.app.data.fold.FoldHandleLook
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class FoldResult { FOLDED, ALREADY_FOLDED, PERMISSION_MISSING, TAB_FAILED }

/**
 * The overlay state machine.
 *
 * One invariant governs the whole thing: the app is never backgrounded without
 * a visible tab. The tab goes up first and comes down last, and a missing
 * overlay permission refuses the fold outright instead of half-performing it.
 *
 * A second invariant is what makes two screens safe: **the app always returns
 * on the built-in display, and only the overlay ever moves.** No activity can
 * be raised above another app's TYPE_PRESENTATION window, and an activity
 * under one reports itself resumed, focused and unobscured while showing
 * nothing; the built-in display is the one screen no other app can cover that
 * way. Overlay windows sit above presentations, so the handle and panel are
 * safe wherever they are asked to go. The return is pinned; the overlay is not.
 */
@Singleton
class FoldController @Inject constructor(
    private val platform: FoldPlatform,
    private val memory: OverlayMemory,
) {
    private val _isFolded = MutableStateFlow(false)
    val isFolded: StateFlow<Boolean> = _isFolded.asStateFlow()

    /**
     * Whether the lite guide panel is up. Only ever true while [isFolded] is:
     * the panel is a second overlay window beside the handle, and it never
     * outlives it.
     */
    private val _isGuidePanelOpen = MutableStateFlow(false)
    val isGuidePanelOpen: StateFlow<Boolean> = _isGuidePanelOpen.asStateFlow()

    /**
     * How many times the app has come back from a fold.
     *
     * The app's readers stay composed behind the panel, holding their reading
     * position in memory: the WebView's scroll, the PDF's page, the image's
     * pan. The panel hosts its own copies of those readers over the same
     * store, and it writes to it. Nothing tells the app's readers that the
     * store moved underneath them, so on return they showed where they had
     * been, and their next write put that over where the panel had got to.
     *
     * This is that signal. It moves once per return, and the vault session's
     * readers re-read the store on each move. A counter rather than
     * [isFolded]'s falling edge because a StateFlow conflates: a collector
     * that missed the true is still told about the return.
     */
    private val _overlayReturns = MutableStateFlow(0)
    val overlayReturns: StateFlow<Int> = _overlayReturns.asStateFlow()

    /**
     * Whether overlay mode is on: entered, and not yet left through one of
     * overlay's own ways back into the app. While it is on, a resume that
     * nobody asked for from the overlay goes straight back into it. Persisted
     * through [memory] so a cold start can pick it up.
     */
    private var overlayMode = false

    private fun setOverlayMode(on: Boolean) {
        overlayMode = on
        memory.setOverlayMode(on)
    }

    /** The display the app folded away from, so unfolding returns it there. */
    private var foldedDisplayId: Int = Display.DEFAULT_DISPLAY

    /**
     * Folds to a handle on [displayId] and nothing else: the collapsed state
     * without going through the panel first. The button's tap. Pressing it
     * from the app means "get out of my way", and the handle is the whole of
     * that; the panel is one tap on the handle further, for when there is
     * something to read.
     */
    fun fold(
        displayId: Int,
        xFraction: Float,
        yFraction: Float,
        look: FoldHandleLook,
    ): FoldResult = enter(displayId, xFraction, yFraction, look, withPanel = false)

    /**
     * Enters overlay on [displayId]: the handle, the panel over it, and then
     * the app goes to the back. What a cold start with overlay mode on folds
     * back into, panel up, so there is something to read and a labelled way
     * out. The button itself takes [fold].
     *
     * The order is forced, because the panel is built on the tab's window
     * context, and it is also the safe one: nothing backgrounds until both
     * windows are up. A refused panel is not a refused fold. The handle is
     * still up and recoverable, and tearing it down again after it appeared
     * trades a degraded state for a worse one.
     */
    fun enterOverlay(
        displayId: Int,
        xFraction: Float,
        yFraction: Float,
        look: FoldHandleLook,
    ): FoldResult = enter(displayId, xFraction, yFraction, look, withPanel = true)

    private fun enter(
        displayId: Int,
        xFraction: Float,
        yFraction: Float,
        look: FoldHandleLook,
        withPanel: Boolean,
    ): FoldResult {
        if (_isFolded.value) return FoldResult.ALREADY_FOLDED
        if (!platform.canDrawOverlays()) return FoldResult.PERMISSION_MISSING

        // Order is the safety property: tab first, then background. And the tab
        // has to actually be up: if it could not be shown, backgrounding would
        // strand the app behind nothing, so the fold is abandoned instead.
        if (!platform.showTab(displayId, xFraction, yFraction, look)) return FoldResult.TAB_FAILED
        foldedDisplayId = displayId
        val panelUp = withPanel && platform.showGuidePanel()
        // The backgrounding itself has to actually happen too. A silent
        // failure here (no activity held, or the system refused) would leave
        // the app foregrounded with isFolded true and the tab up forever: the
        // only hideTab() that could still run is the one [unfold] and
        // [forceUnfold] wait for [onActivityResumed] to reach, and a
        // foregrounded singleTask activity brought "to front" again produces
        // no lifecycle callback to ever get there. Take everything back down
        // and report the same failure showTab's refusal does.
        if (!platform.moveAppToBack()) {
            if (panelUp) platform.hideGuidePanel()
            platform.hideTab()
            return FoldResult.TAB_FAILED
        }
        _isFolded.value = true
        _isGuidePanelOpen.value = panelUp
        setOverlayMode(true)
        return FoldResult.FOLDED
    }

    /**
     * Bring the app back, on the built-in display.
     *
     * Every route out of overlay ends here: the panel's App button, the
     * handle's hold, and the tap fallback when a panel cannot be shown. The
     * built-in display rather than [foldedDisplayId], because that is the one
     * screen no other app can cover with a presentation window; a handle that
     * was swapped to the other screen still brings the app back here. A
     * device that cannot name its built-in display returns the app where the
     * handle is, which is the best remaining guess.
     *
     * This is leaving overlay mode, deliberately, and it is said before the
     * app is asked forward: the resume that follows must read as a return and
     * not as one more start to fold away from.
     */
    fun unfold() {
        if (!_isFolded.value) return
        setOverlayMode(false)
        foldedDisplayId = platform.mainDisplayId() ?: foldedDisplayId
        // The panel first: it is a full screen window over the display the app
        // is being asked back to, and leaving it up would hide the very thing
        // that was just requested.
        closeGuidePanel()
        // The tab stays up. If the relaunch does not land, the user is not
        // stranded. [onActivityResumed] takes it down once the app is really back.
        returnAppToFront()
    }

    /**
     * The handle was tapped: the panel opens, or closes if it was open.
     *
     * The fallback is a rule and not a detail: a tap that cannot open a panel
     * brings the app back instead. A tap on the one thing on screen may do
     * something other than what was asked, but it may never do nothing.
     */
    fun onTabTapped() {
        if (!_isFolded.value) return
        if (_isGuidePanelOpen.value) {
            foldGuidePanel()
            return
        }
        if (openGuidePanel()) return
        unfold()
    }

    /**
     * Puts the panel up over the fold display. False means the window was
     * refused and nothing changed.
     */
    fun openGuidePanel(): Boolean {
        if (!_isFolded.value) return false
        if (_isGuidePanelOpen.value) return true
        if (!platform.showGuidePanel()) return false
        _isGuidePanelOpen.value = true
        return true
    }

    /**
     * Moves the fold, handle and open panel together, to the other display.
     *
     * The panel's `Swap`, and deliberately not an unfold. Reading beside a game
     * on the other screen is the thing being asked for: the App button is what
     * brings the app back, and it always lands on the built-in display. Returns false
     * when there is nowhere to go or the move did not take, in which case
     * nothing has changed.
     */
    fun swapGuidePanelDisplay(): Boolean {
        if (!_isFolded.value) return false
        val target = platform.otherDisplayId(foldedDisplayId) ?: return false
        if (!platform.moveToDisplay(target)) return false
        foldedDisplayId = target
        return true
    }

    /**
     * Folds the panel away and leaves the handle over the game, keeping the
     * panel parked so it reopens where it was: a tap on the handle, or Back
     * with nothing left to close inside the panel.
     */
    fun foldGuidePanel() {
        if (!_isGuidePanelOpen.value) return
        _isGuidePanelOpen.value = false
        platform.parkGuidePanel()
    }

    /**
     * Takes the panel down for good and leaves the handle over the game. The
     * first step of every path that ends the fold.
     */
    fun closeGuidePanel() {
        if (!_isGuidePanelOpen.value) return
        _isGuidePanelOpen.value = false
        platform.hideGuidePanel()
    }

    /**
     * Closes the app, having been asked. The panel's close prompt is the only
     * caller: a hold on the handle raises the question and this answers it.
     */
    fun closeApp() {
        platform.closeApp()
    }

    /**
     * Moves the app itself to the other screen, unfolded, from [fromDisplayId].
     *
     * The fold button's hold. Nothing folds and nothing is drawn over
     * anything: the app is relaunched onto the other display and that is the
     * whole action. [FoldPlatform.bringAppToFront] is the same
     * launch-on-a-display call every return out of overlay already makes, so
     * this route is not new machinery, only a new caller.
     *
     * False means nothing happened and the caller should say so out loud: on a
     * device with one screen there is nowhere to go, and a hold that silently
     * does nothing reads as a broken button.
     *
     * Refused while folded. The app is behind an overlay whose handle and
     * panel live on a display of their own, and moving it out from under them
     * is [unfoldToOtherScreen], which takes the panel down on the way.
     */
    fun swapAppDisplay(fromDisplayId: Int): Boolean {
        if (_isFolded.value) return false
        val target = platform.otherDisplayId(fromDisplayId) ?: return false
        platform.bringAppToFront(target)
        return true
    }

    /**
     * Brings the app back on the screen [unfold] does not return it to.
     *
     * The panel's Return, held. Return normally aims at the built-in display
     * for a reason worth keeping in view: it is the one screen no other app
     * can cover with a presentation window, so a resume there is proof the app
     * is really visible. This route asks for the other display by name, which
     * means the resume it gets back is a claim rather than proof. That is the
     * user's call to make, and it is the entire point of a gesture that says
     * "come back over there".
     *
     * The vault is forgotten as every deliberate exit forgets it, and the
     * panel comes down before the app is asked back, since it is a full screen
     * window over the display in question.
     *
     * False when there is nowhere to go or nothing is folded.
     */
    fun unfoldToOtherScreen(): Boolean {
        if (!_isFolded.value) return false
        val here = platform.mainDisplayId() ?: foldedDisplayId
        val target = platform.otherDisplayId(here) ?: return false
        setOverlayMode(false)
        foldedDisplayId = target
        closeGuidePanel()
        returnAppToFront()
        return true
    }

    /** Whether a swap has anywhere to go from [displayId]. */
    fun canSwapFrom(displayId: Int): Boolean = platform.otherDisplayId(displayId) != null

    /**
     * Asks the app back and settles the fold state on what the platform says
     * actually happened.
     *
     * The normal case is that the app really was away: the tab stays up and
     * [_isFolded] stays true until [onActivityResumed] confirms the relaunch
     * landed, so a dropped relaunch cannot strand the app behind nothing.
     *
     * The other case is that the app was in front all along. That happens when
     * moveTaskToBack(true) reported success but the task stayed visible, which
     * a secondary display can do. No resume is coming for an activity that
     * never paused, so waiting for one would pin the tab on screen forever with
     * no in-app way to remove it. The tab comes down instead.
     */
    private fun returnAppToFront() {
        if (!platform.bringAppToFront(foldedDisplayId).wasAlreadyInFront) return
        _isFolded.value = false
        platform.hideTab()
        _overlayReturns.value++
    }

    /**
     * The activity is on screen again, by whatever route: the tab, recents, a
     * launch intent, the launcher. Fold state follows what happened rather
     * than what was asked for.
     *
     * A resume is taken as proof the app is back, and the tab goes at once.
     * That is exactly true on the built-in display, where nothing can be drawn
     * over the app, and it is where folds land unless the user turned that off.
     * On any other display it is a claim rather than proof, and one this
     * process has no way to check: an activity under a foreign
     * TYPE_PRESENTATION window resumes, takes focus, and reports itself
     * visible and unobscured while showing nothing. That case cannot arise
     * from here: every route back aims at the built-in display, and the
     * overlay itself is always visible to be tapped again.
     *
     * Unless overlay mode is still on. Then nobody asked for this return from
     * the overlay, and the resume is a start from outside, possibly under a
     * game: see [refold]. True means it folded straight back, and the app's
     * own on-foreground work should stand down.
     */
    fun onActivityResumed(): Boolean {
        if (!_isFolded.value) return false
        if (overlayMode && refold()) return true
        setOverlayMode(false)
        _isFolded.value = false
        closeGuidePanel()
        platform.hideTab()
        // Last, once the panel is down: its readers wrote where they got to
        // as they went, and the app's readers re-read on this.
        _overlayReturns.value++
        return false
    }

    /**
     * The app came to the front while overlay mode is on, and not through
     * overlay's own way back: a frontend starting it beside a game, recents,
     * the launcher icon. Over a dual-screen game that start lands under the
     * game's presentation window, where the app cannot be seen, so the mode
     * holds: the panel comes up over the handle that is still there, and the
     * app goes to the back again. Leaving is the panel's buttons, which turn
     * the mode off before they bring the app forward.
     *
     * False when the app would not go back, in which case it is in front and
     * the caller settles that as an ordinary return.
     */
    private fun refold(): Boolean {
        openGuidePanel()
        return platform.moveAppToBack()
    }

    /**
     * Brings a fold down from outside the tab, and sweeps up a stray tab if
     * there is no fold to bring down.
     *
     * This is the deliberate forcing function: the way to get the app back
     * when the tab is not the thing being pressed. Its production caller is
     * [AndroidFoldPlatform.moveToDisplay]'s last resort, where a display swap
     * has taken the tab down and neither screen could put one back up: there is
     * then a backgrounded app with nothing on screen, which is the exact state
     * this class exists to prevent, and the only remaining move is to force the
     * app forward. Its not-folded branch is also the only code that clears a
     * tab left behind with [_isFolded] already false.
     *
     * Folded case gets the exact same safety contract as [unfold]: the app is
     * asked to come back, but the tab stays up and [_isFolded] stays true
     * until [onActivityResumed] confirms the relaunch actually landed. Taking
     * the tab down here on the mere hope that [FoldPlatform.bringAppToFront]
     * worked is the defect this replaces: a dropped relaunch would strand
     * the app backgrounded with nothing on screen to bring it back. The one
     * exception is [returnAppToFront]'s: an app that was in front all along,
     * where no resume is ever coming to settle it.
     */
    fun forceUnfold() {
        if (_isFolded.value) {
            // Forced back, so not in overlay any more: a mode still on would
            // fold the rescued app straight away again.
            setOverlayMode(false)
            closeGuidePanel()
            returnAppToFront()
            return
        }
        // Not folded, so the app was never backgrounded and there is nothing
        // to strand. Still clear out any stray tab that might be lingering.
        closeGuidePanel()
        platform.hideTab()
    }
}
