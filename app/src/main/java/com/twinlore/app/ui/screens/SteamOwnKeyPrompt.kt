package com.twinlore.app.ui.screens

/** The suggestion shown after a Steam profile connects on the bundled Web API key. */
internal data class SteamOwnKeyPrompt(val title: String, val body: String)

/**
 * The suggestion, or null when the user already has a key of their own.
 *
 * Steam only returns when each owned game was last played to the account that
 * owns the API key. On the bundled key, detection falls back to the undated
 * recently played list, so a Steam game played inside the last two weeks cannot
 * be ranked against a RetroAchievements game from the same weeks.
 * [raConnected] adds that part.
 */
internal fun steamOwnKeyPromptFor(apiKeyOverride: String, raConnected: Boolean): SteamOwnKeyPrompt? {
    if (apiKeyOverride.isNotBlank()) return null
    val body = buildString {
        append("Steam only shares when you last played each game with the owner of the API key. ")
        append("With the built-in key, Auto Detect can only see what you played in the last two weeks, without times.")
        if (raConnected) {
            append("\n\nYou also use RetroAchievements. If you played games on both in the last two weeks, ")
            append("it cannot tell which was last, and chooses the RetroAchievements one.")
        }
        append("\n\nAdd your own free Steam Web API key for the best results.")
    }
    return SteamOwnKeyPrompt(title = "Use your own Steam API key", body = body)
}
