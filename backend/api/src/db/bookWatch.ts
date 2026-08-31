import type pg from "pg";

export type WatchProvider = "baza_knig" | "allbookerka";

export class BookWatchRepository {
  constructor(private pool: pg.Pool) {}

  async ensureDefaults(userId: string): Promise<void> {
    await this.pool.query(
      `WITH inserted AS (
         INSERT INTO book_watches (user_id, cycle_id)
         SELECT $1, id FROM cycles WHERE deleted_at IS NULL
         ON CONFLICT DO NOTHING RETURNING id
       )
       INSERT INTO book_watch_queries (watch_id, provider, query)
       SELECT w.id, p.provider, c.title
       FROM book_watches w JOIN cycles c ON c.id = w.cycle_id
       CROSS JOIN (VALUES ('baza_knig'), ('allbookerka')) p(provider)
       WHERE w.user_id = $1
       ON CONFLICT DO NOTHING`,
      [userId],
    );
  }

  async snapshot(userId: string) {
    await this.ensureDefaults(userId);
    const [watches, events, links] = await Promise.all([
      this.pool.query(
        `SELECT w.id, w.cycle_id, COALESCE(w.display_title, c.title) AS display_title,
                w.enabled, w.last_success_at,
                COALESCE(json_agg(json_build_object('id', q.id, 'provider', q.provider,
                  'query', q.query, 'enabled', q.enabled)) FILTER (WHERE q.id IS NOT NULL), '[]') queries
         FROM book_watches w JOIN cycles c ON c.id = w.cycle_id
         LEFT JOIN book_watch_queries q ON q.watch_id = w.id
         WHERE w.user_id = $1 GROUP BY w.id, c.title ORDER BY c.title`,
        [userId],
      ),
      this.pool.query(
        `SELECT id, watch_id, kind, title, author, book_number, status, read_at,
                completed_at, first_seen_at, last_seen_at, occurrence_count
         FROM book_watch_events WHERE user_id = $1 ORDER BY first_seen_at DESC LIMIT 500`,
        [userId],
      ),
      this.pool.query(
        `SELECT l.event_id, l.provider, l.url FROM book_watch_event_links l
         JOIN book_watch_events e ON e.id = l.event_id WHERE e.user_id = $1`,
        [userId],
      ),
    ]);
    const linksByEvent = new Map<string, unknown[]>();
    for (const link of links.rows) {
      const list = linksByEvent.get(link.event_id) ?? [];
      list.push({ provider: link.provider, url: link.url });
      linksByEvent.set(link.event_id, list);
    }
    const eventRows = events.rows.map((event) => ({ ...event, links: linksByEvent.get(event.id) ?? [] }));
    return {
      watches: watches.rows,
      events: eventRows,
      unread_count: eventRows.filter((event) => event.read_at == null).length,
    };
  }

  async updateWatch(userId: string, watchId: string, input: {
    displayTitle: string; enabled: boolean; queries: Array<{ provider: WatchProvider; query: string; enabled: boolean }>;
  }): Promise<boolean> {
    const client = await this.pool.connect();
    try {
      await client.query("BEGIN");
      const updated = await client.query(
        `UPDATE book_watches SET display_title = $3, enabled = $4, updated_at = now()
         WHERE id = $1 AND user_id = $2 RETURNING id`,
        [watchId, userId, input.displayTitle.trim(), input.enabled],
      );
      if (updated.rowCount === 0) { await client.query("ROLLBACK"); return false; }
      await client.query("DELETE FROM book_watch_queries WHERE watch_id = $1", [watchId]);
      for (const query of input.queries) {
        await client.query(
          `INSERT INTO book_watch_queries (watch_id, provider, query, enabled) VALUES ($1, $2, $3, $4)`,
          [watchId, query.provider, query.query.trim(), query.enabled],
        );
      }
      await client.query("COMMIT");
      return true;
    } catch (error) { await client.query("ROLLBACK"); throw error; } finally { client.release(); }
  }

  async markRead(userId: string, eventIds: string[]): Promise<void> {
    await this.pool.query(
      `UPDATE book_watch_events SET read_at = COALESCE(read_at, now())
       WHERE user_id = $1 AND id = ANY($2::uuid[])`,
      [userId, eventIds],
    );
  }

  async enqueue(userId: string) {
    const existing = await this.pool.query(
      `SELECT id, status, created_at FROM book_watch_jobs
       WHERE user_id = $1 AND created_at > now() - interval '6 hours'
       ORDER BY created_at DESC LIMIT 1`, [userId],
    );
    if (existing.rows[0]) return existing.rows[0];
    const result = await this.pool.query(
      `INSERT INTO book_watch_jobs (user_id) VALUES ($1) RETURNING id, status, created_at`, [userId],
    );
    return result.rows[0];
  }
}
