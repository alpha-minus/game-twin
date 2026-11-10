package com.twinlore.app.ui.screens.offline

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IsOmegametroidHostTest {

    @Test
    fun matches_apex() {
        assertTrue(isOmegametroidHost("https://omegametroid.com/metroid-dread-walkthrough/"))
    }

    @Test
    fun matches_www_subdomain() {
        assertTrue(isOmegametroidHost("https://www.omegametroid.com/"))
    }

    @Test
    fun matches_any_subdomain() {
        assertTrue(isOmegametroidHost("https://blog.omegametroid.com/post"))
    }

    @Test
    fun rejects_other_host() {
        assertFalse(isOmegametroidHost("https://bulbapedia.bulbagarden.net/wiki/Foo"))
    }

    @Test
    fun rejects_lookalike_suffix() {
        // Must not match an attacker domain ending in -omegametroid.com without dot.
        assertFalse(isOmegametroidHost("https://evilomegametroid.com/"))
    }

    @Test
    fun rejects_blank_input() {
        assertFalse(isOmegametroidHost(""))
    }

    @Test
    fun rejects_malformed_url() {
        assertFalse(isOmegametroidHost("not a url"))
    }

    @Test
    fun case_insensitive_host() {
        assertTrue(isOmegametroidHost("https://OmegaMetroid.COM/"))
    }
}
