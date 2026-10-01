package com.drift.file.interfaces

interface IMimeSniffer {
    fun detect(head: ByteArray): String
}
