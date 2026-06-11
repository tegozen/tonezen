import { createPool, CatalogRepository } from "./db.js";
import { scanContentRoot } from "./scanner.js";

const contentRoot = process.env.CONTENT_ROOT ?? "/content";
const databaseUrl = process.env.DATABASE_URL;
const intervalSeconds = Number(process.env.INDEXER_INTERVAL_SECONDS ?? "60");

if (!databaseUrl) {
  console.error("DATABASE_URL is required");
  process.exit(1);
}

const pool = createPool(databaseUrl);
const repo = new CatalogRepository(pool);

async function runOnce(): Promise<void> {
  console.log(`[indexer] Scanning ${contentRoot}...`);
  const { cycles, musicAlbums } = await scanContentRoot(contentRoot);
  await repo.upsertCatalog(cycles, musicAlbums);
  console.log(
    `[indexer] Done: ${cycles.length} cycles, ${musicAlbums.length} music albums`,
  );
}

async function main(): Promise<void> {
  await runOnce();
  setInterval(() => {
    runOnce().catch((err) => console.error("[indexer] Error:", err));
  }, intervalSeconds * 1000);
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
