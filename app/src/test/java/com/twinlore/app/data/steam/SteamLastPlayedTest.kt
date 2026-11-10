package com.twinlore.app.data.steam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Which Steam game detection calls last played.
 *
 * GetOwnedGames only carries rtime_last_played (and playtime_2weeks) when the
 * API key belongs to the account being asked about. With the bundled key that
 * is one account; every other public profile comes back without dates
 * (verified 2026-09-17: 9625 games, none dated), and detection found nothing.
 * GetRecentlyPlayedGames still answers for those profiles, so it is the fallback.
 */
class SteamLastPlayedTest {

    private fun owned(appId: Int, name: String, rtime: Long = 0) =
        SteamOwnedGame(appId = appId, name = name, rtimeLastPlayed = rtime)

    private fun recent(appId: Int, name: String?) = SteamRecentGame(appId = appId, name = name, playtime2Weeks = 1)

    private val now = 1_789_600_000_000L
    private val twoWeeks = 14L * 24 * 60 * 60 * 1000

    @Test
    fun `dated owned games pick the latest, with its time`() {
        val pick = SteamLastPlayed.pick(
            owned = listOf(owned(1, "Old", rtime = 100), owned(250900, "Isaac", rtime = 200)),
            recent = listOf(recent(1, "Old")),
            nowMillis = now,
        )
        assertEquals(SteamLastPlayed("Isaac", "250900", 200_000L), pick)
    }

    @Test
    fun `undated owned games fall back to the recently played list`() {
        val pick = SteamLastPlayed.pick(
            owned = listOf(owned(1, "A"), owned(1142710, "Total War: WARHAMMER III")),
            recent = listOf(recent(1142710, "Total War: WARHAMMER III"), recent(1, "A")),
            nowMillis = now,
        )
        // Undated, but on the list means played in the last two weeks: dated at the
        // window's start, so a RetroAchievements game from 2018 no longer wins over it
        // while one played inside the window, whose date is exact, still does.
        assertEquals(SteamLastPlayed("Total War: WARHAMMER III", "1142710", now - twoWeeks), pick)
    }

    @Test
    fun `a recent game without a name gets the same placeholder as an owned one`() {
        val pick = SteamLastPlayed.pick(owned = emptyList(), recent = listOf(recent(42, null)), nowMillis = now)
        assertEquals(SteamLastPlayed("Steam Game 42", "42", now - twoWeeks), pick)
    }

    @Test
    fun `nothing dated and nothing recent is nothing`() {
        assertNull(SteamLastPlayed.pick(owned = listOf(owned(1, "A")), recent = emptyList(), nowMillis = now))
    }

    @Test
    fun `the recent list is only needed when no owned game is dated`() {
        assertEquals(false, SteamLastPlayed.needsRecent(listOf(owned(1, "A", rtime = 5))))
        assertEquals(true, SteamLastPlayed.needsRecent(listOf(owned(1, "A"))))
        assertEquals(true, SteamLastPlayed.needsRecent(emptyList()))
    }
}
