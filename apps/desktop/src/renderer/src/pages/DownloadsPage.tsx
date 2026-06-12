import {
  DownloadsIcon,
  PauseIcon,
  StorageIcon,
  TrashIcon,
} from "../components/TonezenIcons";
import { strings } from "../i18n/strings";

interface DownloadSummary {
  bookId: string;
  title: string;
  author?: string;
  contentType: string;
  downloadedTracks: number;
  totalTracks: number;
  sizeBytes: number;
  downloadProgress: number;
}

interface DownloadsPageProps {
  summaries: DownloadSummary[];
  usedBytes: number;
  selectedTab: number;
  showDeleteConfirm: boolean;
  onTabChange: (tab: number) => void;
  onDeleteAll: () => void;
  onShowDeleteConfirm: (show: boolean) => void;
}

function formatGb(bytes: number): string {
  return `${(bytes / (1024 ** 3)).toFixed(1)} GB`;
}

export function DownloadsPage({
  summaries,
  usedBytes,
  selectedTab,
  showDeleteConfirm,
  onTabChange,
  onDeleteAll,
  onShowDeleteConfirm,
}: DownloadsPageProps) {
  const filtered = summaries.filter((item) =>
    selectedTab === 0 ? item.contentType === "audiobook" : item.contentType === "music",
  );

  return (
    <div className="space-y-5">
      <h1 className="flex items-center gap-2 text-2xl font-bold">
        <DownloadsIcon className="h-6 w-6 text-teal" />
        {strings.downloads}
      </h1>
      <div className="flex border-b border-border">
        {[strings.tabAudiobooks, strings.tabMusic].map((label, index) => (
          <button
            key={label}
            type="button"
            className={`flex-1 pb-3 ${selectedTab === index ? "border-b-2 border-teal text-teal" : "text-muted"}`}
            onClick={() => onTabChange(index)}
          >
            {label}
          </button>
        ))}
      </div>
      <div>
        <div className="flex items-center gap-2 font-semibold">
          <StorageIcon className="h-5 w-5 text-teal" />
          {formatGb(usedBytes)} saved offline
        </div>
        <div className="text-sm text-muted">Manage storage from Profile</div>
      </div>
      <div className="space-y-3">
        {filtered.map((item) => (
          <div key={item.bookId} className="card flex items-center gap-3">
            <div className="flex h-14 w-14 items-center justify-center rounded-xl bg-surface-raised text-teal">
              <DownloadsIcon className="h-6 w-6" />
            </div>
            <div className="min-w-0 flex-1">
              <div className="font-semibold">{item.title}</div>
              <div className="text-sm text-muted">{item.author}</div>
              <div className="text-xs text-muted">
                {item.downloadedTracks}/{item.totalTracks}
              </div>
            </div>
            <div className="text-sm text-teal">{Math.round(item.downloadProgress * 100)}%</div>
          </div>
        ))}
      </div>
      <div className="flex gap-3">
        <button type="button" className="btn-secondary flex flex-1 items-center justify-center gap-2" disabled>
          <PauseIcon className="h-5 w-5" />
          {strings.pauseAll}
        </button>
        <button type="button" className="btn-danger flex flex-1 items-center justify-center gap-2" onClick={() => onShowDeleteConfirm(true)}>
          <TrashIcon className="h-5 w-5" />
          {strings.deleteAll}
        </button>
      </div>
      {showDeleteConfirm && (
        <div className="sheet-overlay flex items-center justify-center p-5">
          <div className="modal-panel">
            <h2 className="text-lg font-semibold">{strings.deleteAllConfirmTitle}</h2>
            <p className="mt-2 text-sm text-muted">{strings.deleteAllConfirmBody}</p>
            <div className="mt-4 flex gap-3">
              <button type="button" className="btn-secondary flex-1" onClick={() => onShowDeleteConfirm(false)}>
                {strings.cancel}
              </button>
              <button type="button" className="btn-danger flex flex-1 items-center justify-center gap-2" onClick={onDeleteAll}>
                <TrashIcon className="h-5 w-5" />
                {strings.deleteAll}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
