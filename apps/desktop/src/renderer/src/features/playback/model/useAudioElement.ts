import { effectiveDurationMs } from "@core/playback/playbackDuration";
import type { Track } from "@core/types";
import {
  useCallback,
  useEffect,
  useRef,
  useState,
  type Dispatch,
  type MutableRefObject,
  type RefObject,
  type SetStateAction,
} from "react";
import { loadPlaybackVolume, savePlaybackVolume } from "../lib/playbackVolume";
import { setMediaPlaybackState } from "./mediaSessionController";

interface UseAudioElementOptions {
  currentTrackRef: RefObject<Track | null>;
  setIsPlaying: Dispatch<SetStateAction<boolean>>;
  setDurationMs: Dispatch<SetStateAction<number>>;
}

export function useAudioElement({
  currentTrackRef,
  setIsPlaying,
  setDurationMs,
}: UseAudioElementOptions) {
  const [volume, setVolumeState] = useState(loadPlaybackVolume);
  const audioEventsCleanupRef = useRef<(() => void) | null>(null);
  const audioRef = useRef<HTMLAudioElement | null>(null);
  const volumeRef = useRef(volume) as MutableRefObject<number>;

  volumeRef.current = volume;

  const applyVolume = useCallback((value: number) => {
    if (audioRef.current) audioRef.current.volume = value;
  }, []);

  const setVolume = useCallback(
    (value: number) => {
      const clamped = Math.min(1, Math.max(0, value));
      setVolumeState(clamped);
      savePlaybackVolume(clamped);
      applyVolume(clamped);
    },
    [applyVolume],
  );

  useEffect(() => {
    applyVolume(volume);
  }, [volume, applyVolume]);

  const syncPlayingState = useCallback((audio: HTMLAudioElement) => {
    const playing = !audio.paused && !audio.ended;
    setIsPlaying(playing);
    setMediaPlaybackState(playing ? "playing" : "paused");
  }, [setIsPlaying]);

  const syncDurationState = useCallback(
    (audio: HTMLAudioElement, track: Track | null) => {
      const duration = effectiveDurationMs(audio.duration, track?.durationMs);
      if (duration > 0) setDurationMs(duration);
    },
    [setDurationMs],
  );

  const durationSecondsForSeek = useCallback((audio: HTMLAudioElement, track: Track | null): number => {
    return effectiveDurationMs(audio.duration, track?.durationMs) / 1000;
  }, []);

  const setAudioElement = useCallback(
    (node: HTMLAudioElement | null) => {
      audioEventsCleanupRef.current?.();
      audioEventsCleanupRef.current = null;
      audioRef.current = node;
      if (!node) return;
      node.volume = volumeRef.current;

      const onPlay = () => syncPlayingState(node);
      const onPause = () => syncPlayingState(node);
      const onLoaded = () => syncDurationState(node, currentTrackRef.current);
      const onDurationChange = () => syncDurationState(node, currentTrackRef.current);
      const onError = () => {
        const mediaError = node.error;
        console.error("Audio element error", mediaError?.code, mediaError?.message, node.src);
        setIsPlaying(false);
      };

      node.addEventListener("play", onPlay);
      node.addEventListener("pause", onPause);
      node.addEventListener("loadedmetadata", onLoaded);
      node.addEventListener("durationchange", onDurationChange);
      node.addEventListener("error", onError);
      syncPlayingState(node);
      syncDurationState(node, currentTrackRef.current);

      audioEventsCleanupRef.current = () => {
        node.removeEventListener("play", onPlay);
        node.removeEventListener("pause", onPause);
        node.removeEventListener("loadedmetadata", onLoaded);
        node.removeEventListener("durationchange", onDurationChange);
        node.removeEventListener("error", onError);
      };
    },
    [currentTrackRef, setIsPlaying, syncDurationState, syncPlayingState],
  );

  useEffect(() => () => audioEventsCleanupRef.current?.(), []);

  return {
    audioRef,
    volume,
    volumeRef,
    setVolume,
    setAudioElement,
    syncPlayingState,
    syncDurationState,
    durationSecondsForSeek,
  };
}
