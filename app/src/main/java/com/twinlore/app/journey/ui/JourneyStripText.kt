package com.twinlore.app.journey.ui

import com.twinlore.app.journey.domain.AwardGrading
import com.twinlore.app.journey.domain.ChartLayout
import com.twinlore.app.journey.domain.JourneyStats
import com.twinlore.app.journey.domain.TickGenerator
import com.twinlore.app.journey.domain.frameUpToElapsed
import com.twinlore.app.journey.domain.pluralize
import com.twinlore.app.journey.domain.model.AwardKind
import com.twinlore.app.journey.domain.model.AwardTally
import com.twinlore.app.journey.domain.model.JourneyFrame
import com.twinlore.app.journey.domain.model.JourneyMode

/**
 * Every word the overview strip says, as pure text - all four labels included, so that no chip's
 * name lives in one strip's file and not the other's.
 *
 * There are two strips now - the Compose chips along the top of the player, and the one the video
 * and image exports draw into the frame - and they have to say the same thing about the same
 * journey or the picture a user shares stops matching the picture they watched. Neither owns the
 * wording: this does, and both read it.
 *
 * Pure Kotlin, no Compose and no canvas, so what a counter reads at a given point of playback is
 * unit tested without either.
 */
internal object JourneyStripText {

    /**
     * One labelled counter: what it is, and what it currently reads.
     *
     * [rivalIndex] is set only on the per-player lines of a compare chart: -1 for the main player,
     * 0-based for a rival, matching [PlayerLine.rivalIndex] and therefore the slot the chart draws
     * that player's curve from. It is here so a *drawn* strip can colour a player's line the way
     * the on-screen compare row does; null means an ordinary counter, drawn in the strip's own ink.
     * The colours themselves stay out of this file - it owns the words, not the palette.
     */
    data class Chip(
        val label: String,
        val value: String,
        val rivalIndex: Int? = null,
        /**
         * The award breakdown this chip carries, drawn as a dot glyph and a count per kind rather
         * than as words. Empty on a chip that has no awards to show.
         *
         * Every surface that draws a chip draws these after its value (the Compose strip with a
         * `Canvas` dot, the exported strip with a drawn circle), because "which awards" is a fact
         * about a shape and a colour on this chart and spelling it out was costing most of a
         * handheld's width. [spoken] is the same fact in words, for a screen reader and for a test.
         */
        val awards: List<AwardPart> = emptyList(),
        /**
         * Whether [awards]' dots may draw their count. True everywhere but a single-game compare
         * row - see [awardCountsShown] for why - so an ordinary chip never has to think about it.
         *
         * Carried on the chip rather than threaded as a mode through the drawing code: the chip is
         * the one thing every surface that draws [awards] already has (`ExportStrip` never sees a
         * [JourneyFrame] or a [JourneyMode], only the chips [compareChips] hands it), and a chip
         * that says "here are the dots, and here is whether they may count themselves" keeps that
         * file free of a fact about the feature it is not the one that knows.
         */
        val awardCountsShown: Boolean = true,
        /**
         * Whether this chip's VALUE is a game's title, drawn in the chart's accent rather than the
         * strip's ordinary bright ink - false everywhere but [gameHeaderChips]'s own name chip.
         *
         * Carried on the chip for the same reason [rivalIndex] and [awardCountsShown] are: colour
         * is a fact about which chip this is, and `ExportStrip` draws whatever chips it is handed
         * without knowing a `GameHeader` or a [JourneyMode] exists. On screen the title takes its
         * colour from `GameHeaderBar`'s own `iconRing` parameter (`PlaybackHeader`'s `Text(title,
         * ... color = iconRing)`), which for a single game is always the accent - a fact that lived
         * only on the screen's side until now, so the exported title drew in the strip's ordinary
         * ink instead of standing out the way the game's name does over the chart.
         */
        val isTitle: Boolean = false,
        /**
         * Where this player stands ("Rank #4"), drawn at the END of their row rather than inside
         * [value] - null on every chip that is not a compare row's player, and on a player whose
         * rank nobody knows.
         *
         * Its own field for the same reason [rivalIndex] and [awardCountsShown] are, and for one
         * more: the rank has to be RIGHT-ALIGNED, which a string fused into [value] cannot be. On
         * screen the compare row lays out name, facts, award dots in the flexible middle, and the
         * rank owning the line's right edge (`JourneyPlayer.PlayerCompareLine`). The exported row
         * used to fold the rank into the same run as the score ("240 pts · 12 unlocks · Rank #4"),
         * which is a different string from the one the watcher saw and long enough that the column
         * ellipsized it - the row read "alphami..." and "R...". A chip that carries the rank apart
         * from the facts is what lets the drawing put it where the screen puts it.
         */
        val rank: String? = null,
        /**
         * This player's completion of the charted game, 0 to 100, with the bar it implies - set
         * only on a single-game compare row, exactly [PlayerLine.percent]'s rule (see its doc for
         * when it is null). On the chip for the same reason [rank] is: the drawn strip
         * (`ExportStrip.drawComparePlate`) sees chips, not frames, and the watched row and the
         * shared picture have to say the same number.
         */
        val percent: Int? = null,
        /** The hardcore share of [percent], the bar's gold layer. See [PlayerLine.hardcorePercent]. */
        val hardcorePercent: Int = 0,
        /**
         * Which line of a stacked plate this chip belongs to, 0-based - 0 on every chip that is
         * one counter on a shared baseline, which is all of them but a running span's two-line
         * header ([spanHeaderChips]).
         *
         * A data-borne tell, the way [rivalIndex] is: `ExportStrip` reads it to decide whether the
         * plate is one baseline or a stack, so nothing has to hand that file a mode it otherwise
         * knows nothing about. It is deliberately a LINE NUMBER rather than a "two lines please"
         * flag, because what the drawing needs to know is which chip goes where; a boolean would
         * leave the file guessing the order from the list's own indices.
         */
        val line: Int = 0,
        /**
         * How much room this chip claims whatever it currently says, in SCREEN dp - 0 for a chip
         * that simply takes its own width.
         *
         * A counter that moves as its own digits change drags everything after it along: "9 of 69
         * achievements" becoming "10 of 69" pushed the points, the cost and the bar a character to
         * the right, several times a second, for the whole of a replay (user report). A slot pins
         * each fact's x whatever the number in it is, and pins the plate's width with them, so the
         * bar and the award at the end of the line stop sliding too.
         *
         * Dp rather than pixels because it is a screen measurement that both surfaces read: the
         * player lays the run out in a `Modifier.width(slot.dp)`, the export multiplies by
         * [com.twinlore.app.journey.export.ExportStrip.DENSITY]. One number, one layout.
         *
         * "Enough for the longest it can get" is the rule for picking one - see the GAME_FACT_*
         * and SPAN_FACT_* constants in `JourneyPlayer`. A slot narrower than its text ellipsizes
         * rather than overflowing, which is a visible cost and the reason they are generous.
         */
        val slotDp: Float = 0f,
    ) {
        /** Everything this chip says, in words: what a screen reader is given, dots included. */
        val spoken: String
            get() = listOfNotNull(value, awardsWords(awards), rank)
                .filter { it.isNotEmpty() }
                .joinToString(" · ")
    }

