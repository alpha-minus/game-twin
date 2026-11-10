package com.twinlore.app.ui.license

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.twinlore.app.data.AppIcon
import com.twinlore.app.data.PIXEL_UNLOCK_TAP_SCORE
import com.twinlore.app.data.license.LicenseConfig
import com.twinlore.app.data.license.LicenseTier
import com.twinlore.app.data.license.tierDisplayName
import com.twinlore.app.ui.components.LibraryPreloadSplash
import com.twinlore.app.ui.screens.ActivateLicenseDialog
import com.twinlore.app.ui.screens.SettingsViewModel

/**
 * Shows everything a theme actually changes - launcher icon, library emblem,
 * splash screen - for one [AppIcon], with the action that fits its state:
 * checkout + key entry when it is a locked supporter theme, the tap-game hint
 * for a locked Pixel, apply when it is already unlocked. The one preview every
 * entry point shares: the Settings picker, the support dialog, the emblem tap.
 */
@Composable
fun ThemePreviewDialog(
    icon: AppIcon,
    tier: LicenseTier,
    tapHighScore: Int,
    onGetEdition: () -> Unit,
    onEnterKey: () -> Unit,
    /** Applies the theme. Null where applying makes no sense (outside Settings); hides the button. */
    onApply: ((AppIcon) -> Unit)?,
    onDismiss: () -> Unit,
) {
    val premiumLocked = !icon.isUnlockedBy(tier)
    val pixelLocked = icon == AppIcon.PIXEL && tapHighScore < PIXEL_UNLOCK_TAP_SCORE
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${icon.displayName} theme") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                val captionStyle = MaterialTheme.typography.bodySmall
                val captionColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                // App icon + emblem side by side: the icon as the launcher shows
                // it, the emblem leading a mock of the library top bar (its real
                // action glyphs, dimmed) so it is obvious WHERE the emblem lives.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Image(
                            painter = painterResource(icon.previewRes),
                            contentDescription = "${icon.displayName} app icon",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(16.dp)),
                        )
                        Text(
                            "App icon",
                            style = captionStyle,
                            color = captionColor,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.Black),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Image(
                                painter = painterResource(icon.emblemRes),
                                contentDescription = "${icon.displayName} library emblem",
                                colorFilter = if (icon.emblemTinted) {
                                    ColorFilter.tint(MaterialTheme.colorScheme.primary)
                                } else {
                                    null
                                },
                                modifier = Modifier
                                    .padding(start = 10.dp)
                                    .size(32.dp),
                            )
                            Row(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .padding(end = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(9.dp),
                            ) {
                                listOf(
                                    Icons.Default.Search,
                                    Icons.Default.Add,
                                    Icons.Default.Timeline,
                                    Icons.Default.Settings,
                                ).forEach { glyph ->
                                    Icon(
                                        imageVector = glyph,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.45f),
                                        modifier = Modifier.size(15.dp),
                                    )
                                }
                            }
                        }
                        Text(
                            "Library top bar",
                            style = captionStyle,
                            color = captionColor,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
                // The splash, as it really opens: the live LibraryPreloadSplash
                // (animated backdrop, glowing logo, themed loading line) laid out
                // at phone size and scaled down into a little bezel-framed screen.
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 108.dp, height = 192.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .border(
                                width = 2.dp,
                                color = Color.White.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(14.dp),
                            )
                            .background(Color(icon.splashBgColor)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .requiredSize(width = 360.dp, height = 640.dp)
                                .graphicsLayer(scaleX = 0.3f, scaleY = 0.3f),
                        ) {
                            LibraryPreloadSplash(
                                preloadOverlayAlpha = 1f,
                                logoRes = icon.splashLogoRes,
                                bgColor = icon.splashBgColor,
                                backdropIcon = icon,
                            )
                        }
                    }
                    Text(
                        "Splash screen",
                        style = captionStyle,
                        color = captionColor,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                when {
                    pixelLocked -> Text(
                        "The Pixel theme is free, but you have to earn it: reach a " +
                            "high score of $PIXEL_UNLOCK_TAP_SCORE in the tapping game " +
                            "(in the download guide view) to unlock it.",
                        modifier = Modifier.testTag("theme_preview_pixel_hint"),
                    )
                    premiumLocked -> Text(
                        "Part of the ${icon.requiredTier.tierDisplayName()}. It changes the app " +
                            "icon, the library emblem and the splash screen, and it is a " +
                            "thank-you to everyone who supports Game Twin.",
                    )
                    onApply != null -> Text(
                        "Changes the app icon, the library emblem and the splash screen. " +
                            "Applying will close Game Twin; reopen it to see the new look.",
                    )
                    else -> Text(
                        "Changes the app icon, the library emblem and the splash screen.",
                    )
                }
                if (!pixelLocked && !premiumLocked) {
                    Text(
                        "To display the icon in the dock in Cocoon go to " +
                            "Settings->Personalization->Dock Style->Icon Pack->Game Twin",
                        style = captionStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            when {
                pixelLocked -> TextButton(onClick = onDismiss) { Text("Got it") }
                premiumLocked -> TextButton(
                    onClick = onGetEdition,
                    modifier = Modifier.testTag("theme_preview_get_edition"),
                ) { Text("Get this edition") }
                onApply != null -> TextButton(
                    onClick = { onApply(icon) },
                    modifier = Modifier.testTag("theme_preview_apply"),
                ) { Text("Apply and close") }
                // Unlocked with no apply route: the confirm slot is this state's
                // only Close, so the dismiss row below stays empty for it.
                else -> TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("theme_preview_close"),
                ) { Text("Close") }
            }
        },
        dismissButton = {
            val confirmIsClose = !pixelLocked && !premiumLocked && onApply == null
            Row {
                if (premiumLocked && !pixelLocked) {
                    TextButton(
                        onClick = onEnterKey,
                        modifier = Modifier.testTag("theme_preview_enter_key"),
                    ) { Text("Enter Key") }
                }
                if (!pixelLocked && !confirmIsClose) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("theme_preview_close"),
                    ) { Text("Close") }
                }
            }
        },
    )
}

