package com.twinlore.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages focus restoration across screen transitions and app lifecycle
 */
@Singleton
class FocusRestorationManager @Inject constructor() {
    private val savedFocusStates = ConcurrentHashMap<String, String>()
    
    /**
     * Get the saved focus state for a screen
     */
    fun getSavedFocusState(screenId: String): String? {
        return savedFocusStates[screenId]
    }
    
    /**
     * Clear saved focus state for a screen
     */
    fun clearFocusState(screenId: String) {
        savedFocusStates.remove(screenId)
    }
    
    /**
     * Clear all saved focus states
     */
    fun clearAll() {
        savedFocusStates.clear()
    }
}

/**
 * Restore focus when returning to a screen
 */
@Composable
fun restoreFocus(
    screenId: String,
    focusManager: FocusManager?,
    restorationManager: FocusRestorationManager?
) {
    LaunchedEffect(screenId, focusManager, restorationManager) {
        val savedFocusId = restorationManager?.getSavedFocusState(screenId)
        if (savedFocusId != null && focusManager != null) {
            // Try to restore focus, but handle if element no longer exists
            try {
                focusManager.requestFocus(savedFocusId)
            } catch (e: Exception) {
                // Focus target no longer exists, clear saved state
                restorationManager.clearFocusState(screenId)
            }
        }
    }
}



