package com.hyperwhisper.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Symmetric encryption primitive for at-rest secrets. Implementations must be
 * infallible from the caller's perspective for [decrypt] (return null on any
 * tamper / corruption) so a corrupted secrets store never crashes the app.
 *
 * Production binding is [AndroidKeystoreSecretCipher]; tests use an in-memory
 * fake to keep SecretsRepository tests JVM-runnable without Robolectric.
 */
interface SecretCipher {
    /** Encrypts [plaintext], returning a self-describing string blob (IV + ciphertext, base64). */
    fun encrypt(plaintext: String): String

    /** Decrypts a blob produced by [encrypt]. Returns null on any failure. */
    fun decrypt(blob: String): String?
}

/**
 * Android Keystore-backed AES-256/GCM implementation.
 *
 * The master key is generated lazily on first use and stored in the AndroidKeyStore
 * under [keyAlias]. Each value is encrypted with a fresh 12-byte IV and serialized
 * as `base64(iv || ciphertext)`.
 *
 * The key is *not* user-authentication-bound by design: the app needs to decrypt
 * API keys silently for HTTP requests during voice transcription. User-facing
 * reveal/export is gated separately via [BiometricGate].
 */
class AndroidKeystoreSecretCipher(
    private val keyAlias: String = DEFAULT_ALIAS,
) : SecretCipher {

    override fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        require(iv.size == GCM_IV_BYTES) { "Unexpected IV length ${iv.size}" }
        val ct = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val combined = ByteArray(iv.size + ct.size).also {
            System.arraycopy(iv, 0, it, 0, iv.size)
            System.arraycopy(ct, 0, it, iv.size, ct.size)
        }
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    override fun decrypt(blob: String): String? {
        return try {
            val combined = Base64.decode(blob, Base64.NO_WRAP)
            if (combined.size <= GCM_IV_BYTES) return null
            val iv = combined.copyOfRange(0, GCM_IV_BYTES)
            val ct = combined.copyOfRange(GCM_IV_BYTES, combined.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(ct), Charsets.UTF_8)
        } catch (t: Throwable) {
            null
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = ks.getKey(keyAlias, null) as? SecretKey
        if (existing != null) return existing

        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        gen.init(
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return gen.generateKey()
    }

    companion object {
        const val DEFAULT_ALIAS = "hyperwhisper_secrets_v1"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_BYTES = 12
        private const val GCM_TAG_BITS = 128
    }
}
