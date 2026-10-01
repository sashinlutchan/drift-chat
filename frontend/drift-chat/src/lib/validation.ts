import { z } from 'zod'
import { AVATAR_TYPES, config } from './config'
export const usernameSchema = z
  .string()
  .min(3, 'Username must be 3-20 characters.')
  .max(20, 'Username must be 3-20 characters.')
  .regex(/^[A-Za-z0-9_-]+$/, 'Use letters, numbers, _ and - only.')

export const nameSchema = z
  .string()
  .trim()
  .min(1, 'Name is required.')
  .max(50, 'Max 50 characters.')

export const emailSchema = z
  .string()
  .trim()
  .min(1, 'Email is required.')
  .email('Enter a valid email.')

export const avatarFileSchema = z
  .instanceof(File, { message: 'Choose an avatar image.' })
  .refine((f) => AVATAR_TYPES.includes(f.type), 'Use a JPEG, PNG or WebP image.')
  .refine((f) => f.size <= config.maxAvatarSize, 'Avatar must be 5 MB or smaller.')

export const signUpSchema = z.object({
  username: usernameSchema,
  name: nameSchema,
  email: emailSchema,
  avatar: avatarFileSchema,
})

export type SignUpValues = z.infer<typeof signUpSchema>

export const profileSchema = z.object({
  name: nameSchema,
  email: emailSchema,
  avatar: avatarFileSchema.optional(),
})

export type ProfileValues = z.infer<typeof profileSchema>

export const createRoomSchema = z.object({
  name: z.string().trim().min(1, 'Room name is required.').max(50, 'Max 50 characters.'),
})

export type CreateRoomValues = z.infer<typeof createRoomSchema>

export function parseRoomSlug(input: string): string | null {
  const trimmed = input.trim()
  if (!trimmed) return null
  const match = /\/r\/([A-Za-z0-9_-]+)\/?(?:[?#].*)?$/.exec(trimmed)
  if (match?.[1]) return match[1]
  return /^[A-Za-z0-9_-]+$/.test(trimmed) ? trimmed : null
}
