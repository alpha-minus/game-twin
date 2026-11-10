package com.twinlore.app.data.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateManagerTest {

    @Test
    fun `isNewer returns true when remote patch is higher`() {
        assertTrue(UpdateManager.isNewer("0.6.1", "0.6.0"))
    }

    @Test
    fun `isNewer returns true when remote minor is higher`() {
        assertTrue(UpdateManager.isNewer("0.7.0", "0.6.0"))
    }

    @Test
    fun `isNewer returns true when remote major is higher`() {
        assertTrue(UpdateManager.isNewer("1.0.0", "0.6.0"))
    }

    @Test
    fun `isNewer returns false when versions are equal`() {
        assertFalse(UpdateManager.isNewer("0.6.0", "0.6.0"))
    }

    @Test
    fun `isNewer returns false when remote is older`() {
        assertFalse(UpdateManager.isNewer("0.5.0", "0.6.0"))
    }

    @Test
    fun `isNewer handles different segment lengths`() {
        assertTrue(UpdateManager.isNewer("0.10.0", "0.9.0"))
        assertTrue(UpdateManager.isNewer("1.0", "0.9.0"))
        assertFalse(UpdateManager.isNewer("0.6", "0.6.0"))
    }

    // --- The 0.x to 1.0.0 transition -----------------------------------------------------
    //
    // Going to 1.0.0 crosses the major boundary for the first time, so both directions matter:
    // everyone on a 0.x build has to be offered it, and nobody on 1.0.0 may be offered the 0.x
    // releases that stay published on GitHub afterwards.

    @Test
    fun `1_0_0 is offered to the current 0_12_x builds`() {
        assertTrue(UpdateManager.isNewer("1.0.0", "0.12.1"))
        assertTrue(UpdateManager.isNewer("1.0.0", "0.12.0"))
    }

    @Test
    fun `an installed 1_0_0 is never sent back to a published 0_x release`() {
        // github.com/alpha-minus/game-twin currently publishes 0.12.0 as "latest". If that tag
        // is still latest when someone installs 1.0.0, the check must stay quiet.
        assertFalse(UpdateManager.isNewer("0.12.0", "1.0.0"))
        assertFalse(UpdateManager.isNewer("0.9.9", "1.0.0"))
    }

    @Test
    fun `updates continue to work above 1_0_0`() {
        assertTrue(UpdateManager.isNewer("1.0.1", "1.0.0"))
        assertTrue(UpdateManager.isNewer("1.1.0", "1.0.0"))
        assertTrue(UpdateManager.isNewer("2.0.0", "1.0.0"))
        assertFalse(UpdateManager.isNewer("1.0.0", "1.0.0"))
    }

    @Test
    fun `comparison is numeric, not lexicographic, past the single digits`() {
        assertTrue(UpdateManager.isNewer("1.0.10", "1.0.9"))
        assertTrue(UpdateManager.isNewer("1.10.0", "1.9.0"))
        assertFalse(UpdateManager.isNewer("1.0.9", "1.0.10"))
    }

    @Test
    fun `isNewer strips suffixes like beta or rc`() {
        assertTrue(UpdateManager.isNewer("0.7.1-beta", "0.7.0"))
        assertTrue(UpdateManager.isNewer("0.7.1-rc1", "0.7.0"))
        assertTrue(UpdateManager.isNewer("1.0.0-alpha", "0.9.0"))
        assertFalse(UpdateManager.isNewer("0.7.0-beta", "0.7.0"))
        assertFalse(UpdateManager.isNewer("0.6.0-beta", "0.7.0"))
    }

    @Test
    fun `update downloads are only trusted from GitHub over HTTPS`() {
        assertTrue(UpdateManager.isTrustedUpdateUrl("https://github.com/alpha-minus/game-twin/releases/download/1.1.0/app.apk"))
        assertTrue(UpdateManager.isTrustedUpdateUrl("https://objects.githubusercontent.com/x/app.apk"))
        assertFalse(UpdateManager.isTrustedUpdateUrl("http://github.com/alpha-minus/game-twin/releases/download/1.1.0/app.apk"))
        assertFalse(UpdateManager.isTrustedUpdateUrl("https://evil.example/app.apk"))
        assertFalse(UpdateManager.isTrustedUpdateUrl("https://github.com.evil.example/app.apk"))
    }
}
