import { type VariantProps } from 'class-variance-authority'
import { Slot } from 'radix-ui'
import * as React from 'react'
import { buttonVariants } from '@/components/ui/button-variants'
import { cn } from '@/lib/utils'
type ButtonProps = React.ComponentProps<'button'> &
  VariantProps<typeof buttonVariants> & {
    asChild?: boolean
  }

function Button({ className, variant, size, asChild = false, ...props }: ButtonProps) {
  const Comp = asChild ? Slot.Root : 'button'
  return <Comp className={cn(buttonVariants({ variant, size }), className)} {...props} />
}

export { Button }
