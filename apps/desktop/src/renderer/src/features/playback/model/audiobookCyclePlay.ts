import { useCallback, useState } from "react";
import type { Cycle, Track } from "@core/types";
import {
  orderedCycleEntriesFromResume,
  resolveCycleResumeTarget,
} from "@core/playback/cycleListenProgress";
import { getTonezenApi } from "@/shared/api";
import type { UseAudiobookSessionOptions } from "./audiobookSessionTypes";

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
};

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
}: UseAudiobookCyclePlayOptions) {
  const api = getTonezenApi();
  const [cyclePlayingId, setCyclePlayingId] = useState<string | null>(null);

  const playCycle = useCallback(
    async (cycle: Cycle) => {
      if (cyclePlayingId === cycle.id && isPlaying) {
        pauseOrResume();
        return;
      }
      setCyclePlayingId(cycle.id);
      music.setMusicMode(false);
      const resume = resolveCycleResumeTarget(cycle, tracksByBookId, progressByBook);
      if (!resume) {
        showToast("В цикле нет доступных глав для воспроизведения");
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
      sessionState,
      setSelectedBook,
      setSelectedCycle,
      setTracks,
      showToast,
      stopPlayback,
      tracksByBookId,
    ],
  );

  return { cyclePlayingId, playCycle };
}
