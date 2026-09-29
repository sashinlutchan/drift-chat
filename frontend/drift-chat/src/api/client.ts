import axios, { AxiosError } from 'axios'
import { config } from '@/lib/config'
import type { Problem } from './types'

/**
 * The one Axios instance. The session lives in an HttpOnly cookie that the browser sends by itself, so there is no
 * token code here. State-changing requests echo the readable `XSRF-TOKEN` cookie in `X-XSRF-TOKEN` (spec section 19).
 */
export const api = axios.create({
  baseURL: config.apiBaseUrl,
  withCredentials: true,
  xsrfCookieName: 'XSRF-TOKEN',
  xsrfHeaderName: 'X-XSRF-TOKEN',
  withXSRFToken: true,
})

type SessionEndedListener = () => void
let onSessionEnded: SessionEndedListener | null = null

/** Registers what happens when the server says the session is gone (a 401 on any call except the start-up check). */
export function setSessionEndedListener(listener: SessionEndedListener | null) {
  onSessionEnded = listener
}

/** Requests that are allowed to 401 without meaning "your session ended". */
const SESSION_PROBES = ['/users/me']

api.interceptors.response.use(
  (response) => response,
  (error: unknown) => {
    if (axios.isAxiosError(error) && error.response?.status === 401) {
      const url = error.config?.url ?? ''
      const method = (error.config?.method ?? 'get').toLowerCase()
      const isProbe = method === 'get' && SESSION_PROBES.includes(url)
      if (!isProbe) onSessionEnded?.()
    }
    return Promise.reject(error)
  },
)

/** Fetches the `XSRF-TOKEN` cookie so the very first POST (sign-up) can send the header. Call once at start-up. */
export async function ensureCsrfCookie(): Promise<void> {
  await api.get('/users/csrf')
}

/** The problem details of a failed request, or a generic one for network errors. */
export function problemOf(error: unknown): Problem {
  if (axios.isAxiosError(error)) {
    const data = (error as AxiosError<Partial<Problem>>).response?.data
    const status = error.response?.status ?? 0
    if (data && typeof data === 'object' && typeof data.detail === 'string') {
      return { status, detail: data.detail, code: data.code }
    }
    if (status === 0)
      return { status, detail: 'Cannot reach the server. Check your connection.', code: 'NETWORK' }
    if (status === 413)
      return { status, detail: 'That file is too large.', code: 'PAYLOAD_TOO_LARGE' }
    if (status === 403)
      return { status, detail: 'You are not allowed to do that.', code: 'FORBIDDEN' }
    return { status, detail: 'Something went wrong. Please try again.', code: 'INTERNAL_ERROR' }
  }
  return {
    status: 0,
    detail: error instanceof Error ? error.message : 'Something went wrong.',
    code: 'UNKNOWN',
  }
}

export function errorMessage(error: unknown): string {
  return problemOf(error).detail
}
