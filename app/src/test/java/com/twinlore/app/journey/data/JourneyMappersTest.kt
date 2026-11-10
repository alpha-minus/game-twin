package com.twinlore.app.journey.data

import com.google.gson.Gson
import com.twinlore.app.data.retroachievements.RaGameInfoAndUserProgressResponse
import com.twinlore.app.data.retroachievements.RaUserCompletionProgressEntry
import com.twinlore.app.data.retroachievements.RaUserCompletionProgressResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JourneyMappersTest {

    private fun loadFixture(name: String): String {
        val stream = javaClass.classLoader!!.getResourceAsStream("journey/$name")
            ?: error("Missing fixture: journey/$name")
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    private fun parsedGameInfo(): RaGameInfoAndUserProgressResponse =
        Gson().fromJson(loadFixture("game_info_fixture.json"), RaGameInfoAndUserProgressResponse::class.java)

    // --------------- toGameSnapshot ---------------

    @Test fun `toGameSnapshot maps top-level game fields`() {
        val snapshot = JourneyMappers.toGameSnapshot(parsedGameInfo(), fallbackIconPath = "/Images/999999.png")

        assertEquals(4650, snapshot.gameId)
        assertEquals("Chrono Trigger", snapshot.title)
        assertEquals("/Images/074123.png", snapshot.iconPath)
        assertEquals(9001, snapshot.players)
    }

    @Test fun `toGameSnapshot orders art as ingame, title, boxart, icon, fallback`() {
        val snapshot = JourneyMappers.toGameSnapshot(parsedGameInfo(), fallbackIconPath = "/Images/999999.png")

        assertEquals(
            listOf(
                "/Images/074125.png", // ImageIngame
                "/Images/074124.png", // ImageTitle
                "/Images/074126.png", // ImageBoxArt
                "/Images/074123.png", // ImageIcon
                "/Images/999999.png", // fallback
            ),
            snapshot.artPaths
        )
    }

    // The backdrop reads artPaths in order; the fact cards ask for the title screen by name, which
    // is only answerable if the snapshot keeps the two candidates apart from the ordered list.
    @Test fun `toGameSnapshot keeps the title screen and the boxart addressable`() {
        val snapshot = JourneyMappers.toGameSnapshot(parsedGameInfo(), fallbackIconPath = null)

        assertEquals("/Images/074124.png", snapshot.titlePath)   // ImageTitle
        assertEquals("/Images/074126.png", snapshot.boxArtPath)  // ImageBoxArt
    }

    @Test fun `toGameSnapshot treats the no-art placeholder as no art`() {
        val dto = parsedGameInfo().copy(imageTitle = "/Images/000001.png", imageBoxArt = null)
        val snapshot = JourneyMappers.toGameSnapshot(dto, fallbackIconPath = null)

        assertNull(snapshot.titlePath)
        assertNull(snapshot.boxArtPath)
    }

    /**
     * `UserTotalPlaytime` arrives in seconds and the snapshot keeps the API's own unit; the header
     * that shows it is what turns 5400 into "1h 30m". Null when the field is absent, which is the
     * only honest answer for a game nothing has been fetched for - never a zero.
     */
    @Test fun `toGameSnapshot carries the playtime in the API's own seconds`() {
        val snapshot = JourneyMappers.toGameSnapshot(parsedGameInfo(), fallbackIconPath = null)

        assertEquals(5400, snapshot.userTotalPlaytimeSeconds)
        assertNull(
            JourneyMappers.toGameSnapshot(
                parsedGameInfo().copy(userTotalPlaytime = null), fallbackIconPath = null,
            ).userTotalPlaytimeSeconds,
        )
    }

    @Test fun `toGameSnapshot drops the achievement with a null id and keeps the other four`() {
        val snapshot = JourneyMappers.toGameSnapshot(parsedGameInfo(), fallbackIconPath = null)

        assertEquals(4, snapshot.achievements.size)
        assertEquals(listOf(101, 102, 103, 104), snapshot.achievements.map { it.id })
    }

    @Test fun `toGameSnapshot preserves raw type strings without normalizing`() {
        val snapshot = JourneyMappers.toGameSnapshot(parsedGameInfo(), fallbackIconPath = null)
        val byId = snapshot.achievements.associateBy { it.id }

        assertEquals("progression", byId.getValue(101).type)
        assertNull(byId.getValue(102).type) // both "type" and "Type" absent/null
        assertEquals("Win Condition", byId.getValue(103).type) // Pascal-only key
        assertEquals("missable", byId.getValue(104).type)
    }

    @Test fun `toGameSnapshot preserves both earned date strings raw, including hardcore-only`() {
        val snapshot = JourneyMappers.toGameSnapshot(parsedGameInfo(), fallbackIconPath = null)
        val byId = snapshot.achievements.associateBy { it.id }

        assertEquals("2026-01-01 10:00:00", byId.getValue(101).dateEarned)
        assertNull(byId.getValue(101).dateEarnedHardcore)

        assertNull(byId.getValue(104).dateEarned)
        assertEquals("2026-02-02 12:00:00", byId.getValue(104).dateEarnedHardcore)
    }

    @Test fun `toGameSnapshot maps numAwarded and points per achievement`() {
        val snapshot = JourneyMappers.toGameSnapshot(parsedGameInfo(), fallbackIconPath = null)
        val ach101 = snapshot.achievements.first { it.id == 101 }

        assertEquals(5000, ach101.numAwarded)
        assertEquals(5, ach101.points)
        assertEquals("First Steps", ach101.title)
        assertEquals("Leave the millennial fair.", ach101.description)
        assertEquals("101", ach101.badgeName)
    }

    @Test fun `toGameSnapshot defaults gameId, title and numAwarded when the dto is sparse`() {
        val sparse = RaGameInfoAndUserProgressResponse(
            id = null,
            title = null,
            imageIcon = null,
            achievements = mapOf(
                "1" to com.twinlore.app.data.retroachievements.RaAchievementDto(id = 1, numAwarded = null)
            )
        )

        val snapshot = JourneyMappers.toGameSnapshot(sparse, fallbackIconPath = "/Images/fallback.png")

        assertEquals(0, snapshot.gameId)
        assertEquals("Unknown game", snapshot.title)
        assertEquals("/Images/fallback.png", snapshot.iconPath)
        assertEquals(0, snapshot.players)
        assertEquals(0, snapshot.achievements.single().numAwarded)
        assertEquals("", snapshot.achievements.single().title)
        assertEquals("", snapshot.achievements.single().description)
        assertEquals("", snapshot.achievements.single().badgeName)
    }

    // --------------- toCompletionRows ---------------

    @Test fun `toCompletionRows returns empty list when results is null`() {
        val rows = JourneyMappers.toCompletionRows(RaUserCompletionProgressResponse(results = null))
        assertTrue(rows.isEmpty())
    }

    @Test fun `toCompletionRows drops entries with a null gameId and defaults missing fields`() {
        val dto = RaUserCompletionProgressResponse(
            results = listOf(
                RaUserCompletionProgressEntry(gameId = null, title = "Ghost"),
                RaUserCompletionProgressEntry(
                    gameId = 42,
                    title = null,
                    imageIcon = "/Images/042.png",
                    numAwarded = null,
                    numAwardedHardcore = null,
                    mostRecentAwardedDate = "2026-03-01 00:00:00",
                    highestAwardKind = "mastered",
                    highestAwardDate = "2026-03-01 00:00:00",
                ),
            )
        )

        val rows = JourneyMappers.toCompletionRows(dto)

        assertEquals(1, rows.size)
        val row = rows.single()
        assertEquals(42, row.gameId)
        assertEquals("", row.title)
        assertEquals("/Images/042.png", row.iconPath)
        assertEquals(0, row.numAwarded)
        assertEquals(0, row.numAwardedHardcore)
        assertEquals("mastered", row.highestAwardKind)
    }
}
