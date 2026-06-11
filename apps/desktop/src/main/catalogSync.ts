import type { Book } from "../shared/types.js";

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
    private apiBaseUrl: string,
    private getAccessToken: () => string | null,
  ) {}

  async fetchBooks(): Promise<Book[]> {
    const headers = this.buildHeaders();
    const cyclesRes = await fetch(`${this.apiBaseUrl}/catalog/cycles`, { headers });
    const cyclesJson = (await cyclesRes.json()) as { cycles: Array<{ books: ApiBook[] }> };
    const musicRes = await fetch(`${this.apiBaseUrl}/catalog/music`, { headers });
    const musicJson = (await musicRes.json()) as { albums: ApiBook[] };

    const books: Book[] = [];
    for (const cycle of cyclesJson.cycles ?? []) {
      books.push(...(cycle.books ?? []).map(mapBook));
    }
    books.push(...(musicJson.albums ?? []).map(mapBook));
    return books;
  }

  private buildHeaders(): Record<string, string> {
    const headers: Record<string, string> = {};
    const token = this.getAccessToken();
    if (token) headers.Authorization = `Bearer ${token}`;
    return headers;
  }
}
