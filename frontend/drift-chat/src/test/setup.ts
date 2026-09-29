import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach, vi } from 'vitest'

afterEach(() => cleanup())

// jsdom doesn't implement these; the UI uses them
Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: (query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    addListener: vi.fn(),
    removeListener: vi.fn(),
    dispatchEvent: vi.fn(),
  }),
})
window.HTMLElement.prototype.scrollTo =
  vi.fn() as unknown as typeof window.HTMLElement.prototype.scrollTo
window.HTMLElement.prototype.scrollIntoView = vi.fn()
class ResizeObserverStub {
  observe() {}
  unobserve() {}
  disconnect() {}
}
window.ResizeObserver = ResizeObserverStub as unknown as typeof ResizeObserver
if (!URL.createObjectURL) URL.createObjectURL = vi.fn(() => 'blob:preview')
if (!URL.revokeObjectURL) URL.revokeObjectURL = vi.fn()
