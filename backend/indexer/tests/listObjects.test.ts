import { describe, expect, it, vi } from "vitest";
import { listChangedObjects } from "../src/storage/listObjects.js";

describe("listChangedObjects", () => {
  it("queries objects newer than watermark or missing in track_files", async () => {
    const watermark = new Date("2025-01-01T00:00:00.000Z");
    const query = vi.fn(async () => ({
      rows: [
        {
          name: "music/new.mp3",
          metadata: { size: 100 },
          updated_at: new Date("2025-02-01T00:00:00.000Z"),
        },
      ],
    }));

    const pool = { query } as never;
    const rows = await listChangedObjects(pool, watermark);

    expect(query).toHaveBeenCalledWith(expect.stringContaining("o.updated_at > $1"), [watermark]);
    expect(query).toHaveBeenCalledWith(expect.stringContaining("NOT EXISTS"), [watermark]);
    expect(rows).toEqual([
      {
        name: "music/new.mp3",
        sizeBytes: 100,
        updatedAt: new Date("2025-02-01T00:00:00.000Z"),
      },
    ]);
  });
});
