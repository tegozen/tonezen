import type { Dispatch, MutableRefObject, SetStateAction } from "react";
import type { Book, SessionState, Track } from "@core/types";
import type { MusicListTrack } from "@core/catalog/musicList";
import type { DownloadQueueApi } from "@/shared/api";

export interface MusicEnsureLocalDeps {
  sessionState: SessionState;
  musicTracks: MusicListTrack[];
  allTracks: Track[];
  downloadQueue: DownloadQueueApi;
  refreshLibrary: () => Promise<void>;
  setMusicError: Dispatch<SetStateAction<string | null>>;
}

export interface MusicPlayControlsDeps {
  books: Book[];
  allTracks: Track[];
  musicTracks: MusicListTrack[];
  setMusicTracks: Dispatch<SetStateAction<MusicListTrack[]>>;
  setTracks: Dispatch<SetStateAction<Track[]>>;
  sessionState: SessionState;
  downloadQueue: DownloadQueueApi;
  playTrack: (track: Track, startMs?: number, book?: Book | null) => void;
  stopPlayback: () => void;
  pauseOrResume: () => void;
  seekTo: (fraction: number) => void;
  currentTrack: Track | null;
  positionMs: number;
  musicMode: boolean;
  setMusicMode: Dispatch<SetStateAction<boolean>>;
  setMusicPlaybackBook: Dispatch<SetStateAction<Book | null>>;
  setMusicError: Dispatch<SetStateAction<string | null>>;
  musicQueueRef: MutableRefObject<MusicListTrack[]>;
  deletingTrackIdRef: MutableRefObject<string | null>;
  setMusicQueue: Dispatch<SetStateAction<MusicListTrack[]>>;
  musicStartedInSessionRef: MutableRefObject<boolean>;
  playbackMusicTracks: MusicListTrack[];
  ensureTrackLocal: (
    bookId: string,
    trackId: string,
    options?: {
      title?: string;
      subtitle?: string | null;
      priority?: "PLAY" | "USER" | "BULK" | "PREFETCH";
      suppressPlaybackError?: boolean;
    },
  ) => Promise<Track | null>;
  resolveLocalTrack: (listTrack: MusicListTrack) => Promise<Track | null>;
  prefetchNextTrack: (queue: MusicListTrack[], currentTrackId: string) => void;
  isTrackPlayable: (track: MusicListTrack) => boolean;
}

export interface MusicDownloadActionsDeps {
  sessionState: SessionState;
  musicTracks: MusicListTrack[];
  downloadQueue: DownloadQueueApi;
  refreshLibrary: () => Promise<void>;
  stopPlayback: () => void;
  currentTrack: Track | null;
  setMusicError: Dispatch<SetStateAction<string | null>>;
  setMusicMode: Dispatch<SetStateAction<boolean>>;
  setMusicPlaybackBook: Dispatch<SetStateAction<Book | null>>;
  setMusicQueue: Dispatch<SetStateAction<MusicListTrack[]>>;
  musicQueueRef: MutableRefObject<MusicListTrack[]>;
  deletingTrackIdRef: MutableRefObject<string | null>;
  prefetchJobRef: MutableRefObject<number>;
}
