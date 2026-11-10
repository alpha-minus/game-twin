package com.twinlore.app.data

import com.twinlore.app.data.license.LicenseTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The icons the overlay offers: unlocked by the tier, Pixel only once earned. */
class SelectableAppIconsTest {

    @Test fun `a free user without the tap score gets the default alone`() {
        assertEquals(listOf(AppIcon.DEFAULT), selectableAppIcons(LicenseTier.NONE, 0))
    }

    @Test fun `the tap score earns Pixel`() {
        assertEquals(
            listOf(AppIcon.DEFAULT, AppIcon.PIXEL),
            selectableAppIcons(LicenseTier.NONE, PIXEL_UNLOCK_TAP_SCORE),
        )
    }

    @Test fun `every icon a tier offers is one it unlocks`() {
        LicenseTier.entries.forEach { tier ->
            selectableAppIcons(tier, PIXEL_UNLOCK_TAP_SCORE).forEach {
                assertTrue("$it offered at $tier", it.isUnlockedBy(tier))
            }
        }
        assertFalse(AppIcon.GOLD in selectableAppIcons(LicenseTier.NONE, PIXEL_UNLOCK_TAP_SCORE))
    }
}
