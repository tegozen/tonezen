import { CyclePlaybackResolver } from "@shared/cyclePlayback";
import { effectiveDurationMs } from "@shared/playbackDuration";
import { needsMetadataBeforeSeek, startSecondsFromMs } from "@shared/playbackStart";
import type { AudiobookProgress, Book, Track } from "@shared/types";
import { useCallback, useEffect, useRef, useState } from "react";
import { toAudioFileUrl } from "../lib/audioFileUrl";
import { formatMs } from "../lib/formatTime";
import { loadPlaybackVolume, savePlaybackVolume } from "../lib/playbackVolume";
import {
  clearMediaSession,
  setMediaPlaybackState,
  setupMediaSession,
  updateMediaPositionState,
} from "../mediaSessionController";

const cycleResolver = new CyclePlaybackResolver();

export interface PlaybackSkipHandlers {
  onSkipNext?: () => boolean;
  onSkipPrevious?: () => boolean;
}

export function usePlayback(
  selectedBook: Book | null,
  tracks: Track[],
  skipTracks: Track[] = tracks,
  skipHandlers?: PlaybackSkipHandlers,
) {
  const [currentTrack, setCurrentTrack] = useState<Track | null>(null);
  const [progressLabel, setProgressLabel] = useState<string | null>(null);
  const [isPlaying, setIsPlaying] = useState(false);
  const [positionMs, setPositionMs] = useState(0);
  const [durationMs, setDurationMs] = useState(0);
  const [playbackSpeed, setPlaybackSpeed] = useState(1);
  const [volume, setVolumeState] = useState(loadPlaybackVolume);
  const lastProgressSaveRef = useRef(0);
  const lastPositionSyncRef = useRef(0);
  const audioEventsCleanupRef = useRef<(() => void) | null>(null);
  const audioRef = useRef<HTMLAudioElement | null>(null);
  const selectedBookRef = useRef(selectedBook);
  const playbackBookRef = useRef<Book | null>(null);
  const tracksRef = useRef(tracks);
  const skipTracksRef = useRef(skipTracks);
  const currentTrackRef = useRef(currentTrack);
  const skipHandlersRef = useRef(skipHandlers);
  const playTrackRef = useRef<(track: Track, startMs?: number, book?: Book | null) => void>(() => {});
  const playGenerationRef = useRef(0);
  const volumeRef = useRef(volume);

  volumeRef.current = volume;

  selectedBookRef.current = selectedBook;
  tracksRef.current = tracks;
  skipTracksRef.current = skipTracks;
  currentTrackRef.current = currentTrack;
  skipHandlersRef.current = skipHandlers;

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
  }, []);

  const syncDurationState = useCallback((audio: HTMLAudioElement, track: Track | null) => {
    const duration = effectiveDurationMs(audio.duration, track?.durationMs);
    if (duration > 0) setDurationMs(duration);
  }, []);

  const durationSecondsForSeek = useCallback((audio: HTMLAudioElement, track: Track | null): number => {
    return effectiveDurationMs(audio.duration, track?.durationMs) / 1000;
  }, []);

  useEffect(() => {
    return window.tonezen.progress.onUpdated((progress) => {
      const saved = progress as AudiobookProgress;
      if (selectedBook?.id === saved.bookId) {
        const track = tracks.find((t) => t.id === saved.trackId);
        setProgressLabel(
          track ? `Продолжить: ${track.title} · ${formatMs(saved.positionMs)}` : null,
        );
      }
    });
  }, [selectedBook, tracks]);

  const syncPositionState = useCallback(() => {
    const audio = audioRef.current;
    const track = currentTrackRef.current;
    if (!audio) return;
    const durationSec = durationSecondsForSeek(audio, track);
    if (durationSec <= 0) return;
    updateMediaPositionState({
      duration: durationSec,
      position: audio.currentTime,
      playbackRate: audio.playbackRate || 1,
    });
  }, [durationSecondsForSeek]);

  const syncMediaSessionForTrack = useCallback(
    (track: Track, book: Book) => {
      setupMediaSession(
        {
          title: track.title,
          artist: book.author ?? book.title,
          album: book.title,
        },
        {
          play: () => {
            const audio = audioRef.current;
            if (!audio) return;
            void audio.play().then(() => syncPlayingState(audio));
          },
          pause: () => {
            audioRef.current?.pause();
          },
          nextTrack: () => {
            if (skipHandlersRef.current?.onSkipNext?.()) return;
            const current = currentTrackRef.current;
            const list = skipTracksRef.current;
            if (!current) return;
            const next = cycleResolver.nextInBook(current, list);
            if (next?.localPath) playTrackRef.current(next);
          },
          previousTrack: () => {
            if (skipHandlersRef.current?.onSkipPrevious?.()) return;
            const current = currentTrackRef.current;
            const list = skipTracksRef.current;
            if (!current) return;
            const prev = cycleResolver.previousInBook(current, list);
            if (prev?.localPath) playTrackRef.current(prev);
          },
          seekTo: (timeSeconds) => {
            if (!audioRef.current) return;
            audioRef.current.currentTime = timeSeconds;
            syncPositionState();
          },
          seekBackward: (offsetSeconds) => {
            if (!audioRef.current) return;
            audioRef.current.currentTime = Math.max(0, audioRef.current.currentTime - offsetSeconds);
          },
          seekForward: (offsetSeconds) => {
            if (!audioRef.current) return;
            const audio = audioRef.current;
            const duration = durationSecondsForSeek(audio, currentTrackRef.current);
            const next = audio.currentTime + offsetSeconds;
            audio.currentTime = duration > 0 ? Math.min(duration, next) : next;
          },
          getTiming: () => {
            const audio = audioRef.current;
            const track = currentTrackRef.current;
            if (!audio) return null;
            const durationSec = durationSecondsForSeek(audio, track);
            if (durationSec <= 0) return null;
            return {
              duration: durationSec,
              position: audio.currentTime,
              playbackRate: audio.playbackRate || 1,
            };
          },
        },
      );
    },
    [syncPositionState, syncPlayingState, durationSecondsForSeek],
  );

  const saveAudiobookProgress = useCallback((positionMs: number) => {
    const book = playbackBookRef.current;
    const track = currentTrackRef.current;
    if (!book || book.contentType !== "audiobook" || !track || track.bookId !== book.id) return;
    lastProgressSaveRef.current = Date.now();
    void window.tonezen.progress.save(book.id, track.id, positionMs);
  }, []);

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
      syncMediaSessionForTrack(track, book);
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
    [syncDurationState, syncMediaSessionForTrack, syncPlayingState],
  );

  playTrackRef.current = playTrack;

  const releaseAudioSource = useCallback(() => {
    const audio = audioRef.current;
    if (!audio) return;
    audio.pause();
    audio.removeAttribute("src");
    audio.load();
  }, []);

  const stopPlayback = useCallback(() => {
    releaseAudioSource();
    clearMediaSession();
    window.tonezen.playback.setActive(false);
    playbackBookRef.current = null;
    setIsPlaying(false);
    setCurrentTrack(null);
    setPositionMs(0);
    setDurationMs(0);
  }, [releaseAudioSource]);

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
  }, [saveAudiobookProgress, syncDurationState, syncPlayingState, syncPositionState]);

  const resumeProgress = useCallback(async () => {
    if (!selectedBook) return;
    const saved = await window.tonezen.progress.get(selectedBook.id);
    if (!saved) return;
    const track = tracks.find((t) => t.id === saved.trackId);
    if (track?.localPath) playTrack(track, saved.positionMs);
  }, [selectedBook, tracks, playTrack]);

  const seekTo = useCallback(
    (fraction: number) => {
      const audio = audioRef.current;
      const track = currentTrackRef.current;
      if (!audio) return;
      const durationSec = durationSecondsForSeek(audio, track);
      if (durationSec <= 0) return;
      audio.currentTime = Math.max(0, Math.min(durationSec, durationSec * fraction));
      setPositionMs(Math.floor(audio.currentTime * 1000));
      syncPositionState();
    },
    [durationSecondsForSeek, syncPositionState],
  );

  const onTrackEnded = useCallback(() => {
    if (!playbackBookRef.current || !currentTrackRef.current) return;
    const next = cycleResolver.nextInBook(currentTrackRef.current, tracksRef.current);
    if (next?.localPath) playTrackRef.current(next);
  }, []);

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
  }, [saveAudiobookProgress, syncPlayingState]);

  const seekBy = useCallback(
    (deltaMs: number) => {
      const audio = audioRef.current;
      const track = currentTrackRef.current;
      if (!audio) return;
      const durationSec = durationSecondsForSeek(audio, track);
      const next = Math.max(0, audio.currentTime + deltaMs / 1000);
      audio.currentTime = durationSec > 0 ? Math.min(durationSec, next) : next;
      setPositionMs(Math.floor(audio.currentTime * 1000));
      syncPositionState();
    },
    [durationSecondsForSeek, syncPositionState],
  );

  const cycleSpeed = useCallback(() => {
    const speeds = [0.75, 1, 1.25, 1.5, 2];
    setPlaybackSpeed((current) => {
      const index = speeds.indexOf(current);
      const next = speeds[(index + 1) % speeds.length];
      if (audioRef.current) audioRef.current.playbackRate = next;
      return next;
    });
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
    [syncDurationState, syncPlayingState],
  );

  useEffect(() => () => audioEventsCleanupRef.current?.(), []);

  useEffect(() => {
    const flushProgress = () => {
      const audio = audioRef.current;
      if (!audio || audio.paused) return;
      saveAudiobookProgress(Math.floor(audio.currentTime * 1000));
    };
    window.addEventListener("pagehide", flushProgress);
    return () => window.removeEventListener("pagehide", flushProgress);
  }, [saveAudiobookProgress]);

  return {
    currentTrack,
    progressLabel,
    isPlaying,
    positionMs,
    durationMs,
    playbackSpeed,
    volume,
    setVolume,
    audioRef: setAudioElement,
    playTrack,
    stopPlayback,
    onTimeUpdate,
    onTrackEnded,
    resumeProgress,
    pauseOrResume,
    seekBy,
    seekTo,
    cycleSpeed,
  };
}
