import { DownloadsIcon } from "@/shared/ui/TonezenIcons";

interface TrackDownloadButtonProps {
  downloading?: boolean;
  progress?: number | null;
  onClick: () => void;
  disabled?: boolean;
}

export function TrackDownloadButton({
  downloading = false,
  progress = null,
  onClick,
  disabled = false,
}: TrackDownloadButtonProps) {
  const showProgress = downloading && progress != null && progress > 0;

  return (
    <button
      type="button"
      className="track-download-btn"
      onClick={(event) => {
        event.stopPropagation();
        onClick();
      }}
      onPointerDown={(event) => event.stopPropagation()}
      disabled={disabled || downloading}
      aria-label="Скачать офлайн"
    >
      {downloading ? (
        <span className="track-download-btn-progress">
          {showProgress ? `${Math.round(progress * 100)}%` : "…"}
        </span>
      ) : (
        <DownloadsIcon className="h-[18px] w-[18px] text-muted" />
      )}
    </button>
  );
}
