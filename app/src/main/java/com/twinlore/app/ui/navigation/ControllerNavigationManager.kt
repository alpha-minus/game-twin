package com.twinlore.app.ui.navigation

import android.view.KeyEvent
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages controller navigation input events
 * Handles D-pad and button events for console-first navigation
 */
@Singleton
class ControllerNavigationManager @Inject constructor() {
    companion object {
        private const val TAG = "ControllerNavManager"
        
        // Key codes for controller input
        const val KEY_DPAD_UP = KeyEvent.KEYCODE_DPAD_UP
        const val KEY_DPAD_DOWN = KeyEvent.KEYCODE_DPAD_DOWN
        const val KEY_DPAD_LEFT = KeyEvent.KEYCODE_DPAD_LEFT
        const val KEY_DPAD_RIGHT = KeyEvent.KEYCODE_DPAD_RIGHT
        const val KEY_DPAD_CENTER = KeyEvent.KEYCODE_DPAD_CENTER
        const val KEY_BUTTON_A = KeyEvent.KEYCODE_BUTTON_A
        
        // Navigation sensitivity (milliseconds between repeat events)
        const val DEFAULT_REPEAT_DELAY = 150L
    }

    /**
     * Check if a key event is a navigation key
     */
    fun isNavigationKey(keyCode: Int): Boolean {
        return keyCode in listOf(
            KEY_DPAD_UP,
            KEY_DPAD_DOWN,
            KEY_DPAD_LEFT,
            KEY_DPAD_RIGHT,
            KEY_DPAD_CENTER,
            KEY_BUTTON_A
        )
    }

    /**
     * Check if a key event is a D-pad direction key
     */
    fun isDirectionKey(keyCode: Int): Boolean {
        return keyCode in listOf(
            KEY_DPAD_UP,
            KEY_DPAD_DOWN,
            KEY_DPAD_LEFT,
            KEY_DPAD_RIGHT
        )
    }

    /**
     * Check if a key event is an action key (confirm/select)
     */
    fun isActionKey(keyCode: Int): Boolean {
        return keyCode == KEY_DPAD_CENTER || keyCode == KEY_BUTTON_A
    }

    /**
     * Get direction from key code
     */
    fun getDirection(keyCode: Int): NavigationDirection? {
        return when (keyCode) {
            KEY_DPAD_UP -> NavigationDirection.UP
            KEY_DPAD_DOWN -> NavigationDirection.DOWN
            KEY_DPAD_LEFT -> NavigationDirection.LEFT
            KEY_DPAD_RIGHT -> NavigationDirection.RIGHT
            else -> null
        }
    }

    /**
     * Handle key event and return navigation action
     */
    fun handleKeyEvent(keyEvent: KeyEvent): NavigationAction? {
        if (keyEvent.action != KeyEvent.ACTION_DOWN) {
            return null
        }

        val keyCode = keyEvent.keyCode
        
        return when {
            isDirectionKey(keyCode) -> {
                val direction = getDirection(keyCode)
                direction?.let { NavigationAction.Navigate(it) }
            }
            isActionKey(keyCode) -> {
                NavigationAction.Confirm
            }
            else -> null
        }
    }
}

/**
 * Navigation direction enum
 */
enum class NavigationDirection {
    UP, DOWN, LEFT, RIGHT
}

/**
 * Navigation action sealed class
 */
sealed class NavigationAction {
    data class Navigate(val direction: NavigationDirection) : NavigationAction()
    object Confirm : NavigationAction()
}



