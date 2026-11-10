package com.twinlore.app.data.retroachievements

/**
 * RetroAchievements credentials. Never log these.
 */
data class RaCredentials(
    val username: String,
    val apiKey: String
) {
    fun isValid(): Boolean = username.isNotBlank() && apiKey.isNotBlank()
}
