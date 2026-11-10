package com.twinlore.app.ui.screens

import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.PopupWindow
import android.widget.TextView

/**
 * A floating selection toolbar for a WebView in a window that cannot make one.
 *
 * In an activity the window's DecorView answers `startActionModeForChild` with
 * the platform's floating toolbar. The fold panel is a bare
 * TYPE_APPLICATION_OVERLAY window with no DecorView, so the request walks up to
 * ViewRootImpl and comes back null. The WebView then keeps the word it
 * highlighted but shows no handles and no menu, which is what the Thor showed:
 * a selection that could be neither adjusted nor copied.
 *
 * This stands in for the platform's mode. The menu is a real [Menu] (borrowed
 * from a [PopupMenu], which builds one), the toolbar is a [PopupWindow], which
 * an overlay window can parent as a sub-panel, and it sits above the selection
 * the callback reports, or below it when there is no room above.
 */
internal class HostlessSelectionActionMode(
    private val anchor: View,
    private val callback: Callback,
) : android.view.ActionMode() {

    private val menu: Menu = PopupMenu(anchor.context, anchor).menu
    private val popup = PopupWindow(anchor.context).apply {
        isFocusable = false
        isTouchable = true
        isOutsideTouchable = false
        setBackgroundDrawable(null)
        elevation = dp(4f)
    }
    private var finished = false
    private val reshow = Runnable { if (!finished) show() }

    init {
        type = TYPE_FLOATING
    }

    /** Creates the mode, or null when the callback declines, as the platform does. */
    fun start(): HostlessSelectionActionMode? {
        if (!callback.onCreateActionMode(this, menu)) return null
        callback.onPrepareActionMode(this, menu)
        show()
        return this
    }

    private fun buildToolbar(): View {
        val row = LinearLayout(anchor.context).apply {
            orientation = LinearLayout.HORIZONTAL
            val pad = dp(4f).toInt()
            setPadding(pad, 0, pad, 0)
        }
        for (i in 0 until menu.size()) {
            val item = menu.getItem(i)
            if (!item.isVisible) continue
            row.addView(TextView(anchor.context).apply {
                text = item.title
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                gravity = Gravity.CENTER
                minHeight = dp(44f).toInt()
                val h = dp(12f).toInt()
                setPadding(h, 0, h, 0)
                isClickable = true
                setOnClickListener { callback.onActionItemClicked(this@HostlessSelectionActionMode, item) }
            })
        }
        return HorizontalScrollView(anchor.context).apply {
            isHorizontalScrollBarEnabled = false
            background = GradientDrawable().apply {
                setColor(0xF0303134.toInt())
                cornerRadius = dp(22f)
            }
            addView(row)
        }
    }

    private fun show() {
        if (finished || anchor.windowToken == null) return
        val content = buildToolbar()
        val maxWidth = (anchor.width - dp(16f).toInt()).coerceAtLeast(1)
        content.measure(
            View.MeasureSpec.makeMeasureSpec(maxWidth, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        val w = content.measuredWidth
        val h = content.measuredHeight

        val rect = Rect()
        val cb = callback
        if (cb is Callback2) cb.onGetContentRect(this, anchor, rect)
        else rect.set(0, 0, anchor.width, anchor.height)
        val loc = IntArray(2)
        anchor.getLocationInWindow(loc)
        val gap = dp(8f).toInt()
        // Below the selection clears its end handle, which hangs under the text.
        val handleClearance = dp(28f).toInt()
        var y = rect.top - h - gap
        if (y < 0) y = (rect.bottom + handleClearance).coerceAtMost(anchor.height - h)
        val x = (rect.centerX() - w / 2).coerceIn(gap, (anchor.width - w - gap).coerceAtLeast(gap))

        popup.contentView = content
        popup.width = ViewGroup.LayoutParams.WRAP_CONTENT
        popup.height = ViewGroup.LayoutParams.WRAP_CONTENT
        if (popup.isShowing) {
            popup.update(loc[0] + x, loc[1] + y, w, h)
        } else {
            runCatching { popup.showAtLocation(anchor, Gravity.NO_GRAVITY, loc[0] + x, loc[1] + y) }
        }
    }

    override fun invalidate() {
        callback.onPrepareActionMode(this, menu)
        show()
    }

    override fun invalidateContentRect() {
        anchor.removeCallbacks(reshow)
        show()
    }

    override fun hide(duration: Long) {
        // The WebView hides the toolbar while a handle is dragged or the page
        // scrolls, and asks for it back with invalidateContentRect.
        anchor.removeCallbacks(reshow)
        popup.dismiss()
        if (duration == 0L) return show()
        val wait = if (duration == DEFAULT_HIDE_DURATION.toLong()) 2000L else duration.coerceAtMost(3000L)
        anchor.postDelayed(reshow, wait)
    }

    override fun finish() {
        if (finished) return
        finished = true
        anchor.removeCallbacks(reshow)
        popup.dismiss()
        callback.onDestroyActionMode(this)
    }

    override fun getMenu(): Menu = menu
    override fun getMenuInflater(): MenuInflater = MenuInflater(anchor.context)
    override fun setTitle(title: CharSequence?) {}
    override fun setTitle(resId: Int) {}
    override fun setSubtitle(subtitle: CharSequence?) {}
    override fun setSubtitle(resId: Int) {}
    override fun setCustomView(view: View?) {}
    override fun getTitle(): CharSequence? = null
    override fun getSubtitle(): CharSequence? = null
    override fun getCustomView(): View? = null

    private fun dp(v: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, anchor.resources.displayMetrics)
}
