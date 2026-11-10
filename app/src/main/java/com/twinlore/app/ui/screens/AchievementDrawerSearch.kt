package com.twinlore.app.ui.screens

import android.os.SystemClock
import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

internal const val DRAWER_SEARCH_PLACEHOLDER = "Search achievements"
internal const val DRAWER_SEARCH_CLEAR = "Clear search"
internal const val DRAWER_SEARCH_FIELD_TAG = "drawer_achievement_search"
internal const val DRAWER_SEARCH_EDIT_TAG = "drawer_achievement_search_edit"
internal const val DRAWER_SEARCH_CLEAR_TAG = "drawer_achievement_search_clear"
private const val CONFIRM_ECHO_MS = 250L

/** What a drawer says when its search box narrows the list to nothing. The overlay's wording. */
internal fun drawerSearchNoMatchText(query: String): String =
    "No achievements match \"${query.trim()}\""

/**
 * The achievements drawers' search: a row of its own between the header and the chips, always
 * there, the way the overlay's panel has it. It used to hide behind a header button that swapped
 * the stats and actions for a field, which made finding something a two-step job and left the
 * buttons gone for as long as a query stood.
 *
 * Two modes, as NavigableTextField has them, because this sits in a D-pad focus chain: resting on
 * it with the controller must not throw the keyboard up over the list. NAV is a focusable pill
 * with no text field in the tree, so nothing can ask for an IME; confirm or a tap swaps in the
 * real field, which takes focus and so brings the keyboard. Back, B, confirm, the IME's search
 * key or moving up or down out of it all drop back to NAV.
 *
 * The X is its own focus stop beside the pill rather than a part of it, so a controller can
 * reach it with a press to the right and clear without opening the keyboard first.
 *
 * Colours are the drawer's: the accent the user picked for focus, and the sheet's own text
 * colour, since this is drawn on the drawer's sheet rather than on the theme's surface.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
internal fun AchievementDrawerSearch(
    query: String,
    onQueryChanged: (String) -> Unit,
    accentColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
) {
    var editing by remember { mutableStateOf(false) }
    // Set when edit mode is left by a key, so focus lands back on the pill rather than nowhere.
    // Not when it is left by focus moving elsewhere: pulling it back would undo the move.
    var refocusPill by remember { mutableStateOf(false) }
    // When edit mode began. The Thor's Confirm is BUTTON_A and then DPAD_CENTER a millisecond
    // later; the A opens the field, and the CENTER behind it must not close it again.
    var editStartedAt by remember { mutableStateOf(0L) }
    val pillFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val pillInteraction = remember { MutableInteractionSource() }
    val pillFocused by pillInteraction.collectIsFocusedAsState()

    fun startEditing() {
        editStartedAt = SystemClock.uptimeMillis()
        editing = true
    }

    fun leaveEditing(refocus: Boolean) {
        keyboardController?.hide()
        refocusPill = refocus
        editing = false
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 40.dp)
                .clip(RoundedCornerShape(50))
                .background(textColor.copy(alpha = 0.15f))
                .border(
                    width = 2.dp,
                    color = if (pillFocused || editing) accentColor else Color.Transparent,
                    shape = RoundedCornerShape(50),
                )
                .then(
                    if (editing) Modifier
                    else Modifier
                        .testTag(DRAWER_SEARCH_FIELD_TAG)
                        .focusRequester(pillFocusRequester)
                        .clickable(interactionSource = pillInteraction, indication = null) {
                            startEditing()
                        }
                        .onPreviewKeyEvent { keyEvent ->
                            val native = keyEvent.nativeKeyEvent
                            when (native.keyCode) {
                                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_BUTTON_A,
                                KeyEvent.KEYCODE_ENTER -> {
                                    if (native.action == KeyEvent.ACTION_DOWN && !editing) startEditing()
                                    true
                                }
                                else -> false
                            }
                        }
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = if (pillFocused || editing) accentColor else textColor.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp),
            )
            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        text = DRAWER_SEARCH_PLACEHOLDER,
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (editing) {
                    val editFocusRequester = remember { FocusRequester() }
                    var hadFocus by remember { mutableStateOf(false) }
                    // Cursor at the end, so a standing query is added to rather than typed over.
                    var fieldValue by remember {
                        mutableStateOf(TextFieldValue(query, selection = TextRange(query.length)))
                    }
                    if (fieldValue.text != query) {
                        fieldValue = fieldValue.copy(text = query, selection = TextRange(query.length))
                    }
                    BasicTextField(
                        value = fieldValue,
                        onValueChange = { next ->
                            fieldValue = next
                            if (next.text != query) onQueryChanged(next.text)
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall.copy(color = textColor),
                        cursorBrush = SolidColor(accentColor),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { leaveEditing(refocus = true) }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(DRAWER_SEARCH_EDIT_TAG)
                            .focusRequester(editFocusRequester)
                            .onFocusChanged { state ->
                                // Only while still editing: a key that already closed the field
                                // detaches it, and the blur that follows must not cancel the
                                // refocus that key asked for.
                                if (state.isFocused) hadFocus = true
                                else if (hadFocus && editing) leaveEditing(refocus = false)
                            }
                            .onPreviewKeyEvent { keyEvent ->
                                val native = keyEvent.nativeKeyEvent
                                val down = native.action == KeyEvent.ACTION_DOWN
                                when (native.keyCode) {
                                    KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_BUTTON_B -> {
                                        if (down) leaveEditing(refocus = true)
                                        true
                                    }
                                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_BUTTON_A -> {
                                        if (down && native.eventTime - editStartedAt > CONFIRM_ECHO_MS) {
                                            leaveEditing(refocus = true)
                                        }
                                        true
                                    }
                                    // A single line has nowhere to go up or down, so these leave
                                    // the field the way they would leave any other row.
                                    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                                        if (down) {
                                            leaveEditing(refocus = false)
                                            focusManager.moveFocus(
                                                if (native.keyCode == KeyEvent.KEYCODE_DPAD_UP) FocusDirection.Up
                                                else FocusDirection.Down
                                            )
                                        }
                                        true
                                    }
                                    else -> false
                                }
                            },
                    )
                    LaunchedEffect(Unit) {
                        try { editFocusRequester.requestFocus() } catch (_: Throwable) {}
                    }
                } else if (query.isNotEmpty()) {
                    Text(
                        text = query,
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (query.isNotEmpty()) {
            val clearInteraction = remember { MutableInteractionSource() }
            val clearFocused by clearInteraction.collectIsFocusedAsState()
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = DRAWER_SEARCH_CLEAR,
                tint = if (clearFocused) accentColor else textColor,
                modifier = Modifier
                    .testTag(DRAWER_SEARCH_CLEAR_TAG)
                    .clip(CircleShape)
                    .border(2.dp, if (clearFocused) accentColor else Color.Transparent, CircleShape)
                    .clickable(interactionSource = clearInteraction, indication = null) {
                        // The X empties the query and leaves the list whole; the field stays put
                        // and keeps focus where a controller can type again with one press.
                        if (editing) leaveEditing(refocus = true)
                        else if (clearFocused) refocusPill = true
                        onQueryChanged("")
                    }
                    .padding(8.dp)
                    .size(20.dp),
            )
        }
    }
    // Back to the pill after a key closed the field, or after the X went away under the focus.
    if (refocusPill && !editing) {
        LaunchedEffect(Unit) {
            try { pillFocusRequester.requestFocus() } catch (_: Throwable) {}
            refocusPill = false
        }
    }
}
