package com.twinlore.app.journey.domain

import com.twinlore.app.journey.domain.model.JourneyFrame
import com.twinlore.app.journey.domain.model.PlayerJourney

/**
 * [frame] with every player's unlocks and medals cut to what the sweep has reached: the running
 * compare bar is the end bar over this, so the two states are one format by construction.
 *
 * Pure frame arithmetic, with no Compose in it, which is why it lives here rather than in the
 * player: the on-screen bar truncates by it while the journey plays, and the export strip
 * truncates by the very same function, so a shared clip never disagrees with what was watched.
 *
 * One CALENDAR instant, which is the right cut only where every player's x is that same calendar
 * clock. A race chart's is not - see [frameUpToElapsed], which is what a single-game compare chart
 * uses.
 */
fun frameUpTo(frame: JourneyFrame, tMillis: Long): JourneyFrame = frame.copy(
    main = frame.main.copy(
        unlocks = frame.main.unlocks.filter { it.earnedAtMillis <= tMillis },
        medals = frame.main.medals.filter { it.whenMillis <= tMillis },
    ),
    rivals = frame.rivals.map { rival ->
        rival.copy(
            unlocks = rival.unlocks.filter { it.earnedAtMillis <= tMillis },
            medals = rival.medals.filter { it.whenMillis <= tMillis },
        )
    },
)

/**
 * [frame] with every player cut to [elapsedMillis] since THEIR OWN first unlock, rather than to one
 * absolute instant on somebody else's clock.
 *
 * The cut a race chart needs, which is every single-game compare chart there is:
 * `JourneyStripText.isGameCompare` (one game, with somebody else on it) is the identical predicate
 * to [LayoutEngine]'s own `raceMode`. There a player's x is `padL + innerW * elapsed / eMax`
 * measured from their own first unlock ([LayoutEngine]'s `xForPlayer`), so the shared sweep at
 * progress p stands for "p of the axis elapsed" for every player on the chart independently, and
 * the calendar has nothing to do with it.
 *
 * THE INVARIANT: a player's row agrees with their own curve at every point of playback. If their
 * dot is on the chart, their row counts it; if it is not, it does not. Which is why the limit is
 * derived from the sweep itself ([com.twinlore.app.journey.ui.HudAtProgress.sweepElapsedMillis])
 * rather than from any one player's timestamps.
 *
 * [frameUpTo] was what these rows were cut by, at `stats.journeyDateMillis` - the MAIN player's
 * last revealed unlock. On an elapsed axis that is a rival's calendar clock read off somebody
 * else's watch, and both of its failures are ordinary rather than edge cases, since a rival carries
 * their own real timestamps:
 * - a rival who played the game EARLIER had no unlock filtered at all, so their row showed their
 *   FINAL totals from the first frame while their curve was still being drawn;
 * - a rival who played it LATER had every unlock filtered for the whole clip, and
 *   `JourneyStripText.compareLines` drops a player whose unlocks are empty - so their row vanished
 *   from the bar and from the exported frame, the landed state included.
 */
fun frameUpToElapsed(frame: JourneyFrame, elapsedMillis: Long): JourneyFrame {
    if (elapsedMillis == Long.MAX_VALUE) return frame
    return frame.copy(
        main = playerUpToElapsed(frame.main, elapsedMillis),
        rivals = frame.rivals.map { playerUpToElapsed(it, elapsedMillis) },
    )
}

/**
 * One player cut to [elapsedMillis] of their own run.
 *
 * The zero is the one the chart measures them from: their first unlock. A player with none has no
 * zero of their own, and the chart anchors what they do have at the axis origin (see
 * [LayoutEngine]'s medal anchoring, which falls back to the medal's own moment), so nothing of
 * theirs is cut here either - the row and the curve agree on showing it from the first frame.
 */
private fun playerUpToElapsed(player: PlayerJourney, elapsedMillis: Long): PlayerJourney {
    val first = player.unlocks.firstOrNull()?.earnedAtMillis ?: return player
    return player.copy(
        unlocks = player.unlocks.filter { it.earnedAtMillis - first <= elapsedMillis },
        // Negative for a medal timestamped before the player's first unlock, which is kept - the
        // same reading LayoutEngine gives it when it clamps such an anchor to the axis origin.
        medals = player.medals.filter { it.whenMillis - first <= elapsedMillis },
    )
}
