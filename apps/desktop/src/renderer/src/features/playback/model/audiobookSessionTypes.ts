import type { Dispatch, SetStateAction } from "react";
import type { AudiobookProgress, Book, Cycle, SessionState, Track } from "@core/types";
import type { DownloadQueueApi, RefreshLibraryOptions } from "@/shared/api";

export interface AudiobookSessionMusicHandlers {
  setMusicMode: (value: boolean) => void;
  handleSkipNext: () => boolean;
  handleSkipPrevious: () => boolean;
  handleTrackEnded: () => boolean;
}

export interface UseAudiobookSessionOptions {
  sessionState: SessionState;
  books: Book[];
  cycles: Cycle[];
  selectedBook: Book | null;
  setSelectedBook: (book: Book | null) => void;
  selectedCycle: Cycle | null;
  setSelectedCycle: (cycle: Cycle | null) => void;
  tracks: Track[];
  setTracks: (tracks: Track[]) => void;
  tracksByBookId: Map<string, Track[]>;
  progressByBook: Map<string, AudiobookProgress>;
  setProgressList: Dispatch<SetStateAction<AudiobookProgress[]>>;
  refreshLibrary: (options?: RefreshLibraryOptions) => Promise<void>;
  downloadQueue: DownloadQueueApi;
  playTrack: (track: Track, startMs?: number, book?: Book | null) => void;
  stopPlayback: () => void;
  pauseOrResume: () => void;
  currentTrack: Track | null;
  durationMs: number;
  isPlaying: boolean;
  showToast: (message: string) => void;
  closeExpandedPlayer: () => void;
  music: AudiobookSessionMusicHandlers;
}
