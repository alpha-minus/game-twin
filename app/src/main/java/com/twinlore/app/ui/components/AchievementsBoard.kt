package com.twinlore.app.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.twinlore.app.journey.domain.pluralize
import com.twinlore.app.journey.ui.chart.HARDCORE_RING
import java.io.File
import kotlin.math.ceil
import kotlin.math.floor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One badge tile on the achievements board, unified across RetroAchievements and Steam. */
data class AchievementsBoardBadge(
    val url: String?,
    val unlocked: Boolean,
    /** RA supplies one colour badge (we desaturate locked); Steam has its own gray icon URL. */
    val grayscaleWhenLocked: Boolean,
    /**
     * Whether this unlock was taken in hardcore, which the tile rings in RetroAchievements' own
     * yellow ([HARDCORE_RING]).
     *
     * Only meaningful when [unlocked]: a locked achievement has not been earned in any mode. Steam
     * has no such distinction and leaves it false, so its board simply draws no rings.
     */
    val hardcore: Boolean = false,
)

/** Everything the board renders; source-agnostic (RA or Steam). */
data class AchievementsBoardData(
    val title: String,
    /** Game image for the header (RA game icon / Steam header / vault artwork). */
    val gameImage: Any?,
    /**
     * Width over height of [gameImage], drawn at that shape rather than cropped
     * to a square. 1 for RA icons and unknown artwork; Steam's store banner is 460:215.
     */
    val gameImageAspect: Float = 1f,
    val earned: Int,
    val total: Int,
    /** RA hardcore unlock count — drawn as the gold overlay on the dual bar. */
    val hardcoreEarned: Int = 0,
    /** e.g. "305 of 570 points" (RA only). */
    val pointsLine: String? = null,
    /** RA: silver softcore + gold hardcore dual bar. Steam: single accent bar. */
    val dualBar: Boolean = false,
    /** RA `highestAwardKind` (mastered / completed / beaten-*); null draws no award. */
    val highestAwardKind: String? = null,
    /**
     * How long this player has had the game open, in minutes, or null when nothing knows.
     *
     * Drawn beside the award as the journey's single-game header draws it - "Mastered in 42h 10m",
     * or a plain "Played 42h 10m" when there is no award to name the hours (user request).
     */
    val playtimeMinutes: Int? = null,
    /**
     * When this player last unlocked something in the game, or null when they never have.
     *
     * The last unlock rather than the last launch: RetroAchievements' own `LastPlayed` is not part
     * of the progress payload this board is built from, and the newest unlock is the most recent
     * thing this screen actually knows. A game played last night with nothing earned therefore
     * still reads as whenever the last achievement came.
     */
    val lastPlayedMillis: Long? = null,
    /**
     * When this player first unlocked something in the game, or null when they never have.
     *
     * The far end of the run the header's "over ..." clause measures; see [boardPlayedLine]. Same
     * source and same caveat as [lastPlayedMillis]: the oldest unlock, not the first launch, which
     * is not something the progress payload knows.
     */
    val firstUnlockMillis: Long? = null,
    val badges: List<AchievementsBoardBadge> = emptyList(),
)

/**
 * How far through the achievement list this player is: "All 54 achievements" once the set is
 * complete, "37 of 54 achievements" while it is not.
 *
 * The same distinction the Achievement Journey's single-game header makes
 * ([com.twinlore.app.journey.ui.JourneyStripText.gameHeader]), so a game reads the same way in the
 * grid and in the journey of it. A full set says so rather than dividing a number by itself.
 */
internal fun boardAchievementsLine(earned: Int, total: Int): String =
    if (total > 0 && earned >= total) "All $total achievements" else "$earned of $total achievements"

/**
 * A stretch of play in full words: "15 hours, 26 minutes".
 *
 * Spelled out rather than the app's usual compact "15h 26m" because this line sits on a header
 * that now gives each fact a line of its own, where the abbreviation reads as a cipher rather than
 * as a saving. A part that is zero is not said at all, so three hours is "3 hours" and not
 * "3 hours, 0 minutes".
 */
internal fun fmtWordyDuration(minutes: Int): String {
    val hours = minutes / 60
    val rest = minutes % 60
    val parts = listOfNotNull(
        pluralize(hours, "hour").takeIf { hours > 0 },
        pluralize(rest, "minute").takeIf { rest > 0 },
    )
    return if (parts.isEmpty()) pluralize(0, "minute") else parts.joinToString(", ")
}

