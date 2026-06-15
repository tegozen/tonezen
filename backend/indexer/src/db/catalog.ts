import type pg from "pg";
import type { ParsedBook, ParsedCycle } from "../parsers.js";
import { storagePathForAudiobook, storagePathForMusic } from "../parsers.js";
import {
  analyzeAudioFileAtPath,
  metadataFromStoredIfUnchanged,
  type FileMetadata,
} from "../mediaProbe.js";
import {
  downloadObjectToTemp,
  removeTempFile,
  type StorageDownloadConfig,
} from "../storage/download.js";
import type { StorageObjectRow } from "../storage/listObjects.js";

export class CatalogRepository {
  private objectSizes = new Map<string, number>();

  constructor(
    private pool: pg.Pool,
    private storage: StorageDownloadConfig,
  ) {}

  setObjectSizes(objects: StorageObjectRow[]): void {
    this.objectSizes = new Map(
      objects
        .filter((object) => object.sizeBytes != null)
        .map((object) => [object.name, object.sizeBytes as number]),
    );
  }

  async upsertCatalog(cycles: ParsedCycle[], musicAlbums: ParsedBook[]): Promise<void> {
    const client = await this.pool.connect();
    try {
      await client.query("BEGIN");
      const activeCycleSlugs = new Set<string>();
      const activeBookSlugs = new Set<string>();

      for (const cycle of cycles) {
        activeCycleSlugs.add(cycle.slug);
        const cycleId = await this.upsertCycle(client, cycle);
        for (let i = 0; i < cycle.bookOrder.length; i++) {
          const bookSlug = cycle.bookOrder[i];
          const book = cycle.books.find((b) => b.slug === bookSlug);
          if (!book) continue;
          activeBookSlugs.add(book.slug);
          const bookId = await this.upsertBook(client, book);
          await client.query(
            `INSERT INTO cycle_books (cycle_id, book_id, sort_order)
             VALUES ($1, $2, $3)
             ON CONFLICT (cycle_id, book_id) DO UPDATE SET sort_order = EXCLUDED.sort_order`,
            [cycleId, bookId, i],
          );
          for (const track of book.tracks) {
            const storagePath = storagePathForAudiobook(cycle.slug, book.slug, track.filename);
            await this.upsertTrack(client, bookId, track, storagePath, null);
          }
        }
      }

      for (const album of musicAlbums) {
        activeBookSlugs.add(album.slug);
        const bookId = await this.upsertBook(client, album);
        for (const track of album.tracks) {
          const storagePath = storagePathForMusic(track.filename);
          await this.upsertTrack(client, bookId, track, storagePath, null);
        }
      }

      await this.softDeleteMissing(client, activeCycleSlugs, activeBookSlugs);
      await client.query("COMMIT");
    } catch (err) {
      await client.query("ROLLBACK");
      throw err;
    } finally {
      client.release();
    }
  }

  private async upsertCycle(client: pg.PoolClient, cycle: ParsedCycle): Promise<string> {
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
    return result.rows[0].id as string;
  }

  private async upsertBook(client: pg.PoolClient, book: ParsedBook): Promise<string> {
    const result = await client.query(
      `INSERT INTO books (slug, content_type, title, author, cover_path, updated_at, deleted_at)
       VALUES ($1, $2, $3, $4, $5, now(), NULL)
       ON CONFLICT (slug) DO UPDATE SET
         content_type = EXCLUDED.content_type,
         title = EXCLUDED.title,
         author = EXCLUDED.author,
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
    track: { filename: string; sortOrder: number; title: string; durationMs?: number | null },
    storagePath: string,
    metadata: FileMetadata | null,
  ): Promise<void> {
    const meta =
      metadata ??
      (await this.resolveFileMetadata(client, storagePath, track.durationMs ?? null));
    const trackResult = await client.query(
      `INSERT INTO tracks (book_id, sort_order, title, filename, updated_at, deleted_at)
       VALUES ($1, $2, $3, $4, now(), NULL)
       ON CONFLICT DO NOTHING
       RETURNING id`,
      [bookId, track.sortOrder, track.title, track.filename],
    );

    let trackId: string;
    if (trackResult.rows.length > 0) {
      trackId = trackResult.rows[0].id as string;
    } else {
      const existing = await client.query(
        `SELECT id FROM tracks WHERE book_id = $1 AND filename = $2`,
        [bookId, track.filename],
      );
      trackId = existing.rows[0].id as string;
      await client.query(
        `UPDATE tracks SET sort_order = $2, title = $3, updated_at = now(), deleted_at = NULL WHERE id = $1`,
        [trackId, track.sortOrder, track.title],
      );
    }

    if (meta?.durationMs != null) {
      await client.query(`UPDATE tracks SET duration_ms = $2 WHERE id = $1`, [trackId, meta.durationMs]);
    }

    await client.query(
      `INSERT INTO track_files (track_id, storage_path, checksum, size_bytes, updated_at)
       VALUES ($1, $2, $3, $4, now())
       ON CONFLICT (track_id) DO UPDATE SET
         storage_path = EXCLUDED.storage_path,
         checksum = EXCLUDED.checksum,
         size_bytes = EXCLUDED.size_bytes,
         updated_at = now()`,
      [trackId, storagePath, meta?.checksum ?? null, meta?.sizeBytes ?? null],
    );
  }

  private async resolveFileMetadata(
    client: pg.PoolClient,
    storagePath: string,
    knownDurationMs: number | null,
  ): Promise<FileMetadata | null> {
    const existing = await client.query(
      `SELECT tf.checksum, tf.size_bytes, t.duration_ms
       FROM track_files tf
       JOIN tracks t ON t.id = tf.track_id
       WHERE tf.storage_path = $1`,
      [storagePath],
    );

    const cachedSize = this.objectSizes.get(storagePath);
    if (existing.rows.length > 0 && cachedSize != null) {
      const reused = metadataFromStoredIfUnchanged(existing.rows[0], cachedSize);
      if (reused) return reused;
    }

    let tempPath: string | null = null;
    try {
      tempPath = await downloadObjectToTemp(storagePath, this.storage);
      const meta = await analyzeAudioFileAtPath(tempPath, {
        knownDurationMs: knownDurationMs ?? undefined,
      });
      if (meta && cachedSize != null && meta.sizeBytes !== cachedSize) {
        return { ...meta, sizeBytes: cachedSize };
      }
      return meta;
    } catch {
      return null;
    } finally {
      if (tempPath) {
        await removeTempFile(tempPath);
      }
    }
  }

  private async softDeleteMissing(
    client: pg.PoolClient,
    activeCycleSlugs: Set<string>,
    activeBookSlugs: Set<string>,
  ): Promise<void> {
    if (activeCycleSlugs.size > 0) {
      await client.query(
        `UPDATE cycles SET deleted_at = now()
         WHERE slug != ALL($1::text[]) AND deleted_at IS NULL`,
        [Array.from(activeCycleSlugs)],
      );
    }
    if (activeBookSlugs.size > 0) {
      await client.query(
        `UPDATE books SET deleted_at = now()
         WHERE slug != ALL($1::text[]) AND deleted_at IS NULL`,
        [Array.from(activeBookSlugs)],
      );
    }
  }
}
