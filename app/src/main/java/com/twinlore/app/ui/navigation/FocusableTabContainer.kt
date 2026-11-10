package com.twinlore.app.ui.navigation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.zIndex

/**
 * Focus-isolated tab container. Renders content always (keeping WebViews, state, etc. alive)
 * but prevents focus and touch input when inactive.
 *
 * - `zIndex(2f)` on the active tab ensures it renders on top and receives touches
 * - `focusGroup()` creates a hard focus boundary so D-pad traversal stays within the tab
 * - `focusProperties { canFocus = false }` prevents any child from receiving D-pad focus
 *   when the tab is inactive, blocking focus from leaking across tabs
 *
 * Switching dissolves instead of snapping: the incoming tab fades in (220ms) on top while
 * the outgoing one stays fully opaque underneath (its fade-out is delayed past the fade-in)
 * — same top-layer dissolve as the nav host, so a tab whose content paints/loads a frame
 * late never flashes its bare background. No animation on first composition (the initial
 * alpha equals the target). zIndex 1 keeps a fading-out tab above the idle (alpha-0) tabs
 * so the dissolve reads cleanly.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun FocusableTabContainer(
    isActive: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val tabAlpha by animateFloatAsState(
        targetValue = if (isActive) 1f else 0f,
        animationSpec = if (isActive) tween(durationMillis = 220)
        else tween(durationMillis = 90, delayMillis = 220),
        label = "tabAlpha"
    )
    Box(
        modifier = modifier
            .fillMaxSize()
            // Active on top; a tab still fading out sits above the idle alpha-0 tabs.
            .zIndex(if (isActive) 2f else if (tabAlpha > 0f) 1f else 0f)
            .alpha(tabAlpha)
            .focusGroup()
            .focusProperties { canFocus = isActive }
    ) {
        content()
    }
}
