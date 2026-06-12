import type { Book } from "../shared/types.js";
import { apiV1Url } from "../shared/serverPaths.js";

interface ApiBook {
  id: string;
  slug: string;
  content_type: string;
  title: string;
  author?: string | null;
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

export class CatalogSyncService {
  constructor(
    private baseUrl: string,
    private getAccessToken: () => string | null,
  ) {}

  async fetchBooks(): Promise<Book[]> {
    const headers = this.buildHeaders();
    const cyclesRes = await fetch(apiV1Url(this.baseUrl, "/catalog/cycles"), { headers });
    const cyclesJson = (await cyclesRes.json()) as { cycles: Array<{ books: ApiBook[] }> };
    const musicRes = await fetch(apiV1Url(this.baseUrl, "/catalog/music"), { headers });
    const musicJson = (await musicRes.json()) as { albums: ApiBook[] };

    const books: Book[] = [];
    for (const cycle of cyclesJson.cycles ?? []) {
      for (const book of cycle.books ?? []) {
        books.push(mapBook(book));
      }
    }
    for (const album of musicJson.albums ?? []) {
      books.push(mapBook(album));
    }
    return books;
  }

  private buildHeaders(): HeadersInit {
    const token = this.getAccessToken();
    return token ? { Authorization: `Bearer ${token}` } : {};
  }
}
