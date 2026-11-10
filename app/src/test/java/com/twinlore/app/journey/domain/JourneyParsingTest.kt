package com.twinlore.app.journey.domain

import com.twinlore.app.journey.domain.model.AchievementType
import com.twinlore.app.journey.domain.model.AwardKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JourneyParsingTest {

    @Test fun `normType maps variants defensively`() {
        assertEquals(AchievementType.STANDARD, JourneyParsing.normType(null))
        assertEquals(AchievementType.STANDARD, JourneyParsing.normType(""))
        assertEquals(AchievementType.STANDARD, JourneyParsing.normType("weird"))
        assertEquals(AchievementType.MISSABLE, JourneyParsing.normType("missable"))
        assertEquals(AchievementType.MISSABLE, JourneyParsing.normType("Missable"))
        assertEquals(AchievementType.PROGRESSION, JourneyParsing.normType("progression"))
        assertEquals(AchievementType.PROGRESSION, JourneyParsing.normType("Progression "))
        assertEquals(AchievementType.WIN_CONDITION, JourneyParsing.normType("win_condition"))
        assertEquals(AchievementType.WIN_CONDITION, JourneyParsing.normType("Win Condition"))
        assertEquals(AchievementType.WIN_CONDITION, JourneyParsing.normType("win-condition"))
    }

    @Test fun `parseWhen handles SQL shape as UTC`() {
        // 2026-03-14 08:22:11 UTC = 1773476531000
        assertEquals(1773476531000L, JourneyParsing.parseWhen("2026-03-14 08:22:11"))
    }

    @Test fun `parseWhen handles ISO shape with offset`() {
        assertEquals(1773476531000L, JourneyParsing.parseWhen("2026-03-14T08:22:11+00:00"))
        assertEquals(1773476531000L - 3600_000, JourneyParsing.parseWhen("2026-03-14T08:22:11+01:00"))
    }

    @Test fun `parseWhen returns null on garbage`() {
        assertNull(JourneyParsing.parseWhen(null))
        assertNull(JourneyParsing.parseWhen(""))
        assertNull(JourneyParsing.parseWhen("not a date"))
    }

    /**
     * The site's two beaten kinds are two different awards, and this used to fold them into one:
     * softcore lost its hollow silver circle, which the rest of the app (and the site's own
     * progression row) has always drawn.
     */
    @Test fun `parseAwardKind keeps the two beaten kinds apart`() {
        assertEquals(AwardKind.BEATEN, JourneyParsing.parseAwardKind("beaten-hardcore"))
        assertEquals(AwardKind.BEATEN_CASUAL, JourneyParsing.parseAwardKind("beaten-softcore"))
        // Anything else the site calls beaten is the hardcore form, as it always was.
        assertEquals(AwardKind.BEATEN, JourneyParsing.parseAwardKind("beaten"))
        assertEquals(AwardKind.COMPLETED, JourneyParsing.parseAwardKind("completed"))
        assertEquals(AwardKind.MASTERED, JourneyParsing.parseAwardKind("mastered"))
        assertNull(JourneyParsing.parseAwardKind(null))
        assertNull(JourneyParsing.parseAwardKind("unknown"))
    }

    // The API's casing and padding are not a contract, so parseAwardKind tolerates both the way
    // normType already does.
    @Test fun `parseAwardKind tolerates casing and padding`() {
        assertEquals(AwardKind.BEATEN, JourneyParsing.parseAwardKind("Beaten-Hardcore"))
        assertEquals(AwardKind.BEATEN_CASUAL, JourneyParsing.parseAwardKind("  BEATEN-SOFTCORE  "))
        assertEquals(AwardKind.COMPLETED, JourneyParsing.parseAwardKind("Completed"))
        assertEquals(AwardKind.MASTERED, JourneyParsing.parseAwardKind(" Mastered "))
        assertNull(JourneyParsing.parseAwardKind("   "))
    }

    @Test fun `artList orders, dedupes, drops placeholder`() {
        val art = JourneyParsing.artList(
            imageIngame = "/Images/ingame.png",
            imageTitle = "/Images/title.png",
            imageBoxArt = "/Images/ingame.png",       // dupe of ingame
            imageIcon = "/Images/000001.png",         // placeholder
            fallbackIcon = "/Images/icon.png",
        )
        assertEquals(listOf("/Images/ingame.png", "/Images/title.png", "/Images/icon.png"), art)
    }

    /**
     * RetroAchievements answers a game with no screenshot by handing back a picture that says so
     * ("No screenshot found"), not by leaving the field empty - and the ids of those cards are the
     * first handful the site ever served. Real media runs to five and six digits, which is what
     * makes a ceiling safe: the sample this rule was written against carried 067895, 054993 and
     * 051872 beside a placeholder 000010.
     */
    @Test fun `the site's own no-art cards are not art`() {
        listOf("/Images/000001.png", "/Images/000002.png", "/Images/000010.png")
            .forEach { assertTrue("$it is a placeholder", JourneyParsing.isPlaceholderArt(it)) }
        listOf("/Images/000011.png", "/Images/051872.png", "/Images/067895.png")
            .forEach { assertFalse("$it is real art", JourneyParsing.isPlaceholderArt(it)) }
        // A path with no media id at all is left alone rather than guessed at.
        assertFalse(JourneyParsing.isPlaceholderArt("/Images/cover.webp".replace("cover", "abc")))

        assertNull(JourneyParsing.artPath("/Images/000010.png"))
        assertEquals("/Images/067895.png", JourneyParsing.artPath("/Images/067895.png"))
    }
}
