export interface TonezenApi {
  session: {
    get: () => Promise<{ state: string; userId: string | null }>;
    setOnline: (online: boolean) => Promise<void>;
    login: (email: string, password: string) => Promise<{ state: string; userId: string | null }>;
    logout: () => Promise<void>;
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
        localPath?: string;
      }>
    >;
  };
  download: {
    track: (bookId: string, trackId: string) => Promise<string>;
    delete: (bookId: string, trackId: string) => Promise<void>;
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