/** A flat month, for [fmtCoarseSpan]. See its doc for why this is not a calendar month. */
private const val SPAN_MONTH_DAYS = 30

/**
 * How long a run of unlocks was spread over: "1 month, 18 days", "18 days, 4 hours", "4 hours".
 *
 * The two coarsest non-zero units and no more, which is as much precision as anybody reads off a
 * line like this. Null for a span with nothing worth naming - under an hour, or unknown - and the
 * caller then leaves the whole clause off rather than printing an empty one.
 *
 * A month here is a flat thirty days, deliberately. This is an approximate readout of how long a
 * player kept at a game; real calendar arithmetic would have two runs of identical length print
 * different answers depending on which month they happened to start in, which is a distinction the
 * sentence does not mean to draw.
 */
internal fun fmtCoarseSpan(millis: Long): String? {
    if (millis <= 0L) return null
    val totalHours = millis / 3_600_000L
    val totalDays = (totalHours / 24).toInt()
    val months = totalDays / SPAN_MONTH_DAYS
    val days = totalDays % SPAN_MONTH_DAYS
    val hours = (totalHours % 24).toInt()
    val parts = listOfNotNull(
        pluralize(months, "month").takeIf { months > 0 },
        pluralize(days, "day").takeIf { days > 0 },
        pluralize(hours, "hour").takeIf { hours > 0 },
    )
    if (parts.isEmpty()) return null
    return parts.take(2).joinToString(", ")
}

/**
 * What the game cost and over how long: "Mastered in 15 hours, 26 minutes over 1 month, 18 days",
 * or a plain "Played 40 minutes" with no award to name the hours and no span worth naming.
 *
 * Null when nothing knows the hours: a board with no playtime says nothing rather than inventing a
 * zero. [spanMillis] is the run from the first unlock to the last; an unknown or trivially short
 * one drops the clause instead of shortening it.
 */
internal fun boardPlayedLine(
    playtimeMinutes: Int?,
    highestAwardKind: String?,
    spanMillis: Long? = null,
): String? {
    if (playtimeMinutes == null || playtimeMinutes <= 0) return null
    val award = when (highestAwardKind) {
        "mastered" -> "Mastered in"
        "completed" -> "Completed in"
        "beaten-hardcore" -> "Beaten in"
        "beaten-softcore" -> "Beaten (casual) in"
        else -> "Played"
    }
    val time = fmtWordyDuration(playtimeMinutes)
    val span = spanMillis?.let(::fmtCoarseSpan)
    return if (span == null) "$award $time" else "$award $time over $span"
}

/**
 * RetroAchievements stamps an unlock "yyyy-MM-dd HH:mm:ss" in UTC (the same shape
 * `SyncLatestPlayedGameUseCase` parses for its own last-played read); anything else is ignored
 * rather than guessed at.
 */
private fun unlockMillis(dateAwarded: List<String?>): List<Long> {
    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
    sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
    return dateAwarded.mapNotNull { raw ->
        raw?.takeIf { it.isNotBlank() }?.let { runCatching { sdf.parse(it)?.time }.getOrNull() }
    }
}

/** The newest unlock in [dateAwarded], in millis, or null when none of them parsed. */
internal fun latestUnlockMillis(dateAwarded: List<String?>): Long? = unlockMillis(dateAwarded).maxOrNull()

/**
 * The oldest one, for the far end of the run [boardPlayedLine]'s "over ..." clause measures.
 *
 * The first unlock rather than the first launch: nothing in the progress payload this board is
 * built from knows when the player first opened the game, and the oldest unlock is the earliest
 * thing this screen actually knows.
 */
internal fun earliestUnlockMillis(dateAwarded: List<String?>): Long? = unlockMillis(dateAwarded).minOrNull()

/**
 * "Last played August 18 2026": an absolute date rather than a relative one.
 *
 * The board is shared as a picture, and "Last played today" on an image somebody opens next week
 * is a sentence that has quietly become false. A date stays true however long the file lives.
 */
internal fun boardLastPlayedLine(lastPlayedMillis: Long?): String? {
    if (lastPlayedMillis == null || lastPlayedMillis <= 0L) return null
    val date = java.text.SimpleDateFormat("MMMM d yyyy", java.util.Locale.getDefault())
        .format(java.util.Date(lastPlayedMillis))
    return "Last played $date"
}

/**
 * [line] with its digits in bold, so a fact reads as a number with words around it rather than as
 * a uniform grey sentence.
 *
 * Runs of digits, not words: "All 54 achievements", "301 of 301 points" and "15 hours, 26 minutes"
 * all want the same treatment, and picking the numbers out by position would need a rule per line.
 */
