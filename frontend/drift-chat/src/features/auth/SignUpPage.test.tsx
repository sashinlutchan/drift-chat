import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useAuth } from '@/stores/auth'
import { SignUpPage } from './SignUpPage'

const { checkUsername, signUp } = vi.hoisted(() => ({ checkUsername: vi.fn(), signUp: vi.fn() }))
vi.mock('@/api/users', () => ({ usersApi: { checkUsername, signUp } }))
vi.mock('sonner', () => ({ toast: { success: vi.fn(), error: vi.fn() } }))

const png = () => new File([new Uint8Array([1, 2, 3])], 'me.png', { type: 'image/png' })

function renderPage(path = '/signup') {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/signup" element={<SignUpPage />} />
          <Route path="/" element={<p>Lobby</p>} />
          <Route path="/r/:slug" element={<p>Share link</p>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('sign up', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useAuth.setState({ status: 'signed-out', user: null, sessionEnded: false })
    checkUsername.mockResolvedValue(true)
  })

  it('validates every field before anything is sent', async () => {
    const user = userEvent.setup()
    renderPage()

    await user.type(screen.getByLabelText('Username'), 'a')
    await user.type(screen.getByLabelText('Name'), 'x')
    await user.clear(screen.getByLabelText('Name'))
    await user.type(screen.getByLabelText('Email'), 'not-an-email')
    await user.click(screen.getByRole('button', { name: /continue/i }))

    expect(await screen.findByText('Username must be 3-20 characters.')).toBeInTheDocument()
    expect(screen.getByText('Name is required.')).toBeInTheDocument()
    expect(screen.getByText('Enter a valid email.')).toBeInTheDocument()
    expect(screen.getByText('Choose an avatar image.')).toBeInTheDocument()
    expect(signUp).not.toHaveBeenCalled()
  })

  it('rejects characters that are not allowed in a username', async () => {
    const user = userEvent.setup()
    renderPage()

    await user.type(screen.getByLabelText('Username'), 'bad name!')

    expect(await screen.findByText('Use letters, numbers, _ and - only.')).toBeInTheDocument()
  })

  it('tells you when a username is taken and blocks submitting', async () => {
    checkUsername.mockResolvedValue(false)
    const user = userEvent.setup()
    renderPage()

    await user.type(screen.getByLabelText('Username'), 'sipho')

    expect(await screen.findByText('This username is taken.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /continue/i })).toBeDisabled()
  })

  it('shows that a free username is available', async () => {
    const user = userEvent.setup()
    renderPage()

    await user.type(screen.getByLabelText('Username'), 'sipho')

    expect(await screen.findByText('sipho is available')).toBeInTheDocument()
    expect(checkUsername).toHaveBeenCalledWith('sipho')
  })

  it('refuses an avatar that is not an image', async () => {
    const user = userEvent.setup({ applyAccept: false })
    renderPage()

    await user.upload(
      screen.getByTestId('avatar-input'),
      new File(['x'], 'notes.txt', { type: 'text/plain' }),
    )

    expect(await screen.findByText('Use a JPEG, PNG or WebP image.')).toBeInTheDocument()
  })

  it('signs up with the form values and lands on the lobby', async () => {
    signUp.mockResolvedValue({
      id: 'u1',
      username: 'sipho',
      name: 'Sipho',
      email: 's@b.co',
      avatarFileId: null,
      online: true,
      sessionExpiresAt: '2026-06-02T12:00:00Z',
      createdAt: '2026-06-01T12:00:00Z',
    })
    const user = userEvent.setup()
    renderPage()

    await user.type(screen.getByLabelText('Username'), 'sipho')
    await user.type(screen.getByLabelText('Name'), 'Sipho')
    await user.type(screen.getByLabelText('Email'), 's@b.co')
    await user.upload(screen.getByTestId('avatar-input'), png())
    await user.click(screen.getByRole('button', { name: /continue/i }))

    await waitFor(() => expect(signUp).toHaveBeenCalledTimes(1))
    expect(signUp.mock.calls[0]![0]).toMatchObject({
      username: 'sipho',
      name: 'Sipho',
      email: 's@b.co',
    })
    expect(signUp.mock.calls[0]![0].avatar).toBeInstanceOf(File)
    expect(await screen.findByText('Lobby')).toBeInTheDocument()
    expect(useAuth.getState().status).toBe('signed-in')
  })

  it('after signing up from a share link, goes to that room instead of the lobby', async () => {
    signUp.mockResolvedValue({
      id: 'u1',
      username: 'sipho',
      name: 'Sipho',
      email: 's@b.co',
      avatarFileId: null,
      online: true,
      sessionExpiresAt: '',
      createdAt: '',
    })
    const user = userEvent.setup()
    renderPage('/signup?next=%2Fr%2Fmovies-abc123')

    await user.type(screen.getByLabelText('Username'), 'sipho')
    await user.type(screen.getByLabelText('Name'), 'Sipho')
    await user.type(screen.getByLabelText('Email'), 's@b.co')
    await user.upload(screen.getByTestId('avatar-input'), png())
    await user.click(screen.getByRole('button', { name: /continue/i }))

    expect(await screen.findByText('Share link')).toBeInTheDocument()
  })

  it('never redirects to another site, whatever the next parameter says', async () => {
    signUp.mockResolvedValue({
      id: 'u1',
      username: 'sipho',
      name: 'Sipho',
      email: 's@b.co',
      avatarFileId: null,
      online: true,
      sessionExpiresAt: '',
      createdAt: '',
    })
    const user = userEvent.setup()
    renderPage('/signup?next=%2F%2Fevil.example')

    await user.type(screen.getByLabelText('Username'), 'sipho')
    await user.type(screen.getByLabelText('Name'), 'Sipho')
    await user.type(screen.getByLabelText('Email'), 's@b.co')
    await user.upload(screen.getByTestId('avatar-input'), png())
    await user.click(screen.getByRole('button', { name: /continue/i }))

    expect(await screen.findByText('Lobby')).toBeInTheDocument()
  })

  it('shows the server’s "username taken" answer on the field', async () => {
    signUp.mockRejectedValue(
      Object.assign(new Error('conflict'), {
        isAxiosError: true,
        response: {
          status: 409,
          data: { status: 409, detail: 'This username is taken.', code: 'USERNAME_TAKEN' },
        },
      }),
    )
    const user = userEvent.setup()
    renderPage()

    await user.type(screen.getByLabelText('Username'), 'sipho')
    await user.type(screen.getByLabelText('Name'), 'Sipho')
    await user.type(screen.getByLabelText('Email'), 's@b.co')
    await user.upload(screen.getByTestId('avatar-input'), png())
    await user.click(screen.getByRole('button', { name: /continue/i }))

    expect(await screen.findAllByText('This username is taken.')).not.toHaveLength(0)
    expect(screen.queryByText('Lobby')).not.toBeInTheDocument()
  })

  it('says "Your session has ended." after an expiry', () => {
    useAuth.setState({ status: 'signed-out', user: null, sessionEnded: true })
    renderPage()

    expect(screen.getByRole('alert')).toHaveTextContent('Your session has ended.')
  })
})
