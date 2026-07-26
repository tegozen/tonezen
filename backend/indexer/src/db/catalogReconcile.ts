import type pg from "pg";
import { naturalCompare } from "../parsers.js";

/** Soft-delete catalog rows whose storage objects no longer exist. */
export async function reconcileDeletionsInTransaction(client: pg.PoolClient): Promise<void> {
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

  await reorderCycleBooksInTransaction(client);
}

/**
 * Re-sort cycles.book_order and cycle_books.sort_order with the same natural
 * collator as the scanner, so partial-upload order (e.g. 10 then 1) self-heals.
 */
export async function reorderCycleBooksInTransaction(client: pg.PoolClient): Promise<void> {
  const cycles = await client.query<{
    id: string;
    book_order: unknown;
    book_id: string | null;
    book_slug: string | null;
  }>(
    `SELECT c.id, c.book_order, b.id AS book_id, b.slug AS book_slug
     FROM cycles c
     LEFT JOIN cycle_books cb ON cb.cycle_id = c.id
     LEFT JOIN books b ON b.id = cb.book_id AND b.deleted_at IS NULL
     WHERE c.deleted_at IS NULL
     ORDER BY c.id`,
  );

  const byCycle = new Map<
    string,
    { bookOrder: unknown; books: Array<{ id: string; slug: string }> }
  >();
  for (const row of cycles.rows) {
    let entry = byCycle.get(row.id);
    if (!entry) {
      entry = { bookOrder: row.book_order, books: [] };
      byCycle.set(row.id, entry);
    }
    if (row.book_id && row.book_slug) {
      entry.books.push({ id: row.book_id, slug: row.book_slug });
    }
  }

  for (const [cycleId, entry] of byCycle) {
    const sortedSlugs = [...new Set(entry.books.map((book) => book.slug))].sort(naturalCompare);
    const existing = Array.isArray(entry.bookOrder)
      ? entry.bookOrder.filter((value): value is string => typeof value === "string")
      : [];
    const orderUnchanged =
      existing.length === sortedSlugs.length &&
      existing.every((slug, index) => slug === sortedSlugs[index]);

    const slugToId = new Map(entry.books.map((book) => [book.slug, book.id]));
    let sortOrderDirty = false;
    for (let index = 0; index < sortedSlugs.length; index++) {
      const bookId = slugToId.get(sortedSlugs[index]);
      if (!bookId) continue;
      const updated = await client.query(
        `UPDATE cycle_books SET sort_order = $3
         WHERE cycle_id = $1 AND book_id = $2 AND sort_order IS DISTINCT FROM $3`,
        [cycleId, bookId, index],
      );
      if ((updated.rowCount ?? 0) > 0) sortOrderDirty = true;
    }

    if (!orderUnchanged || sortOrderDirty) {
      await client.query(
        `UPDATE cycles SET book_order = $2, updated_at = now() WHERE id = $1`,
        [cycleId, JSON.stringify(sortedSlugs)],
      );
    }
  }
}
