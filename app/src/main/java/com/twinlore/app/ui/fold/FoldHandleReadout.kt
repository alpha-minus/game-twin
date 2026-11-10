package com.twinlore.app.ui.fold

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.widget.FrameLayout
import kotlin.math.ceil

/**
 * The colours the handle's battery bars are drawn in.
 *
 * The bars are the icon's own accent at any healthy level, and while charging
 * too: the bolt under the time says charging, so the bars do not have to give
 * up the icon's colour to say it again. Only a low battery overrides it.
 */
internal object HandleReadoutColors {
    /** The bars turn at the level the app's own battery pill does. */
    const val LOW_BATTERY_PERCENT = 15

    val LOW_BATTERY_RED: Int = Color.rgb(0xE5, 0x39, 0x35)

    /**
     * Low battery on an icon whose accent is already red. Red bars there
     * would look the same full or nearly empty.
     */
    val LOW_BATTERY_AMBER: Int = Color.rgb(0xFF, 0xB3, 0x00)

    /** An unlit bar: enough to show there are five, no more. */
    val TRACK: Int = Color.argb(0x50, 0xFF, 0xFF, 0xFF)

    fun battery(accent: Int, percent: Int, charging: Boolean): Int = when {
        charging || percent !in 0 until LOW_BATTERY_PERCENT -> accent
        isReddish(accent) -> LOW_BATTERY_AMBER
        else -> LOW_BATTERY_RED
    }

    private fun isReddish(color: Int): Boolean {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        return hsv[1] > 0.4f && (hsv[0] < 20f || hsv[0] > 340f)
    }
}

/**
 * The handle's square: the dark chip, the icon inside it, the battery as five
 * bars along its bottom edge, the time drawn over the icon, and a crossed-out
 * Wi-Fi mark along the top while there is no connection.
 *
 * Bars rather than the ring that came first: a ring's length around a rounded
 * square is hard to read as a level at a glance, and over a busy game it read
 * as decoration. Five bars are counted, not estimated.
 *
 * The time sits on the icon rather than beside or above it: a pill outside
 * the chip grew the window, had to be kept on screen at every edge, and still
 * read as a second thing next to the handle. On the icon it is one object. The
 * icon is dimmed under it just enough for white digits to hold over any art,
 * and stays recognisable through it.
 */
internal class HandleChip(context: Context) : FrameLayout(context) {

