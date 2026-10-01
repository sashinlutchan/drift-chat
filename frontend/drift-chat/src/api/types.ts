export interface PublicUser {
  id: string
  username: string
  name: string
  avatarFileId: string | null
  online: boolean
}

export interface OwnUser extends PublicUser {
  email: string
  sessionExpiresAt: string
  createdAt: string
}

export interface Room {
  id: string
  name: string
  slug: string
  shareUrl: string
  memberCount: number
  onlineCount: number
  joined: boolean
  left: boolean
  pinned: boolean
  pinnedAt: string | null
  lastActiveAt: string
}

export interface RoomCreator {
  id: string
  name: string
  username: string
  avatarFileId: string | null
}

export interface RoomInfo {
  room: Room
  createdBy: RoomCreator | null
  createdAt: string
  idleDeleteAt: string | null
}

export interface Member {
  userId: string
  username: string
  name: string
  avatarFileId: string | null
  online: boolean
}

export type MessageType = 'text' | 'image' | 'audio' | 'video' | 'system'

export interface Sender {
  id: string | null
  name: string
  username: string | null
  avatarFileId: string | null
  deleted: boolean
}

export interface ChatMessage {
  id: string
  roomId: string
  type: MessageType
  text: string
  fileId: string | null
  sender: Sender | null
  createdAt: string
}

export interface DisplayMessage extends ChatMessage {
  clientId?: string
  status?: 'sending' | 'failed'
}

export interface MessagePage {
  messages: ChatMessage[]
  nextCursor: string | null
}

export interface UploadStarted {
  uploadId: string
  chunkSize: number
  totalChunks: number
}

export interface UploadStatus {
  uploadId: string
  roomId: string
  fileName: string
  size: number
  chunkSize: number
  totalChunks: number
  received: number[]
  missing: number[]
}

export interface Problem {
  status: number
  detail: string
  code?: string
}

export interface TypingUpdate {
  roomId: string
  users: Sender[]
}
