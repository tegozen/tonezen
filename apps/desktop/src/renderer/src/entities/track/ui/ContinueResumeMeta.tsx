import type { BookContinueState } from "@/entities/book";
import { formatMs } from "@/shared/lib/formatTime";

interface ContinueResumeMetaProps {
  state: BookContinueState;
  variant?: "overlay" | "inline" | "button";
}

export function ContinueResumeMeta({ state, variant = "overlay" }: ContinueResumeMetaProps) {
  return (
    <div className={`continue-resume-meta continue-resume-meta-${variant}`}>
      <span className="continue-resume-meta-label">Продолжить</span>
      <span className="continue-resume-meta-chapter" title={state.trackTitle}>
        {state.trackTitle}
      </span>
      <span className="continue-resume-meta-time">{formatMs(state.positionMs)}</span>
    </div>
  );
}
