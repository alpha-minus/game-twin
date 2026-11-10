package com.twinlore.app.data.license

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LicenseActivationErrorTest {

    /** Verbatim shape of what Lemon Squeezy returns with 404 for a key that does not exist. */
    private val notFoundBody =
        """{"activated":false,"error":"license_key not found","license_key":null,"instance":null,"meta":null}"""

    @Test
    fun `the error field is pulled out of a 404 body`() {
        assertEquals("license_key not found", LicenseActivationError.parseApiError(notFoundBody))
    }

    @Test
    fun `a well-formed but unknown key reads as a key problem, not a server problem`() {
        val apiError = LicenseActivationError.parseApiError(notFoundBody)!!
        val message = LicenseActivationError.friendly(apiError)
        assertEquals("License key not found. Please check that you entered it correctly.", message)
        assertTrue(!message.contains("Server error"))
        assertTrue(!message.contains("try again later"))
    }

    @Test
    fun `an absent or unusable body yields no api error`() {
        assertNull(LicenseActivationError.parseApiError(null))
        assertNull(LicenseActivationError.parseApiError(""))
        assertNull(LicenseActivationError.parseApiError("   "))
        assertNull(LicenseActivationError.parseApiError("<html>502 Bad Gateway</html>"))
        assertNull(LicenseActivationError.parseApiError("""{"activated":false}"""))
        assertNull(LicenseActivationError.parseApiError("""{"error":""}"""))
    }

    @Test
    fun `a body that is valid json but not an object does not throw`() {
        assertNull(LicenseActivationError.parseApiError("[1,2,3]"))
    }

    @Test
    fun `the status-code fallback is only for bodies with nothing to say`() {
        assertEquals("Server error (500). Please try again later.", LicenseActivationError.serverError(500))
    }

    @Test
    fun `activation limit reports the counts when they are known`() {
        val message = LicenseActivationError.friendly("license_key activation limit reached", usage = 3, limit = 3)
        assertTrue(message.contains("(3/3 devices)"))
        assertTrue(message.contains("deactivate"))
    }

    @Test
    fun `activation limit drops the counts when they are not`() {
        val message = LicenseActivationError.friendly("license_key activation limit reached")
        assertTrue(message.startsWith("Activation limit reached."))
        assertTrue(!message.contains("devices)"))
    }

    @Test
    fun `expired and disabled keys are named as such`() {
        assertEquals("This license key has expired.", LicenseActivationError.friendly("license_key is expired"))
        assertEquals("This license key has been disabled.", LicenseActivationError.friendly("license_key is disabled"))
    }

    @Test
    fun `an unrecognized api error is passed through rather than swallowed`() {
        assertEquals("something new we have not seen", LicenseActivationError.friendly("something new we have not seen"))
    }

    @Test
    fun `no user-facing message contains an em dash`() {
        val messages = listOf(
            LicenseActivationError.friendly("license_key not found"),
            LicenseActivationError.friendly("license_key activation limit reached", 1, 1),
            LicenseActivationError.friendly("license_key is expired"),
            LicenseActivationError.friendly("license_key is disabled"),
            LicenseActivationError.friendly("invalid license_key"),
            LicenseActivationError.serverError(404),
            LicenseKeyFormat.ERROR_MESSAGE,
        )
        messages.forEach { assertTrue("em dash in: $it", !it.contains('—')) }
    }
}
