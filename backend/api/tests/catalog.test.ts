import pg from "pg";
import { describe, expect, it, vi, beforeEach } from "vitest";
import { CatalogRepository } from "../src/db/catalog.js";

describe("CatalogRepository.getCycles", () => {
  const mockPool = {
    query: vi.fn(),
  } as unknown as pg.Pool;

  const repo = new CatalogRepository(mockPool);

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("loads cycles and books in a single query", async () => {
    vi.mocked(mockPool.query).mockResolvedValueOnce({
      rows: [
        {
          id: "c1",
          slug: "cycle-a",
          title: "Cycle A",
          book_order: ["b1"],
          book_id: "b1",
          book_slug: "book-one",
          content_type: "audiobook",
          book_title: "Book One",
          author: "Author",
          cover_path: null,
          sort_order: 0,
        },
        {
          id: "c2",
          slug: "cycle-b",
          title: "Cycle B",
          book_order: [],
          book_id: null,
          book_slug: null,
          content_type: null,
          book_title: null,
          author: null,
          cover_path: null,
          sort_order: null,
        },
      ],
    } as never);

    const cycles = await repo.getCycles();

    expect(mockPool.query).toHaveBeenCalledTimes(1);
    expect(cycles).toHaveLength(2);
    expect(cycles[0].books).toHaveLength(1);
    expect(cycles[0].books[0]).toEqual({
      id: "b1",
      slug: "book-one",
      content_type: "audiobook",
      title: "Book One",
      author: "Author",
      cover_path: null,
    });
    expect(cycles[1].books).toEqual([]);
  });
});
