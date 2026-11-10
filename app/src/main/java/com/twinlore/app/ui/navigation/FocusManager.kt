package com.twinlore.app.ui.navigation

import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key

/**
 * Focus manager for controller navigation
 * Tracks focusable elements and handles focus traversal
 */
class FocusManager {
    private val focusRequesters = mutableMapOf<String, FocusRequester>()
    private var currentFocusId: String? = null

    /**
     * Register a focus requester with an ID
     */
    fun registerFocus(id: String, focusRequester: FocusRequester) {
        focusRequesters[id] = focusRequester
    }

    /**
     * Request focus for a specific ID
     */
    fun requestFocus(id: String) {
        focusRequesters[id]?.requestFocus()
        currentFocusId = id
    }

    /**
     * Clear focus
     */
    fun clearFocus() {
        currentFocusId = null
    }
}

