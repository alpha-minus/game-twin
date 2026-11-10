package com.twinlore.app.ui.screens.checklist

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

/**
 * Simplified checklist item row.
 *
 * Icon order (left to right): drag · checkbox · title · delete.
 * Tapping the title puts the row into inline edit mode — no separate edit button.
 * Web search now lives on the section header (one button per section) rather
 * than per-item, to reclaim space in the row.
 */
@Composable
fun ChecklistItemRow(
    title: String,
    completed: Boolean,
    tabColor: Color,
    isDragged: Boolean,
    handleDragModifier: Modifier,
    onToggle: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = Color.Unspecified,
) {
    val bg = if (isDragged) {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
    } else {
        Color.Transparent
    }

    var editing by remember(title) { mutableStateOf(false) }
    var editValue by remember(title) {
        mutableStateOf(TextFieldValue(title, TextRange(title.length)))
    }
    // Tracks whether the field has received focus at least once during this edit session.
    // onFocusChanged fires once with isFocused=false immediately after the field appears
    // (before requestFocus resolves), so we can't treat every focus-loss as a commit.
    var hasBeenFocused by remember(title) { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val view = LocalView.current

    LaunchedEffect(editing) {
        if (editing) {
            hasBeenFocused = false
            focusRequester.requestFocus()
        }
    }

    fun commitEdit() {
        val trimmed = editValue.text.trim()
        if (trimmed.isNotEmpty() && trimmed != title) onRename(trimmed)
        editing = false
    }

    val rowInteraction = remember { MutableInteractionSource() }
    val rowFocused by rowInteraction.collectIsFocusedAsState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(bg)
            .border(
                width = 2.dp,
                color = if (rowFocused) accentColor else Color.Transparent,
                shape = RoundedCornerShape(8.dp),
            )
            .focusable(interactionSource = rowInteraction)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Wrap the drag handle in a 40dp Box so it matches the footprint of
        // the delete IconButton on the right — the row reads as evenly
        // spaced instead of cramming the drag icon against the checkbox.
        Box(
            modifier = Modifier
                .size(40.dp)
                .then(handleDragModifier),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.DragIndicator,
                contentDescription = "Reorder",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Box(modifier = Modifier.focusProperties { canFocus = false }) {
            Checkbox(
                checked = completed,
                onCheckedChange = {
                    FeedbackManager.play(view, FeedbackEvent.CONFIRM)
                    onToggle()
                },
                colors = CheckboxDefaults.colors(checkedColor = tabColor),
            )
        }

        if (editing) {
            OutlinedTextField(
                value = editValue,
                onValueChange = { editValue = it.copy(text = it.text.take(200)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { commitEdit() }),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp)
                    .focusRequester(focusRequester)
                    .onFocusChanged { focus ->
                        if (focus.isFocused) {
                            hasBeenFocused = true
                        } else if (hasBeenFocused && editing) {
                            commitEdit()
                        }
                    },
            )
        } else {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (completed) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                textDecoration = if (completed) TextDecoration.LineThrough else TextDecoration.None,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
                    .focusProperties { canFocus = false }
                    .clickable(enabled = !completed) {
                        // Release any field currently focused (rename, add-item,
                        // another item) so its commit runs before this one
                        // grabs focus. Avoids "focus stuck on old field".
                        focusManager.clearFocus()
                        editValue = TextFieldValue(title, TextRange(title.length))
                        editing = true
                    },
            )

            Box(modifier = Modifier.focusProperties { canFocus = false }) {
                IconButton(onClick = {
                    FeedbackManager.play(view, FeedbackEvent.BACK)
                    onDelete()
                }) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete item")
                }
            }
        }
    }
}
