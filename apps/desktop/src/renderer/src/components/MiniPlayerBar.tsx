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
        <div className="h-12 w-12 rounded-lg bg-surface-raised" />
        <div className="min-w-0 flex-1">
          <div className="truncate font-medium">{title}</div>
          <div className="truncate text-sm text-muted">{subtitle}</div>
        </div>
        <button
          type="button"
          className="rounded-full px-3 py-2 text-lg"
          onClick={(e) => {
            e.stopPropagation();
            onPlayPause();
          }}
        >
          {isPlaying ? "❚❚" : "▶"}
        </button>
      </button>
    </div>
  );
}
