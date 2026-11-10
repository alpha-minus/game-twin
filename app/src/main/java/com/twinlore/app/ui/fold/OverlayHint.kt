package com.twinlore.app.ui.fold

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import com.twinlore.app.R
import com.twinlore.app.data.SavedMapRepository
import com.twinlore.app.data.local.SavedTabDao
import com.twinlore.app.domain.AppState
import com.twinlore.app.domain.StateManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether to volunteer that Overlay Mode exists.
 *
 * Once, in landscape, and only once the vault has something worth reading over
 * a game. Pure, so the rule is tested without a composition, the same way
 * [shouldShowFoldButton] and [shouldRestoreOverlay] are.
 */
fun shouldShowOverlayHint(hasContent: Boolean, seen: Boolean, isLandscape: Boolean): Boolean =
    hasContent && !seen && isLandscape

/**
 * Whether the open vault has anything to read over a game.
 *
 * A port, like [OverlayMemory], for the same reason: what "the vault currently
 * open" means is not the button's business, and a composable that reached for
 * DAOs itself could not be tested without them.
 */
interface OverlayHintContent {
    /**
     * Emits again whenever the answer changes, which is the point: the hint is
     * meant to arrive on the first download, and that download happens while
     * the vault is already on screen. A one-shot read would miss it and only
     * teach the feature on some later visit.
     */
    fun currentVaultHasContent(): Flow<Boolean>
}

/**
 * Guides are saved tabs; images are the vault's maps, which is what the Image
 * tab and the panel's own image surface both read.
 */
@Singleton
class VaultOverlayHintContent @Inject constructor(
    private val stateManager: StateManager,
    private val savedTabDao: SavedTabDao,
    private val savedMapRepository: SavedMapRepository,
) : OverlayHintContent {

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override fun currentVaultHasContent(): Flow<Boolean> =
        stateManager.currentState.flatMapLatest { state ->
            val vaultId = (state as? AppState.VaultSession)?.vaultId
                ?: return@flatMapLatest flowOf(false)
            combine(
                savedTabDao.getTabsForVault(vaultId),
                savedMapRepository.getMapsByVaultId(vaultId),
            ) { tabs, maps -> tabs.isNotEmpty() || maps.isNotEmpty() }
        }.map { it }
}

/**
 * The one-time word about Overlay Mode, under the button that opens it.
 *
 * A popup rather than a dialog, unlike [FoldSwapHintDialog]. That one stands
 * in the middle of a press it has to interrupt and answer; this one interrupts
 * nothing, arriving just after a download while the thing it describes is on
 * screen a few dp away. It carries the button's own glyph so the sentence and
 * the control are visibly the same thing.
 */
@Composable
fun OverlayHintPopup(
    onDismiss: () -> Unit,
) {
    Popup(
        popupPositionProvider = BelowAnchor,
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(RoundedCornerShape(12.dp))
                // Two layers, as everywhere else in this feature: surfaceVariant
                // is a 12%-alpha foreground in this app's palettes and lets the
                // page through on its own.
                .background(MaterialTheme.colorScheme.background)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .testTag("fold_overlay_hint"),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.PictureInPictureAlt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = stringResource(R.string.fold_overlay_hint_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = stringResource(R.string.fold_overlay_hint_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.End)
                    .testTag("fold_overlay_hint_dismiss"),
            ) { Text(stringResource(R.string.fold_overlay_hint_dismiss)) }
        }
    }
}

/**
 * Under the button and pulled left, because the button lives in the top bar's
 * right corner and a popup left-aligned with it would hang off the screen.
 */
private object BelowAnchor : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: androidx.compose.ui.unit.IntRect,
        windowSize: androidx.compose.ui.unit.IntSize,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        popupContentSize: androidx.compose.ui.unit.IntSize,
    ): IntOffset {
        val x = (anchorBounds.right - popupContentSize.width)
            .coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val y = anchorBounds.bottom
            .coerceAtMost((windowSize.height - popupContentSize.height).coerceAtLeast(0))
        return IntOffset(x, y)
    }
}
