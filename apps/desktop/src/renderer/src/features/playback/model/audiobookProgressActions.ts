import { useCallback, useState } from "react";
import type { Book, Track } from "@core/types";
import { resolveAudiobookPlaybackIntent } from "@core/playback/audiobookPlaybackIntent";
import {
  findCycleContainingBook,
  resolveEarlierCycleBookConfirm,
} from "@core/playback/cycleListenProgress";
import { getTonezenApi, useSaveProgressMutation } from "@/shared/api";
import type { UseAudiobookSessionOptions } from "./audiobookSessionTypes";
import type { ProgressSyncConflictPromptModel } from "../ui/ProgressSyncConflictPrompt";

type UseAudiobookProgressActionsOptions = Pick<
  UseAudiobookSessionOptions,
  | "selectedBook"
  | "tracks"
  | "progressByBook"
  | "refreshLibrary"
  | "setTracks"
  | "cycles"
  | "tracksByBookId"
> & {
  playAudiobookTrackResolved: (
    book: Book,
    bookTracks: Track[],
    track: Track,
    startMs: number,
  ) => Promise<void>;
};

export type EarlierCycleBookPromptModel = {
  track: Track;
  laterBookTitle: string;
};

function formatPositionLabel(tracks: Track[], trackId: string, positionMs: number): string {
  const track = tracks.find((item) => item.id === trackId);
  const title = track?.title ?? "Глава";
  const totalSec = Math.max(0, Math.floor(positionMs / 1000));
  const min = Math.floor(totalSec / 60);
  const sec = totalSec % 60;
  return `${title} · ${min}:${String(sec).padStart(2, "0")}`;
}

export function useAudiobookProgressActions({
  selectedBook,
  tracks,
  progressByBook,
  refreshLibrary,
  setTracks,
  cycles,
  tracksByBookId,
  playAudiobookTrackResolved,
}: UseAudiobookProgressActionsOptions) {
  const api = getTonezenApi();
  const saveProgress = useSaveProgressMutation();
  const [earlierChapterPrompt, setEarlierChapterPrompt] = useState<Track | null>(null);
  const [earlierCycleBookPrompt, setEarlierCycleBookPrompt] =
    useState<EarlierCycleBookPromptModel | null>(null);
  const [syncConflictTrack, setSyncConflictTrack] = useState<Track | null>(null);
  const [syncConflictModel, setSyncConflictModel] = useState<ProgressSyncConflictPromptModel | null>(
    null,
  );

  const playBookTrack = useCallback(
    async (
      track: Track,
      options?: { skipSyncConflictPrompt?: boolean; skipEarlierCyclePrompt?: boolean },
    ) => {
      if (!selectedBook) return;
      const sortedTracks = [...tracks].sort((a, b) => a.sortOrder - b.sortOrder);
      const saved = progressByBook.get(selectedBook.id) ?? null;
      const intent = resolveAudiobookPlaybackIntent(sortedTracks, saved, track, {
        skipSyncConflictPrompt: options?.skipSyncConflictPrompt,
      });
      if (intent.kind === "ConfirmProgressSyncConflict") {
        setSyncConflictTrack(track);
        setSyncConflictModel({
          localLabel: formatPositionLabel(tracks, intent.localTrackId, intent.localPositionMs),
          serverLabel: formatPositionLabel(
            tracks,
            intent.server.trackId,
            intent.server.positionMs,
          ),
        });
        return;
      }
      if (!options?.skipEarlierCyclePrompt) {
        const cycle = findCycleContainingBook(cycles, selectedBook.id);
        const laterBook = cycle
          ? resolveEarlierCycleBookConfirm(cycle, selectedBook, tracksByBookId, progressByBook)
          : null;
        if (laterBook) {
          setEarlierCycleBookPrompt({ track, laterBookTitle: laterBook.title });
          return;
        }
      }
      if (intent.kind === "ConfirmEarlierChapter") {
        setEarlierChapterPrompt(track);
        return;
      }
      const startMs = intent.kind === "Resume" ? intent.positionMs : 0;
      await playAudiobookTrackResolved(selectedBook, tracks, track, startMs);
    },
    [
      cycles,
      playAudiobookTrackResolved,
      progressByBook,
      selectedBook,
      tracks,
      tracksByBookId,
    ],
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

  const dismissEarlierCycleBookPrompt = useCallback(() => setEarlierCycleBookPrompt(null), []);

  const confirmEarlierCycleBookPrompt = useCallback(() => {
    const prompt = earlierCycleBookPrompt;
    setEarlierCycleBookPrompt(null);
    if (prompt) void playBookTrack(prompt.track, { skipEarlierCyclePrompt: true });
  }, [earlierCycleBookPrompt, playBookTrack]);

  const dismissSyncConflictPrompt = useCallback(() => {
    setSyncConflictTrack(null);
    setSyncConflictModel(null);
  }, []);

  const chooseSyncConflictLocal = useCallback(async () => {
    if (!selectedBook || !syncConflictTrack) return;
    const track = syncConflictTrack;
    dismissSyncConflictPrompt();
    await api.progress.chooseLocal(selectedBook.id);
    await refreshLibrary();
    await playBookTrack(track, { skipSyncConflictPrompt: true });
  }, [
    api.progress,
    dismissSyncConflictPrompt,
    playBookTrack,
    refreshLibrary,
    selectedBook,
    syncConflictTrack,
  ]);

  const chooseSyncConflictServer = useCallback(async () => {
    if (!selectedBook) return;
    dismissSyncConflictPrompt();
    const applied = await api.progress.chooseServer(selectedBook.id);
    await refreshLibrary();
    if (!applied) return;
    const sortedTracks = [...tracks].sort((a, b) => a.sortOrder - b.sortOrder);
    const track =
      sortedTracks.find((item) => item.id === applied.trackId) ?? sortedTracks[0] ?? null;
    if (track) {
      await playAudiobookTrackResolved(selectedBook, tracks, track, applied.positionMs);
    }
  }, [
    api.progress,
    dismissSyncConflictPrompt,
    playAudiobookTrackResolved,
    refreshLibrary,
    selectedBook,
    tracks,
  ]);

  return {
    earlierChapterPrompt,
    dismissEarlierChapterPrompt,
    confirmEarlierChapterPrompt,
    earlierCycleBookPrompt,
    dismissEarlierCycleBookPrompt,
    confirmEarlierCycleBookPrompt,
    syncConflictModel,
    dismissSyncConflictPrompt,
    chooseSyncConflictLocal,
    chooseSyncConflictServer,
    playBookTrack,
    continueBook,
    markBookListened,
    markTrackListened,
  };
}
