import { isBookFullyDownloaded } from "@/entities/catalog";
import { BookDetailPage } from "@/pages/book-detail";
import { AppShellErrorBanner } from "@/app/AppShellErrorBanner";
import type { AppShellRoutesProps } from "@/app/appShellRoutesProps";

type AppShellBookRouteProps = Pick<
  AppShellRoutesProps,
  | "library"
  | "downloadQueue"
  | "music"
  | "audiobook"
  | "downloads"
  | "currentTrack"
  | "positionMs"
  | "savedBookProgress"
  | "bookIsListened"
  | "error"
>;

export function AppShellBookRoute({
  library,
  downloadQueue,
  music,
  audiobook,
  downloads,
  currentTrack,
  positionMs,
  savedBookProgress,
  bookIsListened,
  error,
}: AppShellBookRouteProps) {
  const selectedBook = library.selectedBook;
  if (!selectedBook) return null;

  return (
    <>
      <BookDetailPage
        book={selectedBook}
        tracks={library.tracks}
        currentTrackId={
          !music.musicMode && currentTrack && library.tracks.some((track) => track.id === currentTrack.id)
            ? currentTrack.id
            : null
        }
        playbackPositionMs={positionMs}
        downloadQueue={downloadQueue.state}
        onBack={() => library.setSelectedBook(null)}
        onTrackClick={(track) => void audiobook.playBookTrack(track)}
        onDownloadRequest={() => void downloads.downloadAllBookTracks(selectedBook)}
        onDownloadTrack={(track) => void downloads.downloadBookTrack(selectedBook, track)}
        onToggleBookListened={() => void audiobook.markBookListened(selectedBook, !bookIsListened)}
        onRemoveBookDownloads={() => void downloads.removeBookDownloads(selectedBook)}
        onMarkTrackListened={(track, listened) => void audiobook.markTrackListened(selectedBook, track, listened)}
        onRemoveTrackDownload={(track) => void downloads.removeTrackDownload(selectedBook, track)}
        onContinue={() => void audiobook.continueBook()}
        savedTrackId={savedBookProgress?.trackId ?? null}
        savedPositionMs={savedBookProgress?.positionMs ?? 0}
        isBookListened={bookIsListened}
        hasDownloads={library.tracks.some((t) => t.localPath)}
        allDownloaded={isBookFullyDownloaded(selectedBook.id, library.tracksByBookId)}
      />
      <AppShellErrorBanner error={error} />
    </>
  );
}
