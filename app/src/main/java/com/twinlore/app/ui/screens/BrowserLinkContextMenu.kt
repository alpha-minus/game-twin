package com.twinlore.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.twinlore.app.data.FocusColor
import com.twinlore.app.ui.LocalCachedFocusColor

/**
 * Link long-press context menu (Browser mode only). Mirrors
 * [BrowserImageContextMenu]'s scaffolding so the two long-press affordances
 * feel identical to the user.
 */
@Composable
internal fun BrowserLinkContextMenu(
    linkUrl: String,
    linkText: String?,
    onOpenInNewTab: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val focusColorState by settingsViewModel.focusColor.collectAsState(initial = FocusColor.CRIMSON)
    val cachedFocusColor = LocalCachedFocusColor.current?.value
    val focusColor = cachedFocusColor ?: focusColorState.color
    val context = LocalContext.current
    val clipboard = remember { context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }
    val trimmedText = linkText?.trim()?.takeIf { it.isNotEmpty() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null
                ) { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = modifier
                    .fillMaxWidth(0.9f)
                    .padding(16.dp)
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) { /* consume click so it doesn't dismiss */ },
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(
                        text = "Link Options",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(16.dp)
                    )
                    Text(
                        text = linkUrl,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                    TextButton(
                        onClick = onOpenInNewTab,
                        colors = ButtonDefaults.textButtonColors(contentColor = focusColor)
                    ) {
                        Text("Open in new tab")
                    }
                    TextButton(
                        onClick = {
                            // No in-app toast — Android 13+ shows a system clipboard
                            // banner automatically. Pre-13 silently copies; we accept
                            // that gap rather than duplicate the system affordance.
                            clipboard.setPrimaryClip(ClipData.newPlainText("URL", linkUrl))
                            onDismiss()
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = focusColor)
                    ) {
                        Text("Copy link address")
                    }
                    TextButton(
                        onClick = {
                            if (trimmedText != null) {
                                clipboard.setPrimaryClip(ClipData.newPlainText("Link text", trimmedText))
                                onDismiss()
                            }
                        },
                        enabled = trimmedText != null,
                        colors = ButtonDefaults.textButtonColors(contentColor = focusColor)
                    ) {
                        Text("Copy link text")
                    }
                    TextButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Text("Cancel")
                    }
                }
            }
        }
    }
}
