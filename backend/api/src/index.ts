import pg from "pg";
import { createApp } from "./app.js";
import { loadConfig } from "./config.js";
import { startBookWatchWorker } from "./bookWatch/worker.js";

const { port, databaseUrl, jwtSecret, jwtAudience, jwtIssuer, corsOrigins, storage, auth } =
  loadConfig();

const pool = new pg.Pool({ connectionString: databaseUrl });
const app = createApp(pool, {
  jwtSecret,
  jwtAudience,
  jwtIssuer,
  corsOrigins,
  storage,
  auth,
});
startBookWatchWorker(pool);

app.listen(port, () => {
  console.log(`[api] listening on :${port}`);
});
