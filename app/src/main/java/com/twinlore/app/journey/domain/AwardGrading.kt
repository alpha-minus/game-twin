package com.twinlore.app.journey.domain

import com.twinlore.app.journey.domain.model.AwardKind

/**
 * How an award is graded into marks: which metal it wears, and whether its dot is filled or hollow.
 *
 * The one authority for that pairing in this feature, and it is not this feature's own invention -
 * it mirrors what the rest of Game Twin has always drawn for a RetroAchievements `highestAwardKind`
 * (`ui/components/AchievementVisuals.kt`'s `HighestAwardIcon`, and the tier
 * `domain/AchievementSortInfo.kt`'s `LibraryAchievementCalc.award` sorts vaults by):
 *
 * | RA award kind     | metal  | dot    |
 * |-------------------|--------|--------|
 * | `mastered`        | gold   | filled |
 * | `completed`       | gold   | hollow |
 * | `beaten-hardcore` | silver | filled |
 * | `beaten-softcore` | silver | hollow |
 *
 * So the metal is the *tier* - gold for the two awards that mean every achievement in the game, and
 * silver for beaten - and the fill is hardcore. The chart used to say something else: it put a
 * *silver* dot on a completion and left the hollow one for beaten, which read the two lesser awards
 * back to front against the library tile, the RA tab and the vault drawer showing the same games on
 * the same device. That is the reversal this exists to make impossible to reintroduce.
 *
 * `AwardKind` carries all four cases now. It used to fold the two beaten kinds into one and draw
 * them both filled, which lost the distinction the table above makes and the site's own
 * progression row shows. So the metal is the
 * tier and the fill is hardcore, uniformly, in both tiers.
 *
 * Pure Kotlin, no Compose: the colours themselves belong to the theme
 * ([com.twinlore.app.journey.ui.chart.ChartStyle]'s `gold` and `silver`), and what is worth pinning
 * in a test without a canvas is which award gets which.
 */
object AwardGrading {

    /** Whether [kind] wears the gold tier: mastery and completion do, the beaten pair does not. */
    fun goldTier(kind: AwardKind): Boolean =
        kind == AwardKind.MASTERED || kind == AwardKind.COMPLETED

    /** Whether [kind]'s dot is a filled disc rather than an outline: the hardcore half of each tier. */
    fun filledDot(kind: AwardKind): Boolean =
        kind == AwardKind.MASTERED || kind == AwardKind.BEATEN

    /** The award in a word, lower case, as a counter names it: "3 mastered". */
    fun name(kind: AwardKind): String = when (kind) {
        AwardKind.MASTERED -> "mastered"
        AwardKind.COMPLETED -> "completed"
        AwardKind.BEATEN -> "beaten"
        // The site's own wording for beaten-softcore, so the chart and the progression row agree.
        AwardKind.BEATEN_CASUAL -> "beaten (casual)"
    }

    /** The award as a sentence starts it: "Mastered". */
    fun title(kind: AwardKind): String = name(kind).replaceFirstChar { it.uppercase() }

    /** The site's own slug for a kind, which is what `AchievementsBoardData.highestAwardKind` is. */
    fun slug(kind: AwardKind): String = when (kind) {
        AwardKind.MASTERED -> "mastered"
        AwardKind.COMPLETED -> "completed"
        AwardKind.BEATEN -> "beaten-hardcore"
        AwardKind.BEATEN_CASUAL -> "beaten-softcore"
    }

    /** Every kind, in the order a breakdown reads them: the best award first. */
    val ORDER: List<AwardKind> = listOf(
        AwardKind.MASTERED, AwardKind.COMPLETED, AwardKind.BEATEN, AwardKind.BEATEN_CASUAL,
    )

    /**
     * The best of [kinds], or null when there are none - [ORDER]'s own ranking, asked the way a
     * surface that has a bag of awards asks it.
     *
     * A single game's journey is the caller that needs this: its awards arrive as the milestone
     * medals the sweep passes ([com.twinlore.app.journey.domain.model.Medal]), one per kind it
     * reached, and what the achievements board draws beside the title is the highest of them.
     */
    fun highest(kinds: Iterable<AwardKind>): AwardKind? = ORDER.firstOrNull { it in kinds }
}
