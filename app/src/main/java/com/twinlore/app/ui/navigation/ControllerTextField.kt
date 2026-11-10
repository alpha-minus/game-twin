package com.twinlore.app.ui.navigation

import android.view.KeyEvent
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * Controller-aware text field with ZERO keyboard flash.
 *
 * Two modes:
 * - NAV MODE: focusable Box styled like a text field. No TextField in the tree = no IME.
 * - EDIT MODE: real OutlinedTextField with keyboard. On exit, returns focus to nav Box.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ControllerTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    imeAction: ImeAction = ImeAction.Done,
    keyboardType: KeyboardType = KeyboardType.Text,
    onImeAction: (() -> Unit)? = null
) {
    var isEditing by remember { mutableStateOf(false) }
    var hasBeenEdited by remember { mutableStateOf(false) }
    val navFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    if (isEditing) {
        // --- EDIT MODE: real OutlinedTextField with keyboard ---
        val editFocusRequester = remember { FocusRequester() }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = label?.let { { Text(it, color = MaterialTheme.colorScheme.onSurface) } },
            modifier = modifier
                .focusRequester(editFocusRequester)
                .onPreviewKeyEvent { keyEvent ->
                    val code = keyEvent.nativeKeyEvent.keyCode
                    val action = keyEvent.nativeKeyEvent.action
                    if (action == KeyEvent.ACTION_DOWN && code in listOf(
                            KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK
                        )
                    ) {
                        isEditing = false
                        keyboardController?.hide()
                        true
                    } else false
                },
            keyboardOptions = KeyboardOptions(
                imeAction = imeAction,
                keyboardType = keyboardType
            ),
            keyboardActions = KeyboardActions(onDone = {
                isEditing = false
                keyboardController?.hide()
                onImeAction?.invoke()
            }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = accentColor,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                cursorColor = accentColor,
                focusedLabelColor = accentColor,
                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
            )
        )
        LaunchedEffect(Unit) {
            editFocusRequester.requestFocus()
        }
    } else {
        // --- NAV MODE: focusable Box, no keyboard ---
        val interactionSource = remember { MutableInteractionSource() }
        val isFocused by interactionSource.collectIsFocusedAsState()
        val borderColor = if (isFocused) accentColor else MaterialTheme.colorScheme.outline
        val labelColor = if (isFocused) accentColor else MaterialTheme.colorScheme.onSurfaceVariant
        val textColor = MaterialTheme.colorScheme.onSurface

        Box(
            modifier = modifier
                .focusRequester(navFocusRequester)
                .border(1.dp, borderColor, RoundedCornerShape(4.dp))
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .focusable(interactionSource = interactionSource)
                .onPreviewKeyEvent { keyEvent ->
                    val code = keyEvent.nativeKeyEvent.keyCode
                    val action = keyEvent.nativeKeyEvent.action
                    if (action == KeyEvent.ACTION_DOWN || action == KeyEvent.ACTION_UP) {
                        when {
                            code in listOf(
                                KeyEvent.KEYCODE_DPAD_CENTER,
                                KeyEvent.KEYCODE_BUTTON_A,
                                KeyEvent.KEYCODE_ENTER
                            ) -> {
                                if (action == KeyEvent.ACTION_DOWN) { isEditing = true; hasBeenEdited = true }
                                true
                            }
                            code == KeyEvent.KEYCODE_DPAD_UP -> {
                                if (action == KeyEvent.ACTION_DOWN) focusManager.moveFocus(FocusDirection.Up)
                                true
                            }
                            code == KeyEvent.KEYCODE_DPAD_DOWN -> {
                                if (action == KeyEvent.ACTION_DOWN) focusManager.moveFocus(FocusDirection.Down)
                                true
                            }
                            code == KeyEvent.KEYCODE_DPAD_LEFT -> {
                                if (action == KeyEvent.ACTION_DOWN) focusManager.moveFocus(FocusDirection.Left)
                                true
                            }
                            code == KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                if (action == KeyEvent.ACTION_DOWN) focusManager.moveFocus(FocusDirection.Right)
                                true
                            }
                            else -> false
                        }
                    } else false
                },
            contentAlignment = Alignment.CenterStart
        ) {
            if (value.isEmpty() && label != null) {
                Text(label, color = labelColor, style = MaterialTheme.typography.bodyLarge)
            } else {
                Text(value, color = textColor, style = MaterialTheme.typography.bodyLarge)
            }
        }
        // Re-focus nav Box only after returning from edit mode (not on initial composition)
        if (hasBeenEdited) {
            LaunchedEffect(Unit) {
                try { navFocusRequester.requestFocus() } catch (_: Throwable) {}
            }
        }
    }
}