/**
 * The one-time hint offered at the moment of applying an icon while Cocoon is
 * the detection source: the icon pack only shows up in Cocoon's dock once the
 * user has picked "Game Twin" as its icon pack, and that is easy to miss.
 * Confirming applies the icon (and closes the app, like the preview's own
 * apply); dismissing goes back without applying and without marking the hint
 * shown, so it can still show next time.
 */
@Composable
internal fun CocoonIconPackHintDialog(
    onApply: () -> Unit,
    onBack: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onBack,
        title = { Text("Show it in Cocoon too") },
        text = {
            Text(
                "To display the icon in the dock in Cocoon go to " +
                    "Settings->Personalization->Dock Style->Icon Pack->Game Twin",
            )
        },
        confirmButton = {
            TextButton(
                onClick = onApply,
                modifier = Modifier.testTag("cocoon_icon_pack_hint_apply"),
            ) { Text("Apply and close") }
        },
        dismissButton = {
            TextButton(
                onClick = onBack,
                modifier = Modifier.testTag("cocoon_icon_pack_hint_back"),
            ) { Text("Back") }
        },
    )
}

/**
 * The stateful wrapper every entry point uses: collects tier and tap score,
 * opens the checkout for the theme's own edition, and hosts the key-entry
 * dialog. Renders nothing while [icon] is null.
 */
@Composable
internal fun ThemePreviewHost(
    icon: AppIcon?,
    settingsViewModel: SettingsViewModel,
    onApply: ((AppIcon) -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    val current = icon ?: return
    val tier by settingsViewModel.licenseTier.collectAsState()
    val tapHighScore by settingsViewModel.tapGameHighScore.collectAsState()
    val uriHandler = LocalUriHandler.current
    var showActivate by remember { mutableStateOf(false) }
    ThemePreviewDialog(
        icon = current,
        tier = tier,
        tapHighScore = tapHighScore,
        onGetEdition = {
            onDismiss()
            uriHandler.openUri(LicenseConfig.checkoutUrlFor(current.requiredTier))
        },
        onEnterKey = { showActivate = true },
        onApply = onApply?.let { apply -> { chosen -> onDismiss(); apply(chosen) } },
        onDismiss = onDismiss,
    )
    if (showActivate) {
        ActivateLicenseDialog(
            settingsViewModel = settingsViewModel,
            onDismiss = { showActivate = false },
        )
    }
}
