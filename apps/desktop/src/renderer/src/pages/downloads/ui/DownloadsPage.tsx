import {
  groupDownloadsForPage,
  type CompletedDownloadItem,
  type DownloadsBookGroup,
  type DownloadsPageItem,
} from "@core/downloads/downloadsPageState";
import type { Book, Cycle } from "@core/types";
import type { DownloadQueueState } from "@core/downloads/downloadQueueState";
import { PAGE_TITLE_TOP_SCROLL_PX } from "@/shared/lib/layoutChrome";
import { TitleTopChrome } from "@/widgets/top-chrome";
import { TrackDownloadButton } from "@/features/downloads";
import { TrackListRow } from "@/entities/catalog";

interface DownloadsPageProps {
  downloadQueue: DownloadQueueState;
  completedItems: CompletedDownloadItem[];
  books: Book[];
  cycles: Cycle[];
  onCancelTrack: (bookId: string, trackId: string) => void;
  onCancelAll: () => void;
  onDeleteCompleted: (bookId: string, trackId: string) => void;
}

export function DownloadsPage({
  downloadQueue,
  completedItems,
  books,
  cycles,
  onCancelTrack,
  onCancelAll,
  onDeleteCompleted,
}: DownloadsPageProps) {
  const groups = groupDownloadsForPage({ downloadQueue, completedItems, books, cycles });
  const hasAudiobooks =
    groups.audiobookCycles.length > 0 || groups.audiobookStandaloneBooks.length > 0;
  const hasMusic = groups.music.length > 0;
  const hasActiveItems = downloadQueue.queuedItems.length > 0;
  const isEmpty = !hasAudiobooks && !hasMusic;

  return (
    <div className="profile-page">
      <div
        className="scroll-under-chrome space-y-4"
        style={{ paddingTop: PAGE_TITLE_TOP_SCROLL_PX }}
      >
        {downloadQueue.pausedForNetwork && (
          <p className="text-sm text-muted">Ожидание сети</p>
        )}
        {hasActiveItems && (
          <div className="flex justify-end">
            <button type="button" className="text-sm text-teal" onClick={onCancelAll}>
              Остановить все загрузки
            </button>
          </div>
        )}
        {hasAudiobooks && (
          <section className="space-y-2">
            <h2 className="text-sm font-semibold text-muted">Аудиокниги</h2>
            {groups.audiobookCycles.map((cycle) => (
              <div key={cycle.cycleId} className="space-y-3">
                <h3 className="px-1 text-base font-semibold text-ink">{cycle.title}</h3>
                {cycle.books.map((book) => (
                  <DownloadsBookGroupView
                    key={book.bookId}
                    book={book}
                    onCancelTrack={onCancelTrack}
                    onDeleteCompleted={onDeleteCompleted}
                  />
                ))}
              </div>
            ))}
            {groups.audiobookStandaloneBooks.map((book) => (
              <DownloadsBookGroupView
                key={book.bookId}
                book={book}
                onCancelTrack={onCancelTrack}
                onDeleteCompleted={onDeleteCompleted}
              />
            ))}
          </section>
        )}
        {hasMusic && (
          <section className="space-y-2">
            <h2 className="text-sm font-semibold text-muted">Музыка</h2>
            {groups.music.map((item) => (
              <DownloadsItemRow
                key={`${item.bookId}:${item.trackId}`}
                item={item}
                onCancel={() => onCancelTrack(item.bookId, item.trackId)}
                onDelete={() => onDeleteCompleted(item.bookId, item.trackId)}
              />
            ))}
          </section>
        )}
        {isEmpty && (
          <div className="py-12 text-center">
            <p className="text-sm text-muted">Нет загрузок</p>
          </div>
        )}
      </div>
      <TitleTopChrome title="Загрузки" />
    </div>
  );
}

function DownloadsBookGroupView({
  book,
  onCancelTrack,
  onDeleteCompleted,
}: {
  book: DownloadsBookGroup;
  onCancelTrack: (bookId: string, trackId: string) => void;
  onDeleteCompleted: (bookId: string, trackId: string) => void;
}) {
  const activeCount = book.items.filter((item) => item.status !== "COMPLETED").length;
  const completedCount = book.items.length - activeCount;

  return (
    <div className="space-y-2">
      <div className="px-1">
        <h4 className="text-sm font-semibold text-teal">{book.title}</h4>
        <p className="text-xs text-muted">
          {activeCount > 0
            ? `Сейчас: ${activeCount} · Загружено: ${completedCount}`
            : `Загружено: ${completedCount}`}
        </p>
      </div>
      {book.items.map((item) => (
        <DownloadsItemRow
          key={`${item.bookId}:${item.trackId}`}
          item={item}
          onCancel={() => onCancelTrack(item.bookId, item.trackId)}
          onDelete={() => onDeleteCompleted(item.bookId, item.trackId)}
        />
      ))}
    </div>
  );
}

function DownloadsItemRow({
  item,
  onCancel,
  onDelete,
}: {
  item: DownloadsPageItem;
  onCancel: () => void;
  onDelete: () => void;
}) {
  const isCompleted = item.status === "COMPLETED";
  const subtitle = downloadItemSubtitle(item);

  return (
    <TrackListRow
      title={item.title}
      subtitle={subtitle ?? undefined}
      durationMs={item.durationMs}
      isActive={item.status === "DOWNLOADING"}
      clickEnabled={false}
      onClick={() => {}}
      trailing={
        isCompleted ? (
          <button type="button" className="text-sm text-muted" onClick={onDelete}>
            Удалить загрузку
          </button>
        ) : (
          <TrackDownloadButton
            downloading={item.status === "DOWNLOADING"}
            progress={item.progress}
            onClick={onCancel}
          />
        )
      }
    />
  );
}

function downloadItemSubtitle(item: DownloadsPageItem): string | null {
  if (item.status === "PAUSED_OFFLINE") return "Ожидание сети";
  if (item.status === "QUEUED") return "В очереди";
  if (item.status === "DOWNLOADING") return "Загрузка…";
  if (item.status === "COMPLETED") {
    return item.contentType === "audiobook" ? "Загружено" : item.subtitle ?? "Загружено";
  }
  return item.subtitle;
}
