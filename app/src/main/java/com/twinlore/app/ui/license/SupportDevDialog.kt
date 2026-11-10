package com.twinlore.app.ui.license

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.twinlore.app.data.AppIcon
import com.twinlore.app.data.license.LicenseTier

/**
 * "Support the development" prompt for unlicensed installs. Not a paywall;
 * vault functionality is free. Fired by [SupportPromptGate] at value events
 * (vault created, journey export finished), at most twice per install.
 * Also opened manually from the library top-bar emblem.
 */
@Composable
fun SupportDevDialog(
    onSupport: () -> Unit,
    onDismiss: () -> Unit,
    /** True on the second (last) automatic prompt: appends the farewell line. */
    isFinal: Boolean = false,
    /** Tapping a supporter icon previews that theme. Null hides the strip's tap affordance hosts can't serve. */
    onIconTap: ((AppIcon) -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enjoying Game Twin?") },
        text = {
            Column {
                // The perk is visual, so show it: one tile per supporter icon,
                // tap to see everything the theme changes. Each tile carries the
                // same tier-coloured boundary the Settings picker uses, so an
                // edition reads as the same "metal" everywhere.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AppIcon.entries
                        .filter { it.requiredTier != LicenseTier.NONE }
                        .forEach { icon ->
                            // The tile carries the Settings picker's whole boundary treatment:
                            // the tier ring AND the helping-hand badge on its top edge, so a
                            // supporter icon reads identically wherever it appears.
                            Box(modifier = Modifier.padding(top = 5.dp)) {
                                Image(
                                    painter = painterResource(icon.previewRes),
                                    contentDescription = "${icon.displayName} theme",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .border(
                                            width = 1.5.dp,
                                            color = tierLockColor(icon.requiredTier),
                                            shape = RoundedCornerShape(10.dp),
                                        )
                                        .testTag("support_dialog_icon_${icon.id}")
                                        .then(
                                            if (onIconTap != null) {
                                                Modifier.clickable { onIconTap(icon) }
                                            } else {
                                                Modifier
                                            },
                                        ),
                                )
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .offset(y = (-5).dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(Color.Black.copy(alpha = 0.6f))
                                        .padding(3.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.VolunteerActivism,
                                        contentDescription = "Supporter theme",
                                        tint = tierLockColor(icon.requiredTier),
                                        modifier = Modifier.size(11.dp),
                                    )
                                }
                            }
                        }
                }
                if (onIconTap != null) {
                    // The strip is interactive but nothing about five images says
                    // so; one quiet line makes the affordance discoverable.
                    Text(
                        "Tap a theme to preview it.",
                        modifier = Modifier.padding(bottom = 12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                } else {
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Text(
                    "If Game Twin has earned a place on your handheld, consider supporting it.\n\n" +
                        "Your support is the only thing funding it. No ads, no trackers, " +
                        "no subscriptions. You'll unlock these special app themes as a thank-you.",
                )
                if (isFinal) {
                    Text(
                        "This is the last time Game Twin asks. The door stays open in Settings.",
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .testTag("support_dialog_final_line"),
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSupport) { Text("Support the App") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Maybe Later") }
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun PreviewSupportDevDialog() {
    SupportDevDialog(onSupport = {}, onDismiss = {})
}

@Preview(showBackground = true)
@Composable
private fun PreviewSupportDevDialogFinal() {
    SupportDevDialog(onSupport = {}, onDismiss = {}, isFinal = true, onIconTap = {})
}
