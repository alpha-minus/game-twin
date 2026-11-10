package com.twinlore.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.twinlore.app.domain.Vault
import com.twinlore.app.ui.navigation.NavigableMenuItem
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.twinlore.app.data.FocusColor
import com.twinlore.app.ui.LocalThemePalette
import com.twinlore.app.ui.screens.SettingsViewModel
import com.twinlore.app.ui.theme.ensureReadableAccent

/**
 * Context menu for vault operations
 * Triggered by long-press (≥500ms) on vault items
 */
@Composable
fun VaultContextMenu(
    vault: Vault,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onScrapeArtwork: () -> Unit,
    onRescanLocalMedia: () -> Unit,
    onDelete: () -> Unit,
    onDeleteMultiple: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Get theme palette and focus color
    val palette = LocalThemePalette.current
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val focusColorState by settingsViewModel.focusColor.collectAsState(initial = FocusColor.CRIMSON)
    val autoFocusColorDetection by settingsViewModel.autoFocusColorDetection.collectAsState(initial = true)
    val focusColor = focusColorState.color
    // Title accent. settingsViewModel.focusColor is the persisted/cached accent, which
    // lags the just-focused vault (DataStore round-trip) — reading it paints the PREVIOUS
    // vault's colour for a frame, i.e. the flash. With auto-detection on, derive the
    // colour from THIS vault's own stored vibrant (readability-adjusted, same as the
    // library grid) so the title is correct from the first frame.
    val titleColor = if (autoFocusColorDetection) {
        vault.artworkVibrantArgb?.let { Color(it).ensureReadableAccent(palette.isDark) } ?: focusColor
    } else focusColor
    val cardBackgroundColor = palette.background
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = cardBackgroundColor
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Text(
                    text = vault.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = titleColor,
                    modifier = Modifier.padding(16.dp)
                )
                
                NavigableMenuItem(
                    text = "Rename",
                    onClick = {
                        onRename()
                        // Don't dismiss - let the rename dialog handle dismissal
                    },
                    focusId = "context_rename"
                )
                
                NavigableMenuItem(
                    text = "Assign Artwork",
                    onClick = {
                        onScrapeArtwork()
                        // Don't dismiss - dialog will handle it. The dialog
                        // shows local + scraped options and exposes a + button
                        // for the user to add their own file via file picker.
                    },
                    focusId = "context_assign_artwork"
                )

                NavigableMenuItem(
                    text = "Re-scan local media",
                    onClick = {
                        onRescanLocalMedia()
                        onDismiss()
                    },
                    focusId = "context_rescan_local_media"
                )

                NavigableMenuItem(
                    text = "Delete",
                    onClick = {
                        onDelete()
                        // Don't dismiss - let the delete dialog handle dismissal
                    },
                    focusId = "context_delete",
                    enabled = true // Always enabled, confirmation handled in onDelete
                )

                NavigableMenuItem(
                    text = "Delete multiple",
                    onClick = {
                        onDeleteMultiple()
                        // Don't dismiss - the picker closes the menu when it is done
                    },
                    focusId = "context_delete_multiple"
                )
            }
        }
    }
}



