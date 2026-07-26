import { useMemo, useRef } from "react";
import type { SessionState } from "@core/types";
import { useLibraryDownloads } from "@/features/downloads";
import { useMusicPlayback } from "@/features/music-queue";
import { useAudiobookSession, usePlayback } from "@/features/playback";
import { useLibraryController } from "@/features/library";
import type { DownloadQueueApi } from "@/shared/api";

interface UseAppShellFeatureStackOptions {
  sessionState: SessionState;
  downloadQueue: DownloadQueueApi;
  closeExpandedPlayer: () => void;
  showToast: (message: string) => void;
}

export function useAppShellFeatureStack({
  sessionState,
  downloadQueue,
  closeExpandedPlayer,
  showToast,
}: UseAppShellFeatureStackOptions) {
  const library = useLibraryController({ sessionState, downloadQueueState: downloadQueue.state });

  const musicHandlersRef = useRef<{
    handleSkipNext: () => boolean;
    handleSkipPrevious: () => boolean;
    handleTrackEnded: () => boolean;
  }>({
    handleSkipNext: () => false,
    handleSkipPrevious: () => false,
    handleTrackEnded: () => false,
  });
  const skipHandlers = useMemo(
    () => ({
      onSkipNext: () => musicHandlersRef.current.handleSkipNext(),
      onSkipPrevious: () => musicHandlersRef.current.handleSkipPrevious(),
    }),
    [],
  );

  const {
    currentTrack,
    isPlaying,
    positionMs,
    durationMs,
    audioRef,
    playTrack,
    stopPlayback,
    onTimeUpdate,
    pauseOrResume,
    seekBy,
    seekTo,
    volume,
    setVolume,
  } = usePlayback(library.selectedBook, library.tracks, library.tracks, skipHandlers);

  const music = useMusicPlayback({
    books: library.books,
    allTracks: library.allTracks,
    musicTracks: library.musicTracks,
    setMusicTracks: library.setMusicTracks,
    setTracks: library.setTracks,
    sessionState,
    refreshLibrary: library.refreshLibrary,
    downloadQueue,
    playTrack,
    stopPlayback,
    pauseOrResume,
    seekTo,
    currentTrack,
    positionMs,
  });

  musicHandlersRef.current = {
    handleSkipNext: music.handleSkipNext,
    handleSkipPrevious: music.handleSkipPrevious,
    handleTrackEnded: music.handleTrackEnded,
  };
  library.musicStartedInSessionRef.current = music.musicStartedInSessionRef.current;

  const downloads = useLibraryDownloads({
    sessionState,
    downloadQueue,
    currentTrack,
    selectedBook: library.selectedBook,
    setTracks: library.setTracks,
    stopPlayback,
    closeExpandedPlayer,
    refreshLibrary: library.refreshLibrary,
    showToast,
  });

  const audiobook = useAudiobookSession({
    sessionState,
    books: library.books,
    cycles: library.cycles,
    selectedBook: library.selectedBook,
    setSelectedBook: library.setSelectedBook,
    selectedCycle: library.selectedCycle,
    setSelectedCycle: library.setSelectedCycle,
    tracks: library.tracks,
    setTracks: library.setTracks,
    tracksByBookId: library.tracksByBookId,
    progressByBook: library.progressByBook,
    setProgressList: library.setProgressList,
    refreshLibrary: library.refreshLibrary,
    downloadQueue,
    playTrack,
    stopPlayback,
    pauseOrResume,
    currentTrack,
    durationMs,
    isPlaying,
    showToast,
    closeExpandedPlayer,
    music: {
      setMusicMode: music.setMusicMode,
      handleSkipNext: music.handleSkipNext,
      handleSkipPrevious: music.handleSkipPrevious,
      handleTrackEnded: music.handleTrackEnded,
    },
  });

  return {
    library,
    music,
    downloads,
    audiobook,
    currentTrack,
    isPlaying,
    positionMs,
    durationMs,
    audioRef,
    onTimeUpdate,
    seekBy,
    seekTo,
    volume,
    setVolume,
    stopPlayback,
  };
}
