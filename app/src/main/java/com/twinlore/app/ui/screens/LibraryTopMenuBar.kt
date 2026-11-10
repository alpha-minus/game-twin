package com.twinlore.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import com.twinlore.app.R
import com.twinlore.app.data.AppFont
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.indication
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.twinlore.app.ui.utils.gamepadFocusable
import com.twinlore.app.ui.utils.reportSubtreeFocus
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import android.view.KeyEvent
import com.twinlore.app.data.FocusColor
import com.twinlore.app.ui.LocalThemePalette
import com.twinlore.app.data.HapticFeedbackIntensity
import com.twinlore.app.data.LedTurnOffTime
import com.twinlore.app.ui.navigation.longPressHandler
import com.twinlore.app.ui.screens.SettingsViewModel
import com.twinlore.app.thor.led.ThorLedManager
import com.twinlore.app.ui.utils.FeedbackEvent
import com.twinlore.app.ui.utils.performHapticFeedback
import com.twinlore.app.ui.utils.runWithHapticAndLed

/**
 * Top menu bar for Library screen with overlay buttons (not in focus chain)
 * Buttons are overlay - not in default focus chain, must be explicitly focused
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LibraryTopMenuBar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onCreateVault: () -> Unit,
    onSettings: () -> Unit,
    onJourney: () -> Unit,
    onRefreshBoxArt: () -> Unit,
    onSearchClick: () -> Unit, // Opens search modal
    onInputReceived: () -> Unit, // Callback to reset idle timer
    onTopBarFocusChanged: ((Boolean) -> Unit)? = null, // Callback to track top bar focus state
    isScraping: Boolean = false,
    firstButtonFocusRequester: androidx.compose.ui.focus.FocusRequester,
    addButtonFocusRequester: androidx.compose.ui.focus.FocusRequester = androidx.compose.ui.focus.FocusRequester(),
    accentColorOverride: androidx.compose.ui.graphics.Color? = null,
    leadingContent: (@Composable androidx.compose.foundation.layout.RowScope.() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // Get focus color and haptic/LED settings
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val focusColorState by settingsViewModel.focusColor.collectAsState(initial = FocusColor.CRIMSON)
    val focusColor = accentColorOverride ?: focusColorState.color
    val view = LocalView.current
    val context = LocalContext.current
    val hapticFeedbackIntensity by settingsViewModel.hapticFeedbackIntensity.collectAsState(initial = HapticFeedbackIntensity.MEDIUM)
    val ledEnabled by settingsViewModel.ledEnabled.collectAsState(initial = true)
    val ledBrightness by settingsViewModel.ledBrightness.collectAsState(initial = 127)
    val ledSaturationBoost by settingsViewModel.ledSaturationBoost.collectAsState(initial = 0.75f)
    val ledTurnOffTime by settingsViewModel.ledTurnOffTime.collectAsState(initial = LedTurnOffTime.SEC_15)

    // The Journey button is always on the bar: without an account or with the
    // achievements feature off, the journey screen's own Locked card explains what is missing and
    // links to Settings, which is a better doorway than an absent button nobody knows exists.

    val palette = LocalThemePalette.current
    val isDarkTheme = palette.isDark
    val unfocusedIconColor = palette.foreground.copy(alpha = 0.9f)
    val disabledIconColor = palette.foreground.copy(alpha = 0.4f)
    val iconButtonContainerColor = Color.Transparent

    // App emblem (compass mark), tinted to the theme foreground (the assets are
    // monochrome alpha masks). The Pixel font gets the blocky pixel-art compass; every
    // other font uses the standard compass. Placement follows the status row, which only
    // renders in landscape: portrait shows the emblem at the top-left where nothing else
    // sits; landscape centres it, since the clock/wifi/battery indicators take the left.
    val appFont by settingsViewModel.appFont.collectAsState(initial = AppFont.DEFAULT)
    // The library emblem follows the selected app icon (not the font). Free icons
    // (Default/Pixel) render a monochrome mask tinted to the theme; premium icons
    // render their full-color compass medallion clipped to a circle.
    val selectedIcon by settingsViewModel.appIcon.collectAsState()
    val emblemRes = selectedIcon.emblemRes
    // The Noir emblem is a light monochrome mark drawn full-colour for its dark splash;
    // on a LIGHT theme chrome it washes out. Tint it to the (dark) theme foreground so it
    // reads as a dark logo in white mode. In dark mode keep the original light artwork.
    val emblemTinted =
        if (selectedIcon == com.twinlore.app.data.AppIcon.NOIR && !isDarkTheme) true
        else selectedIcon.emblemTinted
    val emblemTint = palette.foreground

    // Collapse the whole bar's subtree (emblem, search/refresh/add/settings
    // buttons, glyphs) to one property-less semantics node instead of ~20+. Applied to
    // this outer Box, which carries none of the focus/key modifiers itself (those live
    // on the individual IconButtons below), so it covers only the visual content and does
    // not sit above any focus or key-event modifier: d-pad focus and A/Enter activation on
    // every icon keep working exactly as before. No screen reader runs on this hardware
    // (touch_exploration is off); the two enabled accessibility services never read node
    // contents. See VaultGridItem's tile-level clearAndSetSemantics for the same pattern.
    Box(modifier = modifier.fillMaxWidth().clearAndSetSemantics {}) {
        // No backdrop of its own: the bar is the first row of the library's scrolling page, so
        // nothing is ever behind it to occlude, and painting the wallpaper here meant painting
        // it in styles where the library itself paints none. Its vertical gradient below is the
        // whole treatment; whatever the chosen background style draws shows through.
        val noirMode = com.twinlore.app.ui.LocalNoirLibraryMode.current
        // No dim of its own: the page's item-names scrim already spans the whole screen,
        // this bar included, so a bar-level dim on top of it double-darkened the bar band
        // (visibly darker than the dimmed page behind it — user report). The gradient below
        // is the bar's whole treatment over whatever the page drew. Noir is the exception:
        // its crushed-dark chrome is the look.
        if (noirMode) {
            Box(Modifier.matchParentSize().background(palette.background.copy(alpha = 0.7f)))
        }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(
                        palette.background.copy(alpha = 0.85f),
                        palette.background.copy(alpha = 0.6f),
                        Color.Transparent
                    )
                )
            )
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .onKeyEvent { keyEvent ->
                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    onInputReceived()
                    if (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                        false
                    } else {
                        false
                    }
                } else {
                    false
                }
            },
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        // Emblem hugs the top-left in both orientations: portrait it sits alone; landscape
        // it leads the status row (clock/wifi/battery) at the left corner. Focusable so a
        // controller can land on it (accent boundary when focused); A / Enter / tap opens
        // the Support Game Twin page.
        val emblemInteraction = remember { MutableInteractionSource() }
        val emblemFocused by emblemInteraction.collectIsFocusedAsState()
        // Tap shows the support dialog first — only its "Support the App" button leaves for
        // the store; a stray tap on the emblem must never yank the user out of the app.
        var showSupportDialog by remember { mutableStateOf(false) }
        var emblemPreviewIcon by remember { mutableStateOf<com.twinlore.app.data.AppIcon?>(null) }
        if (showSupportDialog) {
            com.twinlore.app.ui.license.SupportDevDialog(
                onSupport = {
                    showSupportDialog = false
                    openSupportDevPage(context)
                },
                onDismiss = { showSupportDialog = false },
                onIconTap = { emblemPreviewIcon = it },
            )
        }
        com.twinlore.app.ui.license.ThemePreviewHost(
            icon = emblemPreviewIcon,
            settingsViewModel = settingsViewModel,
            onDismiss = { emblemPreviewIcon = null },
        )
        Box(
            modifier = Modifier
                .padding(start = 4.dp, end = 4.dp)
                .clip(RoundedCornerShape(10.dp))
                // Accent halo + ring when the controller lands on it, A/Enter activates.
                .then(
                    if (emblemFocused) {
                        Modifier
                            .background(focusColor.copy(alpha = 0.16f))
                            .border(2.dp, focusColor, RoundedCornerShape(10.dp))
                    } else Modifier
                )
                .gamepadFocusable()
                .onKeyEvent { e ->
                    if (e.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                        (e.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_BUTTON_A ||
                            e.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                            e.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER)
                    ) {
                        view.performHapticFeedback(hapticFeedbackIntensity)
                        showSupportDialog = true
                        true
                    } else false
                }
                .clickable(interactionSource = emblemInteraction, indication = null) {
                    view.performHapticFeedback(hapticFeedbackIntensity)
                    showSupportDialog = true
                }
                .padding(4.dp),
            contentAlignment = androidx.compose.ui.Alignment.Center,
        ) {
            GlowingEmblem(
                res = emblemRes,
                tint = emblemTint,
                tinted = emblemTinted,
                glowColor = focusColor,
                // Noir is a flat noir mark — no accent halo. (Dark mode draws it untinted,
                // which already skips the glow; light mode tints it black and must skip it too.)
                showGlow = selectedIcon != com.twinlore.app.data.AppIcon.NOIR,
                modifier = Modifier.height(38.dp),
            )
        }

        if (leadingContent != null) {
            leadingContent()
        }

        Spacer(Modifier.weight(1f))

        // Search button (opens modal, not in focus chain)
        // Supports long-press to also open search
        val searchInteractionSource = remember { MutableInteractionSource() }
        val isSearchFocused by searchInteractionSource.collectIsFocusedAsState()
        LaunchedEffect(isSearchFocused) {
            if (isSearchFocused) {
                view.performHapticFeedback(hapticFeedbackIntensity)
                if (ledEnabled) {
                    ThorLedManager.setColor(
                        focusColor,
                        true,
                        ledBrightness,
                        ledSaturationBoost,
                        ledTurnOffTime.inactivityMs
                    )
                }
                onInputReceived()
            }
        }
        IconButton(
            onClick = {
                runWithHapticAndLed(view, hapticFeedbackIntensity, ledEnabled, focusColor, ledBrightness, ledSaturationBoost, ledTurnOffTime.inactivityMs) {
                    onInputReceived() // Reset idle timer
                    onSearchClick()
                }
            },
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = iconButtonContainerColor,
                contentColor = unfocusedIconColor,
                disabledContainerColor = iconButtonContainerColor,
                disabledContentColor = disabledIconColor
            ),
            modifier = Modifier
                .padding(end = 8.dp)
                .reportSubtreeFocus { onTopBarFocusChanged?.invoke(it) }
                .onFocusChanged { focusState ->
                    if (focusState.isFocused) onInputReceived()
                }
                .focusable(interactionSource = searchInteractionSource)
                .indication(
                    interactionSource = searchInteractionSource,
                    indication = null // Disable all ripple/indication effects
                )
                .longPressHandler(
                    onLongPress = {
                        runWithHapticAndLed(view, hapticFeedbackIntensity, ledEnabled, focusColor, ledBrightness, ledSaturationBoost, ledTurnOffTime.inactivityMs) {
                            onInputReceived() // Reset idle timer
                            onSearchClick()
                        }
                    },
                    onPress = {
                        runWithHapticAndLed(view, hapticFeedbackIntensity, ledEnabled, focusColor, ledBrightness, ledSaturationBoost, ledTurnOffTime.inactivityMs) {
                            onInputReceived() // Reset idle timer
                            onSearchClick()
                        }
                    }
                )
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .then(
                        if (isSearchFocused) Modifier.background(focusColor.copy(alpha = 0.22f), CircleShape)
                        else Modifier
                    )
                    .then(
                        if (isSearchFocused) Modifier.border(2.dp, focusColor, CircleShape)
                        else Modifier
                    ),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                TopBarGlyph(
                    pixelated = appFont == AppFont.PIXEL,
                    materialIcon = Icons.Default.Search,
                    pixelRes = R.drawable.ic_pixel_search,
                    contentDescription = "Search",
                    tint = if (isSearchFocused) focusColor else unfocusedIconColor
                )
            }
        }

        // Refresh Box Art button (not in focus chain)
        val refreshInteractionSource = remember { MutableInteractionSource() }
        val isRefreshFocused by refreshInteractionSource.collectIsFocusedAsState()
        LaunchedEffect(isRefreshFocused) {
            if (isRefreshFocused) {
                view.performHapticFeedback(hapticFeedbackIntensity)
                if (ledEnabled) {
                    ThorLedManager.setColor(
                        focusColor,
                        true,
                        ledBrightness,
                        ledSaturationBoost,
                        ledTurnOffTime.inactivityMs
                    )
                }
                onInputReceived()
            }
        }
        IconButton(
            onClick = {
                runWithHapticAndLed(view, hapticFeedbackIntensity, ledEnabled, focusColor, ledBrightness, ledSaturationBoost, ledTurnOffTime.inactivityMs, event = FeedbackEvent.REFRESH) {
                    onInputReceived() // Reset idle timer
                    onRefreshBoxArt()
                }
            },
            enabled = !isScraping,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = iconButtonContainerColor,
                contentColor = unfocusedIconColor,
                disabledContainerColor = iconButtonContainerColor,
                disabledContentColor = disabledIconColor
            ),
            modifier = Modifier
                .padding(end = 8.dp)
                // NOTE: `firstButtonFocusRequester` used to live here so D-pad UP /
                // initial focus landed on Refresh. Moved to the Add (+) button below
                // so the empty-library entry point is the primary CTA.
                // Feedback comes from onClick's runWithHapticAndLed (fires for both touch
                // and controller-confirm); a key-UP hapticAndLedOnConfirm here would be a
                // second source outside the debounce window and double the sound.
                .reportSubtreeFocus { onTopBarFocusChanged?.invoke(it) }
                .onFocusChanged { focusState ->
                    if (focusState.isFocused) onInputReceived()
                }
                .focusable(interactionSource = refreshInteractionSource)
                .indication(
                    interactionSource = refreshInteractionSource,
                    indication = null // Disable all ripple/indication effects
                )
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .then(
                        if (isRefreshFocused) Modifier.background(focusColor.copy(alpha = 0.22f), CircleShape)
                        else Modifier
                    )
                    .then(
                        if (isRefreshFocused) Modifier.border(2.dp, focusColor, CircleShape)
                        else Modifier
                    ),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                TopBarGlyph(
                    pixelated = appFont == AppFont.PIXEL,
                    materialIcon = Icons.Default.Refresh,
                    pixelRes = R.drawable.ic_pixel_refresh,
                    contentDescription = "Refresh Box Art",
                    tint = when {
                        isScraping -> disabledIconColor
                        isRefreshFocused -> focusColor
                        else -> unfocusedIconColor
                    }
                )
            }
        }
        
        // Create Vault button (not in focus chain)
        val addInteractionSource = remember { MutableInteractionSource() }
        val isAddFocused by addInteractionSource.collectIsFocusedAsState()
        LaunchedEffect(isAddFocused) {
            if (isAddFocused) {
                view.performHapticFeedback(hapticFeedbackIntensity)
                if (ledEnabled) {
                    ThorLedManager.setColor(
                        focusColor,
                        true,
                        ledBrightness,
                        ledSaturationBoost,
                        ledTurnOffTime.inactivityMs
                    )
                }
                onInputReceived()
            }
        }
        IconButton(
            onClick = {
                runWithHapticAndLed(view, hapticFeedbackIntensity, ledEnabled, focusColor, ledBrightness, ledSaturationBoost, ledTurnOffTime.inactivityMs) {
                    onInputReceived() // Reset idle timer
                    onCreateVault()
                }
            },
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = iconButtonContainerColor,
                contentColor = unfocusedIconColor,
                disabledContainerColor = iconButtonContainerColor,
                disabledContentColor = disabledIconColor
            ),
            modifier = Modifier
                .padding(end = 8.dp)
                // Both requesters target the Add (+) button: `firstButtonFocusRequester`
                // is the D-pad UP / initial-focus entry point (moved here from Refresh
                // so the empty library focuses its primary CTA), `addButtonFocusRequester`
                // is the one-shot target used by callers like `onSkip`.
                .focusRequester(firstButtonFocusRequester)
                .focusRequester(addButtonFocusRequester)
                .reportSubtreeFocus { onTopBarFocusChanged?.invoke(it) }
                .onFocusChanged { focusState ->
                    if (focusState.isFocused) onInputReceived()
                }
                .focusable(interactionSource = addInteractionSource)
                .indication(
                    interactionSource = addInteractionSource,
                    indication = null // Disable all ripple/indication effects
                )
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .then(
                        if (isAddFocused) Modifier.background(focusColor.copy(alpha = 0.22f), CircleShape)
                        else Modifier
                    )
                    .then(
                        if (isAddFocused) Modifier.border(2.dp, focusColor, CircleShape)
                        else Modifier
                    ),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                TopBarGlyph(
                    pixelated = appFont == AppFont.PIXEL,
                    materialIcon = Icons.Default.Add,
                    pixelRes = R.drawable.ic_pixel_add,
                    contentDescription = "Add Game",
                    tint = if (isAddFocused) focusColor else unfocusedIconColor
                )
            }
        }
        
        // Journey button (not in focus chain). Placed before Settings so Settings stays
        // last in the bar's tab order. Always present; the journey's Locked card handles the
        // no-account and feature-off cases itself.
        run {
        val journeyInteractionSource = remember { MutableInteractionSource() }
        val isJourneyFocused by journeyInteractionSource.collectIsFocusedAsState()
        LaunchedEffect(isJourneyFocused) {
            if (isJourneyFocused) {
                view.performHapticFeedback(hapticFeedbackIntensity)
                if (ledEnabled) {
                    ThorLedManager.setColor(
                        focusColor,
                        true,
                        ledBrightness,
                        ledSaturationBoost,
                        ledTurnOffTime.inactivityMs
                    )
                }
                onInputReceived()
            }
        }
        IconButton(
            onClick = {
                runWithHapticAndLed(view, hapticFeedbackIntensity, ledEnabled, focusColor, ledBrightness, ledSaturationBoost, ledTurnOffTime.inactivityMs) {
                    onInputReceived() // Reset idle timer
                    onJourney()
                }
            },
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = iconButtonContainerColor,
                contentColor = unfocusedIconColor,
                disabledContainerColor = iconButtonContainerColor,
                disabledContentColor = disabledIconColor
            ),
            modifier = Modifier
                // Journey now sits before Settings (previously the last item, which
                // carried no trailing gap); match the Search/Refresh/Add spacing so the
                // Journey-Settings pair keeps the bar's 8dp gap between icons.
                .padding(end = 8.dp)
                .reportSubtreeFocus { onTopBarFocusChanged?.invoke(it) }
                .onFocusChanged { focusState ->
                    if (focusState.isFocused) onInputReceived()
                }
                .focusable(interactionSource = journeyInteractionSource)
                .indication(
                    interactionSource = journeyInteractionSource,
                    indication = null // Disable all ripple/indication effects
                )
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .then(
                        if (isJourneyFocused) Modifier.background(focusColor.copy(alpha = 0.22f), CircleShape)
                        else Modifier
                    )
                    .then(
                        if (isJourneyFocused) Modifier.border(2.dp, focusColor, CircleShape)
                        else Modifier
                    ),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                TopBarGlyph(
                    pixelated = appFont == AppFont.PIXEL,
                    materialIcon = Icons.Default.Timeline,
                    // Its own glyph: three milestone nodes climbing left to right, which is
                    // what the Material timeline says in the other fonts. It used to borrow
                    // the settings gear, which made it identical to the Settings button
                    // sitting immediately beside it in pixel mode. See
                    // scripts/gen-pixel-journey-icon.py for the source grid.
                    pixelRes = R.drawable.ic_pixel_journey,
                    contentDescription = "Achievement journey",
                    tint = if (isJourneyFocused) focusColor else unfocusedIconColor
                )
            }
        }
        }

        // Settings button (not in focus chain)
        val settingsInteractionSource = remember { MutableInteractionSource() }
        val isSettingsFocused by settingsInteractionSource.collectIsFocusedAsState()
        LaunchedEffect(isSettingsFocused) {
            if (isSettingsFocused) {
                view.performHapticFeedback(hapticFeedbackIntensity)
                if (ledEnabled) {
                    ThorLedManager.setColor(
                        focusColor,
                        true,
                        ledBrightness,
                        ledSaturationBoost,
                        ledTurnOffTime.inactivityMs
                    )
                }
                onInputReceived()
            }
        }
        IconButton(
            onClick = {
                runWithHapticAndLed(view, hapticFeedbackIntensity, ledEnabled, focusColor, ledBrightness, ledSaturationBoost, ledTurnOffTime.inactivityMs) {
                    onInputReceived() // Reset idle timer
                    onSettings()
                }
            },
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = iconButtonContainerColor,
                contentColor = unfocusedIconColor,
                disabledContainerColor = iconButtonContainerColor,
                disabledContentColor = disabledIconColor
            ),
            modifier = Modifier
                .reportSubtreeFocus { onTopBarFocusChanged?.invoke(it) }
                .onFocusChanged { focusState ->
                    if (focusState.isFocused) onInputReceived()
                }
                .focusable(interactionSource = settingsInteractionSource)
                .indication(
                    interactionSource = settingsInteractionSource,
                    indication = null // Disable all ripple/indication effects
                )
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .then(
                        if (isSettingsFocused) Modifier.background(focusColor.copy(alpha = 0.22f), CircleShape)
                        else Modifier
                    )
                    .then(
                        if (isSettingsFocused) Modifier.border(2.dp, focusColor, CircleShape)
                        else Modifier
                    ),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                TopBarGlyph(
                    pixelated = appFont == AppFont.PIXEL,
                    materialIcon = Icons.Default.Settings,
                    pixelRes = R.drawable.ic_pixel_settings,
                    contentDescription = "Settings",
                    tint = if (isSettingsFocused) focusColor else unfocusedIconColor
                )
            }
        }
    }

    }
}


/**
 * Top-bar glyph that swaps the Material vector for a tintable pixel-art PNG when the Pixel
 * font is active, so the chrome matches the blocky aesthetic. Both render at 22dp; the pixel
 * variant tints the alpha mask to the same colour the Material icon would use.
 */
