package com.drift.message.interfaces

import com.drift.message.common.EncryptedPayload

interface IMessageCipher {
    fun encrypt(plaintext: ByteArray): EncryptedPayload

    fun decrypt(
        ciphertext: ByteArray,
        nonce: ByteArray,
        keyVersion: Int,
    ): ByteArray
}
