# 005 — Book watch

## Invariant

- Every active audiobook cycle is always tracked for every authenticated user. There is no subscribe,
  follow, or “remember this cycle” action in either client.
- The cycle overflow button only opens an editor for the existing automatic configuration. It lets the
  user correct the display/search title and provider-specific queries.

## Ownership and event chain

The API and worker use `tonezen_api`, not `service_role`. Migration
`057_book_watch_api_grants.sql` grants their required table operations without changing RLS.
Missing grants in 052 caused HTTP 500 (`42501`, permission denied) on snapshot/save/checks.
API defaults now seed queries from the INSERT's returned rows: the first snapshot includes both
providers, and later snapshots never re-add original queries over user edits.

1. The indexer upserts or restores a cycle.
2. In the same transaction it inserts missing `(user_id, cycle_id)` rows and default `baza_knig` and
   `allbookerka` queries for existing users. Conflict handling preserves all user edits.
3. API `ensureDefaults` remains a recovery path and provisions users registered after cycles existed.
4. Android fetches `/book-watch` before enqueueing `/book-watch/checks`, so the worker cannot consume a
   new job before defaults exist.
5. The worker checks enabled provider queries, deduplicates books across providers/aliases, stores all
   source links, records provider errors separately, and completes events after the catalog gains the book.
6. Android persists events for offline reading, notifies once per server event ID, and opens profile ->
   “Новые книги” from the notification. Read state synchronizes after reconnect.
7. Android overflow immediately opens the editable settings dialog, without network requests. It uses
   cached settings or a draft seeded with the cycle title and default provider queries. For drafts,
   saving resolves the automatic server watch before updating it. Save failures keep the form open.

## Android event screen

- “Новые книги” uses the shared fixed back header and scroll padding for the mini-player/bottom tabs.
- Teal filters wrap on narrow screens; the selected filter survives state restoration.
- Empty filters show contextual explanations. Event cards distinguish provider failures, catalog
  completion, and unread state, with dates and wrapping source actions.
- “Отметить всё прочитанным” appears only when unread events exist, outside the header.
- The screen receives events and callbacks from Profile; runtime visual verification is pending.

## Verified local server repair (2026-09-05)

- Docker API build passed; its Dockerfile-specific context now excludes Android Gradle caches and secrets.
- Migrations through 057 applied; admin/storage seeds passed twice, with the existing admin reused.
- Real GoTrue login 200; snapshot 200 with both default providers; rename/query PUT 200; subsequent
  snapshot retained the exact edited title and queries. Empty title 400; unauthenticated snapshot 401.
- Check POST 202; repeated POST returned the same job. Worker completed the real “Геном” search:
  three book events, five source links, no provider errors, and last_success_at populated.
- Android additionally refreshes its session before saving and persists the accepted settings locally
  instead of treating a later full-snapshot failure as a failed PUT. HTTP/network failures are distinguished.
  These Android changes were not rebuilt; the reproduced server failure is fixed without a client rebuild.
- Production deployment remains pending. Apply 057 and rebuild API for the defaults correction.

## Historical details

Defaults were originally created only during the API snapshot. Android enqueued the worker before taking
that snapshot, so a job could contain no watches; the overflow action then had no record to edit and the
dialog appeared missing. Indexer provisioning and snapshot-before-enqueue ordering address provisioning,
but did not fix Android's silent no-op when its cache lacked a watch. On 2026-09-05 the client gained
an immediate editable draft independent of cache/network availability. Runtime verification is pending.
