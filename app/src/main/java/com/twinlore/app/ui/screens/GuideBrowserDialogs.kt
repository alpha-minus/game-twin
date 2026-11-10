package com.twinlore.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.twinlore.app.data.FocusColor
import com.twinlore.app.ui.LocalThemePalette
import com.twinlore.app.data.HapticFeedbackIntensity
import com.twinlore.app.data.LedTurnOffTime
import com.twinlore.app.domain.ScrollMarker
import com.twinlore.app.ui.LocalCachedFocusColor
import com.twinlore.app.ui.navigation.NavigableMenuItem
import com.twinlore.app.ui.utils.runWithHapticAndLed
import kotlinx.coroutines.delay

/**
 * Reassurance lines shown only when a save runs long. We deliberately do NOT
 * surface internal steps (archiving, optimizing, compressing…) — the user just
 * wants to know it's working. Progresses with time, not a process trace.
 */
private val saveReassuranceMessages = listOf(
    "Just a moment…",
    "Almost done…",
    "Larger pages take a little longer…",
)

/**
 * Save-in-progress overlay: "Saving guide…" with progress, rotating status messages, and Cancel button.
 *
 * When [doneBookmark] is non-null the overlay transitions to a Done state:
 * the scrim and spinner are gone, replaced by a compact card with "Open" and
 * "Close" buttons. Caller sets [doneBookmark] after a successful single-page
 * save and clears it when either button is tapped.
 */
@Composable
internal fun SaveProgressOverlay(
    visible: Boolean,
    progress: SaveProgress?,
    focusColor: androidx.compose.ui.graphics.Color,
    onCancel: () -> Unit,
    doneBookmark: com.twinlore.app.domain.SavedTab? = null,
    onOpen: (() -> Unit)? = null,
    onCloseDone: (() -> Unit)? = null,
    // Vault box art + id for the blurred save-time mask. When provided, the
    // overlay fully hides the page (so the sweep-scroll isn't visible) behind
    // a blurred backdrop matching the guide loading screen. Null → plain
    // opaque theme-colour panel.
    artworkPath: String? = null,
    vaultId: String? = null,
    // Double-tap the download backdrop to launch the tap-game easter egg while
    // the single-page save runs underneath — same as the guide-set ring.
    onRequestTapGame: () -> Unit = {}
) {
    // Done state — show lightweight "Open / Close" card, no scrim.
    if (doneBookmark != null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .padding(start = 12.dp, end = 12.dp, bottom = 80.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Saved: ${doneBookmark.displayTitle.takeIf { it.isNotBlank() } ?: "Guide"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = focusColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = { onOpen?.invoke() },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = focusColor)
                    ) {
                        Text("Open")
                    }
                    TextButton(
                        onClick = { onCloseDone?.invoke() },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    ) {
                        Text("Close")
                    }
                }
            }
        }
        return
    }

    if (!visible) return
    // Plain, step-free status. The user doesn't need the internal pipeline
    // (capture/optimize/compress); just that it's downloading.
    // No trailing ellipsis — the ring's indeterminate sweep already signals progress.
    val progressText = "Downloading"

    // Stay quiet at first; only show a reassurance line once the save runs long,
    // then step gently through them (no looping back to "rush" the user).
    var messageIndex by remember { mutableStateOf(-1) }
    LaunchedEffect(Unit) {
        delay(6000)
        messageIndex = 0
        while (messageIndex < saveReassuranceMessages.lastIndex) {
            delay(7000)
            messageIndex++
        }
    }

    // Landscape is short: the centered ring + status would collide with the
    // bottom-pinned Cancel. Shrink the ring and host Cancel inside the centered
    // column instead, so nothing overlaps. Portrait keeps its taller layout.
    val isLandscape = LocalConfiguration.current.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val cancelButton: @Composable () -> Unit = {
        TextButton(
            onClick = onCancel,
            colors = ButtonDefaults.textButtonColors(contentColor = focusColor)
        ) {
            Text("Cancel")
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Opaque blurred box-art mask: fully hides the page so the save-time
        // sweep-scroll isn't visible (and isn't distracting). It swallows taps,
        // so the tap-game double-tap is carried by the mask itself.
        BlurredSaveMask(
            artworkPath = artworkPath,
            vaultId = vaultId,
            onDoubleTap = onRequestTapGame,
        )
        // Hero ring with the status + reassurance text hosted directly beneath
        // it (the old bottom card is gone — the info lives in the ring area now).
        // Single-page has no count, so the ring is indeterminate.
        com.twinlore.app.ui.screens.browser.DownloadProgressRing(
            progress = null,
            centerLabel = "",
            color = focusColor,
            onDoubleTap = onRequestTapGame,
            modifier = Modifier.align(Alignment.Center),
            ringSize = if (isLandscape) 120.dp else 200.dp,
            // Single page: the indeterminate ring already animates; the
            // travelling dots are redundant, so drop them.
            showProgressDots = false,
            belowRing = {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = progressText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                if (messageIndex >= 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    AnimatedContent(
                        targetState = messageIndex,
                        transitionSpec = {
                            fadeIn(animationSpec = androidx.compose.animation.core.tween(400)) togetherWith
                                fadeOut(animationSpec = androidx.compose.animation.core.tween(400))
                        },
                        label = "save-status",
                    ) { index ->
                        Text(
                            text = saveReassuranceMessages[index.coerceIn(0, saveReassuranceMessages.lastIndex)],
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                // Landscape: Cancel rides in the centered column so it can't
                // clash with the status text above the bottom edge.
                if (isLandscape) {
                    Spacer(modifier = Modifier.height(16.dp))
                    cancelButton()
                }
            },
        )
        // Portrait: slim Cancel near the bottom — no banner.
        if (!isLandscape) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 90.dp)
            ) {
                cancelButton()
            }
        }
    }
}

