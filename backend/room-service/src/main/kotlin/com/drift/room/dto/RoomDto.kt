package com.drift.room.dto

import com.drift.room.models.MemberProfile
import com.drift.room.models.RoomInfo
import com.drift.room.models.RoomSummary
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class CreateRoomRequest(
    @field:NotBlank(message = "Room name is required.")
    @field:Size(max = 50, message = "Room name can be at most 50 characters.")
    val name: String,
)

data class RoomDto(
    val id: UUID,
    val name: String,
    val slug: String,
    val shareUrl: String,
    val memberCount: Int,
    val onlineCount: Int,
    val joined: Boolean,
    val left: Boolean,
    val pinned: Boolean,
    val pinnedAt: Instant?,
    val lastActiveAt: Instant,
) {
    companion object {
        fun from(
            summary: RoomSummary,
            publicBaseUrl: String,
        ): RoomDto =
            RoomDto(
                id = summary.room.id,
                name = summary.room.name,
                slug = summary.room.slug,
                shareUrl = "${publicBaseUrl.trimEnd('/')}/r/${summary.room.slug}",
                memberCount = summary.memberCount,
                onlineCount = summary.onlineCount,
                joined = summary.joined,
                left = summary.left,
                pinned = summary.pinnedAt != null,
                pinnedAt = summary.pinnedAt,
                lastActiveAt = summary.room.lastActiveAt,
            )
    }
}

data class CreatorDto(
    val id: UUID,
    val name: String,
    val username: String,
    val avatarFileId: UUID?,
)

data class RoomInfoDto(
    val room: RoomDto,
    val createdBy: CreatorDto?,
    val createdAt: Instant,
    val idleDeleteAt: Instant?,
) {
    companion object {
        fun from(
            info: RoomInfo,
            publicBaseUrl: String,
        ): RoomInfoDto =
            RoomInfoDto(
                room = RoomDto.from(info.summary, publicBaseUrl),
                createdBy = info.createdBy?.let { CreatorDto(it.id, it.name, it.username, it.avatarFileId) },
                createdAt = info.summary.room.createdAt,
                idleDeleteAt = info.idleDeleteAt,
            )
    }
}

data class MemberDto(
    val userId: UUID,
    val username: String,
    val name: String,
    val avatarFileId: UUID?,
    val online: Boolean,
) {
    companion object {
        fun from(member: MemberProfile): MemberDto =
            MemberDto(member.userId, member.username, member.name, member.avatarFileId, member.online)
    }
}
