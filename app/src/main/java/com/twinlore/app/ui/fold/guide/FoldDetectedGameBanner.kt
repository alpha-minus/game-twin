package com.twinlore.app.ui.fold.guide

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.twinlore.app.data.AppIcon

const val FOLD_DETECTED_PROMPT = "Now playing. Switch to it?"
const val FOLD_DETECTED_SWITCH = "Switch"
const val FOLD_DETECTED_STAY = "Stay"
const val FOLD_DETECTED_SUPPORT = "Support"

/**
 * A game started while the panel was up, offered rather than taken.
 *
 * The app follows a launch by itself on its own resume, which cannot happen
 * here: the activity is behind the game. Asking rather than switching outright
 * is the difference that matters at this moment. Somebody reading a boss
 * strategy who quits to the launcher and starts something else has not asked
 * to lose their page, and the panel is the one surface where the old guide is
 * still the thing being read.
 *
 * A banner and not a card over the page, unlike the first-open intro: this is
 * an offer about something that already happened, so it may be ignored, and
 * ignoring it must not mean reading around it.
 *
 * The cover art is what makes it an offer about a game rather than a line of
 * text. [supportEmblem] is the app's own ask, in the place the showcase makes
 * it, and only while there is something to ask for.
 */
@Composable
fun FoldDetectedGameBanner(
    game: FoldDetectedGame,
    supportEmblem: AppIcon?,
    onSwitch: () -> Unit,
    onDismiss: () -> Unit,
    onSupport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag("fold_detected_game")
            // Two layers, as everywhere else in this window: surfaceVariant is
            // a 12%-alpha foreground in this app's palettes.
            .background(MaterialTheme.colorScheme.background)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        game.artworkUri?.let { art ->
            AsyncImage(
                model = art,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .height(48.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .testTag("fold_detected_art"),
            )
            Spacer(Modifier.width(10.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = game.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = FOLD_DETECTED_PROMPT,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        supportEmblem?.let { emblem ->
            TextButton(
                onClick = onSupport,
                modifier = Modifier.testTag("fold_detected_support"),
            ) {
                Image(
                    painter = painterResource(emblem.emblemRes),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    colorFilter = if (emblem.emblemTinted) {
                        ColorFilter.tint(MaterialTheme.colorScheme.primary)
                    } else {
                        null
                    },
                    modifier = Modifier.height(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(FOLD_DETECTED_SUPPORT)
            }
        }
        TextButton(
            onClick = onDismiss,
            modifier = Modifier.testTag("fold_detected_stay"),
        ) { Text(FOLD_DETECTED_STAY) }
        TextButton(
            onClick = onSwitch,
            modifier = Modifier.testTag("fold_detected_switch"),
        ) { Text(FOLD_DETECTED_SWITCH) }
    }
}
