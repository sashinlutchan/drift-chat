import { create } from 'zustand'

export type ThemeChoice = 'light' | 'dark' | 'system'

const KEY = 'drift-theme'

function read(): ThemeChoice {
  try {
    const value = localStorage.getItem(KEY)
    return value === 'light' || value === 'dark' ? value : 'system'
  } catch {
    return 'system'
  }
}

/** Applies the choice to <html>. "system" follows the operating system. */
export function applyTheme(choice: ThemeChoice) {
  const dark =
    choice === 'dark' || (choice === 'system' && matchMedia('(prefers-color-scheme: dark)').matches)
  document.documentElement.classList.toggle('dark', dark)
}

interface ThemeState {
  choice: ThemeChoice
  setChoice: (choice: ThemeChoice) => void
}

export const useTheme = create<ThemeState>((set) => ({
  choice: read(),
  setChoice: (choice) => {
    try {
      localStorage.setItem(KEY, choice)
    } catch {
      // storage can be unavailable (private mode); the theme still applies for this visit
    }
    applyTheme(choice)
    set({ choice })
  },
}))