    /** One kind of award and how many of them a readout has counted. */
    data class AwardPart(val kind: AwardKind, val count: Int)

    /**
     * The awards a tally holds, best first, with the kinds nobody earned left out.
     *
     * Leaving them out is the point: a journey with three masteries and nothing else says "3
     * mastered" rather than "3 mastered, 0 completed, 0 beaten", and a row of zeroes is exactly
     * what the single "Awards 3" counter was replaced to avoid.
     */
    fun awardParts(tally: AwardTally): List<AwardPart> =
        AwardGrading.ORDER.mapNotNull { kind ->
            tally[kind].takeIf { it > 0 }?.let { AwardPart(kind, it) }
        }

    /** The breakdown in words: "3 mastered · 1 completed". Empty when nothing has been won. */
    fun awardsWords(parts: List<AwardPart>): String =
        parts.joinToString(" · ") { "${it.count} ${AwardGrading.name(it.kind)}" }

    /**
     * Whether an award dot may draw the number beside it.
     *
     * On a span the count is the fact a row exists to carry - "20 mastered" says something a dot's
     * colour alone cannot. On one game a player earns at most one award of a kind, so the number
     * beside every dot can only ever read "1": a constant, spent on width, on the narrowest surface
     * in the feature. [awardsWords] still spells the count out for a screen reader either way - a
     * spoken "1 mastered" costs nothing and loses nothing a sighted reader would not already know
     * from the count not being there.
     */
    fun awardCountsShown(mode: JourneyMode): Boolean = mode != JourneyMode.GAME

    /**
     * The awards counter as a chip: the dots when there are any, and the plain zero when there are
     * not.
     *
     * A journey that has not reached its first award still needs the counter to be *there* - a chip
     * that appeared partway through the replay would move every counter beside it - so the empty
     * case keeps the label and reads "0".
     */
    fun awardsChip(tally: AwardTally): Chip {
        val parts = awardParts(tally)
        if (parts.isEmpty()) return Chip(AWARDS_LABEL, "0")
        // No label: each dot carries its own word ("3 mastered"), and "Awards" in front of them was
        // the widest thing on the row saying what the row already said.
        return Chip(label = "", value = "", awards = parts)
    }

    /**
     * The two things about a journey the strip cannot work out from the frame: where the player
     * stands when it ends, and where each player of a compare chart stands on the game.
     *
     * One holder rather than two parameters because it travels a long way - the export request
     * carries it through the video and image exporters to the frame the encoder draws - and a
     * shared picture that quietly lost the rank the watcher saw is the failure this exists to
     * prevent.
     */
    data class Ranks(
        /**
         * Where the signed-in player stands when the journey ends.
         *
         * Carried, and currently drawn nowhere: the end row is four counters about the journey
         * itself, and a rank is a fact about the whole site. Where a rank is still shown is on a
         * compare row, from [perPlayer], beside the player it ranks. Kept rather than deleted
         * because the ViewModel resolves it either way and the request that carries it is what a
         * closing card would read; a future end state that wants it does not have to rethread the
         * plumbing. Nothing else depends on it being non-null.
         */
        val closing: RankChip? = null,
        val perPlayer: Map<String, Int> = emptyMap(),
    )

    /** What the running total of points is called. */
    const val POINTS_LABEL = "Points"

    /** What the running count of awards is called. */
    const val AWARDS_LABEL = "Awards"

    /** How many games a span covered. */
    const val GAMES_LABEL = "Games"

    /** How many achievements a journey came to. */
    const val ACHIEVEMENTS_LABEL = "Achievements"

    /** The journey's own date, on the single-game header. */
    const val DATE_LABEL = "Date"

    /** "Elapsed" on a race chart, where the axis is time since each player's first unlock. */
    fun whenChipLabel(layout: ChartLayout): String = if (layout.raceMode) "Elapsed" else "Date"

    /**
     * What the date chip reads at this point in the replay.
     *
     * Three cases, and the two unusual ones both exist because a plain date was saying nothing. A
     * finished journey names the whole window rather than freezing on its last day, which is the
     * number the viewer is left looking at. A short one - a session or two, see
     * [TickGenerator.showsTimeOfDay] - carries the clock time, because every unlock in it lands on
     * the same date and a date-only readout would not move for the entire replay.
     *
     * The two combine rather than one overriding the other: a weekend journey's end state has to
     * read "Feb 12, 2026 09:14 to Feb 13, 2026 22:40", because the same rule that gives a short
     * journey a clock while it plays is the rule that makes "Feb 12, 2026 to Feb 12, 2026" a
     * useless thing to finish on.
     */
    fun whenLabel(
        frame: JourneyFrame,
        layout: ChartLayout,
        journeyDateMillis: Long?,
        completed: Boolean,
    ): String {
        val firstUnlock = frame.main.unlocks.firstOrNull()?.earnedAtMillis ?: 0L
        if (layout.raceMode) {
            // Race mode already reads as elapsed time, so the end state is simply all of it.
            if (completed) return TickGenerator.fmtElapsedApprox(layout.eMaxMillis)
            return journeyDateMillis?.let { TickGenerator.fmtElapsedApprox(it - firstUnlock) } ?: "-"
        }
        val withTime = TickGenerator.showsTimeOfDay(layout.axisT1 - layout.axisT0)
        val format: (Long) -> String =
            if (withTime) TickGenerator::fmtDateTime else TickGenerator::fmtDate
        if (completed) return "${format(layout.axisT0)} to ${format(layout.axisT1)}"
        if (journeyDateMillis == null) return "-"
        return format(journeyDateMillis)
    }

    /**
     * How many achievements the charted game has in total, earned or not - the denominator the
     * single-game counter divides by, and 0 when nothing knows (a chart with no game on it, or one
     * built from a snapshot cached before the field existed).
     */
    fun totalAchievements(frame: JourneyFrame): Int =
        frame.main.games.values.firstOrNull()?.totalAchievements ?: 0

