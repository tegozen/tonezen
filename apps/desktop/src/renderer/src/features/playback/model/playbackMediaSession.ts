import { CyclePlaybackResolver } from "@core/playback/cyclePlayback";
import type { Book, Track } from "@core/types";
import type { MutableRefObject, RefObject } from "react";
import { setupMediaSession } from "./mediaSessionController";
import type { PlaybackSkipHandlers } from "./playbackTypes";

const cycleResolver = new CyclePlaybackResolver();

interface BuildMediaSessionHandlersOptions {
  audioRef: RefObject<HTMLAudioElement | null>;
  currentTrackRef: RefObject<Track | null>;
  skipTracksRef: RefObject<Track[]>;
  skipHandlersRef: RefObject<PlaybackSkipHandlers | undefined>;
  playTrackRef: MutableRefObject<(track: Track, startMs?: number, book?: Book | null) => void>;
  syncPlayingState: (audio: HTMLAudioElement) => void;
  syncPositionState: () => void;
  durationSecondsForSeek: (audio: HTMLAudioElement, track: Track | null) => number;
}

export function syncMediaSessionForTrack(
  track: Track,
  book: Book,
  {
    audioRef,
    currentTrackRef,
    skipTracksRef,
    skipHandlersRef,
    playTrackRef,
    syncPlayingState,
    syncPositionState,
    durationSecondsForSeek,
  }: BuildMediaSessionHandlersOptions,
) {
  setupMediaSession(
    {
      title: track.title,
      artist: book.author ?? book.title,
      album: book.title,
    },
    {
      play: () => {
        const audio = audioRef.current;
        if (!audio) return;
        void audio.play().then(() => syncPlayingState(audio));
      },
      pause: () => {
        audioRef.current?.pause();
      },
      nextTrack: () => {
        if (skipHandlersRef.current?.onSkipNext?.()) return;
        const current = currentTrackRef.current;
        const list = skipTracksRef.current;
        if (!current) return;
        const next = cycleResolver.nextInBook(current, list);
        if (next?.localPath) playTrackRef.current(next);
      },
      previousTrack: () => {
        if (skipHandlersRef.current?.onSkipPrevious?.()) return;
        const current = currentTrackRef.current;
        const list = skipTracksRef.current;
        if (!current) return;
        const prev = cycleResolver.previousInBook(current, list);
        if (prev?.localPath) playTrackRef.current(prev);
      },
      seekTo: (timeSeconds) => {
        if (!audioRef.current) return;
        audioRef.current.currentTime = timeSeconds;
        syncPositionState();
      },
      seekBackward: (offsetSeconds) => {
        if (!audioRef.current) return;
        audioRef.current.currentTime = Math.max(0, audioRef.current.currentTime - offsetSeconds);
      },
      seekForward: (offsetSeconds) => {
        if (!audioRef.current) return;
        const audio = audioRef.current;
        const duration = durationSecondsForSeek(audio, currentTrackRef.current);
        const next = audio.currentTime + offsetSeconds;
        audio.currentTime = duration > 0 ? Math.min(duration, next) : next;
      },
      getTiming: () => {
        const audio = audioRef.current;
        const track = currentTrackRef.current;
        if (!audio) return null;
        const durationSec = durationSecondsForSeek(audio, track);
        if (durationSec <= 0) return null;
        return {
          duration: durationSec,
          position: audio.currentTime,
          playbackRate: audio.playbackRate || 1,
        };
      },
    },
  );
}
