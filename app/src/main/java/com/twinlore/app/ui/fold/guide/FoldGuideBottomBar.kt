package com.twinlore.app.ui.fold.guide

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.twinlore.app.ui.LocalCachedFocusColor

/**
 * The way back to the whole app. "Return" rather than "App": you are in the
 * app already, so the button names the trip, not the destination. It was
 * "Full" once, which named a window size, and "App" after that. Its icon is
 * the one the app's own bar uses to open this mode, so the pair reads as one
 * gesture and its reverse.
 */
const val FOLD_GUIDE_RETURN_LABEL = "Return"
/**
 * The way out of the game itself, as opposed to out of the panel.
 *
 * Return, in the top bar, keeps your place: it hands the app the guide you
 * were reading so you land back on it. This one is for being finished with
 * the game, so it names the destination rather than the trip, and the app it
 * opens is on the library rather than on the vault you just left.
 */
const val FOLD_GUIDE_LIBRARY_LABEL = "Library"
const val FOLD_GUIDE_SETTINGS_LABEL = "Settings"
const val FOLD_GUIDE_SWAP_LABEL = "Swap"

/**
 * The panel's content surfaces and the two actions that stay with them.
 *
 * Library is the one way out that lives here. Return moved to the top bar's
 * far right, the corner the app keeps for the button that opens this mode,
 * and Fold moved to the handle. The reason is this bar's own best feature: it hides so the page
 * can have the room. Anything that hides is the wrong home for the action
 * people reach for most, and folding to glance at the game is that action.
 *
 * A bottom bar built like the app's own bottom navigation, icon over label, so
 * the panel reads as part of Game Twin rather than as something bolted to the
 * side of it. Everything to do with the guide itself, the contents, the
 * chapters, the search, the text size, belongs to the reader's own chrome
 * above; this bar only decides where you go next.
 *
 * Collapsed means gone, not a strip: a tap on the page is what takes the chrome
 * away and a tap is what brings it back, the same gesture the reader's own
 * chrome answers to, so a permanent sliver of bar to press was one affordance
 * too many for a gesture people already have.
 */
