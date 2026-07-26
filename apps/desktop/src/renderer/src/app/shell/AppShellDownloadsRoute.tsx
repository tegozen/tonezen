import { DownloadsPage } from "@/pages/downloads";
import { AppShellErrorBanner } from "./AppShellErrorBanner";
import type { AppShellRoutesProps } from "../model";

type AppShellDownloadsRouteProps = Pick<
  AppShellRoutesProps,
  "library" | "downloadQueue" | "deleteDownload" | "error"
>;

export function AppShellDownloadsRoute({
  library,
  downloadQueue,
  deleteDownload,
  error,
}: AppShellDownloadsRouteProps) {
  return (
    <>
      <DownloadsPage
        downloadQueue={downloadQueue.state}
        completedItems={library.completedDownloads}
        books={library.books}
        cycles={library.cycles}
        onCancelTrack={(bookId, trackId) => void downloadQueue.cancelTrack(bookId, trackId)}
        onCancelAll={() => void downloadQueue.cancelAll()}
        onDeleteCompleted={(bookId, trackId) => {
          void downloadQueue.cancelTrack(bookId, trackId);
          void deleteDownload.mutateAsync({ bookId, trackId }).then(() => library.refreshLibrary());
        }}
      />
      <AppShellErrorBanner error={error} />
    </>
  );
}
