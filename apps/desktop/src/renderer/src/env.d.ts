export interface TonezenApi {
  session: {
    get: () => Promise<{ state: string; email: string | null; displayName: string | null }>;
    setOnline: (online: boolean) => Promise<void>;
    login: (
      email: string,
      password: string,
    ) => Promise<{ state: string; email: string | null; displayName: string | null }>;
    logout: () => Promise<void>;
    updateProfile: (
      displayName: string,
    ) => Promise<{ state: string; email: string | null; displayName: string | null }>;
    changePassword: (
      newPassword: string,
    ) => Promise<{ state: string; email: string | null; displayName: string | null }>;
  };
  catalog: {
    sync: () => Promise<Array<{ id: string; title: string; contentType: string; author?: string }>>;
  };
  db: {
    getBooks: () => Promise<
      Array<{ id: string; slug: string; contentType: string; title: string; author?: string }>
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
  favorites: {
    list: () => Promise<string[]>;
    toggle: (bookId: string) => Promise<string[]>;
  };
  sync: {
    status: () => Promise<{ pendingCount: number }>;
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
