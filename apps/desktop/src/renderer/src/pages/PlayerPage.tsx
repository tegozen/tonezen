import type { Book, Track } from "@shared/types";
import { strings } from "../i18n/strings";

function formatMs(ms: number): string {
  const totalSeconds = Math.floor(ms / 1000);
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${minutes}:${seconds.toString().padStart(2, "0")}`;
}

interface PlayerPageProps {
  currentTrack: Track | null;
  book: Book | null;
  isPlaying: boolean;
  positionMs: number;
  durationMs: number;
  upNext: Track[];
  favoritesCount: number;
  downloadsCount: number;
  onPlayPause: () => void;
  onSeekBy: (deltaMs: number) => void;
  onGoToLibrary: () => void;
}

export function PlayerPage({
  currentTrack,
  book,
  isPlaying,
  positionMs,
  durationMs,
  upNext,
  favoritesCount,
  downloadsCount,
  onPlayPause,
  onSeekBy,
  onGoToLibrary,
}: PlayerPageProps) {
  if (!currentTrack || !book) {
    return (
      <div className="flex min-h-[60vh] flex-col items-center justify-center text-center">
        <h2 className="text-xl font-bold">{strings.emptyPlayerTitle}</h2>
        <p className="mt-2 text-muted">{strings.emptyPlayerBody}</p>
        <button type="button" className="btn-primary mt-5" onClick={onGoToLibrary}>
          {strings.goToLibrary}
        </button>
      </div>
    );
  }

  const progress = durationMs > 0 ? positionMs / durationMs : 0;

  return (
    <div className="space-y-5">
      <div className="flex items-center justify-between">
        <h1 className="section-title">{strings.nowPlaying}</h1>
        <span className="chip-teal">{strings.offline}</span>
      </div>
      <div className="card space-y-3">
        <div>
          <div className="font-semibold">{currentTrack.title}</div>
          <div className="text-sm text-muted">{book.author}</div>
        </div>
        <div className="progress-bar">
          <div className="progress-bar-fill" style={{ width: `${Math.min(progress * 100, 100)}%` }} />
        </div>
        <div className="flex justify-between text-xs text-muted">
          <span>{formatMs(positionMs)}</span>
          <span>-{formatMs(Math.max(durationMs - positionMs, 0))}</span>
        </div>
        <div className="flex items-center justify-center gap-4">
          <button type="button" className="btn-secondary px-3 py-2" onClick={() => onSeekBy(-15000)}>
            {strings.rewind15}
          </button>
          <button type="button" className="btn-play" onClick={onPlayPause}>
            {isPlaying ? "❚❚" : "▶"}
          </button>
          <button type="button" className="btn-secondary px-3 py-2" onClick={() => onSeekBy(15000)}>
            {strings.forward15}
          </button>
        </div>
      </div>
      <div className="grid grid-cols-4 gap-2">
        <Stat label={strings.queue} value={String(upNext.length + 1)} />
        <Stat label={strings.favorites} value={String(favoritesCount)} />
        <Stat label={strings.downloads} value={String(downloadsCount)} />
        <Stat label={strings.synced} value="✓" />
      </div>
      <div>
        <h2 className="section-title">{strings.upNext}</h2>
        <div className="mt-3 space-y-3">
          {upNext.map((track) => (
            <div key={track.id} className="flex items-center justify-between">
              <div>
                <div>{track.title}</div>
                <div className="text-sm text-muted">{book.author}</div>
              </div>
              <div className="text-sm text-muted">{formatMs((track.durationMs ?? 0))}</div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div className="card px-3 py-2 text-center">
      <div className="font-semibold">{value}</div>
      <div className="text-xs text-muted">{label}</div>
    </div>
  );
}
