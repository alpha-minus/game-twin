package com.twinlore.app.domain

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the single active application state
 * Thread-safe state management using StateFlow
 */
@Singleton
class StateManager @Inject constructor() {
    companion object {
        private const val TAG = "StateManager"
    }

    private val _currentState: MutableStateFlow<AppState> = MutableStateFlow(AppState.Library)
    val currentState: StateFlow<AppState> = _currentState.asStateFlow()

    /**
     * Get the current state synchronously
     */
    fun getCurrentState(): AppState = _currentState.value

    /**
     * Set state with validation
     * @param newState The state to transition to
     * @param validate Whether to validate the transition (default: true)
     * @return true if state was set, false if transition was invalid
     */
    suspend fun setState(newState: AppState, validate: Boolean = true): Boolean {
        return withContext(Dispatchers.Main) {
            val current = _currentState.value
            
            if (validate && !isValidTransition(current, newState)) {
                try {
                    Log.w(TAG, "Invalid state transition: $current -> $newState")
                } catch (e: Exception) {
                    // Log not available in unit tests, ignore
                }
                false
            } else {
                try {
                    Log.d(TAG, "State transition: $current -> $newState")
                } catch (e: Exception) {
                    // Log not available in unit tests, ignore
                }
                _currentState.value = newState
                true
            }
        }
    }

    /**
     * Validate if a state transition is allowed
     * Transition rules:
     * - Library can go to any state
     * - MapViewer/GuideViewer can transition to each other with same vaultId
     * - Settings accessible from anywhere
     * - Sleep accessible from anywhere
     */
    fun isValidTransition(from: AppState, to: AppState): Boolean {
        return when {
            // Same state is always valid
            from == to -> true
            
            // Settings and Sleep are accessible from anywhere
            to is AppState.Settings || to is AppState.Sleep -> true

            // Library can go to any state
            from is AppState.Library -> true

            // Play stats can return anywhere it was opened from (currently: library).
            from is AppState.GameStats -> true

            // Achievement Journey can be entered from / returned to anywhere it was opened from.
            to is AppState.Journey || from is AppState.Journey -> true

            // VaultSession: Library <-> VaultSession; tab switch within same vault;
            // navigate to MapViewer/GuideViewer with same vaultId
            from is AppState.Library && to is AppState.VaultSession -> true
            from is AppState.VaultSession && to is AppState.Library -> true
            from is AppState.VaultSession && to is AppState.VaultSession ->
                from.vaultId == to.vaultId
            from is AppState.VaultSession && to is AppState.MapViewer ->
                from.vaultId == to.vaultId
            from is AppState.VaultSession && to is AppState.GuideViewer ->
                from.vaultId == to.vaultId

            // MapViewer can go to GuideViewer with same vaultId, or back to VaultSession/Library
            from is AppState.MapViewer ->
                (to is AppState.GuideViewer && from.vaultId == to.vaultId) ||
                (to is AppState.VaultSession && from.vaultId == to.vaultId) ||
                (to is AppState.Library)

            // GuideViewer can go to MapViewer with same vaultId, or back to VaultSession/Library
            from is AppState.GuideViewer ->
                (to is AppState.MapViewer && from.vaultId == to.vaultId) ||
                (to is AppState.VaultSession && from.vaultId == to.vaultId) ||
                (to is AppState.Library)
            
            // Settings can go back to Library
            from is AppState.Settings ->
                to is AppState.Library
            
            // AppCatalog can return to Library (top-bar back / system back).
            from is AppState.AppCatalog ->
                to is AppState.Library
            
            // Sleep can restore to previous state (handled separately)
            from is AppState.Sleep -> true

            // Default: invalid transition
            else -> false
        }
    }

    /**
     * Observe state changes (already available via currentState StateFlow)
     * This is a convenience method for clarity
     */
    fun observeState(): StateFlow<AppState> = currentState

    fun serializeState(state: AppState): String = when (state) {
        is AppState.Library -> "Library"
        is AppState.Settings -> "Settings"
        is AppState.Sleep -> "Sleep"
        is AppState.RetroAchievementsProfile -> "RetroAchievementsProfile"
        is AppState.AppCatalog -> "AppCatalog"
        // Stats are recomputed from scratch anyway; a cold start lands on the library.
        is AppState.GameStats -> "Library"
        // Journey is a transient guided flow; a cold start lands on the library.
        is AppState.Journey -> "Library"
        is AppState.VaultSession -> "VaultSession:${state.vaultId}:${state.activeTab.name}"
        is AppState.MapViewer -> "MapViewer:${state.vaultId}"
        is AppState.GuideViewer -> "GuideViewer:${state.vaultId}:${state.tabId ?: ""}"
    }

    fun deserializeState(value: String): AppState? {
        val firstColon = value.indexOf(':')
        if (firstColon == -1) {
            // Parameterless states
            return when (value) {
                "Library" -> AppState.Library
                "Settings" -> AppState.Settings
                "Sleep" -> AppState.Sleep
                "RetroAchievementsProfile" -> AppState.RetroAchievementsProfile
                "AppCatalog" -> AppState.AppCatalog
                else -> null
            }
        }
        val type = value.substring(0, firstColon)
        val rest = value.substring(firstColon + 1)
        return try {
            when (type) {
                "MapViewer" -> AppState.MapViewer(rest)
                "VaultSession" -> {
                    val lastColon = rest.lastIndexOf(':')
                    if (lastColon == -1) return null
                    val vaultId = rest.substring(0, lastColon)
                    val tabName = rest.substring(lastColon + 1)
                    AppState.VaultSession(vaultId, VaultTab.valueOf(tabName))
                }
                "GuideViewer" -> {
                    val lastColon = rest.lastIndexOf(':')
                    if (lastColon == -1) return null
                    val vaultId = rest.substring(0, lastColon)
                    val tabId = rest.substring(lastColon + 1).ifEmpty { null }
                    AppState.GuideViewer(vaultId, tabId)
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }
}

