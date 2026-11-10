package com.twinlore.app.ui.fold.guide

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

const val FOLD_INTRO_TITLE = "Overlay Mode"
const val FOLD_INTRO_USE =
    "Display Game Twin on top of all apps. Great for dual screen games. Guides, images and achievements can be shown."

/**
 * The handle's two gestures, said once.
 *
 * The panel used to carry a labelled Fold button; folding now belongs to the
 * handle, which is the only control the panel's own chrome cannot hide. That
 * is the right home for it and the wrong thing to leave people to discover, so
 * the card that explains the mode explains this too.
 */
const val FOLD_INTRO_HANDLE =
    "Tap the handle to fold the panel away, and again to bring it back. Hold it to leave the mode or close Game Twin. Return, at the top right, opens the app."
const val FOLD_INTRO_DISMISS = "Got it"

/**
 * What this is, once.
 *
 * One paragraph and a button. The bar underneath already names every way out
 * of the mode, so a card that also explained them was reading the buttons
 * aloud to somebody looking straight at them.
 *
 * Drawn in the panel's own tree, never as a Dialog: this window is a
 * TYPE_APPLICATION_OVERLAY with no token for a sub-window, and a Dialog here
 * is a BadTokenException over a running game. A scrim that swallows taps and a
 * card in the middle do the same job without asking the window for anything.
 *
 * Shown on the first panel ever, not the first per vault. The behaviour it
 * explains does not change from game to game.
 */
@Composable
fun FoldPanelIntro(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("fold_panel_intro")
            .background(Color.Black.copy(alpha = 0.6f))
            // Swallow the tap, without a ripple: a press on the scrim is not
            // a press on the page under it.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .widthIn(max = 420.dp)
                .clip(RoundedCornerShape(16.dp))
                // Opaque: surfaceVariant is a 12%-alpha foreground in this
                // app's palettes and would let the scrim through.
                .background(MaterialTheme.colorScheme.background)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = FOLD_INTRO_TITLE,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = FOLD_INTRO_USE,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = FOLD_INTRO_HANDLE,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End).testTag("fold_panel_intro_dismiss"),
            ) { Text(FOLD_INTRO_DISMISS) }
        }
    }
}