    /**
     * How many points the charted game is worth in total, earned or not - the denominator the
     * single-game header's points line divides by, and 0 when nothing knows (a chart with no game
     * on it, or one built from a snapshot cached before the field existed).
     *
     * A sibling of [totalAchievements] rather than a second lookup: `GameRef.totalPoints` is filled
     * in by [com.twinlore.app.journey.domain.JourneyBuilder] the same way `totalAchievements` is,
     * off the snapshot's full achievement list that is already in memory to build the frame.
     */
    fun totalPoints(frame: JourneyFrame): Int =
        frame.main.games.values.firstOrNull()?.totalPoints ?: 0

    // ---- the single-game header ----

    /**
     * The header a single-game journey carries instead of a row of counters: the game, how far
     * through its achievement list the replay has got, what that is worth, and where in time it is.
     *
     * One object rather than four strings built twice, because two surfaces draw it - the bar over
     * the player and the strip an exported frame carries - and a shared picture that said something
     * different from the picture that was watched is the whole reason the wording lives in this
     * file. The percentage is here too: it is the same fraction the achievement line states, and
     * having the bar compute its own would be two answers to one question.
     */
    data class GameHeader(
        val title: String,
        /** Media-relative already resolved, or null for a game with no icon. */
        val iconUrl: String?,
        /** "All 42" once every achievement is in, "12 of 42" while it is not. */
        val achievements: String,
        /** "120 of 430". */
        val points: String,
        /**
         * Where in time the replay is: the journey's own date while it plays, and what the game cost
         * once it has landed ("Mastered in 42h 10m", or "Played 42h 10m" with no award to name).
         */
        val date: String,
        /**
         * What the exported strip labels [dateValue] with: "Date" while playing, and at the end the
         * award the hours belong to ("Mastered in") or plain "Played".
         */
        val dateLabel: String,
        /** The same fact as [date] with the label taken off the front, for a labelled strip panel. */
        val dateValue: String,
        /**
         * What the game cost, once it has landed - "Mastered in 42h 10m" - and null for every frame
         * before that, and for a game whose hours nobody knows.
         *
         * [date] answers with the running date instead when there is no cost yet; this one does not,
         * because [countsLine] wants the cost and specifically NOT the date. See its own doc.
         */
        val cost: String?,
        /** How much of the achievement list is unlocked at this point, 0 to 100. */
        val percent: Int,
        /**
         * How much of it was taken in hardcore, on the same denominator as [percent] - the gold
         * share of the header's bar, over the silver of the whole of it.
         *
         * Zero for a caller that has not counted them, which is the exported strip: it draws the
         * header as labelled panels and has no bar to overlay. Only the live bar reads this.
         */
        val hardcorePercent: Int = 0,
    ) {
        /** The achievement line as the bar reads it out. */
        val achievementsLine: String get() = "$achievements achievements"

        /** The points line as the bar reads it out. */
        val pointsLine: String get() = "$points points"

        /**
         * The header's second line, whole: how far through the achievement list the replay has got,
         * what that is worth, and - once it has landed - what it cost.
         *
         * Under a first line that is the game's own name and nothing else. The date rode on the
         * title's line for a while, which left a title ellipsizing against a date on the widest line
         * of the panel while the line below it carried two short counts.
         *
         * No RUNNING date on it at all (user request). Where the sweep is in time is the corner
         * readout's job on a followed chart, and it was the same fact twice on the one chart that
         * shows both. [cost] is a different fact - what the game took, which nothing else says - so
         * it stays, and the line is two counts until the journey lands.
         */
        val countsLine: String get() = listOfNotNull(achievementsLine, pointsLine, cost).joinToString(" · ")

        /**
         * The header's two lines as one sentence, for a screen reader: the game and how far through
         * its list the replay is, then what that is worth and when.
         */
        val spoken: String get() = "$title, $achievementsLine, $pointsLine, $date, $percent%"
    }

    /**
     * The header for [frame] at this point of the replay. [revealed] and [points] come from
     * `HudAtProgress`, so both move with the sweep and both land on the journey's own totals at the
     * end.
     *
     * The achievements denominator is the game's own achievement count when a snapshot knows it,
     * and the player's own unlock count when nothing does: "12 of 0" is worse than saying nothing.
     *
     * The points denominator follows the same rule off [totalPoints] rather than off
     * `unlocks.sumOf { it.points }`, which is what a player has EARNED - dividing by it was the bug
     * a device found: a finished game's numerator and that denominator are the same running total,
     * so every completed single-game header read "342 of 342" while the achievements grid and the
     * RetroAchievements drawer both read "342 of 656" for the very same game. The unlock sum is kept
     * only as the fallback for a snapshot cached before `GameRef.totalPoints` existed, exactly as
     * [totalAchievements]'s own fallback is the unlock count rather than a made-up zero.
     */
    fun gameHeader(
        frame: JourneyFrame,
        layout: ChartLayout,
        journeyDateMillis: Long?,
        revealed: Int,
        points: Int,
        completed: Boolean = false,
        /**
         * What the sweep has won, for the one line the end state adds: the highest of these names
         * the hours ("Mastered in 42h 10m"). Empty is the ordinary case for most of a replay.
         */
        awards: AwardTally = AwardTally(),
        /** How many of [revealed] were hardcore. See [GameHeader.hardcorePercent]. */
        hardcore: Int = 0,
    ): GameHeader {
        val game = frame.main.games.values.firstOrNull()
        val known = totalAchievements(frame)
        val total = if (known > 0) known else frame.main.unlocks.size
        // The two numbers come from different places - the denominator off a validator-matched
        // snapshot in Room, the numerator off the live unlock list - so a game whose achievement
        // list has been trimmed since the snapshot was written could print "43 of 40". A fraction
        // over one is not a thing a progress counter can mean; the honest reading is that this
        // player has all of them.
        val shown = revealed.coerceAtMost(total)
        val knownPoints = totalPoints(frame)
        // Same fallback shape as the achievements denominator above: the game's own point total off
        // a validator-matched snapshot when one is known, and the running earned total - today's
        // behaviour - when nothing is, so an old cached journey keeps reading "N of N" instead of
        // "of 0".
        val totalPointsValue = if (knownPoints > 0) knownPoints else frame.main.unlocks.sumOf { it.points }
        // The same guard as [shown] above, for the same reason: the denominator comes off a
        // validator-matched snapshot and the numerator off the live sweep, so a stale total must
        // not print a fraction over one.
        val shownPoints = points.coerceAtMost(totalPointsValue)
        // Never the completed form ("X to Y") while it plays: this is one of two lines on a compact
        // header, and the running date is already the day the sweep is on.
        val running = whenLabel(frame, layout, journeyDateMillis, completed = false)
        val cost = if (completed) playedLine(frame, awardParts(awards).firstOrNull()?.kind) else null
        val shownHardcore = hardcore.coerceIn(0, shown)
        return GameHeader(
            title = game?.title ?: "-",
            iconUrl = game?.iconPath?.let(JourneyUrls::media),
            achievements = if (total > 0 && shown >= total) "All $total" else "$shown of $total",
            points = "$shownPoints of $totalPointsValue",
            date = cost?.let { "${it.first} ${it.second}" } ?: running,
            dateLabel = cost?.first ?: DATE_LABEL,
            dateValue = cost?.second ?: running,
            cost = cost?.let { "${it.first} ${it.second}" },
            percent = if (total <= 0) 0 else (shown * 100) / total,
            hardcorePercent = if (total <= 0) 0 else (shownHardcore * 100) / total,
        )
    }

