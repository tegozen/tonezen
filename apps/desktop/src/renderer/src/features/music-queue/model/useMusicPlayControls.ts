import type { MusicPlayControlsDeps } from "./musicPlaybackDeps";
import { useMusicQueueNavigation } from "./useMusicQueueNavigation";
import { useMusicTrackPlayback } from "./useMusicTrackPlayback";

export function useMusicPlayControls(deps: MusicPlayControlsDeps) {
  const { playMusicTrack, playMusicWave, onMusicTabSelected } = useMusicTrackPlayback(deps);
  const { handleSkipNext, handleSkipPrevious, handleTrackEnded, onMiniPlayerPlayPause } =
    useMusicQueueNavigation({
      stopPlayback: deps.stopPlayback,
      pauseOrResume: deps.pauseOrResume,
      seekTo: deps.seekTo,
      currentTrack: deps.currentTrack,
      positionMs: deps.positionMs,
      musicMode: deps.musicMode,
      musicQueueRef: deps.musicQueueRef,
      isTrackPlayable: deps.isTrackPlayable,
      playMusicTrack,
    });

  return {
    playMusicTrack,
    playMusicWave,
    onMusicTabSelected,
    handleSkipNext,
    handleSkipPrevious,
    handleTrackEnded,
    onMiniPlayerPlayPause,
  };
}
