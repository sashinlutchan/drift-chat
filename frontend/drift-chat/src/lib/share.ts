import { toast } from 'sonner'

/** Copies a room link, or opens the native share sheet on phones (spec section 5.2). */
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
    // Clipboard access can be blocked; show the link so it can still be copied by hand
    toast.error('Could not copy automatically', { description: url })
  }
}
