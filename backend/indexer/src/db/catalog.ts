import type pg from "pg";
import type { ParsedBook, ParsedCycle } from "../parsers.js";
import { storagePathForAudiobook, storagePathForMusic } from "../parsers.js";
import type { FileMetadata } from "../mediaProbe.js";
import type { StorageObjectRow } from "../storage/listObjects.js";
import { reconcileDeletionsInTransaction } from "./catalogReconcile.js";
import { mergePartialBookOrder } from "./mergeBookOrder.js";

export { mergePartialBookOrder } from "./mergeBookOrder.js";

export interface UpsertCatalogOptions {
  getMetadata: (storagePath: string, knownDurationMs: number | null) => Promise<FileMetadata | null>;
  objectUpdatedAtByPath: Map<string, Date | null>;
}

interface CycleUpsertResult {
  id: string;
  bookOrder: string[];
}

export class CatalogRepository {
  private objectUpdatedAtByPath = new Map<string, Date | null>();

  constructor(private pool: pg.Pool) {}

  async upsertPartialCatalog(
    _changedObjects: StorageObjectRow[],
    cycles: ParsedCycle[],
    musicAlbums: ParsedBook[],
    options: UpsertCatalogOptions,
  ): Promise<void> {
    this.objectUpdatedAtByPath = options.objectUpdatedAtByPath;
    await this.upsertParsedCatalog(cycles, musicAlbums, options);
  }

  async reconcileDeletions(): Promise<void> {
    const client = await this.pool.connect();
    try {
      await client.query("BEGIN");
      await reconcileDeletionsInTransaction(client);
      await client.query("COMMIT");
    } catch (err) {
      await client.query("ROLLBACK");
      throw err;
    } finally {
      client.release();
    }
  }

  private async upsertParsedCatalog(
    cycles: ParsedCycle[],
    musicAlbums: ParsedBook[],
    options: UpsertCatalogOptions,
  ): Promise<void> {
    const client = await this.pool.connect();
    try {
      await client.query("BEGIN");

      for (const cycle of cycles) {
        const cycleRow = await this.upsertCycle(client, cycle);
        for (let i = 0; i < cycleRow.bookOrder.length; i++) {
          const bookSlug = cycleRow.bookOrder[i];
          const book = cycle.books.find((b) => b.slug === bookSlug);
          if (!book) continue;
          const bookId = await this.upsertBook(client, book);
          await client.query(
            `INSERT INTO cycle_books (cycle_id, book_id, sort_order)
             VALUES ($1, $2, $3)
             ON CONFLICT (cycle_id, book_id) DO UPDATE SET sort_order = EXCLUDED.sort_order`,
            [cycleRow.id, bookId, i],
          );
          for (const track of book.tracks) {
            const storagePath = storagePathForAudiobook(
              cycle.slug,
              book.storageSlug ?? book.slug,
              track.filename,
            );
            await this.upsertTrack(client, bookId, track, storagePath, options);
          }
        }
      }

      for (const album of musicAlbums) {
        const bookId = await this.upsertBook(client, album);
        for (const track of album.tracks) {
          const storagePath = storagePathForMusic(track.filename);
          await this.upsertTrack(client, bookId, track, storagePath, options);
        }
      }

      await client.query("COMMIT");
    } catch (err) {
      await client.query("ROLLBACK");
      throw err;
    } finally {
      client.release();
    }
  }

  private async upsertCycle(
    client: pg.PoolClient,
    cycle: ParsedCycle,
  ): Promise<CycleUpsertResult> {
    const existing = await client.query<{ id: string; book_order: unknown }>(
      `SELECT id, book_order FROM cycles WHERE slug = $1 FOR UPDATE`,
      [cycle.slug],
    );
    if (existing.rows.length > 0) {
      const mergedBookOrder = mergePartialBookOrder(existing.rows[0].book_order, cycle.bookOrder);
      await client.query(
        `UPDATE cycles SET
           title = $2,
           description = $3,
           book_order = $4,
           updated_at = now(),
           deleted_at = NULL
         WHERE id = $1`,
        [
          existing.rows[0].id,
          cycle.title,
          cycle.description,
          JSON.stringify(mergedBookOrder),
        ],
      );
      return { id: existing.rows[0].id, bookOrder: mergedBookOrder };
    }

    const result = await client.query(
      `INSERT INTO cycles (slug, title, description, book_order, updated_at, deleted_at)
       VALUES ($1, $2, $3, $4, now(), NULL)
       ON CONFLICT (slug) DO UPDATE SET
         title = EXCLUDED.title,
         description = EXCLUDED.description,
         book_order = EXCLUDED.book_order,
         updated_at = now(),
         deleted_at = NULL
       RETURNING id`,
      [cycle.slug, cycle.title, cycle.description, JSON.stringify(cycle.bookOrder)],
    );
    return { id: result.rows[0].id as string, bookOrder: cycle.bookOrder };
  }

