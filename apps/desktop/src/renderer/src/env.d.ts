import type { AudiobookProgress, Book, ContentType, Cycle, SessionState, Track } from "@shared/types";
import type {
  DownloadAwaitResult,
  DownloadQueueState,
  EnqueueDownloadRequest,
} from "@shared/downloadQueueState";
import type { DownloadPriority } from "@shared/downloadQueuePolicy";

type SessionSnapshot = {
  state: SessionState;
  email: string | null;
  displayName: string | null;
  avatarUrl: string | null;
  memberSinceEpochMs: number | null;
};

export interface TonezenApi {
  session: {
    get: () => Promise<SessionSnapshot>;
    setOnline: (online: boolean) => Promise<void>;
    login: (email: string, password: string) => Promise<SessionSnapshot>;
    logout: () => Promise<void>;
    updateProfile: (displayName: string) => Promise<SessionSnapshot>;
    changePassword: (newPassword: string) => Promise<SessionSnapshot>;
    uploadAvatar: (jpegBytes: Uint8Array) => Promise<SessionSnapshot>;
    onProfileUpdated: (callback: (snap: SessionSnapshot) => void) => () => void;
  };
  catalog: {
    sync: () => Promise<Array<{ id: string; title: string; contentType: ContentType; author?: string }>>;
    onUpdated: (callback: () => void) => () => void;
  };
  db: {
    getBooks: () => Promise<Book[]>;
    getCycles: () => Promise<Cycle[]>;
    getLibrarySnapshot: () => Promise<{
      books: Book[];
      cycles: Cycle[];
      tracks: Track[];
    }>;
    getAllTracks: () => Promise<Track[]>;
    getAllProgress: () => Promise<AudiobookProgress[]>;
    getTracks: (bookId: string) => Promise<Track[]>;
  };
  download: {
    track: (bookId: string, trackId: string) => Promise<string>;
    delete: (bookId: string, trackId: string) => Promise<void>;
    list: () => Promise<
      Array<{
        bookId: string;
        title: string;
        author?: string;
        contentType: string;
        downloadedTracks: number;
        totalTracks: number;
        sizeBytes: number;
        downloadProgress: number;
      }>
    >;
    storageStats: () => Promise<{ usedBytes: number }>;
    deleteAll: () => Promise<void>;
    enqueue: (request: EnqueueDownloadRequest) => Promise<void>;
    enqueueBatch: (requests: EnqueueDownloadRequest[], batchId?: string) => Promise<void>;
    awaitTrack: (
      bookId: string,
      trackId: string,
      options?: {
        priority?: DownloadPriority;
        title?: string;
        subtitle?: string | null;
        contentType?: string;
      },
    ) => Promise<DownloadAwaitResult>;
    cancelTrack: (bookId: string, trackId: string) => Promise<void>;
    cancelBatch: (batchId: string) => Promise<void>;
    cancelAll: () => Promise<void>;
    getQueueState: () => Promise<DownloadQueueState>;
    onQueueState: (callback: (state: DownloadQueueState) => void) => () => void;
  };
  sync: {
    status: () => Promise<{ pendingCount: number; lastSyncAtEpochMs: number | null }>;
    trigger: () => Promise<void>;
  };
  progress: {
    get: (bookId: string) => Promise<AudiobookProgress | null>;
    save: (bookId: string, trackId: string, positionMs: number) => Promise<void>;
    onUpdated: (callback: (progress: AudiobookProgress) => void) => () => void;
  };
  playback: {
    setActive: (active: boolean) => Promise<void>;
  };
}

declare global {
  interface Window {
    tonezen: TonezenApi;
  }
}

export {};
