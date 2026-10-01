import { Link } from 'react-router'
import { Button } from '@/components/ui/button'
export function NotFoundPage() {
  return (
    <div className="grid h-full min-h-[60dvh] place-items-center px-4 text-center">
      <div>
        <p className="text-6xl font-semibold text-primary">404</p>
        <h1 className="mt-2 text-xl font-semibold">Nothing here</h1>
        <p className="mt-1 text-muted-foreground">That page doesn’t exist.</p>
        <Button asChild className="mt-5">
          <Link to="/">Back to rooms</Link>
        </Button>
      </div>
    </div>
  )
}