/**
 * PDF-download overlay shown in the Browser while a PDF link is being fetched to disk.
 *
 * During download: spinner + "Cancel" button.
 * When [doneBookmarkTitle] is non-null: transitions to a Done card with "PDF saved",
 * the bookmark title, and "Open" / "Close" buttons.
 */
@Composable
internal fun PdfDownloadOverlay(
    visible: Boolean,
    focusColor: Color,
    onCancel: () -> Unit = {},
    doneBookmarkTitle: String? = null,
    onOpenDone: () -> Unit = {},
    onCloseDone: () -> Unit = {},
    savedLabel: String = "PDF saved",
    downloadingLabel: String = "Downloading PDF…",
) {
    val showDone = doneBookmarkTitle != null
    if (!visible && !showDone) return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 80.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.9f),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            if (showDone) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = savedLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            color = focusColor
                        )
                        Text(
                            text = doneBookmarkTitle ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    OutlinedButton(
                        onClick = onOpenDone,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = focusColor)
                    ) { Text("Open") }
                    TextButton(
                        onClick = onCloseDone,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    ) { Text("Close") }
                }
            } else {
                // Reassure the user during slow downloads: swap the message after
                // 8s, then again after another 8s. Resets each download (this branch
                // leaves composition once the Done card shows).
                var slowPhase by remember { mutableStateOf(0) }
                LaunchedEffect(Unit) {
                    delay(8_000)
                    slowPhase = 1
                    delay(8_000)
                    slowPhase = 2
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = focusColor,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = when (slowPhase) {
                            0 -> downloadingLabel
                            1 -> "Still downloading. This can take a little while…"
                            else -> "Hang tight, larger files take a bit…"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = onCancel,
                        colors = ButtonDefaults.textButtonColors(contentColor = focusColor)
                    ) { Text("Cancel") }
                }
            }
        }
    }
}

/**
 * Image-download overlay shown in the Browser while an image file is being fetched to disk.
 *
 * During download: spinner + "Cancel" button.
 * When [doneMapName] is non-null: transitions to a Done card with "Image saved",
 * the map name, and "Open" / "Close" buttons.
 */
@Composable
internal fun ImageDownloadOverlay(
    visible: Boolean,
    focusColor: Color,
    onCancel: () -> Unit = {},
    doneMapName: String? = null,
    onOpenDone: () -> Unit = {},
    onCloseDone: () -> Unit = {}
) {
    val showDone = doneMapName != null
    if (!visible && !showDone) return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 80.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.9f),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            if (showDone) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Image saved",
                            style = MaterialTheme.typography.bodyMedium,
                            color = focusColor
                        )
                        Text(
                            text = doneMapName ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    OutlinedButton(
                        onClick = onOpenDone,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = focusColor)
                    ) { Text("Open") }
                    TextButton(
                        onClick = onCloseDone,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    ) { Text("Close") }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = focusColor,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "Downloading image…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = onCancel,
                        colors = ButtonDefaults.textButtonColors(contentColor = focusColor)
                    ) { Text("Cancel") }
                }
            }
        }
    }
}

/**
 * Toast notification overlay; auto-dismisses after 2 seconds.
 */
