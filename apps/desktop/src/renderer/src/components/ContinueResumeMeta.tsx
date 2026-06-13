import type { BookContinueState } from "../lib/bookTrackUtils";
import { formatMs } from "../lib/formatTime";
import { strings } from "../i18n/strings";

interface ContinueResumeMetaProps {
  state: BookContinueState;
  variant?: "overlay" | "inline" | "button";
}

export function ContinueResumeMeta({ state, variant = "overlay" }: ContinueResumeMetaProps) {
  return (
    <div className={`continue-resume-meta continue-resume-meta-${variant}`}>
      <span className="continue-resume-meta-label">{strings.resume}</span>
      <span className="continue-resume-meta-chapter" title={state.trackTitle}>
        {state.trackTitle}
      </span>
      <span className="continue-resume-meta-time">{formatMs(state.positionMs)}</span>
    </div>
  );
}
