package com.twinlore.app.ui.screens.browser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.twinlore.app.data.FocusColor
import com.twinlore.app.ui.LocalCachedFocusColor
import com.twinlore.app.ui.LocalThemePalette
import com.twinlore.app.ui.screens.SettingsViewModel

/**
 * Non-modal bulk-save banner. Pinned to the bottom of the screen, doesn't
 * block back-press or page interaction. The banner is non-hideable during
 * a run — only Cancel (Preparing/Saving) and Done/Close (Done/Cancelled)
 * dismiss it. [onComplete] only fires when the run is actually finished
 * (Done or Cancelled state); the mid-save states never call it.
 */
@Composable
internal fun BulkSaveProgressBanner(
    coordinator: com.twinlore.app.data.library.PageGroupSaveCoordinator,
    onOpenVault: () -> Unit,
    onComplete: () -> Unit,
    // Vault box art + id for the blurred mask shown while the set downloads.
    artworkPath: String? = null,
    vaultId: String? = null,
    // Double-tap on the progress ring asks the host to open the tap game.
    onRequestTapGame: () -> Unit = {}
) {
    val state by coordinator.state.collectAsState()
    val palette = LocalThemePalette.current
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val focusColorState by settingsViewModel.focusColor.collectAsState(initial = FocusColor.CRIMSON)
    val cachedFocusColor = LocalCachedFocusColor.current?.value
    val focusColor = cachedFocusColor ?: focusColorState.color
    val textColor = palette.foreground
    val secondaryTextColor = textColor.copy(alpha = 0.7f)

    // Dim the rest of the screen during active save (Preparing / Saving) so
    // the user has a clear visual signal that work is in progress. On
    // Done / Cancelled the screen returns to normal opacity so the user can
    // tap "Open" / "Close" naturally and see the underlying page.
    val isActive = state is com.twinlore.app.ui.screens.browser.BulkSaveState.Preparing ||
        state is com.twinlore.app.ui.screens.browser.BulkSaveState.Saving

    // Keep the screen on during active save. The WebView driver needs
    // the activity foreground+resumed to finish each page load; if the
    // device sleeps or the user switches apps, in-flight saves stall
    // out. View.keepScreenOn is per-view and framework-aggregated, so
    // the lock is released automatically when this banner leaves the
    // composition or when isActive flips back to false on Done /
    // Cancelled.
    val view = LocalView.current
    DisposableEffect(isActive) {
        view.keepScreenOn = isActive
        onDispose { view.keepScreenOn = false }
    }

    // Landscape is short: a centered ring + status text collides with the
    // bottom-pinned Cancel. Shrink the ring and move Cancel into the centered
    // column. Portrait keeps its taller layout.
    val isLandscape = LocalConfiguration.current.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        if (isActive) {
            // Active download: blurred mask + hero ring with the status text
            // hosted directly beneath it + a slim Cancel. No banner — the info
            // lives in the ring area (parity with the single-page overlay).
            com.twinlore.app.ui.screens.BlurredSaveMask(
                artworkPath = artworkPath,
                vaultId = vaultId,
                onDoubleTap = onRequestTapGame,
            )
            // Determinate while saving (completed / total), indeterminate during
            // Preparing. Double-tap launches the tap game.
            val savingState = state as? BulkSaveState.Saving
            val ringProgress: Float? = savingState?.let {
                it.completed.size.toFloat() / it.total.coerceAtLeast(1).toFloat()
            }
            val ringLabel = savingState?.let { "${it.completed.size}/${it.total}" } ?: ""
            DownloadProgressRing(
                progress = ringProgress,
                centerLabel = ringLabel,
                color = focusColor,
                onDoubleTap = onRequestTapGame,
                modifier = Modifier.align(Alignment.Center),
                ringSize = if (isLandscape) 120.dp else 200.dp,
                belowRing = {
                    ActiveBelowRing(state, textColor, secondaryTextColor)
                    // Landscape: Cancel rides in the centered column so it can't
                    // clash with the status text above the bottom edge.
                    if (isLandscape) {
                        Spacer(modifier = Modifier.height(16.dp))
                        TextButton(
                            onClick = { coordinator.cancel() },
                            colors = ButtonDefaults.textButtonColors(contentColor = focusColor),
                        ) { Text("Cancel") }
                    }
                },
            )
            // Portrait: slim Cancel pinned near the bottom.
            if (!isLandscape) {
                TextButton(
                    onClick = { coordinator.cancel() },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 90.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = focusColor),
                ) {
                    Text("Cancel")
                }
            }
        } else {
            // Terminal states (Done / Cancelled) keep a banner card with their
            // Open / Close actions — the ring isn't shown once the run finishes.
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .padding(start = 12.dp, end = 12.dp, bottom = 80.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                    when (val s = state) {
                        is BulkSaveState.Done ->
                            DoneRow(s, focusColor, secondaryTextColor, onOpenVault, onComplete)
                        is BulkSaveState.Cancelled ->
                            CancelledRow(s, focusColor, secondaryTextColor, onComplete)
                        else -> Unit
                    }
                }
            }
        }
    }
}

