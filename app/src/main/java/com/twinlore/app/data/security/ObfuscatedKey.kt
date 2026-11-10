package com.twinlore.app.data.security

/**
 * Decodes a build-time XOR-encoded API key. The encoding happens in
 * `app/build.gradle.kts`; this helper is the runtime inverse.
 *
 * This is not real cryptography. It only prevents automated secret scanners
 * and casual unpackers from finding literal API key strings in the APK. Any
 * determined attacker with a decompiler can still recover the plaintext -
 * that is an accepted tradeoff for avoiding a backend.
 */
object ObfuscatedKey {
    fun decode(base64Encoded: String, mask: String): String {
        if (base64Encoded.isEmpty()) return ""
        require(mask.isNotEmpty()) { "mask must be non-empty" }

        val bytes = java.util.Base64.getDecoder().decode(base64Encoded)
        val maskBytes = mask.toByteArray(Charsets.UTF_8)
        val out = ByteArray(bytes.size)
        for (i in bytes.indices) {
            out[i] = (bytes[i].toInt() xor maskBytes[i % maskBytes.size].toInt()).toByte()
        }
        return String(out, Charsets.UTF_8)
    }
}