internal fun boldNumerals(line: String) = buildAnnotatedString {
    var i = 0
    while (i < line.length) {
        val start = i
        val digit = line[i].isDigit()
        while (i < line.length && line[i].isDigit() == digit) i++
        val run = line.substring(start, i)
        if (digit) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(run) } else append(run)
    }
}

/**
 * The real screen bounds (width, height) the board's clearance is measured against: the window
 * manager's own metrics on API 30+, the display metrics before that. The VIEW's size cannot stand
 * in for this - the whole point is that the dialog window may be smaller or offset.
 */
private fun boardScreenBounds(view: android.view.View): Pair<Int, Int> {
    if (android.os.Build.VERSION.SDK_INT >= 30) {
        val wm = view.context.getSystemService(android.view.WindowManager::class.java)
        if (wm != null) {
            val b = wm.currentWindowMetrics.bounds
            return b.width() to b.height()
        }
    }
    val dm = view.resources.displayMetrics
    return dm.widthPixels to dm.heightPixels
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * The most a badge's art is ever drawn at, in dp.
 *
 * The board's previous ceiling was 72dp, chosen back when the grid was fit-to-space on a phone.
 * On the AYN Thor's wide landscape board that cap turned out never to matter (69 badges fit at
 * ~85px / ~37dp, well under it) while a small game's badges - the case a cap is actually FOR -
 * had room to be much bigger than 72dp and weren't. 128dp is chosen instead: a native-size cap
 * (badges are 64px art) was tried and reverted before this task even started, because it stranded
 * a small grid in the middle of the screen; the user's own reference for "reads fine, enlarged" is
 * RetroAchievements' own site, which upscales this same 64px art to roughly 155px. 128dp lands
 * short of that rather than at it - two full native sizes (2x), which is where smooth
 * (non-pixel-art) upscaling of small source art still reads crisply on a screen held at arm's
 * length, without inviting the blur that matching RA's own figure exactly would start to show.
 */
internal const val BADGE_TILE_MAX_DP = 128f

/**
 * The hardcore ring's stroke width, in dp.
 *
 * A border drawn on the art's own box paints over its outermost pixels, and RA badge art carries
 * its own decorative frame right at the edge - so the ring is drawn on a box outside the art
 * instead (see [badgeRingBoxDp]), in a gap every cell reserves whether or not it ends up ringed
 * (see [GRID_GAP_DP]), rather than out of the art itself. That gap is what keeps a hardcore badge
 * from sitting closer to its neighbours than any other tile, and what stops two adjacent rings
 * from reading as a single gold mesh on a set where nearly every earned badge is hardcore (this
 * user's own games) - one of this codebase's rejected approaches shrank the art by this width on
 * every cell instead, which fixed neither problem: it cost every badge art size for a ring most of
 * them never drew, and adjacent rings still sat only `width + width` apart.
 *
 * 2.dp, unchanged from the tile-shrinking design this replaces: at the previous 72.dp tile cap
 * that read at roughly the same proportion as RetroAchievements' own badge frames (about 2.5% of
 * the tile edge), and it is still clearly visible now that the tile cap has grown to
 * [BADGE_TILE_MAX_DP] (128.dp) - a thinner proportion of a bigger badge, but a ring exists to mark
 * hardcore at a glance rather than to dominate the art, and 2.dp still does that without a second
 * complaint from the user about its weight. If the tile cap changes again, revisit this width too.
 */
internal const val HARDCORE_RING_WIDTH_DP = 2f

/**
 * The gap reserved between adjacent grid cells, in dp - where the hardcore ring is drawn, so it
 * never has to be carved out of a badge's art. Exactly twice [HARDCORE_RING_WIDTH_DP]: one ring
 * width on each side of a cell, so the ring's outer edge lands precisely on the cell boundary
 * and the badges sit flush against each other (the previous 4x
 * reserved a further ring width of dark space per side, which read as empty gaps in the grid).
 * Adjacent rings may now touch; the art itself still never carries a ring stroke.
 */
internal const val GRID_GAP_DP = HARDCORE_RING_WIDTH_DP * 2f