@Composable
private fun TopBarGlyph(
    pixelated: Boolean,
    materialIcon: ImageVector,
    pixelRes: Int,
    contentDescription: String,
    tint: Color,
) {
    if (pixelated) {
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(pixelRes),
            contentDescription = contentDescription,
            colorFilter = ColorFilter.tint(tint),
            modifier = Modifier.size(22.dp)
        )
    } else {
        Icon(
            imageVector = materialIcon,
            contentDescription = contentDescription,
            modifier = Modifier.size(22.dp),
            tint = tint
        )
    }
}

/**
 * App emblem with a static (non-animated) glow behind it, coloured by the auto-focus accent
 * so it recolours as the focused vault's accent changes. The glow is a radial gradient drawn
 * behind the compass; it shows through the emblem's transparent gaps and haloes its edges.
 */
@Composable
private fun GlowingEmblem(
    res: Int,
    tint: Color,
    glowColor: Color,
    tinted: Boolean = true,
    showGlow: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val glow: Modifier = Modifier.drawBehind {
        val r = size.minDimension * 0.62f
        drawCircle(
            brush = androidx.compose.ui.graphics.Brush.radialGradient(
                colors = listOf(
                    glowColor.copy(alpha = 0.65f),
                    glowColor.copy(alpha = 0.30f),
                    Color.Transparent
                ),
                center = center,
                radius = r
            ),
            radius = r,
            center = center
        )
    }
    if (tinted) {
        // Free icons: monochrome alpha mask tinted to the theme (transparent PNG).
        Image(
            painter = androidx.compose.ui.res.painterResource(res),
            contentDescription = "Game Twin",
            colorFilter = ColorFilter.tint(tint),
            modifier = if (showGlow) modifier.then(glow) else modifier
        )
    } else {
        // Premium icons: full-color compass, background colour-keyed to transparent.
        // No accent glow — only the original + pixel emblems get the glow halo.
        Image(
            painter = androidx.compose.ui.res.painterResource(res),
            contentDescription = "Game Twin",
            contentScale = ContentScale.Fit,
            modifier = modifier
        )
    }
}
