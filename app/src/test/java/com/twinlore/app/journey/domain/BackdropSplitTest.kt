package com.twinlore.app.journey.domain

import com.twinlore.app.journey.domain.model.JourneyMode
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The one rule the screen's backdrop and the exported frame both consult for whether a rival's
 * game splits the picture in half. See [splitsForRivals]'s kdoc for why it lives here rather than
 * on either caller.
 */
class BackdropSplitTest {

    @Test fun `only a span splits the backdrop between players`() {
        assertEquals(true, splitsForRivals(JourneyMode.SPAN, hasRivalTrack = true))
        assertEquals(false, splitsForRivals(JourneyMode.GAME, hasRivalTrack = true))
        assertEquals(false, splitsForRivals(JourneyMode.SPAN, hasRivalTrack = false))
        assertEquals(false, splitsForRivals(JourneyMode.GAME, hasRivalTrack = false))
    }
}
