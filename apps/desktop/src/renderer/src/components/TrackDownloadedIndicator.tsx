import { CheckCircleIcon } from "./TonezenIcons";
import { strings } from "../i18n/strings";

export function TrackDownloadedIndicator() {
  return (
    <span className="track-downloaded-indicator" aria-label={strings.offline}>
      <CheckCircleIcon className="h-[18px] w-[18px] text-teal" />
    </span>
  );
}
