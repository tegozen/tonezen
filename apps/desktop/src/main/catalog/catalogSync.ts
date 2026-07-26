import type { Book, Cycle, Track } from "@core/types.js";
import { normalizeCycleBookOrder } from "@core/catalog/cycleBooks.js";
import { apiV1Url } from "@core/platform/serverPaths.js";
import { normalizeWaveformPeaks } from "@core/catalog/waveformPeaks.js";
import { LocalDatabase } from "../db/localDatabase.js";

interface ApiBook {
  id: string;
  slug: string;
  content_type: string;
  title: string;
  author?: string | null;
}

interface ApiTrack {
  id: string;
  sort_order: number;
  title: string;
  filename: string;
  artist?: string | null;
  duration_ms?: number;
  waveform_peaks?: unknown;
}

interface ApiCycle {
  id: string;
  slug: string;
  title: string;
  book_order: string[];
  books: ApiBook[];
}

function mapBook(raw: ApiBook): Book {
  return {
    id: raw.id,
    slug: raw.slug,
    contentType: raw.content_type === "music" ? "music" : "audiobook",
    title: raw.title,
    author: raw.author ?? undefined,
  };
}

function mapTrack(raw: ApiTrack, bookId: string): Track {
  return {
    id: raw.id,
    bookId,
    sortOrder: raw.sort_order,
    title: raw.title,
    filename: raw.filename,
    artist: raw.artist ?? undefined,
    durationMs: raw.duration_ms,
    waveformPeaks: normalizeWaveformPeaks(raw.waveform_peaks) ?? undefined,
  };
}

export class CatalogSyncService {
  private syncInFlight: Promise<Book[]> | null = null;

  constructor(
    private baseUrl: string,
    private getAccessToken: () => string | null,
  ) {}

  async fetchCycles(): Promise<Cycle[]> {
    const headers = this.buildHeaders();
    const cyclesJson = await this.fetchCatalogJson<{ cycles: ApiCycle[] }>("/catalog/cycles", headers);
    return (cyclesJson.cycles ?? []).map((cycle) => {
      const books = (cycle.books ?? []).map(mapBook);
      const bookOrder =
        books.length > 0
          ? books.map((book) => book.slug)
          : normalizeCycleBookOrder(cycle.book_order ?? [], books);
      return {
        id: cycle.id,
        slug: cycle.slug,
        title: cycle.title,
        bookOrder,
        books,
      };
    });
  }

  async fetchBooks(): Promise<Book[]> {
    const cycles = await this.fetchCycles();
    const headers = this.buildHeaders();
    const musicJson = await this.fetchCatalogJson<{ albums: ApiBook[] }>("/catalog/music", headers);

    const books: Book[] = [];
    for (const cycle of cycles) {
      for (const book of cycle.books) {
        books.push(book);
      }
    }
    for (const album of musicJson.albums ?? []) {
      books.push(mapBook(album));
    }
    return books;
  }

  async fetchBookTracks(bookId: string): Promise<Track[]> {
    const detail = await this.fetchCatalogJson<{ tracks?: ApiTrack[] }>(
      `/catalog/books/${bookId}`,
      this.buildHeaders(),
    );
    return (detail.tracks ?? []).map((track) => mapTrack(track, bookId));
  }

  async fetchAllTracks(): Promise<Track[]> {
    const json = await this.fetchCatalogJson<{ tracks?: Array<ApiTrack & { book_id: string }> }>(
      "/catalog/tracks",
      this.buildHeaders(),
    );
    return (json.tracks ?? []).map((track) => mapTrack(track, track.book_id));
  }

  syncCatalog(): Promise<Book[]> {
    if (!this.syncInFlight) {
      this.syncInFlight = this.performSyncCatalog().finally(() => {
        this.syncInFlight = null;
      });
    }
    return this.syncInFlight;
  }

  private async performSyncCatalog(): Promise<Book[]> {
    const cycles = await this.fetchCycles();
    const books = await this.fetchBooks();
    LocalDatabase.upsertBooks(books);
    LocalDatabase.upsertCycles(cycles);
    const bookIds = books.map((book) => book.id);
    const cycleIds = cycles.map((cycle) => cycle.id);
    LocalDatabase.deleteCyclesNotIn(cycleIds);
    const tracks = await this.fetchAllTracks();
    LocalDatabase.upsertTracks(tracks);
    if (tracks.length > 0) {
      LocalDatabase.deleteTracksNotInIds(tracks.map((track) => track.id));
    }
    LocalDatabase.deleteTracksForBooksNotIn(bookIds);
    LocalDatabase.deleteBooksNotIn(bookIds);
    return LocalDatabase.getBooks();
  }

  private buildHeaders(): HeadersInit {
    const token = this.getAccessToken();
    return token ? { Authorization: `Bearer ${token}` } : {};
  }

  private async fetchCatalogJson<T>(path: string, headers: HeadersInit): Promise<T> {
    const res = await fetch(apiV1Url(this.baseUrl, path), { headers });
    if (!res.ok) {
      throw new Error(`Catalog request failed (${res.status})`);
    }
    return (await res.json()) as T;
  }
}
