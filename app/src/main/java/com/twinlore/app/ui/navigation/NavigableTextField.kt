package com.twinlore.app.ui.navigation

import android.view.KeyEvent
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.twinlore.app.data.FocusColor
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import com.twinlore.app.data.HapticFeedbackIntensity
import com.twinlore.app.data.LedTurnOffTime
import com.twinlore.app.thor.led.ThorLedManager
import com.twinlore.app.ui.LocalCachedFocusColor
import com.twinlore.app.ui.utils.FeedbackEvent
import com.twinlore.app.ui.utils.FeedbackManager

/**
 * Console/Android TV-style text field with two modes:
 *
 * - **NAV mode**: Focusable box styled like a text field. No keyboard. D-pad moves focus
 *   to adjacent elements. Confirm (A/Enter/Center) or touch enters EDIT mode.
 * - **EDIT mode**: Real OutlinedTextField with keyboard. B/Back or IME action returns to NAV mode.
 *
 * Integrates with the Navigable component system: focus sound, haptic feedback, LED.
 * Use this everywhere a text input is needed — replaces ControllerTextField.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun NavigableTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    singleLine: Boolean = true,
    imeAction: ImeAction = ImeAction.Done,
    keyboardType: KeyboardType = KeyboardType.Text,
    onSubmit: (() -> Unit)? = null,
    externalFocusRequester: FocusRequester? = null,
    enabled: Boolean = true,
    isError: Boolean = false,
    startInEditMode: Boolean = false,
    trailingLoading: Boolean = false,
    /**
     * Tight vertical padding for a field held under Material's 56dp, such as
     * the fold panel's compact find bar at 40dp. OutlinedTextField's padding is
     * fixed at 16dp top and bottom, which leaves a 40dp field about 8dp for a
     * 24dp line, so typed text was clipped. Dense keeps the same decoration
     * and colours with 8dp instead.
     */
    dense: Boolean = false,
) {
    var isEditing by remember { mutableStateOf(startInEditMode) }
    var hasBeenEdited by remember { mutableStateOf(false) }
    val navFocusRequester = remember { FocusRequester() }
    val effectiveFocusRequester = externalFocusRequester ?: navFocusRequester
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val view = LocalView.current

    // Settings integration (same pattern as NavigableButton)
    // Not a ViewModel: this is composed inside the fold panel's overlay
    // window too, where there is no activity for hiltViewModel() to scope
    // one to. See AppUiSettings.
    val uiSettings = com.twinlore.app.ui.rememberAppUiSettings()
    val focusColorState by uiSettings.focusColor.collectAsState(initial = FocusColor.CRIMSON)
    val cachedFocusColorState = LocalCachedFocusColor.current?.value
    val focusColor = cachedFocusColorState ?: focusColorState.color
    val focusSoundEnabled by uiSettings.focusSoundEnabled.collectAsState(initial = true)
    val ledEnabled by uiSettings.ledEnabled.collectAsState(initial = true)
    val ledBrightness by uiSettings.ledBrightness.collectAsState(initial = 127)
    val ledSaturationBoost by uiSettings.ledSaturationBoost.collectAsState(initial = 0.75f)
    val ledTurnOffTime by uiSettings.ledTurnOffTime.collectAsState(initial = LedTurnOffTime.SEC_15)
    val hapticFeedbackIntensity by uiSettings.hapticFeedbackIntensity.collectAsState(initial = HapticFeedbackIntensity.MEDIUM)

    if (isEditing) {
        // --- EDIT MODE: real OutlinedTextField with keyboard ---
        val editFocusRequester = remember { FocusRequester() }
        val bringIntoViewRequester = remember { BringIntoViewRequester() }
        // Field's measured size, captured so the bring-into-view request
        // below can extend past the field by a keyboard-clearance margin.
        var fieldSize by remember { mutableStateOf<IntSize?>(null) }
        val density = LocalDensity.current
        val keyboardClearancePx = with(density) { 96.dp.toPx() }
        // Start selection at the end of existing text so users can keep typing
        // instead of landing the cursor at position 0.
        var textFieldValue by remember {
            mutableStateOf(TextFieldValue(text = value, selection = TextRange(value.length)))
        }
        // Keep local state in sync if the external value changes while editing.
        if (textFieldValue.text != value) {
            textFieldValue = textFieldValue.copy(text = value, selection = TextRange(value.length))
        }
        val onTextChange: (TextFieldValue) -> Unit = { newValue ->
            textFieldValue = newValue
            if (newValue.text != value) onValueChange(newValue.text)
        }
        val labelSlot: (@Composable () -> Unit)? = label?.let { { Text(it, color = MaterialTheme.colorScheme.onSurface) } }
        val placeholderSlot: (@Composable () -> Unit)? = placeholder?.let { { Text(it, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) } }
        val leadingSlot: (@Composable () -> Unit)? = leadingIcon?.let {
            { Icon(it, contentDescription = null, modifier = Modifier.size(20.dp)) }
        }
        val trailingSlot: (@Composable () -> Unit)? = if (trailingLoading) {
            {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = focusColor
                )
            }
        } else trailingIcon?.let {
            { Icon(it, contentDescription = null, modifier = Modifier.size(20.dp)) }
        }
        val fieldModifier = modifier
            .bringIntoViewRequester(bringIntoViewRequester)
            .onSizeChanged { fieldSize = it }
            .focusRequester(editFocusRequester)
            .onPreviewKeyEvent { keyEvent ->
                val code = keyEvent.nativeKeyEvent.keyCode
                val action = keyEvent.nativeKeyEvent.action
                when {
                    action == KeyEvent.ACTION_DOWN && code in listOf(
                        KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK
                    ) -> {
                        isEditing = false
                        keyboardController?.hide()
                        true
                    }
                    // Gamepad confirm while editing acts like the IME submit action,
                    // so the user can run the field's onSubmit without reaching for
                    // the on-screen keyboard's enter key.
                    action == KeyEvent.ACTION_DOWN && code in listOf(
                        KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_CENTER
                    ) -> {
                        isEditing = false
                        keyboardController?.hide()
                        onSubmit?.invoke()
                        true
                    }
                    else -> false
                }
            }
        val keyboardOptions = KeyboardOptions(
            imeAction = imeAction,
            keyboardType = keyboardType
        )
        val keyboardActions = KeyboardActions(
            onDone = {
                isEditing = false
                keyboardController?.hide()
                onSubmit?.invoke()
            },
            onSearch = {
                isEditing = false
                keyboardController?.hide()
                onSubmit?.invoke()
            },
            onGo = {
                isEditing = false
                keyboardController?.hide()
                onSubmit?.invoke()
            }
        )
        val fieldColors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = focusColor,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            cursorColor = focusColor,
            focusedLabelColor = focusColor,
            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            errorBorderColor = MaterialTheme.colorScheme.error,
            errorLabelColor = MaterialTheme.colorScheme.error
        )
        if (dense) {
            val interactionSource = remember { MutableInteractionSource() }
            BasicTextField(
                value = textFieldValue,
                onValueChange = onTextChange,
                modifier = fieldModifier,
                enabled = enabled,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(focusColor),
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                singleLine = singleLine,
                interactionSource = interactionSource,
                decorationBox = { innerTextField ->
                    OutlinedTextFieldDefaults.DecorationBox(
                        value = textFieldValue.text,
                        innerTextField = innerTextField,
                        enabled = enabled,
                        singleLine = singleLine,
                        visualTransformation = VisualTransformation.None,
                        interactionSource = interactionSource,
                        isError = isError,
                        label = labelSlot,
                        placeholder = placeholderSlot,
                        leadingIcon = leadingSlot,
                        trailingIcon = trailingSlot,
                        colors = fieldColors,
                        contentPadding = OutlinedTextFieldDefaults.contentPadding(
                            top = 8.dp,
                            bottom = 8.dp,
                        ),
                        container = {
                            OutlinedTextFieldDefaults.ContainerBox(
                                enabled = enabled,
                                isError = isError,
                                interactionSource = interactionSource,
                                colors = fieldColors,
                            )
                        },
                    )
                },
            )
        } else {
            OutlinedTextField(
                value = textFieldValue,
                onValueChange = onTextChange,
                label = labelSlot,
                placeholder = placeholderSlot,
                leadingIcon = leadingSlot,
                trailingIcon = trailingSlot,
                singleLine = singleLine,
                enabled = enabled,
                isError = isError,
                modifier = fieldModifier,
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                colors = fieldColors,
            )
        }
        LaunchedEffect(Unit) {
            editFocusRequester.requestFocus()
            // Delay slightly to let keyboard appear, then scroll field into
            // view. We extend the requested rect ~96dp BELOW the field so the
            // scroller doesn't just slide the bottom edge flush with the
            // keyboard top — that leaves the user typing right at the seam
            // with no visible breathing room. With this extension, the parent
            // scrolls until the field plus a comfortable margin is visible.
            kotlinx.coroutines.delay(300)
            val s = fieldSize
            val rect = if (s == null) null else Rect(
                left = 0f,
                top = 0f,
                right = s.width.toFloat(),
                bottom = s.height.toFloat() + keyboardClearancePx,
            )
            bringIntoViewRequester.bringIntoView(rect)
        }
    } else {
        // --- NAV MODE: focusable Box, no keyboard ---
        val interactionSource = remember { MutableInteractionSource() }
        val isFocused by interactionSource.collectIsFocusedAsState()
        val borderColor = when {
            isError -> MaterialTheme.colorScheme.error
            isFocused -> focusColor
            else -> MaterialTheme.colorScheme.outline
        }
        val labelColor = when {
            isError -> MaterialTheme.colorScheme.error
            isFocused -> focusColor
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        val textColor = MaterialTheme.colorScheme.onSurface

        Box(
            modifier = modifier
                .focusRequester(effectiveFocusRequester)
                .defaultMinSize(minHeight = 42.dp)
                .border(
                    width = if (isFocused) 4.dp else 1.dp,
                    color = borderColor,
                    shape = RoundedCornerShape(8.dp)
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled
                ) {
                    FeedbackManager.play(view, FeedbackEvent.CONFIRM, focusSoundEnabled, hapticFeedbackIntensity)
                    isEditing = true
                    hasBeenEdited = true
                }
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .onFocusChanged { focusState ->
                    if (focusState.isFocused) {
                        // NOTE: FOCUS fires synchronously here (not via LaunchedEffect like the
                        // other Navigable components). If a touch path ever calls requestFocus()
                        // before its CONFIRM play, this would fire FOCUS first and defeat the
                        // manager's CONFIRM-suppresses-FOCUS window — keep CONFIRM first.
                        FeedbackManager.play(view, FeedbackEvent.FOCUS, focusSoundEnabled, hapticFeedbackIntensity)
                        if (ledEnabled) {
                            ThorLedManager.setColor(
                                focusColor,
                                true,
                                ledBrightness,
                                ledSaturationBoost,
                                ledTurnOffTime.inactivityMs
                            )
                        }
                    }
                }
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
                                if (action == KeyEvent.ACTION_DOWN) {
                                    FeedbackManager.play(view, FeedbackEvent.CONFIRM, focusSoundEnabled, hapticFeedbackIntensity)
                                    isEditing = true
                                    hasBeenEdited = true
                                }
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (leadingIcon != null) {
                    Icon(
                        leadingIcon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = if (isFocused) focusColor else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(12.dp))
                }
                Box(modifier = Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder ?: label ?: "",
                            color = labelColor,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    } else {
                        Text(
                            text = value,
                            color = textColor,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }
                if (trailingLoading) {
                    Spacer(Modifier.width(8.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = focusColor
                    )
                }
            }
        }
        // Re-focus nav Box only after returning from edit mode (not on initial composition)
        if (hasBeenEdited) {
            LaunchedEffect(Unit) {
                try { effectiveFocusRequester.requestFocus() } catch (_: Throwable) {}
            }
        }
    }
}