/**
 * The largest grid cell (in dp - a square pitch, not just the art inside it) that fits [count]
 * items into [availableW] x [availableH], up to [maxCell].
 *
 * This replaces `cols = ceil(sqrt(n * aspect))`, `aspect` being the container's own width /
 * height. That formula picks a column count from the container's shape alone, with no reference
 * to how many rows it then takes to hold [count] items in it - on a very wide landscape board it
 * settles on many columns and few rows, and the cell is then capped at `availableW / cols`, which
 * is not the same thing as the largest cell that would actually fit. Measured on the Thor's
 * ~1850 x 480dp board area, 69 badges got sqrt'd into 17 columns x 5 rows and a ~96dp offer, when
 * an 18 x 4 arrangement at ~103dp both fits and uses the height far better - the sqrt heuristic
 * never even considers that shape because it derives columns before it knows how many rows they
 * would leave over.
 *
 * So this searches directly instead: for a candidate cell size `c`, `cols = floor(availableW / c)`
 * and `rows = ceil(count / cols)`; `c` fits when `rows * c <= availableH`. The fitting predicate is
 * monotonic in `c` (a smaller cell only ever needs the same or fewer columns to stop fitting, and
 * never more rows for a fixed column count - see the invariant tests), so a bisection between 0
 * and [maxCell] finds the largest fitting `c` in a fixed, small number of steps. [count] is at
 * most a few hundred and this runs once per layout, so there is no need for anything cleverer
 * than a plain search over the one free variable.
 */
internal fun badgeCellDp(count: Int, availableW: Float, availableH: Float, maxCell: Float): Float {
    val n = count.coerceAtLeast(1)
    if (availableW <= 0f || availableH <= 0f || maxCell <= 0f) return 0f
    fun fits(c: Float): Boolean {
        val colsF = availableW / c
        if (colsF < 1f) return false // not even one column of this size fits the width
        val cols = colsF.toInt()
        val rows = ceil(n.toDouble() / cols).toInt()
        return rows * c <= availableH
    }
    var lo = 0f // known to "fit" - a zero-size cell trivially does
    var hi = maxCell
    repeat(48) {
        val mid = (lo + hi) / 2f
        if (fits(mid)) lo = mid else hi = mid
    }
    return lo
}

/**
 * The badge art drawn inside a cell of size [cell], in dp: the cell less [GRID_GAP_DP].
 *
 * Deliberately takes no "is this badge ringed" argument. The old sizing reserved
 * `HARDCORE_RING_WIDTH * 2` out of every cell's offer before capping the tile, which shrank the
 * art on every badge - ringed or not - to make room for a ring most of them never drew. Here the
 * gap is reserved once, in [badgeCellDp]'s own cap (callers pass `BADGE_TILE_MAX_DP + GRID_GAP_DP`
 * as the search's ceiling), and the art is simply what is left after it - the same computation
 * regardless of [AchievementsBoardBadge.hardcore], so a hardcore unlock can never nudge the grid
 * relative to its neighbours.
 */
internal fun badgeArtDp(cell: Float): Float = (cell - GRID_GAP_DP).coerceAtLeast(0f)

/**
 * The size of the box the hardcore ring's border is drawn on, in dp, given an art size of [art].
 *
 * Strictly bigger than [art] by twice [HARDCORE_RING_WIDTH_DP] - the ring sits centred on the same
 * point as the art but outside it, so its stroke is never painted over the badge's own pixels (RA
 * badge art carries its own decorative frame right at the edge, which a border on the art's own
 * box would paint over). Centred within a cell that reserves a whole [GRID_GAP_DP] around the art,
 * this box still leaves [HARDCORE_RING_WIDTH_DP] of dark space between its outer edge and the
 * cell's - and so, doubled, between one badge's ring and its neighbour's. That breathing room is
 * the fix for the actual complaint: on this user's games nearly every earned badge is hardcore, so
 * without it the rings sat edge to edge and read as one continuous gold mesh rather than as
 * individual frames.
 */
internal fun badgeRingBoxDp(art: Float): Float = art + HARDCORE_RING_WIDTH_DP * 2f

/**
 * Fullscreen achievements board: game image, earned counts, progress bar, and the FULL badge
 * set laid out into the remaining space (grid aspect follows the screen, nothing scrolls) - so
 * the share-as-PNG always contains every icon. Tiles fill the space the grid offers them, capped
 * at [BADGE_TILE_MAX_DP] (see [badgeTileDp]).
 *
 * Used by the vault-session achievement drawers' grid view (X). Tap anywhere, B, or any key
 * dismisses. [backgroundArt] is drawn blurred + dimmed behind the board.
 */