  private async upsertBook(client: pg.PoolClient, book: ParsedBook): Promise<string> {
    const result = await client.query(
      `INSERT INTO books (slug, content_type, title, author, cover_path, updated_at, deleted_at)
       VALUES ($1, $2, $3, $4, $5, now(), NULL)
       ON CONFLICT (slug) DO UPDATE SET
         content_type = EXCLUDED.content_type,
         title = EXCLUDED.title,
         author = COALESCE(EXCLUDED.author, books.author),
         cover_path = EXCLUDED.cover_path,
         updated_at = now(),
         deleted_at = NULL
       RETURNING id`,
      [book.slug, book.contentType, book.title, book.author, book.coverPath],
    );
    return result.rows[0].id as string;
  }

  private async upsertTrack(
    client: pg.PoolClient,
    bookId: string,
    track: {
      filename: string;
      sortOrder: number;
      title: string;
      artist?: string | null;
      durationMs?: number | null;
    },
    storagePath: string,
    options: UpsertCatalogOptions,
  ): Promise<void> {
    const meta = await options.getMetadata(storagePath, track.durationMs ?? null);

    const trackResult = await client.query(
      `INSERT INTO tracks (book_id, sort_order, title, filename, artist, updated_at, deleted_at)
       VALUES ($1, $2, $3, $4, $5, now(), NULL)
       ON CONFLICT DO NOTHING
       RETURNING id`,
      [bookId, track.sortOrder, track.title, track.filename, track.artist ?? null],
    );

    let trackId: string;
    if (trackResult.rows.length > 0) {
      trackId = trackResult.rows[0].id as string;
    } else {
      const existing = await client.query(
        `SELECT id, title, artist FROM tracks WHERE book_id = $1 AND filename = $2`,
        [bookId, track.filename],
      );
      trackId = existing.rows[0].id as string;
      const existingTitle = existing.rows[0].title as string;
      const existingArtist = existing.rows[0].artist as string | null;
      await client.query(
        `UPDATE tracks SET
           sort_order = $2,
           title = COALESCE(NULLIF($3, ''), $5),
           artist = COALESCE($4, $6),
           updated_at = now(),
           deleted_at = NULL
         WHERE id = $1`,
        [
          trackId,
          track.sortOrder,
          track.title,
          track.artist ?? null,
          existingTitle,
          existingArtist,
        ],
      );
    }

    const durationMs = meta?.durationMs ?? track.durationMs ?? null;
    if (durationMs != null) {
      await client.query(`UPDATE tracks SET duration_ms = $2 WHERE id = $1`, [trackId, durationMs]);
    }

    const storageObjectUpdatedAt =
      this.objectUpdatedAtByPath.get(storagePath) ??
      options.objectUpdatedAtByPath.get(storagePath) ??
      null;

    await client.query(
      `INSERT INTO track_files (
         track_id, storage_path, checksum, size_bytes, waveform_peaks,
         storage_object_updated_at, updated_at
       )
       VALUES ($1, $2, $3, $4, $5::jsonb, $6, now())
       ON CONFLICT (track_id) DO UPDATE SET
         storage_path = EXCLUDED.storage_path,
         checksum = EXCLUDED.checksum,
         size_bytes = EXCLUDED.size_bytes,
         waveform_peaks = EXCLUDED.waveform_peaks,
         storage_object_updated_at = EXCLUDED.storage_object_updated_at,
         updated_at = now()`,
      [
        trackId,
        storagePath,
        meta?.checksum ?? null,
        meta?.sizeBytes ?? null,
        meta?.waveformPeaks ? JSON.stringify(meta.waveformPeaks) : null,
        storageObjectUpdatedAt,
      ],
    );
  }
}
