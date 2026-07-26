import { useCallback, useMemo, useRef, useState, type Dispatch, type SetStateAction } from "react";
import type { Book, SessionState, Track } from "@core/types";
import { visibleMusicTrackList, type MusicListTrack } from "@core/catalog/musicList";
import type { DownloadQueueApi } from "@/shared/api";
import { useMusicDownloadActions } from "./useMusicDownloadActions";
import { useMusicEnsureLocal } from "./useMusicEnsureLocal";
import { useMusicPlayControls } from "./useMusicPlayControls";

interface UseMusicPlaybackOptions {
  books: Book[];
  allTracks: Track[];
  musicTracks: MusicListTrack[];
  setMusicTracks: Dispatch<SetStateAction<MusicListTrack[]>>;
  setTracks: Dispatch<SetStateAction<Track[]>>;
  sessionState: SessionState;
  refreshLibrary: () => Promise<void>;
  downloadQueue: DownloadQueueApi;
  playTrack: (track: Track, startMs?: number, book?: Book | null) => void;
  stopPlayback: () => void;
  pauseOrResume: () => void;
  seekTo: (fraction: number) => void;
  currentTrack: Track | null;
  positionMs: number;
}

export function useMusicPlayback({
  books,
  allTracks,
  musicTracks,
  setMusicTracks,
  setTracks,
  sessionState,
  refreshLibrary,
  downloadQueue,
  playTrack,
  stopPlayback,
  pauseOrResume,
  seekTo,
  currentTrack,
  positionMs,
}: UseMusicPlaybackOptions) {
  const [musicMode, setMusicMode] = useState(false);
  const [musicPlaybackBook, setMusicPlaybackBook] = useState<Book | null>(null);
  const [musicError, setMusicError] = useState<string | null>(null);
  const musicQueueRef = useRef<MusicListTrack[]>([]);
  const deletingTrackIdRef = useRef<string | null>(null);
  const [musicQueue, setMusicQueue] = useState<MusicListTrack[]>([]);
  const musicStartedInSessionRef = useRef(false);
  const prefetchJobRef = useRef(0);

  const playbackMusicTracks = useMemo(
    () => visibleMusicTrackList(musicTracks, sessionState === "AuthenticatedOnline"),
    [musicTracks, sessionState],
  );

  const { ensureTrackLocal, resolveLocalTrack, prefetchNextTrack, isTrackPlayable } =
    useMusicEnsureLocal({
      sessionState,
      musicTracks,
      allTracks,
      downloadQueue,
      refreshLibrary,
      setMusicError,
    });

  const {
    playMusicTrack,
    playMusicWave,
    onMusicTabSelected,
    handleSkipNext,
    handleSkipPrevious,
    handleTrackEnded,
    onMiniPlayerPlayPause,
  } = useMusicPlayControls({
    books,
    allTracks,
    musicTracks,
    setMusicTracks,
    setTracks,
    sessionState,
    downloadQueue,
    playTrack,
    stopPlayback,
    pauseOrResume,
    seekTo,
    currentTrack,
    positionMs,
    musicMode,
    setMusicMode,
    setMusicPlaybackBook,
    setMusicError,
    musicQueueRef,
    deletingTrackIdRef,
    setMusicQueue,
    musicStartedInSessionRef,
    playbackMusicTracks,
    ensureTrackLocal,
    resolveLocalTrack,
    prefetchNextTrack,
    isTrackPlayable,
  });

  const { downloadAllMusic, downloadMusicTrack, deleteMusicTrack } = useMusicDownloadActions({
    sessionState,
    musicTracks,
    downloadQueue,
    refreshLibrary,
    stopPlayback,
    currentTrack,
    setMusicError,
    setMusicMode,
    setMusicPlaybackBook,
    setMusicQueue,
    musicQueueRef,
    deletingTrackIdRef,
    prefetchJobRef,
  });

  const resetMusicSession = useCallback(() => {
    setMusicMode(false);
    setMusicPlaybackBook(null);
    musicStartedInSessionRef.current = false;
    musicQueueRef.current = [];
    setMusicQueue([]);
    prefetchJobRef.current += 1;
    setMusicError(null);
  }, []);

  return {
    musicMode,
    setMusicMode,
    musicPlaybackBook,
    musicError,
    musicQueue,
    musicStartedInSessionRef,
    musicQueueRef,
    playMusicTrack,
    playMusicWave,
    downloadAllMusic,
    downloadMusicTrack,
    deleteMusicTrack,
    onMusicTabSelected,
    handleSkipNext,
    handleSkipPrevious,
    handleTrackEnded,
    onMiniPlayerPlayPause,
    resetMusicSession,
  };
}
