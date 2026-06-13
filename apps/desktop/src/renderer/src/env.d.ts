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
