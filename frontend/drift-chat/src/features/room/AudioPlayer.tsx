import { Pause, Play } from 'lucide-react'
import { useRef, useState } from 'react'
import { Button } from '@/components/ui/button'
import { formatDuration } from '@/lib/format'

/** Inline audio: play/pause, a seekable progress bar and the duration (spec section 7.3). Nothing to download. */
export function AudioPlayer({ src, name }: { src: string; name: string }) {
  const audio = useRef<HTMLAudioElement>(null)
  const [playing, setPlaying] = useState(false)
  const [time, setTime] = useState(0)
  const [duration, setDuration] = useState(0)

  const toggle = () => {
    const el = audio.current
    if (!el) return
    if (el.paused) void el.play()
    else el.pause()
  }

  return (
    <div className="flex w-64 max-w-full items-center gap-2 sm:w-72" aria-label={`Audio: ${name}`}>
      <audio
        ref={audio}
        src={src}
        preload="metadata"
        onPlay={() => setPlaying(true)}
        onPause={() => setPlaying(false)}
        onEnded={() => setPlaying(false)}
        onTimeUpdate={(e) => setTime(e.currentTarget.currentTime)}
        onLoadedMetadata={(e) => setDuration(e.currentTarget.duration)}
        onDurationChange={(e) => setDuration(e.currentTarget.duration)}
      />
      <Button
        type="button"
        size="icon-sm"
        variant="secondary"
        className="rounded-full"
        onClick={toggle}
        aria-label={playing ? 'Pause' : 'Play'}
      >
        {playing ? <Pause className="fill-current" /> : <Play className="fill-current" />}
      </Button>
      <input
        type="range"
        min={0}
        max={Number.isFinite(duration) && duration > 0 ? duration : 0}
        step="any"
        value={time}
        aria-label="Seek"
        className="h-1.5 min-w-0 flex-1 cursor-pointer accent-current"
        onChange={(e) => {
          const el = audio.current
          if (el) el.currentTime = Number(e.target.value)
        }}
      />
      <span className="w-20 shrink-0 text-right text-xs tabular-nums opacity-80">
        {formatDuration(time)} / {formatDuration(duration)}
      </span>
    </div>
  )
}