    /**
     * What a finished single-game journey cost, as a label and a value: the award it came to, and
     * how long the player had the game open.
     *
     * "Mastered in 42h 10m" - the award off the sweep's own tally, the hours out of
     * RetroAchievements' `UserTotalPlaytime`. The date it replaces has nothing left to say at the
     * end (the sweep has stopped on the last day, which the chart's own axis already ends on), and
     * an award named next to what it took is what a finished single game is actually about. With no
     * award to name it is the plain "Played 42h 10m".
     *
     * How long the run of unlocks itself took ("21 days, 4 hours") used to ride here too. It is a
     * fact about the axis rather than about the player, so it is drawn under the axis now - see
     * `JourneyChartRenderer`'s span label - and the header is left with one short phrase.
     *
     * Null when nothing knows the hours: a game cached before the field existed deserializes
     * without it and there is no second call to be made for it, so the header simply keeps its date
     * rather than inventing an hour or repeating the axis.
     */
    private fun playedLine(frame: JourneyFrame, award: AwardKind?): Pair<String, String>? {
        val seconds = frame.main.games.values.firstOrNull()?.playtimeSeconds
        if (seconds == null || seconds <= 0) return null
        val label = award?.let { "${AwardGrading.title(it)} in" } ?: PLAYED_LABEL
        return label to fmtPlaytime(seconds)
    }

    /** What the end of a single-game header calls the hours when no award named them. */
    const val PLAYED_LABEL = "Played"

    /**
     * RetroAchievements' `UserTotalPlaytime`, which arrives in SECONDS, as the rest of this app
     * shows it: "42h 10m", or "10m" for a game that was never open for an hour.
     *
     * The same shape the RA drawer's own readout uses (`RetroAchievementDrawer` divides the very
     * same field by 60 and prints "${h}h ${m}m"), so the two never disagree about a game both are
     * showing.
     */
    fun fmtPlaytime(seconds: Int): String {
        val minutes = seconds / 60
        val hours = minutes / 60
        val rest = minutes % 60
        return if (hours <= 0) "${rest}m" else "${hours}h ${rest}m"
    }

    /**
     * A stretch of time as the chart's axis names it: "21 days, 4 hours".
     *
     * Drawn centred under the x axis (`JourneyChartRenderer`), which is what it describes: how much
     * time the axis it sits under covers. It was a clause on the finished header's cost line, where
     * it competed with the hours the game was open for - two durations in one sentence, one of them
     * about the player and one about the axis.
     *
     * Rounded to the unit a person would say: hours inside a day,
     * whole days under two months, months under a year, then years with any leftover months.
     * "364 days, 2 hours" was pedantically true and useless under an axis - it reads "1 year"
     * now, and the hours never ride along once there is a day to talk about.
     */
    fun fmtSpanDaysHours(millis: Long): String {
        val totalHours = (millis / 3_600_000L).coerceAtLeast(0)
        val days = (totalHours / 24).toInt()
        val hours = (totalHours % 24).toInt()
        if (days <= 0) return pluralize(hours, "hour")
        if (days < 60) return pluralize(days, "day")
        val totalMonths = Math.round(days / AVERAGE_MONTH_DAYS)
        if (totalMonths < 12) return pluralize(totalMonths, "month")
        val years = totalMonths / 12
        val months = totalMonths % 12
        return if (months == 0) pluralize(years, "year")
        else "${pluralize(years, "year")}, ${pluralize(months, "month")}"
    }

    /** Gregorian mean month length; keeps "364 days" rounding into "1 year", not "11 months". */
    private const val AVERAGE_MONTH_DAYS = 30.437f

    /**
     * The same header as strip chips, for the exported frame.
     *
     * The export lays the header's facts on one baseline across a single plate, behind the game's
     * icon, with the bar - and the percentage and the award under it - in the block at the plate's
     * far end (`ExportStrip.drawExportStrip`). That is where the on-screen [GameHeader] bar puts
     * all three too, so the title carries the game's name and nothing else.
     *
     * It used to carry more, and by the time the plate grew a trailing block of its own that was
     * the same fact twice on every frame of every single-game export: the percentage rode the
     * title as its value, and the highest award rode it as words ("Kirby 100% 1 mastered ... 100%").
     * The screen writes the percentage once and draws the award as a bare circle, so the plate now
     * does the same - see `ExportStrip.BarState.award` for the dot the block draws instead.
     *
     * The name is a chip's VALUE and not its label because a label is the dim, small half of a
     * counter: on screen the game's name is the bright line of the header, and it has to be the
     * bright run here too. It also carries [Chip.isTitle], because "bright" undersold it - on
     * screen the title is not the strip's ordinary ink but the chart's own accent
     * (`GameHeaderBar` hands `PlaybackHeader` its `iconRing`, which for a single game is always
     * `style.accent`, as the title's colour) - and the export used to draw it in the strip's
     * ordinary ink for want of a way to say "this one is the title" without handing `ExportStrip`
     * a `GameHeader` it has no other reason to know about.
     */
    fun gameHeaderChips(header: GameHeader, slots: FactSlots): List<Chip> = listOfNotNull(
        Chip("", header.title, isTitle = true, line = 0),
        // One chip per fact, each in a fixed slot, which is what the screen's own header draws
        // (`PlaybackHeader.factSlots`). It was one fused run for a while, and one run measured at
        // its natural width moves: "9 of 69 achievements" becoming "10 of 69" shifted the points,
        // the cost and the bar at the end of the row a character to the right, several times a
        // second, for the whole replay (user report). Slots pin each fact's x, and with them the
        // plate's width, so the trailing bar stops sliding too.
        //
        // Before that it was label/value counters - "Achievements 50 of 69 · Points 342 of 656" -
        // with labels the screen never writes. The wording here is the screen's own.
        Chip("", header.achievementsLine, line = 1, slotDp = slots.achievements),
        Chip("", header.pointsLine, line = 1, slotDp = slots.points),
        // Only once it has landed. Null while it plays rather than an empty chip in a reserved
        // slot, so a running header does not carry a fact-wide hole where nothing is written yet.
        header.cost?.let { Chip("", it, line = 1, slotDp = slots.cost) },
    )

