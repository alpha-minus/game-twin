package com.twinlore.app.ui.fold.guide

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The reader's own chrome, as the panel hosts it.
 *
 * The reader hides and shows its bar on taps, on scrolls and on back, and the
 * panel's bars go with it. What the panel is told is always the state the
 * reader is now in, never "flip": a flip is only right while the two already
 * agree, and they do not from the first frame, because the panel's collapsed
 * state is persisted and the reader's chrome always starts shown. Answering a
 * toggle with a toggle put them out of phase for the rest of the panel.
 */
@Stable
class PanelReaderChrome(
    initiallyVisible: Boolean = true,
    private val onChange: (Boolean) -> Unit,
) {
    /**
     * Seeded from where the panel's bar already is, and reported to nobody:
     * starting shown while the panel's persisted bar was hidden is the
     * disagreement itself, and announcing the seed would instead overrule the
     * state the reader was deliberately left in.
     */
    var visible by mutableStateOf(initiallyVisible)
        private set

    fun toggle() = set(!visible)
    fun hide() = set(false)
    fun reveal() = set(true)

    private fun set(shown: Boolean) {
        visible = shown
        onChange(shown)
    }
}
