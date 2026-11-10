package com.twinlore.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.twinlore.app.data.FocusColor
import com.twinlore.app.data.library.VgmapsBulkDownloader
import com.twinlore.app.data.library.VgmapsDownloadState
import com.twinlore.app.ui.LocalCachedFocusColor
import com.twinlore.app.ui.LocalThemePalette

/**
 * Non-modal banner showing the current VGMaps download progress (or the
 * post-run summary). Renders nothing in [VgmapsDownloadState.Idle].
 *
 * Pinned to the bottom of the screen as a Card overlay, matching the Browser's
 * Save All Pages pattern. The banner dims the background and keeps the screen
 * on during an active download. It is non-hideable during a run; only Cancel
 * (Downloading) and OK/Close (Complete/Cancelled) dismiss it.
 *
 * Hosted by [MapTabContent] so it sits inside the Maps tab. Because
 * [VgmapsBulkDownloader] is @Singleton, leaving and re-entering the tab
 * mid-run does not interrupt the download; the banner just disappears and
 * reappears.
 *
 * @param onDismissSummary called when the user taps OK or Close on a Complete
 *   or Cancelled banner; the host resets the downloader so the banner hides on
 *   the next state read.
 */
@Composable
fun VgmapsDownloadBanner(
    downloader: VgmapsBulkDownloader,
    onDismissSummary: () -> Unit,
) {
    val state by downloader.state.collectAsState()
    val palette = LocalThemePalette.current
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val focusColorState by settingsViewModel.focusColor.collectAsState(initial = FocusColor.CRIMSON)
    val cachedFocusColor = LocalCachedFocusColor.current?.value
    val focusColor = cachedFocusColor ?: focusColorState.color
    val textColor = palette.foreground
    val secondaryTextColor = textColor.copy(alpha = 0.7f)

    val isActive = state is VgmapsDownloadState.Downloading
    val isVisible = state !is VgmapsDownloadState.Idle

    // Keep the screen on during active download. Mirrors the Browser banner's
    // approach: the device must stay awake so in-flight image saves don't stall.
    val view = LocalView.current
    DisposableEffect(isActive) {
        view.keepScreenOn = isActive
        onDispose { view.keepScreenOn = false }
    }

    if (!isVisible) return

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        if (isActive) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
            )
        }
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(start = 12.dp, end = 12.dp, bottom = 80.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                when (val s = state) {
                    is VgmapsDownloadState.Idle -> Unit
                    is VgmapsDownloadState.Downloading ->
                        DownloadingRow(s, focusColor, textColor, secondaryTextColor,
                            onCancel = { downloader.cancel() })
                    is VgmapsDownloadState.Complete ->
                        CompleteRow(s, focusColor, secondaryTextColor, onDismissSummary)
                    is VgmapsDownloadState.Cancelled ->
                        CancelledRow(s, focusColor, secondaryTextColor, onDismissSummary)
                }
                if (isActive) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Please keep the app open. Screen stays on until done.",
                        color = secondaryTextColor,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadingRow(
    state: VgmapsDownloadState.Downloading,
    focusColor: Color,
    textColor: Color,
    secondaryTextColor: Color,
    onCancel: () -> Unit,
) {
    val progress = state.done.coerceAtLeast(0).toFloat() / state.total.coerceAtLeast(1).toFloat()
    val failed = state.failures.size
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = focusColor)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Saving ${state.done + 1} of ${state.total} from VGMaps",
                style = MaterialTheme.typography.bodyLarge,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                state.currentCaption,
                color = secondaryTextColor,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TextButton(
            onClick = onCancel,
            colors = ButtonDefaults.textButtonColors(contentColor = secondaryTextColor),
        ) {
            Text("Cancel")
        }
    }
    Spacer(Modifier.height(8.dp))
    LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth(), color = focusColor)
    Spacer(Modifier.height(4.dp))
    Text(
        "saved ${state.done - failed} · failed $failed",
        color = secondaryTextColor,
        style = MaterialTheme.typography.bodySmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun CompleteRow(
    state: VgmapsDownloadState.Complete,
    focusColor: Color,
    secondaryTextColor: Color,
    onDismiss: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Done: ${state.saved} saved",
                style = MaterialTheme.typography.bodyLarge,
                color = focusColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (state.failures.isNotEmpty()) {
                Text(
                    "${state.failures.size} failed",
                    color = secondaryTextColor,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        OutlinedButton(
            onClick = onDismiss,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = focusColor),
        ) {
            Text("OK")
        }
    }
}

@Composable
private fun CancelledRow(
    state: VgmapsDownloadState.Cancelled,
    focusColor: Color,
    secondaryTextColor: Color,
    onDismiss: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Cancelled",
                style = MaterialTheme.typography.bodyLarge,
                color = focusColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${state.savedSoFar} saved before stopping",
                color = secondaryTextColor,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TextButton(
            onClick = onDismiss,
            colors = ButtonDefaults.textButtonColors(contentColor = secondaryTextColor),
        ) {
            Text("Close")
        }
    }
}
