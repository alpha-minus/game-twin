package com.twinlore.app.ui.fold.guide

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

const val FOLD_LINK_UNSUPPORTED = "External links are not supported in Overlay Mode"
const val FOLD_LINK_DISMISS = "Close"

/**
 * What a tapped link does in a window that cannot follow one.
 *
 * The reader hands every leaving link here rather than to `startActivity`, and
 * the reason is the fold: Game Twin is behind a running game, so opening
 * YouTube over it takes the player out of their game to answer a tap they made
 * while reading. The other half of the problem was quieter: an ordinary http
 * link reached a null `mainViewModel` and did nothing whatsoever, which reads
 * as a broken guide rather than as a deliberate limit.
 *
 * It said so and offered to queue the page in the app's browser, which turned
 * out to be a promise with a hop in the middle: pressing Open backgrounded the
 * panel, foregrounded the app, and left you looking at the app rather than the
 * page. So it says the simpler true thing instead, and the link is named so you
 * know which one was refused.
 */
@Composable
fun FoldBlockedLinkBanner(
    link: FoldBlockedLink,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = link.url,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = FOLD_LINK_UNSUPPORTED,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onDismiss) { Text(FOLD_LINK_DISMISS) }
        }
    }
}
