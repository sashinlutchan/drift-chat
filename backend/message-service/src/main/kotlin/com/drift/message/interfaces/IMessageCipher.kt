package com.drift.message.interfaces

import com.drift.message.common.EncryptedPayload

/** Encrypts and decrypts message content (AES-256-GCM, spec section 19). Implemented in `common/MessageCipher`. */
interface IMessageCipher {
    fun encrypt(plaintext: ByteArray): EncryptedPayload

    fun decrypt(
        ciphertext: ByteArray,
        nonce: ByteArray,
        keyVersion: Int,
    ): ByteArray
}
