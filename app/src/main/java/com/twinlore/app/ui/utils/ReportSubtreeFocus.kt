package com.twinlore.app.ui.utils

import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged

/**
 * Reports whether focus is anywhere inside this node's subtree, not only on the
 * node itself.
 *
 * A control built as an outer `focusable` around an `IconButton` has two focus
 * targets, and a Confirm press moves focus from the outer one to the button's
 * inner `clickable`. Watching `isFocused` reads that move as the control losing
 * focus; `hasFocus` does not. The library top bar reports through this so the
 * grid's auto-focus effect never mistakes an in-button move for the user leaving
 * the bar and drags focus back to a tile.
 */
fun Modifier.reportSubtreeFocus(onChanged: (Boolean) -> Unit): Modifier =
    onFocusChanged { onChanged(it.hasFocus) }
