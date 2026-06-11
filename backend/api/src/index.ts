import pg from "pg";
import { createApp } from "./app.js";

const port = Number(process.env.PORT ?? "3001");
const databaseUrl = process.env.DATABASE_URL;
const jwtSecret = process.env.JWT_SECRET;
const serviceRoleKey = process.env.SERVICE_ROLE_KEY;
const storageUrl = process.env.STORAGE_INTERNAL_URL ?? "http://storage:5000";
const storageBucket = process.env.STORAGE_BUCKET ?? "content";
const storageSignExpiresIn = Number(process.env.STORAGE_SIGN_EXPIRES_IN ?? "900");

if (!databaseUrl || !jwtSecret || !serviceRoleKey) {
  console.error("DATABASE_URL, JWT_SECRET, SERVICE_ROLE_KEY required");
  process.exit(1);
}

const pool = new pg.Pool({ connectionString: databaseUrl });
const app = createApp(pool, {
  jwtSecret,
  storage: {
    storageUrl,
    bucket: storageBucket,
    serviceRoleKey,
    expiresIn: storageSignExpiresIn,
  },
});

app.listen(port, () => {
  console.log(`[api] listening on :${port}`);
});
