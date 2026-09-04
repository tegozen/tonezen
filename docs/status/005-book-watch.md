# 005 — Book watch

## Invariant

- Every active audiobook cycle is always tracked for every authenticated user. There is no subscribe,
  follow, or “remember this cycle” action in either client.
- The cycle overflow button only opens an editor for the existing automatic configuration. It lets the
  user correct the display/search title and provider-specific queries.

## Ownership and event chain

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
7. The overflow action resolves the already synchronized watch and opens `BookWatchSettingsDialog`; its
   only client state is the transient dialog overlay.

## Historical failure corrected on 2026-09-04

Defaults were originally created only during the API snapshot. Android enqueued the worker before taking
that snapshot, so a job could contain no watches; the overflow action then had no record to edit and the
dialog appeared missing. Indexer provisioning and snapshot-before-enqueue ordering remove that gap.
