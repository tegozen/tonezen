import type { Track } from "@core/types";
import {
  useCallback,
  useState,
  type Dispatch,
  type RefObject,
  type SetStateAction,
} from "react";
import { setMediaPlaybackState } from "./mediaSessionController";

interface UsePlaybackSeekControlsOptions {
  audioRef: RefObject<HTMLAudioElement | null>;
  currentTrackRef: RefObject<Track | null>;
  setIsPlaying: Dispatch<SetStateAction<boolean>>;
  setPositionMs: Dispatch<SetStateAction<number>>;
  durationSecondsForSeek: (audio: HTMLAudioElement, track: Track | null) => number;
  saveAudiobookProgress: (positionMs: number) => void;
  syncPositionState: () => void;
  syncPlayingState: (audio: HTMLAudioElement) => void;
}

export function usePlaybackSeekControls({
  audioRef,
  currentTrackRef,
  setIsPlaying,
  setPositionMs,
  durationSecondsForSeek,
  saveAudiobookProgress,
  syncPositionState,
  syncPlayingState,
}: UsePlaybackSeekControlsOptions) {
  const [playbackSpeed, setPlaybackSpeed] = useState(1);

  const seekTo = useCallback(
    (fraction: number) => {
      const audio = audioRef.current;
      const track = currentTrackRef.current;
      if (!audio) return;
      const durationSec = durationSecondsForSeek(audio, track);
      if (durationSec <= 0) return;
      audio.currentTime = Math.max(0, Math.min(durationSec, durationSec * fraction));
      const positionMs = Math.floor(audio.currentTime * 1000);
      setPositionMs(positionMs);
      syncPositionState();
      saveAudiobookProgress(positionMs);
    },
    [
      audioRef,
      currentTrackRef,
      durationSecondsForSeek,
      saveAudiobookProgress,
      setPositionMs,
      syncPositionState,
    ],
  );

  const pauseOrResume = useCallback(() => {
    const audio = audioRef.current;
    if (!audio) return;
    if (audio.paused) {
      void audio.play().then(
        () => syncPlayingState(audio),
        () => setIsPlaying(false),
      );
    } else {
      audio.pause();
      setIsPlaying(false);
      setMediaPlaybackState("paused");
      saveAudiobookProgress(Math.floor(audio.currentTime * 1000));
    }
  }, [audioRef, saveAudiobookProgress, setIsPlaying, syncPlayingState]);

  const seekBy = useCallback(
    (deltaMs: number) => {
      const audio = audioRef.current;
      const track = currentTrackRef.current;
      if (!audio) return;
      const durationSec = durationSecondsForSeek(audio, track);
      const next = Math.max(0, audio.currentTime + deltaMs / 1000);
      audio.currentTime = durationSec > 0 ? Math.min(durationSec, next) : next;
      const positionMs = Math.floor(audio.currentTime * 1000);
      setPositionMs(positionMs);
      syncPositionState();
      saveAudiobookProgress(positionMs);
    },
    [
      audioRef,
      currentTrackRef,
      durationSecondsForSeek,
      saveAudiobookProgress,
      setPositionMs,
      syncPositionState,
    ],
  );

  const cycleSpeed = useCallback(() => {
    const speeds = [0.75, 1, 1.25, 1.5, 2];
    setPlaybackSpeed((current) => {
      const index = speeds.indexOf(current);
      const next = speeds[(index + 1) % speeds.length];
      if (audioRef.current) audioRef.current.playbackRate = next;
      return next;
    });
  }, [audioRef]);

  return {
    playbackSpeed,
    pauseOrResume,
    seekBy,
    seekTo,
    cycleSpeed,
  };
}
