import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { Room } from '@/api/types'
import { JoinRoomDialog } from './JoinRoomDialog'
import { RoomCard } from './RoomCard'

const { join } = vi.hoisted(() => ({ join: vi.fn() }))
vi.mock('@/api/rooms', () => ({ roomsApi: { join } }))
vi.mock('sonner', () => ({ toast: { success: vi.fn(), error: vi.fn() } }))

const room: Room = {
  id: 'r1',
  name: 'Movie Night',
  slug: 'movie-night-abc123',
  shareUrl: 'https://drift.test/r/movie-night-abc123',
  memberCount: 3,
  onlineCount: 2,
  joined: false,
  left: false,
  pinned: false,
  pinnedAt: null,
  lastActiveAt: '2026-06-01T12:00:00Z',
}

function renderDialog(props: Partial<React.ComponentProps<typeof JoinRoomDialog>> = {}) {
  const onOpenChange = vi.fn()
  const onJoined = vi.fn()
  render(
    <QueryClientProvider client={new QueryClient()}>
      <JoinRoomDialog room={room} onOpenChange={onOpenChange} onJoined={onJoined} {...props} />
    </QueryClientProvider>,
  )
  return { onOpenChange, onJoined }
}

describe('join confirmation dialog', () => {
  beforeEach(() => vi.clearAllMocks())

  it('asks "Do you want to join {room}?"', () => {
    renderDialog()

    expect(screen.getByRole('alertdialog')).toHaveTextContent('Do you want to join Movie Night?')
  })

  it('Cancel closes it without joining', async () => {
    const { onOpenChange, onJoined } = renderDialog()

    await userEvent.click(screen.getByRole('button', { name: 'Cancel' }))

    expect(onOpenChange).toHaveBeenCalledWith(false)
    expect(join).not.toHaveBeenCalled()
    expect(onJoined).not.toHaveBeenCalled()
  })

  it('Yes joins and enters the room', async () => {
    join.mockResolvedValue({ ...room, joined: true })
    const { onOpenChange, onJoined } = renderDialog()

    await userEvent.click(screen.getByRole('button', { name: 'Yes, join' }))

    await waitFor(() =>
      expect(onJoined).toHaveBeenCalledWith(expect.objectContaining({ id: 'r1', joined: true })),
    )
    expect(join).toHaveBeenCalledWith('r1')
    expect(onOpenChange).toHaveBeenCalledWith(false)
  })

  it('stays open when joining fails', async () => {
    join.mockRejectedValue(new Error('nope'))
    const { onJoined } = renderDialog()

    await userEvent.click(screen.getByRole('button', { name: 'Yes, join' }))

    await waitFor(() => expect(join).toHaveBeenCalled())
    expect(onJoined).not.toHaveBeenCalled()
    expect(screen.getByRole('alertdialog')).toBeInTheDocument()
  })

  it('renders nothing when there is no room', () => {
    renderDialog({ room: null })

    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument()
  })
})

describe('room card actions', () => {
  const handlers = () => ({
    onOpen: vi.fn(),
    onJoin: vi.fn(),
    onTogglePin: vi.fn(),
    onShare: vi.fn(),
  })

  it('offers Join to non-members, Open to members and Rejoin to people who left', async () => {
    const { rerender } = render(<RoomCard room={room} {...handlers()} />)
    expect(screen.getByRole('button', { name: 'Join' })).toBeInTheDocument()

    rerender(<RoomCard room={{ ...room, left: true }} {...handlers()} />)
    expect(screen.getByRole('button', { name: 'Rejoin' })).toBeInTheDocument()

    rerender(<RoomCard room={{ ...room, joined: true }} {...handlers()} />)
    expect(screen.getByRole('button', { name: 'Open' })).toBeInTheDocument()
    expect(screen.getByText('Joined')).toBeInTheDocument()
  })

  it('Join asks for confirmation, Open goes straight in', async () => {
    const h = handlers()
    const { rerender } = render(<RoomCard room={room} {...h} />)
    await userEvent.click(screen.getByRole('button', { name: 'Join' }))
    expect(h.onJoin).toHaveBeenCalledWith(room)
    expect(h.onOpen).not.toHaveBeenCalled()

    rerender(<RoomCard room={{ ...room, joined: true }} {...h} />)
    await userEvent.click(screen.getByRole('button', { name: 'Open' }))
    expect(h.onOpen).toHaveBeenCalled()
  })

  it('shows counts, a pin marker and pin / share actions', async () => {
    const h = handlers()
    render(<RoomCard room={{ ...room, pinned: true }} {...h} />)

    expect(screen.getByText('3 members')).toBeInTheDocument()
    expect(screen.getByText('2 online')).toBeInTheDocument()
    expect(screen.getByLabelText('Pinned')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Unpin Movie Night' }))
    await userEvent.click(screen.getByRole('button', { name: 'Share Movie Night' }))
    expect(h.onTogglePin).toHaveBeenCalled()
    expect(h.onShare).toHaveBeenCalled()
  })
})
