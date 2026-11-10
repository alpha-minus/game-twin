package com.twinlore.app.journey.domain

/**
 * What a player is doing while their line is flat: the small sign drawn over their face when the
 * sweep is crossing a stretch of chart where they unlocked nothing.
 *
 * A journey is mostly *not* playing - the curve spends more of its width waiting than climbing -
 * and a face parked on a flat line for a quarter of the replay says nothing about the person it
 * belongs to. A sign says the obvious, warmly: they were asleep, at work, cooking, holding a baby.
 * It is decoration and it knows it, which is why it is one glyph and why the whole thing can be
 * turned off in the player's settings.
 *
 * Pure Kotlin, no Compose: which sign a gap gets is arithmetic - a band by how long the gap is,
 * then a deterministic pick inside that band - and pinning it needs no canvas. Deterministic
 * matters twice over: a sign that re-rolled every frame would flicker, and a shared video has to
 * show the watcher what they watched.
 */
object IdleSigns {

    private const val HOUR = 3_600_000L
    private const val DAY = 24 * HOUR

    /**
     * The shortest gap worth a sign at all - below this, a flat stretch of line is a bathroom
     * break or a loading screen, not a story.
     *
     * Deliberately its own constant rather than a rename of the old six-hour "NIGHT": that name
     * conflated the floor with the point where "they went to bed" becomes the likeliest story, but
     * that story is DAILY's, gated on [DAY], not on this. A three-hour gap clears this floor and
     * lands in CHORES (an evening's errands) - correct, since [DAY] never moved - but it would
     * have been an easy mistake to drag the DAILY boundary down along with the floor because a
     * single constant used to imply they were the same thing.
     */
    private const val FLOOR = 3 * HOUR

    /** Above this a gap is no longer a day off; it is a stretch of life away from the console. */
    private const val AWAY = 4 * DAY

    /**
     * An evening's errands: eating, washing, reading, the desk. Short gaps inside a playing day.
     */
    private val CHORES = listOf("🍽️", "🚿", "📚", "🧺", "🛒", "🐕", "☕", "🍳")

    /** A night, or a day of the ordinary sort: sleep, work, the school run, a night out. */
    private val DAILY = listOf("💤", "😴", "💼", "🍼", "🏫", "🧹", "🍻", "🎬", "🍷", "❤️")

    /** Longer than that: away, ill, moving house, or simply busy with something bigger. */
    private val AWAY_FROM_HOME = listOf("🧳", "🏖️", "✈️", "🛠️", "🤒", "📦", "🎓", "💒")

    /**
     * The sign for a gap of [gapMillis], or null when the gap is too short to be worth a picture.
     *
     * [seed] is something stable about the gap itself (the index of the unlock it follows), never
     * the clock: one gap has to answer the same way every frame it is asked, or the sign would
     * flicker over the player's face and a shared video would not match the replay it came from.
     *
     * [step] is how far the sweep has got THROUGH that gap, in whole switches (see the renderer's
     * own quantising). A long wait moves on to the next sign in the same tier rather than holding
     * one picture for the whole of it (user request): they slept, then they went to work. The tier
     * never changes - how long the gap is is a fact about the gap, not about where the sweep is in
     * it - so a night out cannot turn into a holiday half way through.
     */
    fun signFor(gapMillis: Long, seed: Int, step: Int = 0): String? {
        if (gapMillis < FLOOR) return null
        val pool = when {
            gapMillis < DAY -> CHORES
            gapMillis < AWAY -> DAILY
            else -> AWAY_FROM_HOME
        }
        // A stable hash rather than a modulo of the seed: consecutive gaps would otherwise walk
        // the list in order, and a chart of short gaps would read as a repeating cartoon strip.
        val h = seed * -1640531527
        val start = (h ushr 16) + step
        val index = (start % pool.size + pool.size) % pool.size
        return pool[index]
    }

    /** The shortest gap that gets a sign at all, for the callers that want to test the edge. */
    val minimumGapMillis: Long get() = FLOOR
}
