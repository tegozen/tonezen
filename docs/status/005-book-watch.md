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
7. Android overflow immediately opens the editable settings dialog, without network requests. It uses
   cached settings or a draft seeded with the cycle title and default provider queries. For drafts,
   saving resolves the automatic server watch before updating it. Save failures keep the form open.

## Historical failure corrected on 2026-09-04

Defaults were originally created only during the API snapshot. Android enqueued the worker before taking
that snapshot, so a job could contain no watches; the overflow action then had no record to edit and the
dialog appeared missing. Indexer provisioning and snapshot-before-enqueue ordering address provisioning,
but did not fix Android's silent no-op when its cache lacked a watch. On 2026-09-05 the client gained
an immediate editable draft independent of cache/network availability. Runtime verification is pending.
