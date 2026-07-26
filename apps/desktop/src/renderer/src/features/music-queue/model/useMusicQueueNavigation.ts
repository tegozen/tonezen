import { useCallback } from "react";
import {
  nextMusicIndex,
  previousMusicIndex,
  type MusicListTrack,
} from "@core/catalog/musicList";
import {
  findNextPlayableIndex,
  findPreviousPlayableIndex,
  shouldRestartCurrentMusicTrack,
} from "@core/playback/musicPlayback";
import type { MusicPlayControlsDeps } from "./musicPlaybackDeps";
import type { PlayMusicTrackFn } from "./useMusicTrackPlayback";

type MusicQueueNavigationDeps = Pick<
  MusicPlayControlsDeps,
  | "stopPlayback"
  | "pauseOrResume"
  | "seekTo"
  | "currentTrack"
  | "positionMs"
  | "musicMode"
  | "musicQueueRef"
  | "isTrackPlayable"
> & {
  playMusicTrack: PlayMusicTrackFn;
};

export function useMusicQueueNavigation({
  stopPlayback,
  pauseOrResume,
  seekTo,
  currentTrack,
  positionMs,
  musicMode,
  musicQueueRef,
  isTrackPlayable,
  playMusicTrack,
}: MusicQueueNavigationDeps) {
  const advanceToPlayableTrack = useCallback(
    async (
      queue: MusicListTrack[],
      fromIndex: number,
      direction: "next" | "previous",
    ): Promise<boolean> => {
      const stepper = direction === "next" ? nextMusicIndex : previousMusicIndex;
      const finder = direction === "next" ? findNextPlayableIndex : findPreviousPlayableIndex;
      let index = fromIndex;
      for (let step = 0; step < queue.length - 1; step += 1) {
        const nextIndex = finder(queue, index, isTrackPlayable, stepper);
        if (nextIndex == null) return false;
        const played = await playMusicTrack(queue[nextIndex], {
          showDownloadProgress: false,
          advancePlayback: true,
        });
        if (played) return true;
        index = nextIndex;
      }
      return false;
    },
    [isTrackPlayable, playMusicTrack],
  );

  const handleSkipNext = useCallback(() => {
    const queue = musicQueueRef.current;
    if (!musicMode || queue.length === 0 || !currentTrack) return false;
    const index = queue.findIndex((item) => item.trackId === currentTrack.id);
    if (index < 0) return false;
    void advanceToPlayableTrack(queue, index, "next");
    return true;
  }, [advanceToPlayableTrack, currentTrack, musicMode, musicQueueRef]);

  const handleSkipPrevious = useCallback(() => {
    const queue = musicQueueRef.current;
    if (!musicMode || queue.length === 0 || !currentTrack) return false;
    if (shouldRestartCurrentMusicTrack(positionMs)) {
      seekTo(0);
      return true;
    }
    const index = queue.findIndex((item) => item.trackId === currentTrack.id);
    if (index < 0) return false;
    void advanceToPlayableTrack(queue, index, "previous");
    return true;
  }, [advanceToPlayableTrack, currentTrack, musicMode, musicQueueRef, positionMs, seekTo]);

  const handleTrackEnded = useCallback(() => {
    const queue = musicQueueRef.current;
    if (!musicMode || queue.length === 0 || !currentTrack) return false;
    const index = queue.findIndex((item) => item.trackId === currentTrack.id);
    if (index < 0) return false;
    void (async () => {
      const advanced = await advanceToPlayableTrack(queue, index, "next");
      if (!advanced) {
        stopPlayback();
      }
    })();
    return true;
  }, [advanceToPlayableTrack, currentTrack, musicMode, musicQueueRef, stopPlayback]);

  const onMiniPlayerPlayPause = useCallback(
    (listTrack: MusicListTrack | undefined) => {
      if (musicMode && currentTrack && listTrack?.trackId === currentTrack.id) {
        pauseOrResume();
        return;
      }
      if (musicMode && listTrack) {
        void playMusicTrack(listTrack);
        return;
      }
      pauseOrResume();
    },
    [currentTrack, musicMode, pauseOrResume, playMusicTrack],
  );

  return {
    handleSkipNext,
    handleSkipPrevious,
    handleTrackEnded,
    onMiniPlayerPlayPause,
  };
}
