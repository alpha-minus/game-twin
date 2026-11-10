package com.twinlore.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure decision logic for the one-time "show it in Cocoon too" hint offered
 * when applying an app icon. No Android, no DataStore: [shouldShowCocoonIconPackHint]
 * only makes sense to show when Cocoon is the detection source and the hint
 * has never been shown before.
 */
class CocoonIconPackHintTest {

    @Test
    fun `shows when the source is Cocoon and the hint has never been shown`() {
        assertTrue(shouldShowCocoonIconPackHint(LastPlayedSource.COCOON, alreadyShown = false))
    }

    @Test
    fun `does not show again once the hint has been shown`() {
        assertFalse(shouldShowCocoonIconPackHint(LastPlayedSource.COCOON, alreadyShown = true))
    }

    @Test
    fun `never shows for a non-Cocoon source, shown or not`() {
        LastPlayedSource.entries
            .filter { it != LastPlayedSource.COCOON }
            .forEach { source ->
                assertFalse(
                    "source=$source should not trigger the hint even unseen",
                    shouldShowCocoonIconPackHint(source, alreadyShown = false),
                )
                assertFalse(
                    "source=$source should not trigger the hint once seen",
                    shouldShowCocoonIconPackHint(source, alreadyShown = true),
                )
            }
    }
}
