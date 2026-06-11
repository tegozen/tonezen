import pg from "pg";
import { createApp } from "./app.js";

const port = Number(process.env.PORT ?? "3001");
const databaseUrl = process.env.DATABASE_URL;
const jwtSecret = process.env.JWT_SECRET;
const downloadUrlSecret = process.env.DOWNLOAD_URL_SECRET;
const downloadUrlTtlSeconds = Number(process.env.DOWNLOAD_URL_TTL_SECONDS ?? "900");
const downloadBaseUrl = process.env.DOWNLOAD_BASE_URL ?? "http://localhost:8080";

if (!databaseUrl || !jwtSecret || !downloadUrlSecret) {
  console.error("DATABASE_URL, JWT_SECRET, DOWNLOAD_URL_SECRET required");
  process.exit(1);
}

const pool = new pg.Pool({ connectionString: databaseUrl });
const app = createApp(pool, {
  jwtSecret,
  downloadUrlSecret,
  downloadUrlTtlSeconds,
  downloadBaseUrl,
});

app.listen(port, () => {
  console.log(`[api] listening on :${port}`);
});
