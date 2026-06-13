import type { ReactNode, MouseEvent, PointerEvent } from "react";
import { formatMs } from "../lib/formatTime";

interface TrackListRowProps {
  title: string;
  subtitle?: string;
  durationMs?: number;
  isActive?: boolean;
  listenProgress?: number | null;
  clickEnabled?: boolean;
  onClick: () => void;
  leading?: ReactNode;
  trailing?: ReactNode;
}

function stopRowActivation(event: MouseEvent | PointerEvent) {
  event.stopPropagation();
}

export function TrackListRow({
  title,
  subtitle,
  durationMs,
  isActive = false,
  listenProgress,
  clickEnabled = true,
  onClick,
  leading,
  trailing,
}: TrackListRowProps) {
  const barProgress =
    listenProgress != null && listenProgress > 0 ? Math.min(listenProgress, 1) : null;

  return (
    <div className={`track-list-row ${isActive ? "track-list-row-active" : ""}`}>
      <button type="button" className="track-list-row-main" onClick={onClick} disabled={!clickEnabled}>
        {leading && <div className="track-list-row-leading">{leading}</div>}
        <div className="min-w-0 flex-1">
          <div className={`truncate text-sm ${isActive ? "font-semibold text-amber" : "font-normal text-ink"}`}>
            {title}
          </div>
          {subtitle ? (
            <div className={`truncate text-sm ${isActive ? "text-teal" : "text-muted"}`}>{subtitle}</div>
          ) : null}
        </div>
        <div
          className="track-list-row-trailing"
          onClick={stopRowActivation}
          onPointerDown={stopRowActivation}
        >
          <span className="shrink-0 text-sm text-muted">{formatMs(durationMs ?? 0)}</span>
          {trailing}
        </div>
      </button>
      {barProgress != null && (
        <div className="track-list-row-progress">
          <div className="track-list-row-progress-fill" style={{ width: `${barProgress * 100}%` }} />
        </div>
      )}
    </div>
  );
}
