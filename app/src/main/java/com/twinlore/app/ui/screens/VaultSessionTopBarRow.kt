package com.twinlore.app.ui.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.twinlore.app.data.StatusBarMode

/**
 * The vault session top bar's row: the game name, the pin, the status cluster
 * and the way into Always on Top, in that order across the bar.
 *
 * Slots rather than the controls themselves, and a file of its own, for the
 * reason [vaultSessionTopBarChromeVisible] is a pure function: every control
 * this bar hosts reaches for `hiltViewModel()`, so the bar as a whole cannot be
 * put in front of a test, and the arrangement below is not something to get
 * right by eye on a device.
 *
 * What it gets right:
 *
 * A Row measures its unweighted children first, in order, handing each one
 * whatever the one before it left. The cluster sits between the pin and the
 * fold button, so in Performance — where it is five meters, a clock and a
 * connection mark, and wants more than a 640dp bar has — it took the entire
 * row, and the fold button after it was measured at zero. Not clipped, not
 * hidden by a rule: 0dp wide, at the far right edge, in a mode whose whole
 * point is to watch numbers while a game runs. The cluster's own last child
 * was crushed by the same clip, which is why the connection mark looked tiny
 * rather than absent.
 *
 * So in Performance the cluster is the flexible child instead. Weighted
 * children are measured last, with what is left, which inverts the priority:
 * the pin and the fold button take their size first and the cluster gets the
 * rest. A control that cannot be reached is worse than a reading that has to
 * end early.
 *
 * Only in Performance, and this is the part that is easy to get wrong. Making
 * the cluster flexible in both modes puts two weighted children in the row,
 * they split what is left between them, and the game name — which fills its
 * half — starts 166dp into a bar it used to start at the beginning of. So
 * Standard keeps today's arrangement exactly: the name flexible, the cluster
 * at its natural width, both of which fit.
 *
 * The cluster fills its share rather than shrinking to fit it, and right
 * aligns what it draws. That decides where the slack on a wide bar goes, and
 * all three answers are visible on the device: left of the pin (the pin stops
 * hugging the edge, which is the dead air the panel's bar was showing), after
 * the fold button (the way out leaves the corner), or between the pin and the
 * first meter. Only the last one leaves both ends where the eye expects them.
 */
@Composable
fun VaultSessionTopBarRow(
    statusBarMode: StatusBarMode,
    isLandscape: Boolean,
    title: @Composable (Modifier) -> Unit,
    pin: @Composable () -> Unit,
    divider: @Composable () -> Unit,
    cluster: @Composable (Modifier) -> Unit,
    fold: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val named = vaultSessionTitleVisible(statusBarMode, isLandscape)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Always offered, and weighted only when the name is drawn. In
        // Performance what is left in that slot is the doc switcher's chevron,
        // which is 24dp and the Guide tab's only way to change document: a
        // title that costs a feature to hide is too expensive. Unweighted, it
        // is measured with the buttons rather than against them.
        title(if (named) Modifier.weight(1f) else Modifier)
        pin()
        divider()
        // fill = false so the cluster asks for no more than it needs: on a bar
        // wide enough for all of it there is nothing to give up.
        cluster(if (named) Modifier else Modifier.weight(1f))
        fold()
    }
}