    /**
     * How much room each of a header's facts claims, in screen dp - the player's own slot widths,
     * handed in so the one place they are chosen is the one file that draws them on screen.
     *
     * A parameter rather than constants in this file because they are a fact about layout, and this
     * file is the wording. `JourneyPlayer` owns them (GAME_FACT_*, SPAN_FACT_*) and both surfaces
     * read them from the chips these build.
     */
    data class FactSlots(val achievements: Float, val points: Float, val cost: Float = 0f)

    // ---- the two-line playback header ----

    /**
     * A running span's second line: how many games the sweep has been over, how many achievements
     * it has revealed and what they are worth, in that order, with the award dots drawn after them.
     *
     * UI feedback: the line leads with where in time the sweep is - the date-time the first line
     * used to carry - then the achievements and the points. The game count is not a fact of this
     * line any more: it rides the title as "3 - Zelda" ([numberedTitle]), so the name of the game
     * and which game it is are one string, and the date sits with the counts it moves alongside.
     *
     * Everything is abbreviated to the shortest honest form ("pt", not "point") because the line
     * has to hold the date, two counts and up to three award dots inside a phone's width.
     */
    fun spanPlaybackFacts(whenLabel: String, achievements: Int, points: Int): String = listOf(
        whenLabel,
        pluralize(achievements, "achievement"),
        pluralize(points, "pt"),
    ).joinToString(" · ")

    /**
     * The running span's title: the running game count in front of the game under the sweep,
     * "3 - Zelda" when the sweep has entered its third game.
     */
    fun numberedTitle(games: Int, title: String): String = "$games - $title"

    /**
     * The whole of a running span's two-line header, as one object both surfaces read.
     *
     * The same trick [GameHeader] plays for a single game, for the same reason: the player draws
     * this as `PlaybackHeader` (icon, numbered title, then the facts and the award dots) and an
     * exported frame draws it as a stacked plate, and a shared picture that said something
     * different from the picture that was watched is what this file exists to prevent. It used to
     * be three separate calls at the player's own call site ([numberedTitle], [spanPlaybackFacts],
     * [awardParts]) with the export making up a row of label/value counters of its own - which is
     * exactly how the two surfaces came to disagree about the shape as well as the wording.
     *
     * [gameTitle] is the game under the sweep, and null is the honest answer for a sweep that is
     * between games or on a chart whose track has no game there: the title then falls back to
     * [GAMES_LABEL], which is what the player's own header does with it.
     */
    data class SpanHeader(
        /** "3 - Zelda" - the running game count in front of the game under the sweep. */
        val title: String,
        /** "Aug 12 · 12 achievements · 240 pts" - the whole line in words, for a screen reader. */
        val facts: String,
        val awards: List<AwardPart>,
        /** "12 achievements", the first of the two slots both surfaces draw. */
        val achievementsLine: String,
        /** "240 pts", the second. */
        val pointsLine: String,
    )

    fun spanHeader(
        whenLabel: String,
        games: Int,
        gameTitle: String?,
        achievements: Int,
        points: Int,
        awards: AwardTally,
    ): SpanHeader = SpanHeader(
        title = numberedTitle(games, gameTitle ?: GAMES_LABEL),
        facts = spanPlaybackFacts(whenLabel, achievements, points),
        awards = awardParts(awards),
        achievementsLine = pluralize(achievements, "achievement"),
        pointsLine = pluralize(points, "pt"),
    )

    /**
     * The same header as stacked chips, for the exported frame - the title on the first line, the
     * facts and the award dots on the second.
     *
     * One chip per LINE rather than one chip per counter, which is the shape change this is: the
     * export used to draw a running span as five label/value counters on one baseline ("Games 3 ·
     * Achievements 12 · Points 240 · ... · Date Aug 12"), a row of boxes the watcher never saw.
     * The screen draws a game's name over a line of facts, so the plate does too - through
     * [Chip.line], which is what tells `ExportStrip` to stack rather than to lay one baseline.
     *
     * The title is a chip's VALUE and carries [Chip.isTitle] for the reason [gameHeaderChips]'s own
     * title does: it is the bright, large run of the header, drawn in the icon's ring colour rather
     * than in the strip's ordinary ink. On a span that ring is the game's own series colour, so the
     * title changes colour with the picture behind it - exactly as `PlaybackHeader`'s `iconRing`
     * does on screen.
     *
     * The facts are one fused run with an empty label, not a label/value pair, because that is what
     * the screen draws: `PlaybackHeader`'s second row is a single `bodySmall` `Text`. Splitting it
     * into invented labels is what the plate was doing before.
     */
    fun spanHeaderChips(header: SpanHeader, slots: FactSlots): List<Chip> = listOf(
        Chip("", header.title, isTitle = true, line = 0),
        // The screen's two slots, in its order, and NO DATE - which is what `PlayerOverview` has
        // been drawing all along while this built [SpanHeader.facts] with one in front ("Apr 17,
        // 2026 · 15 achievements · 61 pts"). The export was the only surface showing it, and the
        // date belongs to the chart's own corner readout (user request). [SpanHeader.facts] is
        // still what a screen reader is given.
        Chip("", header.achievementsLine, line = 1, slotDp = slots.achievements),
        Chip("", header.pointsLine, awards = header.awards, line = 1, slotDp = slots.points),
    )

    // ---- the end state ----

    /** How long the player kept coming back, at its longest. */
    const val STREAK_LABEL = "Longest streak"

    /**
     * A streak as the end state says it, or a dash for a journey that never had one worth naming.
     *
     * A run of one day is not a run - it is the fact that the journey happened at all, which every
     * other counter on the row already says - so it reads as a dash rather than as "1 day".
     */
    fun streakValue(days: Int): String =
        if (days < MIN_STREAK_CHIP_DAYS) "-" else pluralize(days, "day")

    /**
     * The shortest run the end state is willing to call a streak.
     *
     * Lower than the chart's own [JourneyStats.MIN_STREAK_DAYS], which governs the flames: a flame
     * on every second day would be noise on the chart, while "came back the next day" is still
     * worth naming in a summary. One day is not, because it is not a run.
     */
    const val MIN_STREAK_CHIP_DAYS = 2

    /** The longest streak this journey ran to, in days. See [JourneyStats.longestStreakDays]. */
    fun streakDays(frame: JourneyFrame): Int = JourneyStats.longestStreakDays(frame.main.unlocks)

