package com.twinlore.app.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages search history persistence per vault
 * Stores the last 10 search queries for each vault
 */
@Singleton
class SearchHistoryManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val PREFS_NAME = "search_history"
        private const val KEY_HISTORY_PREFIX = "history_"
        private const val MAX_HISTORY_SIZE = 10
        private val gson = Gson()
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    /**
     * Save a search query to history for a vault
     * Maintains last 10 searches (FIFO: removes oldest when adding 11th)
     */
    fun saveSearch(vaultId: String, query: String) {
        if (vaultId.isBlank() || query.isBlank()) {
            return
        }
        
        val currentHistory = getSearchHistory(vaultId).toMutableList()
        
        // Remove if already exists (to move to top)
        currentHistory.remove(query)
        
        // Add to the beginning (most recent first)
        currentHistory.add(0, query)
        
        // Limit to MAX_HISTORY_SIZE (remove oldest)
        val limitedHistory = if (currentHistory.size > MAX_HISTORY_SIZE) {
            currentHistory.take(MAX_HISTORY_SIZE)
        } else {
            currentHistory
        }
        
        // Save to preferences as JSON array
        val json = gson.toJson(limitedHistory)
        prefs.edit()
            .putString("${KEY_HISTORY_PREFIX}$vaultId", json)
            .apply()
    }
    
    /**
     * Get search history for a vault
     * Returns list of queries (most recent first), max 10
     */
    fun getSearchHistory(vaultId: String): List<String> {
        if (vaultId.isBlank()) {
            return emptyList()
        }
        
        val json = prefs.getString("${KEY_HISTORY_PREFIX}$vaultId", null) ?: return emptyList()
        
        return try {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson<List<String>>(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Delete a specific search query from history
     */
    fun deleteSearch(vaultId: String, query: String) {
        if (vaultId.isBlank() || query.isBlank()) {
            return
        }
        
        val currentHistory = getSearchHistory(vaultId).toMutableList()
        currentHistory.remove(query)
        
        // Save updated history
        val json = gson.toJson(currentHistory)
        prefs.edit()
            .putString("${KEY_HISTORY_PREFIX}$vaultId", json)
            .apply()
    }
    
    /**
     * Clear all search history for a vault
     */
    fun clearHistory(vaultId: String) {
        if (vaultId.isBlank()) {
            return
        }
        
        prefs.edit()
            .remove("${KEY_HISTORY_PREFIX}$vaultId")
            .apply()
    }
}
