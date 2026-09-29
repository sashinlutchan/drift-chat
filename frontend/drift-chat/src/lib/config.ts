const env = import.meta.env

export const config = {
  apiBaseUrl: (env.VITE_API_BASE_URL as string | undefined) ?? '/api/v1',
  socketUrl: (env.VITE_SOCKET_URL as string | undefined) ?? '/',
  maxFileSize: Number(env.VITE_MAX_FILE_SIZE ?? 1_073_741_824),
  uploadChunkSize: Number(env.VITE_UPLOAD_CHUNK_SIZE ?? 8_388_608),
  maxMessageLength: 2000,
  maxAvatarSize: 5 * 1024 * 1024,
} as const

export const AVATAR_TYPES = ['image/jpeg', 'image/png', 'image/webp']

export function fileUrl(fileId: string | null | undefined): string | undefined {
  return fileId ? `${config.apiBaseUrl}/files/${fileId}` : undefined
}

export function roomShareUrl(slug: string): string {
  return `${window.location.origin}/r/${slug}`
}