    /** The time, the bars and the dimming: the setting is on and nothing hides them. */
    var readoutShown: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    /**
     * No connection. Drawn only with the rest of the readout, and only when
     * true: a mark for being online would be one more thing on the icon that
     * is nearly always saying nothing.
     */
    var offline: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    var time: String = ""
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    var charging: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    /**
     * The time set in the app's pixel font. That face is wide, square and
     * reports line metrics far taller than its glyphs, so it is fitted and
     * centred by the digits' own drawn bounds rather than by the font's line.
     *
     * Drawn to read, not just to fit: at a text size between whole pixel
     * scales each font pixel smears across 2.6 screen pixels, and a blurred
     * shadow softens it further. So the size snaps to a whole scale, the
     * edges are hard, the outline is a solid one-pixel border instead of a
     * shadow, and the colon is drawn narrow so the digits get its width.
     */
    var pixelFont: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            timePaint.typeface = if (value) pixelTypeface(context) else CONDENSED
            timePaint.isAntiAlias = !value
            if (value) {
                timePaint.clearShadowLayer()
                timePaint.textAlign = Paint.Align.LEFT
            } else {
                timePaint.setShadowLayer(density * 2f, 0f, 0f, Color.BLACK)
                timePaint.textAlign = Paint.Align.CENTER
            }
            outlinePaint.typeface = timePaint.typeface
            fitTime()
            invalidate()
        }

    /** The icon's accent: the charging bolt. The bars' colour comes with [setLevel]. */
    var accent: Int = Color.WHITE
        set(value) {
            field = value
            boltPaint.color = value
            invalidate()
        }

    var cornerRadiusPx: Float = 0f
        set(value) {
            field = value
            invalidate()
        }

    /** How tall each battery bar is drawn. */
    var barHeightPx: Float = 0f
        set(value) {
            field = value
            invalidate()
        }

    private var level: Float = 0f
    private var levelColor: Int = Color.WHITE

    private val density = context.resources.displayMetrics.density

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = HandleReadoutColors.TRACK
    }
    private val levelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(DIM_ALPHA, 0, 0, 0)
    }

    // Condensed: the digits have to fit across the icon, and a condensed face
    // gets them taller for the same width, which is what reads at a glance.
    private val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = CONDENSED
        textAlign = Paint.Align.CENTER
        setShadowLayer(density * 2f, 0f, 0f, Color.BLACK)
    }
    private val boltPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    /** The pixel time's hard black border, drawn under the white digits. */
    private val outlinePaint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.MITER
        textAlign = Paint.Align.LEFT
    }

    private val wifiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = Color.WHITE
    }
    private val wifiDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }
    private val slashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = Color.WHITE
    }

    /** Under the slash, in the disc's colour, so a white slash stays apart from the white fan. */
    private val slashEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = Color.BLACK
    }
    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(0xC0, 0, 0, 0)
    }

    private val bolt = Path()
    private val glyphBounds = android.graphics.Rect()
    private val inner = RectF()
    private val bar = RectF()
    private val arc = RectF()

    init {
        // The readout is drawn over the children, after them.
        setWillNotDraw(false)
    }

    /** A new reading: [fraction] of the battery left, in [color]. */
    fun setLevel(fraction: Float, color: Int) {
        level = fraction.coerceIn(0f, 1f)
        levelColor = color
        levelPaint.color = color
        invalidate()
    }

    /**
     * How many of the five bars are lit: any charge at all lights the first,
     * so a battery with a few percent left does not read as dead.
     */
    val litBars: Int
        get() = if (level <= 0f) 0 else ceil(level * BAR_COUNT - 1e-4f).toInt().coerceIn(1, BAR_COUNT)

    @get:androidx.annotation.VisibleForTesting
    internal val levelForTest: Float get() = level

    @get:androidx.annotation.VisibleForTesting
    internal val levelColorForTest: Int get() = levelColor

    /** The time's text size, fitted to the chip: read by tests. */
    @get:androidx.annotation.VisibleForTesting
    internal val timeTextSizeForTest: Float get() = timePaint.textSize

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        fitTime()
    }

    /**
     * Sizes the time so the widest it can be spans most of the icon. Measured
     * once per size against [WIDEST_TIME], so the digits do not change size
     * as the minutes do.
     */
    private fun fitTime() {
        val across = (width - paddingLeft - paddingRight) * (if (pixelFont) PIXEL_TIME_SPAN else TIME_SPAN)
        if (across <= 0f) return
        if (!pixelFont) {
            timePaint.textSize = REFERENCE_SIZE
            val reference = timePaint.measureText(WIDEST_TIME)
            timePaint.textSize = REFERENCE_SIZE * across / reference
            return
        }
        // Four digits at one em each and a colon squeezed to [PIXEL_COLON_EM],
        // with the outline's pixel either side.
        var size = across / (4f + PIXEL_COLON_EM + 2f / PIXEL_GRID)
        timePaint.textSize = REFERENCE_SIZE
        timePaint.getTextBounds(WIDEST_TIME, 0, WIDEST_TIME.length, glyphBounds)
        val tallest = (height - paddingTop - paddingBottom) * PIXEL_TIME_HEIGHT
        if (glyphBounds.height() > 0 && tallest > 0f) {
            size = minOf(size, REFERENCE_SIZE * tallest / glyphBounds.height())
        }
        // The face is drawn on an 8 by 8 grid: a size on a whole multiple of
        // it puts every font pixel on whole screen pixels, which is the
        // difference between crisp blocks and mush.
        // Rounded to the nearest whole scale, up only while that still fits
        // the chip itself, so a small handle does not lose a third of its time.
        if (size >= PIXEL_GRID) {
            val down = kotlin.math.floor(size / PIXEL_GRID) * PIXEL_GRID
            val up = down + PIXEL_GRID
            val inner = (width - paddingLeft - paddingRight).toFloat()
            val upFits = up * (4f + PIXEL_COLON_EM + 2f / PIXEL_GRID) <= inner &&
                (tallest <= 0f || glyphBounds.height() <= 0 ||
                    up * glyphBounds.height() / REFERENCE_SIZE <= (height - paddingTop - paddingBottom) * 0.55f)
            size = if (upFits && up - size < size - down) up else down
        }
        timePaint.textSize = size
        outlinePaint.textSize = size
        outlinePaint.strokeWidth = 2f * size / PIXEL_GRID
        pixelStretch = pixelStretchFor(size, glyphBounds.height() * size / REFERENCE_SIZE)
    }

    /**
     * How much taller than wide the pixel digits are drawn. Their width is
     * spent by the time it fits across the icon, and a square digit at that
     * width stands far shorter than the condensed face's, so they are
     * stretched up, by a whole number of screen pixels per font pixel so the
     * rows stay even and the edges crisp.
     */
    private var pixelStretch: Float = 1f

    private fun pixelStretchFor(size: Float, glyphHeight: Float): Float {
        val fontPx = size / PIXEL_GRID
        val inner = (height - paddingTop - paddingBottom).toFloat()
        if (fontPx < 1f || glyphHeight <= 0f || inner <= 0f) return 1f
        val rows = glyphHeight / fontPx
        val tallPx = kotlin.math.round(inner * PIXEL_TIME_TALL / rows)
            .coerceIn(fontPx, fontPx * PIXEL_MAX_STRETCH)
        return tallPx / fontPx
    }

    /** Where the stretched digits' baseline lands, for what sits under them. */
    private fun stretchedBaseline(baseline: Float): Float =
        height / 2f + (baseline - height / 2f) * pixelStretch

    /**
     * The pixel time, laid out by hand: hours, a narrow colon, minutes,
     * centred as a whole, on whole pixels, over a solid outline.
     */
    private fun drawPixelTime(canvas: Canvas, cx: Float, baseline: Float) {
        val colon = time.indexOf(':')
        val em = timePaint.textSize
        val colonWidth = if (colon >= 0) em * PIXEL_COLON_EM else 0f
        val hours = if (colon >= 0) time.substring(0, colon) else time
        val minutes = if (colon >= 0) time.substring(colon + 1) else ""
        val hoursWidth = timePaint.measureText(hours)
        val total = hoursWidth + colonWidth + timePaint.measureText(minutes)
        val y = kotlin.math.round(baseline)
        var x = kotlin.math.round(cx - total / 2f)
        // Stretched about the chip's middle, which the digits are centred on.
        canvas.save()
        canvas.scale(1f, pixelStretch, cx, height / 2f)
        fun piece(text: String, at: Float) {
            canvas.drawText(text, at, y, outlinePaint)
            canvas.drawText(text, at, y, timePaint)
        }
        piece(hours, x)
        x += hoursWidth
        if (colon >= 0) {
            // The colon glyph sits centred in a full em; pulled left so it is
            // centred in the narrow slot instead.
            piece(":", kotlin.math.round(x + (colonWidth - em) / 2f))
            x += colonWidth
            piece(minutes, x)
        }
        canvas.restore()
    }

    /** The time's baseline, centring the digits themselves on the chip. */
    private fun timeBaseline(): Float {
        if (!pixelFont) return height / 2f - (timePaint.descent() + timePaint.ascent()) / 2f
        timePaint.getTextBounds(WIDEST_TIME, 0, WIDEST_TIME.length, glyphBounds)
        return height / 2f - glyphBounds.exactCenterY()
    }

    /** The top of the bars: the bottom padding is where they sit. */
    private fun barsTop(): Float = height - paddingBottom / 2f - barHeightPx

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (!readoutShown) return
        inner.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(inner, cornerRadiusPx, cornerRadiusPx, dimPaint)
        drawBars(canvas)
        val cx = width / 2f
        // Centred on the chip by the digits' own height, not the font's line.
        val baseline = timeBaseline()
        if (pixelFont) drawPixelTime(canvas, cx, baseline) else canvas.drawText(time, cx, baseline, timePaint)
        if (charging) drawBolt(canvas, cx, if (pixelFont) stretchedBaseline(baseline) else baseline)
        if (offline) drawOffline(canvas)
    }

    /**
     * Five bars across the bottom, inside the chip's rounded corners so the
     * outer two are not clipped by them.
     */
    private fun drawBars(canvas: Canvas) {
        if (barHeightPx <= 0f) return
        val left = maxOf(paddingLeft.toFloat(), cornerRadiusPx * 0.6f)
        val right = width - maxOf(paddingRight.toFloat(), cornerRadiusPx * 0.6f)
        val gap = maxOf(density * 1.5f, barHeightPx * 0.5f)
        val each = (right - left - gap * (BAR_COUNT - 1)) / BAR_COUNT
        if (each <= 0f) return
        val top = barsTop()
        val radius = barHeightPx / 2f
        val lit = litBars
        for (i in 0 until BAR_COUNT) {
            val x = left + i * (each + gap)
            bar.set(x, top, x + each, top + barHeightPx)
            canvas.drawRoundRect(bar, radius, radius, if (i < lit) levelPaint else trackPaint)
        }
    }

    /**
     * The bolt, on a 12 by 20 grid, in the space between the digits and the
     * top of the bars.
     */
    private fun drawBolt(canvas: Canvas, cx: Float, baseline: Float) {
        val room = barsTop() - baseline
        val boltHeight = (room * 0.7f).coerceAtMost(timePaint.textSize * 0.55f)
        if (boltHeight <= 0f) return
        val boltWidth = boltHeight * 12f / 20f
        val top = baseline + (room - boltHeight) / 2f
        val left = cx - boltWidth / 2f
        bolt.reset()
        BOLT_POINTS.forEachIndexed { i, (px, py) ->
            val x = left + px * boltWidth / 12f
            val y = top + py * boltHeight / 20f
            if (i == 0) bolt.moveTo(x, y) else bolt.lineTo(x, y)
        }
        bolt.close()
        canvas.drawPath(bolt, boltPaint)
    }

    /**
     * Wi-Fi, crossed out in white, on a small dark disc centred along the top,
     * above the time, so the two never overlap.
     */
    private fun drawOffline(canvas: Canvas) {
        val size = width * OFFLINE_SPAN
        if (size <= 0f) return
        val inset = maxOf(paddingTop.toFloat() * 0.5f, density)
        val cx = width / 2f
        val cy = inset + size / 2f
        canvas.drawCircle(cx, cy, size / 2f, badgePaint)

        // The fan: two arcs over a dot, centred on a point low in the disc.
        val stroke = size * 0.11f
        wifiPaint.strokeWidth = stroke
        slashPaint.strokeWidth = stroke
        slashEdgePaint.strokeWidth = stroke * 1.7f
        val ox = cx
        val oy = cy + size * 0.24f
        for (r in floatArrayOf(size * 0.38f, size * 0.22f)) {
            arc.set(ox - r, oy - r, ox + r, oy + r)
            canvas.drawArc(arc, 225f, 90f, false, wifiPaint)
        }
        canvas.drawCircle(ox, oy - stroke * 0.2f, stroke * 0.75f, wifiDotPaint)

        val s = size * 0.3f
        canvas.drawLine(cx - s, cy - s, cx + s, cy + s, slashEdgePaint)
        canvas.drawLine(cx - s, cy - s, cx + s, cy + s, slashPaint)
    }

    private companion object {
        /** Digits are tabular in the default fonts, so this is the widest time. */
        const val WIDEST_TIME = "00:00"

        /** How much of the icon's width the widest time spans. */
        const val TIME_SPAN = 0.9f

        /**
         * The pixel font's share: nearly all of it, because its square glyphs
         * are small for their width and need every pixel to read.
         */
        const val PIXEL_TIME_SPAN = 0.98f

        /** The most of the chip's inner height the pixel digits may stand. */
        const val PIXEL_TIME_HEIGHT = 0.45f

        /** How much of the chip's inner height the stretched pixel digits stand. */
        const val PIXEL_TIME_TALL = 0.34f

        /** Never taller than this many times their width: past it they stop reading as the font. */
        const val PIXEL_MAX_STRETCH = 2.5f

        /** The pixel face's design grid: one em is eight font pixels. */
        const val PIXEL_GRID = 8f

        /** The colon's slot, in ems: its glyph is two font pixels wide, its cell eight. */
        const val PIXEL_COLON_EM = 0.5f

        val CONDENSED: Typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)

        /** Loaded once: the same asset the app's typography uses for [com.twinlore.app.data.AppFont.PIXEL]. */
        @Volatile private var pixel: Typeface? = null

        fun pixelTypeface(context: Context): Typeface =
            pixel ?: runCatching { Typeface.createFromAsset(context.assets, PIXEL_FONT_ASSET) }
                .getOrDefault(CONDENSED)
                .also { pixel = it }

        const val PIXEL_FONT_ASSET = "fonts/press_start_2p.ttf"

        /** Any size will do: the fit scales from it linearly. */
        const val REFERENCE_SIZE = 100f

        /** Enough for white digits over bright art; the icon still shows through. */
        const val DIM_ALPHA = 110

        const val BAR_COUNT = 5

        /** The offline mark's diameter as a fraction of the chip. */
        const val OFFLINE_SPAN = 0.3f

        val BOLT_POINTS = listOf(
            7.5f to 0f, 0f to 11.5f, 5f to 11.5f, 4f to 20f, 12f to 8f, 7f to 8f,
        )
    }
}
