import { computeCycleCardState } from "@/entities/catalog";
import { CycleDetailPage } from "@/pages/cycle-detail";
import { AppShellErrorBanner } from "@/app/AppShellErrorBanner";
import type { AppShellRoutesProps } from "@/app/appShellRoutesProps";

type AppShellCycleRouteProps = Pick<
  AppShellRoutesProps,
  "library" | "audiobook" | "downloads" | "openBook" | "error"
>;

export function AppShellCycleRoute({
  library,
  audiobook,
  downloads,
  openBook,
  error,
}: AppShellCycleRouteProps) {
  const selectedCycle = library.selectedCycle;
  if (!selectedCycle) return null;

  return (
    <>
      <CycleDetailPage
        cycle={selectedCycle}
        cardState={
          library.cycleCardStateById[selectedCycle.id] ??
          computeCycleCardState(
            selectedCycle,
            library.downloadedBookIds,
            library.tracksByBookId,
            library.progressByBook,
          )
        }
        downloadedBookIds={library.downloadedBookIds}
        tracksByBookId={library.tracksByBookId}
        progressByBook={library.progressByBook}
        onBack={() => library.setSelectedCycle(null)}
        onBookClick={(book) => void openBook(book, selectedCycle)}
        onDownloadCycle={() => void downloads.downloadCycle(selectedCycle)}
        onToggleCycleListened={() => {
          const state = library.cycleCardStateById[selectedCycle.id];
          void Promise.all(
            selectedCycle.books.map((b) => audiobook.markBookListened(b, !state?.isListened)),
          );
        }}
        onRemoveCycleDownloads={() =>
          void Promise.all(selectedCycle.books.map((b) => downloads.removeBookDownloads(b)))
        }
      />
      <AppShellErrorBanner error={error} />
    </>
  );
}