@Composable
fun AchievementsBoardView(
    data: AchievementsBoardData,
    onDismiss: () -> Unit,
    backgroundArt: Any? = null,
) {
    // The safe area in PIXELS, read OUTSIDE the dialog - from the activity's window, which
    // reliably has it (the dialog's own resolves safeDrawing to zero). Handed to the content as
    // raw screen-space numbers, because the content stopped trusting where its window is at all:
    // it measures its own position on screen after layout and pads by exactly the difference
    // between that and this safe area. See the geometry note in AchievementsBoardContent.
    val density = androidx.compose.ui.platform.LocalDensity.current
    val layoutDir = androidx.compose.ui.platform.LocalLayoutDirection.current
    val safe = WindowInsets.safeDrawing
    val safePx = intArrayOf(
        safe.getLeft(density, layoutDir),
        safe.getTop(density),
        safe.getRight(density, layoutDir),
        safe.getBottom(density),
    )
    // A window of its own. Every caller opens this from inside a ModalBottomSheet, so the board
    // was laid out within the sheet rather than over the screen: its header sat wherever the
    // sheet's top edge was (about half way down) and the badge grid was fitted into
    // what the sheet left, which is also what the share ended up capturing. The board is meant to
    // be the whole screen - its own doc says so - and a full-width dialog is what makes that true.
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            // Edge to edge like the activity behind it, so the window is the SCREEN and not a
            // panel floated over it; the content already keeps itself inside safeDrawing.
            decorFitsSystemWindows = false,
            // The board dismisses on any tap or key of its own; leaving these on would take the
            // first press before its own handlers ever saw it.
            dismissOnClickOutside = false,
            dismissOnBackPress = false,
        ),
    ) {
        // usePlatformDefaultWidth frees the WIDTH; a dialog window's height still wraps, and the
        // platform measures the content against a larger max than the window it finally grants -
        // so the grid planned rows for room that was never there and the last of them fell off
        // the window (owner report, with the shared PNG showing the clipped window itself; in
        // landscape the dialog's default horizontal margin clipped the sides the same way).
        // MATCH_PARENT on the window is the fix for both axes: the window IS the screen, the
        // BoxWithConstraints inside measures against reality, and the share's PixelCopy captures
        // the whole picture.
        val dialogView = LocalView.current
        SideEffect {
            (dialogView.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window?.let { w ->
                w.setLayout(
                    android.view.WindowManager.LayoutParams.MATCH_PARENT,
                    android.view.WindowManager.LayoutParams.MATCH_PARENT,
                )
                // The other half of "the window IS the screen" (owner report, round four: the
                // whole board arrived shifted DOWN in portrait): a dialog window defaults to
                // being laid out BELOW the display cutout, so MATCH_PARENT alone still started
                // the window under the camera - and the activity-measured top inset then padded
                // the content a second time, pushing the grid's tail off the bottom. Letting the
                // window into the cutout puts its origin at the screen's true top; the captured
                // safe-area padding becomes the one and only clearance.
                androidx.core.view.WindowCompat.setDecorFitsSystemWindows(w, false)
                if (android.os.Build.VERSION.SDK_INT >= 30) {
                    w.attributes = w.attributes.apply {
                        layoutInDisplayCutoutMode =
                            android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                    }
                } else if (android.os.Build.VERSION.SDK_INT >= 28) {
                    w.attributes = w.attributes.apply {
                        layoutInDisplayCutoutMode =
                            android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    }
                }
            }
        }
        AchievementsBoardContent(data, onDismiss, backgroundArt, safePx)
    }
}

