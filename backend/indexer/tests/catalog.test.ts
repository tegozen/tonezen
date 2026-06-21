import { describe, expect, it, vi } from "vitest";
import { CatalogRepository, mergePartialBookOrder } from "../src/db/catalog.js";
import { shouldProbe } from "../src/shouldProbe.js";
import type { IndexedTrackRow } from "../src/db/indexedTracks.js";
import type { StorageObjectRow } from "../src/storage/listObjects.js";

describe("catalog upsert safeguards", () => {
  it("preserves author when scan author is null and row unchanged", () => {
    const object: StorageObjectRow = {
      name: "cycles/saga/book-a/001-intro.mp3",
      sizeBytes: 1000,
      updatedAt: new Date("2025-01-01T00:00:00.000Z"),
    };
    const row: IndexedTrackRow = {
      storagePath: object.name,
      checksum: "abc",
      sizeBytes: 1000,
      waveformPeaks: Array.from({ length: 64 }, (_, index) => index),
      storageObjectUpdatedAt: new Date("2025-01-01T00:00:00.000Z"),
      title: "Intro",
      artist: "Stored Author",
      durationMs: 10_000,
    };

    expect(shouldProbe(object, row)).toBe(false);
    expect(row.artist).toBe("Stored Author");
  });

  it("merges partial cycle book order without dropping existing books", () => {
    expect(
      mergePartialBookOrder(["saga--book-one", "saga--book-two"], ["saga--book-two"]),
    ).toEqual(["saga--book-one", "saga--book-two"]);

    expect(
      mergePartialBookOrder(["saga--book-one"], ["saga--book-two"]),
    ).toEqual(["saga--book-one", "saga--book-two"]);
  });

  it("keeps full cycle order when partial indexing updates one book", async () => {
    const queries: Array<{ text: string; values: unknown[] }> = [];
    const client = {
      query: vi.fn(async (text: string, values: unknown[] = []) => {
        queries.push({ text, values });
        if (text.includes("SELECT id, book_order FROM cycles")) {
          return {
            rows: [
              {
                id: "cycle-id",
                book_order: ["saga--book-one", "saga--book-two"],
              },
            ],
          };
        }
        if (text.includes("RETURNING id") && text.includes("INSERT INTO books")) {
          return { rows: [{ id: "book-two-id" }] };
        }
        if (text.includes("RETURNING id") && text.includes("INSERT INTO tracks")) {
          return { rows: [{ id: "track-id" }] };
        }
        return { rows: [] };
      }),
      release: vi.fn(),
    };
    const pool = {
      connect: vi.fn(async () => client),
    };
    const repo = new CatalogRepository(pool as never, {
      storageUrl: "http://storage",
      bucket: "content",
      serviceRoleKey: "key",
    });
    const updatedAt = new Date("2025-06-01T00:00:00.000Z");

    await repo.upsertPartialCatalog(
      [
        {
          name: "cycles/saga/book-two/001.mp3",
          sizeBytes: 10,
          updatedAt,
        },
      ],
      [
        {
          slug: "saga",
          title: "Saga",
          description: null,
          bookOrder: ["saga--book-two"],
          books: [
            {
              slug: "saga--book-two",
              storageSlug: "book-two",
              contentType: "audiobook",
              title: "Book Two",
              author: null,
              coverPath: null,
              tracks: [
                {
                  filename: "001.mp3",
                  sortOrder: 0,
                  title: "One",
                },
              ],
            },
          ],
        },
      ],
      [],
      {
        getMetadata: async () => null,
        objectUpdatedAtByPath: new Map([["cycles/saga/book-two/001.mp3", updatedAt]]),
      },
    );

    const cycleUpdate = queries.find((query) => query.text.includes("UPDATE cycles SET"));
    expect(cycleUpdate?.values[3]).toBe(JSON.stringify(["saga--book-one", "saga--book-two"]));

    const cycleBookUpsert = queries.find((query) => query.text.includes("INSERT INTO cycle_books"));
    expect(cycleBookUpsert?.values).toEqual(["cycle-id", "book-two-id", 1]);
  });
});
