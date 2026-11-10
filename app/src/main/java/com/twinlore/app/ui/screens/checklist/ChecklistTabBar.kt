package com.twinlore.app.ui.screens.checklist

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.zIndex
import com.twinlore.app.domain.ChecklistTab
import com.twinlore.app.ui.theme.ChecklistColorPalette
import com.twinlore.app.ui.utils.FeedbackEvent
import com.twinlore.app.ui.utils.FeedbackManager
import kotlinx.coroutines.delay

/**
 * Horizontal scrollable row of tab chips plus a trailing "+ Tab" chip.
 *
 * Each chip shows a color dot · name · optional `done/total` counter · a tiny
 * drag-indicator icon so users discover the reorder gesture.
 *
 * Interaction:
 * - Tap a chip → select that tab.
 * - Long-press and drag → reorder. Other chips snap to their new slots when
 *   the dragged chip crosses their half-width; release commits the new order
 *   and the landed chip flashes the tab color.
 *
 * Implementation notes:
 * - The drag source of truth is the local `localOrder` list. While a drag is
 *   in progress this list is mutated to show the reorder live. After release
 *   we hand the new order to the ViewModel and wait for `tabs` to match
 *   before clearing `localOrder` — that avoids a visible "flicker to old
 *   order and back" while the Room write round-trips.
 * - Tab widths are captured per-id via onSizeChanged; snap thresholds always
 *   use the actual neighbor width, so chips of different label lengths snap
 *   at the right spot.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChecklistTabBar(
    tabs: List<ChecklistTab>,
    activeTabId: String?,
    completionByTabId: Map<String, Pair<Int, Int>>,
    onSelectTab: (String) -> Unit,
    onReorderTabs: (orderedIds: List<String>) -> Unit,
    onAddTab: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Local optimistic order — mirrors the tabs prop when idle. During a drag
    // this list is mutated to show the reorder live; after drag end we keep
    // it until the tabs prop catches up, so there's no flicker.
    var localOrder by remember { mutableStateOf<List<ChecklistTab>?>(null) }
    var dragIndex by remember { mutableStateOf(-1) }
    var dragOffsetX by remember { mutableStateOf(0f) }
    val tabWidths = remember { mutableStateOf<Map<String, Float>>(emptyMap()) }
    var flashTabId by remember { mutableStateOf<String?>(null) }

    // `pointerInput(tab.id) { detectDragGesturesAfterLongPress(...) }` captures
    // its lambda closures ONCE (when first composed for that tab id). Function
    // parameters like `tabs` and `onReorderTabs` are re-bound each
    // recomposition but the gesture coroutine keeps pointing at the original
    // capture — so a drag initiated AFTER a reorder would seed from a stale
    // tabs list and "undo" the change. rememberUpdatedState gives us a live
    // reference readable from inside the long-running coroutine.
    val currentTabs by rememberUpdatedState(tabs)
    val currentOnReorderTabs by rememberUpdatedState(onReorderTabs)

    // Clear the optimistic override once the upstream tabs prop matches.
    LaunchedEffect(tabs, localOrder) {
        val local = localOrder
        if (local != null && dragIndex < 0) {
            if (tabs.map { it.id } == local.map { it.id }) {
                localOrder = null
            }
        }
    }

    LaunchedEffect(flashTabId) {
        if (flashTabId != null) {
            delay(700)
            flashTabId = null
        }
    }

    val rendered = localOrder ?: tabs

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        itemsIndexed(rendered, key = { _, t -> t.id }) { index, tab ->
            val active = tab.id == activeTabId
            val color = ChecklistColorPalette.color(tab.color)
            val (done, total) = completionByTabId[tab.id] ?: (0 to 0)
            val isDragged = dragIndex >= 0 && index == dragIndex
            val flashColor by animateColorAsState(
                targetValue = if (tab.id == flashTabId) color.copy(alpha = 0.45f) else Color.Transparent,
                animationSpec = tween(durationMillis = 400),
                label = "tabFlash",
            )

            // Placement animation on siblings only — the dragged chip uses
            // graphicsLayer.translationX to track the finger and must NOT
            // also animate its layout slot, or the two effects overlap.
            val placementModifier =
                if (isDragged) Modifier
                else Modifier.animateItemPlacement(tween(durationMillis = 120))

            TabChip(
                tab = tab,
                active = active,
                color = color,
                flashColor = flashColor,
                done = done,
                total = total,
                isDragged = isDragged,
                dragOffsetX = dragOffsetX,
                placementModifier = placementModifier,
                onSelect = { if (dragIndex < 0) onSelectTab(tab.id) },
                onWidthMeasured = { widthPx ->
                    val current = tabWidths.value
                    if (current[tab.id] != widthPx) {
                        tabWidths.value = current + (tab.id to widthPx)
                    }
                },
                dragHandler = Modifier.pointerInput(tab.id) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            // Always seed the drag list from the most recent
                            // optimistic order so consecutive drags can chain
                            // even while a VM commit is in flight. currentTabs
                            // is live (rememberUpdatedState) so we never seed
                            // from a stale copy captured at first composition.
                            val seed = localOrder ?: currentTabs
                            localOrder = seed.toList()
                            dragIndex = seed.indexOfFirst { it.id == tab.id }
                            dragOffsetX = 0f
                        },
                        onDrag = { change, delta ->
                            change.consume()
                            dragOffsetX += delta.x
                            val widths = tabWidths.value
                            // Snap forward (dragging right).
                            while (true) {
                                val list = localOrder ?: break
                                if (dragIndex < 0 || dragIndex >= list.size - 1) break
                                val nextWidth = widths[list[dragIndex + 1].id] ?: break
                                if (dragOffsetX <= nextWidth * 0.5f) break
                                val cur = list.toMutableList()
                                val moved = cur.removeAt(dragIndex)
                                cur.add(dragIndex + 1, moved)
                                localOrder = cur
                                dragIndex += 1
                                dragOffsetX -= nextWidth
                            }
                            // Snap backward (dragging left).
                            while (true) {
                                val list = localOrder ?: break
                                if (dragIndex <= 0) break
                                val prevWidth = widths[list[dragIndex - 1].id] ?: break
                                if (dragOffsetX >= -prevWidth * 0.5f) break
                                val cur = list.toMutableList()
                                val moved = cur.removeAt(dragIndex)
                                cur.add(dragIndex - 1, moved)
                                localOrder = cur
                                dragIndex -= 1
                                dragOffsetX += prevWidth
                            }
                            // Clamp at list edges so the chip doesn't translate
                            // arbitrarily past the leftmost/rightmost slot —
                            // that looked "weird" when dragging the first chip
                            // because it would keep moving right with no snap
                            // once all remaining chips had been passed.
                            val list = localOrder
                            if (list != null) {
                                if (dragIndex == 0 && dragOffsetX < 0) dragOffsetX = 0f
                                if (dragIndex == list.size - 1 && dragOffsetX > 0) dragOffsetX = 0f
                            }
                        },
                        onDragEnd = {
                            val finalList = localOrder
                            val landedIdx = dragIndex
                            dragIndex = -1
                            dragOffsetX = 0f
                            if (finalList != null && landedIdx in finalList.indices) {
                                // Compare against the live tabs list, not the
                                // stale captured one.
                                val originalOrder = currentTabs.map { it.id }
                                val newOrder = finalList.map { it.id }
                                if (newOrder != originalOrder) currentOnReorderTabs(newOrder)
                                flashTabId = finalList[landedIdx].id
                            }
                            // localOrder stays until tabs prop matches
                            // (the LaunchedEffect above clears it).
                        },
                        onDragCancel = {
                            localOrder = null
                            dragIndex = -1
                            dragOffsetX = 0f
                        },
                    )
                },
            )
        }

        item(key = "add_tab") {
            AddTabChip(onClick = onAddTab)
        }
    }
}

@Composable
private fun TabChip(
    tab: ChecklistTab,
    active: Boolean,
    color: Color,
    flashColor: Color,
    done: Int,
    total: Int,
    isDragged: Boolean,
    dragOffsetX: Float,
    placementModifier: Modifier,
    onSelect: () -> Unit,
    onWidthMeasured: (widthPx: Float) -> Unit,
    dragHandler: Modifier,
) {
    val view = LocalView.current
    Row(
        modifier = Modifier
            // Placement animation applies only to non-dragged chips; the
            // dragged chip uses translationX below to follow the finger.
            .then(placementModifier)
            .onSizeChanged { size -> if (size.width > 0) onWidthMeasured(size.width.toFloat()) }
            // graphicsLayer ALWAYS sets translationX so the chip snaps back to
            // 0 the moment isDragged flips false — avoiding the "frozen" look.
            .graphicsLayer {
                translationX = if (isDragged) dragOffsetX else 0f
            }
            .let { if (isDragged) it.zIndex(1f) else it }
            .clip(ChipShape)
            .background(
                if (active) color.copy(alpha = 0.25f).compositeOver(flashColor)
                else flashColor,
            )
            .border(
                width = if (active) 2.dp else 1.dp,
                color = if (active) color else MaterialTheme.colorScheme.outlineVariant,
                shape = ChipShape,
            )
            .then(dragHandler)
            .clickable(onClick = {
                FeedbackManager.play(view, FeedbackEvent.SWITCH)
                onSelect()
            })
            .padding(ChipContentPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(if (active) color else color.copy(alpha = 0.4f)),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = tab.name,
            style = MaterialTheme.typography.labelLarge,
            color = if (active) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (total > 0) {
            Spacer(Modifier.width(6.dp))
            Text(
                text = "$done/$total",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Tiny grip icon hints that the chip is reorderable.
        Spacer(Modifier.width(4.dp))
        Icon(
            imageVector = Icons.Default.DragIndicator,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(14.dp),
        )
    }
}

/** Same outer dimensions and padding as TabChip so the "+ Tab" pill matches height. */
@Composable
private fun AddTabChip(onClick: () -> Unit) {
    val view = LocalView.current
    Row(
        modifier = Modifier
            .clip(ChipShape)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = ChipShape,
            )
            .clickable(onClick = {
                FeedbackManager.play(view, FeedbackEvent.CONFIRM)
                onClick()
            })
            .padding(ChipContentPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = "Add tab",
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = "Tab",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val ChipShape = RoundedCornerShape(50)
private val ChipContentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
