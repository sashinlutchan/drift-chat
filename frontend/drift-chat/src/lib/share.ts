import { toast } from 'sonner'
export async function shareLink(url: string, title: string): Promise<void> {
  const canShare = typeof navigator.share === 'function' && matchMedia('(pointer: coarse)').matches
  if (canShare) {
    try {
      await navigator.share({ title, url })
      return
    } catch (error) {
      if ((error as DOMException).name === 'AbortError') return
    }
  }
  await copyLink(url)
}

export async function copyLink(url: string): Promise<void> {
  try {
    await navigator.clipboard.writeText(url)
    toast.success('Link copied')
  } catch {
    toast.error('Could not copy automatically', { description: url })
  }
}
