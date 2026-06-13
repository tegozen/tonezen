import type { Book, Cycle, Track } from "../shared/types.js";
import { normalizeCycleBookOrder } from "../shared/cycleBooks.js";
import { apiV1Url } from "../shared/serverPaths.js";
import { LocalDatabase } from "./database.js";

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
  duration_ms?: number;
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
    durationMs: raw.duration_ms,
  };
}

export class CatalogSyncService {
  constructor(
    private baseUrl: string,
    private getAccessToken: () => string | null,
  ) {}

  async fetchCycles(): Promise<Cycle[]> {
    const headers = this.buildHeaders();
    const cyclesRes = await fetch(apiV1Url(this.baseUrl, "/catalog/cycles"), { headers });
    const cyclesJson = (await cyclesRes.json()) as { cycles: ApiCycle[] };
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
    const musicRes = await fetch(apiV1Url(this.baseUrl, "/catalog/music"), { headers });
    const musicJson = (await musicRes.json()) as { albums: ApiBook[] };

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
    const res = await fetch(apiV1Url(this.baseUrl, `/catalog/books/${bookId}`), {
      headers: this.buildHeaders(),
    });
    const detail = (await res.json()) as { tracks?: ApiTrack[] };
    return (detail.tracks ?? []).map((track) => mapTrack(track, bookId));
  }

  async syncCatalog(): Promise<Book[]> {
    const cycles = await this.fetchCycles();
    const books = await this.fetchBooks();
    LocalDatabase.upsertBooks(books);
    LocalDatabase.upsertCycles(cycles);
    for (const book of books) {
      const tracks = await this.fetchBookTracks(book.id);
      LocalDatabase.upsertTracks(tracks);
    }
    return LocalDatabase.getBooks();
  }

  private buildHeaders(): HeadersInit {
    const token = this.getAccessToken();
    return token ? { Authorization: `Bearer ${token}` } : {};
  }
}