@Composable
fun FoldGuideBottomBar(
    bottomInset: androidx.compose.ui.unit.Dp,
    surface: FoldPanelSurface,
    collapsed: Boolean,
    swapAvailable: Boolean,
    onSelectSurface: (FoldPanelSurface) -> Unit,
    onToggleBar: () -> Unit,
    onSwap: () -> Unit,
    onReturnToLibrary: () -> Unit = {},
    /**
     * How wide the pill measured, so things above it can line up with its
     * end rather than with the screen's. The bar is centred and only as wide
     * as its entries, so the screen edge is a long way to the right of it.
     */
    onBarWidth: (androidx.compose.ui.unit.Dp) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // The app's own bar fill, so the panel's chrome is the theme's rather than
    // a lighter cousin of it: surface at the app bars' alpha with the glass
    // border they wear (CompactBottomBar, VaultSessionTopBar). It was
    // surfaceVariant over background, which in this app's palettes is a 12%
    // foreground tint and read as a paler bar than the app's on every theme.
    val barFill = MaterialTheme.colorScheme.surface.copy(alpha = FOLD_BAR_FILL_ALPHA)
    val glassBorder = MaterialTheme.colorScheme.onSurface.copy(alpha = FOLD_BAR_BORDER_ALPHA)
    val focusColor = LocalCachedFocusColor.current?.value ?: MaterialTheme.colorScheme.primary

    if (collapsed) return

    // Rounded and only as wide as its entries rather than a full-width block.
    // A slab the width of the screen for a handful of icons reads as chrome
    // the panel could not get rid of, and this is chrome you can. A pill the
    // full width with its contents huddled in the middle reads as a bar that
    // failed to fill itself. Centred in a full-width box so the pill itself
    // is centred without being stretched.
    //
    // Resting on the bottom edge, not floating above it. It hovered a gesture
    // bar's height above the bottom for a while, and with the page running
    // underneath it since the surfaces stopped making room, the strip of page
    // showing through the gap below it made it one more thing on the page to
    // read past. So the pill's own colour runs to the edge and only its top
    // corners are rounded; the gesture inset is padding inside it, so the
    // labels stay clear of the gesture bar without the bar lifting off.
    //
    // The inset and the entries' own bottom padding overlap rather than
    // stack: both exist to keep the labels off the edge, and adding them left
    // an empty band under the labels on the Thor, where the inset is about
    // the padding's size, that read as the labels sitting too high in the bar.
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
    // The bar no longer scrolls, so it has to fit. Entries are their full
    // width where there is room and shrink together where there is not,
    // down to the platform's touch floor; a way out that sits off the end of
    // a scrolling bar is what the scroll was removed to prevent.
    // The surfaces, Settings among them, plus Swap where there is another
    // screen. Return used to be counted here; it lives in the top bar now.
    val entryCount = 1 + FoldPanelSurface.entries.size + (if (swapAvailable) 1 else 0)
    val dividerRoom = if (swapAvailable) DIVIDER_WIDTH else 0.dp
    val room = (maxWidth - ROW_PADDING - dividerRoom) / entryCount
    val entryWidth = if (room >= ENTRY_WIDTH) ENTRY_WIDTH else maxOf(room, MIN_ENTRY_WIDTH)
    val density = LocalDensity.current
    Row(
        modifier = Modifier
            .testTag(FOLD_BOTTOM_BAR_TAG)
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(barFill)
            .border(1.dp, glassBorder, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .onSizeChanged { onBarWidth(with(density) { it.width.toDp() }) }
            .padding(
                start = 4.dp,
                end = 4.dp,
                bottom = (bottomInset - ENTRY_VERTICAL_PADDING).coerceAtLeast(0.dp),
            ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Library first, ahead of everything you can look at while you stay.
        // It is not a surface and never draws as the selected entry: picking
        // it ends the mode rather than changing what the panel is showing.
        BarEntry(
            icon = foldLibraryIcon(),
            label = FOLD_GUIDE_LIBRARY_LABEL,
            onClick = onReturnToLibrary,
            width = entryWidth,
            focusColor = focusColor,
        )
        FoldPanelSurface.entries.forEach { entry ->
            BarEntry(
                icon = entry.icon(),
                label = entry.label,
                onClick = { onSelectSurface(entry) },
                width = entryWidth,
                focusColor = focusColor,
                selected = entry == surface,
            )
        }
        // The divider separates navigation from action: everything left of it
        // changes what you are looking at, everything right of it moves the
        // panel itself. Settings is on the left now: it is a surface like the
        // others, so picking it is the same kind of act as picking the guide.
        //
        // Library is the exception on the left, and sits first because of it:
        // it neither changes the surface nor moves the panel, it ends the
        // mode. It is grouped with the surfaces rather than with Swap because
        // what it answers is "where do I go next", which is this bar's job.
        //
        // Swap is the only thing left on the right, and it is absent rather
        // than disabled when there is only one display. A control that can
        // never do anything is worse than one that is not offered. Neither way
        // out is here: Return is in the top bar's corner, and Fold belongs to
        // the handle, which is on screen whether or not this bar is.
        //
        // Vertical arrows, not horizontal: the screens this moves between are
        // stacked, one above the other, so an up-and-down pair says where the
        // panel is going. The horizontal pair read as "left and right", which
        // is not an arrangement this device has.
        if (swapAvailable) {
            BarDivider()
            BarEntry(Icons.Default.SwapVert, FOLD_GUIDE_SWAP_LABEL, onSwap, width = entryWidth, focusColor = focusColor)
        }
    }
    }
}

/**
 * The Library entry's mark, named so a test can hold it to the app's own.
 *
 * The app's vault bottom bar puts Library first behind a back arrow
 * (`VaultSessionScreen.kt:1043`). The panel's bar is the same bar for the same
 * job, so it wears the same mark: this entry is the way back out of the game,
 * not a fifth thing to look at inside it.
 */
internal fun foldLibraryIcon(): ImageVector = Icons.Default.ArrowBack

private fun FoldPanelSurface.icon(): ImageVector = when (this) {
    FoldPanelSurface.GUIDE -> Icons.Default.MenuBook
    FoldPanelSurface.IMAGE -> Icons.Default.Image
    FoldPanelSurface.AWARDS -> Icons.Default.EmojiEvents
    FoldPanelSurface.SETTINGS -> Icons.Default.Settings
}

@Composable
private fun BarDivider() {
    Box(
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .width(1.dp)
            .height(24.dp)
            .background(MaterialTheme.colorScheme.outline),
    )
}

@Composable
private fun BarEntry(
    icon: ImageVector,
    label: String?,
    onClick: () -> Unit,
    width: androidx.compose.ui.unit.Dp = ENTRY_WIDTH,
    focusColor: Color = MaterialTheme.colorScheme.primary,
    contentDescription: String? = null,
    selected: Boolean = false,
) {
    // The app's own entry colours: the selected one in the focus colour on a
    // tinted disc, the rest in onSurface with the label a step quieter.
    val iconTint = if (selected) focusColor else MaterialTheme.colorScheme.onSurface
    val labelColor = if (selected) focusColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
    Column(
        modifier = Modifier
            .width(width)
            .clickable(onClick = onClick)
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                },
            )
            .padding(vertical = ENTRY_VERTICAL_PADDING),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(FOLD_BAR_ENTRY_DISC)
                .then(
                    if (selected) Modifier.background(focusColor.copy(alpha = 0.22f), CircleShape)
                    else Modifier
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(FOLD_BAR_ENTRY_ICON),
            )
        }
        if (label != null) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = labelColor)
        }
    }
}