    /**
     * What a finished span says: four counters, always the same four, in the same order.
     *
     * How many games, how many achievements, how many awards, and the longest run of days. It used
     * to lead with the game the span spent the longest on and close with the player's site rank,
     * which made a five-counter row where two of the five were a truncated game title and a number
     * about the whole site rather than about this journey. What is left is what the journey itself
     * came to, and it fits one line.
     *
     * Achievements is a plain count rather than a fraction: over a span of forty games there is no
     * honest denominator, and every one of them is unlocked by construction.
     *
     * Single-game mode does not come here at all - it has its own header (see [gameHeader]).
     */
    fun endChips(
        frame: JourneyFrame,
        awards: AwardTally,
        revealed: Int,
        games: Int,
        points: Int,
    ): List<Chip> = listOf(
        // No Points chip any more: the chart's ceiling line
        // carries the exact final total itself now - in the accent, with its own "pts" - so
        // the strip repeating the number was the redundancy. The running strip keeps its
        // climbing counter; only the landed state hands the fact over to the axis.
        Chip(ACHIEVEMENTS_LABEL, "$revealed"),
        // Which awards, not just how many: a span that ended on four masteries and one that ended
        // on four beaten games both used to finish on "Awards 4".
        awardsChip(awards),
        // The game count sits directly before the streak (UI feedback): the two duration-flavoured
        // facts read together at the end of the line.
        Chip(GAMES_LABEL, "$games"),
        Chip(STREAK_LABEL, streakValue(streakDays(frame))),
    )

    /**
     * Every counter the export strip draws: while the journey plays, when it has got to, its
     * points, its awards and the count that depends on the mode; once it has landed, the end state
     * ([endChips]).
     *
     * The Compose strip draws the same things, except that a span folds its game count into the
     * chip naming the game being played (see `PlayerOverview`). A shared picture and the picture
     * that was watched have to agree, which is the whole reason the wording lives here.
     */
    fun exportChips(
        frame: JourneyFrame,
        layout: ChartLayout,
        journeyDateMillis: Long?,
        points: Int,
        awards: AwardTally,
        revealed: Int,
        games: Int,
        completed: Boolean,
        /**
         * Where playback has got to, 0 to 1 - the shared sweep a compare chart's rows are cut at.
         *
         * A single-game compare chart is a RACE (see [isGameCompare]), and a race's rows cannot be
         * cut by a timestamp: [journeyDateMillis] is the main player's own clock, and the rivals
         * run on their own. Only the sweep says the same thing to every player at once.
         */
        progress: Float,
        ranks: Ranks = Ranks(),
        /** Each rival's score at this point, index for index with `frame.rivals`. See [comparePlaybackChips]. */
        rivalPoints: List<Int> = emptyList(),
        /** Each rival's unlock count at this point, index for index with `frame.rivals`. See [comparePlaybackChips]. */
        rivalRevealed: List<Int> = emptyList(),
        /** Each rival's awards at this point, index for index with `frame.rivals`. See [comparePlaybackChips]. */
        rivalAwards: List<AwardTally> = emptyList(),
        /**
         * The title of the game under the sweep, for a running span's header - null when nothing is.
         *
         * Handed in rather than looked up here, and that is the whole point: the caller
         * (`ExportFrame.drawStrip`) already resolves the game under the sweep to draw its ICON at
         * the head of the plate, and the title has to be that same game's or the plate would name
         * one game beside another game's art. Working it out a second time in this file would be a
         * second sweep - the export's is `JourneyChartRenderer.sweepX`, the player's is
         * `HudAtProgress.sweepX` - and two sweeps that agree today are two sweeps, not one answer.
         */
        nowPlaying: String? = null,
        /**
         * The slot widths the two playback headers reserve per fact, in screen dp.
         *
         * Default to the player's own, which is what every caller wants: they are declared in
         * `JourneyPlayer` because that is the file that draws them on screen, and defaulting to
         * them here means a caller cannot accidentally build an unslotted header that jitters.
         * See [FactSlots] and [Chip.slotDp].
         */
        gameSlots: FactSlots = GAME_FACT_SLOTS,
        spanSlots: FactSlots = SPAN_FACT_SLOTS,
    ): List<Chip> {
        // A single-game compare chart shows the players against each other at every point of
        // playback, on screen and in a shared picture alike - cut by each player's own ELAPSED
        // time at the shared sweep, exactly as the player's own bar cuts it (see PlayerOverview),
        // so who was ahead reads the same in the exported frame as it did on screen at that
        // instant. See [frameUpToElapsed] for why one absolute instant is the wrong cut here.
        //
        // The rank is withheld until [completed]: it is where the race ENDS, and a row that named
        // it from the first frame was spoiling a race the picture was still in the middle of
        // drawing. Everything else about the row - the duration, the score, the unlocks, the
        // awards - keeps moving with the sweep either way, which is why only `ranks.perPlayer`
        // itself is conditional here rather than the whole call.
        if (isGameCompare(frame)) {
            val truncated = frameUpToElapsed(frame, HudAtProgress.sweepElapsedMillis(layout, progress))
            return compareChips(truncated, if (completed) ranks.perPlayer else emptyMap())
        }
        // A solo single-game journey carries its header from the first frame to the last, exactly
        // as the player's own top bar does: the game, how far through its list the replay has got,
        // and the same percentage.
        if (frame.mode == JourneyMode.GAME) {
            // The awards go into the header rather than onto a chip: they name the hours the
            // finished header ends on ("Mastered in 42h 10m"), and the highest of them is drawn as
            // a dot beside the plate's own percentage - see [gameHeaderChips].
            return gameHeaderChips(
                gameHeader(frame, layout, journeyDateMillis, revealed, points, completed, awards),
                gameSlots,
            )
        }
        if (completed) return endChipsOrCompare(frame, awards, revealed, games, points, ranks)
        // A running compare span shows who is ahead rather than one player's counters, on screen
        // and in a shared picture alike.
        if (hasRivals(frame)) {
            return comparePlaybackChips(
                frame, layout, journeyDateMillis, points, revealed, awards,
                rivalPoints, rivalRevealed, rivalAwards,
            )
        }
        // The running span header itself, not an approximation of it in counters: the numbered game
        // under the sweep on one line, the date, the achievements, the points and the award dots on
        // the next. It was five label/value chips on a single baseline - the same facts, in a shape
        // the watcher never saw, and with the game the whole picture is of left unnamed.
        return spanHeaderChips(
            spanHeader(
                whenLabel = whenLabel(frame, layout, journeyDateMillis, completed),
                games = games,
                gameTitle = nowPlaying,
                achievements = revealed,
                points = points,
                awards = awards,
            ),
            spanSlots,
        )
    }

    // ---- compare mode, one game ----

