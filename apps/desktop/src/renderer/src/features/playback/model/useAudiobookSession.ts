import { useCallback, useRef } from "react";
import type { Cycle } from "@core/types";
import { useAudiobookCyclePlay } from "./audiobookCyclePlay";
import { useAudiobookEnsureLocal } from "./audiobookEnsureLocal";
import { useAudiobookProgressActions } from "./audiobookProgressActions";
import type { UseAudiobookSessionOptions } from "./audiobookSessionTypes";
import { useAudiobookSkipHandlers } from "./audiobookSkipHandlers";
import type { ProgressSyncConflictPromptModel } from "../ui/ProgressSyncConflictPrompt";

export type { UseAudiobookSessionOptions } from "./audiobookSessionTypes";

export function useAudiobookSession(options: UseAudiobookSessionOptions) {
  const { ensureAudiobookTrackLocal, playAudiobookTrackResolved, advanceAudiobookTrack } =
    useAudiobookEnsureLocal(options);

  const beginCycleSyncConflictRef = useRef<(model: ProgressSyncConflictPromptModel) => void>(
    () => undefined,
  );
  const playCycleRef = useRef<
    (cycle: Cycle, opts?: { skipSyncConflictPrompt?: boolean }) => Promise<void>
  >(async () => undefined);
  const consumePendingCycleRef = useRef<() => Cycle | null>(() => null);
  const clearPendingCycleRef = useRef<() => void>(() => undefined);

  const resumePendingCyclePlay = useCallback(async () => {
    const cycle = consumePendingCycleRef.current();
    if (cycle) await playCycleRef.current(cycle, { skipSyncConflictPrompt: true });
  }, []);

  const clearPendingCyclePlay = useCallback(() => {
    clearPendingCycleRef.current();
  }, []);

  const {
    earlierChapterPrompt,
    dismissEarlierChapterPrompt,
    confirmEarlierChapterPrompt,
    earlierCycleBookPrompt,
    dismissEarlierCycleBookPrompt,
    confirmEarlierCycleBookPrompt,
    syncConflictModel,
    beginCycleSyncConflict,
    dismissSyncConflictPrompt,
    chooseSyncConflictLocal,
    chooseSyncConflictServer,
    playBookTrack,
    continueBook,
    markBookListened,
    markTrackListened,
  } = useAudiobookProgressActions({
    selectedBook: options.selectedBook,
    tracks: options.tracks,
    progressByBook: options.progressByBook,
    refreshLibrary: options.refreshLibrary,
    setTracks: options.setTracks,
    cycles: options.cycles,
    tracksByBookId: options.tracksByBookId,
    playAudiobookTrackResolved,
    resumePendingCyclePlay,
    clearPendingCyclePlay,
  });

  beginCycleSyncConflictRef.current = beginCycleSyncConflict;

  const { cyclePlayingId, playCycle, consumePendingCycleAfterConflict, clearPendingCycleAfterConflict } =
    useAudiobookCyclePlay({
      ...options,
      ensureAudiobookTrackLocal,
      setSyncConflictModel: (model) => {
        if (model) beginCycleSyncConflictRef.current(model);
      },
    });

  playCycleRef.current = playCycle;
  consumePendingCycleRef.current = consumePendingCycleAfterConflict;
  clearPendingCycleRef.current = clearPendingCycleAfterConflict;

  const { handleSkipNext, handleSkipPrevious, handleTrackEnded } = useAudiobookSkipHandlers({
    cycles: options.cycles,
    selectedBook: options.selectedBook,
    selectedCycle: options.selectedCycle,
    tracks: options.tracks,
    tracksByBookId: options.tracksByBookId,
    currentTrack: options.currentTrack,
    durationMs: options.durationMs,
    setProgressList: options.setProgressList,
    setSelectedBook: options.setSelectedBook,
    setSelectedCycle: options.setSelectedCycle,
    setTracks: options.setTracks,
    music: options.music,
    advanceAudiobookTrack,
    playBookTrack,
  });

  return {
    cyclePlayingId,
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
    playCycle,
    continueBook,
    markBookListened,
    markTrackListened,
    handleSkipNext,
    handleSkipPrevious,
    handleTrackEnded,
  };
}
