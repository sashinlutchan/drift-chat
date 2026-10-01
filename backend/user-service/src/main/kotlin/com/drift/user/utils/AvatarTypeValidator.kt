package com.drift.user.utils

object AvatarTypeValidator {
    val ALLOWED = setOf("image/jpeg", "image/png", "image/webp")

    fun isAllowed(contentType: String?): Boolean = contentType != null && contentType.lowercase() in ALLOWED
}
