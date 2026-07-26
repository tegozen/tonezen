import { useCallback, useState } from "react";
import type { Book, Track } from "@core/types";
import { resolveAudiobookPlaybackIntent } from "@core/playback/audiobookPlaybackIntent";
import { getTonezenApi, useSaveProgressMutation } from "@/shared/api";
import type { UseAudiobookSessionOptions } from "./audiobookSessionTypes";

type UseAudiobookProgressActionsOptions = Pick<
  UseAudiobookSessionOptions,
  "selectedBook" | "tracks" | "progressByBook" | "refreshLibrary" | "setTracks"
> & {
  playAudiobookTrackResolved: (
    book: Book,
    bookTracks: Track[],
    track: Track,
    startMs: number,
  ) => Promise<void>;
};

export function useAudiobookProgressActions({
  selectedBook,
  tracks,
  progressByBook,
  refreshLibrary,
  setTracks,
  playAudiobookTrackResolved,
}: UseAudiobookProgressActionsOptions) {
  const api = getTonezenApi();
  const saveProgress = useSaveProgressMutation();
  const [earlierChapterPrompt, setEarlierChapterPrompt] = useState<Track | null>(null);

  const playBookTrack = useCallback(
    async (track: Track) => {
      if (!selectedBook) return;
      const sortedTracks = [...tracks].sort((a, b) => a.sortOrder - b.sortOrder);
      const saved = progressByBook.get(selectedBook.id) ?? null;
      const intent = resolveAudiobookPlaybackIntent(sortedTracks, saved, track);
      if (intent.kind === "ConfirmEarlierChapter") {
        setEarlierChapterPrompt(track);
        return;
      }
      const startMs = intent.kind === "Resume" ? intent.positionMs : 0;
      await playAudiobookTrackResolved(selectedBook, tracks, track, startMs);
    },
    [playAudiobookTrackResolved, progressByBook, selectedBook, tracks],
  );

  const markBookListened = useCallback(
    async (book: Book, listened: boolean) => {
      const bookTracks = await api.db.getTracks(book.id);
      if (bookTracks.length === 0) return;
      const track = listened ? bookTracks[bookTracks.length - 1] : bookTracks[0];
      await saveProgress.mutateAsync({
        bookId: book.id,
        trackId: track.id,
        positionMs: listened ? track.durationMs ?? 0 : 0,
      });
      await refreshLibrary();
    },
    [api, refreshLibrary, saveProgress],
  );

  const markTrackListened = useCallback(
    async (book: Book, track: Track, listened: boolean) => {
      await saveProgress.mutateAsync({
        bookId: book.id,
        trackId: track.id,
        positionMs: listened ? track.durationMs ?? 0 : 0,
      });
      await refreshLibrary();
      if (selectedBook?.id === book.id) {
        setTracks(await api.db.getTracks(book.id));
      }
    },
    [api, refreshLibrary, saveProgress, selectedBook, setTracks],
  );

  const continueBook = useCallback(async () => {
    if (!selectedBook) return;
    const sortedTracks = [...tracks].sort((a, b) => a.sortOrder - b.sortOrder);
    const saved = progressByBook.get(selectedBook.id);
    const track = saved
      ? sortedTracks.find((item) => item.id === saved.trackId) ?? sortedTracks[0]
      : sortedTracks[0];
    if (track) await playBookTrack(track);
  }, [playBookTrack, progressByBook, selectedBook, tracks]);

  const dismissEarlierChapterPrompt = useCallback(() => setEarlierChapterPrompt(null), []);

  const confirmEarlierChapterPrompt = useCallback(() => {
    const track = earlierChapterPrompt;
    setEarlierChapterPrompt(null);
    if (track && selectedBook) void playAudiobookTrackResolved(selectedBook, tracks, track, 0);
  }, [earlierChapterPrompt, playAudiobookTrackResolved, selectedBook, tracks]);

  return {
    earlierChapterPrompt,
    dismissEarlierChapterPrompt,
    confirmEarlierChapterPrompt,
    playBookTrack,
    continueBook,
    markBookListened,
    markTrackListened,
  };
}
