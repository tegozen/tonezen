import { useCallback } from "react";
import type { Book, Track } from "@core/types";
import { CyclePlaybackResolver } from "@core/playback/cyclePlayback";
import { completedAudiobookProgress, upsertAudiobookProgress } from "@core/progress/audiobookProgress";
import { getTonezenApi, useSaveProgressMutation } from "@/shared/api";
import type { UseAudiobookSessionOptions } from "./audiobookSessionTypes";

const cycleResolver = new CyclePlaybackResolver();

type UseAudiobookSkipHandlersOptions = Pick<
  UseAudiobookSessionOptions,
  | "cycles"
  | "selectedBook"
  | "selectedCycle"
  | "tracks"
  | "tracksByBookId"
  | "currentTrack"
  | "durationMs"
  | "setProgressList"
  | "setSelectedBook"
  | "setSelectedCycle"
  | "setTracks"
  | "music"
> & {
  advanceAudiobookTrack: (book: Book, bookTracks: Track[], nextTrack: Track) => Promise<void>;
  playBookTrack: (track: Track) => Promise<void>;
};

export function useAudiobookSkipHandlers({
  cycles,
  selectedBook,
  selectedCycle,
  tracks,
  tracksByBookId,
  currentTrack,
  durationMs,
  setProgressList,
  setSelectedBook,
  setSelectedCycle,
  setTracks,
  music,
  advanceAudiobookTrack,
  playBookTrack,
}: UseAudiobookSkipHandlersOptions) {
  const api = getTonezenApi();
  const saveProgress = useSaveProgressMutation();

  const handleSkipNext = useCallback(() => {
    if (music.handleSkipNext()) return;
    if (!currentTrack || !selectedBook) return;
    const next = cycleResolver.nextInBook(currentTrack, tracks);
    if (next) void playBookTrack(next);
  }, [currentTrack, music, playBookTrack, selectedBook, tracks]);

  const handleSkipPrevious = useCallback(() => {
    if (music.handleSkipPrevious()) return;
    if (!currentTrack || !selectedBook) return;
    const prev = cycleResolver.previousInBook(currentTrack, tracks);
    if (prev) void playBookTrack(prev);
  }, [currentTrack, music, playBookTrack, selectedBook, tracks]);

  const handleTrackEnded = useCallback(() => {
    const completedProgress = completedAudiobookProgress(selectedBook, currentTrack, durationMs);
    if (completedProgress) {
      setProgressList((current) => upsertAudiobookProgress(current, completedProgress));
      void saveProgress.mutate({
        bookId: completedProgress.bookId,
        trackId: completedProgress.trackId,
        positionMs: completedProgress.positionMs,
      });
    }

    if (music.handleTrackEnded()) return;
    if (!currentTrack || !selectedBook) return;

    const nextInBook = cycleResolver.nextInBook(currentTrack, tracks);
    if (nextInBook) {
      void advanceAudiobookTrack(selectedBook, tracks, nextInBook);
      return;
    }

    const cycle =
      selectedCycle ?? cycles.find((item) => item.books.some((book) => book.id === selectedBook.id));
    if (!cycle) return;

    const booksBySlug = new Map(cycle.books.map((book) => [book.slug, book]));
    const result = cycleResolver.nextInCycle(
      selectedBook,
      currentTrack,
      cycle,
      booksBySlug,
      tracksByBookId,
    );
    if (!result.book || !result.track) return;

    void (async () => {
      setSelectedBook(result.book as Book);
      const bookTracks = await api.db.getTracks(result.book!.id);
      setTracks(bookTracks);
      if (!selectedCycle && result.isNextBookInCycle) {
        setSelectedCycle(cycle);
      }
      void advanceAudiobookTrack(result.book as Book, bookTracks, result.track as Track);
    })();
  }, [
    advanceAudiobookTrack,
    api,
    currentTrack,
    cycles,
    durationMs,
    music,
    selectedBook,
    selectedCycle,
    setProgressList,
    setSelectedBook,
    setSelectedCycle,
    setTracks,
    tracks,
    tracksByBookId,
  ]);

  return { handleSkipNext, handleSkipPrevious, handleTrackEnded };
}
