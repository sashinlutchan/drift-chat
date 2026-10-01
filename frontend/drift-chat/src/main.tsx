import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './styles/index.css'
import App from './App.tsx'
import { applyTheme, useTheme } from './stores/theme'
applyTheme(useTheme.getState().choice)
matchMedia('(prefers-color-scheme: dark)').addEventListener('change', () => {
  if (useTheme.getState().choice === 'system') applyTheme('system')
})
createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
