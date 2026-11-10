package com.twinlore.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.twinlore.app.data.AppIcon
import com.twinlore.app.data.HapticFeedbackIntensity
import com.twinlore.app.ui.LocalCachedFocusColor
import com.twinlore.app.ui.fold.activateOnConfirmKey
import com.twinlore.app.ui.license.SupportDevDialog
import com.twinlore.app.ui.license.ThemePreviewHost
import com.twinlore.app.ui.utils.gamepadFocusable
import com.twinlore.app.ui.utils.performHapticFeedback

/**
 * The Game Twin emblem in the post-refresh showcase's top bar, where the
 * pinned-apps pin used to be. Pinned apps are a full grid one screen away in
 * the library; the showcase's one piece of chrome is better spent asking for
 * support, which nothing else does while the showcase is covering the library.
 *
 * Split stateless/stateful the way [com.twinlore.app.ui.fold.PinTopBarButton]
 * is, and for the same reason: the half that draws can be tested without Hilt.
 *
 * Three deliberate differences from the library top bar's emblem
 * ([LibraryTopMenuBar]'s `GlowingEmblem`):
 *  - No Noir light-theme override. That exists because the library bar sits on
 *    theme chrome; the showcase always paints on black under a blurred
 *    backdrop, so Noir's light mark reads correctly as its own artwork.
 *  - No accent glow halo. The library's tracks a moving grid selection. Here
 *    there is one vault, and its cover art below already pulses.
 *  - A tinted mark takes [accentColor], where the library's takes its palette's
 *    foreground. Same reason as the first: on the showcase's black backdrop the
 *    palette foreground is whatever the theme's text colour happens to be,
 *    while the accent is the colour this whole overlay is already built out of,
 *    including the cover art's pulse and this button's own focus ring.
 */
@Composable
fun ShowcaseEmblemButtonContent(
    icon: AppIcon,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val focusColor = LocalCachedFocusColor.current?.value ?: accentColor
    Box(
        modifier = modifier
            .testTag("showcase_emblem_button")
            .clip(RoundedCornerShape(10.dp))
            .then(
                if (isFocused) {
                    Modifier
                        .background(focusColor.copy(alpha = 0.16f), RoundedCornerShape(10.dp))
                        .border(2.dp, focusColor, RoundedCornerShape(10.dp))
                } else {
                    Modifier
                }
            )
            .gamepadFocusable()
            .activateOnConfirmKey(onClick)
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .padding(4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(icon.emblemRes),
            contentDescription = "Game Twin",
            contentScale = ContentScale.Fit,
            colorFilter = if (icon.emblemTinted) ColorFilter.tint(accentColor) else null,
            modifier = Modifier.height(SHOWCASE_EMBLEM_HEIGHT),
        )
    }
}

/**
 * The mark's height: the library bar's emblem is 38dp, and this one was 28dp,
 * which beside the showcase's full-size art and achievements read as small.
 */
internal val SHOWCASE_EMBLEM_HEIGHT = 38.dp

/**
 * The stateful half. Reads the selected icon itself rather than taking it as a
 * parameter, following [com.twinlore.app.ui.fold.PinTopBarButton], so
 * `VaultShowcaseOverlay` does not grow a parameter back after losing five.
 *
 * [onDialogVisibilityChange] exists because the overlay's root key handler
 * dismisses the showcase on any non-confirm key, and has to stand down while a
 * dialog is up. "A dialog" means either of the two this button can raise, not
 * just the first: [ThemePreviewHost] renders a full dialog of its own, and it
 * outlives the support dialog that opened it, since tapping an icon in there
 * closes nothing. Reporting only the support dialog let the flag go false with
 * a preview still on screen, and the next key press dismissed the showcase out
 * from under it.
 */
@Composable
fun ShowcaseEmblemButton(
    accentColor: Color,
    hapticFeedbackIntensity: HapticFeedbackIntensity,
    onDialogVisibilityChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val selectedIcon by settingsViewModel.appIcon.collectAsState()
    val context = LocalContext.current
    val view = LocalView.current

    var showSupportDialog by remember { mutableStateOf(false) }
    var previewIcon by remember { mutableStateOf<AppIcon?>(null) }

    // Either dialog counts. The preview is a dialog in its own right and can be
    // the only one left standing, so keying this off showSupportDialog alone
    // handed the showcase's key handler back while a dialog was still up.
    val anyDialogVisible = showSupportDialog || previewIcon != null
    LaunchedEffect(anyDialogVisible) { onDialogVisibilityChange(anyDialogVisible) }

    if (showSupportDialog) {
        // Tap shows the dialog first. Only its own "Support the App" button
        // leaves for the store: a stray tap must never yank the user out.
        SupportDevDialog(
            onSupport = {
                showSupportDialog = false
                openSupportDevPage(context)
            },
            onDismiss = { showSupportDialog = false },
            onIconTap = { previewIcon = it },
        )
    }
    ThemePreviewHost(
        icon = previewIcon,
        settingsViewModel = settingsViewModel,
        onDismiss = { previewIcon = null },
    )

    ShowcaseEmblemButtonContent(
        icon = selectedIcon,
        accentColor = accentColor,
        modifier = modifier,
        onClick = {
            view.performHapticFeedback(hapticFeedbackIntensity)
            showSupportDialog = true
        },
    )
}
