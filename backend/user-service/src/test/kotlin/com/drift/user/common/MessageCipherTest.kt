package com.drift.user.common

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.Base64
import javax.crypto.AEADBadTagException

class MessageCipherTest {
    private val key32 = "0123456789abcdef0123456789abcdef".toByteArray()
    private val cipher = MessageCipher(mapOf(1 to key32, 2 to "ffffffffffffffffffffffffffffffff".toByteArray()), currentKeyVersion = 1)

    @Test
    fun `round trip preserves content`() {
        val payload = cipher.encrypt("hello drift".toByteArray())
        val decrypted = cipher.decrypt(payload.ciphertext, payload.nonce, payload.keyVersion)
        assertThat(decrypted).isEqualTo("hello drift".toByteArray())
    }

    @Test
    fun `tampered ciphertext fails authentication`() {
        val payload = cipher.encrypt("secret".toByteArray())
        val tampered = payload.ciphertext.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }
        assertThatThrownBy { cipher.decrypt(tampered, payload.nonce, payload.keyVersion) }
            .isInstanceOf(AEADBadTagException::class.java)
    }

    @Test
    fun `tampered nonce fails authentication`() {
        val payload = cipher.encrypt("secret".toByteArray())
        val tamperedNonce = payload.nonce.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }
        assertThatThrownBy { cipher.decrypt(payload.ciphertext, tamperedNonce, payload.keyVersion) }
            .isInstanceOf(AEADBadTagException::class.java)
    }

    @Test
    fun `unique nonce per message`() {
        val a = cipher.encrypt("same".toByteArray())
        val b = cipher.encrypt("same".toByteArray())
        assertThat(a.nonce).isNotEqualTo(b.nonce)
    }

    @Test
    fun `key version is stored with the payload`() {
        assertThat(cipher.encrypt("x".toByteArray()).keyVersion).isEqualTo(1)
        val v2 = MessageCipher(mapOf(2 to key32), currentKeyVersion = 2)
        assertThat(v2.encrypt("x".toByteArray()).keyVersion).isEqualTo(2)
    }

    @Test
    fun `decryption with an older key version still works`() {
        val v1 = MessageCipher(mapOf(1 to key32), currentKeyVersion = 1)
        val payload = v1.encrypt("old data".toByteArray())
        val rotated = MessageCipher(mapOf(1 to key32, 2 to "ffffffffffffffffffffffffffffffff".toByteArray()), currentKeyVersion = 2)
        assertThat(rotated.decrypt(payload.ciphertext, payload.nonce, payload.keyVersion))
            .isEqualTo("old data".toByteArray())
    }

    @Test
    fun `singleKey factory builds a version 1 cipher from base64`() {
        val base64 = Base64.getEncoder().encodeToString(key32)
        val c = MessageCipher.singleKey(base64)
        val payload = c.encrypt("factory".toByteArray())
        assertThat(c.decrypt(payload.ciphertext, payload.nonce, payload.keyVersion)).isEqualTo("factory".toByteArray())
    }

    @Test
    fun `rejects keys that are not 32 bytes`() {
        assertThatCode { MessageCipher(mapOf(1 to "short".toByteArray()), 1) }
            .hasMessageContaining("32 bytes")
    }
}
