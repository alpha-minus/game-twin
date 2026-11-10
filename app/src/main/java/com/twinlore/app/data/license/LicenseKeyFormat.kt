package com.twinlore.app.data.license

/**
 * Shape of a Lemon Squeezy license key: a UUID, `XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX`.
 *
 * Kept apart from [LicenseManager] so the rules are unit-testable without a network stack —
 * the check below is the only thing standing between a typo and a confusing server error.
 */
object LicenseKeyFormat {

    /** Canonical example shown to the user. Not a real key. */
    const val EXAMPLE = "XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX"

    /** What the user sees when [isValid] fails. */
    const val ERROR_MESSAGE =
        "That key doesn't look right. A license key is 36 characters in the form $EXAMPLE. " +
            "Check for a missing or extra character."

    private val KEY_PATTERN = Regex(
        "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
    )

    /**
     * True only for a full 8-4-4-4-12 hex key.
     *
     * Deliberately NOT `UUID.fromString`, which was the original check and is far too lenient to
     * use as a format test: it splits on `-` and parses each group as a hex *number*, so a key
     * whose first group is a character short (`A1B2C3D-…` instead of `A1B2C3D4-…`) parses happily
     * into a different, zero-padded UUID. The typo then reached the API and came back as a
     * generic "Server error. Please try again later.", pointing the user at the network instead
     * of at the character they dropped.
     */
    fun isValid(key: String): Boolean = KEY_PATTERN.matches(key.trim().replace(" ", ""))

    /**
     * Tidy a pasted key: drop surrounding whitespace and any internal spaces, and re-insert the
     * dashes when 32 bare hex characters were pasted. Anything else is handed back trimmed for
     * [isValid] to reject — normalizing is not validating.
     */
    fun normalize(key: String): String {
        val bare = key.trim().replace(" ", "").replace("-", "")
        if (bare.length != 32 || !bare.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) {
            return key.trim()
        }
        return "${bare.substring(0, 8)}-${bare.substring(8, 12)}-${bare.substring(12, 16)}-" +
            "${bare.substring(16, 20)}-${bare.substring(20, 32)}"
    }
}
