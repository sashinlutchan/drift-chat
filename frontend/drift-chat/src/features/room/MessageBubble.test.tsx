import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { describe, expect, it, vi } from 'vitest'
import type { DisplayMessage } from '@/api/types'
import { usePresence } from '@/stores/presence'
import { MessageBubble } from './MessageBubble'
import { MessageList } from './MessageList'

const base: DisplayMessage = {
  id: 'm1',
  roomId: 'r1',
  type: 'text',
  text: 'hello there',
  fileId: null,
  sender: { id: 'u1', name: 'Sipho', username: 'sipho', avatarFileId: null, deleted: false },
  createdAt: '2026-06-01T12:00:00Z',
}

function bubble(
  message: Partial<DisplayMessage> = {},
  props: { own?: boolean; first?: boolean } = {},
) {
  const onImage = vi.fn()
  const onRetry = vi.fn()
  render(
    <MemoryRouter>
      <MessageBubble
        message={{ ...base, ...message }}
        own={props.own ?? false}
        first={props.first ?? true}
        onImage={onImage}
        onRetry={onRetry}
      />
    </MemoryRouter>,
  )
  return { onImage, onRetry }
}

describe('message rendering', () => {
  it('shows the sender, the time and the text', () => {
    bubble()

    expect(screen.getByText('Sipho')).toBeInTheDocument()
    expect(screen.getByText('hello there')).toBeInTheDocument()
    // no presence news for this user yet, so the dot says Offline until a `presence:update` says otherwise
    expect(screen.getByRole('img', { name: 'Offline' })).toBeInTheDocument()
  })

  it('the online dot follows live presence updates', () => {
    usePresence.getState().set('u1', true)
    bubble()

    expect(screen.getByRole('img', { name: 'Online' })).toBeInTheDocument()
    usePresence.setState({ online: {} })
  })

  it('makes URLs clickable and opens them safely', () => {
    bubble({ text: 'see https://example.com/page now' })

    const link = screen.getByRole('link', { name: 'https://example.com/page' })
    expect(link).toHaveAttribute('href', 'https://example.com/page')
    expect(link).toHaveAttribute('target', '_blank')
    expect(link.getAttribute('rel')).toContain('noopener')
  })

  it('never turns message text into HTML', () => {
    bubble({ text: '<img src=x onerror=alert(1)> <b>bold</b>' })

    expect(
      screen.getByText('<img src=x onerror=alert(1)> <b>bold</b>', { exact: false }),
    ).toBeInTheDocument()
    expect(document.querySelector('img[src="x"]')).toBeNull()
    expect(document.querySelector('b')).toBeNull()
  })

  it('shows an image thumbnail with alt text that opens the full-screen viewer', async () => {
    const { onImage } = bubble({ type: 'image', text: 'holiday.png', fileId: 'f1' })

    const img = screen.getByAltText('holiday.png')
    expect(img).toHaveAttribute('src', '/api/v1/files/f1')
    await userEvent.click(img)
    expect(onImage).toHaveBeenCalledWith('/api/v1/files/f1', 'holiday.png')
  })

  it('plays audio inline with play/pause, a seek bar and a duration', () => {
    bubble({ type: 'audio', text: 'voice.mp3', fileId: 'f2' })

    expect(screen.getByRole('button', { name: 'Play' })).toBeInTheDocument()
    expect(screen.getByRole('slider', { name: 'Seek' })).toBeInTheDocument()
    expect(screen.getByText('0:00 / 0:00')).toBeInTheDocument()
  })

  it('plays video inline with native controls', () => {
    bubble({ type: 'video', text: 'clip.mp4', fileId: 'f3' })

    const video = document.querySelector('video')!
    expect(video).toHaveAttribute('src', '/api/v1/files/f3')
    expect(video).toHaveAttribute('controls')
  })

  it('shows system notices inline, without a bubble', () => {
    bubble({ type: 'system', text: 'Thandi joined the room', sender: null })

    expect(screen.getByRole('status')).toHaveTextContent('Thandi joined the room')
    expect(screen.queryByTestId('message')).not.toBeInTheDocument()
  })

  it('shows "Deleted user" for messages from deleted accounts', () => {
    bubble({
      sender: { id: null, name: 'Deleted user', username: null, avatarFileId: null, deleted: true },
    })

    expect(screen.getByText('Deleted user')).toBeInTheDocument()
  })

  it('a failed message offers Retry', async () => {
    const { onRetry } = bubble({ status: 'failed', clientId: 'c1' }, { own: true })

    await userEvent.click(screen.getByRole('button', { name: /retry/i }))

    expect(onRetry).toHaveBeenCalledWith('c1')
    expect(screen.getByText('Not sent')).toBeInTheDocument()
  })

  it('a message being sent says so', () => {
    bubble({ status: 'sending', clientId: 'c1' }, { own: true })

    expect(screen.getByText('Sending…')).toBeInTheDocument()
  })

  it('groups consecutive messages from one sender under a single name', () => {
    const at = (m: number) => `2026-06-01T12:0${m}:00Z`
    const messages: DisplayMessage[] = [
      { ...base, id: 'a', text: 'one', createdAt: at(0) },
      { ...base, id: 'b', text: 'two', createdAt: at(1) },
      { ...base, id: 'c', text: 'three', createdAt: at(2) },
      {
        ...base,
        id: 'd',
        text: 'other',
        createdAt: at(3),
        sender: { ...base.sender!, id: 'u2', name: 'Thandi' },
      },
    ]
    render(
      <MemoryRouter>
        <MessageList
          messages={messages}
          meId="me"
          hasOlder={false}
          loadingOlder={false}
          onLoadOlder={vi.fn()}
          onImage={vi.fn()}
          onRetry={vi.fn()}
        />
      </MemoryRouter>,
    )

    expect(screen.getAllByText('Sipho')).toHaveLength(1)
    expect(screen.getAllByText('Thandi')).toHaveLength(1)
    expect(screen.getAllByTestId('message')).toHaveLength(4)
  })

  it('aligns your own messages to the right and shows the start-of-room marker', () => {
    render(
      <MemoryRouter>
        <MessageList
          messages={[{ ...base, sender: { ...base.sender!, id: 'me' } }]}
          meId="me"
          hasOlder={false}
          loadingOlder={false}
          onLoadOlder={vi.fn()}
          onImage={vi.fn()}
          onRetry={vi.fn()}
        />
      </MemoryRouter>,
    )

    expect(screen.getByTestId('message').className).toContain('flex-row-reverse')
    expect(screen.getByText('This is the start of the room.')).toBeInTheDocument()
  })

  it('shows an empty state for a new room', () => {
    render(
      <MemoryRouter>
        <MessageList
          messages={[]}
          meId="me"
          hasOlder={false}
          loadingOlder={false}
          onLoadOlder={vi.fn()}
          onImage={vi.fn()}
          onRetry={vi.fn()}
        />
      </MemoryRouter>,
    )

    expect(screen.getByText(/No messages yet/)).toBeInTheDocument()
  })
})
