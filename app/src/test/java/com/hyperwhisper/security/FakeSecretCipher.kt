package com.hyperwhisper.security

import java.util.Base64

/**
 * Test-only [SecretCipher] that base64-encodes plaintext with a fixed prefix.
 * Reversible, deterministic, JVM-runnable — just enough to verify that the
 * repository never persists raw plaintext and that round-trips work.
 */
class FakeSecretCipher : SecretCipher {

    override fun encrypt(plaintext: String): String =
        PREFIX + Base64.getEncoder().encodeToString(plaintext.toByteArray(Charsets.UTF_8))

    override fun decrypt(blob: String): String? = try {
        if (!blob.startsWith(PREFIX)) null
        else String(Base64.getDecoder().decode(blob.removePrefix(PREFIX)), Charsets.UTF_8)
    } catch (_: Throwable) {
        null
    }

    companion object {
        const val PREFIX = "fake-enc:"
    }
}
