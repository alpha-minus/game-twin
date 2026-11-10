package com.twinlore.app.ui.fold

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.BatteryManager
import android.text.format.DateFormat
import android.util.Log
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.annotation.VisibleForTesting
import com.twinlore.app.R
import com.twinlore.app.data.HapticFeedbackIntensity
import com.twinlore.app.data.AppIcon
import com.twinlore.app.data.fold.FoldHandleStyle
import java.util.Date
import com.twinlore.app.ui.utils.FeedbackEvent
import com.twinlore.app.ui.utils.FeedbackManager
import kotlin.math.abs
import kotlin.math.hypot

/**
 * The handle: the overlay collapsed, and the one thing on screen while the
 * panel is down.
 *
 * A plain view on purpose. Compose popups and dialogs are sub-windows that
 * need an activity window token, and this lives in a TYPE_APPLICATION_OVERLAY
 * window where no such token exists. Nothing here opens a popup, so nothing
 * here can hit that.
 */
class FoldTabView(
    context: Context,
    iconRes: Int = R.mipmap.ic_launcher,
    /** The selected icon's accent: the battery bars and the charging bolt. */
    accentColor: Int = AppIcon.DEFAULT.handleAccent.toInt(),
) : FrameLayout(context) {

    /**
     * The selected icon's accent, settable so an icon picked from the panel's
     * own settings shows on the handle that is already up.
     */
    private var accentColor: Int = accentColor

    /** The mark's resource, as last set: the drawable itself cannot be compared. */
    @VisibleForTesting
    internal var iconResForTest: Int = iconRes
        private set

    companion object {
        /**
         * The opacity a tab is drawn at when nothing has told it otherwise.
         * A default only: the host sets [handleAlpha] from the stored
         * [FoldHandleStyle] value, and this is that value's own default so
         * the two cannot drift.
         */
        const val DEFAULT_ALPHA = FoldHandleStyle.DEFAULT_ALPHA

        /**
         * How big the mark is drawn while the panel is open, when nothing has
         * said otherwise. A default only: the host sets [recessScale] from the
         * stored [com.twinlore.app.data.fold.FoldHandleStyle] value.
         *
         * 1f, so the handle keeps the size it was chosen at. It was a
         * hardcoded 0.62 for everybody, which is a judgement about how much of
         * the page the handle may cover, made on behalf of a reader whose
         * guide and screen this code has never seen. See [recessed].
         */
        const val DEFAULT_RECESS_SCALE = 1f

        /**
         * The ring drawn around the tab while a hold is registering. 2dp
         * against the 44dp of the smallest tab: readable over a game
         * underneath without swallowing the icon.
         */
        const val RING_WIDTH_DP = 2
        val RING_COLOR: Int = Color.argb(255, 255, 255, 255)

        /**
         * How long a press has to last to count as a hold rather than a tap.
         *
         * Longer than the platform's 500ms default on purpose: the tap is the
         * important gesture and the one people will make in a hurry, and a
         * hurried tap that leaves for the app instead is a much worse outcome
         * than a hold that has to be held a moment longer.
         */
        const val LONG_PRESS_MS = 700L

        /**
         * The battery bars' height as a fraction of the icon's size, held
         * between bounds so the smallest handle's bars still read and the
         * largest's do not eat the icon.
         */
        const val BAR_FRACTION = 0.06f
        const val MIN_BAR_DP = 3f
        const val MAX_BAR_DP = 5f

        private const val TAG = "FoldTabView"
    }

    var onTap: () -> Unit = {}

    /**
     * A hold: the host brings the whole app back.
     *
     * The tap opens the panel and the hold is the destination past it. Fired
     * while the finger is still down, with a ring and a tick to say so.
     */
    var onHold: () -> Unit = {}

    /** The user's haptic setting, mirrored in by the host. */
    var hapticIntensity: HapticFeedbackIntensity = HapticFeedbackIntensity.MEDIUM

    /**
     * What the hold feels like. A property so tests can count it: the real
     * thing goes through the motor and Robolectric has none.
     */
    @VisibleForTesting
    internal var playHoldFeedback: () -> Unit = {
        FeedbackManager.play(this, FeedbackEvent.CONFIRM, hapticIntensity)
    }

    /** Whether the hold ring is up. Read by tests; the drawable is not. */
    @VisibleForTesting
    internal var ringVisible: Boolean = false
        private set
    var onDragTo: (rawX: Float, rawY: Float) -> Unit = { _, _ -> }
    var onDragEnd: (rawX: Float, rawY: Float) -> Unit = { _, _ -> }

    /**
     * How solid the handle is drawn. 0f means invisible, which is reachable on
     * purpose: alpha only, so a fully transparent handle is still fully
     * tappable and draggable.
     *
     * Constant, and that is the whole of the behaviour. The handle used to
     * start solid, fade to this after three idle seconds, and jump back to
     * solid under a finger, which meant the value the user chose was the one
     * value they mostly did not see: over a game, the handle sat at its
     * chosen opacity only in the moments nobody was looking at it. Whatever is
     * set here is what is on screen, always.
     */
    var handleAlpha: Float = DEFAULT_ALPHA
        set(value) {
            field = value.coerceIn(0f, 1f)
            alpha = field
        }

    /**
     * Drawn smaller while the panel is open.
     *
     * The handle has two audiences. Over a running game it competes with
     * whatever the game is drawing and has to be findable, so it is full size.
     * Over the panel it only has to be tappable: the surface under it is the
     * app's own and calm, and the thing it would otherwise obscure is the page
     * somebody is reading. The panel used to reserve a strip down its whole
     * side to stay clear of it, which spent a fixed slice of every screen on an
     * overlap that is small, occasional, and in a corner the user chose.
     *
     * Scale, not size: the window keeps its full bounds, so the touch target
     * does not shrink with the mark. And scale rather than alpha, because
     * alpha is the user's own setting: [handleAlpha] is what they chose and
     * what stays on screen, so nothing else may write to it.
     */
    var recessed: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            applyScale()
        }

    /**
     * How small [recessed] makes it, as a fraction of the handle's own size.
     * 1f means it does not shrink at all, which is the default.
     *
     * Applied immediately when the handle is already recessed, which is what
     * lets the slider that sets this be dragged with the panel open and show
     * its own effect: the thing being sized is on screen the whole time.
     */
    var recessScale: Float = DEFAULT_RECESS_SCALE
        set(value) {
            field = value.coerceIn(0f, 1f)
            applyScale()
        }

    private fun applyScale() {
        val target = if (recessed) recessScale else 1f
        scaleX = target
        scaleY = target
    }

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var dragging = false

    private val tabBackground = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(12f)
        setColor(Color.argb(180, 0, 0, 0))
    }

    @VisibleForTesting
    internal val iconView = ImageView(context).apply {
        setImageResource(iconRes)
        scaleType = ImageView.ScaleType.FIT_CENTER
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
    }

    /**
     * The dark square the icon sits on, the battery bars along its bottom, and
     * the time drawn over the icon.
     */
    @VisibleForTesting
    internal val chip = HandleChip(context).apply {
        background = tabBackground
        cornerRadiusPx = dp(12f)
        accent = accentColor
        val padding = dp(4f).toInt()
        setPadding(padding, padding, padding, padding)
        addView(iconView)
    }

    /**
     * Swaps the mark and its accent in place: the handle that is up becomes
     * the icon just picked, with no new window and no relayout.
     */
    fun setIcon(iconRes: Int, accentColor: Int) {
        iconResForTest = iconRes
        iconView.setImageResource(iconRes)
        this.accentColor = accentColor
        chip.accent = accentColor
        refreshReadout()
    }

    /** The time in the app's pixel font, when that is the font the app is set in. */
    var pixelFont: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            chip.pixelFont = value
        }

    /** Whether the handle shows the time and the battery. */
    var clockAndBattery: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            arrange()
            if (isAttachedToWindow) {
                if (value) startReadoutUpdates() else stopReadoutUpdates()
            }
        }

    /**
     * Hidden while the panel is open: the panel's own bar has the time and the
     * battery, and over a page somebody is reading they are only clutter. The
     * icon is drawn plain again, at full brightness.
     */
    var readoutHidden: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            arrange()
        }

    /**
     * No connection: a crossed-out Wi-Fi mark along the top, drawn with the
     * rest of the readout. The host decides when the connection counts as
     * lost (see [AndroidFoldPlatform]); this only shows it.
     */
    var offline: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            chip.offline = value
            refreshReadout()
        }

    /**
     * The icon's own size in px, the handle size the user picked. The bars'
     * height follows it; the time is fitted to the chip when it is drawn.
     */
    var markSizePx: Int = 0
        set(value) {
            field = value
            chip.barHeightPx = (value * BAR_FRACTION).coerceIn(dp(MIN_BAR_DP), dp(MAX_BAR_DP))
        }

    private val handleDescription = context.getString(R.string.fold_handle_description)

    init {
        addView(chip)
        background = null
        contentDescription = handleDescription
        alpha = handleAlpha
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private fun arrange() {
        chip.readoutShown = clockAndBattery && !readoutHidden
        if (!clockAndBattery) contentDescription = handleDescription
        refreshReadout()
    }

    @VisibleForTesting
    internal var batteryPercent: Int = -1
        private set

    @VisibleForTesting
    internal var batteryCharging = false
        private set

    /** Redraws the time and the ring from the clock and the last battery reading. */
    @VisibleForTesting
    internal fun refreshReadout() {
        if (!clockAndBattery) return
        val pattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm"
        val time = DateFormat.format(pattern, Date()).toString()
        chip.time = time
        chip.charging = batteryCharging
        chip.setLevel(
            fraction = if (batteryPercent >= 0) batteryPercent / 100f else 0f,
            color = HandleReadoutColors.battery(accentColor, batteryPercent, batteryCharging),
        )
        contentDescription = buildString {
            append(handleDescription).append(", ").append(time)
            if (batteryPercent >= 0) {
                append(", battery ").append(batteryPercent).append('%')
                if (batteryCharging) append(", charging")
            }
            if (offline) append(", no connection")
        }
    }

    /** A battery reading, as the broadcast below delivers it. */
    @VisibleForTesting
    internal fun onBatteryReading(percent: Int, charging: Boolean) {
        batteryPercent = percent
        batteryCharging = charging
        refreshReadout()
    }

    /**
     * The battery is followed by its broadcast and the clock by the minute
     * tick. Receivers rather than a poll: this window can sit over a game for
     * hours, and the system already says when either changes.
     *
     * Registered on attach and dropped on detach, the view's own lifetime,
     * which is the lifetime of the window it is in.
     */
    private val readoutReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_BATTERY_CHANGED) readBattery(intent)
            else refreshReadout()
        }
    }
    private var readoutRegistered = false

    private fun readBattery(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        onBatteryReading(
            percent = if (level >= 0 && scale > 0) level * 100 / scale else -1,
            charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL ||
                intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0,
        )
    }

    private fun startReadoutUpdates() {
        if (readoutRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        runCatching { context.applicationContext.registerReceiver(readoutReceiver, filter) }
            .onSuccess { sticky ->
                readoutRegistered = true
                if (sticky != null) readBattery(sticky)
            }
            .onFailure { Log.w(TAG, "Could not follow the battery and clock", it) }
        refreshReadout()
    }

    private fun stopReadoutUpdates() {
        if (!readoutRegistered) return
        runCatching { context.applicationContext.unregisterReceiver(readoutReceiver) }
        readoutRegistered = false
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (clockAndBattery) startReadoutUpdates()
    }

    /**
     * The hold's confirmation ring. Drawn on the tab's own background rather
     * than as an extra view: the background is already a [GradientDrawable],
     * so a stroke is one call and cannot disturb the icon's layout.
     *
     * Up from the moment the hold registers until the finger lifts, and
     * cleared on the window teardown too, since a press still down when the
     * window goes leaves no UP or CANCEL behind.
     */
    private fun setRingVisible(visible: Boolean) {
        ringVisible = visible
        tabBackground.setStroke(
            if (visible) dp(RING_WIDTH_DP.toFloat()).toInt() else 0,
            RING_COLOR,
        )
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopReadoutUpdates()
        // Nothing is meant to be scheduled once the window is gone: a pending
        // long press holds a detached view to fire a gesture at an overlay
        // that is no longer on screen.
        removeCallbacks(longPressRunnable)
        // A press that was still down when the window went away leaves no UP
        // or CANCEL behind to clear the ring.
        setRingVisible(false)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                dragging = false
                held = false
                postDelayed(longPressRunnable, LONG_PRESS_MS)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                // Once the hold has fired the finger has nothing left to do:
                // moving must not drag the handle out from under it.
                if (held) return true
                if (!dragging &&
                    hypot(abs(event.rawX - downX), abs(event.rawY - downY)) > touchSlop
                ) {
                    dragging = true
                    // A drag is a move, not a hold, whatever the clock says.
                    removeCallbacks(longPressRunnable)
                }
                if (dragging) onDragTo(event.rawX, event.rawY)
                return true
            }
            MotionEvent.ACTION_UP -> {
                removeCallbacks(longPressRunnable)
                // Three gestures, and only one may fire per press. A hold has
                // already left; releasing must not also tap.
                when {
                    held -> Unit
                    dragging -> onDragEnd(event.rawX, event.rawY)
                    else -> onTap()
                }
                endGesture()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longPressRunnable)
                endGesture()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun endGesture() {
        setRingVisible(false)
        dragging = false
        held = false
    }

    /**
     * The hold, fired while the finger is still down.
     *
     * On the press rather than the release so the ring and the label can
     * confirm it landed: the whole gesture is a drag that follows, and finding
     * out only on release whether it had begun would make it unusable.
     */
    private val longPressRunnable = Runnable {
        if (dragging) return@Runnable
        held = true
        setRingVisible(true)
        playHoldFeedback()
        onHold()
    }

    private var held = false
}
