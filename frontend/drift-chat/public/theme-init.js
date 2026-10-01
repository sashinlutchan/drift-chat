try {
  var t = localStorage.getItem('drift-theme')
  var dark =
    t === 'dark' || ((!t || t === 'system') && matchMedia('(prefers-color-scheme: dark)').matches)
  if (dark) document.documentElement.classList.add('dark')
} catch (e) {}
