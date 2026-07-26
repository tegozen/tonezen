import { CheckCircleIcon } from "@/shared/ui/TonezenIcons";

export function TrackDownloadedIndicator() {
  return (
    <span className="track-downloaded-indicator" aria-label="Офлайн">
      <CheckCircleIcon className="h-[18px] w-[18px] text-teal" />
    </span>
  );
}
