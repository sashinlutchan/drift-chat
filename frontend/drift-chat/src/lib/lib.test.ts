import { describe, expect, it } from 'vitest'
import type { ChatMessage, DisplayMessage } from '@/api/types'
import { mergeMessages } from '@/hooks/useChat'
import { formatBytes, formatDuration, initials, splitLinks, typingLabel } from './format'
import { MEDIA_TYPES } from './config'
import { checkFile } from './uploader'
import { parseRoomSlug, signUpSchema, usernameSchema } from './validation'
describe('room links', () => {
  it.each([
    ['https://drift.test/r/movie-night-abc123', 'movie-night-abc123'],
    ['https://drift.test/r/movie-night-abc123/', 'movie-night-abc123'],
    ['/r/movie-night-abc123', 'movie-night-abc123'],
    ['  movie-night-abc123  ', 'movie-night-abc123'],
    ['https://drift.test/r/abc?x=1#y', 'abc'],
  ])('reads %s', (input, slug) => expect(parseRoomSlug(input)).toBe(slug))
  it.each(['', '   ', 'not a link at all!', 'https://drift.test/rooms/1'])('rejects %j', (input) =>
    expect(parseRoomSlug(input)).toBeNull(),
  )
})

describe('usernames', () => {
  it.each(['sip', 'Sipho_1', 'a-b-c', 'x'.repeat(20)])('accepts %s', (v) =>
    expect(usernameSchema.safeParse(v).success).toBe(true),
  )
  it.each(['ab', 'x'.repeat(21), 'has space', 'émile', 'a@b'])('rejects %s', (v) =>
    expect(usernameSchema.safeParse(v).success).toBe(false),
  )
  it('needs an avatar', () => {
    expect(signUpSchema.safeParse({ username: 'sipho', name: 'S', email: 's@b.co' }).success).toBe(
      false,
    )
  })
})

describe('text helpers', () => {
  it('splits links out of text without touching the rest', () => {
    expect(splitLinks('a https://x.co/y, b')).toEqual([
      { text: 'a ', url: false },
      { text: 'https://x.co/y', url: true },
      { text: ', b', url: false },
    ])
  })
  it('writes the typing indicator in its readable form', () => {
    expect(typingLabel([])).toBe('')
    expect(typingLabel(['Sipho'])).toBe('Sipho is typing…')
    expect(typingLabel(['Sipho', 'Thandi'])).toBe('Sipho and Thandi are typing…')
    expect(typingLabel(['Sipho', 'Thandi', 'Bongi', 'Lee'])).toBe('Sipho and 3 others are typing…')
  })
  it('formats sizes, durations and initials', () => {
    expect(formatBytes(512)).toBe('512 B')
    expect(formatBytes(1024 * 1024 * 5)).toBe('5.0 MB')
    expect(formatBytes(1024 ** 3)).toBe('1.0 GB')
    expect(formatDuration(75)).toBe('1:15')
    expect(formatDuration(3725)).toBe('1:02:05')
    expect(formatDuration(NaN)).toBe('0:00')
    expect(initials('Sipho Nkosi Dlamini')).toBe('SN')
  })
})

describe('what can be uploaded', () => {
  const file = (size: number, type: string) =>
    Object.defineProperty(new File([''], 'f', { type }), 'size', { value: size })
  const FIVE_GB = 5 * 1024 ** 3
  it('accepts common pictures, audio and video up to 5 GB', () => {
    for (const type of MEDIA_TYPES) expect(checkFile(file(10, type))).toBeNull()
    expect(checkFile(file(FIVE_GB, 'video/mp4'))).toBeNull()
  })
  it('ignores codec parameters such as audio/webm;codecs=opus', () => {
    expect(checkFile(file(10, 'audio/webm;codecs=opus'))).toBeNull()
  })
  it('gives a clear reason otherwise', () => {
    expect(checkFile(file(0, 'image/png'))).toMatch(/empty/)
    expect(checkFile(file(FIVE_GB + 1, 'video/mp4'))).toMatch(/at most 5.0 GB/)
    expect(checkFile(file(10, 'application/zip'))).toMatch(/Only images/)
  })
  it('refuses SVG and anything else that can run code', () => {
    for (const type of [
      'image/svg+xml',
      'text/html',
      'application/javascript',
      'application/x-msdownload',
      'image/x-icon',
      '',
    ])
      expect(checkFile(file(10, type))).toMatch(/Only images/)
  })
})

describe('merging live and loaded messages', () => {
  const msg = (id: string, at: string, extra: Partial<DisplayMessage> = {}): DisplayMessage => ({
    id,
    roomId: 'r',
    type: 'text',
    text: id,
    fileId: null,
    sender: null,
    createdAt: `2026-06-01T12:00:0${at}Z`,
    ...extra,
  })
  it('keeps oldest first and never shows a message twice', () => {
    const merged = mergeMessages([msg('b', '2')], [
      msg('a', '1'),
      msg('b', '2'),
      msg('c', '3'),
    ] as ChatMessage[])
    expect(merged.map((m) => m.id)).toEqual(['a', 'b', 'c'])
  })
  it('keeps sending and failed messages at the end until the server confirms them', () => {
    const pending = msg('pending-x', '0', { status: 'sending', clientId: 'x' })
    const merged = mergeMessages([pending], [msg('a', '1')] as ChatMessage[])
    expect(merged.map((m) => m.id)).toEqual(['a', 'pending-x'])
  })
  it('a real message replaces its placeholder when ids match', () => {
    const merged = mergeMessages([msg('a', '1', { status: 'sending' })], [
      msg('a', '1'),
    ] as ChatMessage[])
    expect(merged).toHaveLength(1)
    expect(merged[0]!.status).toBeUndefined()
  })
})
