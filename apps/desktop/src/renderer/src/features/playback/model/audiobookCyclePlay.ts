import { useCallback, useRef, useState } from "react";
import type { Cycle, Track } from "@core/types";
import {
  orderedCycleEntriesFromResume,
  resolveCycleResumeTarget,
} from "@core/playback/cycleListenProgress";
import { shouldPromptProgressSyncConflict } from "@core/progress/progressMerge";
import { getTonezenApi, useSaveProgressMutation } from "@/shared/api";
import type { UseAudiobookSessionOptions } from "./audiobookSessionTypes";
import type { ProgressSyncConflictPromptModel } from "../ui/ProgressSyncConflictPrompt";

type UseAudiobookCyclePlayOptions = Pick<
  UseAudiobookSessionOptions,
  | "sessionState"
  | "tracksByBookId"
  | "progressByBook"
  | "downloadQueue"
  | "playTrack"
  | "stopPlayback"
  | "pauseOrResume"
  | "isPlaying"
  | "showToast"
  | "setSelectedBook"
  | "setSelectedCycle"
  | "setTracks"
  | "music"
> & {
  ensureAudiobookTrackLocal: (bookId: string, trackId: string) => Promise<Track | null>;
  setSyncConflictModel: (model: ProgressSyncConflictPromptModel | null) => void;
};

function formatProgressLabel(tracks: Track[], trackId: string, positionMs: number): string {
  const track = tracks.find((item) => item.id === trackId);
  const title = track?.title ?? "Глава";
  const totalSec = Math.max(0, Math.floor(positionMs / 1000));
  const min = Math.floor(totalSec / 60);
  const sec = totalSec % 60;
  return `${title} · ${min}:${String(sec).padStart(2, "0")}`;
}

export function useAudiobookCyclePlay({
  sessionState,
  tracksByBookId,
  progressByBook,
  downloadQueue,
  playTrack,
  stopPlayback,
  pauseOrResume,
  isPlaying,
  showToast,
  setSelectedBook,
  setSelectedCycle,
  setTracks,
  music,
  ensureAudiobookTrackLocal,
  setSyncConflictModel,
}: UseAudiobookCyclePlayOptions) {
  const api = getTonezenApi();
  const saveProgress = useSaveProgressMutation();
  const [cyclePlayingId, setCyclePlayingId] = useState<string | null>(null);
  const pendingCycleAfterConflictRef = useRef<Cycle | null>(null);

  const playCycle = useCallback(
    async (cycle: Cycle, options?: { skipSyncConflictPrompt?: boolean }) => {
      if (cyclePlayingId === cycle.id && isPlaying && !options?.skipSyncConflictPrompt) {
        pauseOrResume();
        return;
      }
      setCyclePlayingId(cycle.id);
      music.setMusicMode(false);

      let progressMap = progressByBook;
      if (options?.skipSyncConflictPrompt) {
        const next = new Map(progressByBook);
        for (const book of cycle.books) {
          const row = await window.tonezen.progress.get(book.id);
          if (row) next.set(book.id, row);
          else next.delete(book.id);
        }
        progressMap = next;
      }

      const resume = resolveCycleResumeTarget(cycle, tracksByBookId, progressMap);
      if (!resume) {
        showToast("В цикле нет доступных глав для воспроизведения");
        setCyclePlayingId(null);
        return;
      }

      const progress = progressMap.get(resume.book.id) ?? null;
      if (!options?.skipSyncConflictPrompt && progress && shouldPromptProgressSyncConflict(progress)) {
        const snapshot = {
          trackId: progress.serverTrackId!,
          positionMs: progress.serverPositionMs!,
        };
        const bookTracks = tracksByBookId.get(resume.book.id) ?? [];
        pendingCycleAfterConflictRef.current = cycle;
        setSelectedBook(resume.book);
        setSelectedCycle(cycle);
        setTracks(bookTracks);
        setSyncConflictModel({
          localLabel: formatProgressLabel(bookTracks, progress.trackId, progress.positionMs),
          serverLabel: formatProgressLabel(bookTracks, snapshot.trackId, snapshot.positionMs),
        });
        setCyclePlayingId(null);
        return;
      }

      if (!resume.track.localPath && sessionState !== "AuthenticatedOnline") {
        showToast("Нет сети — нужен интернет для первой загрузки");
        stopPlayback();
        setCyclePlayingId(null);
        return;
      }
      const local = resume.track.localPath
        ? resume.track
        : await ensureAudiobookTrackLocal(resume.book.id, resume.track.id);
      if (!local?.localPath) {
        showToast("Не удалось скачать");
        stopPlayback();
        setCyclePlayingId(null);
        return;
      }
      setSelectedBook(resume.book);
      setSelectedCycle(cycle);
      const bookTracks = await api.db.getTracks(resume.book.id);
      setTracks(bookTracks);
      playTrack(local, resume.startPositionMs, resume.book);
      await saveProgress.mutateAsync({
        bookId: resume.book.id,
        trackId: local.id,
        positionMs: Math.max(1, resume.startPositionMs),
      });
      const entries = orderedCycleEntriesFromResume(cycle, tracksByBookId, {
        book: resume.book,
        track: local,
        startPositionMs: resume.startPositionMs,
      });
      if (entries.length > 1 && sessionState === "AuthenticatedOnline") {
        const next = entries[1];
        if (!next.track.localPath) {
          void downloadQueue
            .enqueue({
              bookId: next.book.id,
              trackId: next.track.id,
              priority: "PREFETCH",
              title: next.track.title,
              subtitle: next.book.title,
              contentType: next.book.contentType,
            })
            .catch(() => {});
        }
      }
    },
    [
      api,
      cyclePlayingId,
      downloadQueue,
      ensureAudiobookTrackLocal,
      isPlaying,
      music,
      pauseOrResume,
      playTrack,
      progressByBook,
      saveProgress,
      sessionState,
      setSelectedBook,
      setSelectedCycle,
      setSyncConflictModel,
      setTracks,
      showToast,
      stopPlayback,
      tracksByBookId,
    ],
  );

  const consumePendingCycleAfterConflict = useCallback((): Cycle | null => {
    const cycle = pendingCycleAfterConflictRef.current;
    pendingCycleAfterConflictRef.current = null;
    return cycle;
  }, []);

  const clearPendingCycleAfterConflict = useCallback(() => {
    pendingCycleAfterConflictRef.current = null;
  }, []);

  return {
    cyclePlayingId,
    playCycle,
    consumePendingCycleAfterConflict,
    clearPendingCycleAfterConflict,
  };
}
