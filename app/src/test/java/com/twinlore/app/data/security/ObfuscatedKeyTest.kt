package com.twinlore.app.data.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.Base64

class ObfuscatedKeyTest {

    private fun encode(raw: String, mask: String): String {
        val rawBytes = raw.toByteArray(Charsets.UTF_8)
        val maskBytes = mask.toByteArray(Charsets.UTF_8)
        val out = ByteArray(rawBytes.size)
        for (i in rawBytes.indices) {
            out[i] = (rawBytes[i].toInt() xor maskBytes[i % maskBytes.size].toInt()).toByte()
        }
        return Base64.getEncoder().encodeToString(out)
    }

    @Test
    fun `round trip through encode and decode returns original string`() {
        val raw = "0123456789ABCDEF0123456789ABCDEF-not-a-real-key"
        val mask = "some-random-mask-of-decent-length"
        val encoded = encode(raw, mask)
        assertEquals(raw, ObfuscatedKey.decode(encoded, mask))
    }

    @Test
    fun `short mask still works via modulo wrap`() {
        val raw = "hello world this is a longer input than the mask"
        val mask = "wrld"
        val encoded = encode(raw, mask)
        assertEquals(raw, ObfuscatedKey.decode(encoded, mask))
    }

    /**
     * The two RetroAchievements credential tests both branch on whether the
     * build has a bundled key configured, so on a checkout without one they
     * assert only the null-credentials case and can no longer fail for the
     * decode they are named after. The round-trip tests above run [encode],
     * which is a mirror of the implementation's own loop and would agree with
     * it even if both were wrong.
     *
     * This one does not: the encoded string and the mask are frozen literals,
     * produced once and checked by hand, so it fails if the decode ever stops
     * being the exact inverse of what `app/build.gradle.kts` writes. It needs
     * nothing from the build's key configuration.
     */
    @Test
    fun `decodes a frozen fixture with no help from the encoder`() {
        // "GT-FIXTURE-KEY-0123456789abcdef" XOR-wrapped with "fixture-mask" and
        // base64'd -- the same transform encodeKey() in app/build.gradle.kts applies.
        val encoded = "IT1VMjwqMXg/JF4gIzBVRERAVhlYV0RTXwgaFxEXAw=="
        val mask = "fixture-mask"

        assertEquals("GT-FIXTURE-KEY-0123456789abcdef", ObfuscatedKey.decode(encoded, mask))
    }

    @Test
    fun `empty encoded input returns empty string`() {
        assertEquals("", ObfuscatedKey.decode("", "anything"))
    }

    @Test
    fun `empty mask throws`() {
        val encoded = encode("x", "m")
        assertThrows(IllegalArgumentException::class.java) {
            ObfuscatedKey.decode(encoded, "")
        }
    }
}
