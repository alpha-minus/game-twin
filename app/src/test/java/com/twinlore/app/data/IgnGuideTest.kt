package com.twinlore.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class IgnGuideTest {

    @Test fun `colon and spaces collapse to single dash`() {
        assertEquals("castlevania-circle-of-the-moon", ignSlug("Castlevania: Circle of the Moon"))
    }

    @Test fun `accents are stripped`() {
        assertEquals("pokemon-red", ignSlug("Pokémon Red"))
    }

    @Test fun `digits are kept so sequels survive`() {
        assertEquals("god-of-war-2", ignSlug("God of War 2"))
        assertEquals("sonic-the-hedgehog-3", ignSlug("Sonic the Hedgehog 3"))
    }

    @Test fun `leading and trailing separators are trimmed`() {
        assertEquals("mega-man-x", ignSlug("  Mega Man X!  "))
    }

    @Test fun `builds the wiki guide url`() {
        assertEquals(
            "https://www.ign.com/wikis/castlevania-circle-of-the-moon",
            ignGuideUrl("Castlevania: Circle of the Moon"),
        )
    }

    @Test fun `blank name falls back to the wikis landing page`() {
        assertEquals("https://www.ign.com/wikis", ignGuideUrl("   "))
    }
}
