package com.twinlore.app.journey.domain

import com.twinlore.app.journey.domain.model.JourneyMode

/**
 * Whether a rival's game splits the backdrop into two halves.
 *
 * Only over a SPAN. There the two curves are on different games at the same moment and each half
 * carries the game behind its own line. One game with rivals on it is still one game, and halving
 * the frame for it draws the same art twice with a seam across the middle.
 *
 * Pure Kotlin and in `domain` rather than living on either caller, because there are two of them:
 * the live screen's backdrop ([com.twinlore.app.journey.ui.backdropArgs]) and the exported
 * frame's ([com.twinlore.app.journey.export.ExportFrame.drawArtBehind]). It used to be
 * `ExportBackdrop.splitsForRivals`, export-only, and the screen never called it - it had its own
 * "is there a rival" check with no mode in it at all, so a single-game compare split on screen
 * long after the export was fixed to stop. One rule, two callers, is the point: it lives where
 * both `ui` and `export` can see it without either importing the other (`export` already depends
 * on `ui` for the chart and layout types, and `ui` reaching back into `export` would have made
 * that a cycle).
 */
fun splitsForRivals(mode: JourneyMode, hasRivalTrack: Boolean): Boolean =
    mode == JourneyMode.SPAN && hasRivalTrack