@Composable
private fun AchievementsBoardContent(
    data: AchievementsBoardData,
    onDismiss: () -> Unit,
    backgroundArt: Any?,
    /** The activity's safeDrawing insets in px: left, top, right, bottom. See the geometry note. */
    safePx: IntArray,
) {
    val gray = remember { ColorMatrix().apply { setToSaturation(0f) } }
    val imageLoader = com.twinlore.app.ui.LocalImageLoader.current
    val accent = com.twinlore.app.ui.LocalCachedFocusColor.current?.value
        ?: MaterialTheme.colorScheme.primary
    // Earned first, like the RA site's game page.
    val sorted = remember(data.badges) {
        data.badges.sortedByDescending { it.unlocked }.filter { !it.url.isNullOrBlank() }
    }

    // --- Share-as-PNG: PixelCopy this board's own window, write to the FileProvider's shares/
    // cache dir, hand off via the system share sheet. The share button itself is alpha-hidden
    // while the frame is captured so it isn't in the PNG.
    //
    // The board's window, emphatically not the activity's: the board is a Dialog now (see
    // AchievementsBoardView), and a dialog draws into a window of its own - copying the activity's
    // returned the screen behind the board rather than the board (the share did not
    // contain the grid). The activity's window is kept as the fallback for a caller that ever
    // hosts this content without a dialog around it.
    val context = LocalContext.current
    val view = LocalView.current
    val shareScope = rememberCoroutineScope()
    var capturing by remember { mutableStateOf(false) }
    fun shareBoard() {
        val window = (view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
            ?: context.findActivity()?.window
            ?: return
        shareScope.launch {
            capturing = true
            delay(64) // let the button-less frame draw before copying the window
            val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            try {
                PixelCopy.request(window, bmp, { result ->
                    capturing = false
                    if (result == PixelCopy.SUCCESS) {
                        shareScope.launch {
                            val uri = withContext(Dispatchers.IO) {
                                val dir = File(context.cacheDir, "shares").apply { mkdirs() }
                                val f = File(dir, "achievements.png")
                                f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
                            }
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "image/png"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(send, "Share achievements"))
                        }
                    }
                }, Handler(Looper.getMainLooper()))
            } catch (e: Exception) {
                capturing = false
                android.util.Log.w("AchievementsBoard", "share capture failed", e)
            }
        }
    }

    // Any key dismisses (same as the previous wallpaper grid), so the board never traps
    // controller focus; the share button is a touch target.
    val keyFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { try { keyFocus.requestFocus() } catch (_: Exception) {} }
    BackHandler(onBack = onDismiss)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(keyFocus)
            .focusable()
            .onKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown) onDismiss()
                e.type == KeyEventType.KeyDown || e.type == KeyEventType.KeyUp
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
    ) {
        // The game art, blurred and dimmed, as the board's backdrop.
        if (backgroundArt != null) {
            AsyncImage(
                model = backgroundArt,
                imageLoader = imageLoader,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(30.dp),
            )
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f)))
        }
        // if/else, NOT an early return: on this Compose runtime an early return from a
        // composable lambda leaves the group stack unbalanced when the branch flips
        // (spinner → content once the cache paints), crashing recomposition.
        if (data.total == 0) {
            CircularProgressIndicator(Modifier.align(Alignment.Center), color = accent)
        } else {
            // MEASURED clearance, not assumed (owner reports, rounds two through four: every
            // assumption about where a dialog window sits was wrong somewhere - the platform
            // granted less height than it measured against, its insets resolved to zero, and on
            // this device the window still originates below the cutout in portrait whatever its
            // cutout mode says). So the content stops trusting the window: after layout it reads
            // its own on-screen position, compares each edge against the ACTIVITY's safe area
            // (safePx, captured outside the dialog), and pads by exactly the shortfall. A window
            // wherever the OEM puts it gets exactly the clearance that remains owed - including
            // zero, when the window's own offset already provides it. The grid's
            // BoxWithConstraints sits inside this padding, so its math inherits the honest room.
            val boardView = LocalView.current
            var clearance by remember { mutableStateOf(intArrayOf(0, 0, 0, 0)) }
            Column(
                Modifier
                    .fillMaxSize()
                    .onGloballyPositioned {
                        val loc = IntArray(2)
                        boardView.getLocationOnScreen(loc)
                        val screen = boardScreenBounds(boardView)
                        val owed = intArrayOf(
                            (safePx[0] - loc[0]).coerceAtLeast(0),
                            (safePx[1] - loc[1]).coerceAtLeast(0),
                            (loc[0] + boardView.width - (screen.first - safePx[2])).coerceAtLeast(0),
                            (loc[1] + boardView.height - (screen.second - safePx[3])).coerceAtLeast(0),
                        )
                        if (!owed.contentEquals(clearance)) clearance = owed
                    }
                    .padding(
                        start = with(LocalDensity.current) { clearance[0].toDp() },
                        top = with(LocalDensity.current) { clearance[1].toDp() },
                        end = with(LocalDensity.current) { clearance[2].toDp() },
                        bottom = with(LocalDensity.current) { clearance[3].toDp() },
                    )
                    .padding(horizontal = 16.dp, vertical = 20.dp),
            ) {
                // The header leads with the game and gives each fact a line of its own. It used to
                // pack them onto three tight lines with mid-line separators, with the percentage
                // out at the right edge away from the bar it describes.
                //
                // Only the title shares the icon's row; the fact lines used to live in a column
                // squeezed next to a 64dp icon and two icon buttons, which on a 360dp phone left
                // 118dp for the title and facts combined - "Final Fantasy V" truncated to
                // "Final F..." and one fact wrapped across four lines. Hoisting the facts below
                // the row, spanning the full content width, is what actually fixes that; a smaller
                // icon or font would only have bought a few more characters.
                val lines = listOfNotNull(
                    boardAchievementsLine(data.earned, data.total),
                    data.pointsLine,
                    boardPlayedLine(
                        data.playtimeMinutes,
                        data.highestAwardKind,
                        spanMillis = if (data.firstUnlockMillis != null && data.lastPlayedMillis != null) {
                            data.lastPlayedMillis - data.firstUnlockMillis
                        } else null,
                    ),
                    boardLastPlayedLine(data.lastPlayedMillis),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (data.gameImage != null) {
                        // At the image's own shape. A wide one (Steam's banner) is drawn
                        // shorter, so it stays about 100dp wide and leaves the title room.
                        val wide = data.gameImageAspect > 1f
                        AsyncImage(
                            model = data.gameImage,
                            imageLoader = imageLoader,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .height(if (wide) 48.dp else 56.dp)
                                .aspectRatio(data.gameImageAspect),
                        )
                        Spacer(Modifier.width(12.dp))
                    }
                    // The title takes the rest of the row and wraps rather than truncating: on
                    // a 360dp phone a one-line cap turned "Final Fantasy V" into "Final F...",
                    // which is not a name anybody can read. Three lines, because a wide banner
                    // leaves a long name like "Knuckles the Echidna in Sonic the Hedgehog 2"
                    // less width than a square icon does.
                    Text(
                        data.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = accent,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = { shareBoard() },
                        // Alpha (not removal) so the layout doesn't shift in the captured frame,
                        // and so the button doesn't land in the shared PNG.
                        modifier = Modifier.alpha(if (capturing) 0f else 1f),
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = "Share as image", tint = Color.White)
                    }
                }
                Spacer(Modifier.height(8.dp))
                // Full width, under the icon rather than beside it. Squeezed into the column left
                // over next to a 64dp icon and two icon buttons, "Played 2 hours, 37 minutes over
                // 23 hours" wrapped across four lines on a 360dp screen.
                lines.forEach { line ->
                    Text(
                        boldNumerals(line),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                }
                Spacer(Modifier.height(12.dp))
                // Progress: dual bar (silver = softcore, gold overlay = hardcore) or accent, with
                // the award circle after it as a sibling rather than a knob riding the fill (user
                // request) - the award already names the run's outcome, so a second mark tracking
                // its position on the bar was redundant with it. The circle is skipped entirely
                // (not just hidden) when there is no award, so its Spacer goes with it and the bar
                // takes the full row rather than stopping short of a gap nothing sits in.
                val fraction = (data.earned / data.total.toFloat()).coerceIn(0f, 1f)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .weight(1f)
                            .height(BAR_HEIGHT)
                            .clip(RoundedCornerShape(BAR_HEIGHT / 2))
                            .background(Color.White.copy(alpha = 0.15f)),
                    ) {
                        if (data.dualBar) {
                            Box(
                                Modifier
                                    .fillMaxWidth(fraction)
                                    .fillMaxHeight()
                                    .background(achievementBarBrush(mastered = false)),
                            )
                            if (data.hardcoreEarned > 0) {
                                Box(
                                    Modifier
                                        .fillMaxWidth(
                                            (data.hardcoreEarned / data.total.toFloat()).coerceIn(0f, 1f),
                                        )
                                        .fillMaxHeight()
                                        .background(achievementBarBrush(mastered = true)),
                                )
                            }
                        } else {
                            Box(
                                Modifier
                                    .fillMaxWidth(fraction)
                                    .fillMaxHeight()
                                    .background(accentBarBrush(accent)),
                            )
                        }
                    }
                    if (data.highestAwardKind != null) {
                        Spacer(Modifier.width(8.dp))
                        HighestAwardIcon(data.highestAwardKind)
                    }
                }
                Text(
                    "${data.earned * 100 / data.total}%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    // The board's own ink, not the accent (owner request, matching the journey
                    // header's same move): the percentage is a fact like the lines above it, and
                    // it was the one fact wearing a colour. The board always draws on its black
                    // scrim whatever the theme, so its ink is the fact lines' white.
                    color = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.padding(top = 4.dp),
                )
                Spacer(Modifier.height(16.dp))
                // Lay out ALL badges: grid shape follows the screen, nothing scrolls, so the
                // shared PNG always contains the complete set. Cell size is the largest that fits
                // (badgeCellDp), capped at BADGE_TILE_MAX_DP worth of art plus GRID_GAP_DP.
                // Top-aligned, not centred: the header is the top of the board and the badges
                // hang directly under it. Centred, a set that did not fill the height floated in
                // the middle of the screen with the header stranded above it.
                BoxWithConstraints(
                    Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    val n = sorted.size
                    val cell = badgeCellDp(
                        count = n,
                        availableW = maxWidth.value,
                        availableH = maxHeight.value,
                        maxCell = BADGE_TILE_MAX_DP + GRID_GAP_DP,
                    )
                    val cols = if (cell > 0f) {
                        floor(maxWidth.value / cell).toInt().coerceAtLeast(1)
                    } else {
                        1
                    }
                    // The art is the cell less GRID_GAP_DP - see badgeArtDp: that subtraction does
                    // not know or care whether a given badge is ringed, so a ringed and an
                    // unringed badge always get the identical art size and the identical cell
                    // Modifier.size below. The ring itself is drawn OUTSIDE the art on a box sized
                    // by badgeRingBoxDp, strictly bigger than the art so its stroke never paints
                    // over RA's own badge frame at the art's edge, and small enough to stay inside
                    // the cell's own gap so it never reaches a neighbour's art or ring.
                    val art = badgeArtDp(cell).dp
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        sorted.chunked(cols).forEach { row ->
                            Row {
                                row.forEach { b ->
                                    // Only an earned achievement can be ringed - a locked one was
                                    // never taken in any mode, hardcore included.
                                    val ringed = b.unlocked && b.hardcore
                                    // The outer cell (not just RingedBadge's own ringBox) is what
                                    // reserves GRID_GAP_DP's breathing room between neighbouring
                                    // tiles - see badgeRingBoxDp and GRID_GAP_DP's own docs for why
                                    // that gap exists at all.
                                    Box(
                                        modifier = Modifier.size(cell.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        RingedBadge(art = art, ringed = ringed) {
                                            AsyncImage(
                                                model = b.url,
                                                imageLoader = imageLoader,
                                                contentDescription = when {
                                                    ringed -> "Unlocked in hardcore"
                                                    b.unlocked -> "Unlocked"
                                                    else -> "Locked"
                                                },
                                                contentScale = ContentScale.Crop,
                                                // Smooth (default) filtering, deliberately.
                                                // Nearest-neighbour was tried so enlarging RA's
                                                // 64x64 badge art wouldn't blur it, but at this
                                                // tile size it read as too blocky. RA's own site
                                                // upscales the same art smoothly, and the user
                                                // chose to match that over crisp pixels.
                                                colorFilter = if (!b.unlocked && b.grayscaleWhenLocked) {
                                                    ColorFilter.colorMatrix(gray)
                                                } else null,
                                                modifier = Modifier.size(art),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private val BAR_HEIGHT = 8.dp

/** [HARDCORE_RING_WIDTH_DP] as a Dp, for the border [Modifier] that actually draws the ring. */
private val HARDCORE_RING_WIDTH = HARDCORE_RING_WIDTH_DP.dp

/**
 * [content] (a badge's art, sized to [art]), optionally ringed in RetroAchievements' own hardcore
 * yellow ([HARDCORE_RING]).
 *
 * Shared by this board's grid (below) and the achievements tab's scrolling list
 * (`RowAchievementItem` in `RetroAchievementDrawer.kt`), so "how a hardcore badge gets ringed" is
 * one rule with one implementation rather than two that can quietly drift apart.
 *
 * Always sized to [badgeRingBoxDp] - [art] plus twice [HARDCORE_RING_WIDTH_DP] - whether or not
 * [ringed] is true, so a ringed and an unringed badge take up identical space in whatever layout
 * wraps this. The ring itself is a box strictly bigger than [art], centred on the same point but
 * outside it, so its stroke is never painted over [content]'s own pixels; RA badge art carries its
 * own decorative frame right at the edge, which a border drawn on the art's own box would cover.
 */
@Composable
fun RingedBadge(
    art: Dp,
    ringed: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val ringBox = badgeRingBoxDp(art.value).dp
    Box(modifier.size(ringBox), contentAlignment = Alignment.Center) {
        if (ringed) {
            Box(Modifier.size(ringBox).border(HARDCORE_RING_WIDTH, HARDCORE_RING))
        }
        content()
    }
}
