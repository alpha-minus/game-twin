package com.twinlore.app.ui.fold.guide

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.twinlore.app.data.StatusBarMode
import com.twinlore.app.di.NetworkConnectivityEntryPoint
import com.twinlore.app.data.metrics.SystemMetricsSnapshot
import com.twinlore.app.ui.components.BatteryReading
import com.twinlore.app.ui.components.PerfMeters
import com.twinlore.app.ui.components.StatusReadouts
import com.twinlore.app.data.NetworkStatus
import com.twinlore.app.ui.fold.PinTopBarButtonContent
import com.twinlore.app.ui.navigation.longPressHandler
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The Return mark, which may not be the smallest thing in this bar.
 *
 * It was 18.dp, set beside a Performance cluster whose five meters and clock
 * are the loudest things on screen, and it read as an afterthought at the one
 * end of the bar somebody actually aims at. 22.dp, the size of the app's own
 * fold button, still read as small there; the cluster around it is simply
 * louder than the same glyph is in the app's much wider bar. It is a normal
 * icon now, and the connection mark gives up the width for it.
 *
 * The floor is the mark the panel's own bottom bar uses, because these are the
 * same kind of control.
 */
internal val FOLD_RETURN_ICON = 24.dp

/** The status cluster's tap, for a screen reader. The app's own label. */
const val FOLD_GUIDE_STATUS_TAP = "Toggle performance meters"

/**
 * Whether the bar draws its readouts at all. The app's `LibraryStatusIndicators`
 * renders nothing in Off and Hidden, and the panel follows: the bar itself
 * stays, since it carries the pin and the way back to the app, but the clock
 * and the meters are the app's status bar and go where it goes.
 */
fun statusReadoutsVisible(mode: StatusBarMode): Boolean =
    mode == StatusBarMode.STANDARD || mode == StatusBarMode.PERFORMANCE

/**
 * Whether the connection mark belongs in the cluster: wherever the readouts
 * are, Performance included.
 *
 * It was dropped from Performance once, when the meters were measured first
 * and the mark, last in the row, was the thing the clip crushed. The meters
 * are weighted now and fit themselves into what the readouts beside them
 * leave, so the mark costs the meters a little width and never the mark its
 * own, or Return its size. And a lost connection is worth knowing in any mode.
 */
fun connectionReadoutVisible(mode: StatusBarMode): Boolean = statusReadoutsVisible(mode)

/**
 * Whether the bar names what is being read.
 *
 * Not in Performance. The five meters take most of this bar, and what was left
 * of the title there was a few characters and an ellipsis: the name of a file,
 * cut short enough to name nothing. The width is better spent on the readings
 * that mode exists for, and the panel's own reader chrome names the guide
 * underneath anyway.
 */
fun panelTitleVisible(mode: StatusBarMode): Boolean =
    mode != StatusBarMode.PERFORMANCE

/**
 * Return, at the far right of the bar.
 *
 * The corner the app keeps for the button that opens this mode, wearing the
 * same glyph, so one mark in one place turns the mode on from the app and off
 * from the panel. Folding is not here and does not need to be: the handle is
 * always on screen and always folds, which is the one thing this bar could not
 * promise, because it hides itself to give the page room.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReturnTopBarButton(
    onClick: () -> Unit,
    tint: Color,
    iconSize: Dp,
    modifier: Modifier = Modifier,
    /** Held: the app comes back on the other screen. Null leaves the button
     *  with the one action, which is what a device with one screen has. */
    onLongClick: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .size(iconSize + 16.dp)
            .testTag("fold_return_button")
            // The order [FoldButtonContent] documents, for the same reason and
            // with the same consequence if it is reversed: combinedClickable
            // installs a key handler that answers the confirm set with a plain
            // onClick, and whichever of the two is installed first wins them.
            // With longPressHandler first the keys can be held; swap the lines
            // and every one of them returns instead of swapping.
            .longPressHandler(
                onLongPress = { onLongClick?.invoke() },
                onPress = onClick,
            )
            .combinedClickable(
                role = Role.Button,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.PictureInPictureAlt,
            contentDescription = FOLD_GUIDE_RETURN_LABEL,
            tint = tint,
            modifier = Modifier.size(iconSize),
        )
    }
}

/**
 * The panel's own top bar: what you are reading on the left; the pin, the
 * status cluster and Return on the right.
 *
 * The readouts the vault session's bar carries, rebuilt rather than hosted.
 * `VaultSessionTopBar` is wired to `MainViewModel` and to the vault's whole tab
 * machinery, none of which exists in this window.
 *
 * Return is here and Fold is not, which is the reverse of how this bar
 * started. Return earns the corner: it is the exact opposite of the app's own
 * fold button and now wears the same glyph in the same place. Fold leaves the
 * bar entirely because a bar that hides cannot hold the mode's most frequent
 * action; the handle holds it instead, and the handle never hides.
 *
 * The readouts follow [statusBarMode] exactly as the app's cluster does, and
 * a tap on them trades Standard for Performance the same way.
 *
 * [metrics] non-null draws the CPU/GPU/RAM/PWR/TMP cluster, the same one the
 * vault session's bar shows in Performance mode and from the same snapshot.
 * Null when the mode is off, which is also when nothing is being polled.
 */
