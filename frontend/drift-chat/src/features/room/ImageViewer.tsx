import { Dialog as DialogPrimitive } from 'radix-ui'
import { X } from 'lucide-react'

interface Props {
  src: string | null
  name: string
  onClose: () => void
}

/** Full-screen image viewer (spec section 7.3). Esc or the close button dismisses it. */
export function ImageViewer({ src, name, onClose }: Props) {
  return (
    <DialogPrimitive.Root open={src !== null} onOpenChange={(open) => !open && onClose()}>
      <DialogPrimitive.Portal>
        <DialogPrimitive.Overlay className="fixed inset-0 z-50 bg-black/90 data-[state=open]:animate-in data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=open]:fade-in-0" />
        <DialogPrimitive.Content
          className="fixed inset-0 z-50 flex items-center justify-center p-4 outline-none"
          onClick={onClose}
          aria-describedby={undefined}
        >
          <DialogPrimitive.Title className="sr-only">{name}</DialogPrimitive.Title>
          {src && (
            <img
              src={src}
              alt={name}
              className="max-h-full max-w-full rounded-lg object-contain shadow-2xl"
              onClick={(e) => e.stopPropagation()}
            />
          )}
          <DialogPrimitive.Close className="absolute top-4 right-4 rounded-full bg-white/10 p-2 text-white backdrop-blur transition-colors hover:bg-white/20 focus-visible:ring-[3px] focus-visible:ring-ring">
            <X className="size-5" />
            <span className="sr-only">Close image</span>
          </DialogPrimitive.Close>
        </DialogPrimitive.Content>
      </DialogPrimitive.Portal>
    </DialogPrimitive.Root>
  )
}
