package com.twinlore.app.ui.screens.checklist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardDoubleArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextRange
import com.twinlore.app.ui.utils.FeedbackEvent
import com.twinlore.app.ui.utils.FeedbackManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.twinlore.app.domain.ChecklistSection

/**
 * Collapsible section card with inline rename, check-all, drag handle, delete, and a body slot.
 *
 * Header icon order (left to right): drag · chevron · check-all · name · count · overflow.
 * Tapping the name enters inline edit mode (mirrors the item row).
 * Search and delete live in the overflow menu to keep the row short on narrow
 * phones.
 */
@Composable
fun ChecklistSectionCard(
    section: ChecklistSection,
    completedCount: Int,
    totalCount: Int,
    tabColor: Color,
    onToggleExpanded: () -> Unit,
    onRename: (String) -> Unit,
    onSearch: () -> Unit,
    onMoveToTop: () -> Unit,
    onPromoteItems: () -> Unit,
    onDelete: () -> Unit,
    onCheckAll: (allComplete: Boolean) -> Unit,
    handleDragModifier: Modifier,
    modifier: Modifier = Modifier,
    accentColor: Color = Color.Unspecified,
    sectionFocusRequester: FocusRequester? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (section.expanded) 180f else 0f,
        label = "chevron",
    )

    var editing by remember(section.id) { mutableStateOf(false) }
    var editValue by remember(section.id) {
        mutableStateOf(TextFieldValue(section.name, TextRange(section.name.length)))
    }
    // See the matching comment in ChecklistItemRow: onFocusChanged fires once with
    // isFocused=false right before requestFocus resolves, so we wait until we've
    // actually gained focus before treating a focus-loss as a commit signal.
    var hasBeenFocused by remember(section.id) { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val view = LocalView.current

    LaunchedEffect(editing) {
        if (editing) {
            hasBeenFocused = false
            focusRequester.requestFocus()
        }
    }

    fun commitRename() {
        val trimmed = editValue.text.trim().take(50)
        if (trimmed.isNotEmpty() && trimmed != section.name) onRename(trimmed)
        editing = false
    }

    val allChecked = totalCount > 0 && completedCount == totalCount

    val headerInteraction = remember { MutableInteractionSource() }
    val headerFocused by headerInteraction.collectIsFocusedAsState()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        // Collapsed: transparent so the blurred artwork shows through and
        // the row reads as just a header. Expanded: faint tab-tinted
        // background + thin tab-colored border so the header+items group
        // visually as one section, with a clear boundary against neighbours.
        colors = CardDefaults.cardColors(
            containerColor = if (section.expanded) {
                tabColor.copy(alpha = 0.08f)
            } else {
                Color.Transparent
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = if (section.expanded) {
            androidx.compose.foundation.BorderStroke(1.dp, tabColor.copy(alpha = 0.35f))
        } else {
            null
        },
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 2.dp,
                        color = if (headerFocused) accentColor else Color.Transparent,
                        shape = RoundedCornerShape(8.dp),
                    )
                    .then(
                        if (sectionFocusRequester != null) Modifier.focusRequester(sectionFocusRequester)
                        else Modifier
                    )
                    .focusable(interactionSource = headerInteraction)
                    // When editing, the chevron stays tappable but the whole-row click-to-expand is off
                    // so taps inside the text field don't get swallowed.
                    .then(
                        if (editing) Modifier
                        else Modifier.clickable {
                            FeedbackManager.play(view, FeedbackEvent.SWITCH)
                            onToggleExpanded()
                        }
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                // Drag handle first — matches the pattern used in the item row,
                // and lines up with users' mental model of "grip, then content."
                if (!editing) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .then(handleDragModifier),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.DragIndicator,
                            contentDescription = "Drag to reorder",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = if (section.expanded) "Collapse" else "Expand",
                    modifier = Modifier.rotate(chevronRotation),
                )

                Box(modifier = Modifier.focusProperties { canFocus = false }) {
                    Checkbox(
                        checked = allChecked,
                        onCheckedChange = {
                            FeedbackManager.play(view, FeedbackEvent.CONFIRM)
                            onCheckAll(!allChecked)
                        },
                        enabled = totalCount > 0,
                        colors = CheckboxDefaults.colors(checkedColor = tabColor),
                    )
                }

                if (editing) {
                    OutlinedTextField(
                        value = editValue,
                        onValueChange = { editValue = it.copy(text = it.text.take(50)) },
                        singleLine = true,
                        supportingText = if (editValue.text.length > 40) {
                            { Text("${editValue.text.length}/50") }
                        } else null,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { commitRename() }),
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester)
                            .onFocusChanged { focus ->
                                if (focus.isFocused) {
                                    hasBeenFocused = true
                                } else if (hasBeenFocused && editing) {
                                    commitRename()
                                }
                            },
                    )
                } else {
                    // Tap the name to enter rename mode — matches the item row's
                    // tap-to-edit gesture, which lets us drop the dedicated edit
                    // button and reclaim the space for a search button.
                    Text(
                        text = section.name,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 4.dp)
                            .focusProperties { canFocus = false }
                            .clickable {
                                // Release whatever field currently has focus
                                // (another rename, an add-item, etc.) so its
                                // commit runs before this one grabs focus.
                                focusManager.clearFocus()
                                editValue = TextFieldValue(
                                    section.name,
                                    TextRange(section.name.length),
                                )
                                editing = true
                            },
                    )
                    // Count is most useful when the section is collapsed and
                    // the items aren't visible. When expanded the items
                    // themselves carry the same information, so the count
                    // becomes noise and the row is less crowded without it.
                    if (!section.expanded) {
                        Text(
                            text = "$completedCount/$totalCount",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    var overflowOpen by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.focusProperties { canFocus = false }) {
                        IconButton(onClick = { overflowOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Section actions")
                        }
                        DropdownMenu(
                            expanded = overflowOpen,
                            onDismissRequest = { overflowOpen = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Search the web") },
                                leadingIcon = {
                                    Icon(Icons.Default.TravelExplore, contentDescription = null)
                                },
                                onClick = {
                                    FeedbackManager.play(view, FeedbackEvent.CONFIRM)
                                    overflowOpen = false
                                    onSearch()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Move to top") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.KeyboardDoubleArrowUp,
                                        contentDescription = null,
                                    )
                                },
                                onClick = {
                                    overflowOpen = false
                                    onMoveToTop()
                                },
                            )
                            if (totalCount > 0) {
                                DropdownMenuItem(
                                    text = { Text("Convert items to sections") },
                                    leadingIcon = {
                                        Icon(Icons.Default.UnfoldMore, contentDescription = null)
                                    },
                                    onClick = {
                                        overflowOpen = false
                                        onPromoteItems()
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Delete section") },
                                leadingIcon = {
                                    Icon(Icons.Default.Delete, contentDescription = null)
                                },
                                onClick = {
                                    FeedbackManager.play(view, FeedbackEvent.BACK)
                                    overflowOpen = false
                                    onDelete()
                                },
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = section.expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    content = content,
                )
            }
        }
    }
}
