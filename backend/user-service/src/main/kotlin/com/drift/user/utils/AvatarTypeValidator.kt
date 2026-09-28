package com.drift.user.utils

/**
 * Allowed avatar image types (spec section 3.1): JPEG, PNG, WebP.
 * Content-type checking lives here so controllers stay thin.
 */
object AvatarTypeValidator {
    val ALLOWED = setOf("image/jpeg", "image/png", "image/webp")

    fun isAllowed(contentType: String?): Boolean = contentType != null && contentType.lowercase() in ALLOWED
}
