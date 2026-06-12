import { loadConfig } from "./config.js";
import { createPool, CatalogRepository } from "./db/index.js";
import { scanContentRoot } from "./scanner.js";

const config = loadConfig();
const pool = createPool(config.databaseUrl);
const repo = new CatalogRepository(pool, config.contentRoot);

async function runOnce(): Promise<void> {
  console.log(`[indexer] Scanning ${config.contentRoot}...`);
  const { cycles, musicAlbums } = await scanContentRoot(config.contentRoot);
  await repo.upsertCatalog(cycles, musicAlbums);
  console.log(
    `[indexer] Done: ${cycles.length} cycles, ${musicAlbums.length} music albums`,
  );
}

async function main(): Promise<void> {
  await runOnce();
  setInterval(() => {
    runOnce().catch((err) => console.error("[indexer] Error:", err));
  }, config.intervalSeconds * 1000);
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
