package com.twinlore.app.domain

/**
 * Award tier for the trophy shown at the end of a vault's progress bar — mirrors
 * RetroAchievements' own award badge. GOLD = mastered or completed (every
 * achievement earned); SILVER = "beaten" (progression done, not everything).
 */
enum class AchievementAward { NONE, SILVER, GOLD }

/**
 * Per-vault achievement completion used for sorting and for the grid-item progress bar.
 * Both fractions are 0f..1f. For Steam (single mode) both layers carry the same value.
 * Unlinked vaults are absent from the map (treated as 0 by callers).
 */
data class AchievementSortInfo(
    val softcoreFraction: Float,
    val hardcoreFraction: Float,
    /**
     * RA highest award kind ("mastered" / "completed" / "beaten-hardcore" /
     * "beaten-softcore"); null for Steam-only or award-less vaults.
     */
    val raAwardKind: String? = null,
)

/** Pure helpers for achievement fractions and award tiers. */
object LibraryAchievementCalc {

    fun fraction(unlocked: Int, total: Int): Float =
        if (total > 0) (unlocked.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f

    /**
     * Award tier for the bar-end trophy, matching RA's award badge:
     * gold for mastered/completed, silver for beaten. Steam (and any vault with
     * no RA award kind) falls back to gold once fully complete ([hardcore] >= 1).
     */
    fun award(raAwardKind: String?, hardcore: Float): AchievementAward = when (raAwardKind) {
        "mastered", "completed" -> AchievementAward.GOLD
        "beaten-hardcore", "beaten-softcore" -> AchievementAward.SILVER
        null -> if (hardcore >= 1f) AchievementAward.GOLD else AchievementAward.NONE
        else -> AchievementAward.NONE
    }

    /**
     * Whether the trophy is a filled disc or a ring, as RA's badge and the
     * achievements view draw it: a softcore award (completed, beaten-softcore)
     * is a ring. Everything else, Steam's fallback gold included, is filled.
     */
    fun awardFilled(raAwardKind: String?): Boolean =
        raAwardKind != "completed" && raAwardKind != "beaten-softcore"
}
