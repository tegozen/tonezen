import pg from "pg";
import { createApp } from "./app.js";
import { loadConfig } from "./config.js";

const { port, databaseUrl, jwtSecret, storage } = loadConfig();

const pool = new pg.Pool({ connectionString: databaseUrl });
const app = createApp(pool, { jwtSecret, storage });

app.listen(port, () => {
  console.log(`[api] listening on :${port}`);
});
