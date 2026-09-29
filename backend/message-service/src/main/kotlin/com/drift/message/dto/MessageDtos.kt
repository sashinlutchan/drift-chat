package com.drift.message.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

/** Who sent a message; the wire shows "Deleted user" when [deleted] (spec section 2). */
data class SenderDto(
    val id: UUID?,
    val name: String,
    val username: String?,
    val avatarFileId: UUID?,
    val deleted: Boolean,
)

/** A message as clients see it, over REST and Socket.IO (`message:new`). */
data class MessageDto(
    val id: UUID,
    val roomId: UUID,
    val type: String,
    /** Message text, or the file name for image, audio and video messages. */
    val text: String,
    val fileId: UUID?,
    val sender: SenderDto?,
    val createdAt: Instant,
)

/** A page of history, newest first (spec section 14). */
data class MessagePageDto(
    val messages: List<MessageDto>,
    /** Pass as `before` to load the next, older page; `null` when there is nothing older. */
    val nextCursor: UUID?,
)

/** Body of `POST /messages/uploads`. */
data class StartUploadRequest(
    val roomId: UUID,
    @field:NotBlank(message = "File name is required.")
    @field:Size(max = 255, message = "File name can be at most 255 characters.")
    val fileName: String,
    @field:Positive(message = "File size must be greater than 0.")
    val size: Long,
    @field:NotBlank(message = "File type is required.")
    val mimeType: String,
)

/** Reply to starting an upload. */
data class UploadStartedDto(
    val uploadId: String,
    val chunkSize: Long,
    val totalChunks: Int,
)

/** Which chunks have arrived, so a client can resume (spec section 13.6). */
data class UploadStatusDto(
    val uploadId: String,
    val roomId: UUID,
    val fileName: String,
    val size: Long,
    val chunkSize: Long,
    val totalChunks: Int,
    val received: List<Int>,
    val missing: List<Int>,
)
