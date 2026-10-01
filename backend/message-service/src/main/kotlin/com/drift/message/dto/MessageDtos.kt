package com.drift.message.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class SenderDto(
    val id: UUID?,
    val name: String,
    val username: String?,
    val avatarFileId: UUID?,
    val deleted: Boolean,
)

data class MessageDto(
    val id: UUID,
    val roomId: UUID,
    val type: String,
    val text: String,
    val fileId: UUID?,
    val sender: SenderDto?,
    val createdAt: Instant,
)

data class MessagePageDto(
    val messages: List<MessageDto>,
    val nextCursor: UUID?,
)

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

data class UploadStartedDto(
    val uploadId: String,
    val chunkSize: Long,
    val totalChunks: Int,
)

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
