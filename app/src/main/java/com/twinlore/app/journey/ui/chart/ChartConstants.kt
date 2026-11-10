package com.twinlore.app.journey.ui.chart

/**
 * Constants shared by everything that has to agree on where playback has got to.
 *
 * The chart, the backdrop and the HUD all answer "has the sweep passed this yet" and all three
 * have to answer it identically, or a badge pops in a frame before its points land in the counter
 * and the art behind it changes at a third moment. Keeping the tolerance in one place is what
 * makes that a single decision rather than three constants that drift apart.
 */
object ChartConstants {

    /**
     * Half a virtual unit of slack on every reveal comparison, in the chart's own 1060x600 space.
     * Playback progress is a float, and an unlock sitting exactly on the sweep should read as
     * reached rather than as almost reached.
     */
    const val REVEAL_TOLERANCE = 0.5f
}