@Composable
internal fun ToastOverlay(
    message: String?,
    onDismiss: () -> Unit,
    icon: @Composable (() -> Unit)? = null
) {
    message ?: return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 80.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        LaunchedEffect(message) {
            delay(2000)
            onDismiss()
        }
        AnimatedVisibility(
            visible = true,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut()
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(0.9f),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (icon != null) {
                        icon()
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/**
 * Scroll marker long-press context menu: Delete this marker / Delete all.
 */
@Composable
internal fun ScrollMarkerContextMenu(
    visible: Boolean,
    markerForDelete: ScrollMarker?,
    onDismiss: () -> Unit,
    onDeleteOne: (ScrollMarker) -> Unit,
    onDeleteAll: () -> Unit
) {
    if (!visible) return
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val focusColorMenu by settingsViewModel.focusColor.collectAsState(initial = FocusColor.CRIMSON)
    val paletteMenu = LocalThemePalette.current
    val isDarkThemeMenu = paletteMenu.isDark
    val menuBackgroundColor = paletteMenu.background.copy(alpha = 0.95f)
    val menuTextColor = paletteMenu.foreground
    val menuScheme = if (isDarkThemeMenu) {
        darkColorScheme().copy(
            surface = Color(0xFF121212),
            onSurface = Color(0xFFFFFFFF),
            onSurfaceVariant = Color(0xFFB0B0B0),
            surfaceVariant = Color(0xFF2D2D2D)
        )
    } else {
        lightColorScheme().copy(
            surface = Color(0xFFFFFFFF),
            onSurface = Color(0xFF000000),
            onSurfaceVariant = Color(0xFF444444),
            surfaceVariant = Color(0xFFF5F5F5)
        )
    }
    Dialog(onDismissRequest = onDismiss) {
        MaterialTheme(colorScheme = menuScheme) {
            Surface(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                color = menuBackgroundColor
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Scroll marker",
                        style = MaterialTheme.typography.titleSmall,
                        color = menuTextColor,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    if (markerForDelete != null) {
                        NavigableMenuItem(
                            text = "Delete \"${markerForDelete.label.take(20)}${if (markerForDelete.label.length > 20) "…" else ""}\"",
                            onClick = {
                                onDismiss()
                                onDeleteOne(markerForDelete)
                            },
                            focusId = "scroll_marker_delete_one"
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    NavigableMenuItem(
                        text = "Delete all",
                        onClick = {
                            onDeleteAll()
                            onDismiss()
                        },
                        focusId = "scroll_marker_delete_all"
                    )
                }
            }
        }
    }
}

/**
 * Delete all scroll markers confirmation dialog.
 */
@Composable
internal fun DeleteAllScrollMarkersConfirmDialog(
    visible: Boolean,
    bookmarkId: String?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    if (!visible || bookmarkId == null) return
    val view = LocalView.current
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val focusColorConfirm by settingsViewModel.focusColor.collectAsState(initial = FocusColor.CRIMSON)
    val cachedFocusColor = LocalCachedFocusColor.current?.value
    val focusColor = cachedFocusColor ?: focusColorConfirm.color
    val paletteConfirm = LocalThemePalette.current
    val isDarkThemeConfirm = paletteConfirm.isDark
    val confirmBgColor = MaterialTheme.colorScheme.surface
    val confirmTextColor = MaterialTheme.colorScheme.onSurface
    val hapticFeedbackIntensity by settingsViewModel.hapticFeedbackIntensity.collectAsState(initial = HapticFeedbackIntensity.MEDIUM)
    val ledEnabled by settingsViewModel.ledEnabled.collectAsState(initial = true)
    val ledBrightness by settingsViewModel.ledBrightness.collectAsState(initial = 127)
    val ledSaturationBoost by settingsViewModel.ledSaturationBoost.collectAsState(initial = 0.75f)
    val ledTurnOffTime by settingsViewModel.ledTurnOffTime.collectAsState(initial = LedTurnOffTime.SEC_15)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Delete all scroll markers",
                color = confirmTextColor,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Text(
                text = "Are you sure you want to delete all scroll markers for this page? This cannot be undone.",
                color = confirmTextColor,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        containerColor = confirmBgColor,
        confirmButton = {
            TextButton(
                onClick = {
                    runWithHapticAndLed(view, hapticFeedbackIntensity, ledEnabled, focusColor, ledBrightness, ledSaturationBoost, ledTurnOffTime.inactivityMs) {
                        onDismiss()
                        onConfirm(bookmarkId)
                    }
                },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Delete all")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    runWithHapticAndLed(view, hapticFeedbackIntensity, ledEnabled, focusColor, ledBrightness, ledSaturationBoost, ledTurnOffTime.inactivityMs) {
                        onDismiss()
                    }
                },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = focusColor
                )
            ) {
                Text("Cancel")
            }
        }
    )
}
