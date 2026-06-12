import type { Book, Track } from "@shared/types";
import { strings } from "../i18n/strings";

function formatMs(ms: number): string {
  const totalSeconds = Math.floor(ms / 1000);
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${minutes}:${seconds.toString().padStart(2, "0")}`;
}

interface BookFlowPageProps {
  book: Book;
  tracks: Track[];
  tab: "player" | "details";
  isPlaying: boolean;
  isFavorite: boolean;
  positionMs: number;
  durationMs: number;
  currentTrack: Track | null;
  showDownloadSheet: boolean;
  onBack: () => void;
  onTabChange: (tab: "player" | "details") => void;
  onPlayPause: () => void;
  onSeekBy: (deltaMs: number) => void;
  onTrackClick: (track: Track) => void;
  onDownloadRequest: () => void;
  onDownloadConfirm: () => void;
  onDownloadDismiss: () => void;
  onToggleFavorite: () => void;
  onStartListening: () => void;
}

export function BookFlowPage({
  book,
  tracks,
  tab,
  isPlaying,
  isFavorite,
  positionMs,
  durationMs,
  currentTrack,
  showDownloadSheet,
  onBack,
  onTabChange,
  onPlayPause,
  onSeekBy,
  onTrackClick,
  onDownloadRequest,
  onDownloadConfirm,
  onDownloadDismiss,
  onToggleFavorite,
  onStartListening,
}: BookFlowPageProps) {
  const progress = durationMs > 0 ? positionMs / durationMs : 0;
  const activeTrack = currentTrack ?? tracks.find((track) => track.localPath) ?? tracks[0];

  if (tab === "details") {
    return (
      <div className="space-y-5">
        <Header onBack={onBack} tab={tab} onTabChange={onTabChange} />
        <div className="flex gap-4">
          <div className="aspect-[0.78] w-2/5 rounded-2xl bg-surface-raised" />
          <div className="flex-1 space-y-2">
            <h2 className="text-xl font-bold">{book.title}</h2>
            <p className="text-muted">{book.author}</p>
            <p className="text-sm text-muted">
              {strings.tracks}: {tracks.length}
            </p>
          </div>
        </div>
        <div className="flex gap-2">
          <span className="chip-teal">{strings.tabAudiobooks}</span>
          {tracks.some((track) => track.localPath) && <span className="chip-teal">{strings.offline}</span>}
        </div>
        <div>
          <h3 className="font-semibold">{strings.aboutThisBook}</h3>
          <p className="text-sm text-muted">{book.title}</p>
        </div>
        <button type="button" className="btn-primary w-full" onClick={onDownloadRequest}>
          {strings.download}
        </button>
        <div className="flex gap-3">
          <button type="button" className="btn-secondary flex-1" onClick={onToggleFavorite}>
            {isFavorite ? strings.favorites : strings.favorite}
          </button>
          <button type="button" className="btn-primary flex-1 bg-amber text-app" onClick={onStartListening}>
            {strings.startListening}
          </button>
        </div>
        {showDownloadSheet && (
          <Sheet title={strings.downloadConfirmTitle} onDismiss={onDownloadDismiss}>
            <button type="button" className="btn-primary mt-4 w-full" onClick={onDownloadConfirm}>
              {strings.downloadOffline}
            </button>
          </Sheet>
        )}
      </div>
    );
  }

  return (
    <div className="space-y-5">
      <Header onBack={onBack} tab={tab} onTabChange={onTabChange} />
      <div className="mx-auto aspect-square w-2/3 rounded-2xl bg-surface-raised" />
      <div className="text-center">
        <h2 className="text-2xl font-bold">{book.title}</h2>
        <p className="text-muted">{book.author}</p>
      </div>
      <div className="flex justify-center gap-2">
        {tracks.some((track) => track.localPath) && <span className="chip-teal">{strings.offline}</span>}
        {book.contentType === "audiobook" && <span className="chip-green">{strings.synced}</span>}
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
      <div className="progress-bar">
        <div className="progress-bar-fill" style={{ width: `${Math.min(progress * 100, 100)}%` }} />
      </div>
      <div className="flex justify-between text-xs text-muted">
        <span>{formatMs(positionMs)}</span>
        <span>-{formatMs(Math.max(durationMs - positionMs, 0))}</span>
      </div>
      <div>
        <h3 className="section-title">{strings.tracks}</h3>
        <div className="mt-3 space-y-2">
          {tracks.map((track) => (
            <button
              key={track.id}
              type="button"
              className={`card w-full text-left ${activeTrack?.id === track.id ? "border-amber/40" : ""}`}
              onClick={() => onTrackClick(track)}
            >
              <div className="flex items-center justify-between">
                <span>{track.title}</span>
                <span className="text-sm text-muted">{formatMs(track.durationMs ?? 0)}</span>
              </div>
            </button>
          ))}
        </div>
      </div>
    </div>
  );
}

function Header({
  onBack,
  tab,
  onTabChange,
}: {
  onBack: () => void;
  tab: "player" | "details";
  onTabChange: (tab: "player" | "details") => void;
}) {
  return (
    <div className="flex items-center justify-between">
      <button type="button" className="btn-secondary px-3 py-2" onClick={onBack}>
        {strings.back}
      </button>
      <div className="flex rounded-xl border border-border bg-surface-raised p-1">
        <button
          type="button"
          className={`rounded-lg px-4 py-2 ${tab === "player" ? "bg-teal/15 text-teal" : "text-muted"}`}
          onClick={() => onTabChange("player")}
        >
          {strings.navPlayer}
        </button>
        <button
          type="button"
          className={`rounded-lg px-4 py-2 ${tab === "details" ? "bg-teal/15 text-teal" : "text-muted"}`}
          onClick={() => onTabChange("details")}
        >
          {strings.details}
        </button>
      </div>
      <button type="button" className="btn-secondary px-3 py-2">
        ⋮
      </button>
    </div>
  );
}

function Sheet({
  title,
  onDismiss,
  children,
}: {
  title: string;
  onDismiss: () => void;
  children: React.ReactNode;
}) {
  return (
    <div className="sheet-overlay">
      <div className="sheet-panel">
        <div className="mb-3 flex items-center justify-between">
          <h3 className="font-semibold">{title}</h3>
          <button type="button" onClick={onDismiss}>
            ✕
          </button>
        </div>
        {children}
        <button type="button" className="btn-secondary mt-3 w-full" onClick={onDismiss}>
          {strings.cancel}
        </button>
      </div>
    </div>
  );
}