/** Download status hosted beneath the hero ring during Preparing / Saving. */
@Composable
private fun ActiveBelowRing(
    state: BulkSaveState,
    textColor: Color,
    secondaryTextColor: Color,
) {
    Spacer(Modifier.height(12.dp))
    when (state) {
        is BulkSaveState.Preparing -> {
            Text(
                "Downloading ${state.total} pages",
                style = MaterialTheme.typography.bodyLarge,
                color = textColor,
                maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            )
            Text(
                "Just a moment…",
                style = MaterialTheme.typography.bodySmall,
                color = secondaryTextColor,
                maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            )
        }
        is BulkSaveState.Saving -> {
            val saved = state.completed.count { it.outcome == Outcome.SAVED }
            val failed = state.completed.count {
                it.outcome == Outcome.FAILED_HTTP ||
                    it.outcome == Outcome.FAILED_LOAD_TIMEOUT ||
                    it.outcome == Outcome.BLOCKED_ROBOTS
            }
            if (state.currentTitle.isNotBlank()) {
                Text(
                    state.currentTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 300.dp),
                )
                Spacer(Modifier.height(4.dp))
            }
            Text(
                "saved $saved · failed $failed",
                style = MaterialTheme.typography.bodySmall,
                color = secondaryTextColor,
                maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            )
        }
        else -> Unit
    }
    Spacer(Modifier.height(6.dp))
    Text(
        "Please keep the app open. Screen stays on until done.",
        style = MaterialTheme.typography.bodySmall,
        color = secondaryTextColor,
        maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
        modifier = Modifier.widthIn(max = 300.dp),
    )
}

@Composable
private fun DoneRow(
    state: com.twinlore.app.ui.screens.browser.BulkSaveState.Done,
    focusColor: Color, secondaryTextColor: Color,
    onOpenVault: () -> Unit, onComplete: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Done: ${state.saved} saved",
                style = MaterialTheme.typography.bodyLarge,
                color = focusColor,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(
                "${state.failed.size} failed",
                color = secondaryTextColor,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        OutlinedButton(onClick = onOpenVault,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = focusColor)) {
            Text("Open")
        }
        TextButton(onClick = onComplete,
            colors = ButtonDefaults.textButtonColors(contentColor = secondaryTextColor)) {
            Text("Done")
        }
    }
}

@Composable
private fun CancelledRow(
    state: com.twinlore.app.ui.screens.browser.BulkSaveState.Cancelled,
    focusColor: Color, secondaryTextColor: Color,
    onComplete: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Cancelled",
                style = MaterialTheme.typography.bodyLarge,
                color = focusColor,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(
                "${state.savedSoFar} saved before stopping",
                color = secondaryTextColor,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        TextButton(onClick = onComplete,
            colors = ButtonDefaults.textButtonColors(contentColor = secondaryTextColor)) {
            Text("Close")
        }
    }
}
