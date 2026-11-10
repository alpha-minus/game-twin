package com.twinlore.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryAchievementCalcTest {

    @Test
    fun fraction_zero_total_is_zero() {
        assertEquals(0f, LibraryAchievementCalc.fraction(5, 0), 0.0001f)
    }

    @Test
    fun fraction_partial() {
        assertEquals(0.25f, LibraryAchievementCalc.fraction(1, 4), 0.0001f)
    }

    @Test
    fun fraction_clamped_to_one() {
        assertEquals(1f, LibraryAchievementCalc.fraction(7, 4), 0.0001f)
    }

    @Test
    fun award_gold_for_mastered_and_completed() {
        assertEquals(AchievementAward.GOLD, LibraryAchievementCalc.award("mastered", hardcore = 1f))
        assertEquals(AchievementAward.GOLD, LibraryAchievementCalc.award("completed", hardcore = 0f))
    }

    @Test
    fun award_silver_for_beaten() {
        assertEquals(AchievementAward.SILVER, LibraryAchievementCalc.award("beaten-hardcore", hardcore = 0.3f))
        assertEquals(AchievementAward.SILVER, LibraryAchievementCalc.award("beaten-softcore", hardcore = 0f))
    }

    @Test
    fun award_steam_falls_back_to_gold_at_full() {
        // No RA award kind (Steam / award-less): gold only once fully complete.
        assertEquals(AchievementAward.GOLD, LibraryAchievementCalc.award(null, hardcore = 1f))
        assertEquals(AchievementAward.NONE, LibraryAchievementCalc.award(null, hardcore = 0.99f))
    }

    @Test
    fun award_softcore_kinds_are_rings() {
        assertEquals(false, LibraryAchievementCalc.awardFilled("completed"))
        assertEquals(false, LibraryAchievementCalc.awardFilled("beaten-softcore"))
    }

    @Test
    fun award_hardcore_kinds_and_steam_are_filled() {
        assertEquals(true, LibraryAchievementCalc.awardFilled("mastered"))
        assertEquals(true, LibraryAchievementCalc.awardFilled("beaten-hardcore"))
        assertEquals(true, LibraryAchievementCalc.awardFilled(null))
    }

    @Test
    fun award_none_for_unknown_or_no_award() {
        assertEquals(AchievementAward.NONE, LibraryAchievementCalc.award("tried", hardcore = 0f))
    }
}
