import type { AudiobookProgress, Book, Track } from "@core/types";
import { useCallback, useEffect, useRef, useState, type RefObject } from "react";
import { formatMs } from "@/shared/lib/formatTime";
import { updateMediaPositionState } from "./mediaSessionController";

interface UseAudiobookProgressSaveOptions {
  selectedBook: Book | null;
  tracks: Track[];
  audioRef: RefObject<HTMLAudioElement | null>;
  currentTrackRef: RefObject<Track | null>;
  playbackBookRef: RefObject<Book | null>;
  durationSecondsForSeek: (audio: HTMLAudioElement, track: Track | null) => number;
  playTrackRef: RefObject<(track: Track, startMs?: number, book?: Book | null) => void>;
}

export function useAudiobookProgressSave({
  selectedBook,
  tracks,
  audioRef,
  currentTrackRef,
  playbackBookRef,
  durationSecondsForSeek,
  playTrackRef,
}: UseAudiobookProgressSaveOptions) {
  const [progressLabel, setProgressLabel] = useState<string | null>(null);
  const lastProgressSaveRef = useRef(0);
  const lastPositionSyncRef = useRef(0);

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
  }, [audioRef, currentTrackRef, durationSecondsForSeek]);

  const saveAudiobookProgress = useCallback(
    (positionMs: number) => {
      const book = playbackBookRef.current;
      const track = currentTrackRef.current;
      if (!book || book.contentType !== "audiobook" || !track || track.bookId !== book.id) return;
      lastProgressSaveRef.current = Date.now();
      void window.tonezen.progress.save(book.id, track.id, positionMs);
    },
    [currentTrackRef, playbackBookRef],
  );

  const resumeProgress = useCallback(async () => {
    if (!selectedBook) return;
    const saved = await window.tonezen.progress.get(selectedBook.id);
    if (!saved) return;
    const track = tracks.find((t) => t.id === saved.trackId);
    if (track?.localPath) playTrackRef.current(track, saved.positionMs);
  }, [playTrackRef, selectedBook, tracks]);

  useEffect(() => {
    const flushProgress = () => {
      const audio = audioRef.current;
      if (!audio || audio.paused) return;
      saveAudiobookProgress(Math.floor(audio.currentTime * 1000));
    };
    window.addEventListener("pagehide", flushProgress);
    return () => window.removeEventListener("pagehide", flushProgress);
  }, [audioRef, saveAudiobookProgress]);

  return {
    progressLabel,
    lastProgressSaveRef,
    lastPositionSyncRef,
    saveAudiobookProgress,
    syncPositionState,
    resumeProgress,
  };
}
