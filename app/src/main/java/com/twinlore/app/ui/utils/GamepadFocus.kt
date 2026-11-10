package com.twinlore.app.ui.utils

import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties

/**
 * Lets a clickable-based control ([androidx.compose.foundation.clickable], `Surface(onClick)`,
 * `IconButton`, `Button`) take controller focus while the device is in touch mode.
 *
 * Compose gates those controls' focusability on non-touch mode, but on this handheld the
 * controller can never flip the device out of touch mode: the D-pad is a HAT that reaches the
 * app as synthesized KeyEvents (dispatched straight into the view hierarchy, bypassing
 * ViewRootImpl's touch-mode exit), and gamepad face buttons are not navigation keys. Without
 * this override, focus anchors and D-pad traversal silently skip every such control.
 *
 * Place it anywhere before the clickable in the modifier chain (or in the modifier passed to
 * the Material component): the outermost focus properties win over the internal touch-mode gate.
 *
 * Pass [canFocus] = false for a control that is currently disabled: the same override that makes a
 * control reachable would otherwise park the D-pad on one that does nothing when pressed.
 */
fun Modifier.gamepadFocusable(canFocus: Boolean = true): Modifier =
    focusProperties { this.canFocus = canFocus }
