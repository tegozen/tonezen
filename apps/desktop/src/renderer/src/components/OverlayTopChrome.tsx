import type { ReactNode } from "react";
import { ChevronLeftIcon } from "./TonezenIcons";

interface OverlayTopChromeProps {
  title: ReactNode;
  onBack: () => void;
  trailing?: ReactNode;
}

export function OverlayTopChrome({ title, onBack, trailing }: OverlayTopChromeProps) {
  return (
    <div className="overlay-chrome-wrap library-chrome-wrap">
      <div className="library-chrome-shell">
        <div className="overlay-chrome-header">
          <button type="button" className="overlay-back-btn" onClick={onBack}>
            <ChevronLeftIcon className="h-5 w-5 shrink-0" />
            <span>Назад</span>
          </button>
          <div className="overlay-chrome-title">{title}</div>
          <div className="overlay-chrome-trailing">{trailing ?? <span className="overlay-chrome-spacer" />}</div>
        </div>
      </div>
    </div>
  );
}
