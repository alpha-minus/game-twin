package com.twinlore.app.data.steam

/**
 * The Steam game detection treats as last played, and when, if Steam said.
 *
 * GetOwnedGames only carries rtime_last_played when the Web API key belongs to
 * the account being asked about. With the bundled key that is a single
 * account; for every other public profile the owned list comes back undated
 * and detection found nothing. GetRecentlyPlayedGames still answers for them,
 * in Steam's own order, but with no time attached.
 *
 * Being on that list means played within the last two weeks, so an undated
 * game is dated at the start of that window. In the cross-service comparison
 * it then beats a RetroAchievements game from before the window, and loses to
 * one played inside it, whose date is exact.
 */
data class SteamLastPlayed(val name: String, val appId: String, val epochMillis: Long) {
    companion object {

        /** Whether the recently played list has to be fetched: no owned game carries a date. */
        fun needsRecent(owned: List<SteamOwnedGame>): Boolean = owned.none { it.rtimeLastPlayed > 0 }

        /** GetRecentlyPlayedGames covers the last two weeks. */
        private const val RECENT_WINDOW_MS = 14L * 24 * 60 * 60 * 1000

        fun pick(owned: List<SteamOwnedGame>, recent: List<SteamRecentGame>, nowMillis: Long): SteamLastPlayed? {
            owned.filter { it.rtimeLastPlayed > 0 }.maxByOrNull { it.rtimeLastPlayed }?.let {
                return SteamLastPlayed(displayName(it.name, it.appId), it.appId.toString(), it.rtimeLastPlayed * 1000L)
            }
            val first = recent.firstOrNull() ?: return null
            return SteamLastPlayed(displayName(first.name, first.appId), first.appId.toString(), nowMillis - RECENT_WINDOW_MS)
        }

        private fun displayName(name: String?, appId: Int): String =
            name?.takeIf { it.isNotBlank() } ?: "Steam Game $appId"
    }
}
