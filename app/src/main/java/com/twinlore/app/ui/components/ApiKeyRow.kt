package com.twinlore.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

@Composable
fun ApiKeyRow(
    label: String,
    currentValue: String,
    onSave: (String) -> Unit,
    description: String? = null,
    keySourceUrl: String? = null,
    keySourceLabel: String? = null,
    allowDefault: Boolean = true,
    /** Opens the editor from outside the row: each increase opens it once. */
    editRequest: Int = 0,
) {
    var showDialog by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(editRequest) {
        if (editRequest > 0) showDialog = true
    }
    val maskedPreview = when {
        currentValue.isBlank() -> if (allowDefault) "Using default" else "Not set"
        currentValue.length <= 4 -> "••••"
        else -> currentValue.take(4) + "••••••••"
    }

    val isDefault = currentValue.isBlank()
    val keyColor = when {
        !isDefault -> Color(0xFFFF9800)        // orange for user-supplied key
        allowDefault -> Color(0xFF4CAF50)      // green for bundled default
        else -> Color(0xFF9E9E9E)              // grey "not set" (no default available)
    }

    ListItem(
        headlineContent = { Text(label) },
        supportingContent = {
            Text(buildAnnotatedString {
                if (description != null) {
                    append(description)
                    append("\n")
                }
                withStyle(SpanStyle(color = keyColor)) {
                    append(maskedPreview)
                }
            })
        },
        trailingContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (currentValue.isNotBlank()) {
                    TextButton(onClick = { onSave("") }) { Text("Clear") }
                }
                Button(onClick = { showDialog = true }) { Text("Edit") }
            }
        },
    )

    if (showDialog) {
        var draft by remember { mutableStateOf(currentValue) }
        val uriHandler = LocalUriHandler.current
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(label) },
            text = {
                Column {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        label = { Text("Key") },
                        placeholder = { Text(if (allowDefault) "Leave empty to use default" else "Enter your API key") },
                        singleLine = true,
                        visualTransformation = if (draft.isBlank()) VisualTransformation.None else PasswordVisualTransformation(),
                    )
                    if (keySourceUrl != null) {
                        Spacer(Modifier.height(8.dp))
                        val bodyColor = MaterialTheme.colorScheme.onSurface
                        val linkText = buildAnnotatedString {
                            withStyle(SpanStyle(color = bodyColor)) {
                                append("Get your key from ")
                            }
                            pushStringAnnotation(tag = "URL", annotation = keySourceUrl)
                            withStyle(SpanStyle(
                                color = Color(0xFF448AFF),
                                textDecoration = TextDecoration.Underline,
                            )) {
                                append(keySourceLabel ?: keySourceUrl)
                            }
                            pop()
                        }
                        @Suppress("DEPRECATION")
                        ClickableText(
                            text = linkText,
                            style = MaterialTheme.typography.bodySmall,
                            onClick = { offset ->
                                linkText.getStringAnnotations(tag = "URL", start = offset, end = offset)
                                    .firstOrNull()?.let { uriHandler.openUri(it.item) }
                            },
                        )
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (allowDefault && currentValue.isNotBlank()) {
                        TextButton(onClick = { onSave(""); showDialog = false }) { Text("Use Default") }
                    }
                    TextButton(onClick = { onSave(draft); showDialog = false }) { Text("Save") }
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Cancel") }
            },
        )
    }
}
