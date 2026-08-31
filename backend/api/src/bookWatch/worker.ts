import type pg from "pg";
import type { WatchProvider } from "../db/bookWatch.js";

export interface BookWatchCandidate { provider: WatchProvider; url: string; title: string; author: string | null; number: number | null }
const origins: Record<WatchProvider, string> = {
  baza_knig: "https://baza-knig.top",
  allbookerka: "https://allbookerka.org",
};

function text(value: string): string {
  return value.replace(/<[^>]+>/g, " ").replace(/&nbsp;|&#160;/g, " ")
    .replace(/&amp;/g, "&").replace(/&quot;/g, '"').replace(/&#39;/g, "'")
    .replace(/\s+/g, " ").trim();
}
function normalized(value: string): string {
  return value.toLocaleLowerCase("ru").replace(/ё/g, "е").replace(/[^\p{L}\p{N}]+/gu, " ").trim();
}
export function bookNumber(value: string): number | null {
  const match = value.match(/(?:книга|том|часть)?\s*(\d{1,3})(?:\D|$)/iu);
  return match ? Number(match[1]) : null;
}
export function parseBookWatchPage(provider: WatchProvider, html: string): BookWatchCandidate[] {
  const origin = origins[provider];
  const candidates = new Map<string, BookWatchCandidate>();
  const anchor = provider === "baza_knig"
    ? /<div\s+class=["']short-title["'][^>]*>[\s\S]*?<a\b[^>]*href=["']([^"']+)["'][^>]*>([\s\S]*?)<\/a>/giu
    : /<div\s+class=["']name["'][^>]*>\s*<a\b[^>]*href=["']([^"']+)["'][^>]*>([\s\S]*?)<\/a>/giu;
  for (const match of html.matchAll(anchor)) {
    const title = text(match[2]);
    if (title.length < 4 || !/\d/.test(title)) continue;
    let url: URL;
    try { url = new URL(match[1], origin); } catch { continue; }
    if (url.origin !== origin || url.pathname.startsWith("/xfsearch/") || url.pathname.startsWith("/tags/")) continue;
    url.hash = ""; url.search = "";
    const number = bookNumber(title);
    if (number == null) continue;
    const authorTitle = title.split(" - ");
    candidates.set(url.href, {
      provider, url: url.href, title: authorTitle[authorTitle.length - 1]?.trim() || title,
      author: authorTitle.length > 1 ? authorTitle.slice(0, -1).join(" - ").trim() : null, number,
    });
  }
  return [...candidates.values()];
}
export function bookWatchUrl(provider: WatchProvider, query: string): string {
  return `${origins[provider]}/xfsearch/cikl/${encodeURIComponent(query)}/`;
}

export async function fetchBookWatchPage(provider: WatchProvider, query: string): Promise<BookWatchCandidate[]> {
  const origin = origins[provider];
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 12_000);
  try {
    const load = async (requestedUrl: string): Promise<string> => {
      let url = requestedUrl;
      let response: Response | null = null;
      for (let redirect = 0; redirect <= 3; redirect += 1) {
        response = await fetch(url, {
          redirect: "manual", signal: controller.signal,
          headers: { "user-agent": "TonezenBookWatch/1.0 (+self-hosted catalog monitor)", accept: "text/html" },
        });
        if (response.status < 300 || response.status >= 400) break;
        const location = response.headers.get("location");
        if (!location) throw new Error(`Redirect ${response.status} without location`);
        const next = new URL(location, url);
        if (next.origin !== origin) throw new Error("Cross-origin redirect rejected");
        url = next.href;
      }
      if (response == null || !response.ok) throw new Error(`HTTP ${response?.status ?? "unknown"}`);
      const length = Number(response.headers.get("content-length") ?? "0");
      if (length > 2_000_000) throw new Error("Response too large");
      const html = await response.text();
      if (html.length > 2_000_000) throw new Error("Response too large");
      return html;
    };
    const firstHtml = await load(bookWatchUrl(provider, query));
    const pages = [firstHtml];
    if (provider === "baza_knig") {
      const pageUrls = new Set<string>();
      for (const match of firstHtml.matchAll(/href=["']([^"']+\/page\/(\d+)\/)["']/giu)) {
        const page = Number(match[2]);
        const url = new URL(match[1], origin);
        if (url.origin === origin && page >= 2 && page <= 10) pageUrls.add(url.href);
      }
      for (const pageUrl of pageUrls) pages.push(await load(pageUrl));
    }
    const byUrl = new Map<string, BookWatchCandidate>();
    for (const html of pages) for (const item of parseBookWatchPage(provider, html)) byUrl.set(item.url, item);
    return [...byUrl.values()];
  } finally { clearTimeout(timeout); }
}

async function completeCatalogMatches(client: pg.PoolClient, userId?: string): Promise<void> {
  await client.query(
    `UPDATE book_watch_events e SET status = 'completed', completed_at = COALESCE(completed_at, now())
     FROM book_watches w
     WHERE e.watch_id = w.id AND e.kind = 'book' AND e.status = 'active'
       AND ($1::uuid IS NULL OR e.user_id = $1)
       AND EXISTS (
         SELECT 1 FROM cycle_books cb JOIN books b ON b.id = cb.book_id
         WHERE cb.cycle_id = w.cycle_id AND b.deleted_at IS NULL
           AND (e.book_number IS NOT NULL AND cb.sort_order + 1 = e.book_number
             OR regexp_replace(lower(b.title), '[^[:alnum:]]+', ' ', 'g') =
                regexp_replace(lower(e.title), '[^[:alnum:]]+', ' ', 'g'))
       )`, [userId ?? null],
  );
}

export function candidateDedupeKey(item: Pick<BookWatchCandidate, "number" | "title">): string {
  return item.number == null ? `title:${normalized(item.title)}` : `number:${item.number}`;
}

async function saveCandidate(client: pg.PoolClient, userId: string, watchId: string, item: BookWatchCandidate): Promise<void> {
  const key = candidateDedupeKey(item);
  const event = await client.query(
    `INSERT INTO book_watch_events (user_id, watch_id, kind, dedupe_key, title, author, book_number)
     SELECT $1, $2, 'book', $3, $4, $5, $6
     WHERE NOT EXISTS (
       SELECT 1 FROM book_watches w JOIN cycle_books cb ON cb.cycle_id = w.cycle_id
       JOIN books b ON b.id = cb.book_id
       WHERE w.id = $2 AND b.deleted_at IS NULL AND
         ($6::int IS NOT NULL AND cb.sort_order + 1 = $6 OR
          regexp_replace(lower(b.title), '[^[:alnum:]]+', ' ', 'g') =
          regexp_replace(lower($4), '[^[:alnum:]]+', ' ', 'g'))
     )
     ON CONFLICT (user_id, watch_id, kind, dedupe_key) DO UPDATE SET
       last_seen_at = now(), occurrence_count = book_watch_events.occurrence_count + 1
     RETURNING id`, [userId, watchId, key, item.title, item.author, item.number],
  );
  if (!event.rows[0]) return;
  await client.query(
    `INSERT INTO book_watch_event_links (event_id, provider, url) VALUES ($1, $2, $3) ON CONFLICT DO NOTHING`,
    [event.rows[0].id, item.provider, item.url],
  );
}
async function saveError(client: pg.PoolClient, userId: string, watchId: string, provider: WatchProvider, error: unknown) {
  const message = error instanceof Error ? error.message.slice(0, 180) : "Unknown provider error";
  await client.query(
    `INSERT INTO book_watch_events (user_id, watch_id, kind, dedupe_key, title)
     VALUES ($1, $2, 'provider_error', $3, $4)
     ON CONFLICT (user_id, watch_id, kind, dedupe_key) DO UPDATE SET
       title = EXCLUDED.title, last_seen_at = now(), occurrence_count = book_watch_events.occurrence_count + 1`,
    [userId, watchId, provider, `Ошибка ${origins[provider]}: ${message}`],
  );
}

async function runJob(pool: pg.Pool, job: { id: string; user_id: string }): Promise<void> {
  const rows = await pool.query(
    `SELECT w.id watch_id, q.provider, q.query FROM book_watches w
     JOIN book_watch_queries q ON q.watch_id = w.id
     WHERE w.user_id = $1 AND w.enabled AND q.enabled ORDER BY w.id, q.provider`, [job.user_id],
  );
  for (const row of rows.rows as Array<{ watch_id: string; provider: WatchProvider; query: string }>) {
    const client = await pool.connect();
    try {
      try {
        for (const item of await fetchBookWatchPage(row.provider, row.query)) await saveCandidate(client, job.user_id, row.watch_id, item);
        await client.query("UPDATE book_watches SET last_success_at = now() WHERE id = $1", [row.watch_id]);
      } catch (error) { await saveError(client, job.user_id, row.watch_id, row.provider, error); }
    } finally { client.release(); }
    await new Promise((resolve) => setTimeout(resolve, 750));
  }
  const client = await pool.connect();
  try { await completeCatalogMatches(client, job.user_id); } finally { client.release(); }
}

export function startBookWatchWorker(pool: pg.Pool): () => void {
  let busy = false;
  const tick = async () => {
    if (busy) return;
    busy = true;
    const client = await pool.connect();
    try {
      await client.query("BEGIN");
      const claimed = await client.query(
        `SELECT id, user_id FROM book_watch_jobs WHERE status = 'queued' ORDER BY created_at
         FOR UPDATE SKIP LOCKED LIMIT 1`,
      );
      const job = claimed.rows[0];
      if (job) await client.query("UPDATE book_watch_jobs SET status = 'running', started_at = now() WHERE id = $1", [job.id]);
      await client.query("COMMIT");
      if (job) {
        try {
          await runJob(pool, job);
          await pool.query("UPDATE book_watch_jobs SET status = 'completed', completed_at = now() WHERE id = $1", [job.id]);
        } catch (error) {
          await pool.query("UPDATE book_watch_jobs SET status = 'failed', completed_at = now(), error = $2 WHERE id = $1", [job.id, String(error).slice(0, 500)]);
        }
      } else { await completeCatalogMatches(client); }
    } catch (error) { await client.query("ROLLBACK").catch(() => undefined); console.error("[book-watch]", error); }
    finally { client.release(); busy = false; }
  };
  const timer = setInterval(() => void tick(), 15_000);
  void tick();
  return () => clearInterval(timer);
}