@Composable
fun FoldGuideTopBar(
    title: String,
    pinned: Boolean,
    onTogglePin: () -> Unit,
    statusBarMode: StatusBarMode,
    onTapStatus: () -> Unit,
    onOpenApp: () -> Unit,
    metrics: SystemMetricsSnapshot?,
    modifier: Modifier = Modifier,
    /** Return, held: the app comes back on the other screen. Null on a device
     *  with one, where the bottom bar's Swap is absent for the same reason. */
    onOpenAppOnOtherScreen: (() -> Unit)? = null,
    /**
     * The app's Pixel font is on. The meters shrink it hard, as the app's own
     * bar does: at the meters' usual size Press Start 2P is so wide that every
     * slot measured for its widest reading became a gap.
     */
    pixelFont: Boolean = false,
) {
    val context = LocalContext.current
    // The app's own bar fill, the same one the bottom bar wears and for the
    // same reason: the panel's chrome should be the theme's, not a lighter
    // cousin of it. See FoldGuideBottomBar.
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = FOLD_BAR_FILL_ALPHA))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val named = panelTitleVisible(statusBarMode)
        if (named) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        // No spacer where the name was. There was one, to hold the readings
        // against the right edge, and what it actually did was push the pin
        // off the left edge and leave dead air in front of it. The cluster
        // below takes that job instead: it fills the middle and packs what it
        // draws to the end, so both ends of the bar keep their control.
        // Not the app's LibraryStatusIndicators, which draws exactly this and
        // more. That one reaches for hiltViewModel() and opens an AlertDialog
        // to stop a recording, and this window has neither an activity to give
        // it nor a token to put a dialog on. The meters underneath are shared;
        // the chrome around them is not.
        PinTopBarButtonContent(
            pinned = pinned,
            onClick = onTogglePin,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            iconSize = 16.dp,
        )
        if (statusReadoutsVisible(statusBarMode)) {
            Row(
                modifier = Modifier
                    // Weighted only when there is no name competing for the
                    // same slack: two weighted children split the row between
                    // them and the name ends up starting a third of the way in.
                    .then(if (named) Modifier else Modifier.weight(1f))
                    .clickable(onClickLabel = FOLD_GUIDE_STATUS_TAP, onClick = onTapStatus),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                val battery = rememberBattery(context)
                metrics?.let {
                    // The meters yield: weighted, they are measured after the
                    // readouts beside them, which keep their size.
                    PerfMeters(
                        it,
                        modifier = Modifier.weight(1f, fill = false),
                        textColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        pixelFont = pixelFont,
                        pixelFontSize = FOLD_METER_PIXEL_SIZE,
                    )
                }
                // Connection, time, battery: the group every bar ends with, in
                // every mode, so a switch to Performance moves none of it.
                StatusReadouts(
                    network = rememberNetwork(context),
                    time = rememberClock(),
                    battery = battery.first.takeIf { p -> p >= 0 }
                        ?.let { p -> BatteryReading(p, battery.second) },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    pixelFont = pixelFont,
                    pixelFontSize = FOLD_METER_PIXEL_SIZE,
                )
            }
        }
        ReturnTopBarButton(
            onClick = onOpenApp,
            onLongClick = onOpenAppOnOtherScreen,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            iconSize = FOLD_RETURN_ICON,
        )
    }
}

/**
 * The meters' Pixel size here. The app bar's 6sp left the battery hard to read
 * over a game, and this bar has fewer readings competing for its width.
 */
private val FOLD_METER_PIXEL_SIZE = 8.sp

/** Ticks on the minute, which is all a clock in a reading panel owes anybody. */
@Composable
private fun rememberClock(): String {
    val format = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    var now by remember { mutableStateOf(format.format(Date())) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(20_000)
            now = format.format(Date())
        }
    }
    return now
}

/**
 * Battery level and charging state, read straight from the sticky broadcast.
 *
 * A poll rather than a receiver: the panel is short-lived and a receiver
 * registered from a window with no lifecycle to unregister on is a leak waiting
 * for a long fold.
 */
@Composable
private fun rememberBattery(context: Context): Pair<Int, Boolean> {
    var state by remember { mutableStateOf(readBattery(context)) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            state = readBattery(context)
        }
    }
    return state
}

/**
 * Whether anything is reachable, followed from the default network.
 *
 * A capability check rather than a reachability test: the panel is a reader
 * over local files and the only thing this readout answers is why a search or
 * an image might not load. Followed rather than polled, the same source as the
 * app's bars: see [com.twinlore.app.data.NetworkConnectivityManager.status]
 * for why a snapshot of activeNetwork kept reading offline after a wake.
 */
@Composable
private fun rememberNetwork(context: Context): NetworkStatus {
    val status by remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            NetworkConnectivityEntryPoint::class.java,
        ).networkConnectivityManager().status
    }.collectAsState()
    return status
}

private fun readBattery(context: Context): Pair<Int, Boolean> = runCatching {
    val intent: Intent? = context.applicationContext
        .registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
    val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
    val percent = if (level >= 0 && scale > 0) level * 100 / scale else 0
    val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
        status == BatteryManager.BATTERY_STATUS_FULL
    percent to charging
}.getOrDefault(0 to false)
