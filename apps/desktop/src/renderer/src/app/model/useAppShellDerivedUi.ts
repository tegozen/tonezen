import { useCallback, useEffect } from "react";
import { progressForTrack } from "@core/downloads/downloadQueueState";
import { findActiveMusicTrack } from "@core/playback/musicPlayback";
import type { BottomTab } from "@core/platform/navigation";
import { isBookFullyListened } from "@/entities/catalog";
import type { useDownloadQueue } from "@/features/downloads";
import type { useLibraryController } from "@/features/library";
import type { useMusicPlayback } from "@/features/music-queue";
import type { Track } from "@core/types";

type Library = ReturnType<typeof useLibraryController>;
type Music = ReturnType<typeof useMusicPlayback>;
type DownloadQueue = ReturnType<typeof useDownloadQueue>;

interface UseAppShellDerivedUiOptions {
  activeTab: BottomTab;
  setActiveTab: (tab: BottomTab) => void;
  library: Library;
  music: Music;
  downloadQueue: DownloadQueue;
  currentTrack: Track | null;
  positionMs: number;
  durationMs: number;
  refreshSession: () => Promise<void>;
}

export function useAppShellDerivedUi({
  activeTab,
  setActiveTab,
  library,
  music,
  downloadQueue,
  currentTrack,
  positionMs,
  durationMs,
  refreshSession,
}: UseAppShellDerivedUiOptions) {
  const savedBookProgress = library.selectedBook
    ? library.progressByBook.get(library.selectedBook.id)
    : undefined;

  const miniTitle = currentTrack?.title ?? null;
  const activeMusicTrack = findActiveMusicTrack(
    music.musicQueue,
    music.musicQueueRef.current,
    currentTrack?.id,
  );
  const miniSubtitle = music.musicMode
    ? activeMusicTrack
      ? [activeMusicTrack.artist, activeMusicTrack.albumTitle].filter(Boolean).join(" · ") ||
        "Сейчас играет"
      : "Сейчас играет"
    : library.selectedBook?.author ?? "Сейчас играет";
  const miniDownloadProgress = currentTrack
    ? progressForTrack(downloadQueue.state, currentTrack.id)
    : null;
  const currentTrackInSelectedBook =
    library.selectedBook != null &&
    currentTrack != null &&
    library.tracks.some((track) => track.id === currentTrack.id);
  const showMiniPlayer =
    Boolean(currentTrack) &&
    (music.musicMode || currentTrackInSelectedBook || (!library.selectedBook && !library.selectedCycle));

  const handleMusicTabSelected = music.onMusicTabSelected;
  const handleTabSelect = useCallback(
    (tab: BottomTab) => {
      setActiveTab(tab);
      if (tab !== "books") library.setShowFilterSheet(false);
      if (tab === "music") handleMusicTabSelected();
      if (tab === "profile") void refreshSession();
    },
    [handleMusicTabSelected, library, refreshSession, setActiveTab],
  );

  useEffect(() => {
    if (activeTab === "music") {
      handleMusicTabSelected();
    }
  }, [activeTab, handleMusicTabSelected]);

  const showBottomNav = !library.selectedBook && !library.selectedCycle;
  const coverSeed = currentTrack?.id ?? library.selectedBook?.id ?? "";
  const bookIsListened = isBookFullyListened(library.tracks, savedBookProgress);
  const progress = durationMs > 0 ? positionMs / durationMs : 0;

  return {
    savedBookProgress,
    miniTitle,
    activeMusicTrack,
    miniSubtitle,
    miniDownloadProgress,
    showMiniPlayer,
    handleTabSelect,
    showBottomNav,
    coverSeed,
    bookIsListened,
    progress,
  };
}