    /** One player's line in the compare row: who, how long they took, what they scored, where they stand. */
    data class PlayerLine(
        val name: String,
        val isMain: Boolean,
        /**
         * Which rival this is, by position in the frame's roster, or -1 for the main player. The
         * colour a line is drawn in has to be the colour that player's curve is drawn in, and the
         * chart keys that off the roster position - which is not the position in this list, since
         * a rival whose fetch came back empty has a roster entry and no line.
         */
        val rivalIndex: Int,
        val duration: String,
        /**
         * The score itself, not a rendering of it. It was a pre-formatted string, which is how the
         * compare row came to read "1 pts" while the chart's own tooltip said "1 pt" for the same
         * number; [compareLineFacts] runs it through [pluralize] like every other count in this
         * file.
         */
        val points: Int,
        val rank: String?,
        /**
         * The end state's own counters, per player: the same four a solo journey finishes on (see
         * [endChips]) so a comparison is a comparison of like with like rather than of one player's
         * totals against two players' points.
         *
         * A row draws the two of them [compareLineFacts] names, in both modes. [mode] is still what
         * decides whether an award dot may print its own count ([awardCountsShown]): one game gives
         * a player at most one award of a kind, so that number could only ever read "1".
         */
        val mode: JourneyMode,
        val games: Int,
        val achievements: Int,
        /**
         * Which awards this player took, by kind. It was a bare count, which on the one view whose
         * whole job is comparison made a rival's four beaten games look like the mastery run beside
         * it; the row now draws the same dots the chart's own medallions carry (see [awardParts]).
         */
        val awards: AwardTally,
        val streakDays: Int,
        /**
         * How far through the game's achievement list this player is, 0 to 100 - the same fraction
         * the solo header's bar draws, per player (owner request), so a race shows how far through
         * the LIST each player is beside how many points that got them. Null on a span (no single
         * achievement list to be a fraction of) and on a game whose total nobody knows (a snapshot
         * cached before `GameRef.totalAchievements` existed): no bar beats a made-up denominator.
         */
        val percent: Int? = null,
        /** The hardcore share of [percent], on the same denominator - the bar's gold layer. */
        val hardcorePercent: Int = 0,
    )

    /**
     * The per-player comparison a single-game compare chart shows instead of the generic counters.
     *
     * On that chart the generic counters are the wrong four numbers: there is one game, one date
     * range and one achievement list, and the question the mode exists for is how the players did
     * against each other. So each player gets their own line - how long their run took from their
     * first unlock to their last, what they scored, and where they stand on the game's leaderboard
     * when that is known ([ranks], keyed by player name; a missing entry is a line without the
     * rank rather than a line that lies about it).
     */
    /** Whether anybody else is on this chart with something to show for it. */
    fun hasRivals(frame: JourneyFrame): Boolean = frame.rivals.any { it.unlocks.isNotEmpty() }

    /**
     * Whether the compare row replaces the counters for the whole replay: one game, with somebody
     * else on it.
     *
     * Only in GAME mode. There the generic counters are the wrong four numbers from the first frame
     * (one game, one achievement list, one date range), while a span's counters are a running
     * readout of a journey that genuinely moves - so a compare span keeps them until it lands, and
     * swaps to the comparison at the end (see [endChipsOrCompare]).
     */
    fun isGameCompare(frame: JourneyFrame): Boolean = frame.mode == JourneyMode.GAME && hasRivals(frame)

    /** What one player's score reads as on a compare bar, on its own: "240 pts". */
    fun comparePoints(line: PlayerLine): String = pluralize(line.points, "pt")

    /**
     * What one player's line on the compare bar reads: what they scored and how many unlocks it
     * took, "240 pts · 12 unlocks".
     *
     * Points lead (user request). A compare chart is a race up the points axis - that is the
     * number the curves are drawn from and the one the eye is already following - so it is what
     * belongs at the front of the line; the unlock count stays behind it as the fact a score alone
     * hides, since two players can be fifty points apart on one unlock or on twenty.
     *
     * The one facts string a compare row has, on screen and in an exported frame alike. There used
     * to be a second, longer one for the export (`compareFacts`: the run's duration, its
     * achievements, its points AND the rank, all fused into a single value), which meant a shared
     * picture of a comparison carried a different sentence per player from the one the watcher had
     * been reading - and, being most of the plate wide, one that ellipsized to "alphami..." inside
     * its column. The rank is not in here for the same reason it is not in the screen's: it is
     * [Chip.rank], drawn at the end of the line.
     */
    fun compareLineFacts(line: PlayerLine): String = compareRowFacts(line.points, line.achievements)

    /**
     * The shared shape [compareLineFacts] and [comparePlaybackChips] both state: what a score is,
     * and how many unlocks it took. One function rather than the format string written twice, so a
     * running row and a landed one never drift into two different sentences for the same fact.
     */
    private fun compareRowFacts(points: Int, unlocks: Int): String =
        // "achievements", not "unlocks" (owner request): the count is achievements earned, and
        // every other surface - the solo header, the end chips, the drawer - already calls the
        // same number achievements. "Unlock" was chart-internal vocabulary leaking into the row.
        "${pluralize(points, "pt")} · ${pluralize(unlocks, "achievement")}"

