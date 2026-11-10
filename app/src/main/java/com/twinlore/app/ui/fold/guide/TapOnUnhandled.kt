package com.twinlore.app.ui.fold.guide

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

/**
 * A tap on whatever the content underneath did not want.
 *
 * The panel's rule is that a tap on what you are reading moves the bars, and
 * every surface but one keeps it: the reader reports its own taps, the image
 * hands the whole picture to one gesture. The achievements list could not,
 * because it is a list - only the rows carried the tap, so the header, the
 * filter strip, the empty states and every gap between rows were dead ground,
 * and on a vault with nothing to list there was no way to get the bar back at
 * all.
 *
 * Wrapping the surface in a plain `clickable` would have taken the taps the
 * rows and pills need. So this watches instead of intercepting: it sees the
 * press first, in the Initial pass, and then reads each event again in Final,
 * after everyone below has had it. What comes back consumed was somebody's -
 * a row, a pill, the refresh button, a fling being stopped - and is left
 * alone. What moved past touch slop was a scroll. Only a press that nothing
 * claimed and nothing dragged is a tap on the surface itself.
 */
@Composable
fun Modifier.tapOnUnhandled(onTap: () -> Unit): Modifier {
    val current by rememberUpdatedState(onTap)
    return this.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var claimed = down.isConsumed
            var dragged = false
            val slop = viewConfiguration.touchSlop
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Final)
                // A second finger is a pinch or a two-finger scroll, never a tap.
                if (event.changes.size > 1) claimed = true
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change == null) {
                    claimed = true
                    break
                }
                if (change.isConsumed) claimed = true
                if ((change.position - down.position).getDistance() > slop) dragged = true
                if (!change.pressed) break
            }
            if (!claimed && !dragged) current()
        }
    }
}
