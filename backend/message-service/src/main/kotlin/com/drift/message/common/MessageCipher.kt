package com.drift.message.common

import com.drift.message.interfaces.IMessageCipher
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class EncryptedPayload(
    val ciphertext: ByteArray,
    val nonce: ByteArray,
    val keyVersion: Int,
)

class MessageCipher(
    private val keys: Map<Int, ByteArray>,
    private val currentKeyVersion: Int,
) : IMessageCipher {
    init {
        require(keys.isNotEmpty()) { "at least one encryption key is required" }
        require(keys.containsKey(currentKeyVersion)) { "currentKeyVersion must exist in keys" }
        keys.values.forEach { key ->
            require(key.size == KEY_BYTES) { "AES-256 keys must be exactly $KEY_BYTES bytes" }
        }
    }

    private val random = SecureRandom()

    override fun encrypt(plaintext: ByteArray): EncryptedPayload {
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keyFor(currentKeyVersion), GCMParameterSpec(TAG_BITS, nonce))
        return EncryptedPayload(cipher.doFinal(plaintext), nonce, currentKeyVersion)
    }

    override fun decrypt(
        ciphertext: ByteArray,
        nonce: ByteArray,
        keyVersion: Int,
    ): ByteArray {
        val key = keys[keyVersion] ?: error("unknown key version $keyVersion")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        return cipher.doFinal(ciphertext)
    }

    private fun keyFor(version: Int): SecretKeySpec = SecretKeySpec(keys.getValue(version), "AES")

    companion object {
        const val NONCE_BYTES = 12
        const val TAG_BITS = 128
        const val KEY_BYTES = 32
        const val TRANSFORMATION = "AES/GCM/NoPadding"

        fun singleKey(base64Key: String): MessageCipher {
            val key =
                java.util.Base64
                    .getDecoder()
                    .decode(base64Key)
            return MessageCipher(mapOf(1 to key), currentKeyVersion = 1)
        }
    }
}
