import { useAudiobookCyclePlay } from "./audiobookCyclePlay";
import { useAudiobookEnsureLocal } from "./audiobookEnsureLocal";
import { useAudiobookProgressActions } from "./audiobookProgressActions";
import type { UseAudiobookSessionOptions } from "./audiobookSessionTypes";
import { useAudiobookSkipHandlers } from "./audiobookSkipHandlers";

export type { UseAudiobookSessionOptions } from "./audiobookSessionTypes";

export function useAudiobookSession(options: UseAudiobookSessionOptions) {
  const { ensureAudiobookTrackLocal, playAudiobookTrackResolved, advanceAudiobookTrack } =
    useAudiobookEnsureLocal(options);

  const { cyclePlayingId, playCycle } = useAudiobookCyclePlay({
    ...options,
    ensureAudiobookTrackLocal,
  });

  const {
    earlierChapterPrompt,
    dismissEarlierChapterPrompt,
    confirmEarlierChapterPrompt,
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
    playAudiobookTrackResolved,
  });

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
