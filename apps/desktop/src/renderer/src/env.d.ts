export interface TonezenApi {
  session: {
    get: () => Promise<{
      state: string;
      email: string | null;
      displayName: string | null;
      avatarUrl: string | null;
      memberSinceEpochMs: number | null;
    }>;
    setOnline: (online: boolean) => Promise<void>;
    login: (
      email: string,
      password: string,
    ) => Promise<{
      state: string;
      email: string | null;
      displayName: string | null;
      avatarUrl: string | null;
      memberSinceEpochMs: number | null;
    }>;
    logout: () => Promise<void>;
    updateProfile: (
      displayName: string,
    ) => Promise<{
      state: string;
      email: string | null;
      displayName: string | null;
      avatarUrl: string | null;
      memberSinceEpochMs: number | null;
    }>;
    changePassword: (
      newPassword: string,
    ) => Promise<{
      state: string;
      email: string | null;
      displayName: string | null;
      avatarUrl: string | null;
      memberSinceEpochMs: number | null;
    }>;
    uploadAvatar: (
      jpegBytes: Uint8Array,
    ) => Promise<{
      state: string;
      email: string | null;
      displayName: string | null;
      avatarUrl: string | null;
      memberSinceEpochMs: number | null;
    }>;
    onProfileUpdated: (
      callback: (snap: {
        state: string;
        email: string | null;
        displayName: string | null;
        avatarUrl: string | null;
        memberSinceEpochMs: number | null;
      }) => void,
    ) => () => void;
  };
  catalog: {
    sync: () => Promise<Array<{ id: string; title: string; contentType: string; author?: string }>>;
    onUpdated: (callback: () => void) => () => void;
  };
  db: {
    getBooks: () => Promise<
      Array<{ id: string; slug: string; contentType: string; title: string; author?: string }>
    >;
    getCycles: () => Promise<
      Array<{
        id: string;
        slug: string;
        title: string;
        bookOrder: string[];
        books: Array<{ id: string; slug: string; contentType: string; title: string; author?: string }>;
      }>
    >;
    getLibrarySnapshot: () => Promise<{
      books: Array<{ id: string; slug: string; contentType: string; title: string; author?: string }>;
      cycles: Array<{
        id: string;
        slug: string;
        title: string;
        bookOrder: string[];
        books: Array<{ id: string; slug: string; contentType: string; title: string; author?: string }>;
      }>;
      tracks: Array<{
        id: string;
        bookId: string;
        sortOrder: number;
        title: string;
        filename: string;
        durationMs?: number;
        localPath?: string;
      }>;
    }>;
    getAllTracks: () => Promise<
      Array<{
        id: string;
        bookId: string;
        sortOrder: number;
        title: string;
        filename: string;
        durationMs?: number;
        localPath?: string;
      }>
    >;
    getAllProgress: () => Promise<
      Array<{ bookId: string; trackId: string; positionMs: number; updatedAt: string }>
    >;
    getTracks: (bookId: string) => Promise<
      Array<{
        id: string;
        bookId: string;
        sortOrder: number;
        title: string;
        filename: string;
        durationMs?: number;
        localPath?: string;
      }>
    >;
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
    enqueue: (request: {
      bookId: string;
      trackId: string;
      priority: "PREFETCH" | "BULK" | "USER" | "PLAY";
      batchId?: string | null;
      title: string;
      subtitle?: string | null;
      contentType: string;
      enqueuedAt?: number;
    }) => Promise<void>;
    enqueueBatch: (
      requests: Array<{
        bookId: string;
        trackId: string;
        priority: "PREFETCH" | "BULK" | "USER" | "PLAY";
        batchId?: string | null;
        title: string;
        subtitle?: string | null;
        contentType: string;
        enqueuedAt?: number;
      }>,
      batchId?: string,
    ) => Promise<void>;
    awaitTrack: (
      bookId: string,
      trackId: string,
      options?: {
        priority?: "PREFETCH" | "BULK" | "USER" | "PLAY";
        title?: string;
        subtitle?: string | null;
        contentType?: string;
      },
    ) => Promise<"COMPLETED" | "CANCELLED" | "FAILED" | "OFFLINE">;
    cancelTrack: (bookId: string, trackId: string) => Promise<void>;
    cancelBatch: (batchId: string) => Promise<void>;
    cancelAll: () => Promise<void>;
    getQueueState: () => Promise<{
      queuedItems: Array<{
        bookId: string;
        trackId: string;
        title: string;
        subtitle: string | null;
        contentType: string;
        status: string;
        progress: number | null;
        batchId: string | null;
        enqueuedAt: number;
        completedAt: number | null;
      }>;
      completedHistory: Array<{
        bookId: string;
        trackId: string;
        title: string;
        subtitle: string | null;
        contentType: string;
        status: string;
        progress: number | null;
        batchId: string | null;
        enqueuedAt: number;
        completedAt: number | null;
      }>;
      activeBookId: string | null;
      activeTrackId: string | null;
      trackProgress: number | null;
      bulkDownloaded: number;
      bulkTotal: number;
      activeBatchId: string | null;
      pausedForNetwork: boolean;
    }>;
    onQueueState: (
      callback: (state: {
        queuedItems: Array<{
          bookId: string;
          trackId: string;
          title: string;
          subtitle: string | null;
          contentType: string;
          status: string;
          progress: number | null;
          batchId: string | null;
          enqueuedAt: number;
          completedAt: number | null;
        }>;
        completedHistory: Array<{
          bookId: string;
          trackId: string;
          title: string;
          subtitle: string | null;
          contentType: string;
          status: string;
          progress: number | null;
          batchId: string | null;
          enqueuedAt: number;
          completedAt: number | null;
        }>;
        activeBookId: string | null;
        activeTrackId: string | null;
        trackProgress: number | null;
        bulkDownloaded: number;
        bulkTotal: number;
        activeBatchId: string | null;
        pausedForNetwork: boolean;
      }) => void,
    ) => () => void;
  };
  sync: {
    status: () => Promise<{ pendingCount: number; lastSyncAtEpochMs: number | null }>;
    trigger: () => Promise<void>;
  };
  progress: {
    get: (bookId: string) => Promise<{
      bookId: string;
      trackId: string;
      positionMs: number;
      updatedAt: string;
    } | null>;
    save: (bookId: string, trackId: string, positionMs: number) => Promise<void>;
    onUpdated: (callback: (progress: {
      bookId: string;
      trackId: string;
      positionMs: number;
      updatedAt: string;
    }) => void) => () => void;
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