    /**
     * What a compare span says while it plays: the date the sweep has reached, and one line per
     * player - what they scored, how many unlocks it took and which awards they have taken so
     * far - each keeping its own roster slot so the drawn strip can colour it the way the live
     * bar does.
     *
     * The comparison used to be points alone, which was the wrong four numbers made into two: a
     * compare span's whole point is who did what against whom, and "who" used to arrive with only
     * half of "what" on it while it played, the unlocks and the awards held back to the end. The
     * rank stays held back regardless - see [Chip.rank]'s own doc and [exportChips]'s
     * `isGameCompare` branch, its sibling on the single-game chart - because a rank is a fact
     * about where the journey LANDS, not about where the sweep currently is.
     *
     * The date is deliberately the one chip in this list with no [Chip.rivalIndex] - every player
     * chip carries one (-1 for the main player, 0-based for a rival), and the drawn plate
     * (`ExportStrip.drawComparePlate`) uses exactly that split to tell the date apart from the
     * players: player chips stack as rows, and the date rides the first row's own right edge in
     * the ordinary counter styling instead of stacking as a row of its own. A solo running
     * export's strip still carries a Date chip, and a compare export silently carrying none at
     * all was an inconsistency rather than a design; but the date is a fact about the frame, not
     * about a player, so it must never occupy a player row - it did once, when this function
     * built the date with no `rivalIndex` and handed it to a plate that had not yet learned the
     * difference, and it got drawn as a fake third player: a bold name-sized label over a dim
     * value-sized fact, on every frame of a compare video but the last one. See
     * `ExportStrip.drawComparePlate`'s own doc for the rest of that story.
     *
     * The generic counters are one player's running readout, which on a chart with two or three
     * curves climbing next to each other is the wrong number to put at the top of the frame. A
     * rival with no unlocks is left out for the same reason their curve is not drawn: a score beside
     * a line that is not there is a comparison with nothing.
     */
    fun comparePlaybackChips(
        frame: JourneyFrame,
        layout: ChartLayout,
        journeyDateMillis: Long?,
        points: Int,
        /** The main player's own unlock count at this point of the sweep. See [HudAtProgress.stats]. */
        revealed: Int,
        /** The main player's own awards at this point of the sweep. See [HudAtProgress.awardTally]. */
        awards: AwardTally,
        /** Each rival's score at this point, index for index with `frame.rivals`. */
        rivalPoints: List<Int>,
        /** Each rival's unlock count at this point, index for index with `frame.rivals`. */
        rivalRevealed: List<Int>,
        /** Each rival's awards at this point, index for index with `frame.rivals`. */
        rivalAwards: List<AwardTally>,
    ): List<Chip> {
        val mine = Chip(
            label = frame.main.name,
            value = compareRowFacts(points, revealed),
            rivalIndex = -1,
            awards = awardParts(awards),
        )
        val theirs = frame.rivals.mapIndexedNotNull { index, rival ->
            if (rival.unlocks.isEmpty()) return@mapIndexedNotNull null
            Chip(
                label = rival.name,
                value = compareRowFacts(rivalPoints.getOrElse(index) { 0 }, rivalRevealed.getOrElse(index) { 0 }),
                rivalIndex = index,
                awards = awardParts(rivalAwards.getOrElse(index) { AwardTally() }),
            )
        }
        // Players and nothing else. The date used to ride the right edge of the first row; where the
        // sweep is in time is the corner readout's job on a followed chart (user request), and a
        // compare plate is a scoreboard - the one non-player fact on it was the odd mark out.
        return listOf(mine) + theirs
    }

    /**
     * The awards half of a compare line, drawn as dots beside [compareLineFacts] rather than in
     * words.
     *
     * On one game, only the HIGHEST (owner request): the kinds are a ladder there - mastering a
     * game implies beating it - so "mastered + beaten" on the same row said one fact twice, and
     * the solo header's own award circle already keeps just the top rung for the same reason. A
     * span keeps the whole breakdown: across forty games "2 mastered · 3 beaten" are different
     * games, and the mix is the point. [awardParts] orders best-first, so the highest is the head.
     */
    fun compareAwards(line: PlayerLine): List<AwardPart> {
        val parts = awardParts(line.awards)
        return if (line.mode == JourneyMode.GAME) parts.take(1) else parts
    }

    /**
     * The compare row as strip chips, so an exported picture of a compare chart carries the same
     * comparison the player was watching rather than dropping back to one player's counters.
     *
     * Each chip keeps its player's roster slot, so the drawn strip can put the line in that
     * player's own colour the way the on-screen row does. Without it a shared clip showed a
     * colour-coded chart over a colourless legend, which is the one half of the compare view that
     * cannot be inferred from anything else on the frame.
     */
    fun compareChips(frame: JourneyFrame, ranks: Map<String, Int>): List<Chip> =
        compareLines(frame, ranks).map {
            Chip(
                label = it.name,
                // The screen's own facts string, and the screen's own split: the rank travels
                // beside the facts rather than inside them, so the drawing can right-align it the
                // way `PlayerCompareLine` does. See [Chip.rank].
                value = compareLineFacts(it),
                rivalIndex = it.rivalIndex,
                awards = compareAwards(it),
                awardCountsShown = awardCountsShown(it.mode),
                rank = it.rank,
                percent = it.percent,
                hardcorePercent = it.hardcorePercent,
            )
        }

    /**
     * What a finished journey says: the per-player comparison when anybody else is on the chart,
     * and the end counters when nobody is.
     *
     * A compare span finishes on the rows alone - one line per player, which is the mode's whole
     * point. The block of the owner's own totals that used to stack above them put one player's
     * counters at the top of a chart about who did what against whom, and on a handheld the two
     * blocks together were most of the screen. Span rivals carry the site rank that came back
     * with their name's spelling; a GAME rival's row carries the game's leaderboard rank.
     */
    fun endChipsOrCompare(
        frame: JourneyFrame,
        awards: AwardTally,
        revealed: Int,
        games: Int,
        points: Int,
        ranks: Ranks = Ranks(),
    ): List<Chip> {
        if (!hasRivals(frame)) return endChips(frame, awards, revealed, games, points)
        return compareChips(frame, ranks.perPlayer)
    }

    fun compareLines(frame: JourneyFrame, ranks: Map<String, Int>): List<PlayerLine> {
        val players = listOf(frame.main to -1) + frame.rivals.mapIndexed { index, rival -> rival to index }
        // One game, one achievement list: every player's percent divides by the same total. Zero -
        // a span, or a snapshot cached before the total existed - means no percent at all.
        val total = if (frame.mode == JourneyMode.GAME) totalAchievements(frame) else 0
        return players
            .filter { (player, rivalIndex) -> rivalIndex < 0 || player.unlocks.isNotEmpty() }
            .map { (player, rivalIndex) ->
                val span = JourneyStats.playerSpanMillis(player.unlocks)
                // The same stale-total guard as [gameHeader]'s `shown`: the numerator comes off the
                // live unlock list and the denominator off a cached snapshot, and a list trimmed
                // since the snapshot must not print a fraction over one.
                val shown = player.unlocks.size.coerceAtMost(total)
                val shownHardcore = player.unlocks.count { it.hardcore }.coerceAtMost(shown)
                PlayerLine(
                    name = player.name,
                    isMain = rivalIndex < 0,
                    rivalIndex = rivalIndex,
                    duration = span?.let { TickGenerator.fmtElapsedApprox(it) } ?: "-",
                    points = player.unlocks.sumOf { it.points },
                    rank = ranks[player.name]?.let { "Rank #$it" },
                    mode = frame.mode,
                    // The games this player actually got through in the window, not the games their
                    // fetch happened to touch: a snapshot with no unlocks in the span is still in
                    // `games`, and counting it would credit a player for a game they never played.
                    games = player.unlocks.map { it.gameId }.distinct().size,
                    achievements = player.unlocks.size,
                    awards = AwardTally.of(player.medals.map { it.kind }),
                    streakDays = JourneyStats.longestStreakDays(player.unlocks),
                    percent = if (total <= 0) null else (shown * 100) / total,
                    hardcorePercent = if (total <= 0) 0 else (shownHardcore * 100) / total,
                )
            }
    }
}
