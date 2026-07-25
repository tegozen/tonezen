import type { AudiobookProgress, Book, ContentType, Cycle, SessionState, Track } from "@core/types";
import type {
  DownloadAwaitResult,
  DownloadQueueState,
  EnqueueDownloadRequest,
} from "@core/downloads/downloadQueueState";
import type { DownloadPriority } from "@core/downloads/downloadQueuePolicy";

export type DiagnosticErrorEntry = {
  area: "download" | "playback" | "sync" | "app";
  message: string;
  code?: string;
  bookId?: string;
  trackId?: string;
  bookTitle?: string;
  trackTitle?: string;
  details?: string;
};

export type SessionSnapshot = {
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
    verifyInviteCode: (code: string) => Promise<boolean>;
    register: (input: {
      inviteCode: string;
      email: string;
      password: string;
      displayName?: string;
    }) => Promise<SessionSnapshot>;
    requestPasswordRecovery: (email: string) => Promise<void>;
    getReferralCode: () => Promise<string>;
    logout: () => Promise<void>;
    updateProfile: (displayName: string) => Promise<SessionSnapshot>;
    changePassword: (currentPassword: string, newPassword: string) => Promise<SessionSnapshot>;
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
    getLibrarySnapshot: (options?: { reconcileLocalPaths?: boolean }) => Promise<{
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
    onFailed: (callback: (entry: DiagnosticErrorEntry) => void) => () => void;
  };
  diagnostics: {
    logError: (entry: DiagnosticErrorEntry) => Promise<string>;
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
