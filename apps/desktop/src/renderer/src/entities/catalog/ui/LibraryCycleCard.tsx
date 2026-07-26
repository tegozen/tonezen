import type { Cycle } from "@core/types";
import { CheckCircleIcon, PauseIcon, PlayIcon } from "@/shared/ui/TonezenIcons";
import { ContinueResumeMeta } from "./ContinueResumeMeta";
import { CycleCover } from "./CycleCover";
import type { CycleCardState } from "../lib/cycleUtils";

interface LibraryCycleCardProps {
  cycle: Cycle;
  state: CycleCardState;
  isPlaying: boolean;
  onClick: () => void;
  onPlayClick: () => void;
}

export function LibraryCycleCard({
  cycle,
  state,
  isPlaying,
  onClick,
  onPlayClick,
}: LibraryCycleCardProps) {
  const progressPercent =
    state.progressFraction != null ? Math.round(state.progressFraction * 100) : null;
  const showProgress = cycle.books.length > 0;
  const continueState = state.continueState;

  return (
    <div className="library-cycle-card">
      <CycleCover cycle={cycle} className="aspect-[0.78] w-full" onClick={onClick} />
      {state.isDownloaded && (
        <CheckCircleIcon className="library-cycle-downloaded text-teal" aria-label="Офлайн" />
      )}
      {(continueState || showProgress) && (
        <div className="library-cycle-footer-meta">
          {continueState && <ContinueResumeMeta state={continueState} variant="overlay" />}
          {showProgress && (
            <span className="library-cycle-progress">{`${progressPercent ?? 0}%`}</span>
          )}
        </div>
      )}
      <button
        type="button"
        className={`compact-media-play-btn ${isPlaying ? "compact-media-play-btn-playing" : ""}`}
        onClick={onPlayClick}
        aria-label={isPlaying ? "Пауза" : "Воспроизвести"}
      >
        {isPlaying ? <PauseIcon className="h-[18px] w-[18px]" /> : <PlayIcon className="h-[18px] w-[18px]" />}
      </button>
    </div>
  );
}
