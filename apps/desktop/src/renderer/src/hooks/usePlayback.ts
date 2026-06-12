import { CyclePlaybackResolver } from "@shared/cyclePlayback";
import type { Book, Track } from "@shared/types";
import { useCallback, useEffect, useRef, useState } from "react";
import {
  clearMediaSession,
  setMediaPlaybackState,
  setupMediaSession,
  updateMediaPositionState,
} from "../mediaSessionController";

const cycleResolver = new CyclePlaybackResolver();

export function usePlayback(selectedBook: Book | null, tracks: Track[]) {
  const [currentTrack, setCurrentTrack] = useState<Track | null>(null);
  const [progressLabel, setProgressLabel] = useState<string | null>(null);
  const lastProgressSaveRef = useRef(0);
  const lastPositionSyncRef = useRef(0);
  const audioRef = useRef<HTMLAudioElement>(null);
  const selectedBookRef = useRef(selectedBook);
  const tracksRef = useRef(tracks);
  const currentTrackRef = useRef(currentTrack);
  const playTrackRef = useRef<(track: Track, startMs?: number) => void>(() => {});

  selectedBookRef.current = selectedBook;
  tracksRef.current = tracks;
  currentTrackRef.current = currentTrack;

  useEffect(() => {
    return window.tonezen.progress.onUpdated((progress) => {
      if (selectedBook?.id === progress.bookId) {
        const track = tracks.find((t) => t.id === progress.trackId);
        setProgressLabel(track ? `Continue: ${track.title}` : null);
      }
    });
  }, [selectedBook, tracks]);

  const syncPositionState = useCallback(() => {
    const audio = audioRef.current;
    if (!audio || !Number.isFinite(audio.duration) || audio.duration <= 0) return;
    updateMediaPositionState({
      duration: audio.duration,
      position: audio.currentTime,
      playbackRate: audio.playbackRate || 1,
    });
  }, []);

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
            void audioRef.current?.play();
          },
          pause: () => {
            audioRef.current?.pause();
          },
          nextTrack: () => {
            const current = currentTrackRef.current;
            const list = tracksRef.current;
            if (!current) return;
            const next = cycleResolver.nextInBook(current, list);
            if (next?.localPath) playTrackRef.current(next);
          },
          previousTrack: () => {
            const current = currentTrackRef.current;
            const list = tracksRef.current;
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
            const duration = audioRef.current.duration;
            const next = audioRef.current.currentTime + offsetSeconds;
            audioRef.current.currentTime = Number.isFinite(duration) ? Math.min(duration, next) : next;
          },
          getTiming: () => {
            const audio = audioRef.current;
            if (!audio || !Number.isFinite(audio.duration) || audio.duration <= 0) return null;
            return {
              duration: audio.duration,
              position: audio.currentTime,
              playbackRate: audio.playbackRate || 1,
            };
          },
        },
      );
    },
    [syncPositionState],
  );

  const playTrack = useCallback(
    (track: Track, startMs = 0) => {
      if (!track.localPath || !selectedBookRef.current) return;
      const book = selectedBookRef.current;
      setCurrentTrack(track);
      window.tonezen.playback.setActive(true);
      syncMediaSessionForTrack(track, book);
      if (audioRef.current) {
        audioRef.current.src = `file://${track.localPath}`;
        if (startMs > 0) audioRef.current.currentTime = startMs / 1000;
        void audioRef.current.play();
      }
    },
    [syncMediaSessionForTrack],
  );

  playTrackRef.current = playTrack;

  useEffect(() => {
    const audio = audioRef.current;
    if (!audio) return;

    const onPlay = () => setMediaPlaybackState("playing");
    const onPause = () => setMediaPlaybackState("paused");
    const onEmptied = () => setMediaPlaybackState("none");

    audio.addEventListener("play", onPlay);
    audio.addEventListener("pause", onPause);
    audio.addEventListener("emptied", onEmptied);
    return () => {
      audio.removeEventListener("play", onPlay);
      audio.removeEventListener("pause", onPause);
      audio.removeEventListener("emptied", onEmptied);
    };
  }, [selectedBook]);

  const stopPlayback = useCallback(() => {
    audioRef.current?.pause();
    clearMediaSession();
    window.tonezen.playback.setActive(false);
  }, []);

  const onTimeUpdate = useCallback(() => {
    const book = selectedBookRef.current;
    const track = currentTrackRef.current;
    const audio = audioRef.current;
    if (!book || book.contentType !== "audiobook" || !track || !audio) return;

    const now = Date.now();
    if (now - lastPositionSyncRef.current >= 1000) {
      lastPositionSyncRef.current = now;
      syncPositionState();
    }

    if (now - lastProgressSaveRef.current < 15000) return;
    lastProgressSaveRef.current = now;
    void window.tonezen.progress.save(book.id, track.id, Math.floor(audio.currentTime * 1000));
  }, [syncPositionState]);

  const resumeProgress = useCallback(async () => {
    if (!selectedBook) return;
    const saved = await window.tonezen.progress.get(selectedBook.id);
    if (!saved) return;
    const track = tracks.find((t) => t.id === saved.trackId);
    if (track?.localPath) playTrack(track, saved.positionMs);
  }, [selectedBook, tracks, playTrack]);

  const onTrackEnded = useCallback(() => {
    if (!selectedBook || !currentTrack) return;
    const next = cycleResolver.nextInBook(currentTrack, tracks);
    if (next?.localPath) playTrack(next);
  }, [selectedBook, currentTrack, tracks, playTrack]);

  const setInitialTrackState = useCallback(
    (book: Book, bookTracks: Track[], saved: { trackId: string } | null) => {
      if (saved && book.contentType === "audiobook") {
        const resumeTrack = bookTracks.find((t) => t.id === saved.trackId) ?? bookTracks[0];
        setCurrentTrack(resumeTrack ?? null);
        setProgressLabel(resumeTrack ? `Continue: ${resumeTrack.title}` : null);
      } else {
        setCurrentTrack(bookTracks[0] ?? null);
        setProgressLabel(null);
      }
    },
    [],
  );

  return {
    currentTrack,
    progressLabel,
    audioRef,
    playTrack,
    stopPlayback,
    onTimeUpdate,
    onTrackEnded,
    resumeProgress,
    setInitialTrackState,
  };
}
