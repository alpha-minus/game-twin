package com.twinlore.app.ui.navigation

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Global tracker for focused element actions
 * Used by Activity-level key event handling to trigger actions on focused buttons
 */
@Singleton
class GlobalFocusActionTracker @Inject constructor() {
    private var currentAction: (() -> Unit)? = null
    private var currentFocusId: String? = null

    /**
     * Register an action for a focused element
     */
    fun registerAction(focusId: String, action: () -> Unit) {
        currentFocusId = focusId
        currentAction = action
    }

    /**
     * Clear the registered action
     */
    fun clearAction(focusId: String) {
        if (currentFocusId == focusId) {
            currentAction = null
            currentFocusId = null
        }
    }

    /**
     * Clear all registered actions (e.g. on tab switch to prevent stale actions)
     */
    fun clearAll() {
        currentAction = null
        currentFocusId = null
    }

}



