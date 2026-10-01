package com.drift.room.utils

import java.security.SecureRandom
import java.text.Normalizer

object SlugGenerator {
    private const val SUFFIX_LENGTH = 6
    private const val MAX_BASE_LENGTH = 40
    private const val ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789"
    private val random = SecureRandom()

    fun slugify(name: String): String =
        Normalizer
            .normalize(name, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(MAX_BASE_LENGTH)
            .trim('-')

    fun generate(name: String): String {
        val base = slugify(name).ifEmpty { "room" }
        val suffix = (1..SUFFIX_LENGTH).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")
        return "$base-$suffix"
    }
}
