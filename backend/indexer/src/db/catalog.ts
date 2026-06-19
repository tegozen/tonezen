import type pg from "pg";
import type { ParsedBook, ParsedCycle } from "../parsers.js";
import { storagePathForAudiobook, storagePathForMusic } from "../parsers.js";
import type { FileMetadata } from "../mediaProbe.js";
import {
  analyzeAudioFileAtPath,
  metadataFromStoredIfUnchanged,
} from "../mediaProbe.js";
import {
  downloadObjectToTemp,
  removeTempFile,
  type StorageDownloadConfig,
} from "../storage/download.js";
import type { StorageObjectRow } from "../storage/listObjects.js";

export interface UpsertCatalogOptions {
  getMetadata: (storagePath: string, knownDurationMs: number | null) => Promise<FileMetadata | null>;
  objectUpdatedAtByPath: Map<string, Date | null>;
}

export class CatalogRepository {
  private objectSizes = new Map<string, number>();
  private objectUpdatedAtByPath = new Map<string, Date | null>();

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
    this.objectUpdatedAtByPath = new Map(
      objects.map((object) => [object.name, object.updatedAt]),
    );
  }

  async upsertCatalog(cycles: ParsedCycle[], musicAlbums: ParsedBook[]): Promise<void> {
    await this.upsertParsedCatalog(cycles, musicAlbums, null);
  }

  async upsertPartialCatalog(
    changedObjects: StorageObjectRow[],
    cycles: ParsedCycle[],
    musicAlbums: ParsedBook[],
    options: UpsertCatalogOptions,
  ): Promise<void> {
    this.setObjectSizes(changedObjects);
    this.objectUpdatedAtByPath = options.objectUpdatedAtByPath;
    await this.upsertParsedCatalog(cycles, musicAlbums, options);
  }

  async reconcileDeletions(): Promise<void> {
    const client = await this.pool.connect();
    try {
      await client.query("BEGIN");
      await this.reconcileDeletionsInTransaction(client);
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
    options: UpsertCatalogOptions | null,
  ): Promise<void> {
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
        activeBookSlugs.add(album.slug);
        const bookId = await this.upsertBook(client, album);
        for (const track of album.tracks) {
          const storagePath = storagePathForMusic(track.filename);
          await this.upsertTrack(client, bookId, track, storagePath, options);
        }
      }

      if (options == null) {
        await this.softDeleteMissing(client, activeCycleSlugs, activeBookSlugs);
      }

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
    options: UpsertCatalogOptions | null,
  ): Promise<void> {
    const meta = options
      ? await options.getMetadata(storagePath, track.durationMs ?? null)
      : await this.resolveFileMetadata(client, storagePath, track.durationMs ?? null);

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
      options?.objectUpdatedAtByPath.get(storagePath) ??
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

  private async resolveFileMetadata(
    client: pg.PoolClient,
    storagePath: string,
    knownDurationMs: number | null,
  ): Promise<FileMetadata | null> {
    const existing = await client.query(
      `SELECT tf.checksum, tf.size_bytes, tf.waveform_peaks, t.duration_ms
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

  private async reconcileDeletionsInTransaction(client: pg.PoolClient): Promise<void> {
    await client.query(
      `UPDATE tracks t
       SET deleted_at = now(), updated_at = now()
       FROM track_files tf
       WHERE t.id = tf.track_id
         AND (tf.storage_path LIKE 'cycles/%' OR tf.storage_path LIKE 'music/%')
         AND t.deleted_at IS NULL
         AND NOT EXISTS (
           SELECT 1 FROM storage.objects o
           WHERE o.bucket_id = 'content' AND o.name = tf.storage_path
         )`,
    );

    await client.query(
      `UPDATE books b
       SET deleted_at = now(), updated_at = now()
       WHERE b.deleted_at IS NULL
         AND NOT EXISTS (
           SELECT 1 FROM tracks t
           WHERE t.book_id = b.id AND t.deleted_at IS NULL
         )`,
    );

    await client.query(
      `UPDATE cycles c
       SET deleted_at = now(), updated_at = now()
       WHERE c.deleted_at IS NULL
         AND NOT EXISTS (
           SELECT 1 FROM cycle_books cb
           JOIN books b ON b.id = cb.book_id
           WHERE cb.cycle_id = c.id AND b.deleted_at IS NULL
         )`,
    );
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
