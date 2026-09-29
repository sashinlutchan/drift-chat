package com.drift.file.models

/** What a stored file is for (spec section 18): avatars are small and public to any session, media belongs to a room. */
enum class FileKind {
    AVATAR,
    MEDIA,
}
