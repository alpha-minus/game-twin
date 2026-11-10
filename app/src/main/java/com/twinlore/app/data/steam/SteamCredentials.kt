package com.twinlore.app.data.steam

/**
 * Steam Web API credentials. Never log these.
 */
data class SteamCredentials(
    val vanityUrl: String,
    val steamId: String
) {
    fun isValid(): Boolean = steamId.isNotBlank()
}
