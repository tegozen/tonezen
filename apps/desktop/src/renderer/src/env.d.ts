export interface TPlayerApi {
  session: {
    get: () => Promise<{ state: string; userId: string | null }>;
    login: (email: string, password: string) => Promise<unknown>;
    logout: () => Promise<void>;
    refreshIfNeeded: () => Promise<string>;
  };
  db: {
    getBooks: () => Promise<
      Array<{
        id: string;
        slug: string;
        contentType: string;
        title: string;
        author?: string;
      }>
    >;
    getTracks: (bookId: string) => Promise<
      Array<{
        id: string;
        bookId: string;
        sortOrder: number;
        title: string;
        filename: string;
      }>
    >;
  };
  playback: {
    setActive: (active: boolean) => Promise<void>;
  };
}

declare global {
  interface Window {
    tplayer: TPlayerApi;
  }
}

export {};
