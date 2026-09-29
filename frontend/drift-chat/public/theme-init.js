// Applies the saved theme before the first paint so there is no light flash in dark mode.
// A separate file (not inline) so the strict Content-Security-Policy needs no 'unsafe-inline' for scripts.
try {
  var t = localStorage.getItem('drift-theme')
  var dark = t === 'dark' || ((!t || t === 'system') && matchMedia('(prefers-color-scheme: dark)').matches)
  if (dark) document.documentElement.classList.add('dark')
} catch (e) {}
