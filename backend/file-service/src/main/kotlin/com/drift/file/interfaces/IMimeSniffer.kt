package com.drift.file.interfaces

/** Detects a file's real type from its bytes; the client-declared type is never trusted (spec section 13.4). */
interface IMimeSniffer {
    fun detect(head: ByteArray): String
}
