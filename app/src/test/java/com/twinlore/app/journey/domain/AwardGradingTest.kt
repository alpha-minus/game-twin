package com.twinlore.app.journey.domain

import com.twinlore.app.domain.AchievementAward
import com.twinlore.app.domain.LibraryAchievementCalc
import com.twinlore.app.journey.domain.model.AwardKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The journey grades an award the way the rest of Game Twin grades it, and this is what makes that
 * a fact rather than an intention.
 *
 * The chart, the library tile, the RA tab and the vault drawer can all be on screen for the same
 * game within two taps of each other, and the chart used to disagree with the other three below the
 * top award: it drew a completion with a filled SILVER dot and left the hollow one for beaten, so
 * the two lesser awards were the wrong way round against the app's own award badge.
 *
 * The authority is [LibraryAchievementCalc.award] for the metal (it is what the library sorts and
 * draws by) and `ui/components/AchievementVisuals.kt`'s `HighestAwardIcon` for the fill: filled for
 * `mastered` and `beaten-hardcore`, a ring for `completed` and `beaten-softcore`. The first is
 * asserted against the real function below; the second cannot be (it is a composable that draws),
 * so it is asserted as the table that file's own KDoc states.
 */
class AwardGradingTest {

    /** Every award kind RetroAchievements can send, and how the library grades its metal. */
    private val kinds = listOf("mastered", "completed", "beaten-hardcore", "beaten-softcore")

    @Test fun `the metal is the library's own tier, kind for kind`() {
        kinds.forEach { raw ->
            val kind = JourneyParsing.parseAwardKind(raw)!!
            val libraryTier = LibraryAchievementCalc.award(raw, hardcore = 1f)
            assertEquals(
                "$raw must wear the metal the library tile gives it",
                libraryTier == AchievementAward.GOLD,
                AwardGrading.goldTier(kind),
            )
        }
    }

    @Test fun `gold is the whole-game tier and silver is beaten`() {
        assertTrue(AwardGrading.goldTier(AwardKind.MASTERED))
        assertTrue(AwardGrading.goldTier(AwardKind.COMPLETED))
        assertFalse("beaten is the silver one", AwardGrading.goldTier(AwardKind.BEATEN))
    }

    /**
     * The fill, as `HighestAwardIcon` draws it: a completion is the hollow one, and it is the only
     * hollow one the journey can draw - both beaten kinds collapse into [AwardKind.BEATEN] on the
     * way in ([JourneyParsing.parseAwardKind]), and the filled form is `beaten-hardcore`'s.
     */
    @Test fun `only a completion is hollow`() {
        assertTrue(AwardGrading.filledDot(AwardKind.MASTERED))
        assertFalse("a completion is the ring, not the disc", AwardGrading.filledDot(AwardKind.COMPLETED))
        assertTrue(AwardGrading.filledDot(AwardKind.BEATEN))
    }

    /** Both metals are used, and no two awards make the same mark. */
    /**
     * Four awards, four marks: the metal is the tier (gold for the two that mean every achievement
     * in the game) and the fill is hardcore, so every combination of the two is spoken for and no
     * two awards can be confused for each other.
     */
    @Test fun `no two awards are drawn the same way`() {
        val marks = AwardGrading.ORDER.map { AwardGrading.goldTier(it) to AwardGrading.filledDot(it) }
        assertEquals("four awards, four distinct marks", 4, marks.toSet().size)
        assertEquals(true to true, AwardGrading.goldTier(AwardKind.MASTERED) to AwardGrading.filledDot(AwardKind.MASTERED))
        assertEquals(true to false, AwardGrading.goldTier(AwardKind.COMPLETED) to AwardGrading.filledDot(AwardKind.COMPLETED))
        assertEquals(false to true, AwardGrading.goldTier(AwardKind.BEATEN) to AwardGrading.filledDot(AwardKind.BEATEN))
        assertEquals(
            "beaten-softcore is the hollow silver circle",
            false to false,
            AwardGrading.goldTier(AwardKind.BEATEN_CASUAL) to AwardGrading.filledDot(AwardKind.BEATEN_CASUAL),
        )
    }

    @Test fun `a breakdown reads best award first`() {
        assertEquals(
            listOf(AwardKind.MASTERED, AwardKind.COMPLETED, AwardKind.BEATEN, AwardKind.BEATEN_CASUAL),
            AwardGrading.ORDER,
        )
        assertEquals("every kind is in the order", AwardKind.entries.toSet(), AwardGrading.ORDER.toSet())
    }

    @Test fun `an award names itself the way a counter and a tooltip need it`() {
        assertEquals("mastered", AwardGrading.name(AwardKind.MASTERED))
        assertEquals("completed", AwardGrading.name(AwardKind.COMPLETED))
        assertEquals("beaten", AwardGrading.name(AwardKind.BEATEN))
        assertEquals("Mastered", AwardGrading.title(AwardKind.MASTERED))
    }

    /**
     * The best of a bag of awards, which is what a single game's achievements board draws beside
     * its title: its awards arrive as the milestone medals the sweep passed, in whatever order the
     * milestones landed in, and "highest" has to mean [AwardGrading.ORDER]'s ranking rather than
     * whichever medal happens to be first in the list.
     *
     * A mastered game carries a beaten medal too - it was beaten on the way - so the order of the
     * arguments below is exactly the case that matters.
     */
    @Test fun `the highest award is the best one, not the first one`() {
        assertEquals(
            "a game beaten and then mastered is mastered",
            AwardKind.MASTERED,
            AwardGrading.highest(listOf(AwardKind.BEATEN, AwardKind.MASTERED)),
        )
        assertEquals(
            AwardKind.COMPLETED,
            AwardGrading.highest(listOf(AwardKind.BEATEN_CASUAL, AwardKind.COMPLETED)),
        )
        assertEquals(AwardKind.BEATEN, AwardGrading.highest(listOf(AwardKind.BEATEN)))
        assertEquals("nothing won, nothing named", null, AwardGrading.highest(emptyList()))
    }

    /** The slug is `AchievementsBoardData.highestAwardKind`'s own spelling, not the counter's word. */
    @Test fun `the slug is the site's own spelling, not the counter's word`() {
        assertEquals("mastered", AwardGrading.slug(AwardKind.MASTERED))
        assertEquals("completed", AwardGrading.slug(AwardKind.COMPLETED))
        assertEquals("beaten-hardcore", AwardGrading.slug(AwardKind.BEATEN))
        assertEquals("beaten-softcore", AwardGrading.slug(AwardKind.BEATEN_CASUAL))
    }
}
