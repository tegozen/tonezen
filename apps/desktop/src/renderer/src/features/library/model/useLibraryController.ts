import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type { AudiobookProgress, Book, Cycle, SessionState, Track } from "@core/types";
import {
  buildMusicTrackListForCatalogUpdate,
  visibleMusicTrackList,
  type MusicListTrack,
} from "@core/catalog/musicList";
import { completedDownloadItems } from "@core/downloads/downloadsPageState";
import type { DownloadQueueState } from "@core/downloads/downloadQueueState";
import type { LibraryFilter } from "@core/platform/navigation";
import {
  buildTracksByBookId,
  computeCycleCardState,
  filterAndSortCycles,
  isBookFullyDownloaded,
} from "@/entities/cycle";
import { getTonezenApi } from "@/shared/api";

export type RefreshLibraryOptions = { rebuildMusic?: boolean; reconcileLocalPaths?: boolean };

export const defaultLibraryFilter: LibraryFilter = { contentFilter: "all", sortOrder: "recent" };

interface UseLibraryControllerOptions {
  sessionState: SessionState;
  downloadQueueState: DownloadQueueState;
}

export function useLibraryController({ sessionState, downloadQueueState }: UseLibraryControllerOptions) {
  const api = getTonezenApi();

  const [cycles, setCycles] = useState<Cycle[]>([]);
  const [books, setBooks] = useState<Book[]>([]);
  const [allTracks, setAllTracks] = useState<Track[]>([]);
  const [selectedCycle, setSelectedCycle] = useState<Cycle | null>(null);
  const [selectedBook, setSelectedBook] = useState<Book | null>(null);
  const [tracks, setTracks] = useState<Track[]>([]);
  const [query, setQuery] = useState("");
  const [filter, setFilter] = useState<LibraryFilter>(defaultLibraryFilter);
  const [showFilterSheet, setShowFilterSheet] = useState(false);
  const [isLoading, setIsLoading] = useState(true);
  const [storageUsed, setStorageUsed] = useState(0);
  const [pendingCount, setPendingCount] = useState(0);
  const [lastSyncAtEpochMs, setLastSyncAtEpochMs] = useState<number | null>(null);
  const [progressList, setProgressList] = useState<AudiobookProgress[]>([]);
  const [musicTracks, setMusicTracks] = useState<MusicListTrack[]>([]);

  const musicStartedInSessionRef = useRef(false);
  const refreshLibraryRef = useRef<(options?: RefreshLibraryOptions) => Promise<void>>(async () => {});

  const refreshLibrary = useCallback(
    async (options?: RefreshLibraryOptions) => {
      const rebuildMusic = options?.rebuildMusic ?? true;
      const reconcileLocalPaths = options?.reconcileLocalPaths ?? true;
      const [library, stats, sync, progress] = await Promise.all([
        api.db.getLibrarySnapshot({ reconcileLocalPaths }),
        api.download.storageStats(),
        api.sync.status(),
        api.db.getAllProgress(),
      ]);
      setCycles(library.cycles);
      setBooks(library.books);
      setAllTracks(library.tracks);
      setStorageUsed(stats.usedBytes);
      setPendingCount(sync.pendingCount);
      setLastSyncAtEpochMs(sync.lastSyncAtEpochMs);
      setProgressList(progress);
      if (rebuildMusic) {
        setMusicTracks((current) =>
          buildMusicTrackListForCatalogUpdate(
            current,
            library.books,
            library.tracks,
            musicStartedInSessionRef.current,
          ),
        );
      }
      setIsLoading(false);
    },
    [api],
  );

  refreshLibraryRef.current = refreshLibrary;

  // Stable identity so consumer hooks (e.g. music playback) don't need to re-subscribe
  // whenever refreshLibrary's own dependencies change.
  const refreshLibraryStable = useCallback(
    (options?: RefreshLibraryOptions) => refreshLibraryRef.current(options),
    [],
  );

  useEffect(() => {
    if (sessionState === "Unauthenticated") return;
    if (sessionState === "AuthenticatedOffline") {
      void refreshLibraryStable({ rebuildMusic: true, reconcileLocalPaths: false }).then(() =>
        refreshLibraryStable({ rebuildMusic: true, reconcileLocalPaths: true }),
      );
      return;
    }
    setIsLoading(true);
    const sync = api.catalog.sync().then(
      () => true,
      () => false,
    );
    void refreshLibraryStable({ rebuildMusic: true, reconcileLocalPaths: false })
      .then(() => sync)
      .then(() => refreshLibraryStable({ rebuildMusic: true, reconcileLocalPaths: true }))
      .catch(() => refreshLibraryStable({ rebuildMusic: true, reconcileLocalPaths: true }));
  }, [sessionState, refreshLibraryStable, api]);

  useEffect(() => {
    if (sessionState === "Unauthenticated" || sessionState === "AuthenticatedOffline") return;
    return api.catalog.onUpdated(() => {
      void refreshLibraryStable({ rebuildMusic: true });
    });
  }, [sessionState, refreshLibraryStable, api]);

  useEffect(() => {
    let timer: ReturnType<typeof setTimeout> | undefined;
    const unsubscribe = api.download.onQueueState((state) => {
      if (!state.activeTrackId && state.queuedItems.length === 0) {
        clearTimeout(timer);
        timer = setTimeout(() => {
          void refreshLibraryStable();
        }, 300);
      }
    });
    return () => {
      clearTimeout(timer);
      unsubscribe();
    };
  }, [refreshLibraryStable, api]);

  const tracksByBookId = useMemo(() => buildTracksByBookId(allTracks), [allTracks]);

  const downloadedBookIds = useMemo(() => {
    const ids = new Set<string>();
    for (const book of books) {
      if (isBookFullyDownloaded(book.id, tracksByBookId)) ids.add(book.id);
    }
    return ids;
  }, [books, tracksByBookId]);

  const progressByBook = useMemo(
    () => new Map(progressList.map((p) => [p.bookId, p])),
    [progressList],
  );

  const cycleCardStateById = useMemo(() => {
    const map: Record<string, ReturnType<typeof computeCycleCardState>> = {};
    for (const cycle of cycles) {
      map[cycle.id] = computeCycleCardState(cycle, downloadedBookIds, tracksByBookId, progressByBook);
    }
    return map;
  }, [cycles, downloadedBookIds, tracksByBookId, progressByBook]);

  const filteredCycles = useMemo(
    () => filterAndSortCycles(cycles, query, filter, downloadedBookIds, progressByBook),
    [cycles, query, filter, downloadedBookIds, progressByBook],
  );

  const completedDownloads = useMemo(
    () => completedDownloadItems(downloadQueueState, allTracks, books),
    [allTracks, books, downloadQueueState],
  );

  const visibleMusicTracks = useMemo(
    () => visibleMusicTrackList(musicTracks, sessionState === "AuthenticatedOnline"),
    [musicTracks, sessionState],
  );

  const openBook = useCallback(
    async (book: Book, fromCycle: Cycle | null = null) => {
      setSelectedBook(book);
      const bookTracks = await api.db.getTracks(book.id);
      setTracks(bookTracks);
      if (fromCycle) setSelectedCycle(fromCycle);
    },
    [api],
  );

  const resetFilter = useCallback(() => setFilter(defaultLibraryFilter), []);

  return {
    cycles,
    books,
    allTracks,
    tracks,
    setTracks,
    selectedCycle,
    setSelectedCycle,
    selectedBook,
    setSelectedBook,
    query,
    setQuery,
    filter,
    setFilter,
    resetFilter,
    showFilterSheet,
    setShowFilterSheet,
    isLoading,
    setIsLoading,
    storageUsed,
    pendingCount,
    lastSyncAtEpochMs,
    progressList,
    setProgressList,
    musicTracks,
    setMusicTracks,
    visibleMusicTracks,
    musicStartedInSessionRef,
    refreshLibrary: refreshLibraryStable,
    tracksByBookId,
    downloadedBookIds,
    progressByBook,
    cycleCardStateById,
    filteredCycles,
    completedDownloads,
    openBook,
  };
}

export type LibraryController = ReturnType<typeof useLibraryController>;
