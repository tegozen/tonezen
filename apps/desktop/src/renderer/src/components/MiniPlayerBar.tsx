import { PauseIcon, PlayIcon, PlayerIcon } from "./TonezenIcons";

interface MiniPlayerBarProps {
  title: string | null;
  subtitle: string | null;
  isPlaying: boolean;
  onBarClick: () => void;
  onPlayPause: () => void;
}

export function MiniPlayerBar({ title, subtitle, isPlaying, onBarClick, onPlayPause }: MiniPlayerBarProps) {
  if (!title) return null;
  return (
    <div className="mini-player">
      <button type="button" className="flex w-full items-center gap-3 text-left" onClick={onBarClick}>
        <div className="flex h-12 w-12 items-center justify-center rounded-lg bg-surface-raised text-teal">
          <PlayerIcon className="h-6 w-6" />
        </div>
        <div className="min-w-0 flex-1">
          <div className="truncate font-medium">{title}</div>
          <div className="truncate text-sm text-muted">{subtitle}</div>
        </div>
        <button
          type="button"
          className="flex h-10 w-10 items-center justify-center rounded-full text-[0] text-ink"
          onClick={(e) => {
            e.stopPropagation();
            onPlayPause();
          }}
        >
          {isPlaying ? <PauseIcon className="h-5 w-5 text-base" /> : <PlayIcon className="h-5 w-5 text-base" />}
        </button>
      </button>
    </div>
  );
}
