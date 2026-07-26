import { CyclePlaybackResolver } from "@core/playback/cyclePlayback";
import { needsMetadataBeforeSeek, startSecondsFromMs } from "@core/playback/playbackStart";
import type { Book, Track } from "@core/types";
import {
  useCallback,
  useMemo,
  useRef,
  useState,
  type Dispatch,
  type MutableRefObject,
  type RefObject,
  type SetStateAction,
} from "react";
import { toAudioFileUrl } from "@core/platform/localAudioUrl";
import { clearMediaSession } from "./mediaSessionController";
import { syncMediaSessionForTrack } from "./playbackMediaSession";
import type { PlaybackSkipHandlers } from "./playbackTypes";
import { usePlaybackSeekControls } from "./usePlaybackSeekControls";

const cycleResolver = new CyclePlaybackResolver();

interface UsePlaybackControlsOptions {
  selectedBookRef: RefObject<Book | null>;
  tracksRef: RefObject<Track[]>;
  skipTracksRef: RefObject<Track[]>;
  skipHandlersRef: RefObject<PlaybackSkipHandlers | undefined>;
  audioRef: RefObject<HTMLAudioElement | null>;
  volumeRef: MutableRefObject<number>;
  playbackBookRef: MutableRefObject<Book | null>;
  currentTrackRef: MutableRefObject<Track | null>;
  playTrackRef: MutableRefObject<(track: Track, startMs?: number, book?: Book | null) => void>;
  setIsPlaying: Dispatch<SetStateAction<boolean>>;
  setDurationMs: Dispatch<SetStateAction<number>>;
  syncPlayingState: (audio: HTMLAudioElement) => void;
  syncDurationState: (audio: HTMLAudioElement, track: Track | null) => void;
  durationSecondsForSeek: (audio: HTMLAudioElement, track: Track | null) => number;
  saveAudiobookProgress: (positionMs: number) => void;
  syncPositionState: () => void;
  lastProgressSaveRef: MutableRefObject<number>;
  lastPositionSyncRef: MutableRefObject<number>;
}

export function usePlaybackControls({
  selectedBookRef,
  tracksRef,
  skipTracksRef,
  skipHandlersRef,
  audioRef,
  volumeRef,
  playbackBookRef,
  currentTrackRef,
  playTrackRef,
  setIsPlaying,
  setDurationMs,
  syncPlayingState,
  syncDurationState,
  durationSecondsForSeek,
  saveAudiobookProgress,
  syncPositionState,
  lastProgressSaveRef,
  lastPositionSyncRef,
}: UsePlaybackControlsOptions) {
  const [currentTrack, setCurrentTrack] = useState<Track | null>(null);
  const [positionMs, setPositionMs] = useState(0);
  const playGenerationRef = useRef(0);

  currentTrackRef.current = currentTrack;

  const mediaSessionDeps = useMemo(
    () => ({
      audioRef,
      currentTrackRef,
      skipTracksRef,
      skipHandlersRef,
      playTrackRef,
      syncPlayingState,
      syncPositionState,
      durationSecondsForSeek,
    }),
    [
      audioRef,
      currentTrackRef,
      durationSecondsForSeek,
      playTrackRef,
      skipHandlersRef,
      skipTracksRef,
      syncPlayingState,
      syncPositionState,
    ],
  );

  const playTrack = useCallback(
    (track: Track, startMs = 0, bookOverride?: Book | null) => {
      if (!track.localPath) return;
      const book = bookOverride ?? selectedBookRef.current;
      if (!book) return;
      const generation = ++playGenerationRef.current;
      playbackBookRef.current = book;
      setCurrentTrack(track);
      setPositionMs(startMs);
      setDurationMs(track.durationMs ?? 0);
      window.tonezen.playback.setActive(true);
      syncMediaSessionForTrack(track, book, mediaSessionDeps);
      const audio = audioRef.current;
      if (!audio) return;
      audio.volume = volumeRef.current;
      audio.src = toAudioFileUrl(track.localPath);

      const startPlayback = () => {
        if (generation !== playGenerationRef.current) return;
        if (startMs > 0) {
          audio.currentTime = startSecondsFromMs(startMs);
        }
        void audio.play().then(
          () => {
            if (generation !== playGenerationRef.current) return;
            syncPlayingState(audio);
            syncDurationState(audio, track);
          },
          (error: unknown) => {
            if (generation !== playGenerationRef.current) return;
            console.error("Playback failed", error, track.localPath);
            setIsPlaying(false);
          },
        );
      };

      if (needsMetadataBeforeSeek(startMs, audio.readyState)) {
        audio.addEventListener("loadedmetadata", startPlayback, { once: true });
      } else {
        startPlayback();
      }
    },
    [
      audioRef,
      playbackBookRef,
      selectedBookRef,
      setDurationMs,
      setIsPlaying,
      syncDurationState,
      mediaSessionDeps,
      syncPlayingState,
      volumeRef,
    ],
  );

  const releaseAudioSource = useCallback(() => {
    const audio = audioRef.current;
    if (!audio) return;
    audio.pause();
    audio.removeAttribute("src");
    audio.load();
  }, [audioRef]);

  const stopPlayback = useCallback(() => {
    releaseAudioSource();
    clearMediaSession();
    window.tonezen.playback.setActive(false);
    playbackBookRef.current = null;
    setIsPlaying(false);
    setCurrentTrack(null);
    setPositionMs(0);
    setDurationMs(0);
  }, [playbackBookRef, releaseAudioSource, setDurationMs, setIsPlaying]);

  const onTimeUpdate = useCallback(() => {
    const book = playbackBookRef.current;
    const track = currentTrackRef.current;
    const audio = audioRef.current;
    if (!audio) return;

    setPositionMs(Math.floor(audio.currentTime * 1000));
    syncDurationState(audio, track);
    syncPlayingState(audio);

    if (!book || book.contentType !== "audiobook" || !track) return;

    const now = Date.now();
    if (now - lastPositionSyncRef.current >= 1000) {
      lastPositionSyncRef.current = now;
      syncPositionState();
    }

    if (now - lastProgressSaveRef.current < 15000) return;
    saveAudiobookProgress(Math.floor(audio.currentTime * 1000));
  }, [
    audioRef,
    currentTrackRef,
    lastPositionSyncRef,
    lastProgressSaveRef,
    playbackBookRef,
    saveAudiobookProgress,
    syncDurationState,
    syncPlayingState,
    syncPositionState,
  ]);

  const onTrackEnded = useCallback(() => {
    if (!playbackBookRef.current || !currentTrackRef.current) return;
    const next = cycleResolver.nextInBook(currentTrackRef.current, tracksRef.current ?? []);
    if (next?.localPath) playTrackRef.current(next);
  }, [currentTrackRef, playbackBookRef, playTrackRef, tracksRef]);

  const seekControls = usePlaybackSeekControls({
    audioRef,
    currentTrackRef,
    setIsPlaying,
    setPositionMs,
    durationSecondsForSeek,
    saveAudiobookProgress,
    syncPositionState,
    syncPlayingState,
  });

  return {
    currentTrack,
    positionMs,
    playbackSpeed: seekControls.playbackSpeed,
    playTrack,
    stopPlayback,
    onTimeUpdate,
    onTrackEnded,
    pauseOrResume: seekControls.pauseOrResume,
    seekBy: seekControls.seekBy,
    seekTo: seekControls.seekTo,
    cycleSpeed: seekControls.cycleSpeed,
  };
}
