package com.twinlore.app.journey.ui.chart

import com.twinlore.app.data.retroachievements.RaUrls
import com.twinlore.app.journey.domain.LayoutEngine
import com.twinlore.app.journey.domain.model.AchievementType
import com.twinlore.app.journey.domain.model.GameRef
import com.twinlore.app.journey.domain.model.JourneyFrame
import com.twinlore.app.journey.domain.model.JourneyMode
import com.twinlore.app.journey.domain.model.PlayerJourney
import com.twinlore.app.journey.domain.model.Unlock
import com.twinlore.app.journey.ui.JourneyUrls
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The retry half of the empty-icon report. A badge whose image fails to load is simply absent from
 * the decoded map, and the screen keeps that map for as long as the journey is loaded - so a single
 * timeout used to empty an icon for the rest of the session. [JourneyBitmaps.missingUrls] is what
 * the screen asks to find the failures worth one more attempt; the attempt itself needs an
 * ImageLoader and is not unit tested, but deciding *what* to retry is pure and is.
 */
class JourneyBitmapsTest {

    private val day = 86_400_000L
    private val t0 = 1_760_000_000_000L

    private fun unlock(id: Int) = Unlock(
        id = id, title = "A$id", description = "d$id", points = 10, badgeName = "b$id",
        type = AchievementType.STANDARD, earnedAtMillis = t0 + id * day, numAwarded = 10, gameId = 1,
    )

    private val frame = JourneyFrame(
        mode = JourneyMode.SPAN,
        main = PlayerJourney(
            name = "me",
            unlocks = (1..3).map { unlock(it) },
            games = mapOf(1 to GameRef(1, "G1", null, emptyList(), colorIndex = 1, players = 100, award = null)),
            medals = emptyList(),
        ),
        rivals = emptyList(),
        spanFromMillis = 0,
        spanToMillis = t0 + 100 * day,
    )
    private val layout = LayoutEngine.layout(frame, includeNodes = true)

    /**
     * The renderer falls back to the game's icon for a badge whose own art did not decode, so the
     * icon has to be in the preloaded map even when the game has no medal on the chart. It used to
     * be collected from `layout.medals` alone, which left the fallback tier fetching nothing on
     * exactly the charts that have no awards on them.
     */
    @Test fun `game icons are preloaded for badge games, not only for medal games`() {
        val icon = "/Images/012345.png"
        val withIcon = frame.copy(
            main = frame.main.copy(
                games = mapOf(
                    1 to GameRef(1, "G1", icon, emptyList(), colorIndex = 1, players = 100, award = null),
                ),
            ),
        )
        val layoutWithIcon = LayoutEngine.layout(withIcon, includeNodes = true)
        assertTrue("fixture must have badges and no medals", layoutWithIcon.nodes.isNotEmpty())
        assertTrue("fixture must have no medals", layoutWithIcon.medals.isEmpty())

        val urls = JourneyBitmaps.urlsFor(withIcon, layoutWithIcon, "me")

        assertTrue(urls.contains(JourneyUrls.media(icon)))
        // Three badges from the same game must not ask for its icon three times.
        assertEquals(1, urls.count { it == JourneyUrls.media(icon) })
    }

    @Test fun `nothing is missing once every url has decoded`() {
        val loaded = JourneyBitmaps.urlsFor(frame, layout, "me").toSet()

        assertTrue(loaded.isNotEmpty())
        assertEquals(emptyList<String>(), JourneyBitmaps.missingUrls(frame, layout, "me", loaded))
    }

    @Test fun `a badge that failed to decode is what a retry would ask for`() {
        val failed = RaUrls.badge("b2")
        val loaded = JourneyBitmaps.urlsFor(frame, layout, "me").filterNot { it == failed }.toSet()

        assertEquals(listOf(failed), JourneyBitmaps.missingUrls(frame, layout, "me", loaded))
    }

    @Test fun `an avatar counts too, and an empty map asks for everything`() {
        assertTrue(
            JourneyBitmaps.missingUrls(frame, layout, "me", emptySet())
                .contains(JourneyUrls.userPic("me")),
        )
        assertEquals(
            JourneyBitmaps.urlsFor(frame, layout, "me"),
            JourneyBitmaps.missingUrls(frame, layout, "me", emptySet()),
        )
    }
}
