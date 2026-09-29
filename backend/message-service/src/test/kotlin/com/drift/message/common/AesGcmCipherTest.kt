package com.drift.message.common

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Placeholder AES-256-GCM implementation so the test setup can be verified in Phase 0.
 * The full component (with key versions) arrives in Phase 1.
 */
object AesGcmCipher {
    fun encrypt(
        plaintext: ByteArray,
        key: ByteArray,
        @Suppress("UNUSED_PARAMETER") keyVersion: Int,
    ): Pair<ByteArray, ByteArray> {
        val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        val ciphertext = cipher.doFinal(plaintext)
        return ciphertext to nonce
    }

    fun decrypt(
        ciphertext: ByteArray,
        nonce: ByteArray,
        key: ByteArray,
    ): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        return cipher.doFinal(ciphertext)
    }
}

class AesGcmCipherTest {
    private val key = "0123456789abcdef0123456789abcdef".toByteArray() // 32 bytes = AES-256

    @Test
    fun `round trip preserves content`() {
        val plaintext = "hello drift".toByteArray()
        val (ciphertext, nonce) = AesGcmCipher.encrypt(plaintext, key, 1)
        val decrypted = AesGcmCipher.decrypt(ciphertext, nonce, key)
        assertThat(decrypted).isEqualTo(plaintext)
    }

    @Test
    fun `tampered ciphertext fails authentication`() {
        val (ciphertext, nonce) = AesGcmCipher.encrypt("secret".toByteArray(), key, 1)
        ciphertext[0] = (ciphertext[0].toInt() xor 1).toByte()
        assertThatThrownBy { AesGcmCipher.decrypt(ciphertext, nonce, key) }
            .isInstanceOf(AEADBadTagException::class.java)
    }

    @Test
    fun `unique nonce per encryption`() {
        val (_, nonce1) = AesGcmCipher.encrypt("same".toByteArray(), key, 1)
        val (_, nonce2) = AesGcmCipher.encrypt("same".toByteArray(), key, 1)
        assertThat(nonce1).isNotEqualTo(nonce2)
    }

    @Test
    fun `wrong key fails decryption`() {
        val otherKey = "ffffffffffffffffffffffffffffffff".toByteArray()
        val (ciphertext, nonce) = AesGcmCipher.encrypt("secret".toByteArray(), key, 1)
        assertThatThrownBy { AesGcmCipher.decrypt(ciphertext, nonce, otherKey) }
            .isInstanceOf(AEADBadTagException::class.java)
    }
}
