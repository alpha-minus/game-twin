package com.twinlore.app.ui.fold

import android.app.Activity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.View
import android.widget.FrameLayout
import com.twinlore.app.data.AppIcon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The tab has to tell a tap apart from a drag with one finger and no
 * ambiguity: a tap opens the panel, a drag moves the tab. Getting this
 * wrong means the panel pops open every time you nudge the handle.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class FoldTabViewTest {

    private fun motion(action: Int, x: Float, y: Float): MotionEvent =
        MotionEvent.obtain(0L, 0L, action, x, y, 0)

    private fun newView(): FoldTabView =
        FoldTabView(RuntimeEnvironment.getApplication())

    /**
     * postDelayed only actually runs once a view is attached to a window
     * (Android queues it otherwise); the hold tests need a real attachment for
     * the long-press runnable to fire when the looper is idled.
     */
    private fun newAttachedView(sizePx: Int? = null): FoldTabView {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val view = FoldTabView(activity)
        val root = FrameLayout(activity)
        activity.setContentView(root)
        if (sizePx == null) {
            root.addView(view)
        } else {
            root.addView(view, FrameLayout.LayoutParams(sizePx, sizePx))
            view.measure(
                android.view.View.MeasureSpec.makeMeasureSpec(sizePx, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(sizePx, android.view.View.MeasureSpec.EXACTLY),
            )
            view.layout(0, 0, sizePx, sizePx)
        }
        return view
    }

    private val slop: Int
        get() = ViewConfiguration.get(RuntimeEnvironment.getApplication()).scaledTouchSlop

    @Test
    fun `a press and release in place is a tap`() {
        val view = newView()
        var taps = 0
        view.onTap = { taps++ }

        view.dispatchTouchEvent(motion(MotionEvent.ACTION_DOWN, 10f, 10f))
        view.dispatchTouchEvent(motion(MotionEvent.ACTION_UP, 10f, 10f))

        assertEquals(1, taps)
    }

    @Test
    fun `a drag past the touch slop is not a tap`() {
        val view = newView()
        var taps = 0
        view.onTap = { taps++ }

        view.dispatchTouchEvent(motion(MotionEvent.ACTION_DOWN, 10f, 10f))
        view.dispatchTouchEvent(motion(MotionEvent.ACTION_MOVE, 10f, 10f + slop * 3))
        view.dispatchTouchEvent(motion(MotionEvent.ACTION_UP, 10f, 10f + slop * 3))

        assertEquals(0, taps)
    }

    @Test
    fun `a wobble within the slop is still a tap`() {
        val view = newView()
        var taps = 0
        view.onTap = { taps++ }

        view.dispatchTouchEvent(motion(MotionEvent.ACTION_DOWN, 10f, 10f))
        view.dispatchTouchEvent(motion(MotionEvent.ACTION_MOVE, 10f, 10f + 1f))
        view.dispatchTouchEvent(motion(MotionEvent.ACTION_UP, 10f, 10f + 1f))

        assertEquals(1, taps)
    }

    @Test
    fun `dragging reports raw coordinates while moving and once at the end`() {
        val view = newView()
        val moves = mutableListOf<Pair<Float, Float>>()
        var ended: Pair<Float, Float>? = null
        view.onDragTo = { x, y -> moves += x to y }
        view.onDragEnd = { x, y -> ended = x to y }

        view.dispatchTouchEvent(motion(MotionEvent.ACTION_DOWN, 10f, 10f))
        view.dispatchTouchEvent(motion(MotionEvent.ACTION_MOVE, 10f, 10f + slop * 3))
        view.dispatchTouchEvent(motion(MotionEvent.ACTION_UP, 10f, 10f + slop * 4))

        assertTrue(moves.isNotEmpty())
        assertEquals(10f + slop * 4, ended!!.second, 1e-3f)
    }

    @Test
    fun `a cancelled gesture is neither a tap nor a drag end`() {
        val view = newView()
        var taps = 0
        var ends = 0
        view.onTap = { taps++ }
        view.onDragEnd = { _, _ -> ends++ }

        view.dispatchTouchEvent(motion(MotionEvent.ACTION_DOWN, 10f, 10f))
        view.dispatchTouchEvent(motion(MotionEvent.ACTION_CANCEL, 10f, 10f))

        assertEquals(0, taps)
        assertEquals(0, ends)
    }

    /**
     * The handle is drawn at exactly what it was set to, and nothing moves it.
     *
     * It used to start solid, fade to this after three idle seconds, and jump
     * back to solid under a finger. The value the user picked was then the one
     * value they mostly did not see: over a game, the handle sat at its chosen
     * opacity only in the moments nobody was looking at it. These tests are
     * the whole of the new contract, so they cover the three moments the old
     * fade moved it: on arrival, after the idle delay it used to wait, and
     * under a finger.
     */
    @Test
    fun `the handle arrives at the alpha it was given`() {
        val view = newView()
        view.handleAlpha = 0.2f

        assertEquals(0.2f, view.alpha, 1e-3f)
    }

    @Test
    fun `left untouched the handle does not fade`() {
        val view = newAttachedView()
        view.handleAlpha = 0.8f

        idleFor(10_000)

        assertEquals(0.8f, view.alpha, 1e-3f)
    }

    @Test
    fun `touching a faint handle does not brighten it, and still counts as a tap`() {
        val view = newView()
        view.handleAlpha = FoldTabView.DEFAULT_ALPHA
        var taps = 0
        view.onTap = { taps++ }

        view.dispatchTouchEvent(motion(MotionEvent.ACTION_DOWN, 10f, 10f))
        assertEquals(
            "a press must not brighten the handle",
            FoldTabView.DEFAULT_ALPHA,
            view.alpha,
            1e-3f,
        )

        view.dispatchTouchEvent(motion(MotionEvent.ACTION_UP, 10f, 10f))
        assertEquals(FoldTabView.DEFAULT_ALPHA, view.alpha, 1e-3f)
        assertEquals(1, taps)
    }

    @Test
    fun `dragging a faint handle does not brighten it`() {
        val view = newAttachedView()
        view.handleAlpha = 0.4f
        view.onDragTo = { _, _ -> }

        view.dispatchTouchEvent(motion(MotionEvent.ACTION_DOWN, 10f, 300f))
        view.dispatchTouchEvent(motion(MotionEvent.ACTION_MOVE, 10f, 300f + slop * 3))
        assertEquals(0.4f, view.alpha, 1e-3f)

        view.dispatchTouchEvent(motion(MotionEvent.ACTION_UP, 10f, 300f + slop * 3))
        idleFor(10_000)

        assertEquals(0.4f, view.alpha, 1e-3f)
    }

    @Test
    fun `holding a faint handle does not brighten it`() {
        val view = newAttachedView()
        view.handleAlpha = 0.4f
        view.playHoldFeedback = {}
        view.onHold = {}

        view.dispatchTouchEvent(motion(MotionEvent.ACTION_DOWN, 10f, 300f))
        idleFor(FoldTabView.LONG_PRESS_MS + 200)

        assertEquals(0.4f, view.alpha, 1e-3f)
    }

    @Test
    fun `a fully visible handle stays solid`() {
        val view = newAttachedView()
        view.handleAlpha = 1f

        idleFor(10_000)

        assertEquals(1f, view.alpha, 1e-3f)
    }

    @Test
    fun `an alpha outside the range is clamped rather than rejected`() {
        val view = newView()

        view.handleAlpha = 4f
        assertEquals(1f, view.alpha, 1e-3f)

        view.handleAlpha = -1f
        assertEquals(0f, view.alpha, 1e-3f)
    }

    /**
     * While the panel is up the handle only has to be tappable, not findable
     * against a game, so it may be drawn smaller. The window keeps its bounds,
     * so what shrinks is the mark and not the target: the panel reserving a
     * strip to avoid the handle is what this replaced.
     *
     * How much smaller is the user's, and the default is not at all. It was a
     * hardcoded 0.62 for everybody, which is a judgement about how much of the
     * page a handle may cover, made on behalf of a reader this code has never
     * seen.
     */
    @Test
    fun `by default recessing does not shrink the mark`() {
        val view = newView()

        view.recessed = true

        assertEquals(1f, view.scaleX, 1e-3f)
        assertEquals(1f, view.scaleY, 1e-3f)
    }

    @Test
    fun `recessing shrinks the mark to the chosen scale and leaves the tab tappable`() {
        val view = newView()
        view.recessScale = 0.5f
        assertEquals("nothing shrinks until the panel is up", 1f, view.scaleX, 1e-3f)

        view.recessed = true
        assertEquals(0.5f, view.scaleX, 1e-3f)
        assertEquals(0.5f, view.scaleY, 1e-3f)

        var taps = 0
        view.onTap = { taps++ }
        view.dispatchTouchEvent(motion(MotionEvent.ACTION_DOWN, 10f, 10f))
        view.dispatchTouchEvent(motion(MotionEvent.ACTION_UP, 10f, 10f))
        assertEquals("a smaller mark is the same target", 1, taps)

        view.recessed = false
        assertEquals(1f, view.scaleX, 1e-3f)
    }

    /**
     * The slider that sets this lives on the panel, which is the surface that
     * recesses the handle in the first place. So it has to move the handle
     * while the handle is already recessed, or dragging it would show nothing
     * until the panel closed.
     */
    @Test
    fun `changing the scale while recessed redraws at once`() {
        val view = newView()
        view.recessed = true

        view.recessScale = 0.4f
        assertEquals(0.4f, view.scaleX, 1e-3f)

        view.recessScale = 0.8f
        assertEquals(0.8f, view.scaleX, 1e-3f)

        // And the handle over a game is still full size, whatever this says.
        view.recessed = false
        assertEquals(1f, view.scaleX, 1e-3f)
    }

    @Test
    fun `a scale outside the range is clamped rather than rejected`() {
        val view = newView()
        view.recessed = true

        view.recessScale = 4f
        assertEquals(1f, view.scaleX, 1e-3f)

        view.recessScale = -1f
        assertEquals(0f, view.scaleX, 1e-3f)
    }

    // Zero is reachable on purpose: a handle nobody can see, still exactly
    // where it was left and still taking touches. Both Settings surfaces carry
    // the slider that brings it back.
    @Test
    fun `an invisible handle is still tappable`() {
        val view = newView()
        view.handleAlpha = 0f
        var taps = 0
        view.onTap = { taps++ }

        view.dispatchTouchEvent(motion(MotionEvent.ACTION_DOWN, 10f, 10f))
        view.dispatchTouchEvent(motion(MotionEvent.ACTION_UP, 10f, 10f))

        assertEquals(0f, view.alpha, 1e-3f)
        assertEquals(1, taps)
    }

    private fun idleFor(ms: Long) {
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(
            java.time.Duration.ofMillis(ms)
        )
    }

    // Three gestures: tap opens the panel, drag moves the handle, hold
    // brings the app back. Only one may fire per press, and the hold must not
    // also tap on release: the hold is the leaving, and the release must not
    // leave twice.
    @Test
    fun `a hold fires and does not also tap on release`() {
        val view = newAttachedView()
        var taps = 0
        var holds = 0
        view.onTap = { taps++ }
        view.onHold = { holds++ }

        view.dispatchTouchEvent(motion(MotionEvent.ACTION_DOWN, 10f, 300f))
        idleFor(FoldTabView.LONG_PRESS_MS + 200)
        view.dispatchTouchEvent(motion(MotionEvent.ACTION_UP, 10f, 300f))

        assertEquals(1, holds)
        assertEquals(0, taps)
    }

    // The hold fires while the finger is still down, so the ring and the
    // tick can say it landed.
    @Test
    fun `the hold fires before the finger lifts`() {
        val view = newAttachedView()
        var holds = 0
        view.onHold = { holds++ }

        view.dispatchTouchEvent(motion(MotionEvent.ACTION_DOWN, 10f, 300f))
        idleFor(FoldTabView.LONG_PRESS_MS + 200)

        assertEquals(1, holds)
    }

    // Moving after the hold has fired must not drag the handle.
    @Test
    fun `moving after a hold does not drag the handle`() {
        val view = newAttachedView()
        var moves = 0
        view.onDragTo = { _, _ -> moves++ }

        view.dispatchTouchEvent(motion(MotionEvent.ACTION_DOWN, 300f, 300f))
        idleFor(FoldTabView.LONG_PRESS_MS + 200)
        view.dispatchTouchEvent(motion(MotionEvent.ACTION_MOVE, 700f, 300f))

        assertEquals(0, moves)
    }

    // A drag is a move whatever the clock says: dragging the handle into a
    // corner takes longer than the hold timeout, and must not fire the hold.
    @Test
    fun `a slow drag does not become a hold`() {
        val view = newAttachedView()
        var holds = 0
        var ends = 0
        view.onHold = { holds++ }
        view.onDragEnd = { _, _ -> ends++ }

        view.dispatchTouchEvent(motion(MotionEvent.ACTION_DOWN, 10f, 300f))
        view.dispatchTouchEvent(motion(MotionEvent.ACTION_MOVE, 400f, 300f))
        idleFor(FoldTabView.LONG_PRESS_MS + 200)
        view.dispatchTouchEvent(motion(MotionEvent.ACTION_UP, 400f, 300f))

        assertEquals(0, holds)
        assertEquals(1, ends)
    }

    // The hold fires while the finger is still down so that it can be
    // confirmed, and this is the confirmation: a ring while the press is
    // registering, a tick from the motor. Without either, a 700ms hold on the
    // one thing on screen gives no sign it was long enough.
    @Test
    fun `a hold draws the ring and plays the haptic`() {
        val view = newAttachedView()
        var haptics = 0
        view.playHoldFeedback = { haptics++ }

        view.dispatchTouchEvent(motion(MotionEvent.ACTION_DOWN, 10f, 300f))
        assertEquals(false, view.ringVisible)
        idleFor(FoldTabView.LONG_PRESS_MS + 200)

        assertEquals(true, view.ringVisible)
        assertEquals(1, haptics)
    }

    @Test
    fun `lifting the finger clears the ring`() {
        val view = newAttachedView()
        view.playHoldFeedback = {}
        view.dispatchTouchEvent(motion(MotionEvent.ACTION_DOWN, 10f, 300f))
        idleFor(FoldTabView.LONG_PRESS_MS + 200)

        view.dispatchTouchEvent(motion(MotionEvent.ACTION_UP, 10f, 300f))

        assertEquals(false, view.ringVisible)
    }

    @Test
    fun `a tap plays no hold feedback`() {
        val view = newAttachedView()
        var haptics = 0
        view.playHoldFeedback = { haptics++ }

        view.dispatchTouchEvent(motion(MotionEvent.ACTION_DOWN, 10f, 10f))
        view.dispatchTouchEvent(motion(MotionEvent.ACTION_UP, 10f, 10f))

        assertEquals(0, haptics)
        assertEquals(false, view.ringVisible)
    }

    // The handle is the whole interface while it is up, and an unlabelled
    // square is what a screen reader had.
    @Test
    fun `the handle describes itself`() {
        val view = newView()
        assertEquals(
            RuntimeEnvironment.getApplication().getString(com.twinlore.app.R.string.fold_handle_description),
            view.contentDescription,
        )
    }

    @Test
    fun `a quick tap with no hold still taps`() {
        val view = newAttachedView()
        var taps = 0
        view.onTap = { taps++ }

        view.dispatchTouchEvent(motion(MotionEvent.ACTION_DOWN, 10f, 300f))
        view.dispatchTouchEvent(motion(MotionEvent.ACTION_UP, 10f, 300f))

        assertEquals(1, taps)
    }

    // Movement past the slop makes the gesture a drag, and a drag ends in a
    // drag end rather than a tap. Otherwise every reposition of the tab would
    // also open the panel.
    @Test
    fun `a drag with no hold still fires drag end and not tap`() {
        val view = newAttachedView()
        var taps = 0
        var ends = 0
        view.onTap = { taps++ }
        view.onDragEnd = { _, _ -> ends++ }

        view.dispatchTouchEvent(motion(MotionEvent.ACTION_DOWN, 10f, 300f))
        view.dispatchTouchEvent(motion(MotionEvent.ACTION_MOVE, 10f, 300f + slop * 3))
        view.dispatchTouchEvent(motion(MotionEvent.ACTION_UP, 10f, 300f + slop * 3))

        assertEquals(1, ends)
        assertEquals(0, taps)
    }

    // Detaching is the ordinary hide path. Nothing may remain scheduled once
    // the window is gone: a pending long press holds a detached view to fire a
    // gesture at an overlay nobody can see any more.
    @Test
    fun `detaching leaves no gesture scheduled`() {
        val view = newAttachedView()
        var holds = 0
        view.onHold = { holds++ }
        view.playHoldFeedback = {}
        view.handleAlpha = 0.2f

        view.dispatchTouchEvent(motion(MotionEvent.ACTION_DOWN, 10f, 300f))
        (view.parent as ViewGroup).removeView(view)
        idleFor(FoldTabView.LONG_PRESS_MS + 500)

        assertEquals("a detached handle must not still fire a hold", 0, holds)
        assertEquals("and it keeps the alpha it was given", 0.2f, view.alpha, 1e-3f)
        assertEquals(false, view.ringVisible)
    }

    // Robolectric 4.11.1 ships no ShadowImageView (confirmed absent from
    // shadows-framework-4.11.1.jar; Shadows.shadowOf(ImageView) doesn't even
    // resolve, it falls back to the ShadowView overload, which has no
    // imageResourceId), so ImageView.setImageResource runs the real AOSP
    // implementation, which stashes the resource id in the private
    // `mResource` field. Reading it via ReflectionHelpers is what
    // ShadowImageView.imageResourceId itself used to do internally back when
    // it existed.
    @Test
    fun `the tab shows the icon it was given`() {
        val view = FoldTabView(
            RuntimeEnvironment.getApplication(),
            iconRes = com.twinlore.app.R.mipmap.ic_launcher_gold,
        )
        val image = view.iconView
        assertEquals(
            com.twinlore.app.R.mipmap.ic_launcher_gold,
            org.robolectric.util.ReflectionHelpers.getField<Int>(image, "mResource"),
        )
    }

    @Test
    fun `the tab falls back to the default icon`() {
        val image = newView().iconView
        assertEquals(
            com.twinlore.app.R.mipmap.ic_launcher,
            org.robolectric.util.ReflectionHelpers.getField<Int>(image, "mResource"),
        )
    }

    @Test
    fun `the clock is off by default and draws nothing over the icon`() {
        val view = newView()
        view.markSizePx = 112

        assertTrue(!view.chip.readoutShown)
    }

    @Test
    fun `with the clock on the chip shows the time and the bars the level`() {
        val view = newAttachedView()
        view.markSizePx = 112
        view.clockAndBattery = true
        view.onBatteryReading(percent = 64, charging = false)

        assertTrue(view.chip.readoutShown)
        assertTrue(view.chip.time.contains(':'))
        assertEquals(0.64f, view.chip.levelForTest, 1e-4f)
        assertEquals(AppIcon.DEFAULT.handleAccent.toInt(), view.chip.levelColorForTest)
        assertTrue(view.contentDescription.contains("battery 64%"))
    }

    /** The time is fitted to the icon, so a bigger handle gets bigger digits. */
    @Test
    fun `the time grows with the handle`() {
        val small = newAttachedView(sizePx = 120).apply { clockAndBattery = true }
        val big = newAttachedView(sizePx = 260).apply { clockAndBattery = true }

        assertTrue(small.chip.timeTextSizeForTest > 0f)
        assertTrue(big.chip.timeTextSizeForTest > small.chip.timeTextSizeForTest)
    }

    /** The panel's own bar has both, so over the panel they are clutter. */
    @Test
    fun `the panel hides the time and bars`() {
        val view = newAttachedView()
        view.markSizePx = 112
        view.clockAndBattery = true

        view.readoutHidden = true
        assertTrue(!view.chip.readoutShown)

        view.readoutHidden = false
        assertTrue(view.chip.readoutShown)
    }

    @Test
    fun `the bars take the icon's accent, and charging keeps it`() {
        val cyan = AppIcon.CYBER.handleAccent.toInt()
        val view = FoldTabView(
            RuntimeEnvironment.getApplication(),
            accentColor = cyan,
        )
        view.markSizePx = 112
        view.clockAndBattery = true

        view.onBatteryReading(percent = 80, charging = false)
        assertEquals(cyan, view.chip.levelColorForTest)

        view.onBatteryReading(percent = 5, charging = true)
        assertEquals(cyan, view.chip.levelColorForTest)
        assertTrue(view.chip.charging)
    }

    @Test
    fun `a low battery turns the bars red, or amber on a red icon`() {
        assertEquals(
            HandleReadoutColors.LOW_BATTERY_RED,
            HandleReadoutColors.battery(AppIcon.GOLD.handleAccent.toInt(), 10, charging = false),
        )
        assertEquals(
            HandleReadoutColors.LOW_BATTERY_AMBER,
            HandleReadoutColors.battery(AppIcon.DEMONIC.handleAccent.toInt(), 10, charging = false),
        )
        assertEquals(
            AppIcon.DEMONIC.handleAccent.toInt(),
            HandleReadoutColors.battery(AppIcon.DEMONIC.handleAccent.toInt(), 15, charging = false),
        )
    }

    /** Five bars, counted rather than estimated; any charge lights the first. */
    @Test
    fun `the level lights one bar per fifth, and any charge lights one`() {
        val view = newAttachedView()
        view.clockAndBattery = true
        val lit = { percent: Int ->
            view.onBatteryReading(percent = percent, charging = false)
            view.chip.litBars
        }
        assertEquals(0, lit(0))
        assertEquals(1, lit(1))
        assertEquals(1, lit(20))
        assertEquals(2, lit(21))
        assertEquals(3, lit(60))
        assertEquals(4, lit(64))
        assertEquals(5, lit(81))
        assertEquals(5, lit(100))
        // No reading yet: nothing lit rather than a guess.
        assertEquals(0, lit(-1))
    }

    @Test
    fun `a lost connection is marked on the chip and said aloud`() {
        val view = newAttachedView()
        view.clockAndBattery = true

        view.offline = true
        assertTrue(view.chip.offline)
        assertTrue(view.contentDescription.contains("no connection"))

        view.offline = false
        assertTrue(!view.chip.offline)
        assertTrue(!view.contentDescription.contains("no connection"))
    }
}
