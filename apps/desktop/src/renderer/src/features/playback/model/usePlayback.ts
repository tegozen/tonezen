import type { Book, Track } from "@core/types";
import { useRef, useState } from "react";
import { useAudioElement } from "./useAudioElement";
import { useAudiobookProgressSave } from "./useAudiobookProgressSave";
import type { PlaybackSkipHandlers } from "./playbackTypes";
import { usePlaybackControls } from "./usePlaybackControls";

export type { PlaybackSkipHandlers } from "./playbackTypes";

export function usePlayback(
  selectedBook: Book | null,
  tracks: Track[],
  skipTracks: Track[] = tracks,
  skipHandlers?: PlaybackSkipHandlers,
) {
  const [isPlaying, setIsPlaying] = useState(false);
  const [durationMs, setDurationMs] = useState(0);

  const selectedBookRef = useRef(selectedBook);
  const playbackBookRef = useRef<Book | null>(null);
  const tracksRef = useRef(tracks);
  const skipTracksRef = useRef(skipTracks);
  const currentTrackRef = useRef<Track | null>(null);
  const skipHandlersRef = useRef(skipHandlers);
  const playTrackRef = useRef<(track: Track, startMs?: number, book?: Book | null) => void>(() => {});

  selectedBookRef.current = selectedBook;
  tracksRef.current = tracks;
  skipTracksRef.current = skipTracks;
  skipHandlersRef.current = skipHandlers;

  const audio = useAudioElement({
    currentTrackRef,
    setIsPlaying,
    setDurationMs,
  });

  const progress = useAudiobookProgressSave({
    selectedBook,
    tracks,
    audioRef: audio.audioRef,
    currentTrackRef,
    playbackBookRef,
    durationSecondsForSeek: audio.durationSecondsForSeek,
    playTrackRef,
  });

  const controls = usePlaybackControls({
    selectedBookRef,
    tracksRef,
    skipTracksRef,
    skipHandlersRef,
    audioRef: audio.audioRef,
    volumeRef: audio.volumeRef,
    playbackBookRef,
    currentTrackRef,
    playTrackRef,
    setIsPlaying,
    setDurationMs,
    syncPlayingState: audio.syncPlayingState,
    syncDurationState: audio.syncDurationState,
    durationSecondsForSeek: audio.durationSecondsForSeek,
    saveAudiobookProgress: progress.saveAudiobookProgress,
    syncPositionState: progress.syncPositionState,
    lastProgressSaveRef: progress.lastProgressSaveRef,
    lastPositionSyncRef: progress.lastPositionSyncRef,
  });

  playTrackRef.current = controls.playTrack;

  return {
    currentTrack: controls.currentTrack,
    progressLabel: progress.progressLabel,
    isPlaying,
    positionMs: controls.positionMs,
    durationMs,
    playbackSpeed: controls.playbackSpeed,
    volume: audio.volume,
    setVolume: audio.setVolume,
    audioRef: audio.setAudioElement,
    playTrack: controls.playTrack,
    stopPlayback: controls.stopPlayback,
    onTimeUpdate: controls.onTimeUpdate,
    onTrackEnded: controls.onTrackEnded,
    resumeProgress: progress.resumeProgress,
    pauseOrResume: controls.pauseOrResume,
    seekBy: controls.seekBy,
    seekTo: controls.seekTo,
    cycleSpeed: controls.cycleSpeed,
  };
}