/**
 * The mark on a bar entry. Named so the top bar can be held to it.
 *
 * The app's own bottom navigation draws an 18dp mark on a 32dp disc; this
 * one is a step larger on the same disc, because it sits in a window over a
 * game and the app's size read as small there. It went to 24dp on a 40dp
 * disc for a while, and the bar it made was a third taller than the app's,
 * which was too much bar for a panel this short.
 */
internal val FOLD_BAR_ENTRY_ICON = 20.dp

/** The disc behind a selected entry's mark: the app's own. */
private val FOLD_BAR_ENTRY_DISC = 32.dp

/** The app bars' fill: surface at this alpha (CompactBottomBar's barFillAlpha). */
internal const val FOLD_BAR_FILL_ALPHA = 0.95f

/** The app bars' glass border: onSurface at this alpha. */
internal const val FOLD_BAR_BORDER_ALPHA = 0.12f

/**
 * An entry's width where there is room; they shrink together where not.
 *
 * The app's own entries are about this wide, and it keeps the pill short of
 * the panel's sides: the handle is dragged along the edges, and a bar that
 * reached most of the way across left it nowhere to go along the bottom.
 */
private val ENTRY_WIDTH = 64.dp

/**
 * Above the mark and below the label; with them, the height of the target.
 * The app's own entries use 2dp; a little more here keeps the label off the
 * pill's bottom edge, which the app's bar pads separately.
 */
private val ENTRY_VERTICAL_PADDING = 4.dp

/** The platform's minimum touch target: the floor an entry may shrink to. */
private val MIN_ENTRY_WIDTH = 44.dp

/** The row's own horizontal padding, both sides. */
private val ROW_PADDING = 8.dp

/** The divider plus its padding, both sides. */
private val DIVIDER_WIDTH = 9.dp

/** The pill itself, so a test can hold its edges to the panel's. */
internal const val FOLD_BOTTOM_BAR_TAG = "fold_bottom_bar"
