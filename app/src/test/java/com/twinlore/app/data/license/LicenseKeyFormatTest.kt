package com.twinlore.app.data.license

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LicenseKeyFormatTest {

    // A well-formed key, shape only — not a real license.
    private val validKey = "A1B2C3D4-5E6F-4A7B-B8C9-0D1E2F3A4B5C"

    @Test
    fun `a well-formed key is valid`() {
        assertTrue(LicenseKeyFormat.isValid(validKey))
    }

    @Test
    fun `lowercase hex is valid`() {
        assertTrue(LicenseKeyFormat.isValid(validKey.lowercase()))
    }

    @Test
    fun `surrounding whitespace and inner spaces are ignored`() {
        assertTrue(LicenseKeyFormat.isValid("  A1B2C3D4-5E6F-4A7B -B8C9-0D1E2F3A4B5C "))
    }

    /**
     * The regression this file exists for. `UUID.fromString` accepted a key with a short first
     * group by zero-padding it, so the typo reached the API and surfaced as a generic server
     * error instead of a format error.
     */
    @Test
    fun `a group one character short is rejected`() {
        assertFalse(LicenseKeyFormat.isValid("A1B2C3D-5E6F-4A7B-B8C9-0D1E2F3A4B5C"))
    }

    @Test
    fun `every short group is rejected`() {
        assertFalse(LicenseKeyFormat.isValid("A1B2C3D4-5E6-4A7B-B8C9-0D1E2F3A4B5C"))
        assertFalse(LicenseKeyFormat.isValid("A1B2C3D4-5E6F-4A7-B8C9-0D1E2F3A4B5C"))
        assertFalse(LicenseKeyFormat.isValid("A1B2C3D4-5E6F-4A7B-B8C-0D1E2F3A4B5C"))
        assertFalse(LicenseKeyFormat.isValid("A1B2C3D4-5E6F-4A7B-B8C9-0D1E2F3A4B4"))
    }

    @Test
    fun `a group one character long is rejected`() {
        assertFalse(LicenseKeyFormat.isValid("A1B2C3D42-5E6F-4A7B-B8C9-0D1E2F3A4B5C"))
        assertFalse(LicenseKeyFormat.isValid("A1B2C3D4-5E6F-4A7B-B8C9-0D1E2F3A4B5C8"))
    }

    @Test
    fun `non-hex characters are rejected`() {
        assertFalse(LicenseKeyFormat.isValid("A1B2C3D4-5E6F-4A7B-B8C9-0D1E2F3A4BGG"))
    }

    @Test
    fun `wrong group count is rejected`() {
        assertFalse(LicenseKeyFormat.isValid("A1B2C3D4-5E6F-4A7B-0D1E2F3A4B5C"))
        assertFalse(LicenseKeyFormat.isValid("A1B2C3D4-5E6F-4A7B-B8C9-0D1E-2F3A4B5C"))
    }

    @Test
    fun `blank and junk are rejected`() {
        assertFalse(LicenseKeyFormat.isValid(""))
        assertFalse(LicenseKeyFormat.isValid("   "))
        assertFalse(LicenseKeyFormat.isValid("not-a-key"))
    }

    @Test
    fun `32 bare hex characters get their dashes back`() {
        assertEquals(validKey, LicenseKeyFormat.normalize("A1B2C3D45E6F4A7BB8C90D1E2F3A4B5C"))
    }

    @Test
    fun `a normalized bare key passes validation`() {
        assertTrue(LicenseKeyFormat.isValid(LicenseKeyFormat.normalize("A1B2C3D45E6F4A7BB8C90D1E2F3A4B5C")))
    }

    @Test
    fun `an already-dashed key survives normalization unchanged`() {
        assertEquals(validKey, LicenseKeyFormat.normalize("  $validKey  "))
    }

    /** Normalizing is not validating: a 31-character key must come back for [isValid] to reject. */
    @Test
    fun `a short key is not padded into a valid one`() {
        val short = "A1B2C3D-5E6F-4A7B-B8C9-0D1E2F3A4B5C"
        assertEquals(short, LicenseKeyFormat.normalize(short))
        assertFalse(LicenseKeyFormat.isValid(LicenseKeyFormat.normalize(short)))
    }

    @Test
    fun `32 non-hex characters are not dashed into a fake key`() {
        val junk = "ZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZ"
        assertEquals(junk, LicenseKeyFormat.normalize(junk))
        assertFalse(LicenseKeyFormat.isValid(LicenseKeyFormat.normalize(junk)))
    }
}
