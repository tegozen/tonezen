# Backend

Apply under `backend/**`.

- SQL migrations live in `backend/supabase/migrations/` and define explicit RLS.
- Indexer uses pure parsers and thin database IO; add Vitest fixtures only when tests are requested.
- Express API modules use domain routes and `db/` repositories; do not use `any`.
- Use HMAC-signed download URLs; never expose the raw content volume over HTTP.
- Catalog soft deletion uses `deleted_at`.
