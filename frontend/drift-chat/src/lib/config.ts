const env = import.meta.env

export const config = {
  apiBaseUrl: (env.VITE_API_BASE_URL as string | undefined) ?? '/api/v1',
  socketUrl: (env.VITE_SOCKET_URL as string | undefined) ?? '/',
  maxFileSize: Number(env.VITE_MAX_FILE_SIZE ?? 5368709120),
  uploadChunkSize: Number(env.VITE_UPLOAD_CHUNK_SIZE ?? 8388608),
  maxMessageLength: 2000,
  maxAvatarSize: 5 * 1024 * 1024,
} as const

export const AVATAR_TYPES = ['image/jpeg', 'image/png', 'image/webp']

export const MEDIA_TYPES = [
  'image/jpeg',
  'image/png',
  'image/gif',
  'image/webp',
  'image/avif',
  'image/bmp',
  'audio/mpeg',
  'audio/ogg',
  'audio/wav',
  'audio/webm',
  'audio/mp4',
  'audio/aac',
  'audio/flac',
  'video/mp4',
  'video/webm',
  'video/ogg',
  'video/quicktime',
]

export const MEDIA_ACCEPT = MEDIA_TYPES.join(',')

export function fileUrl(fileId: string | null | undefined): string | undefined {
  return fileId ? `${config.apiBaseUrl}/files/${fileId}` : undefined
}

export function roomShareUrl(slug: string): string {
  return `${window.location.origin}/r/${slug}`
}
